package com.clinic.clinicmanager.service.impl;

import com.clinic.clinicmanager.DTO.KsefCorrectionMatchDTO;
import com.clinic.clinicmanager.DTO.KsefCorrectionRefDTO;
import com.clinic.clinicmanager.DTO.KsefInvoiceDTO;
import com.clinic.clinicmanager.DTO.KsefInvoiceItemDTO;
import com.clinic.clinicmanager.DTO.StockChangePreviewDTO;
import com.clinic.clinicmanager.DTO.StockChangeProductDTO;
import com.clinic.clinicmanager.DTO.request.KsefBookCorrectionItemDTO;
import com.clinic.clinicmanager.DTO.request.KsefBookCorrectionRequestDTO;
import com.clinic.clinicmanager.DTO.request.KsefInvoiceFilterDTO;
import com.clinic.clinicmanager.DTO.request.KsefVerifyProductsItemDTO;
import com.clinic.clinicmanager.DTO.request.KsefVerifyProductsRequestDTO;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.StockChangeBlockedException;
import com.clinic.clinicmanager.model.KsefInvoice;
import com.clinic.clinicmanager.model.KsefInvoiceItem;
import com.clinic.clinicmanager.model.KsefSeller;
import com.clinic.clinicmanager.model.Product;
import com.clinic.clinicmanager.model.constants.ExpenseCategoryCode;
import com.clinic.clinicmanager.model.constants.SupplyChangeSource;
import com.clinic.clinicmanager.repo.KsefInvoiceRepo;
import com.clinic.clinicmanager.repo.ProductRepo;
import com.clinic.clinicmanager.service.AuditLogService;
import com.clinic.clinicmanager.service.ExpenseCategoryService;
import com.clinic.clinicmanager.service.KsefInvoiceService;
import com.clinic.clinicmanager.service.KsefSellerItemMappingService;
import com.clinic.clinicmanager.service.KsefSellerService;
import com.clinic.clinicmanager.service.SupplyHistoryService;
import com.clinic.clinicmanager.utils.KsefCorrectionMatcher;
import com.clinic.clinicmanager.utils.KsefCorrectionUtils;
import com.clinic.clinicmanager.utils.MoneyUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Objects.isNull;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KsefInvoiceServiceImpl implements KsefInvoiceService {

    private static final Set<String> KSEF_FORBIDDEN_CATEGORIES = Set.of(
            ExpenseCategoryCode.RENT,
            ExpenseCategoryCode.TAXES,
            ExpenseCategoryCode.ZUS,
            ExpenseCategoryCode.SALARY,
            ExpenseCategoryCode.MIXED);

    private final KsefInvoiceRepo invoiceRepo;
    private final ProductRepo productRepo;
    private final AuditLogService auditLogService;
    private final KsefSellerItemMappingService mappingService;
    private final KsefSellerService sellerService;
    private final SupplyHistoryService supplyHistoryService;
    private final ExpenseCategoryService expenseCategoryService;

    @Override
    public Page<KsefInvoiceDTO> getInvoices(KsefInvoiceFilterDTO filter, int page, int size) {
        if (isNull(filter)) {
            filter = new KsefInvoiceFilterDTO();
        }

        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("issueDate"), Sort.Order.desc("id")));

        LocalDate dateFrom = null;
        LocalDate dateTo = null;
        if (!isNull(filter.getYear()) && !isNull(filter.getMonth())) {
            dateFrom = LocalDate.of(filter.getYear(), filter.getMonth(), 1);
            dateTo = dateFrom.withDayOfMonth(dateFrom.lengthOfMonth());
        }

        List<String> categories = filter.getCategories();
        List<String> cats = (categories == null || categories.isEmpty()) ? null : categories;
        boolean hasCategories = cats != null;
        boolean wantUncategorized = Boolean.TRUE.equals(filter.getUncategorized());
        boolean wantAwaiting = Boolean.TRUE.equals(filter.getAwaitingVerification());
        boolean noCategoryFilter = !hasCategories && !wantUncategorized && !wantAwaiting;

        Collection<String> categoriesParam = hasCategories ? cats : List.of("");

        return invoiceRepo.findAllWithFilters(
                        dateFrom,
                        dateTo,
                        filter.getSellerId(),
                        filter.getIsPaid(),
                        noCategoryFilter,
                        hasCategories,
                        categoriesParam,
                        wantUncategorized,
                        wantAwaiting,
                        pageable)
                .map(KsefInvoiceDTO::new);
    }

    @Override
    public KsefInvoiceDTO getInvoiceById(Long id) {
        KsefInvoice invoice = invoiceRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("KSeF invoice not found: " + id));

        KsefInvoiceDTO dto = new KsefInvoiceDTO(invoice, true);

        List<KsefCorrectionRefDTO> corr = Objects.equals(invoice.getInvoiceType(), "VAT")
                ? invoiceRepo.findCorrectionsByInvoiceId(id).stream()
                .map(KsefCorrectionRefDTO::new)
                .toList()
                : List.of();

        dto.setCorrections(corr);

        KsefSeller seller = invoice.getSeller();
        Long sellerId = seller != null ? seller.getId() : null;
        if (sellerId != null) {
            Map<Long, KsefInvoiceItem> itemsById = invoice.getItems().stream()
                    .collect(Collectors.toMap(KsefInvoiceItem::getId, Function.identity()));
            dto.getItems().forEach(itemDto -> {
                if (itemDto.getProduct() == null) {
                    KsefInvoiceItem item = itemsById.get(itemDto.getId());
                    if (item != null) {
                        mappingService.findFor(sellerId, item.getKsefItemName(), item.getKsefGtin())
                                .ifPresent(m -> itemDto.setSuggestedProductId(m.getProduct().getId()));
                    }
                }
            });
        }

        if (isCorrection(invoice) && invoice.getCorrectedInvoiceId() != null) {
            invoiceRepo.findById(invoice.getCorrectedInvoiceId())
                    .ifPresent(parent -> attachCorrectionMatches(dto, invoice, parent));
        }
        return dto;
    }

    private void attachCorrectionMatches(KsefInvoiceDTO dto, KsefInvoice correction, KsefInvoice parent) {
        List<KsefInvoiceItem> items = correction.getItems();
        List<KsefInvoiceItemDTO> itemDtos = dto.getItems();
        for (int i = 0; i < items.size() && i < itemDtos.size(); i++) {
            KsefInvoiceItem item = items.get(i);
            KsefCorrectionMatcher.Match match = KsefCorrectionMatcher.match(item, parent.getItems());
            itemDtos.get(i).setCorrectionMatch(new KsefCorrectionMatchDTO(item, match));
        }
    }

    @Override
    @Transactional
    public KsefInvoiceDTO togglePaidStatus(Long id) {
        KsefInvoice invoice = invoiceRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("KSeF invoice not found: " + id));

        KsefInvoiceDTO oldSnapshot = new KsefInvoiceDTO(invoice);
        invoice.setIsPaid(!Boolean.TRUE.equals(invoice.getIsPaid()));
        KsefInvoice saved = invoiceRepo.save(invoice);
        KsefInvoiceDTO newSnapshot = new KsefInvoiceDTO(saved);

        auditLogService.logUpdate("KsefInvoice", saved.getId(),
                saved.getInvoiceNumber(), oldSnapshot, newSnapshot);

        return newSnapshot;
    }

    @Override
    @Transactional
    public KsefInvoiceDTO toggleProductInvoice(Long id) {
        KsefInvoice invoice = invoiceRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("KSeF invoice not found: " + id));

        if (isCorrection(invoice)) {
            throw new ConflictException("A correction inherits its category from the original invoice");
        }
        if (ExpenseCategoryCode.PRODUCTS.equals(invoice.getCategory())) {
            throw new ConflictException("Cannot toggle a verified products invoice");
        }

        KsefInvoiceDTO oldSnapshot = new KsefInvoiceDTO(invoice);
        boolean nowAwaiting = !invoice.isAwaitingProductVerification();
        invoice.setAwaitingProductVerification(nowAwaiting);
        if (nowAwaiting) {
            invoice.setCategory(null);
        }
        KsefInvoice saved = invoiceRepo.save(invoice);
        propagateToCorrections(saved);

        KsefInvoiceDTO newSnapshot = new KsefInvoiceDTO(saved);
        auditLogService.logUpdate("KsefInvoice (CE)", saved.getId(),
                saved.getInvoiceNumber(), oldSnapshot, newSnapshot);

        return newSnapshot;
    }

    @Override
    @Transactional
    public KsefInvoiceDTO verifyProducts(Long id, KsefVerifyProductsRequestDTO request) {
        KsefInvoice invoice = invoiceRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("KSeF invoice not found: " + id));

        if (isCorrection(invoice)) {
            throw new ConflictException("Use book-correction to book a correction invoice");
        }
        if (!invoice.isAwaitingProductVerification()) {
            throw new ConflictException("Invoice must be awaiting product verification to verify");
        }

        KsefInvoiceDTO oldSnapshot = new KsefInvoiceDTO(invoice, true);

        Map<Long, KsefInvoiceItem> itemsById = invoice.getItems().stream()
                .collect(Collectors.toMap(KsefInvoiceItem::getId, Function.identity()));

        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new ConflictException("Verify products request must contain at least one item");
        }
        List<KsefVerifyProductsItemDTO> payloadItems = request.getItems();

        KsefSeller seller = invoice.getSeller();

        for (KsefVerifyProductsItemDTO payload : payloadItems) {
            KsefInvoiceItem item = itemsById.get(payload.getItemId());
            if (item == null) {
                throw new ConflictException(
                        "Item " + payload.getItemId() + " does not belong to invoice " + id);
            }
            if (payload.getProductId() == null) continue;

            Integer quantity = payload.getQuantity();
            if (quantity == null || quantity <= 0) {
                throw new ConflictException(
                        "Quantity must be positive for item " + payload.getItemId());
            } else if (item.getQuantity() != null
                    && BigDecimal.valueOf(quantity).compareTo(item.getQuantity()) < 0) {
                throw new ConflictException(
                        "Declared quantity must be greater or equal to InvoiceItem quantity.");
            }

            Product product = productRepo.findOneById(payload.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Product not found: " + payload.getProductId()));
            if (Boolean.TRUE.equals(product.getIsDeleted())) {
                throw new ConflictException(
                        "Cannot assign deleted product: " + product.getId());
            }

            item.setProduct(product);
            item.setDeclaredQuantity(quantity);
            product.addToSupply(quantity);
            supplyHistoryService.record(product, quantity, SupplyChangeSource.KSEF_INVOICE,
                    invoice.getId(), invoice.getInvoiceNumber());

            if (Boolean.TRUE.equals(product.getCategory().getIsSystem())) {
                applyPurchasePrice(product, item, quantity);
            }

            if (payload.isRememberForSeller() && seller != null) {
                mappingService.saveOrUpdate(seller, item.getKsefItemName(), item.getKsefGtin(), product);
            }
        }

        if (request.isRememberSeller() && seller != null) {
            sellerService.setDefaultCategory(seller, ExpenseCategoryCode.PRODUCTS);
        }

        invoice.setAwaitingProductVerification(false);
        invoice.setCategory(ExpenseCategoryCode.PRODUCTS);
        KsefInvoice saved = invoiceRepo.save(invoice);
        propagateToCorrections(saved);

        KsefInvoiceDTO newSnapshot = new KsefInvoiceDTO(saved, true);
        auditLogService.logUpdate("KsefInvoice (CE)", saved.getId(),
                saved.getInvoiceNumber(), oldSnapshot, newSnapshot);

        return newSnapshot;
    }

    private void propagateToCorrections(KsefInvoice parent) {
        List<KsefInvoice> corrections = invoiceRepo.findByCorrectedInvoiceId(parent.getId());
        for (KsefInvoice correction : corrections) {
            KsefCorrectionUtils.applyParentCategory(correction, parent);
            invoiceRepo.save(correction);
        }
    }

    @Override
    public StockChangePreviewDTO previewCorrectionBooking(Long id, KsefBookCorrectionRequestDTO request) {
        KsefInvoice invoice = findBookableCorrection(id);
        List<CorrectionLine> lines = resolveCorrectionLines(invoice, request);
        Map<Long, Integer> quantities = sumSignedQuantities(lines);
        List<Product> products = quantities.isEmpty()
                ? List.of()
                : productRepo.findAllById(quantities.keySet());

        validateCorrectionProducts(quantities.keySet(), products);
        return buildCorrectionStockPreview(products, quantities);
    }

    @Override
    @Transactional
    public KsefInvoiceDTO bookCorrection(Long id, KsefBookCorrectionRequestDTO request) {
        KsefInvoice invoice = findBookableCorrection(id);

        KsefInvoiceDTO oldSnapshot = new KsefInvoiceDTO(invoice, true);

        List<CorrectionLine> lines = resolveCorrectionLines(invoice, request);
        Map<Long, Integer> quantities = sumSignedQuantities(lines);
        List<Product> lockedProducts = quantities.isEmpty()
                ? List.of()
                : productRepo.findAllByIdInForUpdate(quantities.keySet());

        Map<Long, Product> productsById = validateCorrectionProducts(quantities.keySet(), lockedProducts);
        StockChangePreviewDTO preview = buildCorrectionStockPreview(lockedProducts, quantities);
        if (!preview.isCanApply()) {
            throw new StockChangeBlockedException(
                    "Insufficient supply to book KSeF correction " + id, preview);
        }

        for (CorrectionLine line : lines) {
            line.item().setProduct(productsById.get(line.productId()));
        }

        for (Product product : lockedProducts) {
            int delta = quantities.get(product.getId());
            if (delta == 0) continue;
            if (delta < 0) {
                product.reduceSupply(-delta);
            } else {
                product.addToSupply(delta);
            }
            supplyHistoryService.record(product, delta, SupplyChangeSource.KSEF_CORRECTION,
                    invoice.getId(), invoice.getInvoiceNumber());
        }

        invoice.setAwaitingProductVerification(false);
        invoice.setCategory(ExpenseCategoryCode.PRODUCTS);
        KsefInvoice saved = invoiceRepo.save(invoice);

        KsefInvoiceDTO newSnapshot = new KsefInvoiceDTO(saved, true);
        auditLogService.logUpdate("KsefInvoice (CE)", saved.getId(),
                saved.getInvoiceNumber(), oldSnapshot, newSnapshot);

        return newSnapshot;
    }

    private record CorrectionLine(KsefInvoiceItem item, Long productId, int signedQuantity) {
    }

    private KsefInvoice findBookableCorrection(Long id) {
        KsefInvoice invoice = invoiceRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("KSeF invoice not found: " + id));

        if (!isCorrection(invoice)) {
            throw new ConflictException("Invoice " + id + " is not a correction");
        }
        if (invoice.getCorrectedInvoiceId() == null) {
            throw new ConflictException("Correction is not linked to an original invoice");
        }
        if (!invoice.isAwaitingProductVerification()) {
            throw new ConflictException("Correction is not awaiting product booking");
        }
        return invoice;
    }

    private List<CorrectionLine> resolveCorrectionLines(KsefInvoice invoice, KsefBookCorrectionRequestDTO request) {
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new ConflictException("Book correction request must contain at least one item");
        }

        Map<Long, KsefInvoiceItem> itemsById = invoice.getItems().stream()
                .collect(Collectors.toMap(KsefInvoiceItem::getId, Function.identity()));
        Set<Long> seenItemIds = new HashSet<>();
        List<CorrectionLine> lines = new ArrayList<>();

        for (KsefBookCorrectionItemDTO payload : request.getItems()) {
            KsefInvoiceItem item = itemsById.get(payload.getItemId());
            if (item == null) {
                throw new ConflictException(
                        "Item " + payload.getItemId() + " does not belong to invoice " + invoice.getId());
            }
            if (!seenItemIds.add(item.getId())) {
                throw new ConflictException("Item " + item.getId() + " appears more than once in the request");
            }
            if (payload.getProductId() == null) continue;

            Integer quantity = payload.getQuantity();
            if (quantity == null || quantity < 0) {
                throw new ConflictException(
                        "Quantity must not be negative for item " + payload.getItemId());
            }

            int direction = item.getQuantity() != null ? item.getQuantity().signum() : 0;
            if (direction == 0 && quantity != 0) {
                throw new ConflictException(
                        "Item " + payload.getItemId() + " has no quantity change - quantity must be 0");
            }

            lines.add(new CorrectionLine(item, payload.getProductId(), direction * quantity));
        }
        return lines;
    }

    private Map<Long, Integer> sumSignedQuantities(List<CorrectionLine> lines) {
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        for (CorrectionLine line : lines) {
            quantities.merge(line.productId(), line.signedQuantity(), Integer::sum);
        }
        return quantities;
    }

    private Map<Long, Product> validateCorrectionProducts(Collection<Long> productIds, List<Product> products) {
        Map<Long, Product> productsById = products.stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        for (Long productId : productIds) {
            Product product = productsById.get(productId);
            if (product == null) {
                throw new ResourceNotFoundException("Product not found: " + productId);
            }
            if (Boolean.TRUE.equals(product.getIsDeleted())) {
                throw new ConflictException("Cannot assign deleted product: " + productId);
            }
        }
        return productsById;
    }

    private StockChangePreviewDTO buildCorrectionStockPreview(List<Product> products,
                                                              Map<Long, Integer> quantities) {
        List<StockChangeProductDTO> productPreviews = products.stream()
                .map(product -> toCorrectionStockProduct(product, quantities.get(product.getId())))
                .sorted(Comparator.comparing(StockChangeProductDTO::getProductName,
                        String.CASE_INSENSITIVE_ORDER))
                .toList();
        boolean canApply = productPreviews.stream().allMatch(StockChangeProductDTO::isSufficient);
        return new StockChangePreviewDTO(canApply, productPreviews);
    }

    private StockChangeProductDTO toCorrectionStockProduct(Product product, int quantity) {
        int currentSupply = product.getSupply();
        int supplyAfter = currentSupply + quantity;
        return new StockChangeProductDTO(
                product.getId(),
                product.getName(),
                quantity,
                currentSupply,
                supplyAfter,
                quantity >= 0 || supplyAfter >= 0,
                false);
    }

    private boolean isCorrection(KsefInvoice invoice) {
        return invoice.getInvoiceType() != null && invoice.getInvoiceType().startsWith("KOR");
    }

    private void applyPurchasePrice(Product product, KsefInvoiceItem item, int quantity) {
        BigDecimal netPricePerUnit = null;
        if (item.getNetValue() != null) {
            netPricePerUnit = item.getNetValue()
                    .divide(BigDecimal.valueOf(quantity), MoneyUtils.PRICE_SCALE, MoneyUtils.ROUNDING);
        } else if (item.getUnitNetPrice() != null) {
            netPricePerUnit = item.getUnitNetPrice();
        }

        if (netPricePerUnit == null) {
            return;
        }

        product.setFallbackNetPurchasePrice(MoneyUtils.price(netPricePerUnit));
        product.setFallbackVatRate(item.getVatRate());
    }

    @Override
    @Transactional
    public KsefInvoiceDTO setCategory(Long id, String category, boolean rememberForSeller) {
        expenseCategoryService.requireExistingCode(category);

        if (ExpenseCategoryCode.PRODUCTS.equals(category)) {
            throw new ConflictException("Category PRODUCTS can only be set through product verification");
        }

        if (KSEF_FORBIDDEN_CATEGORIES.contains(category)) {
            throw new ConflictException("Category " + category + " cannot be assigned to a KSeF invoice");
        }

        KsefInvoice invoice = invoiceRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("KSeF invoice not found: " + id));

        if (isCorrection(invoice)) {
            throw new ConflictException("A correction inherits its category from the original invoice");
        }
        if (ExpenseCategoryCode.PRODUCTS.equals(invoice.getCategory())) {
            throw new ConflictException("Category is locked for a verified products invoice");
        }

        KsefInvoiceDTO oldSnapshot = new KsefInvoiceDTO(invoice);
        invoice.setCategory(category);
        invoice.setAwaitingProductVerification(false);

        KsefSeller seller = invoice.getSeller();
        if (rememberForSeller && seller != null) {
            sellerService.setDefaultCategory(seller, category);
        }

        KsefInvoice saved = invoiceRepo.save(invoice);
        propagateToCorrections(saved);
        KsefInvoiceDTO newSnapshot = new KsefInvoiceDTO(saved);

        auditLogService.logUpdate("KsefInvoice (CE)", saved.getId(),
                saved.getInvoiceNumber(), oldSnapshot, newSnapshot);

        return newSnapshot;
    }

}
