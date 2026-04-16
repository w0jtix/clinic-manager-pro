package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.BaseServiceCategoryDTO;
import com.clinic.clinicmanager.DTO.BaseServiceDTO;
import com.clinic.clinicmanager.DTO.BaseServiceVariantDTO;
import com.clinic.clinicmanager.DTO.request.ServiceFilterDTO;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.DeletionException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.model.BaseService;
import com.clinic.clinicmanager.model.BaseServiceCategory;
import com.clinic.clinicmanager.model.BaseServiceVariant;
import com.clinic.clinicmanager.repo.BaseServiceRepo;
import com.clinic.clinicmanager.repo.VisitItemRepo;
import com.clinic.clinicmanager.service.impl.BaseServiceServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BaseServiceServiceImplTest {

    @Mock BaseServiceRepo baseServiceRepo;
    @Mock VisitItemRepo visitItemRepo;
    @Mock AuditLogService auditLogService;

    @InjectMocks
    BaseServiceServiceImpl baseServiceService;

    private BaseServiceCategory category() {
        return BaseServiceCategory.builder().id(1L).name("Klasyczna").color("#fff").isDeleted(false).build();
    }

    private BaseService activeService(Long id, String name) {
        return BaseService.builder().id(id).name(name).price(50.0).duration(60)
                .category(category()).isDeleted(false).variants(new HashSet<>()).build();
    }

    private BaseServiceVariant variant(Long id, boolean isDeleted) {
        return BaseServiceVariant.builder().id(id).name("Wariant").price(60.0).duration(60).isDeleted(isDeleted).build();
    }

    @Test
    void getBaseServiceById_shouldReturnDTO_whenExists() {
        when(baseServiceRepo.findOneById(1L)).thenReturn(Optional.of(activeService(1L, "Masaż")));

        BaseServiceDTO result = baseServiceService.getBaseServiceById(1L);

        assertEquals(1L, result.getId());
        assertEquals("Masaż", result.getName());
    }

    @Test
    void getBaseServiceById_shouldThrowResourceNotFoundException_whenNotFound() {
        when(baseServiceRepo.findOneById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> baseServiceService.getBaseServiceById(99L));
    }

    @Test
    void getBaseServices_shouldUseEmptyFilter_whenFilterIsNull() {
        when(baseServiceRepo.findAllWithFilters(isNull(), isNull())).thenReturn(List.of());

        baseServiceService.getBaseServices(null);

        verify(baseServiceRepo).findAllWithFilters(isNull(), isNull());
    }

    @Test
    void getBaseServices_shouldPassFilterValues_whenFilterProvided() {
        ServiceFilterDTO filter = new ServiceFilterDTO();
        filter.setKeyword("masa");

        when(baseServiceRepo.findAllWithFilters(eq("masa"), isNull())).thenReturn(List.of());

        baseServiceService.getBaseServices(filter);

        verify(baseServiceRepo).findAllWithFilters(eq("masa"), isNull());
    }

    @Test
    void getBaseServicesByCategoryId_shouldReturnServicesForCategory() {
        when(baseServiceRepo.findAllByCategoryId(1L)).thenReturn(List.of(activeService(1L, "Masaż")));

        List<BaseServiceDTO> result = baseServiceService.getBaseServicesByCategoryId(1L);

        assertEquals(1, result.size());
    }

    @Test
    void createBaseService_shouldSaveAndReturnDTO_whenNameIsUnique() {
        BaseServiceCategoryDTO categoryDTO = new BaseServiceCategoryDTO(1L, "Klasyczna", "#fff", false);
        BaseServiceDTO input = new BaseServiceDTO(null, "Masaż", 50.0, 60, categoryDTO, false, List.of());
        BaseService saved = activeService(1L, "Masaż");

        when(baseServiceRepo.existsByName("Masaż")).thenReturn(false);
        when(baseServiceRepo.save(any())).thenReturn(saved);

        BaseServiceDTO result = baseServiceService.createBaseService(input);

        assertEquals(1L, result.getId());
        verify(auditLogService).logCreate(eq("BaseService"), eq(1L), anyString(), any());
    }

    @Test
    void createBaseService_shouldThrowConflictException_whenNameAlreadyExists() {
        BaseServiceCategoryDTO categoryDTO = new BaseServiceCategoryDTO(1L, "Klasyczna", "#fff", false);
        BaseServiceDTO input = new BaseServiceDTO(null, "Masaż", 50.0, 60, categoryDTO, false, List.of());

        when(baseServiceRepo.existsByName("Masaż")).thenReturn(true);

        assertThrows(ConflictException.class, () -> baseServiceService.createBaseService(input));
        verify(baseServiceRepo, never()).save(any());
    }

    @Test
    void updateBaseService_shouldSoftDeleteRemovedVariant_whenVariantHasVisitReferences() {
        BaseServiceVariant existingVariant = variant(10L, false);
        BaseService existing = activeService(1L, "Masaż");
        existing.getVariants().add(existingVariant);

        BaseServiceCategoryDTO categoryDTO = new BaseServiceCategoryDTO(1L, "Klasyczna", "#fff", false);
        BaseServiceDTO input = new BaseServiceDTO(null, "Masaż", 50.0, 60, categoryDTO, false, new ArrayList<>());

        when(baseServiceRepo.findOneById(1L)).thenReturn(Optional.of(existing));
        when(baseServiceRepo.findByName("Masaż")).thenReturn(Optional.of(existing));
        when(visitItemRepo.existsByServiceVariantId(10L)).thenReturn(true);
        when(baseServiceRepo.save(any())).thenReturn(existing);

        baseServiceService.updateBaseService(1L, input);

        assertTrue(existingVariant.getIsDeleted());
        ArgumentCaptor<BaseService> captor = ArgumentCaptor.forClass(BaseService.class);
        verify(baseServiceRepo).save(captor.capture());
        assertTrue(captor.getValue().getVariants().stream().anyMatch(v -> v.getId().equals(10L)));
    }

    @Test
    void updateBaseService_shouldNotKeepRemovedVariant_whenNoVisitReferences() {
        BaseServiceVariant existingVariant = variant(10L, false);
        BaseService existing = activeService(1L, "Masaż");
        existing.getVariants().add(existingVariant);

        BaseServiceCategoryDTO categoryDTO = new BaseServiceCategoryDTO(1L, "Klasyczna", "#fff", false);
        BaseServiceDTO input = new BaseServiceDTO(null, "Masaż", 50.0, 60, categoryDTO, false, new ArrayList<>());

        when(baseServiceRepo.findOneById(1L)).thenReturn(Optional.of(existing));
        when(baseServiceRepo.findByName("Masaż")).thenReturn(Optional.of(existing));
        when(visitItemRepo.existsByServiceVariantId(10L)).thenReturn(false);
        when(baseServiceRepo.save(any())).thenReturn(existing);

        baseServiceService.updateBaseService(1L, input);

        assertFalse(existingVariant.getIsDeleted());
        ArgumentCaptor<BaseService> captor = ArgumentCaptor.forClass(BaseService.class);
        verify(baseServiceRepo).save(captor.capture());
        assertTrue(captor.getValue().getVariants().stream().noneMatch(v -> v.getId().equals(10L)));
    }

    @Test
    void updateBaseService_shouldThrowResourceNotFoundException_whenNotFound() {
        when(baseServiceRepo.findOneById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> baseServiceService.updateBaseService(99L, new BaseServiceDTO()));
    }

    @Test
    void updateBaseService_shouldThrowConflictException_whenNameTakenByAnotherService() {
        BaseService existing = activeService(1L, "Masaż");
        BaseService other = activeService(2L, "Peeling");

        BaseServiceCategoryDTO categoryDTO = new BaseServiceCategoryDTO(1L, "Klasyczna", "#fff", false);
        BaseServiceDTO input = new BaseServiceDTO(null, "Peeling", 50.0, 60, categoryDTO, false, List.of());

        when(baseServiceRepo.findOneById(1L)).thenReturn(Optional.of(existing));
        when(baseServiceRepo.findByName("Peeling")).thenReturn(Optional.of(other));

        assertThrows(ConflictException.class, () -> baseServiceService.updateBaseService(1L, input));
    }

    @Test
    void deleteBaseServiceById_shouldThrowResourceNotFoundException_whenNotFound() {
        when(baseServiceRepo.findOneById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> baseServiceService.deleteBaseServiceById(99L));
    }

    @Test
    void deleteBaseServiceById_shouldThrowDeletionException_whenAlreadySoftDeleted() {
        BaseService deleted = BaseService.builder().id(1L).name("Masaż").price(50.0).duration(60)
                .category(category()).isDeleted(true).variants(new HashSet<>()).build();

        when(baseServiceRepo.findOneById(1L)).thenReturn(Optional.of(deleted));

        assertThrows(DeletionException.class, () -> baseServiceService.deleteBaseServiceById(1L));
    }

    @Test
    void deleteBaseServiceById_shouldSoftDelete_whenHasVisitReferences() {
        BaseService service = activeService(1L, "Masaż");
        when(baseServiceRepo.findOneById(1L)).thenReturn(Optional.of(service));
        when(visitItemRepo.existsByServiceId(1L)).thenReturn(true);

        baseServiceService.deleteBaseServiceById(1L);

        assertTrue(service.getIsDeleted());
        verify(baseServiceRepo).save(service);
        verify(baseServiceRepo, never()).deleteById(any());
    }

    @Test
    void deleteBaseServiceById_shouldPhysicallyDelete_whenNoVisitReferences() {
        BaseService service = activeService(1L, "Masaż");
        when(baseServiceRepo.findOneById(1L)).thenReturn(Optional.of(service));
        when(visitItemRepo.existsByServiceId(1L)).thenReturn(false);

        baseServiceService.deleteBaseServiceById(1L);

        verify(baseServiceRepo).deleteById(1L);
        verify(baseServiceRepo, never()).save(any());
    }
}
