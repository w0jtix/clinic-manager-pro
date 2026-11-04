package com.clinic.clinicmanager.service.impl;

import com.clinic.clinicmanager.DTO.SaleDTO;
import com.clinic.clinicmanager.DTO.SaleItemDTO;
import com.clinic.clinicmanager.DTO.VisitDTO;
import com.clinic.clinicmanager.DTO.VoucherDTO;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.model.*;
import com.clinic.clinicmanager.model.constants.SupplyChangeSource;
import com.clinic.clinicmanager.model.constants.VoucherStatus;
import com.clinic.clinicmanager.repo.ProductRepo;
import com.clinic.clinicmanager.repo.VisitRepo;
import com.clinic.clinicmanager.repo.VoucherRepo;
import com.clinic.clinicmanager.service.ProductCategoryService;
import com.clinic.clinicmanager.service.SupplyHistoryService;
import com.clinic.clinicmanager.utils.SessionUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Iterator;

@Service
@RequiredArgsConstructor
public class VisitSaleService {

    private final ProductRepo productRepo;
    private final VoucherRepo voucherRepo;
    private final VisitPricingCalculator pricingCalculator;
    private final VisitRepo visitRepo;
    private final SupplyHistoryService supplyHistoryService;
    private final ProductCategoryService productCategoryService;

    public void processSale(VisitDTO visit, AppSettings settings, boolean preview) {
        if (visit.getSale() == null) return;
        SaleDTO sale = visit.getSale();

        for (SaleItemDTO item : sale.getItems()) {
            if(!preview) {
                if (item.getProduct() != null) {
                    Product product = productRepo.findOneById(item.getProduct().getId())
                            .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + item.getProduct().getId()));
                    if(item.getPrice() == null) {
                        if(product.getSellingPrice() != null) {
                            item.setPrice(product.getSellingPrice());
                        } else {
                            throw new IllegalStateException("SaleItem Price not defined, Product sellingPrice not set either.");
                        }
                    }

                    item.setName(product.getName());
                    if(product.getSupply() < 1) {
                        throw new IllegalStateException("Product out of stock! ProductId: " + product.getId());
                    }
                    product.setSupply(product.getSupply() - 1);
                    productRepo.save(product);
                    supplyHistoryService.record(product, -1, SupplyChangeSource.VISIT_SALE,
                            visit.getId(), "Wizyta #" + visit.getId());
                }

                if (item.getVoucher() != null) {
                    VoucherDTO voucher = item.getVoucher();
                    voucher.setClient(visit.getClient());
                    voucher.setIssueDate(LocalDate.from(visit.getDate()));
                    voucher.setValue(item.getPrice());
                    voucher.setStatus(VoucherStatus.ACTIVE);
                    voucher.setExpiryDate(
                            voucher.getIssueDate().plusMonths(settings.getVoucherExpiryTime())
                    );
                    Voucher voucherEntity = voucher.toEntity();
                    voucherEntity.setCreatedByUserId(SessionUtils.getUserIdFromSession());
                    Voucher savedVoucher = voucherRepo.save(voucherEntity);
                    voucher.setId(savedVoucher.getId());
                    if (item.getName() == null) item.setName("Voucher");
                }
            }
            pricingCalculator.calculateSaleItemTotals(item);
        }
        pricingCalculator.calculateSaleTotals(sale);
    }
    public void undoSale(Visit visit) {
        Sale sale = visit.getSale();
        if (sale == null) return;

        for (Iterator<SaleItem> it = sale.getItems().iterator(); it.hasNext();) {
            SaleItem item = it.next();

            if (item.getVoucher() != null) {
                Voucher voucher = voucherRepo.findOneById(item.getVoucher().getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Voucher not found with id: " + item.getVoucher().getId()));
                checkSaleItemVouchers(visit);
                voucherRepo.delete(voucher);
            }

            if (item.getProduct() != null) {
                Product product = productRepo.findOneById(item.getProduct().getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + item.getProduct().getId()));

                int supplyBefore = product.getSupply() != null ? product.getSupply() : 0;
                if (product.getIsDeleted()) {
                    productCategoryService.restoreIfHidden(product.getCategory());
                    product.restore(1);
                } else {
                    product.setSupply(product.getSupply() + 1);
                }
                productRepo.save(product);
                supplyHistoryService.record(product, product.getSupply() - supplyBefore,
                        SupplyChangeSource.VISIT_DELETED, visit.getId(), "Wizyta #" + visit.getId());
            }

            it.remove();
        }

        visit.setSale(null);
    }

    public void checkSaleItemVouchers(Visit visit) {
        if(!visit.getSale().getItems().isEmpty()) {
            for(SaleItem saleItem : visit.getSale().getItems()) {
                if(saleItem.getVoucher() != null && saleItem.getVoucher().getStatus() == VoucherStatus.USED) {
                    Visit visitPaidByVoucher = visitRepo.findByVoucherId(saleItem.getVoucher().getId())
                            .orElseThrow(() -> new ResourceNotFoundException("Visit paid by used Voucher not found, voucherId: " + saleItem.getVoucher().getId()));
                    throw new IllegalStateException("Failed to delete Visit, Reason: Voucher from this Visit was used as Payment method for the Visit with Id:" + visitPaidByVoucher.getId());
                }
            }
        }
    }
}
