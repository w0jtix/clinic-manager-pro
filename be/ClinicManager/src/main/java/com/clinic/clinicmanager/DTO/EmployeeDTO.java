package com.clinic.clinicmanager.DTO;

import com.clinic.clinicmanager.model.Employee;
import com.clinic.clinicmanager.model.constants.EmploymentType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class EmployeeDTO {

    private Long id;
    private String name;
    private String lastName;
    private Boolean isDeleted;
    private EmploymentType employmentType;
    @Min(0)
    @Max(100)
    private Double bonusPercent;

    @Min(0)
    @Max(100)
    private Double saleBonusPercent;

    public EmployeeDTO(Employee employee) {
        this.id = employee.getId();
        this.name = employee.getName();
        this.lastName = employee.getLastName();
        this.isDeleted = employee.getIsDeleted();
        this.employmentType = employee.getEmploymentType();
        this.bonusPercent = employee.getBonusPercent();
        this.saleBonusPercent = employee.getSaleBonusPercent();
    }


    public Employee toEntity() {
        return Employee.builder()
                .id(this.id)
                .name(this.name)
                .lastName(this.lastName)
                .isDeleted(this.isDeleted != null ? this.isDeleted : false)
                .employmentType(this.employmentType != null ? this.employmentType : EmploymentType.HALF)
                .bonusPercent(this.bonusPercent)
                .saleBonusPercent(this.saleBonusPercent)
                .build();
    }
}
