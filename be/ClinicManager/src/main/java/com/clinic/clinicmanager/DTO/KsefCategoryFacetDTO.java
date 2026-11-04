package com.clinic.clinicmanager.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class KsefCategoryFacetDTO {

    private String category;
    private Long count;
}
