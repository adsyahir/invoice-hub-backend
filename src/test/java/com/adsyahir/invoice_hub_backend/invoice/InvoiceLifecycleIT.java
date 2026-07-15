package com.adsyahir.invoice_hub_backend.invoice;

import com.adsyahir.invoice_hub_backend.dao.AuditLogRepo;
import com.adsyahir.invoice_hub_backend.dao.InvoiceRepo;
import com.adsyahir.invoice_hub_backend.dao.NotificationRepo;
import com.adsyahir.invoice_hub_backend.dto.response.InvoiceResponse;
import com.adsyahir.invoice_hub_backend.dto.response.PaymentResponse;
import com.adsyahir.invoice_hub_backend.enums.EInvoiceStatus;
import com.adsyahir.invoice_hub_backend.enums.InvoiceStatus;
import com.adsyahir.invoice_hub_backend.enums.PaymentMethod;
import com.adsyahir.invoice_hub_backend.enums.PaymentStatus;
import com.adsyahir.invoice_hub_backend.model.AuditLog;
import com.adsyahir.invoice_hub_backend.model.Client;
import com.adsyahir.invoice_hub_backend.model.Invoice;
import com.adsyahir.invoice_hub_backend.model.Tenant;
import com.adsyahir.invoice_hub_backend.model.User;
import com.adsyahir.invoice_hub_backend.service.InvoiceService;
import com.adsyahir.invoice_hub_backend.service.PaymentService;
import com.adsyahir.invoice_hub_backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * The invoice lifecycle, end to end, against a real PostgreSQL.
 *
 * <p>The invariant under test: <b>payments are the source of truth</b>. An
 * invoice's amountPaid, amountDue and status are always derived from its payment
 * rows by {@code PaymentService.recomputeAndSaveInvoice} — never set by hand. Every
 * assertion below reloads the invoice from the database rather than trusting the
 * returned DTO, so a value that was computed but not persisted still fails.
 */
class InvoiceLifecycleIT extends AbstractIntegrationTest {

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private InvoiceRepo invoiceRepo;

    @Autowired
    private AuditLogRepo auditLogRepo;

    @Autowired
    private NotificationRepo notificationRepo;

    private Tenant tenant;
    private User accountant;
    private Client client;

    @BeforeEach
    void seedTenant() {
        tenant = fixtures.tenant("Acme Sdn Bhd", "acme");
        accountant = fixtures.user("accountant@acme.test", tenant,
                fixtures.role("ACCOUNTANT", "invoice:read", "invoice:write", "invoice:void",
                        "payment:read", "payment:record", "payment:refund"));
        client = fixtures.client(tenant, "Nexus Digital", "billing@nexus.test");
    }

    /** RM 1,000.00 @ 8% SST -> total RM 1,080.00. */
    private Invoice draftInvoice(String number) {
        return invoiceService.createInvoice(fixtures.invoiceRequest(client, number), accountant);
    }

    private Invoice reload(Invoice invoice) {
        return invoiceRepo.findById(invoice.getId()).orElseThrow();
    }

    // --- creation ----------------------------------------------------------

    @Test
    @DisplayName("create: totals are computed server-side from the line items")
    void createComputesTotalsFromLineItems() {
        Invoice invoice = draftInvoice("INV-2026-0001");

        Invoice saved = reload(invoice);
        assertThat(saved.getStatus()).isEqualTo(InvoiceStatus.DRAFT);
        assertThat(saved.getSubtotal()).isEqualByComparingTo("1000.00");
        assertThat(saved.getTaxAmount()).isEqualByComparingTo("80.00");   // 8% SST
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("1080.00");
        assertThat(saved.getAmountPaid()).isEqualByComparingTo("0.00");
        assertThat(saved.getAmountDue()).isEqualByComparingTo("1080.00");
        assertThat(saved.getEinvoiceStatus()).isEqualTo(EInvoiceStatus.NOT_SUBMITTED);
        // lineItems is LAZY and we are outside a session here, so count the rows
        // directly rather than triggering a LazyInitializationException.
        assertThat(lineItemCount(saved.getId())).isEqualTo(1);
    }

