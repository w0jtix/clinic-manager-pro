package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.DiscountDTO;

import java.util.List;

public interface DiscountService {

    DiscountDTO getDiscountById(Long id);

    List<DiscountDTO> getDiscounts();

    DiscountDTO createDiscount(DiscountDTO discount);

    DiscountDTO updateDiscount(Long id, DiscountDTO discount);

    void deleteDiscountById(Long id);

}
