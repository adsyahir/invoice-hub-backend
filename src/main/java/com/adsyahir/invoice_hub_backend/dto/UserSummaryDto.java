package com.adsyahir.invoice_hub_backend.dto;

import java.time.LocalDateTime;

/** A user row for the admin users table. */
public record UserSummaryDto(
        Long id,
        String fullName,
        String email,
        String role,
        String tenantName,
        LocalDateTime createdAt) {
}
