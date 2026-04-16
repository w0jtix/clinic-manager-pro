package com.clinic.clinicmanager.DTO.request;

import com.clinic.clinicmanager.model.constants.ReviewSource;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReviewFilterDTO {
    private String keyword;
    private ReviewSource source;
    private Boolean isUsed;
}
