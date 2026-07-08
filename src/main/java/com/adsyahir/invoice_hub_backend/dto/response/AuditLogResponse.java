package com.adsyahir.invoice_hub_backend.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/** API view of one audit-trail entry (invoice audit tab / activity feed). */
@Getter
@Builder
public class AuditLogResponse {
    private Long id;
    private String entityType;
    private String action;
    private String performedByName;   // "System" for automated actions
    private String summary;
    private LocalDateTime createdAt;
}
