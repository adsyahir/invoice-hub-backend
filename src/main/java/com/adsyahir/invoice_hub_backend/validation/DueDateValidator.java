
package com.adsyahir.invoice_hub_backend.validation;
import com.adsyahir.invoice_hub_backend.dto.request.CreateInvoiceRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class DueDateValidator implements ConstraintValidator<ValidDueDate, CreateInvoiceRequest> {

    @Override
    public boolean isValid(CreateInvoiceRequest request, ConstraintValidatorContext context) {
        if (request.getIssueDate() == null || request.getDueDate() == null) {
            return true; // let @NotNull handle null checks
        }
        if (request.getDueDate().isBefore(request.getIssueDate())) {
            // Attach the violation to the dueDate node so it surfaces as a
            // FieldError ({ errors: { dueDate: ... } }) — a class-level default
            // would be a global error, which GlobalExceptionHandler drops.
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode("dueDate")
                    .addConstraintViolation();

            return false;
        }
        return true;
    }
}