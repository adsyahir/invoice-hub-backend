package com.adsyahir.invoice_hub_backend.dto.response;

import com.adsyahir.invoice_hub_backend.enums.TenantPlan;
import com.adsyahir.invoice_hub_backend.enums.TenantStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Platform (super-admin) view of a tenant: its profile, plan/status and derived
 * usage stats (member count + invoices raised this month). Mirrors the frontend
 * Tenant type used by the admin Tenants table.
 */
@Getter
@Builder
public class AdminTenantResponse {
    private UUID uuid;
    private String name;
    private String slug;
    private TenantPlan plan;
    private TenantStatus status;
    private String tin;              // from tenant.taxId
    private Integer maxUsers;
    private Integer maxInvoicesPerMonth;
    private long userCount;
    private long invoicesThisMonth;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
