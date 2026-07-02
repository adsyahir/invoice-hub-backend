package com.adsyahir.invoice_hub_backend.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
public class TeamMemberResponse {
    private Long id;
    private String fullName;
    private String email;
    private String role;        // "TENANT_ADMIN", "ACCOUNTANT", ...
}
