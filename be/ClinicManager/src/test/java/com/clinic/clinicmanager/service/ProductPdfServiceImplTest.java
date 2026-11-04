package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.BrandDTO;
import com.clinic.clinicmanager.DTO.EmployeeSummaryDTO;
import com.clinic.clinicmanager.DTO.ProductCategoryDTO;
import com.clinic.clinicmanager.DTO.ProductDTO;
import com.clinic.clinicmanager.DTO.request.ProductFilterDTO;
import com.clinic.clinicmanager.config.security.services.UserDetailsImpl;
import com.clinic.clinicmanager.model.constants.Unit;
import com.clinic.clinicmanager.service.impl.ProductPdfServiceImpl;
import com.clinic.clinicmanager.utils.SessionUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductPdfServiceImplTest {

    @Mock ProductService productService;

    @InjectMocks
    ProductPdfServiceImpl productPdfService;

    private ProductDTO buildProductDTO(String name, String categoryName) {
        ProductDTO dto = new ProductDTO();
        dto.setId(1L);
        dto.setName(name);
        dto.setSupply(10);
        ProductCategoryDTO category = new ProductCategoryDTO(1L, categoryName, "#000000");
        dto.setCategory(category);
        BrandDTO brand = new BrandDTO(1L, "TestBrand");
        dto.setBrand(brand);
        dto.setVolume(100);
        dto.setUnit(Unit.ML);
        return dto;
    }

    @Test
    void generateInventoryReport_shouldReturnNonEmptyByteArray() {
        when(productService.getProducts(any())).thenReturn(List.of(buildProductDTO("Krem", "Produkty")));

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(SessionUtils::getUserDetailsFromSession).thenReturn(null);

            byte[] result = productPdfService.generateInventoryReport(new ProductFilterDTO());

            assertNotNull(result);
            assertTrue(result.length > 0);
        }
    }

    @Test
    void generateInventoryReport_shouldCallProductServiceWithFilter() {
        ProductFilterDTO filter = new ProductFilterDTO();
        when(productService.getProducts(filter)).thenReturn(List.of());

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(SessionUtils::getUserDetailsFromSession).thenReturn(null);

            productPdfService.generateInventoryReport(filter);

            verify(productService).getProducts(filter);
        }
    }

    @Test
    void generateInventoryReport_shouldWork_whenProductListIsEmpty() {
        when(productService.getProducts(any())).thenReturn(List.of());

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(SessionUtils::getUserDetailsFromSession).thenReturn(null);

            assertDoesNotThrow(() -> productPdfService.generateInventoryReport(new ProductFilterDTO()));
        }
    }

    @Test
    void generateInventoryReport_shouldGroupProductsByCategory() {
        List<ProductDTO> products = List.of(
                buildProductDTO("Krem A", "Produkty"),
                buildProductDTO("Krem B", "Produkty"),
                buildProductDTO("Nóż", "Narzędzia")
        );
        when(productService.getProducts(any())).thenReturn(products);

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(SessionUtils::getUserDetailsFromSession).thenReturn(null);

            byte[] result = productPdfService.generateInventoryReport(new ProductFilterDTO());

            assertNotNull(result);
            assertTrue(result.length > 0);
        }
    }

    @Test
    void generateInventoryReport_shouldIncludeEmployeeName_whenSessionPresent() {
        when(productService.getProducts(any())).thenReturn(List.of());

        UserDetailsImpl userDetails = mock(UserDetailsImpl.class);
        EmployeeSummaryDTO employee = mock(EmployeeSummaryDTO.class);
        when(userDetails.getEmployee()).thenReturn(employee);
        when(employee.getName()).thenReturn("Anna");

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(SessionUtils::getUserDetailsFromSession).thenReturn(userDetails);

            byte[] result = productPdfService.generateInventoryReport(new ProductFilterDTO());

            assertNotNull(result);
            assertTrue(result.length > 0);
        }
    }

    @Test
    void generateInventoryReport_shouldHandleProductWithNullVolume() {
        ProductDTO dto = buildProductDTO("Krem", "Produkty");
        dto.setVolume(null);
        dto.setUnit(null);
        when(productService.getProducts(any())).thenReturn(List.of(dto));

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(SessionUtils::getUserDetailsFromSession).thenReturn(null);

            assertDoesNotThrow(() -> productPdfService.generateInventoryReport(new ProductFilterDTO()));
        }
    }
}