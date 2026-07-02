package com.adsyahir.invoice_hub_backend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Payload for updating the organization profile (PUT /settings). */
@Data
public class UpdateOrganizationRequest {

    @NotBlank(message = "Organization name is required")
    private String organizationName;

    @NotBlank(message = "Workspace subdomain is required")
    private String workspaceSubdomain;

    @NotBlank(message = "Billing email is required")
    @Email(message = "Billing email is invalid")
    private String billingEmail;

    @NotBlank(message = "Default currency is required")
    private String defaultCurrency;

    // Optional — registration / tax id.
    private String taxId;
}
