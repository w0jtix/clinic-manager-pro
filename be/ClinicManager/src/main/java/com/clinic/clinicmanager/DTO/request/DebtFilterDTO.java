package com.clinic.clinicmanager.DTO.request;

import com.clinic.clinicmanager.model.constants.PaymentStatus;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DebtFilterDTO {
    private PaymentStatus paymentStatus;
    private String keyword;
}
