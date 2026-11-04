package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.model.SoftDeletableCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

@NoRepositoryBean
public interface SoftDeletableCategoryRepo<T extends SoftDeletableCategory> extends JpaRepository<T, Long> {

    @Query("SELECT c FROM #{#entityName} c WHERE LOWER(TRIM(c.name)) = LOWER(TRIM(:name)) ORDER BY c.id")
    List<T> findAllByCategoryName(@Param("name") String name);

    Optional<T> findByIdAndIsDeletedTrue(Long id);

    boolean existsByColorAndIsDeletedFalse(String color);

    boolean existsByColorAndIsDeletedFalseAndIdNot(String color, Long id);
}
