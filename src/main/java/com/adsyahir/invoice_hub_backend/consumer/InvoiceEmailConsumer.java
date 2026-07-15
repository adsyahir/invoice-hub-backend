package com.adsyahir.invoice_hub_backend.consumer;

import com.adsyahir.invoice_hub_backend.config.KafkaTopicConfig;
import com.adsyahir.invoice_hub_backend.event.InvoiceSentEvent;
import com.adsyahir.invoice_hub_backend.service.InvoiceEmailService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.stereotype.Component;

/**
 * Emails the client their invoice (PDF + pay link), off the request thread.
 *
 * <p>This is where the Kafka rewrite pays for itself. The email used to run inline in
 * {@code InvoiceService.send()} wrapped in a catch-all that logged a warning — so a dead SMTP
 * server meant the client silently never received their invoice and nobody found out. Here a
 * failure simply throws: Kafka redelivers with backoff, and if every attempt fails the record
 * lands on a dead-letter topic where it can be inspected and replayed. The failure became
 * recoverable instead of ignored.
 *
 * <p>Kafka delivers AT-LEAST-ONCE, so this listener can see the same event twice (a consumer
 * rebalance, or a crash between doing the work and committing the offset). A duplicate email is
 * annoying but not corrupting, so we accept it. If it ever has to be exact, add a
 * processed_events table keyed on {@code eventId} with INSERT ... ON CONFLICT DO NOTHING.
 *
 * <p>NOTE: one {@code @DltHandler} is allowed per class, which is why the payment receipt lives
 * in its own consumer rather than as a second method here.
 */
@Component
@RequiredArgsConstructor
public class InvoiceEmailConsumer {

    private static final Logger log = LoggerFactory.getLogger(InvoiceEmailConsumer.class);

    private final InvoiceEmailService emailService;

    /**
     * 4 attempts: 2s, 4s, 8s. Transient SMTP failures usually clear within seconds; anything
     * that survives this is a real problem and belongs in the DLT, not in a retry loop that
     * hammers the mail server forever.
     */
    @RetryableTopic(
            attempts = "4",
            backOff = @BackOff(delay = 2000, multiplier = 2.0),
            dltTopicSuffix = "-dlt")
    @KafkaListener(topics = KafkaTopicConfig.INVOICE_EVENTS, groupId = "invoice-email")
    public void onInvoiceSent(InvoiceSentEvent event) {
        log.debug("Emailing invoice {} (eventId={})", event.invoiceNumber(), event.eventId());

        // Pass the id, not an entity: the email service loads it inside its own transaction so
        // the lazy client/tenant/lineItems associations resolve against a live session. It also
        // means we act on current truth — by now the invoice may have been voided or paid.
        emailService.sendInvoice(event.invoiceId());
    }

    @DltHandler
    public void dlt(InvoiceSentEvent event) {
        log.error("Invoice email PERMANENTLY failed after retries — invoice={} eventId={}. "
                        + "Fix the cause, then replay this record from the DLT.",
                event.invoiceNumber(), event.eventId());
    }
}
