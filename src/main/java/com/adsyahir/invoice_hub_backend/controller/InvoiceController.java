package com.adsyahir.invoice_hub_backend.controller;

import com.adsyahir.invoice_hub_backend.dto.request.CreateInvoiceRequest;
import com.adsyahir.invoice_hub_backend.model.Invoice;
import com.adsyahir.invoice_hub_backend.model.UserPrincipal;
import com.adsyahir.invoice_hub_backend.service.InvoiceService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/invoices")
public class InvoiceController {

    private static final Logger log = LoggerFactory.getLogger(InvoiceController.class);

    @Autowired
    private InvoiceService invoiceService;

    @PostMapping("/create")
    @PreAuthorize("hasAuthority('invoice:write')")
    public ResponseEntity<?> create(@Valid @RequestBody CreateInvoiceRequest request,
                                    @AuthenticationPrincipal UserPrincipal principal) {
        log.info("Creating invoice {} for user {}", request.getInvoiceNumber(),
                principal.getUser().getEmail());

        Invoice invoice = invoiceService.createInvoice(request, principal.getUser());

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "Invoice created successfully",
                "id", invoice.getUuid(),   // public handle, not the internal bigint id
                "invoiceNumber", invoice.getInvoiceNumber(),
                "status", invoice.getStatus(),
                "subtotal", invoice.getSubtotal(),
                "taxAmount", invoice.getTaxAmount(),
                "totalAmount", invoice.getTotalAmount(),
                "amountDue", invoice.getAmountDue()
        ));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('invoice:read')")
    public ResponseEntity<?> list(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(invoiceService.list(principal.getUser()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('invoice:read')")
    public ResponseEntity<?> show(@PathVariable UUID id,
                                  @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(invoiceService.show(id, principal.getUser()));
    }

    /** Send the invoice to its client (email + pay link); DRAFT -> SENT. */
    @PostMapping("/{id}/send")
    @PreAuthorize("hasAuthority('invoice:write')")
    public ResponseEntity<?> send(@PathVariable UUID id,
                                  @AuthenticationPrincipal UserPrincipal principal) {
        log.info("Sending invoice {} for user {}", id, principal.getUser().getEmail());
        return ResponseEntity.ok(invoiceService.send(id, principal.getUser()));
    }

    /** Void / cancel the invoice. */
    @PostMapping("/{id}/void")
    @PreAuthorize("hasAuthority('invoice:void')")
    public ResponseEntity<?> voidInvoice(@PathVariable UUID id,
                                         @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(invoiceService.voidInvoice(id, principal.getUser()));
    }

    /** Duplicate the invoice as a fresh DRAFT; returns the new invoice. */
    @PostMapping("/{id}/duplicate")
    @PreAuthorize("hasAuthority('invoice:write')")
    public ResponseEntity<?> duplicate(@PathVariable UUID id,
                                       @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(invoiceService.duplicate(id, principal.getUser()));
    }

    /** Download the invoice as a PDF. */
    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAuthority('invoice:read')")
    public ResponseEntity<byte[]> pdf(@PathVariable UUID id,
                                      @AuthenticationPrincipal UserPrincipal principal) {
        byte[] pdf = invoiceService.pdf(id, principal.getUser());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"invoice-" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    /** Audit trail for the invoice (newest first). */
    @GetMapping("/{id}/audit-log")
    @PreAuthorize("hasAuthority('invoice:read')")
    public ResponseEntity<?> auditLog(@PathVariable UUID id,
                                      @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(invoiceService.auditTrail(id, principal.getUser()));
    }

    /** Submit the invoice to LHDN MyInvois; returns the updated invoice. */
    @PostMapping("/{id}/einvoice/submit")
    @PreAuthorize("hasAuthority('invoice:write')")
    public ResponseEntity<?> submitEInvoice(@PathVariable UUID id,
                                            @AuthenticationPrincipal UserPrincipal principal) {
        log.info("Submitting invoice {} to MyInvois for user {}", id, principal.getUser().getEmail());
        return ResponseEntity.ok(invoiceService.submitEInvoice(id, principal.getUser()));
    }
}
