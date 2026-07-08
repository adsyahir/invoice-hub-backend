-- Append-only audit trail of significant actions (create, status change, send,
-- void, payment, refund, etc.). Tenant-scoped via tenant_id.
--
-- Polymorphic: entity_type + entity_id identify the affected record across tables,
-- so there is intentionally no FK on entity_id. Rows are IMMUTABLE — they are only
-- ever inserted, never updated or deleted (hence no updated_at / deleted_at).
CREATE TABLE audit_logs (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id     INTEGER NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    entity_type   VARCHAR(100) NOT NULL,                 -- INVOICE, PAYMENT, USER, CLIENT, ...
    entity_id     BIGINT NOT NULL,                       -- internal id of the affected row
    action        VARCHAR(100) NOT NULL,                 -- CREATED, STATUS_CHANGED, SENT, VOIDED, ...
    performed_by  INTEGER REFERENCES users(id),          -- actor; NULL for system/automated actions
    old_value     JSONB,                                 -- state before the change
    new_value     JSONB,                                 -- state after the change
    ip_address    INET,                                  -- request source IP
    user_agent    TEXT,                                  -- request user-agent
    created_at    TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Query patterns: per-entity trail, tenant-scoped feeds, newest-first ordering.
CREATE INDEX idx_audit_logs_entity       ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_logs_tenant       ON audit_logs(tenant_id);
CREATE INDEX idx_audit_logs_created_at   ON audit_logs(created_at DESC);
CREATE INDEX idx_audit_logs_performed_by ON audit_logs(performed_by);
