package com.adsyahir.invoice_hub_backend.controller;

import com.adsyahir.invoice_hub_backend.dto.request.CreateInvoiceRequest;
import com.adsyahir.invoice_hub_backend.dto.response.ApiResponse;
import com.adsyahir.invoice_hub_backend.model.Invoice;
import com.adsyahir.invoice_hub_backend.model.UserPrincipal;
import com.adsyahir.invoice_hub_backend.service.InvoiceService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
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
        return ResponseEntity.ok(ApiResponse.success(invoiceService.list(principal.getUser())));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('invoice:read')")
    public ResponseEntity<?> show(@PathVariable UUID id,
                                  @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(invoiceService.show(id, principal.getUser())));
    }
}
