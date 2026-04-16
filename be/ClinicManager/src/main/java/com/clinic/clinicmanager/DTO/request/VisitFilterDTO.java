package com.clinic.clinicmanager.DTO.request;

import com.clinic.clinicmanager.model.constants.PaymentMethod;
import com.clinic.clinicmanager.model.constants.PaymentStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class VisitFilterDTO {
    private List<Long> clientIds;
    private List<Long> serviceIds;
    private List<Long> employeeIds;
    private Boolean isBoost;
    private Boolean isVip;
    private Boolean delayed;
    private Boolean absence;
    private Boolean hasDiscount;
    private Boolean hasSale;
    private Integer year;
    private Integer month;
    private PaymentStatus paymentStatus;
    private Double totalValueFrom;
    private Double totalValueTo;
}
