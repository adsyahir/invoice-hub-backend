-- Roles: named bundles of permissions. Seed data is loaded by RbacSeeder.
CREATE TABLE roles (
    id          SERIAL PRIMARY KEY,
    name        VARCHAR(50) NOT NULL UNIQUE,    -- SUPER_ADMIN | TENANT_ADMIN | ACCOUNTANT | VIEWER
    description VARCHAR(255)
);
