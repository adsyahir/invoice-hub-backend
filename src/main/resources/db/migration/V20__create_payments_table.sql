-- Payments recorded against invoices. Tenant-scoped via tenant_id.
-- method/status are enums stored as their name (EnumType.STRING).
-- Soft delete: a non-null deleted_at marks the row as deleted.
CREATE TABLE payments (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,  -- internal key: FKs & joins
    uuid            UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,   -- public handle: URLs & API
    tenant_id       INTEGER NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    invoice_id      BIGINT NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    amount          NUMERIC(15,2) NOT NULL CHECK (amount > 0),
    currency        CHAR(3) NOT NULL,                                 -- ISO 4217
    method          VARCHAR(50) NOT NULL,                            -- PaymentMethod: CARD, BANK_TRANSFER, CASH, FPX, EWALLET
    status          VARCHAR(50) NOT NULL DEFAULT 'PENDING',          -- PaymentStatus: PENDING, COMPLETED, FAILED, REFUNDED
    gateway         VARCHAR(50),                                     -- STRIPE, BILLPLZ, MANUAL
    gateway_txn_id  VARCHAR(255),                                    -- provider transaction id
    reference       VARCHAR(255),                                    -- manual ref / receipt no.
    metadata        JSONB,                                           -- free-form provider payload
    recorded_by     INTEGER REFERENCES users(id),                    -- who logged a manual payment (nullable)
    paid_at         TIMESTAMP NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMP
);

-- Common query patterns: tenant-scoped lists, by-invoice lookups, status filters.
CREATE INDEX idx_payments_tenant_id ON payments(tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_payments_invoice_id ON payments(invoice_id);
CREATE INDEX idx_payments_status    ON payments(status);
