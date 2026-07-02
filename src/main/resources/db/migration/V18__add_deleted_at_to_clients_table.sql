-- Soft delete: a non-null deleted_at marks the row as deleted. Rows are never
-- physically removed; all reads filter on deleted_at IS NULL (see @SQLRestriction).
ALTER TABLE clients ADD COLUMN deleted_at TIMESTAMP;

-- Partial index keeps "live" lookups fast since every query carries the filter.
CREATE INDEX idx_clients_active ON clients(tenant_id) WHERE deleted_at IS NULL;
