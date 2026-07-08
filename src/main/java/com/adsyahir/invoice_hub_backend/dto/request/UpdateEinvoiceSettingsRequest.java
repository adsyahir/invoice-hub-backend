package com.adsyahir.invoice_hub_backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Payload for PUT /settings/einvoice — the tenant's MyInvois details. */
@Data
public class UpdateEinvoiceSettingsRequest {

    @NotBlank(message = "Environment is required")
    @Pattern(regexp = "SANDBOX|PRODUCTION", message = "Environment must be SANDBOX or PRODUCTION")
    private String environment;

    // TIN is optional to save (a draft), but required before verifying.
    @Size(max = 30, message = "TIN is too long")
    private String tin;

    @Size(max = 30, message = "BRN is too long")
    private String brn;

    @Size(max = 30, message = "SST number is too long")
    private String sstNumber;
}
