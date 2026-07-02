package com.adsyahir.invoice_hub_backend.dto;

import java.util.List;

/** A role plus its assigned permissions, for the RBAC management API. */
public record RoleDto(Long id, String name, String description, List<PermissionDto> permissions) {
}
