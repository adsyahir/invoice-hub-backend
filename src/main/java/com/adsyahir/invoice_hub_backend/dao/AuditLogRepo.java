package com.adsyahir.invoice_hub_backend.dao;

import com.adsyahir.invoice_hub_backend.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepo extends JpaRepository<AuditLog, Long> {

    // Per-entity trail, newest first (e.g. the audit tab on an invoice).
    List<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, Long entityId);

    // Tenant-scoped feed, newest first.
    List<AuditLog> findByTenantIdOrderByCreatedAtDesc(Long tenantId);
}
