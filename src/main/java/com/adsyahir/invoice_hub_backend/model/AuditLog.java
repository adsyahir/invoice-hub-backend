package com.adsyahir.invoice_hub_backend.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Append-only audit trail row (table created in V26). Rows are only ever
 * inserted — never updated or deleted. Polymorphic: entityType + entityId
 * identify the affected record across tables, so there is no FK on entityId.
 *
 * ip_address / user_agent columns exist in the table but are intentionally not
 * mapped here (they stay NULL) to keep the write path simple and free of INET
 * casting concerns; add them when request-context capture is wired in.
 */
@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "entity_type", nullable = false, length = 100)
    private String entityType;      // INVOICE, PAYMENT, USER, CLIENT, ...

    @Column(name = "entity_id", nullable = false)
    private Long entityId;          // internal id of the affected row

    @Column(nullable = false, length = 100)
    private String action;          // CREATED, SENT, VOIDED, RECORDED, REFUNDED, ...

    // Actor; NULL for system/automated actions (e.g. the overdue job).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performed_by")
    private User performedBy;

    // Stored in the JSONB columns. We keep a human-readable summary in new_value
    // (Hibernate stores the String as a JSON value); richer diffs can go here later.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_value")
    private String oldValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_value")
    private String newValue;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
