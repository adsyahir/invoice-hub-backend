package com.adsyahir.invoice_hub_backend.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordMatchesValidator.class)
public @interface PasswordMatches {

    String message() default "Password and confirm password must match";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}