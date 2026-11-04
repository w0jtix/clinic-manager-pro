package com.clinic.clinicmanager.DTO.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class KsefBookCorrectionRequestDTO {

    private List<KsefBookCorrectionItemDTO> items;
}
