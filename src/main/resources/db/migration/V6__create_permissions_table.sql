-- Granular, domain-scoped permissions (e.g. 'invoice:write').
-- Seed data is loaded by RbacSeeder at application startup, not here.
CREATE TABLE permissions (
    id          SERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(255)
);
