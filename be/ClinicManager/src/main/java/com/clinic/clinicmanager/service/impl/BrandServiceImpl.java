package com.clinic.clinicmanager.service.impl;

import com.clinic.clinicmanager.DTO.BrandDTO;
import com.clinic.clinicmanager.DTO.ProductDTO;
import com.clinic.clinicmanager.DTO.request.KeywordFilterDTO;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.CreationException;
import com.clinic.clinicmanager.exceptions.DeletionException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.UpdateException;
import com.clinic.clinicmanager.model.Brand;
import com.clinic.clinicmanager.model.Product;
import com.clinic.clinicmanager.repo.BrandRepo;
import com.clinic.clinicmanager.service.AuditLogService;
import com.clinic.clinicmanager.service.BrandService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.Objects.isNull;

@Service
@RequiredArgsConstructor
public class BrandServiceImpl implements BrandService {
    private final BrandRepo brandRepo;
    private final AuditLogService auditLogService;

    @Override
    public BrandDTO getBrandById(Long id){
        return new BrandDTO(brandRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Brand not found with given id: " + id)));
    }

    @Override
    public List<BrandDTO> getBrands(KeywordFilterDTO filter) {
        if(isNull(filter)) {
            filter = new KeywordFilterDTO();
        }
        return brandRepo.findAllWithFilters(
                        filter.getKeyword())
                .stream()
                .map(BrandDTO::new)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public BrandDTO createBrand(BrandDTO brand) {
        try{
            if (brandAlreadyExists(brand)) {
                throw new ConflictException("Brand already exists: " + brand.getName());
            }
            BrandDTO savedBrand = new BrandDTO(brandRepo.save(brand.toEntity()));
            auditLogService.logCreate("Brand", savedBrand.getId(), savedBrand.getName(), savedBrand);
            return savedBrand;
        } catch (ConflictException e) {
            throw e;
        } catch (Exception e) {
            throw new CreationException("Failed to create Brand. Reason: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public List<BrandDTO> createBrands(List<BrandDTO> brands) {
        return brands.stream()
                .map(this::createBrand)
                .toList();
    }

    @Override
    @Transactional
    public BrandDTO updateBrand(Long id, BrandDTO brand) {
        try{
            BrandDTO oldBrandSnapshot = getBrandById(id);

            checkForDuplicatesExcludingCurrent(brand, id);
            brand.setId(id);
            BrandDTO savedBrand = new BrandDTO(brandRepo.save(brand.toEntity()));

            auditLogService.logUpdate("Brand", id, oldBrandSnapshot.getName(), oldBrandSnapshot, savedBrand);
            return savedBrand;
        } catch (ResourceNotFoundException | ConflictException e) {
            throw e;
        } catch (Exception e) {
            throw new UpdateException("Failed to update Brand, Reason: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public void deleteBrandById(Long id) {
        try{
            BrandDTO brandSnapshot = getBrandById(id);
            brandRepo.deleteById(id);
            auditLogService.logDelete("Brand", id, brandSnapshot.getName(), brandSnapshot);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new DeletionException("Failed to delete Brand, Reason: " + e.getMessage(), e);
        }
    }

    private boolean brandAlreadyExists(BrandDTO brandDTO) {
        return brandRepo.findByBrandName(brandDTO.getName()).isPresent();
    }

    private void checkForDuplicatesExcludingCurrent(BrandDTO brandDTO, Long currentId) {
        Optional<Brand> duplicate = brandRepo.findByBrandName(
                brandDTO.getName()
        );

        if (duplicate.isPresent() && !duplicate.get().getId().equals(currentId)) {
            throw new ConflictException("Brand with provided details already exists.");
        }
    }
}
