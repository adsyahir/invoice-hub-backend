package com.adsyahir.invoice_hub_backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/** Replaces the full permission set of a role. */
@Data
public class UpdateRolePermissionsRequest {

    @NotNull(message = "permissionIds is required")
    private List<Long> permissionIds;
}
