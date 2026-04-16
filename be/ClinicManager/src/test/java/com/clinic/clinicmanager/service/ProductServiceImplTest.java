package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.BrandDTO;
import com.clinic.clinicmanager.DTO.ProductCategoryDTO;
import com.clinic.clinicmanager.DTO.ProductDTO;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.DeletionException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.model.Brand;
import com.clinic.clinicmanager.model.Product;
import com.clinic.clinicmanager.model.ProductCategory;
import com.clinic.clinicmanager.model.constants.RoleType;
import com.clinic.clinicmanager.model.constants.VatRate;
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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ProductServiceImplTest {

    @Mock
    ProductRepo productRepo;

    @Mock
    AuditLogService auditLogService;

    @Mock
    ProductReferenceService productReferenceService;

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
        inputDTO.setFallbackNetPurchasePrice(15.0);
        inputDTO.setFallbackVatRate(VatRate.VAT_23);

        Product savedEntity = Product.builder()
                .id(1L).name("Krem do stóp")
                .brand(Brand.builder().id(1L).name("Nike").build())
                .category(ProductCategory.builder().id(1L).name("Kremy").color("255,255,255").build())
                .fallbackNetPurchasePrice(15.0)
                .fallbackVatRate(VatRate.VAT_23)
                .build();


        when(productRepo.findByName("Krem do stóp")).thenReturn(Optional.empty());
        when(productRepo.save(any(Product.class))).thenReturn(savedEntity);

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(() -> SessionUtils.hasUserRole(RoleType.ROLE_ADMIN)).thenReturn(true);

            ProductDTO result = productService.createProduct(inputDTO);

            assertEquals(15.0, result.getFallbackNetPurchasePrice());
            assertEquals(VatRate.VAT_23, result.getFallbackVatRate());
        }
    }

    @Test
    void createProduct_shouldReturnProductDTOAndNotSetAdminFields_whenUserNotWithAdminRole() {
        ProductDTO inputDTO = new ProductDTO();
        inputDTO.setName("Krem do stóp");
        inputDTO.setBrand(new BrandDTO(1L, "Kremowo"));
        inputDTO.setCategory(new ProductCategoryDTO(1L, "Kremy", "255,255,255"));
        inputDTO.setFallbackNetPurchasePrice(15.0);
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
            mockedSession.when(() -> SessionUtils.hasUserRole(RoleType.ROLE_ADMIN)).thenReturn(false);

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
        inputDTO.setFallbackNetPurchasePrice(15.0);
        inputDTO.setFallbackVatRate(VatRate.VAT_23);

        Product savedEntity = Product.builder()
                .id(1L).name("Krem do stóp")
                .brand(Brand.builder().id(1L).name("Nike").build())
                .category(ProductCategory.builder().id(1L).name("Kremy").color("255,255,255").build())
                .fallbackNetPurchasePrice(15.0)
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
            mockedSession.when(() -> SessionUtils.hasUserRole(RoleType.ROLE_ADMIN)).thenReturn(true);

            ProductDTO result = productService.updateProduct(1L, inputDTO);

            assertEquals(15.0, result.getFallbackNetPurchasePrice());
            assertEquals(VatRate.VAT_23, result.getFallbackVatRate());
        }
    }

    @Test
    void updateProduct_shouldReturnProductDTOAndNotSetAdminFields_whenUserNotWithAdminRole() {
        ProductDTO inputDTO = new ProductDTO();
        inputDTO.setName("Krem do stóp");
        inputDTO.setBrand(new BrandDTO(1L, "Kremowo"));
        inputDTO.setCategory(new ProductCategoryDTO(1L, "Kremy", "255,255,255"));
        inputDTO.setFallbackNetPurchasePrice(15.0);
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
            mockedSession.when(() -> SessionUtils.hasUserRole(RoleType.ROLE_ADMIN)).thenReturn(false);

            productService.updateProduct(1L, inputDTO);

            verify(productRepo).save(captor.capture());
            Product captured = captor.getValue();

            assertNull(captured.getFallbackNetPurchasePrice());
            assertNull(captured.getFallbackVatRate());
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
}
