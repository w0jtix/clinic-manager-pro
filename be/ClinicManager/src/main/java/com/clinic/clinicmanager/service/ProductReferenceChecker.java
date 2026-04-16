package com.clinic.clinicmanager.service;

public interface ProductReferenceChecker {

    boolean existsByProductId(Long productId);

    default boolean existsByProductIdExcluding(Long productId, Long excludeParentId) {
        return existsByProductId(productId);
    }
}
