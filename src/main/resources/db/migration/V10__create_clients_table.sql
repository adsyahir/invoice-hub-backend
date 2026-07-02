-- Clients (customers) a tenant bills. Tenant-scoped via tenant_id.
CREATE TABLE clients (
    id                  SERIAL PRIMARY KEY,                          -- internal key: FKs & joins
    uuid                UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE, -- public handle: URLs & API
    tenant_id           INTEGER NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name                VARCHAR(255) NOT NULL,           -- "Name" (company or individual)
    email               VARCHAR(255) NOT NULL,           -- "Email" (primary billing email)
    phone               VARCHAR(50),                     -- "Phone"
    tax_id              VARCHAR(100),                    -- "Tax ID" (SST/GST/VAT number)
    address_line1       VARCHAR(255),                    -- "Street address"
    city                VARCHAR(100),                    -- "City"
    state               VARCHAR(100),                    -- "State"
    country             VARCHAR(100),                    -- "Country"
    currency            CHAR(3) NOT NULL DEFAULT 'MYR',  -- "Currency" (ISO 4217)
    payment_terms_days  INTEGER NOT NULL DEFAULT 30,     -- "Payment terms"
    created_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Tenant-scoped queries always filter by tenant_id, so index it.
CREATE INDEX idx_clients_tenant_id ON clients(tenant_id);
