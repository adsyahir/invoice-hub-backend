package com.adsyahir.invoice_hub_backend.dto.request;

import com.adsyahir.invoice_hub_backend.enums.TenantStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Payload for PATCH /admin/tenants/{uuid}/status (super-admin only). */
@Data
public class UpdateTenantStatusRequest {

    @NotNull(message = "Status is required")
    private TenantStatus status;   // ACTIVE | SUSPENDED | CANCELLED
}
