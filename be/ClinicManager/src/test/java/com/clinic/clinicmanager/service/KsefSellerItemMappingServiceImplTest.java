package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.model.KsefSeller;
import com.clinic.clinicmanager.model.KsefSellerItemMapping;
import com.clinic.clinicmanager.model.Product;
import com.clinic.clinicmanager.repo.KsefSellerItemMappingRepo;
import com.clinic.clinicmanager.service.impl.KsefSellerItemMappingServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KsefSellerItemMappingServiceImplTest {

    @Mock
    private KsefSellerItemMappingRepo mappingRepo;

    @InjectMocks
    private KsefSellerItemMappingServiceImpl mappingService;

    private KsefSeller seller() {
        return KsefSeller.builder().id(10L).nip("1234567890").name("S").build();
    }

    private Product product(Long id) {
        return Product.builder().id(id).name("Product " + id).build();
    }

    private Product deletedProduct(Long id) {
        return Product.builder().id(id).name("Product " + id).isDeleted(true).build();
    }

    @Test
    void findFor_returnsByGtin_whenGtinMatchesFirst() {
        KsefSellerItemMapping byGtin = KsefSellerItemMapping.builder()
                .id(1L).product(product(50L)).build();
        when(mappingRepo.findBySellerIdAndKsefGtin(10L, "5901234567890"))
                .thenReturn(Optional.of(byGtin));

        Optional<KsefSellerItemMapping> result = mappingService.findFor(10L, "Krem", "5901234567890");

        assertTrue(result.isPresent());
        assertSame(byGtin, result.get());
        verify(mappingRepo, never()).findBySellerIdAndKsefItemName(any(), any());
    }

    @Test
    void findFor_fallsBackToName_whenGtinMissesOrAbsent() {
        KsefSellerItemMapping byName = KsefSellerItemMapping.builder()
                .id(2L).product(product(50L)).build();
        when(mappingRepo.findBySellerIdAndKsefGtin(10L, "GTIN"))
                .thenReturn(Optional.empty());
        when(mappingRepo.findBySellerIdAndKsefItemName(10L, "Krem"))
                .thenReturn(Optional.of(byName));

        Optional<KsefSellerItemMapping> result = mappingService.findFor(10L, "Krem", "GTIN");

        assertTrue(result.isPresent());
        assertSame(byName, result.get());
    }

    @Test
    void findFor_returnsEmpty_whenMatchByGtinReferencesDeletedProduct() {
        KsefSellerItemMapping byGtin = KsefSellerItemMapping.builder()
                .id(1L).product(deletedProduct(50L)).build();
        when(mappingRepo.findBySellerIdAndKsefGtin(10L, "GTIN"))
                .thenReturn(Optional.of(byGtin));
        when(mappingRepo.findBySellerIdAndKsefItemName(10L, "Krem"))
                .thenReturn(Optional.empty());

        Optional<KsefSellerItemMapping> result = mappingService.findFor(10L, "Krem", "GTIN");

        assertTrue(result.isEmpty());
    }

    @Test
    void findFor_returnsEmpty_whenMatchByNameReferencesDeletedProduct() {
        KsefSellerItemMapping byName = KsefSellerItemMapping.builder()
                .id(2L).product(deletedProduct(50L)).build();
        when(mappingRepo.findBySellerIdAndKsefItemName(10L, "Krem"))
                .thenReturn(Optional.of(byName));

        Optional<KsefSellerItemMapping> result = mappingService.findFor(10L, "Krem", null);

        assertTrue(result.isEmpty());
    }

    @Test
    void findFor_returnsEmpty_whenNothingMatches() {
        when(mappingRepo.findBySellerIdAndKsefGtin(any(), any())).thenReturn(Optional.empty());
        when(mappingRepo.findBySellerIdAndKsefItemName(any(), any())).thenReturn(Optional.empty());

        Optional<KsefSellerItemMapping> result = mappingService.findFor(10L, "Krem", "GTIN");

        assertTrue(result.isEmpty());
    }

    @Test
    void findFor_returnsEmpty_whenSellerIdNull() {
        Optional<KsefSellerItemMapping> result = mappingService.findFor(null, "Krem", "GTIN");

        assertTrue(result.isEmpty());
        verifyNoInteractions(mappingRepo);
    }

    @Test
    void saveOrUpdate_createsNewMapping_whenNoneExists() {
        KsefSeller s = seller();
        Product p = product(50L);
        when(mappingRepo.findBySellerIdAndKsefGtin(10L, "GTIN")).thenReturn(Optional.empty());
        when(mappingRepo.findBySellerIdAndKsefItemName(10L, "Krem")).thenReturn(Optional.empty());
        when(mappingRepo.save(any(KsefSellerItemMapping.class))).thenAnswer(inv -> inv.getArgument(0));

        mappingService.saveOrUpdate(s, "Krem", "GTIN", p);

        ArgumentCaptor<KsefSellerItemMapping> captor = ArgumentCaptor.forClass(KsefSellerItemMapping.class);
        verify(mappingRepo).save(captor.capture());
        KsefSellerItemMapping saved = captor.getValue();
        assertSame(s, saved.getSeller());
        assertEquals("Krem", saved.getKsefItemName());
        assertEquals("GTIN", saved.getKsefGtin());
        assertSame(p, saved.getProduct());
    }

    @Test
    void saveOrUpdate_updatesExistingMapping_matchedByGtin() {
        KsefSeller s = seller();
        Product oldProduct = product(40L);
        Product newProduct = product(50L);
        KsefSellerItemMapping existing = KsefSellerItemMapping.builder()
                .id(1L)
                .seller(s)
                .ksefItemName("Old name")
                .ksefGtin("GTIN")
                .product(oldProduct)
                .build();
        when(mappingRepo.findBySellerIdAndKsefGtin(10L, "GTIN")).thenReturn(Optional.of(existing));
        when(mappingRepo.save(any(KsefSellerItemMapping.class))).thenAnswer(inv -> inv.getArgument(0));

        mappingService.saveOrUpdate(s, "New name", "GTIN", newProduct);

        assertEquals("New name", existing.getKsefItemName());
        assertSame(newProduct, existing.getProduct());
    }

    @Test
    void saveOrUpdate_updatesExistingMapping_pointingToSoftDeletedProduct_matchedByName() {
        KsefSeller s = seller();
        Product deletedOld = deletedProduct(40L);
        Product newProduct = product(50L);
        KsefSellerItemMapping existing = KsefSellerItemMapping.builder()
                .id(1L)
                .seller(s)
                .ksefItemName("Krem")
                .ksefGtin(null)
                .product(deletedOld)
                .build();
        when(mappingRepo.findBySellerIdAndKsefItemName(10L, "Krem")).thenReturn(Optional.of(existing));
        when(mappingRepo.save(any(KsefSellerItemMapping.class))).thenAnswer(inv -> inv.getArgument(0));

        mappingService.saveOrUpdate(s, "Krem", null, newProduct);

        ArgumentCaptor<KsefSellerItemMapping> captor = ArgumentCaptor.forClass(KsefSellerItemMapping.class);
        verify(mappingRepo).save(captor.capture());
        assertSame(existing, captor.getValue());
        assertSame(newProduct, existing.getProduct());
        assertEquals("Krem", existing.getKsefItemName());
    }

    @Test
    void saveOrUpdate_isNoOp_whenSellerNull() {
        mappingService.saveOrUpdate(null, "Krem", "GTIN", product(50L));

        verifyNoInteractions(mappingRepo);
    }

    @Test
    void saveOrUpdate_isNoOp_whenItemNameBlank() {
        mappingService.saveOrUpdate(seller(), "  ", "GTIN", product(50L));

        verifyNoInteractions(mappingRepo);
    }

    @Test
    void saveOrUpdate_isNoOp_whenProductNull() {
        mappingService.saveOrUpdate(seller(), "Krem", "GTIN", null);

        verifyNoInteractions(mappingRepo);
    }
}
