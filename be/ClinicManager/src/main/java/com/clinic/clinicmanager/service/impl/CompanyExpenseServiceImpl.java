package com.clinic.clinicmanager.service.impl;

import com.clinic.clinicmanager.DTO.CompanyExpenseDTO;
import com.clinic.clinicmanager.DTO.CompanyExpenseItemDTO;
import com.clinic.clinicmanager.DTO.ExchangeRateDTO;
import com.clinic.clinicmanager.DTO.ExpenseSellerDTO;
import com.clinic.clinicmanager.DTO.KsefSellerDTO;
import com.clinic.clinicmanager.DTO.StockChangePreviewDTO;
import com.clinic.clinicmanager.DTO.StockChangeProductDTO;
import com.clinic.clinicmanager.DTO.request.CompanyExpenseFilterDTO;
import com.clinic.clinicmanager.DTO.request.CompanyExpenseUpdateDTO;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.CreationException;
import com.clinic.clinicmanager.exceptions.DeletionException;
import com.clinic.clinicmanager.exceptions.ExternalServiceUnavailableException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.StockChangeBlockedException;
import com.clinic.clinicmanager.exceptions.UpdateException;
import com.clinic.clinicmanager.exceptions.ValidationException;
import com.clinic.clinicmanager.model.*;
import com.clinic.clinicmanager.model.constants.ExpenseCategoryCode;
import com.clinic.clinicmanager.model.constants.ExpenseCurrency;
import com.clinic.clinicmanager.model.constants.SupplyChangeSource;
import com.clinic.clinicmanager.repo.CompanyExpenseItemRepo;
import com.clinic.clinicmanager.repo.CompanyExpenseRepo;
import com.clinic.clinicmanager.repo.EmployeeRepo;
import com.clinic.clinicmanager.repo.KsefSellerRepo;
import com.clinic.clinicmanager.repo.ProductRepo;
import com.clinic.clinicmanager.repo.projection.UnifiedExpenseProjection;
import com.clinic.clinicmanager.service.AuditLogService;
import com.clinic.clinicmanager.service.CompanyExpenseService;
import com.clinic.clinicmanager.service.ExchangeRateService;
import com.clinic.clinicmanager.service.ExpenseCategoryService;
import com.clinic.clinicmanager.service.SupplyHistoryService;
import com.clinic.clinicmanager.utils.NipUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static java.util.Objects.isNull;

@Service
@RequiredArgsConstructor
public class CompanyExpenseServiceImpl implements CompanyExpenseService {

    private static final Set<String> SELLERLESS_CATEGORIES = Set.of(
            ExpenseCategoryCode.SALARY,
            ExpenseCategoryCode.ZUS,
            ExpenseCategoryCode.TAXES);

    private static final Set<String> ITEM_FORBIDDEN_CATEGORIES = Set.of(
            ExpenseCategoryCode.RENT,
            ExpenseCategoryCode.SALARY,
            ExpenseCategoryCode.ZUS,
            ExpenseCategoryCode.TAXES,
            ExpenseCategoryCode.MIXED);

    private static final Set<String> LOCKED_CATEGORIES = Set.of(
            ExpenseCategoryCode.PRODUCTS,
            ExpenseCategoryCode.MIXED,
            ExpenseCategoryCode.SALARY,
            ExpenseCategoryCode.RENT,
            ExpenseCategoryCode.ZUS,
            ExpenseCategoryCode.TAXES);

    private static final Set<String> FIXED_SOURCE_CATEGORIES = Set.of(
            ExpenseCategoryCode.ZUS,
            ExpenseCategoryCode.TAXES);

    private static final Set<String> PLN_ONLY_CATEGORIES = Set.of(
            ExpenseCategoryCode.SALARY,
            ExpenseCategoryCode.ZUS,
            ExpenseCategoryCode.TAXES);

    private static final int SELLER_NAME_MAX_LENGTH = 200;
    private static final int SELLER_ADDRESS_MAX_LENGTH = 500;
    private static final int INVOICE_NUMBER_MAX_LENGTH = 64;

    private final CompanyExpenseRepo companyExpenseRepo;
    private final CompanyExpenseItemRepo companyExpenseItemRepo;
    private final ProductRepo productRepo;
    private final EmployeeRepo employeeRepo;
    private final KsefSellerRepo ksefSellerRepo;
    private final AuditLogService auditLogService;
    private final SupplyHistoryService supplyHistoryService;
    private final ExpenseCategoryService expenseCategoryService;
    private final ExchangeRateService exchangeRateService;

