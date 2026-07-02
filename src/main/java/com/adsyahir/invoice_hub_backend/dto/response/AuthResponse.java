package com.adsyahir.invoice_hub_backend.dto.response;

import com.adsyahir.invoice_hub_backend.enums.TenantPlan;
import com.adsyahir.invoice_hub_backend.enums.TenantStatus;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

/**
 * Auth payload returned raw (no envelope) by register / login / accept-invite:
 * token + user + tenant + permissions at the top level.
 */
@Getter
@Builder
public class AuthResponse {

    private String token;
    private UserSummary user;
    private TenantSummary tenant;
    private List<String> permissions;

    @Getter
    @Builder
    public static class UserSummary {
        private UUID uuid;          // public handle (not the internal bigint id)
        private String fullName;
        private String email;
        private String role;        // role name, e.g. "TENANT_ADMIN"
    }

    @Getter
    @Builder
    public static class TenantSummary {
        private UUID uuid;          // public handle
        private String name;
        private String slug;
        private TenantPlan plan;
        private TenantStatus status;
    }
}
