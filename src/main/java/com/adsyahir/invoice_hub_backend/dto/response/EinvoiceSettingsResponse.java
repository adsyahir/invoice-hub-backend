package com.adsyahir.invoice_hub_backend.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * The tenant's MyInvois settings as shown in Settings → e-Invoicing. Never
 * exposes any client secret. {@code platformReady} reflects whether InvoiceHub's
 * own intermediary credentials are configured server-side.
 */
@Getter
@Builder
public class EinvoiceSettingsResponse {
    private String environment;
    private String tin;
    private String brn;
    private String sstNumber;
    private String status;          // NOT_CONNECTED | CONNECTED | ERROR
    private boolean connected;
    private LocalDateTime lastVerifiedAt;
    private String lastError;
    private boolean platformReady;  // InvoiceHub intermediary creds present in config
}