    @Override
    public CompanyExpenseDTO getExpenseById(Long id) {
        return new CompanyExpenseDTO(companyExpenseRepo.findOneByIdWithItems(id)
                .orElseThrow(() -> new ResourceNotFoundException("Company expense not found with id: " + id)));
    }

    @Override
    public List<CompanyExpenseDTO> getSalaryHistory(Long employeeId) {
        if (employeeRepo.findOneById(employeeId).isEmpty()) {
            throw new ResourceNotFoundException("Employee not found with id: " + employeeId);
        }
        return companyExpenseRepo
                .findByCategoryAndEmployeeIdOrderByExpenseDateAsc(ExpenseCategoryCode.SALARY, employeeId)
                .stream()
                .map(CompanyExpenseDTO::new)
                .toList();
    }

    @Override
    public Optional<CompanyExpenseDTO> getLatestExpenseByCategory(String category) {
        return companyExpenseRepo.findFirstByCategoryOrderByExpenseDateDescIdDesc(category)
                .flatMap(expense -> companyExpenseRepo.findOneByIdWithItems(expense.getId()))
                .map(CompanyExpenseDTO::new);
    }

    @Override
    public Page<CompanyExpenseDTO> getExpenses(CompanyExpenseFilterDTO filter, int page, int size) {
        if (isNull(filter)) {
            filter = new CompanyExpenseFilterDTO();
        }

        Pageable pageable = PageRequest.of(page, size);

        LocalDate dateFrom;
        LocalDate dateTo;

        if (filter.getYear() != null && filter.getMonth() != null) {
            YearMonth yearMonth = YearMonth.of(filter.getYear(), filter.getMonth());
            dateFrom = yearMonth.atDay(1);
            dateTo = yearMonth.atEndOfMonth();
        } else if (filter.getYear() != null) {
            YearMonth currentMonth = YearMonth.of(filter.getYear(), LocalDate.now().getMonthValue());
            dateFrom = currentMonth.atDay(1);
            dateTo = currentMonth.atEndOfMonth();
        } else {
            YearMonth now = YearMonth.now();
            dateFrom = now.atDay(1);
            dateTo = now.atEndOfMonth();
        }

        List<String> categoryNames = filter.getCategories() == null
                ? List.of()
                : filter.getCategories().stream().filter(Objects::nonNull).toList();
        int categoryCount = categoryNames.size();
        List<String> categoryParam = categoryNames.isEmpty() ? List.of("") : categoryNames;

        Page<UnifiedExpenseProjection> expenses = companyExpenseRepo.findUnifiedWithFilters(
                dateFrom,
                dateTo,
                categoryParam,
                categoryCount,
                pageable);
        return expenses.map(CompanyExpenseDTO::new);
    }

    @Override
    public CompanyExpenseDTO getExpensePreview(CompanyExpenseDTO expenseDTO) {
        CompanyExpense tempExpense = convertDtoToEntity(expenseDTO);
        tempExpense.setCurrency(resolveCurrency(expenseDTO.getCategory(), expenseDTO.getCurrency()));
        tempExpense.setExpenseDate(expenseDTO.getExpenseDate());
        applyExchangeRate(tempExpense);
        tempExpense.calculateTotals();

        expenseDTO.setTotalValue(tempExpense.getTotalValue());
        expenseDTO.setTotalNet(tempExpense.getTotalNet());
        expenseDTO.setTotalVat(tempExpense.getTotalVat());
        expenseDTO.setCurrency(tempExpense.getCurrency());
        expenseDTO.setExchangeRate(tempExpense.getExchangeRate());
        expenseDTO.setExchangeRateDate(tempExpense.getExchangeRateDate());
        expenseDTO.setExchangeRateTable(tempExpense.getExchangeRateTable());
        expenseDTO.setTotalValueCurrency(tempExpense.getTotalValueCurrency());
        expenseDTO.setTotalNetCurrency(tempExpense.getTotalNetCurrency());
        expenseDTO.setTotalVatCurrency(tempExpense.getTotalVatCurrency());

        return expenseDTO;
    }

