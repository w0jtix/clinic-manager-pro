package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.BrandDTO;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.model.Brand;
import com.clinic.clinicmanager.repo.BrandRepo;
import com.clinic.clinicmanager.service.impl.BrandServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BrandServiceImplTest {

    @Mock
    BrandRepo brandRepo;

    @Mock
    AuditLogService auditLogService;

    @InjectMocks
    BrandServiceImpl brandService;

    @Test
    void getBrandById_shouldReturnBrandDTO_whenBrandExists() {
        Brand brand = Brand.builder().id(1L).name("Nike").build();
        when(brandRepo.findById(1L)).thenReturn(Optional.of(brand));

        BrandDTO result = brandService.getBrandById(1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("Nike", result.getName());
    }

    @Test
    void getBrandById_shouldThrowResourceNotFoundException_whenBrandNotFound() {
        when(brandRepo.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> brandService.getBrandById(99L));
    }

    @Test
    void createBrand_shouldReturnSavedBrandDTO_whenBrandIsNew() {

        BrandDTO inputDTO = new BrandDTO(null, "Adidas");
        Brand savedEntity = Brand.builder().id(2L).name("Adidas").build();

        when(brandRepo.findByBrandName("Adidas")).thenReturn(Optional.empty());


        when(brandRepo.save(any(Brand.class))).thenReturn(savedEntity);

        BrandDTO result = brandService.createBrand(inputDTO);

        assertNotNull(result);
        assertEquals(2L, result.getId());
        assertEquals("Adidas", result.getName());

        verify(brandRepo, times(1)).save(any(Brand.class));
    }

    @Test
    void createBrand_shouldThrowConflictException_whenBrandAlreadyExists() {

        BrandDTO inputDTO = new BrandDTO(null, "Nike");
        Brand existingBrand = Brand.builder().id(1L).name("Nike").build();

        when(brandRepo.findByBrandName("Nike")).thenReturn(Optional.of(existingBrand));

        assertThrows(ConflictException.class,
                () -> brandService.createBrand(inputDTO));

        verify(brandRepo, never()).save(any());
    }

    @Test
    void updateBrand_shouldReturnUpdatedBrandDTO_whenBrandExistsAndNoConflict() {

        Brand existingBrand = Brand.builder().id(1L).name("Nike").build();
        Brand updatedEntity = Brand.builder().id(1L).name("Nike Pro").build();
        BrandDTO updateDTO = new BrandDTO(null, "Nike Pro");

        when(brandRepo.findById(1L)).thenReturn(Optional.of(existingBrand));
        when(brandRepo.findByBrandName("Nike Pro")).thenReturn(Optional.empty());
        when(brandRepo.save(any(Brand.class))).thenReturn(updatedEntity);

        BrandDTO result = brandService.updateBrand(1L, updateDTO);

        assertEquals(1L, result.getId());
        assertEquals("Nike Pro", result.getName());

        verify(brandRepo, times(1)).save(any(Brand.class));
    }

    @Test
    void updateBrand_shouldThrowResourceNotFoundException_whenBrandNotFound() {

        when(brandRepo.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> brandService.updateBrand(99L, new BrandDTO(null, "Nike")));
    }

    @Test
    void updateBrand_shouldThrowConflictException_whenNameTakenByOtherBrand() {

        Brand currentBrand = Brand.builder().id(1L).name("Nike").build();
        Brand otherBrand = Brand.builder().id(2L).name("Adidas").build();

        when(brandRepo.findById(1L)).thenReturn(Optional.of(currentBrand));
        when(brandRepo.findByBrandName("Adidas")).thenReturn(Optional.of(otherBrand));

        assertThrows(ConflictException.class,
                () -> brandService.updateBrand(1L, new BrandDTO(null, "Adidas")));
    }

    @Test
    void deleteBrandById_shouldDeleteBrand_whenBrandExists() {

        Brand brand = Brand.builder().id(1L).name("Nike").build();
        when(brandRepo.findById(1L)).thenReturn(Optional.of(brand));

        brandService.deleteBrandById(1L);

        verify(brandRepo, times(1)).deleteById(1L);
    }

    @Test
    void deleteBrandById_shouldThrowResourceNotFoundException_whenBrandNotFound() {

        when(brandRepo.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> brandService.deleteBrandById(99L));

        verify(brandRepo, never()).deleteById(any());
    }
}
