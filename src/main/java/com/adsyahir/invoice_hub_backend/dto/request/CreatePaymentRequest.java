package com.adsyahir.invoice_hub_backend.dto.request;

import com.adsyahir.invoice_hub_backend.enums.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

/** Payload for recording a payment against an invoice (POST /payments). */
@Data
public class CreatePaymentRequest {

    @NotNull(message = "Invoice is required")
    private UUID invoiceId;          // the invoice's public uuid

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be greater than 0")
    private BigDecimal amount;

    @NotNull(message = "Method is required")
    private PaymentMethod method;    // CARD, BANK_TRANSFER, CASH, FPX, EWALLET

    private String reference;        // optional manual ref / receipt no.
}
