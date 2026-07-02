package com.adsyahir.invoice_hub_backend.dto.request;

import com.adsyahir.invoice_hub_backend.dto.LineItemRequest;
import com.adsyahir.invoice_hub_backend.validation.ValidDueDate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@ValidDueDate  // class level
public class CreateInvoiceRequest {
    @NotBlank(message = "Invoice number is required")
    private String invoiceNumber;

    @NotBlank(message = "Client is required")
    private String clientId;

    @NotNull(message = "Issue date is required")
    private LocalDate issueDate;

    @NotNull(message = "Due date is required")
    private LocalDate dueDate;

    @NotBlank(message = "Currency is required")
    private String currency;

    @NotEmpty(message = "Add at least one line item")
    @Valid // cascades validation into each LineItemRequest
    private List<LineItemRequest> lineItems;

    // Optional free-text fields (no validation — both nullable on the invoice).
    private String notes;          // shown to client on the PDF
    private String internalNotes;  // internal only
}
