package com.clinic.clinicmanager.service.impl;


import com.clinic.clinicmanager.DTO.BaseServiceDTO;
import com.clinic.clinicmanager.DTO.BaseServiceVariantDTO;
import com.clinic.clinicmanager.DTO.request.KeywordFilterDTO;
import com.clinic.clinicmanager.DTO.request.ServiceFilterDTO;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.CreationException;
import com.clinic.clinicmanager.exceptions.DeletionException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.UpdateException;
import com.clinic.clinicmanager.exceptions.ValidationException;
import com.clinic.clinicmanager.model.BaseService;
import com.clinic.clinicmanager.model.BaseServiceCategory;
import com.clinic.clinicmanager.model.BaseServiceVariant;
import com.clinic.clinicmanager.repo.BaseServiceCategoryRepo;
import com.clinic.clinicmanager.repo.BaseServiceRepo;
import com.clinic.clinicmanager.repo.VisitItemRepo;
import com.clinic.clinicmanager.service.AuditLogService;
import com.clinic.clinicmanager.service.BaseServiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.Objects.isNull;

@Service
@RequiredArgsConstructor
public class BaseServiceServiceImpl implements BaseServiceService {

    private final BaseServiceRepo baseServiceRepo;
    private final BaseServiceCategoryRepo serviceCategoryRepo;
    private final VisitItemRepo visitItemRepo;
    private final AuditLogService auditLogService;

