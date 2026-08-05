package com.adsyahir.invoice_hub_backend.controller;

import com.adsyahir.invoice_hub_backend.dto.request.PublicPaymentRequest;
import com.adsyahir.invoice_hub_backend.service.InvoiceService;
import com.adsyahir.invoice_hub_backend.service.PaymentService;
import com.adsyahir.invoice_hub_backend.service.StripeCheckoutService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Unauthenticated payer-facing endpoints, resolved by an invoice's payment-link
 * token. Whitelisted in SecurityConfig (/public/**). No tenant context — the
 * token itself is the capability.
 */
@RestController
@RequestMapping("/public/invoices")
public class PublicInvoiceController {

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private StripeCheckoutService checkoutService;

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

    /**
     * Start a real Stripe Checkout for this invoice and hand back the URL to redirect to.
     *
     * <p>Minted per click rather than at send time — Checkout sessions expire in 24h, and an
     * emailed invoice may be paid weeks later. The payment is NOT recorded here; only the
     * signed webhook does that.
     */
    @PostMapping("/{token}/checkout")
    public ResponseEntity<?> checkout(@PathVariable String token) {
        return ResponseEntity.ok(Map.of("url", checkoutService.createCheckoutUrl(token)));
    }
}
