-- Tenants: each row is an organization (a customer workspace) on InvoiceHub.
-- Multi-tenancy strategy: shared schema with a tenant_id discriminator
-- (see V3, which links users to a tenant).
CREATE TABLE tenants (
    id                     SERIAL PRIMARY KEY,
    name                   VARCHAR(255) NOT NULL,
    slug                   VARCHAR(100) NOT NULL UNIQUE,   -- subdomain, e.g. "novosoft"
    plan                   VARCHAR(50)  NOT NULL DEFAULT 'FREE',    -- FREE | STARTER | PROFESSIONAL | ENTERPRISE
    status                 VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | SUSPENDED | CANCELLED
    max_users              INTEGER      NOT NULL DEFAULT 3,
    max_invoices_per_month INTEGER      NOT NULL DEFAULT 10,
    created_at             TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMP    NOT NULL DEFAULT NOW()
);
