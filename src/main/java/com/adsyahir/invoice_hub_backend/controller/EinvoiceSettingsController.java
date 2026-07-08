package com.adsyahir.invoice_hub_backend.controller;

import com.adsyahir.invoice_hub_backend.dto.request.UpdateEinvoiceSettingsRequest;
import com.adsyahir.invoice_hub_backend.model.UserPrincipal;
import com.adsyahir.invoice_hub_backend.service.EinvoiceSettingsService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Settings → e-Invoicing: manage a tenant's LHDN MyInvois connection.
 * Requires settings:manage (TENANT_ADMIN / SUPER_ADMIN).
 */
@RestController
@RequestMapping("/settings/einvoice")
public class EinvoiceSettingsController {

    private final EinvoiceSettingsService service;

    public EinvoiceSettingsController(EinvoiceSettingsService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('settings:manage')")
    public ResponseEntity<?> get(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(service.get(principal.getUser()));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('settings:manage')")
    public ResponseEntity<?> update(@Valid @RequestBody UpdateEinvoiceSettingsRequest request,
                                    @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(service.update(request, principal.getUser()));
    }

    /** Verify / connect to MyInvois (simulated until the real client lands). */
    @PostMapping("/verify")
    @PreAuthorize("hasAuthority('settings:manage')")
    public ResponseEntity<?> verify(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(service.verify(principal.getUser()));
    }

    @DeleteMapping
    @PreAuthorize("hasAuthority('settings:manage')")
    public ResponseEntity<?> disconnect(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(service.disconnect(principal.getUser()));
    }
}
