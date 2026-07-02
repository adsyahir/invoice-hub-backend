-- Public handle for tenants (dual-key: bigint id for FKs/joins, uuid for URLs/API).
-- DEFAULT gen_random_uuid() backfills any existing rows on add.
ALTER TABLE tenants
    ADD COLUMN uuid UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE;
