package com.adsyahir.invoice_hub_backend.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * An invoice was sent to its client. Published after the transaction commits and
 * relayed to Kafka by {@link DomainEventRelay}; a consumer emails the PDF + pay link.
 *
 * <p>Carries scalars only — never the Invoice entity. The entity has lazy associations
 * (tenant, client, lineItems) that fail to serialize once the session closes, and it
 * holds fields that must never reach a topic: internalNotes, and paymentLinkToken —
 * a bearer token that lets anyone holding it view and pay the invoice.
 *
 * <p>The consumer reloads the invoice by id, so it acts on current state rather than a
 * snapshot that may already be stale (voided, paid) by the time it is processed.
 *
 * @param eventId unique per publish. Kafka delivers at-least-once, so the same record
 *                can arrive twice; consumers use this to skip a redelivery.
 */
public record InvoiceSentEvent(
        String eventId,
        Long tenantId,
        Long invoiceId,
        UUID invoiceUuid,
        String invoiceNumber,
        String clientEmail,
        LocalDateTime occurredAt
) {
    /** Stamps the eventId and timestamp so callers don't repeat that at every publish site. */
    public static InvoiceSentEvent of(Long tenantId, Long invoiceId, UUID invoiceUuid,
                                      String invoiceNumber, String clientEmail) {
        return new InvoiceSentEvent(UUID.randomUUID().toString(), tenantId, invoiceId,
                invoiceUuid, invoiceNumber, clientEmail, LocalDateTime.now());
    }
}
