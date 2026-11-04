package com.clinic.clinicmanager.DTO.request;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ServiceFilterDTO {
    private List<Long> categoryIds;
    private String keyword;
}