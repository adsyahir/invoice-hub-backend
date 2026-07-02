-- Organization-profile fields edited on the Settings page (name/slug already exist).
-- default_currency is the DEFAULT for NEW invoices only — existing invoices keep
-- their own currency (each invoice stores its currency at creation time).
ALTER TABLE tenants
    ADD COLUMN billing_email    VARCHAR(255),
    ADD COLUMN default_currency CHAR(3) NOT NULL DEFAULT 'MYR',   -- ISO 4217
    ADD COLUMN tax_id           VARCHAR(100);
