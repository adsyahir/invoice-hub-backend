package com.adsyahir.invoice_hub_backend.service;

import com.adsyahir.invoice_hub_backend.dao.InvoiceRepo;
import com.adsyahir.invoice_hub_backend.dao.PaymentRepo;
import com.adsyahir.invoice_hub_backend.dto.request.CreatePaymentRequest;
import com.adsyahir.invoice_hub_backend.dto.response.PaymentResponse;
import com.adsyahir.invoice_hub_backend.enums.InvoiceStatus;
import com.adsyahir.invoice_hub_backend.enums.PaymentMethod;
import com.adsyahir.invoice_hub_backend.enums.PaymentStatus;
import com.adsyahir.invoice_hub_backend.event.PaymentRecordedEvent;
import com.adsyahir.invoice_hub_backend.event.SearchIndexRequested;
import com.adsyahir.invoice_hub_backend.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepo paymentRepo;
    private final InvoiceRepo invoiceRepo;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher events;
    private final ReportCacheEvictor reportCacheEvictor;


    private static String invoiceLink(Invoice invoice) {
        return "/invoices/" + invoice.getUuid();
    }

    /**
     * Record a manual (offline) payment against an invoice, then recompute the
     * invoice's paid/due totals and status (US-021/022). Payments are the source
     * of truth: the invoice amounts are always derived from its payment rows.
     */
    @Transactional
    public PaymentResponse create(CreatePaymentRequest request, User currentUser) {
        requireTenant(currentUser);

        Invoice invoice = invoiceRepo
                .findByUuidAndTenantId(request.getInvoiceId(), currentUser.getTenant().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));

        if (invoice.getStatus() == InvoiceStatus.VOID) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot record a payment against a void invoice");
        }
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This invoice is already fully paid");
        }

        BigDecimal amount = request.getAmount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal due = nz(invoice.getAmountDue());
        if (amount.compareTo(due) > 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Payment (" + amount + ") exceeds the outstanding balance (" + due + ")");
        }

        Payment payment = Payment.builder()
                .tenant(currentUser.getTenant())
                .invoice(invoice)
                .amount(amount)
                .currency(invoice.getCurrency())
                .method(request.getMethod())
                .status(PaymentStatus.COMPLETED)
                .gateway("MANUAL")
                .reference(request.getReference())
                .recordedBy(currentUser)
                .paidAt(LocalDateTime.now())
                .build();
        payment = paymentRepo.save(payment);

        recomputeAndSaveInvoice(invoice);
        auditService.record(invoice.getTenant(), "PAYMENT", payment.getId(), "RECORDED", currentUser,
                "Recorded " + invoice.getCurrency() + " " + amount + " against " + invoice.getInvoiceNumber());
        notifyPayment(invoice, amount, "Payment recorded");

        // Published AFTER recomputeAndSaveInvoice: that call is what flips the invoice to PAID,
        // so reading the status any earlier would always report settlesInvoice = false.
        // The receipt email is sent by PaymentReceiptConsumer once this transaction commits.
        events.publishEvent(PaymentRecordedEvent.of(
                invoice.getTenant().getId(),
                invoice.getId(),
                payment.getUuid(),
                amount,
                invoice.getCurrency(),
                invoice.getStatus() == InvoiceStatus.PAID));

        return toResponse(payment);
    }

    /** Notify the org of a payment; add a "fully paid" note if it settled the invoice. */
    private void notifyPayment(Invoice invoice, BigDecimal amount, String verb) {
        notificationService.notify(invoice.getTenant(), "PAYMENT_RECEIVED",
                verb + ": " + invoice.getInvoiceNumber(),
                invoice.getCurrency() + " " + amount + " received for " + invoice.getInvoiceNumber() + ".",
                invoiceLink(invoice));
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            notificationService.notify(invoice.getTenant(), "INVOICE_PAID",
                    "Invoice paid: " + invoice.getInvoiceNumber(),
                    invoice.getInvoiceNumber() + " is now fully paid.",
                    invoiceLink(invoice));
        }
    }

    /**
     * Fully refund a completed payment (US-024). Flips it to REFUNDED and
     * recomputes the invoice — a fully-refunded invoice returns to REFUNDED,
     * a partially-refunded one back to PARTIALLY_PAID / SENT.
     */
    @Transactional
    public PaymentResponse refund(java.util.UUID paymentUuid, User currentUser) {
        requireTenant(currentUser);

        Payment payment = paymentRepo.findByUuidAndTenantId(paymentUuid, currentUser.getTenant().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));

        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a completed payment can be refunded");
        }

        payment.setStatus(PaymentStatus.REFUNDED);
        payment = paymentRepo.save(payment);

        recomputeAndSaveInvoice(payment.getInvoice());
        auditService.record(payment.getTenant(), "PAYMENT", payment.getId(), "REFUNDED", currentUser,
                "Refunded " + payment.getCurrency() + " " + payment.getAmount());
        notificationService.notify(payment.getTenant(), "PAYMENT_REFUNDED",
                "Refund issued: " + payment.getInvoice().getInvoiceNumber(),
                payment.getCurrency() + " " + payment.getAmount() + " refunded on "
                        + payment.getInvoice().getInvoiceNumber() + ".",
                invoiceLink(payment.getInvoice()));

        return toResponse(payment);
    }

    /**
     * Pay an invoice in full via its public payment link (US-020). Simulates a
     * gateway charge — records a COMPLETED payment for the full outstanding
     * balance and recomputes the invoice to PAID. No authenticated user, so the
     * payment is not attributed to a team member.
     */
    @Transactional
    public PaymentResponse payViaLink(String token, PaymentMethod method) {
        Invoice invoice = invoiceRepo.findByPaymentLinkToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));

        if (invoice.getPaymentLinkExpiresAt() != null
                && invoice.getPaymentLinkExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "This payment link has expired");
        }
        if (invoice.getStatus() == InvoiceStatus.VOID) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This invoice is no longer payable");
        }
        BigDecimal due = nz(invoice.getAmountDue());
        if (due.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This invoice is already paid");
        }

        Payment payment = Payment.builder()
                .tenant(invoice.getTenant())
                .invoice(invoice)
                .amount(due.setScale(2, RoundingMode.HALF_UP))
                .currency(invoice.getCurrency())
                .method(method)
                .status(PaymentStatus.COMPLETED)
                .gateway("ONLINE")
                .gatewayTxnId("SIM-" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16))
                .recordedBy(null)
                .paidAt(LocalDateTime.now())
                .build();
        payment = paymentRepo.save(payment);

        recomputeAndSaveInvoice(invoice);
        auditService.record(invoice.getTenant(), "PAYMENT", payment.getId(), "PAID_ONLINE", null,
                "Online payment of " + invoice.getCurrency() + " " + due + " via " + method);
        notifyPayment(invoice, due, "Online payment received");

        // A client who paid through the public link gets a receipt too.
        events.publishEvent(PaymentRecordedEvent.of(
                invoice.getTenant().getId(),
                invoice.getId(),
                payment.getUuid(),
                due,
                invoice.getCurrency(),
                invoice.getStatus() == InvoiceStatus.PAID));

        return toResponse(payment);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> list(User currentUser) {
        if (currentUser.getTenant() == null) {
            return List.of();
        }
        return paymentRepo.findByTenantIdOrderByPaidAtDesc(currentUser.getTenant().getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> listForInvoice(java.util.UUID invoiceUuid, User currentUser) {
        requireTenant(currentUser);
        Invoice invoice = invoiceRepo.findByUuidAndTenantId(invoiceUuid, currentUser.getTenant().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));
        return paymentRepo.findByInvoiceId(invoice.getId()).stream()
                .sorted((a, b) -> b.getPaidAt().compareTo(a.getPaidAt()))
                .map(this::toResponse)
                .toList();
    }

    /**
     * Recompute an invoice's amountPaid / amountDue / status from its payment
     * rows. Shared by create, refund and the public pay flow so the derivation
     * lives in exactly one place. VOID invoices are never re-statused here.
     */
    void recomputeAndSaveInvoice(Invoice invoice) {
        List<Payment> payments = paymentRepo.findByInvoiceId(invoice.getId());

        BigDecimal completed = payments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.COMPLETED)
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal refunded = payments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.REFUNDED)
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // A refund flips the payment COMPLETED -> REFUNDED, so a refunded payment is
        // ALREADY excluded from `completed`. Subtracting `refunded` here as well would
        // count it twice: refunding a fully-paid RM1,080 invoice produced an amountPaid
        // of -1,080.00 and an amountDue of 2,160.00. What is still paid is exactly the
        // sum of the payments that are still COMPLETED.
        BigDecimal paid = completed.setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = nz(invoice.getTotalAmount());
        BigDecimal outstanding = total.subtract(paid);

        invoice.setAmountPaid(paid);
        invoice.setAmountDue(outstanding);

        if (invoice.getStatus() != InvoiceStatus.VOID) {
            if (refunded.signum() > 0 && paid.signum() <= 0) {
                invoice.setStatus(InvoiceStatus.REFUNDED);
                invoice.setPaidAt(null);
            } else if (outstanding.signum() <= 0 && total.signum() > 0) {
                invoice.setStatus(InvoiceStatus.PAID);
                if (invoice.getPaidAt() == null) {
                    invoice.setPaidAt(LocalDateTime.now());
                }
            } else if (paid.signum() > 0) {
                invoice.setStatus(InvoiceStatus.PARTIALLY_PAID);
                invoice.setPaidAt(null);
            } else {
                // No net payment left — fall back to SENT if it was ever sent, else DRAFT.
                invoice.setStatus(invoice.getSentAt() != null ? InvoiceStatus.SENT : InvoiceStatus.DRAFT);
                invoice.setPaidAt(null);
            }
        }

        invoiceRepo.save(invoice);

        // Every payment path (create / refund / payViaLink) funnels through here, and each
        // changes amountPaid / amountDue / status — the exact inputs to the cached reports. So
        // this is the one place to evict. reportCacheEvictor is a separate bean, so the call
        // goes through its proxy and @CacheEvict actually fires (a same-bean call would not).
        if (invoice.getTenant() != null) {
            reportCacheEvictor.evict(invoice.getTenant().getId());

            // Same choke-point logic for search: status/amounts are indexed, so refresh
            // the invoice's Elasticsearch document (async, via Kafka, after commit).
            events.publishEvent(SearchIndexRequested.invoice(
                    invoice.getTenant().getId(), invoice.getId()));
        }
    }

    private void requireTenant(User user) {
        if (user.getTenant() == null || user.getTenant().getId() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found");
        }
    }

    private PaymentResponse toResponse(Payment p) {
        return PaymentResponse.builder()
                .id(p.getUuid())
                .invoiceId(p.getInvoice() != null ? p.getInvoice().getUuid() : null)
                .invoiceNumber(p.getInvoice() != null ? p.getInvoice().getInvoiceNumber() : null)
                .amount(p.getAmount())
                .currency(p.getCurrency())
                .method(p.getMethod())
                .status(p.getStatus())
                .gateway(p.getGateway())
                .reference(p.getReference())
                .recordedById(p.getRecordedBy() != null ? p.getRecordedBy().getId() : null)
                .recordedByName(p.getRecordedBy() != null ? p.getRecordedBy().getFullName() : null)
                .paidAt(p.getPaidAt())
                .createdAt(p.getCreatedAt())
                .build();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
