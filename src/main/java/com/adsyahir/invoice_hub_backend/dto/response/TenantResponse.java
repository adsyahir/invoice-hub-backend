package com.adsyahir.invoice_hub_backend.dto.response;

import com.adsyahir.invoice_hub_backend.enums.TenantPlan;
import com.adsyahir.invoice_hub_backend.enums.TenantStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class TenantResponse {

    private Long id;
    private UUID uuid;
    private String name;
    private String slug;

    private TenantPlan plan;          // serialized as "FREE", ...
    private TenantStatus status;      // serialized as "ACTIVE", ...

    // Organization-profile fields (V21)
    private String billingEmail;
    private String defaultCurrency;
    private String taxId;

    private Integer maxUsers;
    private Integer maxInvoicesPerMonth;

    private LocalDateTime createdAt;  // serializes to ISO-8601 string
    private LocalDateTime updatedAt;
}
