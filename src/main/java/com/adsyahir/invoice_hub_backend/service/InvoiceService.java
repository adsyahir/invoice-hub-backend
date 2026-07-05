package com.adsyahir.invoice_hub_backend.service;

import com.adsyahir.invoice_hub_backend.dao.ClientRepo;
import com.adsyahir.invoice_hub_backend.dao.InvoiceRepo;
import com.adsyahir.invoice_hub_backend.dto.request.CreateInvoiceRequest;
import com.adsyahir.invoice_hub_backend.dto.LineItemRequest;
import com.adsyahir.invoice_hub_backend.enums.EInvoiceStatus;
import com.adsyahir.invoice_hub_backend.enums.InvoiceStatus;
import com.adsyahir.invoice_hub_backend.exception.ValidationException;
import com.adsyahir.invoice_hub_backend.model.Client;
import com.adsyahir.invoice_hub_backend.model.Invoice;
import com.adsyahir.invoice_hub_backend.model.InvoiceLineItem;
import com.adsyahir.invoice_hub_backend.model.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.adsyahir.invoice_hub_backend.dto.response.InvoiceResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

@Service
public class InvoiceService {

    private final InvoiceRepo invoiceRepo;
    private final ClientRepo clientRepo;

    public InvoiceService(InvoiceRepo invoiceRepo, ClientRepo clientRepo) {
        this.invoiceRepo = invoiceRepo;
        this.clientRepo = clientRepo;
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


        return invoiceRepo.save(invoice); // cascades line items
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
     * Submit an invoice to LHDN MyInvois.
     *
     * TODO(integration): this simulates the round-trip. A real implementation
     * builds the UBL 2.1 document, POSTs it to the MyInvois API, and the invoice
     * goes PENDING first; a poll/webhook later flips it to VALIDATED (with the
     * LHDN UUID + long id) or REJECTED. Here we validate synchronously so the UI
     * can be exercised end-to-end.
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

        // --- simulate a successful LHDN validation ---
        LocalDateTime now = LocalDateTime.now();
        String lhdnUuid = UUID.randomUUID().toString().replace("-", "").toUpperCase();
        String longId = lhdnUuid + invoice.getInvoiceNumber().replaceAll("\\D", "");

        invoice.setEinvoiceStatus(EInvoiceStatus.VALIDATED);
        invoice.setMyinvoisUuid(lhdnUuid);
        invoice.setMyinvoisLongId(longId);
        invoice.setEinvoiceValidationUrl(MYINVOIS_PORTAL + "/" + lhdnUuid + "/share/" + longId);
        invoice.setEinvoiceSubmittedAt(now);
        invoice.setEinvoiceValidatedAt(now);
        invoice.setEinvoiceRejectionReason(null); // clear any previous rejection on resubmit

        return toResponse(invoiceRepo.save(invoice));
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
