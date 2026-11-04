package com.clinic.clinicmanager.service.impl;

import com.clinic.clinicmanager.DTO.CompanyExpenseDTO;
import com.clinic.clinicmanager.DTO.KsefCategoryFacetDTO;
import com.clinic.clinicmanager.DTO.KsefSellerDTO;
import com.clinic.clinicmanager.DTO.KsefSellerItemMappingDTO;
import com.clinic.clinicmanager.DTO.KsefSellerSummaryDTO;
import com.clinic.clinicmanager.DTO.KsefSpendChartDTO;
import com.clinic.clinicmanager.DTO.KsefSpendChartPointDTO;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.UpdateException;
import com.clinic.clinicmanager.model.KsefSeller;
import com.clinic.clinicmanager.model.KsefSellerItemMapping;
import com.clinic.clinicmanager.model.constants.ExpenseCategoryCode;
import com.clinic.clinicmanager.repo.CompanyExpenseRepo;
import com.clinic.clinicmanager.repo.KsefInvoiceRepo;
import com.clinic.clinicmanager.repo.KsefSellerItemMappingRepo;
import com.clinic.clinicmanager.repo.KsefSellerRepo;
import com.clinic.clinicmanager.repo.projection.KsefCategoryFacetProjection;
import com.clinic.clinicmanager.repo.projection.KsefMonthlySpendProjection;
import com.clinic.clinicmanager.repo.projection.KsefSellerSummaryProjection;
import com.clinic.clinicmanager.repo.projection.SellerManualExpenseSummaryProjection;
import com.clinic.clinicmanager.service.AuditLogService;
import com.clinic.clinicmanager.service.ExpenseCategoryService;
import com.clinic.clinicmanager.service.KsefSellerService;
import com.clinic.clinicmanager.utils.NipUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KsefSellerServiceImpl implements KsefSellerService {

    private static final Set<String> FORBIDDEN_DEFAULT_CATEGORIES = Set.of(
            ExpenseCategoryCode.RENT,
            ExpenseCategoryCode.TAXES,
            ExpenseCategoryCode.ZUS,
            ExpenseCategoryCode.SALARY,
            ExpenseCategoryCode.MIXED);

    private static final int MONTHS_IN_YEAR = 12;

    private final KsefSellerRepo sellerRepo;
    private final KsefInvoiceRepo invoiceRepo;
    private final KsefSellerItemMappingRepo mappingRepo;
    private final CompanyExpenseRepo companyExpenseRepo;
    private final AuditLogService auditLogService;
    private final ExpenseCategoryService expenseCategoryService;

    @Override
    public KsefSellerDTO getSellerById(Long id) {
        return new KsefSellerDTO(requireSeller(id));
    }

    @Override
    public KsefSellerDTO getSellerByNip(String nip) {
        String normalizedNip = NipUtils.normalizeTaxId(nip);
        return sellerRepo.findByNip(normalizedNip)
                .map(KsefSellerDTO::new)
                .orElseThrow(() -> new ResourceNotFoundException("KSeF seller not found with NIP: " + normalizedNip));
    }

    @Override
    public KsefSellerSummaryDTO getSummary(Long id) {
        requireSeller(id);

        KsefSellerSummaryProjection totals = invoiceRepo.summarizeBySellerId(id);
        SellerManualExpenseSummaryProjection manualTotals = companyExpenseRepo.summarizeBySellerId(id);

        List<KsefCategoryFacetDTO> facets = new ArrayList<>();
        for (KsefCategoryFacetProjection facet : invoiceRepo.countByCategoryForSeller(id)) {
            facets.add(new KsefCategoryFacetDTO(facet.getCategory(), count(facet.getCnt())));
        }

        return new KsefSellerSummaryDTO(
                totals != null ? count(totals.getInvoiceCount()) : 0L,
                totals != null && totals.getTotalGross() != null ? totals.getTotalGross() : BigDecimal.ZERO,
                totals != null ? count(totals.getUnpaidCount()) : 0L,
                totals != null ? count(totals.getAwaitingVerificationCount()) : 0L,
                totals != null ? count(totals.getUncategorizedCount()) : 0L,
                facets,
                manualTotals != null ? count(manualTotals.getExpenseCount()) : 0L,
                manualTotals != null && manualTotals.getTotalValue() != null
                        ? manualTotals.getTotalValue() : BigDecimal.ZERO);
    }

    @Override
    public KsefSpendChartDTO getSpendChart(Long id, Integer year) {
        requireSeller(id);

        int targetYear = year != null ? year : LocalDate.now().getYear();
        LocalDate from = LocalDate.of(targetYear, 1, 1);
        LocalDate to = LocalDate.of(targetYear, 12, 31);

        Map<Integer, KsefMonthlySpendProjection> byMonth = new HashMap<>();
        for (KsefMonthlySpendProjection row : invoiceRepo.sumMonthlySpendBySellerId(id, from, to)) {
            if (row.getMonth() != null) {
                byMonth.put(row.getMonth().intValue(), row);
            }
        }

        Map<Integer, KsefMonthlySpendProjection> manualByMonth = new HashMap<>();
        for (KsefMonthlySpendProjection row : companyExpenseRepo.sumMonthlySpendBySellerId(id, from, to)) {
            if (row.getMonth() != null) {
                manualByMonth.put(row.getMonth().intValue(), row);
            }
        }

        List<KsefSpendChartPointDTO> points = new ArrayList<>(MONTHS_IN_YEAR);
        for (int month = 1; month <= MONTHS_IN_YEAR; month++) {
            KsefMonthlySpendProjection row = byMonth.get(month);
            KsefMonthlySpendProjection manualRow = manualByMonth.get(month);
            points.add(new KsefSpendChartPointDTO(
                    month,
                    gross(row).add(gross(manualRow)),
                    row != null ? count(row.getCnt()) : 0L,
                    manualRow != null ? count(manualRow.getCnt()) : 0L));
        }

        LocalDate first = earliest(invoiceRepo.findFirstIssueDateBySellerId(id),
                companyExpenseRepo.findFirstExpenseDateBySellerId(id));
        LocalDate last = latest(invoiceRepo.findLastIssueDateBySellerId(id),
                companyExpenseRepo.findLastExpenseDateBySellerId(id));

        return new KsefSpendChartDTO(
                first != null ? first.getYear() : LocalDate.now().getYear(),
                last != null ? last.getYear() : LocalDate.now().getYear(),
                points);
    }

    @Override
    public Page<CompanyExpenseDTO> getManualExpenses(Long id, int page, int size) {
        requireSeller(id);

        return companyExpenseRepo.findBySellerId(id, PageRequest.of(page, size))
                .map(CompanyExpenseDTO::new);
    }

    @Override
    public List<KsefSellerItemMappingDTO> getItemMappings(Long sellerId) {
        requireSeller(sellerId);

        return mappingRepo.findBySellerIdWithProduct(sellerId).stream()
                .map(KsefSellerItemMappingDTO::new)
                .toList();
    }

    @Override
    @Transactional
    public void deleteItemMapping(Long sellerId, Long mappingId) {
        requireSeller(sellerId);

        KsefSellerItemMapping mapping = mappingRepo.findById(mappingId)
                .filter(m -> m.getSeller() != null && sellerId.equals(m.getSeller().getId()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "KSeF item mapping not found: " + mappingId));

        KsefSellerItemMappingDTO deletedSnapshot = new KsefSellerItemMappingDTO(mapping);
        mappingRepo.delete(mapping);

        auditLogService.logDelete("KsefSellerItemMapping", mappingId,
                mapping.getKsefItemName(), deletedSnapshot);
    }

    @Override
    @Transactional
    public void removeAutoMark(Long id) {
        setDefaultCategory(requireSeller(id), null);
    }

    @Override
    @Transactional
    public void setDefaultCategory(Long id, String category) {
        setDefaultCategory(requireSeller(id), category);
    }

    @Override
    @Transactional
    public void setDefaultCategory(KsefSeller seller, String category) {
        if (seller == null) {
            return;
        }
        if (category != null && FORBIDDEN_DEFAULT_CATEGORIES.contains(category)) {
            throw new UpdateException("Category " + category + " cannot be a KSeF seller default");
        }
        if (category != null) {
            expenseCategoryService.requireExistingCode(category);
        }

        boolean autoMarkProducts = ExpenseCategoryCode.PRODUCTS.equals(category);
        if (Objects.equals(seller.getDefaultExpenseCategory(), category)
                && Boolean.valueOf(autoMarkProducts).equals(seller.getAutoMarkProducts())) {
            return;
        }

        KsefSellerDTO oldSnapshot = new KsefSellerDTO(seller);
        seller.setDefaultExpenseCategory(category);
        seller.setAutoMarkProducts(autoMarkProducts);
        KsefSeller saved = sellerRepo.save(seller);
        KsefSellerDTO newSnapshot = new KsefSellerDTO(saved);

        String identifier = saved.getName() != null ? saved.getName() : saved.getNip();
        auditLogService.logUpdate("KsefSeller", saved.getId(), identifier, oldSnapshot, newSnapshot);
    }

    private KsefSeller requireSeller(Long id) {
        return sellerRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("KSeF seller not found: " + id));
    }

    private long count(Long value) {
        return value != null ? value : 0L;
    }

    private BigDecimal gross(KsefMonthlySpendProjection row) {
        return row != null && row.getGross() != null ? row.getGross() : BigDecimal.ZERO;
    }

    private LocalDate earliest(LocalDate a, LocalDate b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.isBefore(b) ? a : b;
    }

    private LocalDate latest(LocalDate a, LocalDate b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.isAfter(b) ? a : b;
    }
}
