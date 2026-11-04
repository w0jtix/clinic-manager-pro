package com.clinic.clinicmanager.DTO;

import com.clinic.clinicmanager.model.VisitItem;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class VisitSummaryServiceDTO {
    private String name;
    private String color;

    public VisitSummaryServiceDTO(VisitItem item) {
        this.name = item.getName();
        this.color = item.getService() != null && item.getService().getCategory() != null
                ? item.getService().getCategory().getColor()
                : null;
    }
}
