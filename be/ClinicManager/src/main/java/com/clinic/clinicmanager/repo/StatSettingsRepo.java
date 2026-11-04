package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.model.StatSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface StatSettingsRepo extends JpaRepository<StatSettings, Long> {

    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END FROM StatSettings s "
            + "WHERE s.id = :id AND s.companyLogo IS NOT NULL")
    boolean hasCompanyLogo(@Param("id") Long id);

    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END FROM StatSettings s "
            + "WHERE s.id = :id AND s.emailFooterLogo IS NOT NULL")
    boolean hasEmailFooterLogo(@Param("id") Long id);

    default StatSettings getSettings() {
        return findById(1L).orElseGet(() -> {
            StatSettings defaults = StatSettings.builder().build();
            defaults.setId(1L);
            return save(defaults);
        });
    }
}
