-- Stripe Connect (Express) onboarding state, one connected account per tenant.
--
-- These columns CACHE what Stripe reports and are refreshed by the account.updated
-- webhook. Stripe is the source of truth: never set stripe_charges_enabled from our
-- own code. The onboarding status the API exposes is derived from these columns, not
-- stored, so nothing can drift out of step with the booleans.
--
-- No secret here. The Stripe API key and webhook signing secret are config (.env),
-- shared across tenants, like the MyInvois intermediary credentials in V29.
ALTER TABLE tenants
    ADD COLUMN stripe_account_id        VARCHAR(255) UNIQUE,   -- acct_… (null until created)
    ADD COLUMN stripe_charges_enabled   BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN stripe_payouts_enabled   BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN stripe_details_submitted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN stripe_disabled_reason   VARCHAR(255),          -- requirements.disabled_reason
    ADD COLUMN stripe_requirements      TEXT,                  -- requirements.currently_due, comma-separated
    ADD COLUMN stripe_synced_at         TIMESTAMP;             -- last time we read Stripe
