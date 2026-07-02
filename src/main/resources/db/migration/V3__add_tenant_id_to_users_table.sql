-- Link each user to the tenant (organization) they belong to.
--
-- Nullable on purpose: platform SUPER_ADMIN users are not scoped to a single
-- tenant. Every other user should have a tenant_id.
ALTER TABLE users
    ADD COLUMN tenant_id INTEGER REFERENCES tenants(id) ON DELETE CASCADE;

-- Tenant-scoped queries always filter by tenant_id, so index it.
CREATE INDEX idx_users_tenant_id ON users(tenant_id);
