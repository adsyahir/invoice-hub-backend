-- Soft delete for invoices: deleted_at IS NULL means live. Physical rows stay
-- for audit/history; all reads filter them out (see @SQLRestriction on Invoice).
ALTER TABLE invoices ADD COLUMN deleted_at TIMESTAMP;

CREATE INDEX idx_invoices_active ON invoices(tenant_id) WHERE deleted_at IS NULL;
