package com.adsyahir.invoice_hub_backend.service;

import com.adsyahir.invoice_hub_backend.dao.AuditLogRepo;
import com.adsyahir.invoice_hub_backend.dto.response.AuditLogResponse;
import com.adsyahir.invoice_hub_backend.model.AuditLog;
import com.adsyahir.invoice_hub_backend.model.Tenant;
import com.adsyahir.invoice_hub_backend.model.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Writes append-only audit rows for significant actions (create, send, void,
 * payment, refund, ...) and reads them back for the UI. A single choke point so
 * every action logs the same way and the JSONB summary is always valid JSON.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AuditLogRepo auditLogRepo;

    public AuditService(AuditLogRepo auditLogRepo) {
        this.auditLogRepo = auditLogRepo;
    }

    /**
     * Record one audit event. {@code performedBy} may be null for system actions
     * (e.g. the overdue @Scheduled job). The summary is stored as a JSON string
     * in new_value. Audit writes must never break the business action, so a
     * failure here is logged and swallowed. Runs in its own transaction
     * (REQUIRES_NEW) so an audit write can never mark the caller's transaction
     * rollback-only and break the business action.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Tenant tenant, String entityType, Long entityId, String action,
                       User performedBy, String summary) {
        try {
            AuditLog entry = AuditLog.builder()
                    .tenant(tenant)
                    .entityType(entityType)
                    .entityId(entityId)
                    .action(action)
                    .performedBy(performedBy)
                    .newValue(summary != null ? MAPPER.writeValueAsString(summary) : null)
                    .build();
            auditLogRepo.save(entry);
        } catch (Exception e) {
            log.warn("Failed to write audit log ({} {} #{}): {}", action, entityType, entityId, e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> forEntity(String entityType, Long entityId) {
        return auditLogRepo.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private AuditLogResponse toResponse(AuditLog a) {
        return AuditLogResponse.builder()
                .id(a.getId())
                .entityType(a.getEntityType())
                .action(a.getAction())
                .performedByName(a.getPerformedBy() != null ? a.getPerformedBy().getFullName() : "System")
                .summary(unwrapJson(a.getNewValue()))
                .createdAt(a.getCreatedAt())
                .build();
    }

    /** new_value is a JSON string; unwrap it back to plain text for the UI. */
    private String unwrapJson(String json) {
        if (json == null) return null;
        try {
            return MAPPER.readValue(json, String.class);
        } catch (Exception e) {
            return json;
        }
    }
}
