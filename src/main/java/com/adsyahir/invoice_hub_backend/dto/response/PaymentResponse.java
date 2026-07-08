package com.adsyahir.invoice_hub_backend.dto.response;

import com.adsyahir.invoice_hub_backend.enums.PaymentMethod;
import com.adsyahir.invoice_hub_backend.enums.PaymentStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * API view of a Payment. Exposes the public uuids (never the bigint ids) and
 * flattens the invoice to its uuid + number so the frontend can link back.
 */
@Getter
@Builder
public class PaymentResponse {
    private UUID id;                 // payment public uuid
    private UUID invoiceId;          // invoice public uuid
    private String invoiceNumber;
    private BigDecimal amount;
    private String currency;
    private PaymentMethod method;
    private PaymentStatus status;
    private String gateway;
    private String reference;
    private Long recordedById;
    private String recordedByName;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
}
