package com.clinic.clinicmanager.DTO.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CodedErrorResponse {
    private String code;
    private String message;
}
