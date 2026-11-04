package com.clinic.clinicmanager.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ClientReviewInfoDTO {

    private boolean present;
    private boolean rewardUsed;

    public static ClientReviewInfoDTO of(boolean present, boolean hasUnusedReward) {
        return new ClientReviewInfoDTO(present, present && !hasUnusedReward);
    }
}
