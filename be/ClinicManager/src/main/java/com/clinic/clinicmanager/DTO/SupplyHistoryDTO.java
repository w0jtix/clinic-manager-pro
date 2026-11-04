package com.clinic.clinicmanager.DTO;

import com.clinic.clinicmanager.model.ProductSupplyHistory;
import com.clinic.clinicmanager.model.constants.SupplyChangeSource;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

import static java.util.Objects.isNull;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SupplyHistoryDTO {

    private Long id;
    private Integer delta;
    private Integer supplyAfter;
    private SupplyChangeSource source;
    private Long sourceId;
    private String sourceLabel;
    private LocalDateTime occurredAt;
    private String performedBy;
    private boolean sourceExists;

    public SupplyHistoryDTO(ProductSupplyHistory history) {
        if (isNull(history))
            return;
        this.id = history.getId();
        this.delta = history.getDelta();
        this.supplyAfter = history.getSupplyAfter();
        this.source = history.getSource();
        this.sourceId = history.getSourceId();
        this.sourceLabel = history.getSourceLabel();
        this.occurredAt = history.getOccurredAt();
        this.performedBy = history.getPerformedBy();
        this.sourceExists = true;
    }
}
