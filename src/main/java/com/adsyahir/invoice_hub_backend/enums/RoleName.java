package com.adsyahir.invoice_hub_backend.enums;

/**
 * Canonical role names. This is a type-safe set of constants for referencing
 * roles in code (seeder, default assignment, checks) — it does NOT replace the
 * {@code roles} table or the permission-based RBAC. {@link com.adsyahir.invoice_hub_backend.model.Role}
 * rows are still the source of truth; their {@code name} matches one of these.
 */
public enum RoleName {
    SUPER_ADMIN,
    TENANT_ADMIN,
    ACCOUNTANT,
    VIEWER
}