    @Override
    public BaseServiceDTO getBaseServiceById(Long id) {
        return new BaseServiceDTO(baseServiceRepo.findOneById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found with given id: " + id)));
    }

    @Override
    public List<BaseServiceDTO> getBaseServices(ServiceFilterDTO filter) {
        if(isNull(filter)) {
            filter = new ServiceFilterDTO();
        }
        return baseServiceRepo.findAllWithFilters(filter.getKeyword(), filter.getCategoryIds()).stream()
                .map(BaseServiceDTO::new)
                .collect(Collectors.toList());
    }

    @Override
    public List<BaseServiceDTO> getBaseServicesByCategoryId(Long categoryId) {
        return baseServiceRepo.findAllByCategoryId(categoryId).stream()
                .map(BaseServiceDTO::new)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public BaseServiceDTO createBaseService(BaseServiceDTO service) {
        try{
            if(baseServiceRepo.existsByName(service.getName())) {
                throw new ConflictException("Service already exists: " + service.getName());
            }
            ensureCategoryAssignable(service, null, false);
            BaseService newService = service.toEntity();
            newService.setDisplayOrder(baseServiceRepo.findMaxDisplayOrder() + 1);

            BaseServiceDTO savedService = new BaseServiceDTO(baseServiceRepo.save(newService));
            auditLogService.logCreate("BaseService", savedService.getId(), savedService.getName(), savedService);
            return savedService;
        } catch (ConflictException | ResourceNotFoundException | ValidationException | AccessDeniedException
                 | CreationException | UpdateException | DeletionException e) {
            throw e;
        } catch (Exception e) {
            throw new CreationException("Failed to create Service.", e);
        }
    }

    @Override
    @Transactional
    public BaseServiceDTO updateBaseService(Long id, BaseServiceDTO serviceDTO) {
        try {
            BaseService existingService = baseServiceRepo.findOneById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Service not found with id: " + id));

            BaseServiceDTO oldServiceSnapshot = new BaseServiceDTO(existingService);

            checkForDuplicatesExcludingCurrent(serviceDTO, id);
            ensureCategoryAssignable(serviceDTO, existingService.getCategory().getId(),
                    Boolean.TRUE.equals(existingService.getIsDeleted()) && Boolean.TRUE.equals(serviceDTO.getIsDeleted()));

            Set<Long> newVariantIds = serviceDTO.getVariants().stream()
                    .map(BaseServiceVariantDTO::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            Set<BaseServiceVariantDTO> variantsToKeep = new HashSet<>();

            for (BaseServiceVariant existingVariant : existingService.getVariants()) {
                if (!newVariantIds.contains(existingVariant.getId())) {

                    if (visitItemRepo.existsByServiceVariantId(existingVariant.getId())) {
                        existingVariant.softDelete();
                        variantsToKeep.add(new BaseServiceVariantDTO(existingVariant));
                    }
                }
            }

            serviceDTO.getVariants().addAll(variantsToKeep);

            serviceDTO.setId(id);
            BaseService serviceToSave = serviceDTO.toEntity();
            serviceToSave.setDisplayOrder(existingService.getDisplayOrder());

            BaseServiceDTO savedService = new BaseServiceDTO(baseServiceRepo.save(serviceToSave));

            auditLogService.logUpdate("BaseService", id, oldServiceSnapshot.getName(), oldServiceSnapshot, savedService);
            return savedService;

        } catch (ResourceNotFoundException | ConflictException | ValidationException | AccessDeniedException
                 | CreationException | UpdateException | DeletionException e) {
            throw e;
        } catch (Exception e) {
            throw new UpdateException("Failed to update Service.", e);
        }
    }

    @Override
    @Transactional
    public void deleteBaseServiceById(Long id) {
        try {
            BaseService service = baseServiceRepo.findOneById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Service not found with id: " + id));

            if (service.getIsDeleted()) {
                throw new DeletionException("Service is already soft-deleted.");
            }

            BaseServiceDTO serviceSnapshot = new BaseServiceDTO(service);

            if (hasVisitItemReferences(id)) {
                service.softDelete();
                baseServiceRepo.save(service);
            } else {
                baseServiceRepo.deleteById(id);
            }

            auditLogService.logDelete("BaseService", id, serviceSnapshot.getName(), serviceSnapshot);
        } catch (ResourceNotFoundException | DeletionException | ConflictException | ValidationException
                 | AccessDeniedException | CreationException | UpdateException e) {
            throw e;
        } catch (Exception e) {
            throw new DeletionException("Failed to delete Service.", e);
        }
    }

    @Override
    @Transactional
    public void moveService(Long serviceId, Long afterServiceId) {
        BaseService moved = baseServiceRepo.findOneById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found with id: " + serviceId));

        if (serviceId.equals(afterServiceId)) {
            throw new UpdateException("Service cannot be moved after itself.");
        }

        int from = moved.getDisplayOrder();
        int to;

        if (isNull(afterServiceId)) {
            to = 0;
        } else {
            BaseService anchor = baseServiceRepo.findOneById(afterServiceId)
                    .orElseThrow(() -> new ResourceNotFoundException("Service not found with id: " + afterServiceId));
            int anchorOrder = anchor.getDisplayOrder();
            to = anchorOrder + (from > anchorOrder ? 1 : 0);
        }

        if (to == from) {
            return;
        }

        if (to < from) {
            baseServiceRepo.shiftUp(from, to);
        } else {
            baseServiceRepo.shiftDown(from, to);
        }

        moved.setDisplayOrder(to);
        baseServiceRepo.save(moved);
    }

    private boolean hasVisitItemReferences(Long serviceId) {
        return visitItemRepo.existsByServiceId(serviceId);
    }

    private void ensureCategoryAssignable(BaseServiceDTO service, Long currentCategoryId, boolean archived) {
        if (service.getCategory() == null || service.getCategory().getId() == null) {
            return;
        }
        Long targetCategoryId = service.getCategory().getId();
        if (archived && targetCategoryId.equals(currentCategoryId)) {
            return;
        }
        Optional<BaseServiceCategory> hidden = serviceCategoryRepo.findByIdAndIsDeletedTrue(targetCategoryId);
        if (hidden.isPresent()) {
            throw new ConflictException("Kategoria „" + hidden.get().getName()
                    + "” jest ukryta. Najpierw przywróć ją.");
        }
    }

    private void checkForDuplicatesExcludingCurrent(BaseServiceDTO serviceDTO, Long currentId) {
        Optional<BaseService> duplicate = baseServiceRepo.findByName(serviceDTO.getName());

        if(duplicate.isPresent() && !duplicate.get().getId().equals(currentId)) {
            throw new ConflictException("Service with provided details already exists.");
        }
    }
}
