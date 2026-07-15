package com.adsyahir.invoice_hub_backend.service;

import com.adsyahir.invoice_hub_backend.dao.ClientRepo;
import com.adsyahir.invoice_hub_backend.dao.InvoiceRepo;
import com.adsyahir.invoice_hub_backend.dto.request.CreateInvoiceRequest;
import com.adsyahir.invoice_hub_backend.dto.LineItemRequest;
import com.adsyahir.invoice_hub_backend.enums.EInvoiceStatus;
import com.adsyahir.invoice_hub_backend.enums.InvoiceStatus;
import com.adsyahir.invoice_hub_backend.event.EInvoiceSubmissionRequested;
import com.adsyahir.invoice_hub_backend.event.InvoiceSentEvent;
import com.adsyahir.invoice_hub_backend.exception.ValidationException;
import com.adsyahir.invoice_hub_backend.dto.response.AuditLogResponse;
import com.adsyahir.invoice_hub_backend.dto.response.PublicInvoiceResponse;
import com.adsyahir.invoice_hub_backend.model.Client;
import com.adsyahir.invoice_hub_backend.model.Invoice;
import com.adsyahir.invoice_hub_backend.model.InvoiceLineItem;
import com.adsyahir.invoice_hub_backend.model.User;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.adsyahir.invoice_hub_backend.dto.response.InvoiceResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InvoiceService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);

    private final InvoiceRepo invoiceRepo;
    private final ClientRepo clientRepo;
    private final AuditService auditService;
    private final InvoicePdfService pdfService;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher events;
    private final ReportCacheEvictor reportCacheEvictor;

    // No JavaMailSender here any more: outbound email moved to InvoiceEmailService,
    // driven by a Kafka consumer. This service no longer does I/O it cannot roll back.


    private static String invoiceLink(Invoice invoice) {
        return "/invoices/" + invoice.getUuid();
    }

    /** Drop the tenant's cached reports after a write that changes their figures. */
    private void evictReports(Invoice invoice) {
        if (invoice.getTenant() != null) {
            reportCacheEvictor.evict(invoice.getTenant().getId());
        }
    }

    /**
     * Create a DRAFT invoice for the authenticated user. All amounts are
     * recomputed here from the line items — the client only sends quantities,
     * unit prices and tax rates (US-011: totals are server-authoritative).
     */
    @Transactional
    public Invoice createInvoice(CreateInvoiceRequest req, User currentUser) {

        long id;
        try {
            id = Long.parseLong(req.getClientId());
        } catch (NumberFormatException e) {
            throw new ValidationException(Map.of("clientId", "Invalid client"));
        }

        Optional<Client> result = clientRepo.findById(id);

        if (result.isEmpty()) {
            throw new ValidationException(Map.of("clientId", "Client not found"));
        }

        Client client = result.get();

        if (currentUser.getTenant() != null && !currentUser.getTenant().getId().equals(client.getTenant().getId())) {
            throw new ValidationException(Map.of("clientId", "Client not found"));
        }

        if (invoiceRepo.existsByInvoiceNumber(req.getInvoiceNumber())) {
            throw new ValidationException(Map.of("invoiceNumber", "Invoice number already exists"));
       }

        Invoice invoice = Invoice.builder()
                .tenant(client.getTenant())
                .client(client)
                .createdBy(currentUser)
                .invoiceNumber(req.getInvoiceNumber())
                .status(InvoiceStatus.DRAFT)
                .currency(req.getCurrency())
                .issueDate(req.getIssueDate())
                .dueDate(req.getDueDate())
                .notes(req.getNotes())
                .internalNotes(req.getInternalNotes())
                .build();

        BigDecimal subTotal = BigDecimal.ZERO;;
        BigDecimal taxTotal = BigDecimal.ZERO;
        int order = 0;

        for (LineItemRequest li : req.getLineItems()) {
            BigDecimal qty = BigDecimal.valueOf(li.getQuantity());
            BigDecimal base = li.getUnitPrice().multiply(qty);
            BigDecimal lineTax = base.multiply(li.getTaxRate())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            BigDecimal lineTotal = base.add(lineTax).setScale(2, RoundingMode.HALF_UP);

            InvoiceLineItem item = new InvoiceLineItem();
            item.setDescription(li.getDescription());
            item.setQuantity(li.getQuantity());
            item.setUnitPrice(li.getUnitPrice().setScale(2, RoundingMode.HALF_UP));
            item.setTaxRate(li.getTaxRate());
            item.setTaxAmount(lineTax);
            item.setLineTotal(lineTotal);
            item.setSortOrder(order++);
            invoice.addLineItem(item);

            subTotal = subTotal.add(base);
            taxTotal = taxTotal.add(lineTax);
        }



        // 5. Invoice-level totals. No discount is collected on create yet.
        BigDecimal discount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        subTotal = subTotal.setScale(2, RoundingMode.HALF_UP);
        taxTotal = taxTotal.setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = subTotal.add(taxTotal).subtract(discount).setScale(2, RoundingMode.HALF_UP);
        BigDecimal amountPaid = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        invoice.setSubtotal(subTotal);
        invoice.setTaxAmount(taxTotal);
        invoice.setDiscountAmount(discount);
        invoice.setTotalAmount(total);
        invoice.setAmountPaid(amountPaid);
        invoice.setAmountDue(total.subtract(amountPaid));


        Invoice saved = invoiceRepo.save(invoice); // cascades line items
        auditService.record(saved.getTenant(), "INVOICE", saved.getId(), "CREATED", currentUser,
                "Created invoice " + saved.getInvoiceNumber() + " for " + total);
        evictReports(saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<InvoiceResponse> list(User currentUser) {
        if (currentUser.getTenant() == null) {
            return List.of();
        }
        return invoiceRepo.findAllInvoiceByTenantId(currentUser.getTenant().getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public InvoiceResponse show(UUID uuid, User currentUser) {
        if (currentUser.getTenant() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found");
        }
        // Look up by the public uuid AND tenant together. An invoice belonging to
        // another tenant won't match, so a user can't reach it by guessing the URL.
        // 404 (not 403) so we don't reveal that the invoice exists at all.
        Invoice invoice = invoiceRepo.findByUuidAndTenantId(uuid, currentUser.getTenant().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));
        return toResponse(invoice);
    }

    // LHDN MyInvois validated-invoice portal. Real submissions call the MyInvois
    // API; the public share URL (encoded into the QR) is built off this host.
    private static final String MYINVOIS_PORTAL = "https://myinvois.hasil.gov.my";

    /**
     * Queue an invoice for submission to LHDN MyInvois.
     *
     * <p>This is deliberately ASYNCHRONOUS, because MyInvois itself is: you submit a
     * document and LHDN validates it later, then issues the UUID + long id. So this
     * method only marks the invoice PENDING and publishes a command; EInvoiceConsumer
     * performs the submission and flips it to VALIDATED or REJECTED.
     *
     * <p>The API therefore returns PENDING, not VALIDATED — the frontend polls until the
     * badge changes.
     *
     * <p>TODO(integration): the consumer still SIMULATES the round-trip. Replacing the
     * simulation with a real UBL 2.1 build + signed POST to the MyInvois API is now a
     * change to the consumer alone; nothing here moves.
     */
    @Transactional
    public InvoiceResponse submitEInvoice(UUID uuid, User currentUser) {
        if (currentUser.getTenant() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found");
        }
        // Tenant-scoped lookup (IDOR guard): another tenant's invoice isn't found.
        Invoice invoice = invoiceRepo.findByUuidAndTenantId(uuid, currentUser.getTenant().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));

        if (invoice.getStatus() == InvoiceStatus.VOID) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A void invoice can't be submitted to MyInvois");
        }
        EInvoiceStatus current = invoice.getEinvoiceStatus();
        if (current == EInvoiceStatus.PENDING || current == EInvoiceStatus.VALIDATED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This invoice has already been submitted to MyInvois");
        }

        invoice.setEinvoiceStatus(EInvoiceStatus.PENDING);
        invoice.setEinvoiceSubmittedAt(LocalDateTime.now());
        invoice.setEinvoiceRejectionReason(null);   // clear any previous rejection on resubmit

        Invoice saved = invoiceRepo.save(invoice);
        auditService.record(saved.getTenant(), "INVOICE", saved.getId(), "EINVOICE_SUBMITTED", currentUser,
                "Queued " + saved.getInvoiceNumber() + " for MyInvois submission");

        // Relayed to Kafka after this transaction commits — so the consumer can never see
        // an invoice that is not yet PENDING in the database.
        events.publishEvent(EInvoiceSubmissionRequested.of(
                saved.getTenant().getId(), saved.getId(), saved.getUuid()));

        return toResponse(saved);
    }

    // --- lifecycle: send / void / duplicate / overdue ----------------------

    /**
     * Send an invoice to its client (US-012). Ensures a payment-link token,
     * flips DRAFT -> SENT (re-send keeps the current status), stamps sentAt, and
     * emails the client a PDF + pay link. Already-settled or void invoices are
     * rejected. Email delivery is best-effort — the state transition still
     * commits if the mail server is unreachable (dev/Mailpit).
     */
    @Transactional
    public InvoiceResponse send(UUID uuid, User currentUser) {
        Invoice invoice = requireInvoice(uuid, currentUser);

        if (invoice.getStatus() == InvoiceStatus.VOID) {
            throw conflict("A void invoice can't be sent");
        }
        if (invoice.getStatus() == InvoiceStatus.PAID || invoice.getStatus() == InvoiceStatus.REFUNDED) {
            throw conflict("This invoice is already settled");
        }

        if (invoice.getPaymentLinkToken() == null) {
            invoice.setPaymentLinkToken(newToken());
            invoice.setPaymentLinkExpiresAt(LocalDateTime.now().plusDays(30));
        }
        if (invoice.getStatus() == InvoiceStatus.DRAFT) {
            invoice.setStatus(InvoiceStatus.SENT);
        }
        if (invoice.getSentAt() == null) {
            invoice.setSentAt(LocalDateTime.now());
        }

        Invoice saved = invoiceRepo.save(invoice);
        auditService.record(saved.getTenant(), "INVOICE", saved.getId(), "SENT", currentUser,
                "Sent " + saved.getInvoiceNumber() + " to " + clientEmail(saved));
        notificationService.notify(saved.getTenant(), "INVOICE_SENT",
                "Invoice sent: " + saved.getInvoiceNumber(),
                saved.getInvoiceNumber() + " was sent to " + clientEmail(saved) + ".",
                invoiceLink(saved));

        // Delivery is now a consumer's job: DomainEventRelay forwards this to Kafka once
        // the transaction commits, and InvoiceEmailConsumer renders the PDF and sends the
        // mail — with retries and a dead-letter topic. The old inline emailInvoice() call
        // had to swallow its own failures to avoid breaking the send; now a failure is
        // simply retried instead of silently dropped.
        events.publishEvent(InvoiceSentEvent.of(
                saved.getTenant().getId(),
                saved.getId(),
                saved.getUuid(),
                saved.getInvoiceNumber(),
                clientEmail(saved)));

        evictReports(saved);   // status DRAFT -> SENT moves outstanding/aging figures
        return toResponse(saved);
    }

    /** Void an invoice (US-014). A settled invoice can't be voided; refund it instead. */
    @Transactional
    public InvoiceResponse voidInvoice(UUID uuid, User currentUser) {
        Invoice invoice = requireInvoice(uuid, currentUser);

        if (invoice.getStatus() == InvoiceStatus.PAID || invoice.getStatus() == InvoiceStatus.REFUNDED) {
            throw conflict("A settled invoice can't be voided — refund it instead");
        }
        if (invoice.getStatus() == InvoiceStatus.VOID) {
            throw conflict("Invoice is already void");
        }

        invoice.setStatus(InvoiceStatus.VOID);
        Invoice saved = invoiceRepo.save(invoice);
        auditService.record(saved.getTenant(), "INVOICE", saved.getId(), "VOIDED", currentUser,
                "Voided " + saved.getInvoiceNumber());
        evictReports(saved);   // a voided invoice drops out of every report total
        return toResponse(saved);
    }

    /**
     * Duplicate an invoice as a fresh DRAFT (US-016). Copies the client, currency,
     * notes and line items; resets status, payments, tokens, e-invoice fields and
     * dates. The new invoice number is the source number suffixed with -COPY
     * (deduped if that already exists).
     */
    @Transactional
    public InvoiceResponse duplicate(UUID uuid, User currentUser) {
        Invoice src = requireInvoice(uuid, currentUser);

        LocalDate issue = LocalDate.now();
        long termDays = ChronoUnit.DAYS.between(src.getIssueDate(), src.getDueDate());
        LocalDate due = issue.plusDays(Math.max(0, termDays));

        Invoice copy = Invoice.builder()
                .tenant(src.getTenant())
                .client(src.getClient())
                .createdBy(currentUser)
                .invoiceNumber(nextCopyNumber(src.getInvoiceNumber()))
                .status(InvoiceStatus.DRAFT)
                .currency(src.getCurrency())
                .subtotal(src.getSubtotal())
                .taxAmount(src.getTaxAmount())
                .discountAmount(src.getDiscountAmount())
                .totalAmount(src.getTotalAmount())
                .amountPaid(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .amountDue(src.getTotalAmount())
                .issueDate(issue)
                .dueDate(due)
                .notes(src.getNotes())
                .internalNotes(src.getInternalNotes())
                .build();

        for (InvoiceLineItem li : src.getLineItems()) {
            InvoiceLineItem item = new InvoiceLineItem();
            item.setDescription(li.getDescription());
            item.setQuantity(li.getQuantity());
            item.setUnitPrice(li.getUnitPrice());
            item.setTaxRate(li.getTaxRate());
            item.setTaxAmount(li.getTaxAmount());
            item.setLineTotal(li.getLineTotal());
            item.setSortOrder(li.getSortOrder());
            copy.addLineItem(item);
        }

        Invoice saved = invoiceRepo.save(copy);
        auditService.record(saved.getTenant(), "INVOICE", saved.getId(), "DUPLICATED", currentUser,
                "Duplicated " + src.getInvoiceNumber() + " -> " + saved.getInvoiceNumber());
        evictReports(saved);   // new DRAFT copy; harmless even though a draft isn't in totals yet
        return toResponse(saved);
    }

    /**
     * System sweep (called by the scheduled job): flip unpaid SENT / PARTIALLY_PAID
     * invoices whose due date has passed to OVERDUE. Runs across all tenants.
     * Returns how many were updated.
     */
    @Transactional
    public int markOverdueInvoices() {
        List<Invoice> due = invoiceRepo.findByStatusInAndDueDateBefore(
                List.of(InvoiceStatus.SENT, InvoiceStatus.PARTIALLY_PAID), LocalDate.now());
        int count = 0;
        for (Invoice inv : due) {
            if (nz(inv.getAmountDue()).signum() > 0) {
                inv.setStatus(InvoiceStatus.OVERDUE);
                invoiceRepo.save(inv);
                auditService.record(inv.getTenant(), "INVOICE", inv.getId(), "OVERDUE", null,
                        "Marked overdue (due " + inv.getDueDate() + ")");
                notificationService.notify(inv.getTenant(), "INVOICE_OVERDUE",
                        "Invoice overdue: " + inv.getInvoiceNumber(),
                        inv.getInvoiceNumber() + " is past due (" + inv.getDueDate() + ") with "
                                + inv.getCurrency() + " " + nz(inv.getAmountDue()) + " outstanding.",
                        invoiceLink(inv));
                // Runs on the cron thread, across every tenant — evict each affected one.
                // Flipping to OVERDUE changes both totalOverdue and the aging buckets.
                evictReports(inv);
                count++;
            }
        }
        if (count > 0) {
            log.info("Overdue sweep: marked {} invoice(s) OVERDUE", count);
        }
        return count;
    }

    /** Render the invoice to a PDF (tenant-scoped). */
    @Transactional(readOnly = true)
    public byte[] pdf(UUID uuid, User currentUser) {
        Invoice invoice = requireInvoice(uuid, currentUser);
        // Touch line items so they're loaded before the template renders (LAZY).
        invoice.getLineItems().size();
        return pdfService.render(invoice);
    }

    /** Audit trail for one invoice (tenant-scoped), newest first. */
    @Transactional(readOnly = true)
    public List<AuditLogResponse> auditTrail(UUID uuid, User currentUser) {
        Invoice invoice = requireInvoice(uuid, currentUser);
        return auditService.forEntity("INVOICE", invoice.getId());
    }

    /**
     * Public, unauthenticated invoice view resolved by its payment-link token
     * (US-020). 404 if the token is unknown, 410 if the link has expired.
     */
    @Transactional(readOnly = true)
    public PublicInvoiceResponse publicView(String token) {
        Invoice invoice = invoiceRepo.findByPaymentLinkToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));
        if (invoice.getPaymentLinkExpiresAt() != null
                && invoice.getPaymentLinkExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "This payment link has expired");
        }
        boolean payable = invoice.getStatus() != InvoiceStatus.VOID
                && invoice.getStatus() != InvoiceStatus.PAID
                && nz(invoice.getAmountDue()).signum() > 0;

        List<PublicInvoiceResponse.Line> lines = invoice.getLineItems().stream()
                .map(li -> PublicInvoiceResponse.Line.builder()
                        .description(li.getDescription())
                        .quantity(li.getQuantity())
                        .unitPrice(li.getUnitPrice())
                        .lineTotal(li.getLineTotal())
                        .build())
                .toList();

        return PublicInvoiceResponse.builder()
                .invoiceNumber(invoice.getInvoiceNumber())
                .organizationName(invoice.getTenant() != null ? invoice.getTenant().getName() : null)
                .clientName(invoice.getClient() != null ? invoice.getClient().getName() : null)
                .currency(invoice.getCurrency())
                .totalAmount(invoice.getTotalAmount())
                .amountPaid(invoice.getAmountPaid())
                .amountDue(invoice.getAmountDue())
                .issueDate(invoice.getIssueDate())
                .dueDate(invoice.getDueDate())
                .status(invoice.getStatus())
                .payable(payable)
                .lineItems(lines)
                .build();
    }

    // --- helpers -----------------------------------------------------------

    private Invoice requireInvoice(UUID uuid, User user) {
        if (user.getTenant() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found");
        }
        return invoiceRepo.findByUuidAndTenantId(uuid, user.getTenant().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    /** Unguessable token for the public payment link. */
    private static String newToken() {
        return (UUID.randomUUID().toString() + UUID.randomUUID().toString()).replace("-", "");
    }

    private String nextCopyNumber(String original) {
        String base = original + "-COPY";
        String candidate = base;
        int n = 2;
        while (invoiceRepo.existsByInvoiceNumber(candidate)) {
            candidate = base + "-" + n++;
        }
        return candidate;
    }

    private static String clientEmail(Invoice inv) {
        return inv.getClient() != null && inv.getClient().getEmail() != null
                ? inv.getClient().getEmail() : "(no email)";
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }


    /** Map an Invoice entity to the flat API DTO (no tenant / proxy leakage). */
    private InvoiceResponse toResponse(Invoice inv) {
        return InvoiceResponse.builder()
                .id(inv.getUuid())   // expose the public uuid as the id, never the bigint
                .invoiceNumber(inv.getInvoiceNumber())
                .client(toClientSummary(inv.getClient()))
                .createdById(inv.getCreatedBy() != null ? inv.getCreatedBy().getId() : null)
                .createdByName(inv.getCreatedBy() != null ? inv.getCreatedBy().getFullName() : null)
                .status(inv.getStatus())
                .currency(inv.getCurrency())
                .subtotal(inv.getSubtotal())
                .taxAmount(inv.getTaxAmount())
                .discountAmount(inv.getDiscountAmount())
                .totalAmount(inv.getTotalAmount())
                .amountPaid(inv.getAmountPaid())
                .amountDue(inv.getAmountDue())
                .issueDate(inv.getIssueDate())
                .dueDate(inv.getDueDate())
                .notes(inv.getNotes())
                .internalNotes(inv.getInternalNotes())
                .paymentLinkToken(inv.getPaymentLinkToken())
                .paymentLinkExpiresAt(inv.getPaymentLinkExpiresAt())
                .sentAt(inv.getSentAt())
                .paidAt(inv.getPaidAt())
                .einvoiceStatus(inv.getEinvoiceStatus())
                .einvoiceType(inv.getEinvoiceType())
                .myinvoisUuid(inv.getMyinvoisUuid())
                .myinvoisLongId(inv.getMyinvoisLongId())
                .einvoiceValidationUrl(inv.getEinvoiceValidationUrl())
                .einvoiceSubmittedAt(inv.getEinvoiceSubmittedAt())
                .einvoiceValidatedAt(inv.getEinvoiceValidatedAt())
                .einvoiceRejectionReason(inv.getEinvoiceRejectionReason())
                .createdAt(inv.getCreatedAt())
                .updatedAt(inv.getUpdatedAt())
                .lineItems(inv.getLineItems().stream()
                        .map(this::toLineItemResponse)
                        .toList())
                .build();
    }

    private InvoiceResponse.ClientSummary toClientSummary(Client c) {
        if (c == null) return null;
        return InvoiceResponse.ClientSummary.builder()
                .id(c.getId())
                .name(c.getName())
                .email(c.getEmail())
                .phone(c.getPhone())
                .currency(c.getCurrency())
                .build();
    }

    private InvoiceResponse.LineItemResponse toLineItemResponse(InvoiceLineItem li) {
        return InvoiceResponse.LineItemResponse.builder()
                .id(li.getId())
                .description(li.getDescription())
                .quantity(li.getQuantity())
                .unitPrice(li.getUnitPrice())
                .taxRate(li.getTaxRate())
                .taxAmount(li.getTaxAmount())
                .lineTotal(li.getLineTotal())
                .sortOrder(li.getSortOrder())
                .build();
    }

}
