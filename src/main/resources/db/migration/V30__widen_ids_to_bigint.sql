-- Widen every int4 id (and every FK pointing at one) to BIGINT.
--
-- WHY: the early tables were created with SERIAL / INTEGER (int4), while every JPA
-- entity declares `private Long id` (BIGINT). The app never surfaced this because it
-- boots with ddl-auto: update, which does not check column types, and JDBC silently
-- widens int4 into a Java Long at runtime. Hibernate's schema *validation* — which the
-- integration tests now run — rejects it:
--
--   Schema validation: wrong column type encountered in column [performed_by]
--   in table [audit_logs]; found [int4], but expecting [bigint]
--
-- The later tables (invoices, payments, audit_logs, notifications, ...) already use
-- BIGINT identity keys, so the codebase was half-migrated. This finishes the job:
-- one id type everywhere, schema and entities in agreement.
--
-- SAFETY: int4 -> int8 is a widening conversion. No data is lost and no value changes.
-- PostgreSQL rewrites the tables, which is fine at this size. FK constraints stay valid
-- because both sides are widened in the same transaction (Flyway runs this file as one).
--
-- NOTE: this file deliberately touches many tables, unlike the one-table-per-file rule
-- the create migrations follow. It is a single logical change — the id type — and
-- splitting it would leave FK pairs mismatched between migrations.

-- --- primary keys: SERIAL (int4) -> BIGINT -----------------------------------

ALTER TABLE users              ALTER COLUMN id TYPE BIGINT;
ALTER TABLE tenants            ALTER COLUMN id TYPE BIGINT;
ALTER TABLE clients            ALTER COLUMN id TYPE BIGINT;
ALTER TABLE roles              ALTER COLUMN id TYPE BIGINT;
ALTER TABLE permissions        ALTER COLUMN id TYPE BIGINT;
ALTER TABLE states             ALTER COLUMN id TYPE BIGINT;
ALTER TABLE cities             ALTER COLUMN id TYPE BIGINT;
ALTER TABLE postcodes          ALTER COLUMN id TYPE BIGINT;
ALTER TABLE refresh_tokens     ALTER COLUMN id TYPE BIGINT;
ALTER TABLE team_invitations   ALTER COLUMN id TYPE BIGINT;
ALTER TABLE invoice_line_items ALTER COLUMN id TYPE BIGINT;

-- SERIAL also created an int4 sequence capped at 2,147,483,647. Widening the column
-- alone would leave that ceiling in place, so raise the sequences too.
ALTER SEQUENCE users_id_seq              AS BIGINT;
ALTER SEQUENCE tenants_id_seq            AS BIGINT;
ALTER SEQUENCE clients_id_seq            AS BIGINT;
ALTER SEQUENCE roles_id_seq              AS BIGINT;
ALTER SEQUENCE permissions_id_seq        AS BIGINT;
ALTER SEQUENCE states_id_seq             AS BIGINT;
ALTER SEQUENCE cities_id_seq             AS BIGINT;
ALTER SEQUENCE postcodes_id_seq          AS BIGINT;
ALTER SEQUENCE refresh_tokens_id_seq     AS BIGINT;
ALTER SEQUENCE team_invitations_id_seq   AS BIGINT;
ALTER SEQUENCE invoice_line_items_id_seq AS BIGINT;

-- --- foreign keys pointing at those ids --------------------------------------

ALTER TABLE users
    ALTER COLUMN role_id   TYPE BIGINT,
    ALTER COLUMN tenant_id TYPE BIGINT;

ALTER TABLE clients
    ALTER COLUMN tenant_id   TYPE BIGINT,
    ALTER COLUMN state_id    TYPE BIGINT,
    ALTER COLUMN city_id     TYPE BIGINT,
    ALTER COLUMN postcode_id TYPE BIGINT;

ALTER TABLE invoices
    ALTER COLUMN tenant_id  TYPE BIGINT,
    ALTER COLUMN client_id  TYPE BIGINT,
    ALTER COLUMN created_by TYPE BIGINT;

ALTER TABLE payments
    ALTER COLUMN tenant_id   TYPE BIGINT,
    ALTER COLUMN recorded_by TYPE BIGINT;

ALTER TABLE audit_logs
    ALTER COLUMN tenant_id    TYPE BIGINT,
    ALTER COLUMN performed_by TYPE BIGINT;

ALTER TABLE notifications
    ALTER COLUMN tenant_id TYPE BIGINT;

ALTER TABLE tenant_einvoice_settings
    ALTER COLUMN tenant_id TYPE BIGINT;

ALTER TABLE password_reset_tokens
    ALTER COLUMN user_id TYPE BIGINT;

ALTER TABLE refresh_tokens
    ALTER COLUMN user_id TYPE BIGINT;

ALTER TABLE team_invitations
    ALTER COLUMN tenant_id  TYPE BIGINT,
    ALTER COLUMN role_id    TYPE BIGINT,
    ALTER COLUMN invited_by TYPE BIGINT;

ALTER TABLE role_permissions
    ALTER COLUMN role_id       TYPE BIGINT,
    ALTER COLUMN permission_id TYPE BIGINT;

ALTER TABLE cities
    ALTER COLUMN state_id TYPE BIGINT;

ALTER TABLE postcodes
    ALTER COLUMN city_id TYPE BIGINT;
