-- NexusOps IAM Module - Insert Default Admin User
-- Version: 1.0.1
-- Description: Insert admin user for initial access
-- Note: the default tenant row itself is seeded by platform's migration
-- (platform.tenants is the canonical Tenant table); iam.users.tenant_id
-- carries no FK constraint, so it just references that tenant's id by value.

-- Insert admin user
-- Password: admin123456 (BCrypt hash with cost 12)
INSERT INTO iam.users (id, email, password_hash, first_name, last_name, status, tenant_id, email_verified, password_changed_at, created_at, updated_at, version)
VALUES
    ('00000000-0000-0000-0000-000000000002', 'admin@nexusops.com', '$2a$12$P4mbPapFduqapjC6Sa2ZF.N0ZPLwF/zFhwmTVPKI/bgPyJTeztWzW', 'Admin', 'User', 'ACTIVE', '00000000-0000-0000-0000-000000000000', true, NOW(), NOW(), NOW(), 0)
ON CONFLICT (email) DO NOTHING;

-- Assign SUPER_ADMIN role to admin user
INSERT INTO iam.user_roles (user_id, role)
VALUES ('00000000-0000-0000-0000-000000000002', 'SUPER_ADMIN')
ON CONFLICT (user_id, role) DO NOTHING;