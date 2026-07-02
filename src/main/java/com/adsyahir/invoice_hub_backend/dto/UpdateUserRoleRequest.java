package com.adsyahir.invoice_hub_backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Reassigns a user to a different role. */
@Data
public class UpdateUserRoleRequest {

    @NotNull(message = "roleId is required")
    private Long roleId;
}
