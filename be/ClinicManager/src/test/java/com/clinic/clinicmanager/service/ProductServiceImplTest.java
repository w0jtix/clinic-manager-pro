package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.config.security.AppRoleHierarchy;
import com.clinic.clinicmanager.DTO.BrandDTO;
import com.clinic.clinicmanager.DTO.ProductCategoryDTO;
import com.clinic.clinicmanager.DTO.ProductDTO;
import com.clinic.clinicmanager.DTO.request.ProductFilterDTO;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.CreationException;
import com.clinic.clinicmanager.exceptions.DeletionException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.UpdateException;
import com.clinic.clinicmanager.model.Brand;
import com.clinic.clinicmanager.model.Product;
import com.clinic.clinicmanager.model.ProductCategory;
import com.clinic.clinicmanager.model.constants.StockStatus;
import com.clinic.clinicmanager.model.constants.SupplyChangeSource;
import com.clinic.clinicmanager.model.constants.VatRate;
import com.clinic.clinicmanager.repo.ProductCategoryRepo;
import com.clinic.clinicmanager.repo.ProductRepo;
import com.clinic.clinicmanager.service.impl.ProductServiceImpl;
import com.clinic.clinicmanager.utils.SessionUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ProductServiceImplTest {

    private static void assertMoney(double expected, BigDecimal actual) {
        assertEquals(0, BigDecimal.valueOf(expected).compareTo(actual),
                () -> "expected " + expected + " but was " + actual);
    }

    private static ProductDTO productInput(String name) {
        ProductDTO inputDTO = new ProductDTO();
        inputDTO.setName(name);
        inputDTO.setBrand(new BrandDTO(1L, "Kremowo"));
        inputDTO.setCategory(new ProductCategoryDTO(1L, "Kremy", "255,255,255"));
        return inputDTO;
    }

    private static Product savedProduct(String name) {
        return Product.builder()
                .id(1L).name(name)
                .brand(Brand.builder().id(1L).name("Kremowo").build())
                .category(ProductCategory.builder().id(1L).name("Kremy").color("255,255,255").build())
                .build();
    }

    @Mock
    ProductRepo productRepo;

    @Mock
    ProductCategoryRepo productCategoryRepo;

    @Mock
    AuditLogService auditLogService;

    @Mock
    ProductReferenceService productReferenceService;

    @Mock
    SupplyHistoryService supplyHistoryService;

    @InjectMocks
    ProductServiceImpl productService;


    @Test
    void getProductById_ShouldReturnProductDTO_whenProductExists() {
        Product product = Product.builder()
                .id(1L).name("Test").isDeleted(false)
                .brand(Brand.builder().id(1L).name("Nike").build())
                .category(ProductCategory.builder().id(1L).name("Kremy").color("255,255,255").build())
                .build();

        when(productRepo.findOneById(1L)).thenReturn(Optional.of(product));

        ProductDTO result = productService.getProductById(1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("Test", result.getName());
    }

    @Test
    void getProductById_shouldThrowResourceNotFound_whenProductNotFound() {
        when(productRepo.findOneById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> productService.getProductById(99L));
    }

    @Test
    void createProduct_shouldReturnProductDTO_whenNotFoundAndNotSoftDeleted () {
        BrandDTO brandDTO = new BrandDTO(1L, "Kremowo");
        ProductCategoryDTO categoryDTO = new ProductCategoryDTO(1L, "Kremy", "255,255,255");

        ProductDTO inputDTO = new ProductDTO();
        inputDTO.setName("Krem do stóp");
        inputDTO.setBrand(brandDTO);
        inputDTO.setCategory(categoryDTO);
        inputDTO.setSupply(10);

        Product savedEntity = Product.builder().id(1L).name("Krem do stóp")
                .brand(Brand.builder().id(1L).name("Kremy").build())
                .category(ProductCategory.builder().id(1L).name("Kremy").color("255,255,255").build()).supply(10)
                .build();

        when(productRepo.findByName("Krem do stóp")).thenReturn(Optional.empty());
        when(productRepo.save(any(Product.class))).thenReturn(savedEntity);

        ProductDTO result = productService.createProduct(inputDTO);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("Krem do stóp", result.getName());

        verify(productRepo, times(1)).save(any(Product.class));
    }

    @Test
    void createProduct_shouldReturnProductDTOAndSetAdminFields_whenUserWithAdminRole() {
        ProductDTO inputDTO = new ProductDTO();
        inputDTO.setName("Krem do stóp");
        inputDTO.setBrand(new BrandDTO(1L, "Kremowo"));
        inputDTO.setCategory(new ProductCategoryDTO(1L, "Kremy", "255,255,255"));
        inputDTO.setFallbackNetPurchasePrice(BigDecimal.valueOf(15.0));
        inputDTO.setFallbackVatRate(VatRate.VAT_23);

        Product savedEntity = Product.builder()
                .id(1L).name("Krem do stóp")
                .brand(Brand.builder().id(1L).name("Nike").build())
                .category(ProductCategory.builder().id(1L).name("Kremy").color("255,255,255").build())
                .fallbackNetPurchasePrice(BigDecimal.valueOf(15.0))
                .fallbackVatRate(VatRate.VAT_23)
                .build();


        when(productRepo.findByName("Krem do stóp")).thenReturn(Optional.empty());
        when(productRepo.save(any(Product.class))).thenReturn(savedEntity);

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(() -> SessionUtils.hasRole(AppRoleHierarchy.COMPANY)).thenReturn(true);

            ProductDTO result = productService.createProduct(inputDTO);

            assertMoney(15.0, result.getFallbackNetPurchasePrice());
            assertEquals(VatRate.VAT_23, result.getFallbackVatRate());
        }
    }

    @Test
    void createProduct_shouldReturnProductDTOAndNotSetAdminFields_whenUserNotWithAdminRole() {
        ProductDTO inputDTO = new ProductDTO();
        inputDTO.setName("Krem do stóp");
        inputDTO.setBrand(new BrandDTO(1L, "Kremowo"));
        inputDTO.setCategory(new ProductCategoryDTO(1L, "Kremy", "255,255,255"));
        inputDTO.setFallbackNetPurchasePrice(BigDecimal.valueOf(15.0));
        inputDTO.setFallbackVatRate(VatRate.VAT_23);

        Product savedEntity = Product.builder()
                .id(1L).name("Krem do stóp")
                .brand(Brand.builder().id(1L).name("Nike").build())
                .category(ProductCategory.builder().id(1L).name("Kremy").color("255,255,255").build())
                .build();

        when(productRepo.findByName("Krem do stóp")).thenReturn(Optional.empty());
        when(productRepo.save(any(Product.class))).thenReturn(savedEntity);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(() -> SessionUtils.hasRole(AppRoleHierarchy.COMPANY)).thenReturn(false);

            productService.createProduct(inputDTO);

            verify(productRepo).save(captor.capture());
            Product captured = captor.getValue();

            assertNull(captured.getFallbackNetPurchasePrice());
            assertNull(captured.getFallbackVatRate());
        }
    }

    @Test
    void createProduct_shouldReturnRestoredProductDTO_whenFoundAndSoftDeleted () {
        ProductDTO inputDTO = new ProductDTO();
        inputDTO.setName("Krem do stóp");
        inputDTO.setBrand(new BrandDTO(1L, "Kremowo"));
        inputDTO.setCategory(new ProductCategoryDTO(1L, "Kremy", "255,255,255"));
        inputDTO.setSupply(10);

        Product existingProduct = Product.builder()
                .id(1L).name("Krem do stóp")
                .brand(Brand.builder().id(1L).name("Kremowo").build())
                .category(ProductCategory.builder().id(1L).name("Kremy").color("255,255,255").build())
                .supply(0)
                .isDeleted(true)
                .build();

        Product savedEntity = Product.builder()
                .id(1L).name("Krem do stóp")
                .brand(Brand.builder().id(1L).name("Kremowo").build())
                .category(ProductCategory.builder().id(1L).name("Kremy").color("255,255,255").build())
                .supply(10)
                .isDeleted(false)
                .build();

        when(productRepo.findByName("Krem do stóp")).thenReturn(Optional.of(existingProduct));
        when(productRepo.save(any(Product.class))).thenReturn(savedEntity);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);

        productService.createProduct(inputDTO);
        verify(productRepo).save(captor.capture());
        Product captured = captor.getValue();

        assertFalse(captured.getIsDeleted());
        assertEquals(10, captured.getSupply());
    }

    @Test
    void createProduct_shouldThrowConflictException_whenProductNameAlreadyExists() {
        BrandDTO brandDTO = new BrandDTO(1L, "Kremowo");
        ProductCategoryDTO categoryDTO = new ProductCategoryDTO(1L, "Kremy", "255,255,255");

        ProductDTO inputDTO = new ProductDTO();
        inputDTO.setName("Krem do stóp2");
        inputDTO.setBrand(brandDTO);
        inputDTO.setCategory(categoryDTO);
        inputDTO.setSupply(10);

        Product existingProduct = Product.builder()
                .id(3L).name("Krem do stóp2")
                .brand(Brand.builder().id(1L).name("Kremowo").build())
                .category(ProductCategory.builder().id(1L).name("Kremy").color("255,255,255").build())
                .supply(0)
                .build();

        when(productRepo.findByName("Krem do stóp2")).thenReturn(Optional.of(existingProduct));

        assertThrows(ConflictException.class,
                () -> productService.createProduct(inputDTO));
    }

    @Test
    void updateProduct_shouldReturnProductDTO_whenProductFound() {
        BrandDTO brandDTO = new BrandDTO(1L, "Kremowo");
        ProductCategoryDTO categoryDTO = new ProductCategoryDTO(1L, "Kremy", "255,255,255");

        ProductDTO inputDTO = new ProductDTO();
        inputDTO.setName("Krem do stóp2");
        inputDTO.setBrand(brandDTO);
        inputDTO.setCategory(categoryDTO);
        inputDTO.setSupply(10);

        Product existingProduct = Product.builder()
                .id(1L).name("Krem do stóp")
                .brand(Brand.builder().id(1L).name("Kremowo").build())
                .category(ProductCategory.builder().id(1L).name("Kremy").color("255,255,255").build())
                .supply(0)
                .build();
        Product savedEntity = Product.builder()
                .id(1L).name("Krem do stóp2")
                .brand(Brand.builder().id(1L).name("Kremowo").build())
                .category(ProductCategory.builder().id(1L).name("Kremy").color("255,255,255").build())
                .supply(10)
                .build();

        when(productRepo.save(any(Product.class))).thenReturn(savedEntity);
        when(productRepo.findOneById(1L)).thenReturn(Optional.of(existingProduct));
        when(productRepo.findByName("Krem do stóp2")).thenReturn(Optional.empty());

        ProductDTO result = productService.updateProduct(1L, inputDTO);

        assertEquals("Krem do stóp2", result.getName());
        assertEquals(10, result.getSupply());

        verify(productRepo, times(1)).save(any(Product.class));
    }

    @Test
    void updateProduct_shouldThrowResourceNotFoundException_whenProductNotFound() {
        BrandDTO brandDTO = new BrandDTO(1L, "Kremowo");
        ProductCategoryDTO categoryDTO = new ProductCategoryDTO(1L, "Kremy", "255,255,255");

        ProductDTO inputDTO = new ProductDTO();
        inputDTO.setName("Krem do stóp");
        inputDTO.setBrand(brandDTO);
        inputDTO.setCategory(categoryDTO);
        inputDTO.setSupply(10);

        when(productRepo.findOneById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> productService.updateProduct(99L, inputDTO));
    }

    @Test
    void updateProduct_shouldReturnProductDTOAndSetAdminFields_whenUserWithAdminRole() {
        ProductDTO inputDTO = new ProductDTO();
        inputDTO.setName("Krem do stóp");
        inputDTO.setBrand(new BrandDTO(1L, "Kremowo"));
        inputDTO.setCategory(new ProductCategoryDTO(1L, "Kremy", "255,255,255"));
        inputDTO.setFallbackNetPurchasePrice(BigDecimal.valueOf(15.0));
        inputDTO.setFallbackVatRate(VatRate.VAT_23);

        Product savedEntity = Product.builder()
                .id(1L).name("Krem do stóp")
                .brand(Brand.builder().id(1L).name("Nike").build())
                .category(ProductCategory.builder().id(1L).name("Kremy").color("255,255,255").build())
                .fallbackNetPurchasePrice(BigDecimal.valueOf(15.0))
                .fallbackVatRate(VatRate.VAT_23)
                .build();

        Product existingProduct = Product.builder()
                .id(1L).name("Krem do stóp")
                .brand(Brand.builder().id(1L).name("Kremowo").build())
                .category(ProductCategory.builder().id(1L).name("Kremy").color("255,255,255").build())
                .supply(0)
                .build();

        when(productRepo.findOneById(1L)).thenReturn(Optional.of(existingProduct));
        when(productRepo.save(any(Product.class))).thenReturn(savedEntity);

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(() -> SessionUtils.hasRole(AppRoleHierarchy.COMPANY)).thenReturn(true);

            ProductDTO result = productService.updateProduct(1L, inputDTO);

            assertMoney(15.0, result.getFallbackNetPurchasePrice());
            assertEquals(VatRate.VAT_23, result.getFallbackVatRate());
        }
    }

    @Test
    void updateProduct_shouldReturnProductDTOAndNotSetAdminFields_whenUserNotWithAdminRole() {
        ProductDTO inputDTO = new ProductDTO();
        inputDTO.setName("Krem do stóp");
        inputDTO.setBrand(new BrandDTO(1L, "Kremowo"));
        inputDTO.setCategory(new ProductCategoryDTO(1L, "Kremy", "255,255,255"));
        inputDTO.setFallbackNetPurchasePrice(BigDecimal.valueOf(15.0));
        inputDTO.setFallbackVatRate(VatRate.VAT_23);

        Product savedEntity = Product.builder()
                .id(1L).name("Krem do stóp")
                .brand(Brand.builder().id(1L).name("Nike").build())
                .category(ProductCategory.builder().id(1L).name("Kremy").color("255,255,255").build())
                .build();

        Product existingProduct = Product.builder()
                .id(1L).name("Krem do stóp")
                .brand(Brand.builder().id(1L).name("Kremowo").build())
                .category(ProductCategory.builder().id(1L).name("Kremy").color("255,255,255").build())
                .build();

        when(productRepo.findOneById(1L)).thenReturn(Optional.of(existingProduct));
        when(productRepo.findByName("Krem do stóp")).thenReturn(Optional.empty());
        when(productRepo.save(any(Product.class))).thenReturn(savedEntity);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(() -> SessionUtils.hasRole(AppRoleHierarchy.COMPANY)).thenReturn(false);

            productService.updateProduct(1L, inputDTO);

            verify(productRepo).save(captor.capture());
            Product captured = captor.getValue();

            assertNull(captured.getFallbackNetPurchasePrice());
            assertNull(captured.getFallbackVatRate());
        }
    }

    @Test
    void updateProduct_shouldPreserveStoredPurchaseDataAndThreshold_whenNonCompanyCallerSendsNulls() {
        ProductDTO inputDTO = productInput("Krem do stóp");
        inputDTO.setSupply(5);
        inputDTO.setFallbackNetPurchasePrice(null);
        inputDTO.setFallbackVatRate(null);
        inputDTO.setLowStockThreshold(null);

        Product existingProduct = savedProduct("Krem do stóp");
        existingProduct.setSupply(5);
        existingProduct.setFallbackNetPurchasePrice(BigDecimal.valueOf(12.5));
        existingProduct.setFallbackVatRate(VatRate.VAT_8);
        existingProduct.setLowStockThreshold(4);

        when(productRepo.findOneById(1L)).thenReturn(Optional.of(existingProduct));
        when(productRepo.findByName("Krem do stóp")).thenReturn(Optional.empty());
        when(productRepo.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(() -> SessionUtils.hasRole(AppRoleHierarchy.COMPANY)).thenReturn(false);

            productService.updateProduct(1L, inputDTO);

            verify(productRepo).save(captor.capture());
            Product captured = captor.getValue();

            assertMoney(12.5, captured.getFallbackNetPurchasePrice());
            assertEquals(VatRate.VAT_8, captured.getFallbackVatRate());
            assertEquals(4, captured.getLowStockThreshold());
        }
    }

    @Test
    void updateProduct_shouldOverwritePurchaseData_whenCompanyCallerSendsNewValues() {
        ProductDTO inputDTO = productInput("Krem do stóp");
        inputDTO.setFallbackNetPurchasePrice(BigDecimal.valueOf(20.0));
        inputDTO.setFallbackVatRate(VatRate.VAT_23);
        inputDTO.setLowStockThreshold(2);

        Product existingProduct = savedProduct("Krem do stóp");
        existingProduct.setFallbackNetPurchasePrice(BigDecimal.valueOf(12.5));
        existingProduct.setFallbackVatRate(VatRate.VAT_8);
        existingProduct.setLowStockThreshold(4);

        when(productRepo.findOneById(1L)).thenReturn(Optional.of(existingProduct));
        when(productRepo.findByName("Krem do stóp")).thenReturn(Optional.empty());
        when(productRepo.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(() -> SessionUtils.hasRole(AppRoleHierarchy.COMPANY)).thenReturn(true);

            productService.updateProduct(1L, inputDTO);

            verify(productRepo).save(captor.capture());
            Product captured = captor.getValue();

            assertMoney(20.0, captured.getFallbackNetPurchasePrice());
            assertEquals(VatRate.VAT_23, captured.getFallbackVatRate());
            assertEquals(2, captured.getLowStockThreshold());
        }
    }

    @Test
    void updateProduct_shouldThrowConflictException_whenProductNameAlreadyExists() {
        BrandDTO brandDTO = new BrandDTO(1L, "Kremowo");
        ProductCategoryDTO categoryDTO = new ProductCategoryDTO(1L, "Kremy", "255,255,255");

        ProductDTO inputDTO = new ProductDTO();
        inputDTO.setName("Krem do stóp2");
        inputDTO.setBrand(brandDTO);
        inputDTO.setCategory(categoryDTO);
        inputDTO.setSupply(10);

        Product currentProduct = Product.builder()
                .id(1L).name("Krem do stóp")
                .brand(Brand.builder().id(1L).name("Kremowo").build())
                .category(ProductCategory.builder().id(1L).name("Kremy").color("255,255,255").build())
                .build();


        Product existingProduct = Product.builder()
                .id(3L).name("Krem do stóp2")
                .brand(Brand.builder().id(1L).name("Kremowo").build())
                .category(ProductCategory.builder().id(1L).name("Kremy").color("255,255,255").build())
                .supply(0)
                .build();

        when(productRepo.findOneById(1L)).thenReturn(Optional.of(currentProduct));
        when(productRepo.findByName("Krem do stóp2")).thenReturn(Optional.of(existingProduct));

        assertThrows(ConflictException.class,
                () -> productService.updateProduct(1L, inputDTO));
    }

    @Test
    void deleteProductById_shouldDeleteProduct_whenProductExistsAndHasNoReferences() {
        Product product = Product.builder()
                .id(1L).name("Test").isDeleted(false)
                .brand(Brand.builder().id(1L).name("Nike").build())
                .category(ProductCategory.builder().id(1L).name("Kremy").color("255,255,255").build())
                .build();

        when(productRepo.findOneById(1L)).thenReturn(Optional.of(product));
        when(productReferenceService.hasAnyReferences(1L)).thenReturn(false);

        productService.deleteProductById(1L);

        verify(productRepo, times(1)).deleteById(1L);
    }

    @Test
    void deleteProductById_shouldThrowDeletionException_whenProductExistsAndIsSoftDeleted() {
        Product product = Product.builder()
                .id(1L).name("Test").isDeleted(false)
                .brand(Brand.builder().id(1L).name("Nike").build())
                .category(ProductCategory.builder().id(1L).name("Kremy").color("255,255,255").build())
                .isDeleted(true)
                .build();

        when(productRepo.findOneById(1L)).thenReturn(Optional.of(product));

        assertThrows(DeletionException.class,
                () -> productService.deleteProductById(1L));
    }

    @Test
    void deleteProductById_shouldSoftDelete_whenProductExistsAndHasReference() {
        Product product = Product.builder()
                .id(1L).name("Test").isDeleted(false)
                .brand(Brand.builder().id(1L).name("Nike").build())
                .category(ProductCategory.builder().id(1L).name("Kremy").color("255,255,255").build())
                .build();

        when(productRepo.findOneById(1L)).thenReturn(Optional.of(product));
        when(productReferenceService.hasAnyReferences(1L)).thenReturn(true);

        productService.deleteProductById(1L);

        verify(productRepo, times(1)).save(any(Product.class));
        verify(productRepo, never()).deleteById(any());
    }

    @Test
    void deleteProductById_shouldThrowResourceNotFound_whenProductNotFound() {
        when(productRepo.findOneById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> productService.deleteProductById(99L));

        verify(productRepo, never()).deleteById(any());
    }

    @Test
    void createProduct_shouldRecordSupplyHistory_withProductCreatedSourceAndInitialSupply() {
        BrandDTO brandDTO = new BrandDTO(1L, "Kremowo");
        ProductCategoryDTO categoryDTO = new ProductCategoryDTO(1L, "Kremy", "255,255,255");

        ProductDTO inputDTO = new ProductDTO();
        inputDTO.setName("Krem do stóp");
        inputDTO.setBrand(brandDTO);
        inputDTO.setCategory(categoryDTO);
        inputDTO.setSupply(10);

        Product savedEntity = Product.builder().id(1L).name("Krem do stóp")
                .brand(Brand.builder().id(1L).name("Kremy").build())
                .category(ProductCategory.builder().id(1L).name("Kremy").color("255,255,255").build()).supply(10)
                .build();

        when(productRepo.findByName("Krem do stóp")).thenReturn(Optional.empty());
        when(productRepo.save(any(Product.class))).thenReturn(savedEntity);

        productService.createProduct(inputDTO);

        verify(supplyHistoryService).record(savedEntity, 10, SupplyChangeSource.PRODUCT_CREATED,
                1L, "Nowy produkt");
    }

    @Test
    void createProduct_shouldRecordSupplyHistory_withProductRestoredSource_whenRestoringSoftDeletedProduct() {
        ProductDTO inputDTO = new ProductDTO();
        inputDTO.setName("Krem do stóp");
        inputDTO.setBrand(new BrandDTO(1L, "Kremowo"));
        inputDTO.setCategory(new ProductCategoryDTO(1L, "Kremy", "255,255,255"));
        inputDTO.setSupply(10);

        Product existingProduct = Product.builder()
                .id(1L).name("Krem do stóp")
                .brand(Brand.builder().id(1L).name("Kremowo").build())
                .category(ProductCategory.builder().id(1L).name("Kremy").color("255,255,255").build())
                .supply(0)
                .isDeleted(true)
                .build();

        Product savedEntity = Product.builder()
                .id(1L).name("Krem do stóp")
                .brand(Brand.builder().id(1L).name("Kremowo").build())
                .category(ProductCategory.builder().id(1L).name("Kremy").color("255,255,255").build())
                .supply(10)
                .isDeleted(false)
                .build();

        when(productRepo.findByName("Krem do stóp")).thenReturn(Optional.of(existingProduct));
        when(productRepo.save(any(Product.class))).thenReturn(savedEntity);

        productService.createProduct(inputDTO);

        verify(supplyHistoryService).record(savedEntity, 10, SupplyChangeSource.PRODUCT_RESTORED,
                1L, "Przywrócenie produktu");
    }

    @Test
    void updateProduct_shouldRecordSupplyHistory_withDeltaBetweenOldAndNewSupply() {
        BrandDTO brandDTO = new BrandDTO(1L, "Kremowo");
        ProductCategoryDTO categoryDTO = new ProductCategoryDTO(1L, "Kremy", "255,255,255");

        ProductDTO inputDTO = new ProductDTO();
        inputDTO.setName("Krem do stóp2");
        inputDTO.setBrand(brandDTO);
        inputDTO.setCategory(categoryDTO);
        inputDTO.setSupply(10);

        Product existingProduct = Product.builder()
                .id(1L).name("Krem do stóp")
                .brand(Brand.builder().id(1L).name("Kremowo").build())
                .category(ProductCategory.builder().id(1L).name("Kremy").color("255,255,255").build())
                .supply(4)
                .build();
        Product savedEntity = Product.builder()
                .id(1L).name("Krem do stóp2")
                .brand(Brand.builder().id(1L).name("Kremowo").build())
                .category(ProductCategory.builder().id(1L).name("Kremy").color("255,255,255").build())
                .supply(10)
                .build();

        when(productRepo.save(any(Product.class))).thenReturn(savedEntity);
        when(productRepo.findOneById(1L)).thenReturn(Optional.of(existingProduct));
        when(productRepo.findByName("Krem do stóp2")).thenReturn(Optional.empty());

        productService.updateProduct(1L, inputDTO);

        verify(supplyHistoryService).record(savedEntity, 6, SupplyChangeSource.PRODUCT_FORM,
                1L, "Edycja produktu");
    }

    @Test
    void getProducts_shouldPassStockStatusName_whenFilterHasStockStatus() {
        ProductFilterDTO filter = new ProductFilterDTO();
        filter.setStockStatus(StockStatus.LOW_STOCK);

        when(productRepo.findAllWithFilters(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        productService.getProducts(filter);

        verify(productRepo).findAllWithFilters(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                eq("LOW_STOCK"));
    }

    @Test
    void getProducts_shouldPassNullStockStatus_whenFilterHasNoStockStatus() {
        when(productRepo.findAllWithFilters(any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        productService.getProducts(new ProductFilterDTO(), 0, 30);

        verify(productRepo).findAllWithFilters(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                isNull(), any(Pageable.class));
    }

    @Test
    void createProduct_shouldSetDefaultLowStockThreshold_whenNotProvided() {
        ProductDTO inputDTO = productInput("Krem do stóp");

        when(productRepo.findByName("Krem do stóp")).thenReturn(Optional.empty());
        when(productRepo.save(any(Product.class))).thenReturn(savedProduct("Krem do stóp"));

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(() -> SessionUtils.hasRole(AppRoleHierarchy.COMPANY)).thenReturn(true);

            productService.createProduct(inputDTO);

            verify(productRepo).save(captor.capture());
            assertEquals(1, captor.getValue().getLowStockThreshold());
        }
    }

    @Test
    void createProduct_shouldSetLowStockThreshold_whenUserWithAdminRole() {
        ProductDTO inputDTO = productInput("Krem do stóp");
        inputDTO.setLowStockThreshold(5);

        when(productRepo.findByName("Krem do stóp")).thenReturn(Optional.empty());
        when(productRepo.save(any(Product.class))).thenReturn(savedProduct("Krem do stóp"));

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(() -> SessionUtils.hasRole(AppRoleHierarchy.COMPANY)).thenReturn(true);

            productService.createProduct(inputDTO);

            verify(productRepo).save(captor.capture());
            assertEquals(5, captor.getValue().getLowStockThreshold());
        }
    }

    @Test
    void createProduct_shouldIgnoreLowStockThresholdFromBody_whenUserNotWithAdminRole() {
        ProductDTO inputDTO = productInput("Krem do stóp");
        inputDTO.setLowStockThreshold(5);

        when(productRepo.findByName("Krem do stóp")).thenReturn(Optional.empty());
        when(productRepo.save(any(Product.class))).thenReturn(savedProduct("Krem do stóp"));

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(() -> SessionUtils.hasRole(AppRoleHierarchy.COMPANY)).thenReturn(false);

            productService.createProduct(inputDTO);

            verify(productRepo).save(captor.capture());
            assertEquals(1, captor.getValue().getLowStockThreshold());
        }
    }

    @Test
    void createProducts_shouldIgnoreLowStockThresholdFromBody_whenUserNotWithAdminRole() {
        ProductDTO firstDTO = productInput("Krem do stóp");
        firstDTO.setLowStockThreshold(5);
        ProductDTO secondDTO = productInput("Krem do rąk");
        secondDTO.setLowStockThreshold(8);

        when(productRepo.findByName("Krem do stóp")).thenReturn(Optional.empty());
        when(productRepo.findByName("Krem do rąk")).thenReturn(Optional.empty());
        when(productRepo.save(any(Product.class))).thenReturn(savedProduct("Krem do stóp"));

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(() -> SessionUtils.hasRole(AppRoleHierarchy.COMPANY)).thenReturn(false);

            productService.createProducts(List.of(firstDTO, secondDTO));

            verify(productRepo, times(2)).save(captor.capture());
            assertTrue(captor.getAllValues().stream()
                    .allMatch(saved -> saved.getLowStockThreshold() == 1));
        }
    }

    @Test
    void createProduct_shouldThrowCreationException_whenAdminSendsNegativeLowStockThreshold() {
        ProductDTO inputDTO = productInput("Krem do stóp");
        inputDTO.setLowStockThreshold(-1);

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(() -> SessionUtils.hasRole(AppRoleHierarchy.COMPANY)).thenReturn(true);

            assertThrows(CreationException.class,
                    () -> productService.createProduct(inputDTO));

            verify(productRepo, never()).save(any(Product.class));
        }
    }

    @Test
    void updateProduct_shouldSetLowStockThreshold_whenUserWithAdminRole() {
        ProductDTO inputDTO = productInput("Krem do stóp");
        inputDTO.setLowStockThreshold(7);

        Product existingProduct = savedProduct("Krem do stóp");
        existingProduct.setLowStockThreshold(3);

        when(productRepo.findOneById(1L)).thenReturn(Optional.of(existingProduct));
        when(productRepo.findByName("Krem do stóp")).thenReturn(Optional.empty());
        when(productRepo.save(any(Product.class))).thenReturn(savedProduct("Krem do stóp"));

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(() -> SessionUtils.hasRole(AppRoleHierarchy.COMPANY)).thenReturn(true);

            productService.updateProduct(1L, inputDTO);

            verify(productRepo).save(captor.capture());
            assertEquals(7, captor.getValue().getLowStockThreshold());
        }
    }

    @Test
    void updateProduct_shouldKeepCurrentLowStockThreshold_whenAdminSendsNull() {
        ProductDTO inputDTO = productInput("Krem do stóp");

        Product existingProduct = savedProduct("Krem do stóp");
        existingProduct.setLowStockThreshold(3);

        when(productRepo.findOneById(1L)).thenReturn(Optional.of(existingProduct));
        when(productRepo.findByName("Krem do stóp")).thenReturn(Optional.empty());
        when(productRepo.save(any(Product.class))).thenReturn(savedProduct("Krem do stóp"));

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(() -> SessionUtils.hasRole(AppRoleHierarchy.COMPANY)).thenReturn(true);

            productService.updateProduct(1L, inputDTO);

            verify(productRepo).save(captor.capture());
            assertEquals(3, captor.getValue().getLowStockThreshold());
        }
    }

    @Test
    void updateProduct_shouldKeepCurrentLowStockThreshold_whenUserNotWithAdminRole() {
        ProductDTO inputDTO = productInput("Krem do stóp");
        inputDTO.setLowStockThreshold(9);

        Product existingProduct = savedProduct("Krem do stóp");
        existingProduct.setLowStockThreshold(3);

        when(productRepo.findOneById(1L)).thenReturn(Optional.of(existingProduct));
        when(productRepo.findByName("Krem do stóp")).thenReturn(Optional.empty());
        when(productRepo.save(any(Product.class))).thenReturn(savedProduct("Krem do stóp"));

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(() -> SessionUtils.hasRole(AppRoleHierarchy.COMPANY)).thenReturn(false);

            productService.updateProduct(1L, inputDTO);

            verify(productRepo).save(captor.capture());
            assertEquals(3, captor.getValue().getLowStockThreshold());
        }
    }

    @Test
    void updateProduct_shouldKeepCurrentLowStockThreshold_whenUserNotWithAdminRoleAndSendsNull() {
        ProductDTO inputDTO = productInput("Krem do stóp");

        Product existingProduct = savedProduct("Krem do stóp");
        existingProduct.setLowStockThreshold(3);

        when(productRepo.findOneById(1L)).thenReturn(Optional.of(existingProduct));
        when(productRepo.findByName("Krem do stóp")).thenReturn(Optional.empty());
        when(productRepo.save(any(Product.class))).thenReturn(savedProduct("Krem do stóp"));

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(() -> SessionUtils.hasRole(AppRoleHierarchy.COMPANY)).thenReturn(false);

            productService.updateProduct(1L, inputDTO);

            verify(productRepo).save(captor.capture());
            assertEquals(3, captor.getValue().getLowStockThreshold());
        }
    }

    @Test
    void deleteProductById_shouldRecordSupplyHistory_withProductDeletedSource_whenSoftDeleted() {
        Product product = Product.builder()
                .id(1L).name("Test").isDeleted(false).supply(7)
                .brand(Brand.builder().id(1L).name("Nike").build())
                .category(ProductCategory.builder().id(1L).name("Kremy").color("255,255,255").build())
                .build();

        when(productRepo.findOneById(1L)).thenReturn(Optional.of(product));
        when(productReferenceService.hasAnyReferences(1L)).thenReturn(true);

        productService.deleteProductById(1L);

        verify(supplyHistoryService).record(product, -7, SupplyChangeSource.PRODUCT_DELETED,
                1L, "Usunięcie produktu");
    }

    private static final String HIDDEN_CATEGORY_MESSAGE = "Kategoria „Jednorazowe” jest ukryta. Najpierw przywróć ją.";

    private static ProductCategory hiddenProductCategory() {
        return ProductCategory.builder().id(5L).name("Jednorazowe").color("1,1,1").isDeleted(true).build();
    }

    private static ProductCategory activeProductCategory() {
        return ProductCategory.builder().id(1L).name("Kremy").color("255,255,255").build();
    }

    private static Product storedProduct(boolean archived, ProductCategory category) {
        return Product.builder()
                .id(1L).name("Krem do stóp")
                .brand(Brand.builder().id(1L).name("Kremowo").build())
                .category(category)
                .supply(0)
                .isDeleted(archived)
                .build();
    }

    @Test
    void createProduct_shouldThrowConflict_whenTargetCategoryHidden() {
        ProductDTO inputDTO = productInput("Krem do stóp");
        inputDTO.setCategory(new ProductCategoryDTO(5L, "Jednorazowe", "1,1,1"));
        when(productRepo.findByName("Krem do stóp")).thenReturn(Optional.empty());
        when(productCategoryRepo.findByIdAndIsDeletedTrue(5L)).thenReturn(Optional.of(hiddenProductCategory()));

        ConflictException ex = assertThrows(ConflictException.class, () -> productService.createProduct(inputDTO));

        assertEquals(HIDDEN_CATEGORY_MESSAGE, ex.getMessage());
        verify(productRepo, never()).save(any());
        verifyNoInteractions(supplyHistoryService, auditLogService);
    }

    @Test
    void createProduct_shouldSave_whenTargetCategoryActive() {
        ProductDTO inputDTO = productInput("Krem do stóp");
        when(productRepo.findByName("Krem do stóp")).thenReturn(Optional.empty());
        when(productCategoryRepo.findByIdAndIsDeletedTrue(1L)).thenReturn(Optional.empty());
        when(productRepo.save(any(Product.class))).thenReturn(savedProduct("Krem do stóp"));

        ProductDTO result = productService.createProduct(inputDTO);

        assertEquals(1L, result.getId());
        assertEquals(1L, result.getCategory().getId());
        verify(productRepo).save(any(Product.class));
    }

    @Test
    void createProduct_shouldThrowConflict_whenRestoringArchivedProductIntoHiddenCategory() {
        Product archived = storedProduct(true, hiddenProductCategory());
        when(productRepo.findByName("Krem do stóp")).thenReturn(Optional.of(archived));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> productService.createProduct(productInput("Krem do stóp")));

        assertEquals(HIDDEN_CATEGORY_MESSAGE, ex.getMessage());
        assertTrue(archived.getIsDeleted());
        verify(productRepo, never()).save(any());
        verifyNoInteractions(supplyHistoryService, auditLogService);
    }

    @Test
    void createProduct_shouldRestoreArchivedProduct_whenItsCategoryActive() {
        Product archived = storedProduct(true, activeProductCategory());
        when(productRepo.findByName("Krem do stóp")).thenReturn(Optional.of(archived));
        when(productRepo.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductDTO result = productService.createProduct(productInput("Krem do stóp"));

        assertFalse(archived.getIsDeleted());
        assertFalse(result.getIsDeleted());
    }

    @Test
    void updateProduct_shouldThrowConflict_whenActiveProductMovedToHiddenCategory() {
        ProductDTO inputDTO = productInput("Krem do stóp");
        inputDTO.setCategory(new ProductCategoryDTO(5L, "Jednorazowe", "1,1,1"));
        when(productRepo.findOneById(1L)).thenReturn(Optional.of(storedProduct(false, activeProductCategory())));
        when(productRepo.findByName("Krem do stóp")).thenReturn(Optional.empty());
        when(productCategoryRepo.findByIdAndIsDeletedTrue(5L)).thenReturn(Optional.of(hiddenProductCategory()));

        ConflictException ex = assertThrows(ConflictException.class, () -> productService.updateProduct(1L, inputDTO));

        assertEquals(HIDDEN_CATEGORY_MESSAGE, ex.getMessage());
        verify(productRepo, never()).save(any());
    }

    @Test
    void updateProduct_shouldSave_whenArchivedProductStaysArchivedInItsHiddenCategory() {
        ProductDTO inputDTO = productInput("Krem do stóp");
        inputDTO.setCategory(new ProductCategoryDTO(5L, "Jednorazowe", "1,1,1"));
        inputDTO.setIsDeleted(true);
        inputDTO.setDescription("Nowy opis");
        when(productRepo.findOneById(1L)).thenReturn(Optional.of(storedProduct(true, hiddenProductCategory())));
        when(productRepo.findByName("Krem do stóp")).thenReturn(Optional.empty());
        when(productRepo.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductDTO result = productService.updateProduct(1L, inputDTO);

        assertTrue(result.getIsDeleted());
        assertEquals(5L, result.getCategory().getId());
        assertEquals("Nowy opis", result.getDescription());
        verify(productCategoryRepo, never()).findByIdAndIsDeletedTrue(any());
    }

    @Test
    void updateProduct_shouldThrowConflict_whenArchivedProductUnarchivedInHiddenCategory() {
        ProductDTO inputDTO = productInput("Krem do stóp");
        inputDTO.setCategory(new ProductCategoryDTO(5L, "Jednorazowe", "1,1,1"));
        inputDTO.setIsDeleted(false);
        when(productRepo.findOneById(1L)).thenReturn(Optional.of(storedProduct(true, hiddenProductCategory())));
        when(productRepo.findByName("Krem do stóp")).thenReturn(Optional.empty());
        when(productCategoryRepo.findByIdAndIsDeletedTrue(5L)).thenReturn(Optional.of(hiddenProductCategory()));

        ConflictException ex = assertThrows(ConflictException.class, () -> productService.updateProduct(1L, inputDTO));

        assertEquals(HIDDEN_CATEGORY_MESSAGE, ex.getMessage());
        verify(productRepo, never()).save(any());
    }

    @Test
    void updateProduct_shouldSave_whenMovedToActiveCategory() {
        ProductDTO inputDTO = productInput("Krem do stóp");
        inputDTO.setCategory(new ProductCategoryDTO(2L, "Narzędzia", "2,2,2"));
        when(productRepo.findOneById(1L)).thenReturn(Optional.of(storedProduct(false, activeProductCategory())));
        when(productRepo.findByName("Krem do stóp")).thenReturn(Optional.empty());
        when(productCategoryRepo.findByIdAndIsDeletedTrue(2L)).thenReturn(Optional.empty());
        when(productRepo.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductDTO result = productService.updateProduct(1L, inputDTO);

        assertEquals(2L, result.getCategory().getId());
        verify(productRepo).save(any(Product.class));
    }

    private static final String RAW_DB_ERROR =
            "duplicate key value violates unique constraint \"uk_product_name\" Detail: Key (phone)=(123456789)";

    @Test
    void createProduct_shouldNotExposeDatabaseErrorInMessage_whenSaveFails() {
        DataIntegrityViolationException dbError = new DataIntegrityViolationException(RAW_DB_ERROR);
        when(productRepo.findByName("Krem do stóp")).thenReturn(Optional.empty());
        when(productRepo.save(any(Product.class))).thenThrow(dbError);

        CreationException ex = assertThrows(CreationException.class,
                () -> productService.createProduct(productInput("Krem do stóp")));

        assertEquals("Failed to create Product.", ex.getMessage());
        assertFalse(ex.getMessage().contains("duplicate key"));
        assertFalse(ex.getMessage().contains("123456789"));
        assertSame(dbError, ex.getCause());
    }

    @Test
    void updateProduct_shouldNotExposeDatabaseErrorInMessage_whenSaveFails() {
        DataIntegrityViolationException dbError = new DataIntegrityViolationException(RAW_DB_ERROR);
        when(productRepo.findOneById(1L)).thenReturn(Optional.of(savedProduct("Krem do stóp")));
        when(productRepo.save(any(Product.class))).thenThrow(dbError);

        UpdateException ex = assertThrows(UpdateException.class,
                () -> productService.updateProduct(1L, productInput("Krem do stóp")));

        assertEquals("Failed to update Product.", ex.getMessage());
        assertFalse(ex.getMessage().contains("duplicate key"));
        assertFalse(ex.getMessage().contains("123456789"));
        assertSame(dbError, ex.getCause());
    }

    @Test
    void deleteProductById_shouldNotExposeDatabaseErrorInMessage_whenDeleteFails() {
        DataIntegrityViolationException dbError = new DataIntegrityViolationException(RAW_DB_ERROR);
        when(productRepo.findOneById(1L)).thenReturn(Optional.of(savedProduct("Krem do stóp")));
        when(productReferenceService.hasAnyReferences(1L)).thenReturn(false);
        doThrow(dbError).when(productRepo).deleteById(1L);

        DeletionException ex = assertThrows(DeletionException.class,
                () -> productService.deleteProductById(1L));

        assertEquals("Failed to delete Product.", ex.getMessage());
        assertFalse(ex.getMessage().contains("duplicate key"));
        assertFalse(ex.getMessage().contains("123456789"));
        assertSame(dbError, ex.getCause());
    }

    @Test
    void createProduct_shouldPassConflictExceptionThroughWithOriginalMessage_whenProductAlreadyExists() {
        when(productRepo.findByName("Krem do stóp")).thenReturn(Optional.of(savedProduct("Krem do stóp")));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> productService.createProduct(productInput("Krem do stóp")));

        assertEquals("Product already exists: Krem do stóp", ex.getMessage());
        assertTrue(ex.getMessage().contains("already exists"));
        assertNull(ex.getCause());
        verify(productRepo, never()).save(any(Product.class));
    }

    @Test
    void updateProduct_shouldPassConflictExceptionThroughWithOriginalMessage_whenNameTakenByOtherProduct() {
        Product other = savedProduct("Krem do stóp");
        other.setId(2L);
        when(productRepo.findOneById(1L)).thenReturn(Optional.of(savedProduct("Krem stary")));
        when(productRepo.findByName("Krem do stóp")).thenReturn(Optional.of(other));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> productService.updateProduct(1L, productInput("Krem do stóp")));

        assertEquals("Product with provided details already exists.", ex.getMessage());
        verify(productRepo, never()).save(any(Product.class));
    }
}
