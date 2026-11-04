package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.BaseServiceCategoryDTO;
import com.clinic.clinicmanager.DTO.BaseServiceDTO;
import com.clinic.clinicmanager.DTO.BaseServiceVariantDTO;
import com.clinic.clinicmanager.DTO.request.ServiceFilterDTO;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.DeletionException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.UpdateException;
import com.clinic.clinicmanager.model.BaseService;
import com.clinic.clinicmanager.model.BaseServiceCategory;
import com.clinic.clinicmanager.model.BaseServiceVariant;
import com.clinic.clinicmanager.repo.BaseServiceCategoryRepo;
import com.clinic.clinicmanager.repo.BaseServiceRepo;
import com.clinic.clinicmanager.repo.VisitItemRepo;
import com.clinic.clinicmanager.service.impl.BaseServiceServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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
    @Mock BaseServiceCategoryRepo serviceCategoryRepo;
    @Mock VisitItemRepo visitItemRepo;
    @Mock AuditLogService auditLogService;

    @InjectMocks
    BaseServiceServiceImpl baseServiceService;

    private BaseServiceCategory category() {
        return BaseServiceCategory.builder().id(1L).name("Klasyczna").color("#fff").isDeleted(false).build();
    }

    private BaseService activeService(Long id, String name) {
        return BaseService.builder().id(id).name(name).price(BigDecimal.valueOf(50.0)).duration(60)
                .category(category()).isDeleted(false).variants(new HashSet<>()).build();
    }

    private BaseService orderedService(Long id, String name, int displayOrder) {
        BaseService service = activeService(id, name);
        service.setDisplayOrder(displayOrder);
        return service;
    }

    private BaseServiceVariant variant(Long id, boolean isDeleted) {
        return BaseServiceVariant.builder().id(id).name("Wariant").price(BigDecimal.valueOf(60.0)).duration(60).isDeleted(isDeleted).build();
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
        BaseServiceDTO input = new BaseServiceDTO(null, "Masaż", BigDecimal.valueOf(50.0), 60, categoryDTO, 0, false, List.of());
        BaseService saved = activeService(1L, "Masaż");

        when(baseServiceRepo.existsByName("Masaż")).thenReturn(false);
        when(baseServiceRepo.findMaxDisplayOrder()).thenReturn(-1);
        when(baseServiceRepo.save(any())).thenReturn(saved);

        BaseServiceDTO result = baseServiceService.createBaseService(input);

        assertEquals(1L, result.getId());
        verify(auditLogService).logCreate(eq("BaseService"), eq(1L), anyString(), any());
    }

    @Test
    void createBaseService_shouldThrowConflictException_whenNameAlreadyExists() {
        BaseServiceCategoryDTO categoryDTO = new BaseServiceCategoryDTO(1L, "Klasyczna", "#fff", false);
        BaseServiceDTO input = new BaseServiceDTO(null, "Masaż", BigDecimal.valueOf(50.0), 60, categoryDTO, 0, false, List.of());

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
        BaseServiceDTO input = new BaseServiceDTO(null, "Masaż", BigDecimal.valueOf(50.0), 60, categoryDTO, 0, false, new ArrayList<>());

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
        BaseServiceDTO input = new BaseServiceDTO(null, "Masaż", BigDecimal.valueOf(50.0), 60, categoryDTO, 0, false, new ArrayList<>());

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
        BaseServiceDTO input = new BaseServiceDTO(null, "Peeling", BigDecimal.valueOf(50.0), 60, categoryDTO, 0, false, List.of());

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
        BaseService deleted = BaseService.builder().id(1L).name("Masaż").price(BigDecimal.valueOf(50.0)).duration(60)
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

    @Test
    void createBaseService_shouldAssignNextDisplayOrder_whenServicesAlreadyExist() {
        BaseServiceCategoryDTO categoryDTO = new BaseServiceCategoryDTO(1L, "Klasyczna", "#fff", false);
        BaseServiceDTO input = new BaseServiceDTO(null, "Masaż", BigDecimal.valueOf(50.0), 60, categoryDTO, 0, false, List.of());

        when(baseServiceRepo.existsByName("Masaż")).thenReturn(false);
        when(baseServiceRepo.findMaxDisplayOrder()).thenReturn(5);
        when(baseServiceRepo.save(any())).thenReturn(orderedService(1L, "Masaż", 6));

        baseServiceService.createBaseService(input);

        ArgumentCaptor<BaseService> captor = ArgumentCaptor.forClass(BaseService.class);
        verify(baseServiceRepo).save(captor.capture());
        assertEquals(6, captor.getValue().getDisplayOrder());
    }

    @Test
    void createBaseService_shouldAssignZeroDisplayOrder_whenNoServicesExist() {
        BaseServiceCategoryDTO categoryDTO = new BaseServiceCategoryDTO(1L, "Klasyczna", "#fff", false);
        BaseServiceDTO input = new BaseServiceDTO(null, "Masaż", BigDecimal.valueOf(50.0), 60, categoryDTO, 0, false, List.of());

        when(baseServiceRepo.existsByName("Masaż")).thenReturn(false);
        when(baseServiceRepo.findMaxDisplayOrder()).thenReturn(-1);
        when(baseServiceRepo.save(any())).thenReturn(orderedService(1L, "Masaż", 0));

        baseServiceService.createBaseService(input);

        ArgumentCaptor<BaseService> captor = ArgumentCaptor.forClass(BaseService.class);
        verify(baseServiceRepo).save(captor.capture());
        assertEquals(0, captor.getValue().getDisplayOrder());
    }

    @Test
    void updateBaseService_shouldPreserveDisplayOrderOfExistingService() {
        BaseService existing = orderedService(1L, "Masaż", 7);

        BaseServiceCategoryDTO categoryDTO = new BaseServiceCategoryDTO(1L, "Klasyczna", "#fff", false);
        BaseServiceDTO input = new BaseServiceDTO(null, "Masaż", BigDecimal.valueOf(80.0), 90, categoryDTO, 0, false, new ArrayList<>());

        when(baseServiceRepo.findOneById(1L)).thenReturn(Optional.of(existing));
        when(baseServiceRepo.findByName("Masaż")).thenReturn(Optional.of(existing));
        when(baseServiceRepo.save(any())).thenReturn(existing);

        baseServiceService.updateBaseService(1L, input);

        ArgumentCaptor<BaseService> captor = ArgumentCaptor.forClass(BaseService.class);
        verify(baseServiceRepo).save(captor.capture());
        assertEquals(7, captor.getValue().getDisplayOrder());
    }

    @Test
    void moveService_shouldShiftDownAndTakeAnchorPosition_whenMovedDownwards() {
        BaseService moved = orderedService(1L, "Masaż", 1);
        BaseService anchor = orderedService(2L, "Peeling", 3);

        when(baseServiceRepo.findOneById(1L)).thenReturn(Optional.of(moved));
        when(baseServiceRepo.findOneById(2L)).thenReturn(Optional.of(anchor));

        baseServiceService.moveService(1L, 2L);

        verify(baseServiceRepo).shiftDown(1, 3);
        verify(baseServiceRepo, never()).shiftUp(anyInt(), anyInt());
        assertEquals(3, moved.getDisplayOrder());
        verify(baseServiceRepo).save(moved);
    }

    @Test
    void moveService_shouldShiftUpAndLandRightAfterAnchor_whenMovedUpwards() {
        BaseService moved = orderedService(1L, "Masaż", 4);
        BaseService anchor = orderedService(2L, "Peeling", 1);

        when(baseServiceRepo.findOneById(1L)).thenReturn(Optional.of(moved));
        when(baseServiceRepo.findOneById(2L)).thenReturn(Optional.of(anchor));

        baseServiceService.moveService(1L, 2L);

        verify(baseServiceRepo).shiftUp(4, 2);
        verify(baseServiceRepo, never()).shiftDown(anyInt(), anyInt());
        assertEquals(2, moved.getDisplayOrder());
        verify(baseServiceRepo).save(moved);
    }

    @Test
    void moveService_shouldMoveToFirstPosition_whenAfterServiceIdIsNull() {
        BaseService moved = orderedService(1L, "Masaż", 3);

        when(baseServiceRepo.findOneById(1L)).thenReturn(Optional.of(moved));

        baseServiceService.moveService(1L, null);

        verify(baseServiceRepo).shiftUp(3, 0);
        assertEquals(0, moved.getDisplayOrder());
        verify(baseServiceRepo).save(moved);
    }

    @Test
    void moveService_shouldDoNothing_whenTargetPositionEqualsCurrentPosition() {
        BaseService moved = orderedService(1L, "Masaż", 2);
        BaseService anchor = orderedService(2L, "Peeling", 1);

        when(baseServiceRepo.findOneById(1L)).thenReturn(Optional.of(moved));
        when(baseServiceRepo.findOneById(2L)).thenReturn(Optional.of(anchor));

        baseServiceService.moveService(1L, 2L);

        verify(baseServiceRepo, never()).shiftUp(anyInt(), anyInt());
        verify(baseServiceRepo, never()).shiftDown(anyInt(), anyInt());
        verify(baseServiceRepo, never()).save(any());
        assertEquals(2, moved.getDisplayOrder());
    }

    @Test
    void moveService_shouldDoNothing_whenAlreadyFirstAndAfterServiceIdIsNull() {
        BaseService moved = orderedService(1L, "Masaż", 0);

        when(baseServiceRepo.findOneById(1L)).thenReturn(Optional.of(moved));

        baseServiceService.moveService(1L, null);

        verify(baseServiceRepo, never()).shiftUp(anyInt(), anyInt());
        verify(baseServiceRepo, never()).shiftDown(anyInt(), anyInt());
        verify(baseServiceRepo, never()).save(any());
    }

    @Test
    void moveService_shouldThrowResourceNotFoundException_whenMovedServiceNotFound() {
        when(baseServiceRepo.findOneById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> baseServiceService.moveService(99L, 2L));
        verify(baseServiceRepo, never()).save(any());
    }

    @Test
    void moveService_shouldThrowResourceNotFoundException_whenAnchorServiceNotFound() {
        when(baseServiceRepo.findOneById(1L)).thenReturn(Optional.of(orderedService(1L, "Masaż", 1)));
        when(baseServiceRepo.findOneById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> baseServiceService.moveService(1L, 99L));
        verify(baseServiceRepo, never()).shiftUp(anyInt(), anyInt());
        verify(baseServiceRepo, never()).shiftDown(anyInt(), anyInt());
        verify(baseServiceRepo, never()).save(any());
    }

    @Test
    void moveService_shouldThrowUpdateException_whenMovedAfterItself() {
        when(baseServiceRepo.findOneById(1L)).thenReturn(Optional.of(orderedService(1L, "Masaż", 1)));

        assertThrows(UpdateException.class, () -> baseServiceService.moveService(1L, 1L));
        verify(baseServiceRepo, never()).save(any());
    }

    private static final String HIDDEN_CATEGORY_MESSAGE = "Kategoria „Masaże” jest ukryta. Najpierw przywróć ją.";

    private BaseServiceCategory hiddenServiceCategory() {
        return BaseServiceCategory.builder().id(5L).name("Masaże").color("#111").isDeleted(true).build();
    }

    private BaseServiceDTO serviceInput(Long categoryId, Boolean isDeleted) {
        BaseServiceCategoryDTO categoryDTO = new BaseServiceCategoryDTO(categoryId, "Kategoria", "#fff", false);
        return new BaseServiceDTO(null, "Masaż", BigDecimal.valueOf(50.0), 60, categoryDTO, 0, isDeleted, new ArrayList<>());
    }

    private BaseService storedService(boolean archived, BaseServiceCategory category) {
        return BaseService.builder().id(1L).name("Masaż").price(BigDecimal.valueOf(50.0)).duration(60)
                .category(category).isDeleted(archived).variants(new HashSet<>()).build();
    }

    @Test
    void createBaseService_shouldThrowConflict_whenTargetCategoryHidden() {
        when(baseServiceRepo.existsByName("Masaż")).thenReturn(false);
        when(serviceCategoryRepo.findByIdAndIsDeletedTrue(5L)).thenReturn(Optional.of(hiddenServiceCategory()));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> baseServiceService.createBaseService(serviceInput(5L, false)));

        assertEquals(HIDDEN_CATEGORY_MESSAGE, ex.getMessage());
        verify(baseServiceRepo, never()).save(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void createBaseService_shouldSave_whenTargetCategoryActive() {
        when(baseServiceRepo.existsByName("Masaż")).thenReturn(false);
        when(serviceCategoryRepo.findByIdAndIsDeletedTrue(1L)).thenReturn(Optional.empty());
        when(baseServiceRepo.findMaxDisplayOrder()).thenReturn(0);
        when(baseServiceRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BaseServiceDTO result = baseServiceService.createBaseService(serviceInput(1L, false));

        assertEquals(1L, result.getCategory().getId());
        verify(baseServiceRepo).save(any());
    }

    @Test
    void updateBaseService_shouldThrowConflict_whenActiveServiceMovedToHiddenCategory() {
        when(baseServiceRepo.findOneById(1L)).thenReturn(Optional.of(storedService(false, category())));
        when(baseServiceRepo.findByName("Masaż")).thenReturn(Optional.empty());
        when(serviceCategoryRepo.findByIdAndIsDeletedTrue(5L)).thenReturn(Optional.of(hiddenServiceCategory()));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> baseServiceService.updateBaseService(1L, serviceInput(5L, false)));

        assertEquals(HIDDEN_CATEGORY_MESSAGE, ex.getMessage());
        verify(baseServiceRepo, never()).save(any());
    }

    @Test
    void updateBaseService_shouldSave_whenArchivedServiceStaysArchivedInItsHiddenCategory() {
        when(baseServiceRepo.findOneById(1L)).thenReturn(Optional.of(storedService(true, hiddenServiceCategory())));
        when(baseServiceRepo.findByName("Masaż")).thenReturn(Optional.empty());
        when(baseServiceRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BaseServiceDTO result = baseServiceService.updateBaseService(1L, serviceInput(5L, true));

        assertTrue(result.getIsDeleted());
        assertEquals(5L, result.getCategory().getId());
        verify(serviceCategoryRepo, never()).findByIdAndIsDeletedTrue(any());
    }

    @Test
    void updateBaseService_shouldThrowConflict_whenArchivedServiceUnarchivedInHiddenCategory() {
        when(baseServiceRepo.findOneById(1L)).thenReturn(Optional.of(storedService(true, hiddenServiceCategory())));
        when(baseServiceRepo.findByName("Masaż")).thenReturn(Optional.empty());
        when(serviceCategoryRepo.findByIdAndIsDeletedTrue(5L)).thenReturn(Optional.of(hiddenServiceCategory()));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> baseServiceService.updateBaseService(1L, serviceInput(5L, false)));

        assertEquals(HIDDEN_CATEGORY_MESSAGE, ex.getMessage());
        verify(baseServiceRepo, never()).save(any());
    }

    @Test
    void updateBaseService_shouldSave_whenMovedToActiveCategory() {
        when(baseServiceRepo.findOneById(1L)).thenReturn(Optional.of(storedService(false, category())));
        when(baseServiceRepo.findByName("Masaż")).thenReturn(Optional.empty());
        when(serviceCategoryRepo.findByIdAndIsDeletedTrue(2L)).thenReturn(Optional.empty());
        when(baseServiceRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BaseServiceDTO result = baseServiceService.updateBaseService(1L, serviceInput(2L, false));

        assertEquals(2L, result.getCategory().getId());
        verify(baseServiceRepo).save(any());
    }
}
