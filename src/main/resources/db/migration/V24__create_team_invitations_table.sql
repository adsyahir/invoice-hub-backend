-- Pending invitations for a user to join a tenant's team. A row is created when
-- an admin invites someone; the token is embedded in the emailed invitation link
-- and consumed when the invitee accepts (which then creates the users row).
CREATE TABLE team_invitations (
    id           SERIAL PRIMARY KEY,                             -- internal key: FKs & joins
    uuid         UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE, -- public handle: URLs & API
    tenant_id    INTEGER NOT NULL REFERENCES tenants(id) ON DELETE CASCADE, -- the organization
    email        VARCHAR(255) NOT NULL,                          -- invitee's email (helper.setTo)
    role_id      INTEGER NOT NULL REFERENCES roles(id),          -- role granted on acceptance
    token        VARCHAR(255) NOT NULL UNIQUE,                   -- secret in the invitation link
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',         -- PENDING | ACCEPTED | EXPIRED | REVOKED
    invited_by   INTEGER NOT NULL REFERENCES users(id),          -- inviter (for "inviterName")
    expires_at   TIMESTAMP NOT NULL,                             -- now() + expiryHours
    accepted_at  TIMESTAMP,                                      -- set when consumed; null while pending
    created_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Acceptance looks the invite up by its token, so index it (unique already does this,
-- listed here for intent). Tenant-scoped listing filters by tenant_id.
CREATE INDEX idx_team_invitations_tenant_id ON team_invitations(tenant_id);

-- Only one live (pending) invite per email per tenant; re-inviting replaces/expires the old one.
CREATE UNIQUE INDEX uq_team_invitations_pending
    ON team_invitations(tenant_id, email)
    WHERE status = 'PENDING';
