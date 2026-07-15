package com.adsyahir.invoice_hub_backend.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A request to submit an invoice to LHDN MyInvois. A command, not a fact — it asks for
 * work to be done, where {@link InvoiceSentEvent} reports something that already
 * happened. Hence the topic split (einvoice-commands vs invoice-events).
 *
 * <p>The invoice is saved as PENDING before this is published; the consumer performs the
 * submission and flips it to VALIDATED or REJECTED. That mirrors how MyInvois actually
 * behaves — you submit, and LHDN validates asynchronously.
 *
 * @param eventId unique per publish; consumers use it to skip an at-least-once redelivery.
 */
public record EInvoiceSubmissionRequested(
        String eventId,
        Long tenantId,
        Long invoiceId,
        UUID invoiceUuid,
        LocalDateTime occurredAt
) {
    public static EInvoiceSubmissionRequested of(Long tenantId, Long invoiceId, UUID invoiceUuid) {
        return new EInvoiceSubmissionRequested(UUID.randomUUID().toString(), tenantId,
                invoiceId, invoiceUuid, LocalDateTime.now());
    }
}
