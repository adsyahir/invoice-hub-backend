package com.adsyahir.invoice_hub_backend.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

/** One line item on a CreateInvoiceRequest. Validated per-element via @Valid. */
@Data
public class LineItemRequest {

    @NotBlank(message = "Description is required")
    @Size(max = 500, message = "Description is too long")
    private String description;

    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be greater than 0")
    private Long quantity;                 // BIGINT, whole units

    @NotNull(message = "Unit price is required")
    @PositiveOrZero(message = "Unit price cannot be negative")
    private BigDecimal unitPrice;          // NUMERIC(15,2)

    @NotNull(message = "Tax rate is required")
    @DecimalMin(value = "0.0", message = "Tax rate cannot be negative")
    @DecimalMax(value = "100.0", message = "Tax rate cannot exceed 100")
    private BigDecimal taxRate;            // NUMERIC(5,2)
}
