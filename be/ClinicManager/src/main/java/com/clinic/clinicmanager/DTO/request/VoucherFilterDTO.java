package com.clinic.clinicmanager.DTO.request;

import com.clinic.clinicmanager.model.constants.VoucherStatus;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VoucherFilterDTO {
    private VoucherStatus status;
    private String keyword;
}
