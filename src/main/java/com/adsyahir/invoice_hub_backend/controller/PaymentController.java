package com.adsyahir.invoice_hub_backend.controller;

import com.adsyahir.invoice_hub_backend.dto.request.CreatePaymentRequest;
import com.adsyahir.invoice_hub_backend.dto.response.PaymentResponse;
import com.adsyahir.invoice_hub_backend.model.UserPrincipal;
import com.adsyahir.invoice_hub_backend.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    /** List all payments for the tenant, newest first. */
    @GetMapping
    @PreAuthorize("hasAuthority('payment:read')")
    public ResponseEntity<?> list(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(paymentService.list(principal.getUser()));
    }

    /** Payments recorded against a single invoice (by the invoice's public uuid). */
    @GetMapping("/invoice/{invoiceId}")
    @PreAuthorize("hasAuthority('payment:read')")
    public ResponseEntity<?> listForInvoice(@PathVariable UUID invoiceId,
                                            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(paymentService.listForInvoice(invoiceId, principal.getUser()));
    }

    /** Record a manual (offline) payment; recomputes the invoice's paid/due/status. */
    @PostMapping
    @PreAuthorize("hasAuthority('payment:record')")
    public ResponseEntity<?> create(@Valid @RequestBody CreatePaymentRequest request,
                                    @AuthenticationPrincipal UserPrincipal principal) {
        PaymentResponse payment = paymentService.create(request, principal.getUser());
        return ResponseEntity.status(HttpStatus.CREATED).body(payment);
    }

    /** Refund a completed payment; recomputes the invoice back to its prior state. */
    @PostMapping("/{id}/refund")
    @PreAuthorize("hasAuthority('payment:refund')")
    public ResponseEntity<?> refund(@PathVariable UUID id,
                                    @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(paymentService.refund(id, principal.getUser()));
    }
}
