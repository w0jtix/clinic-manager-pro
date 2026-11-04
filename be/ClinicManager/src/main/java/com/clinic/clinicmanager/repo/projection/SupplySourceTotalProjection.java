package com.clinic.clinicmanager.repo.projection;

import com.clinic.clinicmanager.model.constants.SupplyChangeSource;

public interface SupplySourceTotalProjection {
    SupplyChangeSource getSource();
    Long getTotal();
}
