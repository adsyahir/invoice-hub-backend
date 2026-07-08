-- Per-organization LHDN MyInvois e-invoicing configuration (one row per tenant).
-- Intermediary model: the tenant only supplies its TIN/BRN/SST. InvoiceHub's own
-- intermediary client_id/secret live in application config (.env), NOT here, and
-- are shared across all tenants.
CREATE TABLE tenant_einvoice_settings (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id          INTEGER NOT NULL UNIQUE REFERENCES tenants(id) ON DELETE CASCADE,
    environment        VARCHAR(20) NOT NULL DEFAULT 'SANDBOX',       -- SANDBOX | PRODUCTION
    tin                VARCHAR(30),                                  -- taxpayer TIN
    brn                VARCHAR(30),                                  -- business registration no.
    sst_number         VARCHAR(30),
    status             VARCHAR(20) NOT NULL DEFAULT 'NOT_CONNECTED', -- NOT_CONNECTED | CONNECTED | ERROR
    last_verified_at   TIMESTAMP,
    last_error         TEXT,
    created_at         TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP NOT NULL DEFAULT NOW()
);
