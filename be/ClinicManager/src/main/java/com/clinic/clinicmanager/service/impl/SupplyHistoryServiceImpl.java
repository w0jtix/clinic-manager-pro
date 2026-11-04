package com.clinic.clinicmanager.service.impl;

import com.clinic.clinicmanager.DTO.SupplyChartDTO;
import com.clinic.clinicmanager.DTO.SupplyChartPointDTO;
import com.clinic.clinicmanager.DTO.SupplyHistoryDTO;
import com.clinic.clinicmanager.DTO.SupplySummaryDTO;
import com.clinic.clinicmanager.config.security.services.UserDetailsImpl;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.model.Product;
import com.clinic.clinicmanager.model.ProductSupplyHistory;
import com.clinic.clinicmanager.model.constants.SupplyChangeSource;
import com.clinic.clinicmanager.model.constants.VatRate;
import com.clinic.clinicmanager.repo.CompanyExpenseRepo;
import com.clinic.clinicmanager.repo.InventoryReportRepo;
import com.clinic.clinicmanager.repo.ProductRepo;
import com.clinic.clinicmanager.repo.ProductSupplyHistoryRepo;
import com.clinic.clinicmanager.repo.UsageRecordRepo;
import com.clinic.clinicmanager.repo.VisitRepo;
import com.clinic.clinicmanager.repo.projection.ProductPurchaseProjection;
import com.clinic.clinicmanager.repo.projection.SupplySourceTotalProjection;
import com.clinic.clinicmanager.service.SupplyHistoryService;
import com.clinic.clinicmanager.utils.MoneyUtils;
import com.clinic.clinicmanager.utils.SessionUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SupplyHistoryServiceImpl implements SupplyHistoryService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private static final Set<SupplyChangeSource> IN_SOURCES = EnumSet.of(
            SupplyChangeSource.KSEF_INVOICE,
            SupplyChangeSource.COMPANY_EXPENSE,
            SupplyChangeSource.COMPANY_EXPENSE_DELETED,
            SupplyChangeSource.INITIAL_BALANCE,
            SupplyChangeSource.PRODUCT_CREATED,
            SupplyChangeSource.PRODUCT_RESTORED);

    private static final Set<SupplyChangeSource> EXPENSE_SOURCES = EnumSet.of(
            SupplyChangeSource.COMPANY_EXPENSE,
            SupplyChangeSource.COMPANY_EXPENSE_DELETED);

    private static final Set<SupplyChangeSource> SOLD_SOURCES = EnumSet.of(
            SupplyChangeSource.VISIT_SALE,
            SupplyChangeSource.VISIT_DELETED);

    private static final Set<SupplyChangeSource> USED_SOURCES = EnumSet.of(
            SupplyChangeSource.USAGE_RECORD,
            SupplyChangeSource.USAGE_REVERT);

    private static final Set<SupplyChangeSource> REPORT_SOURCES = EnumSet.of(
            SupplyChangeSource.INVENTORY_REPORT,
            SupplyChangeSource.INVENTORY_REPORT_REVERT);

    private final ProductSupplyHistoryRepo supplyHistoryRepo;
    private final ProductRepo productRepo;
    private final VisitRepo visitRepo;
    private final UsageRecordRepo usageRecordRepo;
    private final InventoryReportRepo inventoryReportRepo;
    private final CompanyExpenseRepo companyExpenseRepo;

    @Override
    public void record(Product product, int delta, SupplyChangeSource source, Long sourceId, String sourceLabel) {
        if (delta == 0) return;

        supplyHistoryRepo.save(ProductSupplyHistory.builder()
                .product(product)
                .delta(delta)
                .supplyAfter(product.getSupply())
                .source(source)
                .sourceId(sourceId)
                .sourceLabel(sourceLabel)
                .occurredAt(LocalDateTime.now())
                .performedBy(getCurrentUsername())
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SupplyHistoryDTO> getHistory(Long productId, List<SupplyChangeSource> sources, Pageable pageable) {
        requireProduct(productId);

        Collection<SupplyChangeSource> sourceFilter =
                (sources == null || sources.isEmpty()) ? null : sources;

        Page<SupplyHistoryDTO> entries = supplyHistoryRepo.findAllByProductId(productId, sourceFilter, pageable)
                .map(SupplyHistoryDTO::new);

        resolveSourceExistence(entries.getContent());

        return entries;
    }

    @Override
    @Transactional(readOnly = true)
    public SupplyChartDTO getChart(Long productId, Integer year) {
        requireProduct(productId);

        int targetYear = year != null ? year : LocalDate.now().getYear();
        LocalDateTime from = LocalDate.of(targetYear, 1, 1).atStartOfDay();
        LocalDateTime to = LocalDate.of(targetYear + 1, 1, 1).atStartOfDay();

        List<Integer> opening = supplyHistoryRepo.findSupplyBefore(productId, from, PageRequest.of(0, 1));

        Map<LocalDate, Integer> lastPerDay = new LinkedHashMap<>();
        for (ProductSupplyHistory entry : supplyHistoryRepo.findAllByProductIdInRange(productId, from, to)) {
            lastPerDay.put(entry.getOccurredAt().toLocalDate(), entry.getSupplyAfter());
        }

        List<SupplyChartPointDTO> points = lastPerDay.entrySet().stream()
                .map(e -> new SupplyChartPointDTO(e.getKey(), e.getValue()))
                .collect(Collectors.toList());

        LocalDateTime first = supplyHistoryRepo.findFirstOccurredAt(productId);
        LocalDateTime last = supplyHistoryRepo.findLastOccurredAt(productId);

        return new SupplyChartDTO(
                opening.isEmpty() ? 0 : opening.get(0),
                first != null ? first.getYear() : targetYear,
                last != null ? last.getYear() : targetYear,
                points);
    }

    @Override
    @Transactional(readOnly = true)
    public SupplySummaryDTO getSummary(Long productId) {
        Product product = requireProduct(productId);

        Map<SupplyChangeSource, Integer> totals = new EnumMap<>(SupplyChangeSource.class);
        for (SupplySourceTotalProjection row : supplyHistoryRepo.sumDeltasBySource(productId)) {
            totals.put(row.getSource(), row.getTotal() != null ? row.getTotal().intValue() : 0);
        }

        int totalIn = sumOf(totals, IN_SOURCES);
        int totalSold = sumOf(totals, SOLD_SOURCES);
        int totalUsed = sumOf(totals, USED_SOURCES);
        int totalAdjustments = totals.entrySet().stream()
                .filter(e -> !IN_SOURCES.contains(e.getKey())
                        && !SOLD_SOURCES.contains(e.getKey())
                        && !USED_SOURCES.contains(e.getKey()))
                .mapToInt(Map.Entry::getValue)
                .sum();

        List<ProductPurchaseProjection> purchases = productRepo.findAllPurchases(productId);

        BigDecimal avgNetPurchasePrice;
        boolean fallbackPriceUsed = false;

        BigDecimal weightedNet = BigDecimal.ZERO;
        BigDecimal totalQuantity = BigDecimal.ZERO;
        for (ProductPurchaseProjection purchase : purchases) {
            int quantity = purchase.getQuantity() != null ? purchase.getQuantity() : 0;
            if (quantity <= 0) continue;
            BigDecimal quantityValue = BigDecimal.valueOf(quantity);
            weightedNet = weightedNet.add(resolvePurchaseNetPrice(purchase).multiply(quantityValue));
            totalQuantity = totalQuantity.add(quantityValue);
        }

        if (totalQuantity.signum() > 0) {
            avgNetPurchasePrice = MoneyUtils.money(
                    weightedNet.divide(totalQuantity, MoneyUtils.PRICE_SCALE, MoneyUtils.ROUNDING));
        } else if (product.getFallbackNetPurchasePrice() != null) {
            avgNetPurchasePrice = MoneyUtils.money(product.getFallbackNetPurchasePrice());
            fallbackPriceUsed = true;
        } else {
            avgNetPurchasePrice = MoneyUtils.money(BigDecimal.ZERO);
        }

        int currentSupply = product.getSupply() != null ? product.getSupply() : 0;
        BigDecimal stockValue = MoneyUtils.money(avgNetPurchasePrice.multiply(BigDecimal.valueOf(currentSupply)));

        return new SupplySummaryDTO(
                currentSupply,
                totalIn,
                totalSold,
                totalUsed,
                totalAdjustments,
                avgNetPurchasePrice,
                fallbackPriceUsed,
                stockValue);
    }

    private Product requireProduct(Long productId) {
        return productRepo.findByIdIncludingDeleted(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with given id: " + productId));
    }

    private int sumOf(Map<SupplyChangeSource, Integer> totals, Set<SupplyChangeSource> sources) {
        return sources.stream()
                .mapToInt(source -> totals.getOrDefault(source, 0))
                .sum();
    }

    private void resolveSourceExistence(List<SupplyHistoryDTO> entries) {
        Set<Long> existingVisits = fetchExistingIds(
                collectSourceIds(entries, SOLD_SOURCES), visitRepo::findExistingIds);
        Set<Long> existingUsages = fetchExistingIds(
                collectSourceIds(entries, USED_SOURCES), usageRecordRepo::findExistingIds);
        Set<Long> existingReports = fetchExistingIds(
                collectSourceIds(entries, REPORT_SOURCES), inventoryReportRepo::findExistingIds);
        Set<Long> existingExpenses = fetchExistingIds(
                collectSourceIds(entries, EXPENSE_SOURCES), companyExpenseRepo::findExistingIds);

        for (SupplyHistoryDTO entry : entries) {
            if (EXPENSE_SOURCES.contains(entry.getSource())) {
                entry.setSourceExists(entry.getSourceId() != null && existingExpenses.contains(entry.getSourceId()));
            } else if (SOLD_SOURCES.contains(entry.getSource())) {
                entry.setSourceExists(entry.getSourceId() != null && existingVisits.contains(entry.getSourceId()));
            } else if (USED_SOURCES.contains(entry.getSource())) {
                entry.setSourceExists(entry.getSourceId() != null && existingUsages.contains(entry.getSourceId()));
            } else if (REPORT_SOURCES.contains(entry.getSource())) {
                entry.setSourceExists(entry.getSourceId() != null && existingReports.contains(entry.getSourceId()));
            } else {
                entry.setSourceExists(true);
            }
        }
    }

    private Set<Long> collectSourceIds(List<SupplyHistoryDTO> entries, Set<SupplyChangeSource> sources) {
        return entries.stream()
                .filter(entry -> sources.contains(entry.getSource()) && entry.getSourceId() != null)
                .map(SupplyHistoryDTO::getSourceId)
                .collect(Collectors.toSet());
    }

    private Set<Long> fetchExistingIds(Set<Long> ids, Function<Collection<Long>, Set<Long>> loader) {
        return ids.isEmpty() ? Set.of() : loader.apply(ids);
    }

    private BigDecimal resolvePurchaseNetPrice(ProductPurchaseProjection purchase) {
        if (purchase.getUnitNetPrice() != null) {
            return purchase.getUnitNetPrice();
        }
        BigDecimal gross = MoneyUtils.nz(purchase.getUnitGrossPrice());
        BigDecimal rate = MoneyUtils.vatPercent(parseVatRate(purchase.getVatRate()));
        return gross.multiply(HUNDRED).divide(HUNDRED.add(rate), MoneyUtils.PRICE_SCALE, MoneyUtils.ROUNDING);
    }

    private VatRate parseVatRate(String vatRate) {
        if (vatRate == null) return null;
        try {
            return VatRate.valueOf(vatRate);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String getCurrentUsername() {
        UserDetailsImpl userDetails = SessionUtils.getUserDetailsFromSession();
        if (userDetails != null) {
            return userDetails.getUsername();
        }
        return "SYSTEM";
    }
}
