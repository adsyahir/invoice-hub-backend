package com.adsyahir.invoice_hub_backend.dto.request;

import com.adsyahir.invoice_hub_backend.enums.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Payload for paying an invoice via its public link (POST /public/invoices/{token}/pay). */
@Data
public class PublicPaymentRequest {

    @NotNull(message = "Payment method is required")
    private PaymentMethod method;   // CARD, FPX, EWALLET
}
