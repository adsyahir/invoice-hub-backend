-- Replace the old free-text users.role column with a foreign key to roles.
-- Roles are seeded by RbacSeeder at startup; the application assigns a role to
-- every user it creates, so role_id is NOT NULL.
ALTER TABLE users DROP COLUMN role;
ALTER TABLE users ADD COLUMN role_id INTEGER NOT NULL REFERENCES roles(id);
CREATE INDEX idx_users_role_id ON users(role_id);
