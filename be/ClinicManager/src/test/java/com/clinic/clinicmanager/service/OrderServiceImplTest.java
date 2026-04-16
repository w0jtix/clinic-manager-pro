package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.OrderDTO;
import com.clinic.clinicmanager.DTO.OrderProductDTO;
import com.clinic.clinicmanager.DTO.ProductDTO;
import com.clinic.clinicmanager.DTO.SupplierDTO;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.model.*;
import com.clinic.clinicmanager.model.constants.VatRate;
import com.clinic.clinicmanager.repo.*;
import com.clinic.clinicmanager.service.impl.OrderServiceImpl;
import com.clinic.clinicmanager.model.Brand;
import com.clinic.clinicmanager.model.ProductCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OrderServiceImplTest {

    @Mock
    OrderRepo orderRepo;

    @Mock
    OrderProductRepo orderProductRepo;

    @Mock
    SupplierRepo supplierRepo;

    @Mock
    ProductRepo productRepo;

    @Mock
    CompanyExpenseRepo companyExpenseRepo;

    @Mock
    ProductReferenceService productReferenceService;

    @Mock
    AuditLogService auditLogService;

    @InjectMocks
    OrderServiceImpl orderService;

    private Supplier supplier;
    private Product product;
    private Order order;

    @BeforeEach
    void setUp() {
        ProductCategory category = ProductCategory.builder()
                .id(1L).name("Produkty").color("0,255,4").build();

        Brand brand = Brand.builder()
                .id(1L).name("Nike").build();

        supplier = Supplier.builder()
                .id(1L).name("Dostawca ABC").build();

        product = Product.builder()
                .id(1L).name("Krem do stóp")
                .category(category).brand(brand)
                .supply(10).isDeleted(false)
                .build();

        OrderProduct orderProduct = OrderProduct.builder()
                .id(1L).product(product).name("Krem do stóp")
                .quantity(5).vatRate(VatRate.VAT_23).price(12.30)
                .build();

        order = Order.builder()
                .id(1L).supplier(supplier).orderNumber(1L)
                .orderDate(LocalDate.of(2024, 1, 15))
                .shippingCost(10.0)
                .build();

        order.addOrderProduct(orderProduct);
    }

    @Test
    void getOrderById_shouldReturnOrderDTO_whenOrderFound() {
        when(orderRepo.findOneByIdWithDetails(1L)).thenReturn(Optional.of(order));

        OrderDTO result = orderService.getOrderById(1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("Dostawca ABC", result.getSupplier().getName());
    }

    @Test
    void getOrderById_shouldResourceNotFound_whenOrderNotFound() {
        when(orderRepo.findOneByIdWithDetails(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> orderService.getOrderById(99L));
    }

    @Test
    void getOrderPreview_shouldReturnOrderDTOWithCalculatedTotals() {
        OrderProductDTO opDTO = new OrderProductDTO();
        opDTO.setPrice(12.30);
        opDTO.setQuantity(5);
        opDTO.setVatRate(VatRate.VAT_23);

        OrderDTO inputDTO = new OrderDTO();
        inputDTO.setShippingCost(10.0);
        inputDTO.setOrderProducts(List.of(opDTO));

        OrderDTO result = orderService.getOrderPreview(inputDTO);

        assertNotNull(result.getTotalValue());
        assertNotNull(result.getTotalNet());
        assertNotNull(result.getTotalVat());
        assertEquals(71.50, result.getTotalValue());

    }

    @Test
    void getOrderPreview_shouldThrowResourceNotFoundException_whenSupplierNotFound() {
        SupplierDTO supplierDTO = new SupplierDTO(99L, "Nieznany", null);
        OrderDTO inputDTO = new OrderDTO();
        inputDTO.setSupplier(supplierDTO);
        inputDTO.setOrderProducts(List.of());

        when(supplierRepo.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> orderService.getOrderPreview(inputDTO));

    }

    @Test
    void createOrder_shouldReturnOrderDTO_whenSupplierExists() {
        OrderProductDTO opDTO = new OrderProductDTO();
        opDTO.setProduct(new ProductDTO());
        opDTO.getProduct().setId(1L);
        opDTO.setQuantity(5);
        opDTO.setVatRate(VatRate.VAT_23);
        opDTO.setPrice(12.30);

        SupplierDTO supplierDTO = new SupplierDTO(1L, "Dostawca ABC", null);

        OrderDTO inputDTO = new OrderDTO();
        inputDTO.setSupplier(supplierDTO);
        inputDTO.setOrderDate(LocalDate.of(2024, 1, 15));
        inputDTO.setShippingCost(10.0);
        inputDTO.setOrderProducts(List.of(opDTO));

        when(supplierRepo.findById(1L)).thenReturn(Optional.of(supplier));
        when(orderRepo.findTopByOrderByOrderNumberDesc()).thenReturn(Optional.of(order));
        when(productRepo.findById(1L)).thenReturn(Optional.of(product));
        when(orderRepo.save(any(Order.class))).thenReturn(order);

        OrderDTO result = orderService.createOrder(inputDTO);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        verify(orderRepo, times(1)).save(any(Order.class));
        verify(productRepo, times(1)).save(any(Product.class));
    }

    @Test
    void createOrder_shouldThrowResourceNotFound_whenSupplierNotFound() {
        SupplierDTO supplierDTO = new SupplierDTO(99L, "Nieznany", null);
        OrderDTO inputDTO = new OrderDTO();
        inputDTO.setSupplier(supplierDTO);
        inputDTO.setOrderProducts(List.of());

        when(supplierRepo.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> orderService.createOrder(inputDTO));
    }

    @Test
    void createOrder_shouldAssignOrderNumberOne_whenNoOrdersExist() {
        OrderProductDTO opDTO = new OrderProductDTO();
        opDTO.setProduct(new ProductDTO());
        opDTO.getProduct().setId(1L);
        opDTO.setQuantity(5);
        opDTO.setVatRate(VatRate.VAT_23);
        opDTO.setPrice(12.30);

        SupplierDTO supplierDTO = new SupplierDTO(1L, "Dostawca ABC", null);

        OrderDTO inputDTO = new OrderDTO();
        inputDTO.setSupplier(supplierDTO);
        inputDTO.setOrderDate(LocalDate.of(2024, 1, 15));
        inputDTO.setShippingCost(10.0);
        inputDTO.setOrderProducts(List.of(opDTO));

        when(supplierRepo.findById(1L)).thenReturn(Optional.of(supplier));
        when(orderRepo.findTopByOrderByOrderNumberDesc()).thenReturn(Optional.empty());
        when(productRepo.findById(1L)).thenReturn(Optional.of(product));
        when(orderRepo.save(any(Order.class))).thenReturn(order);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);

        orderService.createOrder(inputDTO);

        verify(orderRepo).save(captor.capture());
        assertEquals(1L, captor.getValue().getOrderNumber());
    }

    @Test
    void createOrder_shouldThrowResourceNotFound_whenProductNotFound() {
        OrderProductDTO opDTO = new OrderProductDTO();
        opDTO.setProduct(new ProductDTO());
        opDTO.getProduct().setId(99L);

        SupplierDTO supplierDTO = new SupplierDTO(1L, "Nieznany", null);

        OrderDTO inputDTO = new OrderDTO();
        inputDTO.setSupplier(supplierDTO);
        inputDTO.setOrderProducts(List.of(opDTO));

        when(supplierRepo.findById(1L)).thenReturn(Optional.of(supplier));
        when(orderRepo.findTopByOrderByOrderNumberDesc()).thenReturn(Optional.of(order));
        when(productRepo.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> orderService.createOrder(inputDTO));

    }

    @Test
    void createOrder_shouldRestoreProduct_whenProductFoundAndSoftDeleted() {
        OrderProductDTO opDTO = new OrderProductDTO();
        opDTO.setProduct(new ProductDTO());
        opDTO.getProduct().setId(1L);
        opDTO.setQuantity(5);
        opDTO.setVatRate(VatRate.VAT_23);
        opDTO.setPrice(12.30);

        SupplierDTO supplierDTO = new SupplierDTO(1L, "Dostawca ABC", null);

        OrderDTO inputDTO = new OrderDTO();
        inputDTO.setSupplier(supplierDTO);
        inputDTO.setOrderDate(LocalDate.of(2024, 1, 15));
        inputDTO.setShippingCost(10.0);
        inputDTO.setOrderProducts(List.of(opDTO));

        product.setIsDeleted(true);
        product.setSupply(0); ;

        when(supplierRepo.findById(1L)).thenReturn(Optional.of(supplier));
        when(orderRepo.findTopByOrderByOrderNumberDesc()).thenReturn(Optional.of(order));
        when(productRepo.findById(1L)).thenReturn(Optional.of(product));
        when(orderRepo.save(any(Order.class))).thenReturn(order);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);

        orderService.createOrder(inputDTO);
        verify(productRepo).save(captor.capture());
        Product captured = captor.getValue();

        assertFalse(captured.getIsDeleted());
        assertEquals(5, captured.getSupply());
    }

    @Test
    void createOrder_shouldSetProductFallbackNetPurchasePrice_whenCategoryProducts() {
        OrderProductDTO opDTO = new OrderProductDTO();
        opDTO.setProduct(new ProductDTO());
        opDTO.getProduct().setId(1L);
        opDTO.setQuantity(5);
        opDTO.setVatRate(VatRate.VAT_23);
        opDTO.setPrice(12.30);

        SupplierDTO supplierDTO = new SupplierDTO(1L, "Dostawca ABC", null);

        OrderDTO inputDTO = new OrderDTO();
        inputDTO.setSupplier(supplierDTO);
        inputDTO.setOrderDate(LocalDate.of(2024, 1, 15));
        inputDTO.setShippingCost(10.0);
        inputDTO.setOrderProducts(List.of(opDTO));

        when(supplierRepo.findById(1L)).thenReturn(Optional.of(supplier));
        when(orderRepo.findTopByOrderByOrderNumberDesc()).thenReturn(Optional.of(order));
        when(productRepo.findById(1L)).thenReturn(Optional.of(product));
        when(orderRepo.save(any(Order.class))).thenReturn(order);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);

        orderService.createOrder(inputDTO);
        verify(productRepo).save(captor.capture());
        Product captured = captor.getValue();

        assertNotNull(captured.getFallbackNetPurchasePrice());
        assertNotNull(captured.getFallbackVatRate());
    }

    @Test
    void createOrder_shouldNotSetProductFallbackNetPurchasePrice_whenCategoryNotProducts() {
        OrderProductDTO opDTO = new OrderProductDTO();
        opDTO.setProduct(new ProductDTO());
        opDTO.getProduct().setId(1L);
        opDTO.setQuantity(5);
        opDTO.setVatRate(VatRate.VAT_23);
        opDTO.setPrice(12.30);

        SupplierDTO supplierDTO = new SupplierDTO(1L, "Dostawca ABC", null);

        OrderDTO inputDTO = new OrderDTO();
        inputDTO.setSupplier(supplierDTO);
        inputDTO.setOrderDate(LocalDate.of(2024, 1, 15));
        inputDTO.setShippingCost(10.0);
        inputDTO.setOrderProducts(List.of(opDTO));

        product.setCategory(ProductCategory.builder()
                .id(2L).name("Narzędzia").color("2,235,24").build());

        when(supplierRepo.findById(1L)).thenReturn(Optional.of(supplier));
        when(orderRepo.findTopByOrderByOrderNumberDesc()).thenReturn(Optional.of(order));
        when(productRepo.findById(1L)).thenReturn(Optional.of(product));
        when(orderRepo.save(any(Order.class))).thenReturn(order);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);

        orderService.createOrder(inputDTO);
        verify(productRepo).save(captor.capture());
        Product captured = captor.getValue();

        assertNull(captured.getFallbackNetPurchasePrice());
        assertNull(captured.getFallbackVatRate());
    }

    @Test
    void updateOrder_shouldReturnOrderDTO_whenOrderSupplierFound() {
        OrderProductDTO opDTO = new OrderProductDTO();
        opDTO.setProduct(new ProductDTO());
        opDTO.getProduct().setId(1L);
        opDTO.setQuantity(5);
        opDTO.setVatRate(VatRate.VAT_23);
        opDTO.setPrice(12.30);

        SupplierDTO supplierDTO = new SupplierDTO(1L, "Dostawca ABC", null);

        OrderDTO inputDTO = new OrderDTO();
        inputDTO.setSupplier(supplierDTO);
        inputDTO.setOrderDate(LocalDate.of(2024, 1, 15));
        inputDTO.setShippingCost(15.0);
        inputDTO.setOrderProducts(List.of(opDTO));

        when(orderRepo.findOneByIdWithProducts(1L)).thenReturn(Optional.of(order));
        when(supplierRepo.findById(1L)).thenReturn(Optional.of(supplier));
        when(productRepo.findById(1L)).thenReturn(Optional.of(product));
        when(orderRepo.save(any(Order.class))).thenReturn(order);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);

        orderService.updateOrder(1L,inputDTO);

        verify(orderRepo).save(captor.capture());
        assertEquals(15.0, captor.getValue().getShippingCost());
    }

    @Test
    void updateOrder_returnResourceNotFound_whenOrderNotFound() {

        when(orderRepo.findOneByIdWithProducts(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> orderService.updateOrder(1L, new OrderDTO()));
    }

    @Test
    void updateOrder_returnResourceNotFound_whenSupplierNotFound() {

        SupplierDTO supplierDTO = new SupplierDTO(99L, "Nieznany", null);
        OrderDTO inputDTO = new OrderDTO();
        inputDTO.setSupplier(supplierDTO);
        inputDTO.setOrderProducts(List.of());

        when(orderRepo.findOneByIdWithProducts(1L)).thenReturn(Optional.of(order));
        when(supplierRepo.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> orderService.updateOrder(1L, inputDTO));
    }

    private OrderDTO buildInputDTO(int quantity) {
        OrderProductDTO opDTO = new OrderProductDTO();
        opDTO.setProduct(new ProductDTO());
        opDTO.getProduct().setId(1L);
        opDTO.setQuantity(quantity);
        opDTO.setVatRate(VatRate.VAT_23);
        opDTO.setPrice(12.30);

        OrderDTO inputDTO = new OrderDTO();
        inputDTO.setSupplier(new SupplierDTO(1L, "Dostawca ABC", null));
        inputDTO.setOrderDate(LocalDate.of(2024, 1, 15));
        inputDTO.setShippingCost(15.0);
        inputDTO.setOrderProducts(List.of(opDTO));
        return inputDTO;
    }

    @Test
    void updateOrder_shouldNotAdjustInventory_whenQuantityUnchanged() {
        // old=5, new=5, net=0 → saveAll nigdy nie wywołane
        OrderDTO inputDTO = buildInputDTO(5);

        when(orderRepo.findOneByIdWithProducts(1L)).thenReturn(Optional.of(order));
        when(supplierRepo.findById(1L)).thenReturn(Optional.of(supplier));
        when(productRepo.findById(1L)).thenReturn(Optional.of(product));
        when(orderRepo.save(any(Order.class))).thenReturn(order);

        orderService.updateOrder(1L, inputDTO);

        verify(productRepo, never()).saveAll(any());
    }

    @Test
    void updateOrder_shouldIncreaseInventory_whenQuantityIncreased() {
        // old=5, new=8, net=+3 → supply: 10 + 3 = 13
        OrderDTO inputDTO = buildInputDTO(8);

        when(orderRepo.findOneByIdWithProducts(1L)).thenReturn(Optional.of(order));
        when(supplierRepo.findById(1L)).thenReturn(Optional.of(supplier));
        when(productRepo.findById(1L)).thenReturn(Optional.of(product));
        when(productRepo.findAllById(any())).thenReturn(List.of(product));
        when(orderRepo.save(any(Order.class))).thenReturn(order);

        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);

        orderService.updateOrder(1L, inputDTO);

        verify(productRepo).saveAll(captor.capture());
        assertEquals(13, ((Product) captor.getValue().getFirst()).getSupply());
    }

    @Test
    void updateOrder_shouldDecreaseInventory_whenQuantityDecreased() {
        // old=5, new=3, net=-2 → supply: 10 - 2 = 8
        OrderDTO inputDTO = buildInputDTO(3);

        when(orderRepo.findOneByIdWithProducts(1L)).thenReturn(Optional.of(order));
        when(supplierRepo.findById(1L)).thenReturn(Optional.of(supplier));
        when(productRepo.findById(1L)).thenReturn(Optional.of(product));
        when(productRepo.findAllById(any())).thenReturn(List.of(product));
        when(orderRepo.save(any(Order.class))).thenReturn(order);

        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);

        orderService.updateOrder(1L, inputDTO);

        verify(productRepo).saveAll(captor.capture());
        assertEquals(8, ((Product) captor.getValue().getFirst()).getSupply());
    }

    @Test
    void updateOrder_shouldClampInventoryToZero_whenDecreaseBeyondStock() {
        // old=5, new=1, supply=3, net=-4 → supply: max(3-4, 0) = 0
        product.setSupply(3);
        OrderDTO inputDTO = buildInputDTO(1);

        when(orderRepo.findOneByIdWithProducts(1L)).thenReturn(Optional.of(order));
        when(supplierRepo.findById(1L)).thenReturn(Optional.of(supplier));
        when(productRepo.findById(1L)).thenReturn(Optional.of(product));
        when(productRepo.findAllById(any())).thenReturn(List.of(product));
        when(orderRepo.save(any(Order.class))).thenReturn(order);

        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);

        orderService.updateOrder(1L, inputDTO);

        verify(productRepo).saveAll(captor.capture());
        assertEquals(0, ((Product) captor.getValue().getFirst()).getSupply());
    }

    @Test
    void updateOrder_shouldAggregateInventoryAdjustments_whenSameProductAppearsMultipleTimes() {
        OrderProductDTO opDTO1 = new OrderProductDTO();
        opDTO1.setProduct(new ProductDTO());
        opDTO1.getProduct().setId(1L);
        opDTO1.setQuantity(3);
        opDTO1.setVatRate(VatRate.VAT_23);
        opDTO1.setPrice(10.00);

        OrderProductDTO opDTO2 = new OrderProductDTO();
        opDTO2.setProduct(new ProductDTO());
        opDTO2.getProduct().setId(1L);
        opDTO2.setQuantity(4);
        opDTO2.setVatRate(VatRate.VAT_23);
        opDTO2.setPrice(12.30);

        OrderDTO inputDTO = new OrderDTO();
        inputDTO.setSupplier(new SupplierDTO(1L, "Dostawca ABC", null));
        inputDTO.setOrderDate(LocalDate.of(2024, 1, 15));
        inputDTO.setShippingCost(10.0);
        inputDTO.setOrderProducts(List.of(opDTO1, opDTO2));

        when(orderRepo.findOneByIdWithProducts(1L)).thenReturn(Optional.of(order));
        when(supplierRepo.findById(1L)).thenReturn(Optional.of(supplier));
        when(productRepo.findById(1L)).thenReturn(Optional.of(product));
        when(productRepo.findAllById(any())).thenReturn(List.of(product));
        when(orderRepo.save(any(Order.class))).thenReturn(order);

        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);

        orderService.updateOrder(1L, inputDTO);

        verify(productRepo).saveAll(captor.capture());
        // net = (3+4) - 5 = +2 → supply: 10 + 2 = 12
        assertEquals(12, ((Product) captor.getValue().getFirst()).getSupply());
    }

    @Test
    void updateOrder_shouldThrowResourceNotFoundException_whenProductNotFound() {
        OrderProductDTO opDTO = new OrderProductDTO();
        opDTO.setProduct(new ProductDTO());                                                                                                            opDTO.getProduct().setId(99L);
        opDTO.setQuantity(3);
        opDTO.setVatRate(VatRate.VAT_23);
        opDTO.setPrice(12.30);

        OrderDTO inputDTO = new OrderDTO();
        inputDTO.setSupplier(new SupplierDTO(1L, "Dostawca ABC", null));
        inputDTO.setOrderDate(LocalDate.of(2024, 1, 15));
        inputDTO.setShippingCost(10.0);
        inputDTO.setOrderProducts(List.of(opDTO));

        when(orderRepo.findOneByIdWithProducts(1L)).thenReturn(Optional.of(order));
        when(supplierRepo.findById(1L)).thenReturn(Optional.of(supplier));
        when(productRepo.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> orderService.updateOrder(1L, inputDTO));
    }

    @Test
    void updateOrder_shouldRestoreProduct_whenProductSoftDeleted() {
        product.setIsDeleted(true);

        OrderProductDTO opDTO = new OrderProductDTO();
        opDTO.setProduct(new ProductDTO());
        opDTO.getProduct().setId(1L);
        opDTO.setQuantity(2);
        opDTO.setVatRate(VatRate.VAT_23);
        opDTO.setPrice(12.30);

        OrderDTO inputDTO = new OrderDTO();
        inputDTO.setSupplier(new SupplierDTO(1L, "Dostawca ABC", null));
        inputDTO.setOrderDate(LocalDate.of(2024, 1, 15));
        inputDTO.setShippingCost(10.0);
        inputDTO.setOrderProducts(List.of(opDTO));

        when(orderRepo.findOneByIdWithProducts(1L)).thenReturn(Optional.of(order));
        when(supplierRepo.findById(1L)).thenReturn(Optional.of(supplier));
        when(productRepo.findById(1L)).thenReturn(Optional.of(product));
        when(productRepo.findAllById(any())).thenReturn(List.of(product));
        when(orderRepo.save(any(Order.class))).thenReturn(order);

        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);

        orderService.updateOrder(1L, inputDTO);

        verify(productRepo).saveAll(captor.capture());
        assertFalse(((Product) captor.getValue().getFirst()).getIsDeleted());
    }

    @Test
    void updateOrder_shouldDeleteProduct_whenRemovedFromOrderAndSoftDeletedAndNoOtherReferences() {
        product.setIsDeleted(true);

        OrderDTO inputDTO = new OrderDTO();
        inputDTO.setSupplier(new SupplierDTO(1L, "Dostawca ABC", null));
        inputDTO.setOrderDate(LocalDate.of(2024, 1, 15));
        inputDTO.setShippingCost(10.0);
        inputDTO.setOrderProducts(List.of());

        when(orderRepo.findOneByIdWithProducts(1L)).thenReturn(Optional.of(order));
        when(supplierRepo.findById(1L)).thenReturn(Optional.of(supplier));
        when(productRepo.findAllById(any())).thenReturn(List.of(product));
        when(productRepo.findById(1L)).thenReturn(Optional.of(product));
        when(productReferenceService.hasAnyReferencesExcluding(1L, 1L)).thenReturn(false);
        when(orderRepo.save(any(Order.class))).thenReturn(order);

        orderService.updateOrder(1L, inputDTO);

        verify(productRepo, times(1)).delete(product);
    }

    @Test
    void updateOrder_shouldNotDeleteProduct_whenRemovedFromOrderButHasOtherReferences() {
        product.setIsDeleted(true);

        OrderDTO inputDTO = new OrderDTO();
        inputDTO.setSupplier(new SupplierDTO(1L, "Dostawca ABC", null));
        inputDTO.setOrderDate(LocalDate.of(2024, 1, 15));
        inputDTO.setShippingCost(10.0);
        inputDTO.setOrderProducts(List.of());

        when(orderRepo.findOneByIdWithProducts(1L)).thenReturn(Optional.of(order));
        when(supplierRepo.findById(1L)).thenReturn(Optional.of(supplier));
        when(productRepo.findAllById(any())).thenReturn(List.of(product));
        when(productRepo.findById(1L)).thenReturn(Optional.of(product));
        when(productReferenceService.hasAnyReferencesExcluding(1L, 1L)).thenReturn(true);
        when(orderRepo.save(any(Order.class))).thenReturn(order);

        orderService.updateOrder(1L, inputDTO);

        verify(productRepo, never()).delete(any());
    }

    @Test
    void updateOrder_shouldNotDeleteProduct_whenRemovedFromOrderButNotSoftDeleted() {

        OrderDTO inputDTO = new OrderDTO();
        inputDTO.setSupplier(new SupplierDTO(1L, "Dostawca ABC", null));
        inputDTO.setOrderDate(LocalDate.of(2024, 1, 15));
        inputDTO.setShippingCost(10.0);
        inputDTO.setOrderProducts(List.of());

        when(orderRepo.findOneByIdWithProducts(1L)).thenReturn(Optional.of(order));
        when(supplierRepo.findById(1L)).thenReturn(Optional.of(supplier));
        when(productRepo.findAllById(any())).thenReturn(List.of(product));
        when(productRepo.findById(1L)).thenReturn(Optional.of(product));
        when(orderRepo.save(any(Order.class))).thenReturn(order);

        orderService.updateOrder(1L, inputDTO);

        verify(productRepo, never()).delete(any());
    }

    @Test
    void deleteOrderById_shouldDeleteOrder_whenOrderFound() {
        when(orderRepo.findOneByIdWithProducts(1L)).thenReturn(Optional.of(order));
        when(companyExpenseRepo.existsByOrderId(1L)).thenReturn(false);

        orderService.deleteOrderById(1L);

        verify(orderRepo, times(1)).delete(any(Order.class));
        verify(orderRepo, times(1)).flush();

    }

    @Test
    void deleteOrderById_shouldThrowResourceNotFoundException_whenOrderNotFound() {
        when(orderRepo.findOneByIdWithProducts(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> orderService.deleteOrderById(1L));

        verify(orderRepo, never()).delete(any());
    }

    @Test
    void deleteOrderById_shouldThrowConflictException_whenExpenseByOrderExists() {
        when(orderRepo.findOneByIdWithProducts(1L)).thenReturn(Optional.of(order));
        when(companyExpenseRepo.existsByOrderId(1L)).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> orderService.deleteOrderById(1L));

        verify(orderRepo, never()).deleteById(any());
    }

    @Test
    void deleteOrderById_shouldApplyInventoryAdjustments() {
        when(orderRepo.findOneByIdWithProducts(1L)).thenReturn(Optional.of(order));
        when(companyExpenseRepo.existsByOrderId(1L)).thenReturn(false);

        when(productRepo.findAllById(any())).thenReturn(List.of(product));

        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);

        orderService.deleteOrderById(1L);

        verify(productRepo).saveAll(captor.capture());
        List<Product> captured = captor.getValue();

        assertEquals(5, ((Product) captured.getFirst()).getSupply());
    }

    @Test
    void deleteOrderById_shouldDeleteProduct_whenAlreadySoftDeleted() {
        product.setIsDeleted(true);

        when(orderRepo.findOneByIdWithProducts(1L)).thenReturn(Optional.of(order));
        when(companyExpenseRepo.existsByOrderId(1L)).thenReturn(false);
        when(productRepo.findById(1L)).thenReturn(Optional.of(product));
        when(productReferenceService.hasAnyReferencesExcluding(1L,1L)).thenReturn(false);

        orderService.deleteOrderById(1L);

        verify(productRepo, times(1)).delete(product);
    }

    @Test
    void deleteOrderById_shouldNotDelete_whenNotSoftDeleted() {
        product.setIsDeleted(false);

        when(orderRepo.findOneByIdWithProducts(1L)).thenReturn(Optional.of(order));
        when(companyExpenseRepo.existsByOrderId(1L)).thenReturn(false);

        orderService.deleteOrderById(1L);

        verify(productRepo, never()).delete(any());
    }

    @Test
    void deleteOrderById_shouldNotDeleteProduct_whenSoftDeletedButHasOtherReferences() {
        product.setIsDeleted(true);

        when(orderRepo.findOneByIdWithProducts(1L)).thenReturn(Optional.of(order));
        when(companyExpenseRepo.existsByOrderId(1L)).thenReturn(false);
        when(productRepo.findById(1L)).thenReturn(Optional.of(product));
        when(productReferenceService.hasAnyReferencesExcluding(1L, 1L)).thenReturn(true);

        orderService.deleteOrderById(1L);

        verify(productRepo, never()).delete(any());
    }



    }
