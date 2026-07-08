package com.adsyahir.invoice_hub_backend.controller;

import com.adsyahir.invoice_hub_backend.dto.request.PublicPaymentRequest;
import com.adsyahir.invoice_hub_backend.service.InvoiceService;
import com.adsyahir.invoice_hub_backend.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Unauthenticated payer-facing endpoints, resolved by an invoice's payment-link
 * token. Whitelisted in SecurityConfig (/public/**). No tenant context — the
 * token itself is the capability.
 */
@RestController
@RequestMapping("/public/invoices")
public class PublicInvoiceController {

    private final InvoiceService invoiceService;
    private final PaymentService paymentService;

    public PublicInvoiceController(InvoiceService invoiceService, PaymentService paymentService) {
        this.invoiceService = invoiceService;
        this.paymentService = paymentService;
    }

    /** Resolve the tokenized link to a minimal public invoice view. */
    @GetMapping("/{token}")
    public ResponseEntity<?> view(@PathVariable String token) {
        return ResponseEntity.ok(invoiceService.publicView(token));
    }

    /** Pay the invoice in full via the public link (simulated gateway). */
    @PostMapping("/{token}/pay")
    public ResponseEntity<?> pay(@PathVariable String token,
                                 @Valid @RequestBody PublicPaymentRequest request) {
        return ResponseEntity.ok(paymentService.payViaLink(token, request.getMethod()));
    }
}
