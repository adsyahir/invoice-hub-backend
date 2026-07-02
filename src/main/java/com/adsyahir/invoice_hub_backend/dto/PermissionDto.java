package com.adsyahir.invoice_hub_backend.dto;

/** A permission as exposed by the RBAC management API. */
public record PermissionDto(Long id, String name, String description) {
}