    @Override
    @Transactional
    public CompanyExpenseDTO createExpense(CompanyExpenseDTO expenseDTO) {
        try {
            validateItems(expenseDTO.getExpenseItems());
            if (expenseDTO.getCategory() == null) {
                throw new ValidationException("Kategoria kosztu jest wymagana.");
            }
            expenseCategoryService.requireExistingCode(expenseDTO.getCategory());
            String invoiceNumber = normalizeInvoiceNumber(expenseDTO.getInvoiceNumber());
            ExpenseCurrency currency = resolveCurrency(expenseDTO.getCategory(), expenseDTO.getCurrency());

            boolean isMixedCategory = ExpenseCategoryCode.MIXED.equals(expenseDTO.getCategory());
            if (isMixedCategory) {
                validateMixedItems(expenseDTO.getExpenseItems());
            }

            KsefSeller seller = resolveSeller(expenseDTO.getCategory(), expenseDTO.getSeller());

            CompanyExpense expense = CompanyExpense.builder()
                    .source(seller != null && seller.getName() != null ? seller.getName() : expenseDTO.getSource())
                    .expenseDate(expenseDTO.getExpenseDate())
                    .category(expenseDTO.getCategory())
                    .seller(seller)
                    .invoiceNumber(invoiceNumber)
                    .currency(currency)
                    .build();
            applyExchangeRate(expense);

            boolean isProductsCategory = ExpenseCategoryCode.PRODUCTS.equals(expenseDTO.getCategory());
            boolean isSalaryCategory = ExpenseCategoryCode.SALARY.equals(expenseDTO.getCategory());

            Map<Long, Product> suppliedProducts = new LinkedHashMap<>();
            Map<Long, Integer> suppliedQuantities = new LinkedHashMap<>();

            if (isSalaryCategory && expenseDTO.getEmployeeId() != null) {
                expense.setEmployee(resolveEmployee(expenseDTO.getEmployeeId()));
            }

            for (CompanyExpenseItemDTO itemDTO : expenseDTO.getExpenseItems()) {
                CompanyExpenseItem item = CompanyExpenseItem.builder()
                        .companyExpense(expense)
                        .name(itemDTO.getName())
                        .quantity(itemDTO.getQuantity())
                        .vatRate(itemDTO.getVatRate())
                        .price(itemDTO.getPrice())
                        .salaryComponent(isSalaryCategory ? itemDTO.getSalaryComponent() : null)
                        .category(isMixedCategory ? itemDTO.getCategory() : null)
                        .build();

                boolean isProductItem = isProductsCategory
                        || (isMixedCategory && ExpenseCategoryCode.PRODUCTS.equals(itemDTO.getCategory()));

                if (isProductItem && itemDTO.getProductId() != null) {
                    Product product = resolveProduct(itemDTO.getProductId());
                    item.setProduct(product);
                    item.setName(product.getName());
                    product.addToSupply(item.getQuantity());
                    productRepo.save(product);

                    suppliedProducts.putIfAbsent(product.getId(), product);
                    suppliedQuantities.merge(product.getId(), item.getQuantity(), Integer::sum);
                }

                expense.addExpenseItem(item);
            }

            expense.calculateTotals();

            CompanyExpense savedExpense = companyExpenseRepo.save(expense);

            for (Map.Entry<Long, Product> entry : suppliedProducts.entrySet()) {
                supplyHistoryService.record(entry.getValue(), suppliedQuantities.get(entry.getKey()),
                        SupplyChangeSource.COMPANY_EXPENSE, savedExpense.getId(),
                        "Koszt: " + savedExpense.getSource());
            }

            CompanyExpenseDTO savedExpenseDTO = new CompanyExpenseDTO(savedExpense);
            auditLogService.logCreate("CompanyExpense", savedExpenseDTO.getId(), "Koszt: " + savedExpenseDTO.getSource(), savedExpenseDTO);
            return savedExpenseDTO;
        } catch (ResourceNotFoundException | ConflictException | ValidationException
                 | ExternalServiceUnavailableException | AccessDeniedException | CreationException
                 | UpdateException | DeletionException e) {
            throw e;
        } catch (Exception e) {
            throw new CreationException("Failed to create company expense.", e);
        }
    }

