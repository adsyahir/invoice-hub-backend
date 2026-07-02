-- Invoices. Tenant-scoped via tenant_id. Amounts are server-authoritative.
-- status references the invoice_statuses lookup table (seed 'DRAFT' before use).
CREATE TABLE invoices (
    id                       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, -- internal key: FKs & joins
    uuid                     UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,   -- public handle: URLs & API
    tenant_id                INTEGER NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    invoice_number           VARCHAR(50) NOT NULL UNIQUE,            -- e.g. INV-2026-0001
    client_id                INTEGER NOT NULL REFERENCES clients(id),
    created_by               INTEGER NOT NULL REFERENCES users(id),
    status                   VARCHAR(50) NOT NULL DEFAULT 'DRAFT',   -- InvoiceStatus enum, stored as name (EnumType.STRING)
    currency                 CHAR(3) NOT NULL,                       -- ISO 4217
    subtotal                 NUMERIC(15,2) NOT NULL,                 -- sum of line bases before tax
    tax_amount               NUMERIC(15,2) NOT NULL DEFAULT 0,
    discount_amount          NUMERIC(15,2) NOT NULL DEFAULT 0 CHECK (discount_amount >= 0),
    total_amount             NUMERIC(15,2) NOT NULL,                 -- subtotal + tax - discount
    amount_paid              NUMERIC(15,2) NOT NULL DEFAULT 0,
    amount_due               NUMERIC(15,2) NOT NULL,                 -- total_amount - amount_paid
    issue_date               DATE NOT NULL,
    due_date                 DATE NOT NULL,
    notes                    TEXT,                                   -- visible to client on PDF
    internal_notes           TEXT,                                   -- internal only
    payment_link_token       VARCHAR(255) UNIQUE,
    payment_link_expires_at  TIMESTAMP,
    sent_at                  TIMESTAMP,
    paid_at                  TIMESTAMP,
    created_at               TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMP NOT NULL DEFAULT NOW(),
    version                  INTEGER NOT NULL DEFAULT 0               -- optimistic locking
);

-- Common query patterns (see documentation 3.3).
CREATE INDEX idx_invoices_tenant_id  ON invoices(tenant_id);
CREATE INDEX idx_invoices_client_id  ON invoices(client_id);
CREATE INDEX idx_invoices_status     ON invoices(status);
CREATE INDEX idx_invoices_due_date   ON invoices(due_date);
CREATE INDEX idx_invoices_created_at ON invoices(created_at DESC);
