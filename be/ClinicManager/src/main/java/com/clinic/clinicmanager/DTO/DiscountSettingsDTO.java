package com.clinic.clinicmanager.DTO;

import com.clinic.clinicmanager.model.AppSettings;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class DiscountSettingsDTO {
    private Integer googleReviewDiscount;
    private Integer booksyHappyHours;

    public DiscountSettingsDTO(AppSettings settings) {
        this.googleReviewDiscount = settings.getGoogleReviewDiscount();
        this.booksyHappyHours = settings.getBooksyHappyHours();
    }
}
