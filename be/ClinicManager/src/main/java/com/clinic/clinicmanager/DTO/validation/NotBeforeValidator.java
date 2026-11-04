package com.clinic.clinicmanager.DTO.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.LocalDate;

public class NotBeforeValidator implements ConstraintValidator<NotBefore, LocalDate> {

    private LocalDate min;

    @Override
    public void initialize(NotBefore annotation) {
        this.min = LocalDate.parse(annotation.value());
    }

    @Override
    public boolean isValid(LocalDate value, ConstraintValidatorContext context) {
        return value == null || !value.isBefore(min);
    }
}
