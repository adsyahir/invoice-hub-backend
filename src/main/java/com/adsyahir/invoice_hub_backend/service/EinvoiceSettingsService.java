package com.adsyahir.invoice_hub_backend.service;

import com.adsyahir.invoice_hub_backend.dao.TenantEinvoiceSettingsRepo;
import com.adsyahir.invoice_hub_backend.dto.request.UpdateEinvoiceSettingsRequest;
import com.adsyahir.invoice_hub_backend.dto.response.EinvoiceSettingsResponse;
import com.adsyahir.invoice_hub_backend.model.TenantEinvoiceSettings;
import com.adsyahir.invoice_hub_backend.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

/**
 * Manages a tenant's MyInvois e-invoicing settings (Settings → e-Invoicing).
 *
 * Intermediary model: the tenant only supplies its TIN/BRN/SST here; InvoiceHub's
 * own intermediary credentials come from config ({@code myinvois.client-id}).
 *
 * NOTE: {@link #verify} currently SIMULATES the LHDN connection check (mirrors the
 * simulated {@code InvoiceService.submitEInvoice}). When the real MyInvois client
 * lands, replace the body of verify() with: fetch intermediary OAuth token ->
 * call Validate-Taxpayer-TIN on-behalf-of -> set CONNECTED / ERROR from the result.
 */
@Service
public class EinvoiceSettingsService {

    private final TenantEinvoiceSettingsRepo repo;
    private final AuditService auditService;
    private final NotificationService notificationService;

    // InvoiceHub's own intermediary client id (empty when not configured yet).
    @Value("${myinvois.client-id:}")
    private String platformClientId;

    public EinvoiceSettingsService(TenantEinvoiceSettingsRepo repo,
                                   AuditService auditService,
                                   NotificationService notificationService) {
        this.repo = repo;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public EinvoiceSettingsResponse get(User currentUser) {
        requireTenant(currentUser);
        return repo.findByTenantId(currentUser.getTenant().getId())
                .map(this::toResponse)
                .orElseGet(this::defaultResponse);
    }

    /**
     * Save the tenant's details. Any edit resets the connection to NOT_CONNECTED —
     * the tenant must re-verify after changing their TIN/BRN/SST/environment.
     */
    @Transactional
    public EinvoiceSettingsResponse update(UpdateEinvoiceSettingsRequest req, User currentUser) {
        requireTenant(currentUser);
        TenantEinvoiceSettings s = repo.findByTenantId(currentUser.getTenant().getId())
                .orElseGet(() -> TenantEinvoiceSettings.builder()
                        .tenant(currentUser.getTenant())
                        .build());

        s.setEnvironment(req.getEnvironment());
        s.setTin(trimToNull(req.getTin()));
        s.setBrn(trimToNull(req.getBrn()));
        s.setSstNumber(trimToNull(req.getSstNumber()));
        s.setStatus("NOT_CONNECTED");
        s.setLastError(null);
        s.setLastVerifiedAt(null);

        TenantEinvoiceSettings saved = repo.save(s);
        auditService.record(currentUser.getTenant(), "EINVOICE_SETTINGS", saved.getId(), "UPDATED",
                currentUser, "Updated MyInvois settings (" + saved.getEnvironment() + ")");
        return toResponse(saved);
    }

    /**
     * Verify the MyInvois connection. SIMULATED for now: succeeds if a TIN is set
     * (and InvoiceHub's intermediary creds are configured), otherwise records an
     * error. Replace with the real token + TIN-validation call later.
     */
    @Transactional
    public EinvoiceSettingsResponse verify(User currentUser) {
        requireTenant(currentUser);
        TenantEinvoiceSettings s = repo.findByTenantId(currentUser.getTenant().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Save your MyInvois details before connecting"));

        if (s.getTin() == null || s.getTin().isBlank()) {
            s.setStatus("ERROR");
            s.setLastError("A TIN is required to connect to MyInvois.");
            return toResponse(repo.save(s));
        }
        if (!platformConfigured()) {
            s.setStatus("ERROR");
            s.setLastError("MyInvois is not configured on this InvoiceHub instance yet "
                    + "(missing intermediary credentials). This is a simulated connection.");
            // Still flip to CONNECTED so the UI flow can be exercised end-to-end in dev.
        }

        // --- simulated successful connection ---
        s.setStatus("CONNECTED");
        s.setLastVerifiedAt(LocalDateTime.now());
        s.setLastError(platformConfigured() ? null
                : "Simulated connection (no live MyInvois credentials configured).");

        TenantEinvoiceSettings saved = repo.save(s);
        auditService.record(currentUser.getTenant(), "EINVOICE_SETTINGS", saved.getId(), "CONNECTED",
                currentUser, "Connected to MyInvois (" + saved.getEnvironment() + ", TIN " + saved.getTin() + ")");
        notificationService.notify(currentUser.getTenant(), "EINVOICE_CONNECTED",
                "MyInvois connected",
                "Your workspace is connected to LHDN MyInvois (" + saved.getEnvironment() + ").",
                "/settings/einvoice");
        return toResponse(saved);
    }

    /** Disconnect: keep the details but drop the connection so it must be re-verified. */
    @Transactional
    public EinvoiceSettingsResponse disconnect(User currentUser) {
        requireTenant(currentUser);
        TenantEinvoiceSettings s = repo.findByTenantId(currentUser.getTenant().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No MyInvois settings found"));
        s.setStatus("NOT_CONNECTED");
        s.setLastVerifiedAt(null);
        s.setLastError(null);
        TenantEinvoiceSettings saved = repo.save(s);
        auditService.record(currentUser.getTenant(), "EINVOICE_SETTINGS", saved.getId(), "DISCONNECTED",
                currentUser, "Disconnected from MyInvois");
        return toResponse(saved);
    }

    // --- helpers -----------------------------------------------------------

    private boolean platformConfigured() {
        return platformClientId != null && !platformClientId.isBlank();
    }

    private void requireTenant(User user) {
        if (user.getTenant() == null || user.getTenant().getId() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found");
        }
    }

    private EinvoiceSettingsResponse defaultResponse() {
        return EinvoiceSettingsResponse.builder()
                .environment("SANDBOX")
                .status("NOT_CONNECTED")
                .connected(false)
                .platformReady(platformConfigured())
                .build();
    }

    private EinvoiceSettingsResponse toResponse(TenantEinvoiceSettings s) {
        return EinvoiceSettingsResponse.builder()
                .environment(s.getEnvironment())
                .tin(s.getTin())
                .brn(s.getBrn())
                .sstNumber(s.getSstNumber())
                .status(s.getStatus())
                .connected("CONNECTED".equals(s.getStatus()))
                .lastVerifiedAt(s.getLastVerifiedAt())
                .lastError(s.getLastError())
                .platformReady(platformConfigured())
                .build();
    }

    private static String trimToNull(String v) {
        if (v == null) return null;
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }
}
