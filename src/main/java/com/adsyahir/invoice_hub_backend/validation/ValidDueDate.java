package com.adsyahir.invoice_hub_backend.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DueDateValidator.class)
public @interface ValidDueDate {
    String message() default "Due date cannot be before issue date";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}