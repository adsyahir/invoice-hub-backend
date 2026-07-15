package com.adsyahir.invoice_hub_backend.consumer;

import com.adsyahir.invoice_hub_backend.config.KafkaTopicConfig;
import com.adsyahir.invoice_hub_backend.dao.InvoiceRepo;
import com.adsyahir.invoice_hub_backend.enums.EInvoiceStatus;
import com.adsyahir.invoice_hub_backend.event.EInvoiceSubmissionRequested;
import com.adsyahir.invoice_hub_backend.model.Invoice;
import com.adsyahir.invoice_hub_backend.service.AuditService;
import com.adsyahir.invoice_hub_backend.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Submits an invoice to LHDN MyInvois and records the outcome.
 *
 * <p>This mirrors how MyInvois actually behaves: the API call is a submission, and LHDN
 * validates afterwards. {@code InvoiceService.submitEInvoice()} marks the invoice PENDING and
 * publishes a command; this consumer does the work and flips it to VALIDATED (or REJECTED).
 *
 * <p>TODO(integration): the LHDN round-trip below is SIMULATED. The real version:
 * <ol>
 *   <li>load the tenant's TIN from tenant_einvoice_settings</li>
 *   <li>fetch an OAuth token with InvoiceHub's intermediary client_id/secret (from .env)</li>
 *   <li>build the UBL 2.1 document, sign it, POST it on-behalf-of the tenant's TIN</li>
 *   <li>poll for the validation result, then set VALIDATED + uuid/longId, or REJECTED + reason</li>
 * </ol>
 * Everything outside this class stays exactly as it is when that lands — which was the point
 * of moving it here.
 */
@Component
@RequiredArgsConstructor
public class EInvoiceConsumer {

    private static final Logger log = LoggerFactory.getLogger(EInvoiceConsumer.class);

    /** LHDN's validated-invoice portal; the public share URL (encoded into the QR) hangs off it. */
    private static final String MYINVOIS_PORTAL = "https://myinvois.hasil.gov.my";

    private final InvoiceRepo invoiceRepo;
    private final AuditService auditService;
    private final NotificationService notificationService;

    /**
     * 5 attempts with a longer backoff than the email consumer (5s, 10s, 20s, 40s): a tax
     * authority's API is exactly the kind of dependency that has brief outages, and a submission
     * is worth waiting on rather than dropping.
     */
    @RetryableTopic(
            attempts = "5",
            backOff = @BackOff(delay = 5000, multiplier = 2.0),
            dltTopicSuffix = "-dlt")
    @KafkaListener(topics = KafkaTopicConfig.EINVOICE_COMMANDS, groupId = "einvoice-submitter")
    @Transactional
    public void onSubmissionRequested(EInvoiceSubmissionRequested event) {
        Invoice invoice = invoiceRepo.findById(event.invoiceId())
                .orElseThrow(() -> new IllegalStateException(
                        "Invoice " + event.invoiceId() + " not found (eventId=" + event.eventId() + ")"));

        // Idempotency guard. Kafka is at-least-once, so this event can arrive twice; without
        // this, a redelivery would submit the same invoice to LHDN a second time and issue a
        // duplicate tax document. Cheap, and it makes redelivery harmless.
        if (invoice.getEinvoiceStatus() == EInvoiceStatus.VALIDATED) {
            log.debug("Invoice {} already validated — ignoring duplicate submission (eventId={})",
                    invoice.getInvoiceNumber(), event.eventId());
            return;
        }

        log.info("Submitting invoice {} to MyInvois", invoice.getInvoiceNumber());

        // --- SIMULATED LHDN validation (see TODO above) ---
        LocalDateTime now = LocalDateTime.now();
        String lhdnUuid = UUID.randomUUID().toString().replace("-", "").toUpperCase();
        String longId = lhdnUuid + invoice.getInvoiceNumber().replaceAll("\\D", "");

        invoice.setEinvoiceStatus(EInvoiceStatus.VALIDATED);
        invoice.setMyinvoisUuid(lhdnUuid);
        invoice.setMyinvoisLongId(longId);
        invoice.setEinvoiceValidationUrl(MYINVOIS_PORTAL + "/" + lhdnUuid + "/share/" + longId);
        invoice.setEinvoiceValidatedAt(now);
        invoice.setEinvoiceRejectionReason(null);

        Invoice saved = invoiceRepo.save(invoice);

        auditService.record(saved.getTenant(), "INVOICE", saved.getId(), "EINVOICE_VALIDATED", null,
                "MyInvois validated " + saved.getInvoiceNumber() + " (uuid " + lhdnUuid + ")");
        notificationService.notify(saved.getTenant(), "EINVOICE_VALIDATED",
                "E-invoice validated: " + saved.getInvoiceNumber(),
                saved.getInvoiceNumber() + " was validated by LHDN MyInvois.",
                "/invoices/" + saved.getUuid());
    }

    /**
     * Every retry failed. Park the invoice as REJECTED so the UI stops showing a spinner
     * forever, and tell the tenant — a submission stuck on PENDING with nobody notified is
     * exactly the silent failure this whole refactor set out to remove.
     */
    @DltHandler
    @Transactional
    public void dlt(EInvoiceSubmissionRequested event) {
        log.error("MyInvois submission PERMANENTLY failed — invoiceId={} eventId={}",
                event.invoiceId(), event.eventId());

        invoiceRepo.findById(event.invoiceId()).ifPresent(invoice -> {
            if (invoice.getEinvoiceStatus() != EInvoiceStatus.VALIDATED) {
                invoice.setEinvoiceStatus(EInvoiceStatus.REJECTED);
                invoice.setEinvoiceRejectionReason(
                        "Submission to MyInvois failed after repeated attempts. Try again later.");
                invoiceRepo.save(invoice);

                notificationService.notify(invoice.getTenant(), "EINVOICE_REJECTED",
                        "E-invoice failed: " + invoice.getInvoiceNumber(),
                        invoice.getInvoiceNumber() + " could not be submitted to LHDN MyInvois.",
                        "/invoices/" + invoice.getUuid());
            }
        });
    }
}
