package com.clinic.clinicmanager.service.impl;

import com.clinic.clinicmanager.DTO.VoucherDTO;
import com.clinic.clinicmanager.DTO.request.DebtFilterDTO;
import com.clinic.clinicmanager.DTO.request.VoucherFilterDTO;
import com.clinic.clinicmanager.exceptions.CreationException;
import com.clinic.clinicmanager.exceptions.DeletionException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.UpdateException;
import com.clinic.clinicmanager.model.AppSettings;
import com.clinic.clinicmanager.model.Voucher;
import com.clinic.clinicmanager.model.constants.VoucherStatus;
import com.clinic.clinicmanager.repo.AppSettingsRepo;
import com.clinic.clinicmanager.repo.SaleItemRepo;
import com.clinic.clinicmanager.repo.VisitRepo;
import com.clinic.clinicmanager.repo.VoucherRepo;
import com.clinic.clinicmanager.service.AuditLogService;
import com.clinic.clinicmanager.service.VoucherService;
import com.clinic.clinicmanager.utils.SessionUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Objects.isNull;

@Service
@RequiredArgsConstructor
public class VoucherServiceImpl  implements VoucherService {

    private final VoucherRepo voucherRepo;
    private final SaleItemRepo saleItemRepo;
    private final AppSettingsRepo settingsRepo;
    private final VisitRepo visitRepo;
    private final AuditLogService auditLogService;
    private final OwnershipService ownershipService;

    @Override
    public VoucherDTO getVoucherById(Long id) {
        return new VoucherDTO(voucherRepo.findOneById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Voucher not found with given id: " + id)));
    }

    @Override
    public List<VoucherDTO> getVouchers(VoucherFilterDTO filter) {
        if(isNull(filter)) {
            filter = new VoucherFilterDTO();
        }

        return voucherRepo.findAllWithFilters(
                filter.getStatus(),
                filter.getKeyword()
                )
                .stream()
                .map(v -> {
                    Long visitId = visitRepo.findPurchaseVisitIdByVoucherId(v.getId());
                    return new VoucherDTO(v, visitId);
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public VoucherDTO createVoucher(VoucherDTO voucher) {
        try{
            AppSettings settings = settingsRepo.getSettings();
            voucher.setStatus(VoucherStatus.ACTIVE);
            if(voucher.getExpiryDate() == null && voucher.getIssueDate() !=null) {
                LocalDate expiryDate = voucher.getIssueDate().plusMonths(settings.getVoucherExpiryTime());
                voucher.setExpiryDate(expiryDate);
            }
            Voucher voucherEntity = voucher.toEntity();
            voucherEntity.setCreatedByUserId(SessionUtils.getUserIdFromSession());
            VoucherDTO savedDTO = new VoucherDTO(voucherRepo.save(voucherEntity));
            auditLogService.logCreate("Voucher", savedDTO.getId(), "Voucher Klienta: " + savedDTO.getClient().getFirstName() + savedDTO.getClient().getLastName(), savedDTO);
            return savedDTO;
        } catch (Exception e) {
            throw new CreationException("Failed to create Voucher. Reason: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public VoucherDTO updateVoucher(Long id, VoucherDTO voucher) {
        try{
            Voucher oldVoucher = voucherRepo.findOneById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Voucher not found with given id: " + id));
            ownershipService.checkOwnershipOrAdmin(oldVoucher.getCreatedByUserId());
            VoucherDTO oldVoucherSnapshot = new VoucherDTO(oldVoucher);
            voucher.setId(id);
            Voucher entityToSave = voucher.toEntity();
            entityToSave.setCreatedByUserId(oldVoucher.getCreatedByUserId());
            VoucherDTO savedDTO = new VoucherDTO(voucherRepo.save(entityToSave));
            auditLogService.logUpdate("Voucher", id, "Voucher Klienta: " + oldVoucherSnapshot.getClient().getFirstName() + oldVoucherSnapshot.getClient().getLastName(), oldVoucherSnapshot, savedDTO);
            return savedDTO;
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new UpdateException("Failed to update Voucher. Reason: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteVoucherById(Long id) {
        try{
            Voucher existingVoucher = voucherRepo.findOneById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Voucher not found with given id: " + id));
            ownershipService.checkOwnershipOrAdmin(existingVoucher.getCreatedByUserId());
            VoucherDTO voucherSnapshot = new VoucherDTO(existingVoucher);
            voucherRepo.deleteById(id);
            auditLogService.logDelete("Voucher", id, "Voucher Klienta: " + voucherSnapshot.getClient().getFirstName() + voucherSnapshot.getClient().getLastName(), voucherSnapshot);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new DeletionException("Failed to delete Voucher. Reason: "+ e.getMessage(), e);
        }
    }

    @Scheduled(cron = "0 0 0 * * ?")
    @Transactional
    public void updateVoucherStatuses() {
        voucherRepo.findAll().forEach(voucher -> {
            VoucherStatus newStatus = recalculateStatus(voucher);
            if (voucher.getStatus() != newStatus) {
                voucher.setStatus(newStatus);
                voucherRepo.save(voucher);
            }
        });
    }

    public VoucherStatus recalculateStatus(Voucher voucher) {
        if (voucher.getStatus() == VoucherStatus.USED) return VoucherStatus.USED;
        if (voucher.getExpiryDate() != null && voucher.getExpiryDate().isBefore(LocalDate.now()))
            return VoucherStatus.EXPIRED;
        return VoucherStatus.ACTIVE;
    }

    @Override
    public Boolean hasSaleReference(Long voucherId) {
        return saleItemRepo.existsByVoucherId(voucherId);
    }
}
