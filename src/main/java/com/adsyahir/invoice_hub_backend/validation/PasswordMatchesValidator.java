package com.adsyahir.invoice_hub_backend.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordMatchesValidator implements ConstraintValidator<PasswordMatches, PasswordMatching> {

    @Override
    public boolean isValid(PasswordMatching request, ConstraintValidatorContext context) {
        if (request.getPassword() == null || request.getConfirmPassword() == null) {
            return true; // let @NotNull handle the null case separately
        }

        boolean isValid = request.getPassword().equals(request.getConfirmPassword());

        if (!isValid) {
            // attach error to the confirmPassword field specifically, not the whole class
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Password and confirm password must match")
                    .addPropertyNode("confirmPassword")
                    .addConstraintViolation();
        }

        return isValid;
    }
}