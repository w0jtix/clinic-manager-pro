package com.clinic.clinicmanager.DTO;

import com.clinic.clinicmanager.model.User;
import com.clinic.clinicmanager.model.constants.CardTheme;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UserPreferencesDTO {
    private CardTheme cardTheme;

    public UserPreferencesDTO(User user) {
        this.cardTheme = user.getCardTheme();
    }
}
