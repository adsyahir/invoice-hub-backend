package com.adsyahir.invoice_hub_backend.event;

import com.adsyahir.invoice_hub_backend.config.KafkaTopicConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * The single bridge between the domain and Kafka. Services publish plain Spring
 * application events; this class forwards them to a topic — and only AFTER the
 * database transaction has committed.
 *
 * <p>That ordering is the point. If a service called {@code kafkaTemplate.send()}
 * directly, the record would leave while the transaction was still open: a consumer
 * could pick it up, query the invoice, and not find the (uncommitted) row. Worse, if the
 * transaction then rolled back, we would have emailed a client about an invoice that
 * never existed. {@code AFTER_COMMIT} makes both impossible — Spring holds the event
 * until the commit succeeds and silently discards it if it does not.
 *
 * <p>Keeping Kafka here also keeps it out of InvoiceService/PaymentService: those know
 * only that "something happened", not how it is transported.
 *
 * <p>KNOWN GAP: there is still a window where the DB commits and the process dies before
 * the send lands, losing the event. Closing it properly needs a transactional outbox —
 * write the event to an outbox table in the same transaction and have a poller (or
 * Debezium) ship it. This is the pragmatic 95% version, chosen deliberately.
 *
 * <p>The partition key is the tenant id: one organization's events stay ordered relative
 * to each other, while different organizations spread across partitions.
 */
@Component
public class DomainEventRelay {

    private static final Logger log = LoggerFactory.getLogger(DomainEventRelay.class);

    private final KafkaTemplate<String, Object> kafka;

    public DomainEventRelay(KafkaTemplate<String, Object> kafka) {
        this.kafka = kafka;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInvoiceSent(InvoiceSentEvent event) {
        publish(KafkaTopicConfig.INVOICE_EVENTS, event.tenantId(), event.eventId(), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentRecorded(PaymentRecordedEvent event) {
        publish(KafkaTopicConfig.PAYMENT_EVENTS, event.tenantId(), event.eventId(), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEInvoiceSubmissionRequested(EInvoiceSubmissionRequested event) {
        publish(KafkaTopicConfig.EINVOICE_COMMANDS, event.tenantId(), event.eventId(), event);
    }

    private void publish(String topic, Long tenantId, String eventId, Object payload) {
        kafka.send(topic, String.valueOf(tenantId), payload)
                .whenComplete((result, error) -> {
                    if (error != null) {
                        // The business action is already committed — we cannot undo it, so
                        // log loudly. This is exactly the window an outbox would close.
                        log.error("Failed to publish {} (eventId={}) to {}: {}",
                                payload.getClass().getSimpleName(), eventId, topic,
                                error.getMessage(), error);
                    } else if (log.isDebugEnabled()) {
                        log.debug("Published {} (eventId={}) to {}-{}@{}",
                                payload.getClass().getSimpleName(), eventId, topic,
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
