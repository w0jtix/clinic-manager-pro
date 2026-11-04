package com.clinic.clinicmanager.DTO;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BonusChangePointDTO {
    private Integer year;
    private Integer month;
    private Double bonusPercent;
    private Double bonusPercentChange;
    private Double saleBonusPercent;
    private Double saleBonusPercentChange;
}
