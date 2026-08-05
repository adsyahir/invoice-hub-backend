package com.adsyahir.invoice_hub_backend.controller;

import com.adsyahir.invoice_hub_backend.dto.request.OnboardingLinkRequest;
import com.adsyahir.invoice_hub_backend.model.UserPrincipal;
import com.adsyahir.invoice_hub_backend.service.StripeConnectService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Settings → Payouts, and the post-registration onboarding step. Gated on
 * {@code settings:manage} — only a tenant admin can complete Stripe's KYC.
 */
@RestController
@RequestMapping("/payouts")
public class PayoutsController {

    private final StripeConnectService service;

    public PayoutsController(StripeConnectService service) {
        this.service = service;
    }

    /**
     * Current Stripe Connect state. 404 when the deployment has no Stripe key — the
     * frontend reads that as "no payouts here" and hides the step.
     */
    @GetMapping("/account")
    @PreAuthorize("hasAuthority('settings:manage')")
    public ResponseEntity<?> account(@AuthenticationPrincipal UserPrincipal principal) {
        if (!service.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payouts are not enabled on this deployment");
        }
        return ResponseEntity.ok(service.get(principal.getUser()));
    }

    /** Single-use link to Stripe's hosted onboarding form. */
    @PostMapping("/onboarding-link")
    @PreAuthorize("hasAuthority('settings:manage')")
    public ResponseEntity<?> onboardingLink(@RequestBody(required = false) OnboardingLinkRequest request,
                                            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(Map.of("url", service.createOnboardingLink(principal.getUser(), request)));
    }

    /** One-time login link to the tenant's own Stripe Express dashboard. */
    @PostMapping("/dashboard-link")
    @PreAuthorize("hasAuthority('settings:manage')")
    public ResponseEntity<?> dashboardLink(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(Map.of("url", service.createDashboardLink(principal.getUser())));
    }
}