    @Override
    @Transactional
    public CompanyExpenseDTO updateExpense(Long id, CompanyExpenseUpdateDTO updateDTO) {
        try {
            CompanyExpense expense = companyExpenseRepo.findOneByIdWithItems(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Company expense not found with ID: " + id));

            String currentCategory = expense.getCategory();
            if (ExpenseCategoryCode.SALARY.equals(currentCategory)) {
                throw new ConflictException("Nie można edytować kosztu wypłaty.");
            }
            if (updateDTO.getExpenseDate() == null) {
                throw new ValidationException("Data kosztu jest wymagana.");
            }
            String newCategory = updateDTO.getCategory();
            if (newCategory == null) {
                throw new ValidationException("Kategoria kosztu jest wymagana.");
            }
            expenseCategoryService.requireExistingCode(newCategory);
            String invoiceNumber = normalizeInvoiceNumber(updateDTO.getInvoiceNumber());
            ExpenseCurrency newCurrency = resolveCurrency(newCategory, updateDTO.getCurrency());
            if (!newCategory.equals(currentCategory)
                    && ((currentCategory != null && LOCKED_CATEGORIES.contains(currentCategory))
                            || LOCKED_CATEGORIES.contains(newCategory))) {
                throw new ConflictException("Nie można zmienić tej kategorii kosztu.");
            }

            boolean fixedSource = FIXED_SOURCE_CATEGORIES.contains(newCategory);
            boolean linksSeller = !SELLERLESS_CATEGORIES.contains(newCategory) && hasSellerNip(updateDTO.getSeller());
            if (!fixedSource && !linksSeller) {
                requireSource(updateDTO.getSource());
            }

            CompanyExpenseDTO oldExpenseSnapshot = new CompanyExpenseDTO(expense);

            KsefSeller seller = resolveSeller(newCategory, updateDTO.getSeller());
            String source;
            if (fixedSource) {
                source = expense.getSource();
            } else if (seller != null && seller.getName() != null) {
                source = seller.getName();
            } else {
                source = requireSource(updateDTO.getSource());
            }

            boolean currencyChanged = newCurrency != expense.getCurrency();
            boolean dateChanged = !updateDTO.getExpenseDate().equals(expense.getExpenseDate());
            boolean recalculateRate = currencyChanged
                    || (newCurrency.isForeign() && (dateChanged || expense.getExchangeRate() == null));

            expense.setExpenseDate(updateDTO.getExpenseDate());
            expense.setCategory(newCategory);
            expense.setSeller(seller);
            expense.setSource(source);
            expense.setInvoiceNumber(invoiceNumber);

            if (recalculateRate) {
                expense.setCurrency(newCurrency);
                applyExchangeRate(expense);
                expense.calculateTotals();
            }

            CompanyExpenseDTO savedExpenseDTO = new CompanyExpenseDTO(companyExpenseRepo.save(expense));
            auditLogService.logUpdate("CompanyExpense", id, "Koszt: " + savedExpenseDTO.getSource(),
                    oldExpenseSnapshot, savedExpenseDTO);
            return savedExpenseDTO;
        } catch (ResourceNotFoundException | ConflictException | ValidationException
                 | ExternalServiceUnavailableException | AccessDeniedException | CreationException
                 | UpdateException | DeletionException e) {
            throw e;
        } catch (Exception e) {
            throw new UpdateException("Failed to update company expense.", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public StockChangePreviewDTO getDeletionPreview(Long id) {
        CompanyExpense expense = companyExpenseRepo.findOneByIdWithItems(id)
                .orElseThrow(() -> new ResourceNotFoundException("Company expense not found with ID: " + id));

        Map<Long, Integer> productQuantities = sumProductQuantities(expense);
        List<Product> products = productQuantities.isEmpty()
                ? List.of()
                : productRepo.findAllById(productQuantities.keySet());

        return buildDeletionPreview(products, productQuantities);
    }

    @Override
    @Transactional
    public void deleteExpenseById(Long id) {
        try {
            CompanyExpense existingExpense = companyExpenseRepo.findOneByIdWithItems(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Company expense not found with ID: " + id));

            Map<Long, Integer> productQuantities = sumProductQuantities(existingExpense);
            List<Product> lockedProducts = productQuantities.isEmpty()
                    ? List.of()
                    : productRepo.findAllByIdInForUpdate(productQuantities.keySet());

            StockChangePreviewDTO preview = buildDeletionPreview(lockedProducts, productQuantities);
            if (!preview.isCanApply()) {
                throw new StockChangeBlockedException(
                        "Insufficient supply to remove CompanyExpense with ID: " + id, preview);
            }

            CompanyExpenseDTO expenseSnapshot = new CompanyExpenseDTO(existingExpense);

            for (Product product : lockedProducts) {
                if (isRemovedFromStock(product)) {
                    continue;
                }
                int quantity = productQuantities.get(product.getId());
                product.reduceSupply(quantity);
                productRepo.save(product);
                supplyHistoryService.record(product, -quantity, SupplyChangeSource.COMPANY_EXPENSE_DELETED,
                        id, "Koszt: " + existingExpense.getSource());
            }

            companyExpenseRepo.delete(existingExpense);

            auditLogService.logDelete("CompanyExpense", id, "Koszt: " + expenseSnapshot.getSource(), expenseSnapshot);
        } catch (ResourceNotFoundException | StockChangeBlockedException | ConflictException | ValidationException
                 | AccessDeniedException | CreationException | UpdateException | DeletionException e) {
            throw e;
        } catch (Exception e) {
            throw new DeletionException("Failed to delete company expense.", e);
        }
    }

    private Map<Long, Integer> sumProductQuantities(CompanyExpense expense) {
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        for (CompanyExpenseItem item : expense.getExpenseItems()) {
            if (item.getProduct() != null) {
                quantities.merge(item.getProduct().getId(), item.getQuantity(), Integer::sum);
            }
        }
        return quantities;
    }

    private StockChangePreviewDTO buildDeletionPreview(List<Product> products, Map<Long, Integer> productQuantities) {
        List<StockChangeProductDTO> productPreviews = products.stream()
                .map(product -> toDeletionProduct(product, productQuantities.get(product.getId())))
                .sorted(Comparator.comparing(StockChangeProductDTO::getProductName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        boolean canApply = productPreviews.stream().allMatch(StockChangeProductDTO::isSufficient);
        return new StockChangePreviewDTO(canApply, productPreviews);
    }

    private StockChangeProductDTO toDeletionProduct(Product product, int quantity) {
        boolean skipped = isRemovedFromStock(product);
        int currentSupply = product.getSupply();
        return new StockChangeProductDTO(
                product.getId(),
                product.getName(),
                -quantity,
                currentSupply,
                skipped ? currentSupply : currentSupply - quantity,
                skipped || currentSupply >= quantity,
                skipped);
    }

    private boolean isRemovedFromStock(Product product) {
        return Boolean.TRUE.equals(product.getIsDeleted());
    }

    private void validateItems(List<CompanyExpenseItemDTO> items) {
        if (items == null || items.isEmpty()) {
            throw new ValidationException("Koszt musi mieć co najmniej jedną pozycję.");
        }
        for (CompanyExpenseItemDTO item : items) {
            if (item.getQuantity() == null || item.getQuantity() < 1) {
                throw new ValidationException("Ilość pozycji musi wynosić co najmniej 1.");
            }
            if (item.getPrice() == null || item.getPrice().compareTo(BigDecimal.ZERO) < 0) {
                throw new ValidationException("Cena pozycji nie może być ujemna.");
            }
            if (item.getVatRate() == null) {
                throw new ValidationException("Pozycja musi mieć stawkę VAT.");
            }
        }
    }

    private void validateMixedItems(List<CompanyExpenseItemDTO> items) {
        Set<String> distinctCategories = new HashSet<>();
        for (CompanyExpenseItemDTO item : items) {
            String itemCategory = item.getCategory();
            if (itemCategory == null) {
                throw new ValidationException("Każda pozycja kosztu mieszanego musi mieć kategorię.");
            }
            if (ITEM_FORBIDDEN_CATEGORIES.contains(itemCategory)) {
                throw new ValidationException("Niedozwolona kategoria pozycji.");
            }
            expenseCategoryService.requireExistingCode(itemCategory);
            if (ExpenseCategoryCode.PRODUCTS.equals(itemCategory) != (item.getProductId() != null)) {
                throw new ValidationException("Pozycja z kategorią Produkty musi mieć wybrany produkt.");
            }
            distinctCategories.add(itemCategory);
        }
        if (distinctCategories.size() < 2) {
            throw new ValidationException("Wszystkie pozycje mają tę samą kategorię - wybierz ją jako kategorię kosztu.");
        }
    }

    private Product resolveProduct(Long productId) {
        return productRepo.findByIdNotDeleted(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));
    }

    private boolean hasSellerNip(ExpenseSellerDTO sellerDTO) {
        return sellerDTO != null && sellerDTO.getNip() != null && !sellerDTO.getNip().isBlank();
    }

    private String requireSource(String source) {
        String trimmed = source != null ? source.trim() : "";
        if (trimmed.isEmpty()) {
            throw new ValidationException("Nazwa dostawcy jest wymagana.");
        }
        return trimmed;
    }

    private String normalizeInvoiceNumber(String invoiceNumber) {
        if (invoiceNumber == null || invoiceNumber.isBlank()) {
            return null;
        }
        String trimmed = invoiceNumber.trim();
        if (trimmed.length() > INVOICE_NUMBER_MAX_LENGTH) {
            throw new ValidationException("Numer faktury może mieć maksymalnie " + INVOICE_NUMBER_MAX_LENGTH + " znaki.");
        }
        return trimmed;
    }

    private KsefSeller resolveSeller(String category, ExpenseSellerDTO sellerDTO) {
        if ((category != null && SELLERLESS_CATEGORIES.contains(category)) || !hasSellerNip(sellerDTO)) {
            return null;
        }

        String nip = NipUtils.normalizeTaxId(sellerDTO.getNip());
        if (!NipUtils.isValidTaxId(nip)) {
            throw new ValidationException("Niepoprawny NIP lub numer VAT UE sprzedawcy.");
        }

        Optional<KsefSeller> existing = ksefSellerRepo.findByNip(nip);
        if (existing.isPresent()) {
            return existing.get();
        }

        String name = sellerDTO.getName() != null ? sellerDTO.getName().trim() : "";
        String address = sellerDTO.getAddress() != null ? sellerDTO.getAddress().trim() : "";
        if (name.isEmpty()) {
            throw new ValidationException("Nazwa sprzedawcy jest wymagana dla nowego NIP.");
        }
        if (address.isEmpty()) {
            throw new ValidationException("Adres sprzedawcy jest wymagany dla nowego NIP.");
        }
        if (name.length() > SELLER_NAME_MAX_LENGTH) {
            throw new ValidationException("Nazwa sprzedawcy może mieć maksymalnie " + SELLER_NAME_MAX_LENGTH + " znaków.");
        }
        if (address.length() > SELLER_ADDRESS_MAX_LENGTH) {
            throw new ValidationException("Adres sprzedawcy może mieć maksymalnie " + SELLER_ADDRESS_MAX_LENGTH + " znaków.");
        }

        KsefSeller savedSeller = ksefSellerRepo.save(KsefSeller.builder()
                .nip(nip)
                .name(name)
                .address(address)
                .build());
        auditLogService.logCreate("KsefSeller", savedSeller.getId(), savedSeller.getName(), new KsefSellerDTO(savedSeller));
        return savedSeller;
    }

    private ExpenseCurrency resolveCurrency(String category, ExpenseCurrency requested) {
        ExpenseCurrency currency = requested != null ? requested : ExpenseCurrency.PLN;
        if (currency.isForeign() && category != null && PLN_ONLY_CATEGORIES.contains(category)) {
            throw new ValidationException("Ta kategoria kosztu może być tylko w PLN.");
        }
        return currency;
    }

    private void applyExchangeRate(CompanyExpense expense) {
        if (!expense.getCurrency().isForeign()) {
            expense.clearExchangeRate();
            return;
        }
        if (expense.getExpenseDate() == null) {
            throw new ValidationException("Data kosztu jest wymagana.");
        }
        ExchangeRateDTO rate = exchangeRateService.resolveRate(expense.getCurrency(), expense.getExpenseDate())
                .orElseThrow(() -> new IllegalStateException("Missing exchange rate for " + expense.getCurrency()));
        expense.applyExchangeRate(rate.getMid(), rate.getRateDate(), rate.getTableNo());
    }

    private Employee resolveEmployee(Long employeeId) {
        return employeeRepo.findOneById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + employeeId));
    }

    // Helper method for preview calculations
    private CompanyExpense convertDtoToEntity(CompanyExpenseDTO dto) {
        CompanyExpense expense = CompanyExpense.builder().build();

        for (CompanyExpenseItemDTO itemDto : dto.getExpenseItems()) {
            CompanyExpenseItem item = CompanyExpenseItem.builder()
                    .price(itemDto.getPrice())
                    .quantity(itemDto.getQuantity())
                    .vatRate(itemDto.getVatRate())
                    .build();
            expense.addExpenseItem(item);
        }

        return expense;
    }
}