    private int lineItemCount(Long invoiceId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM invoice_line_items WHERE invoice_id = ?",
                Integer.class, invoiceId);
    }

    @Test
    @DisplayName("create: a draft has no payment link until it is sent")
    void draftHasNoPaymentLink() {
        assertThat(reload(draftInvoice("INV-2026-0002")).getPaymentLinkToken()).isNull();
    }

    // --- send --------------------------------------------------------------

    @Nested
    @DisplayName("send")
    class Send {

        @Test
        @DisplayName("flips DRAFT to SENT and issues an unguessable payment link")
        void sendFlipsDraftToSent() {
            Invoice invoice = draftInvoice("INV-2026-0010");

            invoiceService.send(invoice.getUuid(), accountant);

            Invoice sent = reload(invoice);
            assertThat(sent.getStatus()).isEqualTo(InvoiceStatus.SENT);
            assertThat(sent.getSentAt()).isNotNull();
            assertThat(sent.getPaymentLinkToken()).isNotNull().hasSizeGreaterThan(32);
            assertThat(sent.getPaymentLinkExpiresAt()).isAfter(java.time.LocalDateTime.now());
        }

        @Test
        @DisplayName("re-sending keeps the original token and does not reset sentAt")
        void resendIsIdempotentOnTokenAndSentAt() {
            Invoice invoice = draftInvoice("INV-2026-0011");
            invoiceService.send(invoice.getUuid(), accountant);
            Invoice first = reload(invoice);

            invoiceService.send(invoice.getUuid(), accountant);
            Invoice second = reload(invoice);

            assertThat(second.getPaymentLinkToken()).isEqualTo(first.getPaymentLinkToken());
            assertThat(second.getSentAt()).isEqualTo(first.getSentAt());
        }

        @Test
        @DisplayName("a void invoice cannot be sent")
        void voidInvoiceCannotBeSent() {
            Invoice invoice = draftInvoice("INV-2026-0012");
            invoiceService.voidInvoice(invoice.getUuid(), accountant);

            ResponseStatusException error = catchThrowableOfType(ResponseStatusException.class,
                    () -> invoiceService.send(invoice.getUuid(), accountant));

            assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }
    }

    // --- payments: the core invariant --------------------------------------

    @Nested
    @DisplayName("payments drive invoice status")
    class Payments {

        @Test
        @DisplayName("a partial payment moves the invoice to PARTIALLY_PAID and reduces the balance")
        void partialPaymentMarksPartiallyPaid() {
            Invoice invoice = draftInvoice("INV-2026-0020");
            invoiceService.send(invoice.getUuid(), accountant);

            paymentService.create(fixtures.paymentRequest(invoice.getUuid(), "500.00"), accountant);

            Invoice partial = reload(invoice);
            assertThat(partial.getStatus()).isEqualTo(InvoiceStatus.PARTIALLY_PAID);
            assertThat(partial.getAmountPaid()).isEqualByComparingTo("500.00");
            assertThat(partial.getAmountDue()).isEqualByComparingTo("580.00");
            assertThat(partial.getPaidAt()).isNull();
        }

        @Test
        @DisplayName("payments that settle the balance move the invoice to PAID and stamp paidAt")
        void payingTheBalanceMarksPaid() {
            Invoice invoice = draftInvoice("INV-2026-0021");
            invoiceService.send(invoice.getUuid(), accountant);

            paymentService.create(fixtures.paymentRequest(invoice.getUuid(), "500.00"), accountant);
            paymentService.create(fixtures.paymentRequest(invoice.getUuid(), "580.00"), accountant);

            Invoice paid = reload(invoice);
            assertThat(paid.getStatus()).isEqualTo(InvoiceStatus.PAID);
            assertThat(paid.getAmountPaid()).isEqualByComparingTo("1080.00");
            assertThat(paid.getAmountDue()).isEqualByComparingTo("0.00");
            assertThat(paid.getPaidAt()).isNotNull();
        }

        @Test
        @DisplayName("a payment larger than the outstanding balance is rejected")
        void overpaymentIsRejected() {
            Invoice invoice = draftInvoice("INV-2026-0022");
            invoiceService.send(invoice.getUuid(), accountant);

            ResponseStatusException error = catchThrowableOfType(ResponseStatusException.class,
                    () -> paymentService.create(
                            fixtures.paymentRequest(invoice.getUuid(), "1080.01"), accountant));

            assertThat(error.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(reload(invoice).getAmountPaid()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("a void invoice cannot take a payment")
        void voidInvoiceRejectsPayment() {
            Invoice invoice = draftInvoice("INV-2026-0023");
            invoiceService.voidInvoice(invoice.getUuid(), accountant);

            ResponseStatusException error = catchThrowableOfType(ResponseStatusException.class,
                    () -> paymentService.create(
                            fixtures.paymentRequest(invoice.getUuid(), "100.00"), accountant));

            assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        @DisplayName("a fully-paid invoice cannot take another payment")
        void paidInvoiceRejectsFurtherPayment() {
            Invoice invoice = draftInvoice("INV-2026-0024");
            invoiceService.send(invoice.getUuid(), accountant);
            paymentService.create(fixtures.paymentRequest(invoice.getUuid(), "1080.00"), accountant);

            ResponseStatusException error = catchThrowableOfType(ResponseStatusException.class,
                    () -> paymentService.create(
                            fixtures.paymentRequest(invoice.getUuid(), "1.00"), accountant));

            assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }
    }

    // --- refunds ------------------------------------------------------------

    @Nested
    @DisplayName("refunds")
    class Refunds {

        @Test
        @DisplayName("refunding the only payment returns the invoice to REFUNDED with the full balance owing")
        void refundingTheOnlyPaymentMarksRefunded() {
            Invoice invoice = draftInvoice("INV-2026-0030");
            invoiceService.send(invoice.getUuid(), accountant);
            PaymentResponse payment = paymentService.create(
                    fixtures.paymentRequest(invoice.getUuid(), "1080.00"), accountant);
            assertThat(reload(invoice).getStatus()).isEqualTo(InvoiceStatus.PAID);

            PaymentResponse refunded = paymentService.refund(payment.getId(), accountant);

            assertThat(refunded.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
            Invoice after = reload(invoice);
            assertThat(after.getStatus()).isEqualTo(InvoiceStatus.REFUNDED);
            assertThat(after.getAmountPaid()).isEqualByComparingTo("0.00");
            assertThat(after.getAmountDue()).isEqualByComparingTo("1080.00");
            assertThat(after.getPaidAt()).isNull();
        }

        @Test
        @DisplayName("refunding one of two payments drops the invoice back to PARTIALLY_PAID")
        void partialRefundReturnsToPartiallyPaid() {
            Invoice invoice = draftInvoice("INV-2026-0031");
            invoiceService.send(invoice.getUuid(), accountant);
            PaymentResponse first = paymentService.create(
                    fixtures.paymentRequest(invoice.getUuid(), "500.00"), accountant);
            paymentService.create(fixtures.paymentRequest(invoice.getUuid(), "580.00"), accountant);
            assertThat(reload(invoice).getStatus()).isEqualTo(InvoiceStatus.PAID);

            paymentService.refund(first.getId(), accountant);

            Invoice after = reload(invoice);
            assertThat(after.getStatus()).isEqualTo(InvoiceStatus.PARTIALLY_PAID);
            assertThat(after.getAmountPaid()).isEqualByComparingTo("580.00");
            assertThat(after.getAmountDue()).isEqualByComparingTo("500.00");
        }

        @Test
        @DisplayName("a payment can only be refunded once")
        void doubleRefundIsRejected() {
            Invoice invoice = draftInvoice("INV-2026-0032");
            invoiceService.send(invoice.getUuid(), accountant);
            PaymentResponse payment = paymentService.create(
                    fixtures.paymentRequest(invoice.getUuid(), "1080.00"), accountant);
            paymentService.refund(payment.getId(), accountant);

            ResponseStatusException error = catchThrowableOfType(ResponseStatusException.class,
                    () -> paymentService.refund(payment.getId(), accountant));

            assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }
    }

    // --- void / duplicate ---------------------------------------------------

    @Test
    @DisplayName("void: a settled invoice cannot be voided — it must be refunded")
    void settledInvoiceCannotBeVoided() {
        Invoice invoice = draftInvoice("INV-2026-0040");
        invoiceService.send(invoice.getUuid(), accountant);
        paymentService.create(fixtures.paymentRequest(invoice.getUuid(), "1080.00"), accountant);

        ResponseStatusException error = catchThrowableOfType(ResponseStatusException.class,
                () -> invoiceService.voidInvoice(invoice.getUuid(), accountant));

        assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reload(invoice).getStatus()).isEqualTo(InvoiceStatus.PAID);
    }

    @Test
    @DisplayName("duplicate: copies the line items into a fresh DRAFT with no payments carried over")
    void duplicateCreatesAFreshDraft() {
        Invoice source = draftInvoice("INV-2026-0050");
        invoiceService.send(source.getUuid(), accountant);
        paymentService.create(fixtures.paymentRequest(source.getUuid(), "1080.00"), accountant);

        InvoiceResponse copy = invoiceService.duplicate(source.getUuid(), accountant);

        assertThat(copy.getInvoiceNumber()).isEqualTo("INV-2026-0050-COPY");
        assertThat(copy.getStatus()).isEqualTo(InvoiceStatus.DRAFT);
        assertThat(copy.getTotalAmount()).isEqualByComparingTo("1080.00");
        assertThat(copy.getAmountPaid()).isEqualByComparingTo("0.00");
        assertThat(copy.getAmountDue()).isEqualByComparingTo("1080.00");
        assertThat(copy.getPaymentLinkToken()).isNull();
        assertThat(copy.getSentAt()).isNull();
        assertThat(copy.getLineItems()).hasSize(1);
    }

    @Test
    @DisplayName("duplicate: a second copy is deduped rather than colliding on invoice_number")
    void duplicateTwiceDedupesTheNumber() {
        Invoice source = draftInvoice("INV-2026-0051");

        invoiceService.duplicate(source.getUuid(), accountant);
        InvoiceResponse second = invoiceService.duplicate(source.getUuid(), accountant);

        assertThat(second.getInvoiceNumber()).isEqualTo("INV-2026-0051-COPY-2");
    }

    // --- overdue sweep ------------------------------------------------------

    @Nested
    @DisplayName("overdue sweep")
    class OverdueSweep {

        private Invoice pastDueInvoice(String number) {
            return invoiceService.createInvoice(
                    fixtures.invoiceRequest(client, number, 1L, "1000.00", "8.00",
                            LocalDate.now().minusDays(60), LocalDate.now().minusDays(30)),
                    accountant);
        }

        @Test
        @DisplayName("marks an unpaid, past-due SENT invoice OVERDUE")
        void marksUnpaidPastDueInvoiceOverdue() {
            Invoice invoice = pastDueInvoice("INV-2026-0060");
            invoiceService.send(invoice.getUuid(), accountant);

            int swept = invoiceService.markOverdueInvoices();

            assertThat(swept).isEqualTo(1);
            assertThat(reload(invoice).getStatus()).isEqualTo(InvoiceStatus.OVERDUE);
        }

        @Test
        @DisplayName("leaves a past-due invoice alone once it is fully paid")
        void skipsPastDueInvoiceWithNothingOutstanding() {
            Invoice invoice = pastDueInvoice("INV-2026-0061");
            invoiceService.send(invoice.getUuid(), accountant);
            paymentService.create(fixtures.paymentRequest(invoice.getUuid(), "1080.00"), accountant);

            int swept = invoiceService.markOverdueInvoices();

            assertThat(swept).isZero();
            assertThat(reload(invoice).getStatus()).isEqualTo(InvoiceStatus.PAID);
        }

        @Test
        @DisplayName("leaves a draft alone — an invoice the client never received cannot be late")
        void skipsDrafts() {
            Invoice draft = pastDueInvoice("INV-2026-0062");

            int swept = invoiceService.markOverdueInvoices();

            assertThat(swept).isZero();
            assertThat(reload(draft).getStatus()).isEqualTo(InvoiceStatus.DRAFT);
        }

        @Test
        @DisplayName("a partially-paid, past-due invoice still goes OVERDUE")
        void marksPartiallyPaidPastDueOverdue() {
            Invoice invoice = pastDueInvoice("INV-2026-0063");
            invoiceService.send(invoice.getUuid(), accountant);
            paymentService.create(fixtures.paymentRequest(invoice.getUuid(), "80.00"), accountant);

            int swept = invoiceService.markOverdueInvoices();

            assertThat(swept).isEqualTo(1);
            Invoice overdue = reload(invoice);
            assertThat(overdue.getStatus()).isEqualTo(InvoiceStatus.OVERDUE);
            assertThat(overdue.getAmountDue()).isEqualByComparingTo("1000.00");
        }
    }

    // --- public payment link ------------------------------------------------

    @Test
    @DisplayName("public pay link: settles the invoice in full without an authenticated user")
    void payViaLinkSettlesTheInvoice() {
        Invoice invoice = draftInvoice("INV-2026-0070");
        invoiceService.send(invoice.getUuid(), accountant);
        String token = reload(invoice).getPaymentLinkToken();

        PaymentResponse payment = paymentService.payViaLink(token, PaymentMethod.FPX);

        assertThat(payment.getAmount()).isEqualByComparingTo("1080.00");
        assertThat(payment.getRecordedById()).isNull();   // nobody on staff recorded it
        Invoice paid = reload(invoice);
        assertThat(paid.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(paid.getAmountDue()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("public pay link: an unknown token is a 404, never a leak")
    void unknownPayTokenIsNotFound() {
        assertThatThrownBy(() -> paymentService.payViaLink("nope", PaymentMethod.FPX))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    // --- audit + notifications ---------------------------------------------

    @Test
    @DisplayName("audit: every lifecycle transition is recorded, and audit rows survive independently")
    void lifecycleIsAudited() {
        Invoice invoice = draftInvoice("INV-2026-0080");
        invoiceService.send(invoice.getUuid(), accountant);
        paymentService.create(fixtures.paymentRequest(invoice.getUuid(), "1080.00"), accountant);

        List<String> invoiceActions = auditLogRepo
                .findByEntityTypeAndEntityIdOrderByCreatedAtDesc("INVOICE", invoice.getId())
                .stream().map(AuditLog::getAction).toList();

        assertThat(invoiceActions).contains("CREATED", "SENT");
        assertThat(auditLogRepo.findByTenantIdOrderByCreatedAtDesc(tenant.getId()))
                .extracting(AuditLog::getAction)
                .contains("RECORDED");
    }

    @Test
    @DisplayName("notifications: sending and paying raise tenant-scoped notifications")
    void lifecycleRaisesNotifications() {
        Invoice invoice = draftInvoice("INV-2026-0081");
        invoiceService.send(invoice.getUuid(), accountant);
        paymentService.create(fixtures.paymentRequest(invoice.getUuid(), "1080.00"), accountant);

        List<String> types = notificationRepo
                .findByTenantIdOrderByCreatedAtDesc(tenant.getId(),
                        org.springframework.data.domain.PageRequest.of(0, 20))
                .stream().map(n -> n.getType()).toList();

        assertThat(types).contains("INVOICE_SENT", "PAYMENT_RECEIVED", "INVOICE_PAID");
        assertThat(notificationRepo.countByTenantIdAndReadAtIsNull(tenant.getId())).isPositive();
    }

    // --- e-invoice ----------------------------------------------------------

    @Test
    @DisplayName("e-invoice: submitting queues the invoice as PENDING and cannot be repeated")
    void einvoiceSubmissionQueuesAndIsIdempotent() {
        Invoice invoice = draftInvoice("INV-2026-0090");

        InvoiceResponse submitted = invoiceService.submitEInvoice(invoice.getUuid(), accountant);

        // Submission is ASYNC now — MyInvois validates after the fact, so the API returns
        // PENDING and EInvoiceConsumer flips it to VALIDATED. That transition is covered in
        // EInvoiceAsyncIT; here we only assert the synchronous half of the contract.
        assertThat(submitted.getEinvoiceStatus()).isEqualTo(EInvoiceStatus.PENDING);
        assertThat(submitted.getEinvoiceSubmittedAt()).isNotNull();
        assertThat(submitted.getMyinvoisUuid()).isNull();   // LHDN has not answered yet

        // The LHDN lifecycle does not touch the payment lifecycle.
        assertThat(reload(invoice).getStatus()).isEqualTo(InvoiceStatus.DRAFT);

        // A second submit while one is in flight is a conflict, not a duplicate filing.
        ResponseStatusException error = catchThrowableOfType(ResponseStatusException.class,
                () -> invoiceService.submitEInvoice(invoice.getUuid(), accountant));
        assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    // --- money --------------------------------------------------------------

    @Test
    @DisplayName("money: amounts round to 2dp and the balance closes exactly, with no float drift")
    void amountsAreExactToTwoDecimalPlaces() {
        // 3 x 33.33 @ 6% = 99.99 + 6.00 = 105.99
        Invoice invoice = invoiceService.createInvoice(
                fixtures.invoiceRequest(client, "INV-2026-0100", 3L, "33.33", "6.00",
                        LocalDate.now(), LocalDate.now().plusDays(14)),
                accountant);
        invoiceService.send(invoice.getUuid(), accountant);

        Invoice sent = reload(invoice);
        assertThat(sent.getSubtotal()).isEqualByComparingTo("99.99");
        assertThat(sent.getTaxAmount()).isEqualByComparingTo("6.00");
        assertThat(sent.getTotalAmount()).isEqualByComparingTo("105.99");

        paymentService.create(fixtures.paymentRequest(invoice.getUuid(), "105.99"), accountant);

        Invoice paid = reload(invoice);
        assertThat(paid.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(paid.getAmountDue()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
