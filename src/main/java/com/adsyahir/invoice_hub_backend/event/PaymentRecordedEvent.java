package com.adsyahir.invoice_hub_backend.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A payment was recorded against an invoice. Published after the transaction commits;
 * a consumer emails the client a receipt.
 *
 * @param settlesInvoice true when this payment cleared the balance. Read the invoice's
 *                       status AFTER {@code recomputeAndSaveInvoice} runs — that call is
 *                       what flips it to PAID, so reading it earlier always yields false.
 * @param eventId        unique per publish; consumers use it to skip an at-least-once redelivery.
 */
public record PaymentRecordedEvent(
        String eventId,
        Long tenantId,
        Long invoiceId,
        UUID paymentUuid,
        BigDecimal amount,
        String currency,
        boolean settlesInvoice,
        LocalDateTime occurredAt
) {
    public static PaymentRecordedEvent of(Long tenantId, Long invoiceId, UUID paymentUuid,
                                          BigDecimal amount, String currency, boolean settlesInvoice) {
        return new PaymentRecordedEvent(UUID.randomUUID().toString(), tenantId, invoiceId,
                paymentUuid, amount, currency, settlesInvoice, LocalDateTime.now());
    }
}
