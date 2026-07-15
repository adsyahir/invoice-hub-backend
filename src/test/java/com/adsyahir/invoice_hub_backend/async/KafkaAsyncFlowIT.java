package com.adsyahir.invoice_hub_backend.async;

import com.adsyahir.invoice_hub_backend.dao.InvoiceRepo;
import com.adsyahir.invoice_hub_backend.enums.EInvoiceStatus;
import com.adsyahir.invoice_hub_backend.model.Client;
import com.adsyahir.invoice_hub_backend.model.Invoice;
import com.adsyahir.invoice_hub_backend.model.Tenant;
import com.adsyahir.invoice_hub_backend.model.User;
import com.adsyahir.invoice_hub_backend.service.InvoiceService;
import com.adsyahir.invoice_hub_backend.service.PaymentService;
import com.adsyahir.invoice_hub_backend.support.AbstractIntegrationTest;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * The async pipeline, end to end, against a real Kafka broker:
 *
 * <pre>
 *   service (tx)  ->  ApplicationEvent  ->  [COMMIT]  ->  DomainEventRelay  ->  Kafka  ->  consumer
 * </pre>
 *
 * <p>Nothing here is mocked except the SMTP transport, so a passing test means a record really
 * was serialized, published, partitioned, polled and deserialized. Assertions are wrapped in
 * Awaitility rather than a sleep — the consumer runs on its own thread, and "wait until true,
 * up to N seconds" is both faster and not flaky.
 */
class KafkaAsyncFlowIT extends AbstractIntegrationTest {

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private InvoiceRepo invoiceRepo;

    private User accountant;
    private Client client;

    @BeforeEach
    void seedTenant() {
        Tenant tenant = fixtures.tenant("Acme Sdn Bhd", "acme");
        accountant = fixtures.user("accountant@acme.test", tenant,
                fixtures.role("ACCOUNTANT", "invoice:read", "invoice:write", "invoice:void",
                        "payment:read", "payment:record", "payment:refund"));
        client = fixtures.client(tenant, "Nexus Digital", "billing@nexus.test");
    }

    private Invoice draftInvoice(String number) {
        return invoiceService.createInvoice(fixtures.invoiceRequest(client, number), accountant);
    }

    // --- invoice email ------------------------------------------------------

    @Test
    @DisplayName("send: the request returns immediately, and a consumer emails the client afterwards")
    void sendingAnInvoiceEmailsTheClientAsynchronously() {
        Invoice invoice = draftInvoice("INV-2026-0300");

        invoiceService.send(invoice.getUuid(), accountant);

        // The whole point of the refactor: send() did not block on SMTP, but the mail still goes.
        verify(mailSender, timeout(15_000)).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("creating a draft emails nobody — only sending does")
    void creatingADraftPublishesNothing() {
        draftInvoice("INV-2026-0301");

        // Give the pipeline a chance to be wrong before declaring it right.
        verify(mailSender, timeout(2_000).times(0)).send(any(MimeMessage.class));
    }

    // --- payment receipt ----------------------------------------------------

    @Test
    @DisplayName("recording a payment emails the client a receipt")
    void recordingAPaymentEmailsAReceipt() {
        Invoice invoice = draftInvoice("INV-2026-0310");
        invoiceService.send(invoice.getUuid(), accountant);
        verify(mailSender, timeout(15_000)).send(any(MimeMessage.class));   // the invoice itself

        paymentService.create(fixtures.paymentRequest(invoice.getUuid(), "1080.00"), accountant);

        // Invoice email + receipt email = 2 sends.
        verify(mailSender, timeout(15_000).times(2)).send(any(MimeMessage.class));
    }

    // --- e-invoice: the async LHDN round-trip -------------------------------

    @Test
    @DisplayName("e-invoice: submit returns PENDING, then the consumer validates it")
    void einvoiceIsValidatedAsynchronously() {
        Invoice invoice = draftInvoice("INV-2026-0320");

        var queued = invoiceService.submitEInvoice(invoice.getUuid(), accountant);
        assertThat(queued.getEinvoiceStatus()).isEqualTo(EInvoiceStatus.PENDING);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            Invoice validated = invoiceRepo.findById(invoice.getId()).orElseThrow();
            assertThat(validated.getEinvoiceStatus()).isEqualTo(EInvoiceStatus.VALIDATED);
            assertThat(validated.getMyinvoisUuid()).isNotBlank();
            assertThat(validated.getMyinvoisLongId()).isNotBlank();
            assertThat(validated.getEinvoiceValidationUrl()).contains("myinvois.hasil.gov.my");
            assertThat(validated.getEinvoiceValidatedAt()).isNotNull();
        });
    }

    @Test
    @DisplayName("e-invoice: the validated invoice raises a notification for the org")
    void einvoiceValidationNotifiesTheOrg() {
        Invoice invoice = draftInvoice("INV-2026-0321");

        invoiceService.submitEInvoice(invoice.getUuid(), accountant);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            Integer notifications = jdbc.queryForObject(
                    "SELECT count(*) FROM notifications WHERE type = 'EINVOICE_VALIDATED'",
                    Integer.class);
            assertThat(notifications).isEqualTo(1);
        });
    }

    // --- the transactional guarantee ---------------------------------------

    @Test
    @DisplayName("a rejected send publishes nothing — no email for an action that never committed")
    void aFailedSendPublishesNoEvent() {
        Invoice invoice = draftInvoice("INV-2026-0330");
        invoiceService.voidInvoice(invoice.getUuid(), accountant);

        // send() throws 409 on a void invoice, so the transaction never commits...
        catchThrowableOfType(ResponseStatusException.class,
                () -> invoiceService.send(invoice.getUuid(), accountant));

        // ...and AFTER_COMMIT therefore never fires. Publishing inside the transaction instead
        // would have emailed the client about an invoice that was never sent.
        verify(mailSender, never()).send(any(MimeMessage.class));
    }
}
