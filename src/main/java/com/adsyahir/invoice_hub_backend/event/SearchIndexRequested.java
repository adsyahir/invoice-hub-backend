package com.adsyahir.invoice_hub_backend.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * "This entity changed — refresh its search document." Published wherever an invoice or
 * client is written; relayed to Kafka after commit; SearchIndexConsumer does the ES write.
 *
 * <p>Why through Kafka instead of writing to Elasticsearch inline: the ES write becomes
 * asynchronous (an ES outage or slow node never blocks or fails an invoice save), retried
 * with backoff, and dead-lettered if it keeps failing — the same guarantees the email
 * flow gets. The index may lag the database by milliseconds; for a search box that is
 * exactly the right trade ("eventual consistency").
 *
 * <p>Carries only ids, never the entity or its fields. The consumer reloads current state
 * from the database, which also makes redelivery naturally idempotent: indexing the same
 * id twice writes the same document.
 */
public record SearchIndexRequested(
        String eventId,
        Long tenantId,
        EntityType entityType,
        Long entityId,
        boolean deleted,
        LocalDateTime occurredAt
) {
    public enum EntityType { INVOICE, CLIENT }

    public static SearchIndexRequested invoice(Long tenantId, Long invoiceId) {
        return new SearchIndexRequested(UUID.randomUUID().toString(), tenantId,
                EntityType.INVOICE, invoiceId, false, LocalDateTime.now());
    }

    public static SearchIndexRequested client(Long tenantId, Long clientId) {
        return new SearchIndexRequested(UUID.randomUUID().toString(), tenantId,
                EntityType.CLIENT, clientId, false, LocalDateTime.now());
    }

    /** A client was (soft-)deleted: drop their document and all of their invoice documents. */
    public static SearchIndexRequested clientDeleted(Long tenantId, Long clientId) {
        return new SearchIndexRequested(UUID.randomUUID().toString(), tenantId,
                EntityType.CLIENT, clientId, true, LocalDateTime.now());
    }
}
