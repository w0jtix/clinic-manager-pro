package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.model.ClinicWorkingHours;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClinicWorkingHoursRepo extends JpaRepository<ClinicWorkingHours, Long> {

    List<ClinicWorkingHours> findAllByOrderByDayOfWeekAsc();
}
