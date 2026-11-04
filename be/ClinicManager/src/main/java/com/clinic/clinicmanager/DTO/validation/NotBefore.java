package com.clinic.clinicmanager.DTO.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = NotBeforeValidator.class)
public @interface NotBefore {

    String value();

    String message() default "must not be before {value}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
