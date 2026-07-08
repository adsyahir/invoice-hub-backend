-- In-app notifications shown in the topbar bell. Tenant-scoped: every member of
-- the organization sees the same feed, and read state is org-level (read_at).
CREATE TABLE notifications (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id   INTEGER NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    type        VARCHAR(50) NOT NULL,      -- PAYMENT_RECEIVED, INVOICE_SENT, INVOICE_PAID, INVOICE_OVERDUE, PAYMENT_REFUNDED
    title       VARCHAR(200) NOT NULL,
    message     TEXT NOT NULL,
    link        VARCHAR(300),              -- optional frontend route, e.g. /invoices/{uuid}
    read_at     TIMESTAMP,                 -- NULL until read
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Feed is queried per-tenant, newest first; unread count filters on read_at.
CREATE INDEX idx_notifications_tenant_created ON notifications(tenant_id, created_at DESC);
CREATE INDEX idx_notifications_tenant_unread  ON notifications(tenant_id) WHERE read_at IS NULL;
