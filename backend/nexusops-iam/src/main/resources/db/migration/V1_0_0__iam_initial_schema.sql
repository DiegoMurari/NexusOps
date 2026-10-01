-- NexusOps IAM Module - Initial Schema
-- Version: 1.0.0
-- Description: Create IAM schema and tables

CREATE SCHEMA IF NOT EXISTS iam;

-- Permissions table (reference data)
CREATE TABLE iam.permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    permission_key VARCHAR(100) NOT NULL UNIQUE,
    resource VARCHAR(50) NOT NULL,
    action VARCHAR(50) NOT NULL,
    scope VARCHAR(20) NOT NULL,
    description VARCHAR(500),
    category VARCHAR(50),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_permissions_resource_action ON iam.permissions(resource, action);
CREATE INDEX idx_permissions_category ON iam.permissions(category);

-- Roles table
CREATE TABLE iam.roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    tenant_id VARCHAR(36) NOT NULL,
    system_role BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_roles_name_tenant UNIQUE (name, tenant_id)
);

CREATE INDEX idx_roles_tenant ON iam.roles(tenant_id);

-- Role permissions junction table
CREATE TABLE iam.role_permissions (
    role_id UUID NOT NULL REFERENCES iam.roles(id) ON DELETE CASCADE,
    permission VARCHAR(100) NOT NULL,
    PRIMARY KEY (role_id, permission)
);

-- Users table
CREATE TABLE iam.users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    phone VARCHAR(50),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    tenant_id VARCHAR(36) NOT NULL,
    avatar_url VARCHAR(500),
    last_login_at TIMESTAMP WITH TIME ZONE,
    failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMP WITH TIME ZONE,
    password_changed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    email_verification_token VARCHAR(255),
    email_verification_token_expires TIMESTAMP WITH TIME ZONE,
    mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by VARCHAR(36),
    updated_by VARCHAR(36),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_users_email ON iam.users(email);
CREATE INDEX idx_users_tenant ON iam.users(tenant_id);
CREATE INDEX idx_users_status ON iam.users(status);

-- User roles junction table
CREATE TABLE iam.user_roles (
    user_id UUID NOT NULL REFERENCES iam.users(id) ON DELETE CASCADE,
    role VARCHAR(100) NOT NULL,
    PRIMARY KEY (user_id, role)
);

-- User permissions junction table
CREATE TABLE iam.user_permissions (
    user_id UUID NOT NULL REFERENCES iam.users(id) ON DELETE CASCADE,
    permission VARCHAR(100) NOT NULL,
    PRIMARY KEY (user_id, permission)
);

-- MFA secrets table
CREATE TABLE iam.mfa_secrets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES iam.users(id) ON DELETE CASCADE,
    secret VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    backup_codes JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

-- Insert default permissions
INSERT INTO iam.permissions (permission_key, resource, action, scope, description, category) VALUES
-- User permissions
('USER:CREATE:GLOBAL', 'USER', 'CREATE', 'GLOBAL', 'Create users globally', 'USER_MANAGEMENT'),
('USER:READ:GLOBAL', 'USER', 'READ', 'GLOBAL', 'Read users globally', 'USER_MANAGEMENT'),
('USER:UPDATE:GLOBAL', 'USER', 'UPDATE', 'GLOBAL', 'Update users globally', 'USER_MANAGEMENT'),
('USER:DELETE:GLOBAL', 'USER', 'DELETE', 'GLOBAL', 'Delete users globally', 'USER_MANAGEMENT'),
('USER:CREATE:TENANT', 'USER', 'CREATE', 'TENANT', 'Create users within tenant', 'USER_MANAGEMENT'),
('USER:READ:TENANT', 'USER', 'READ', 'TENANT', 'Read users within tenant', 'USER_MANAGEMENT'),
('USER:UPDATE:TENANT', 'USER', 'UPDATE', 'TENANT', 'Update users within tenant', 'USER_MANAGEMENT'),
('USER:DELETE:TENANT', 'USER', 'DELETE', 'TENANT', 'Delete users within tenant', 'USER_MANAGEMENT'),
('USER:READ:OWN', 'USER', 'READ', 'OWN', 'Read own user profile', 'USER_MANAGEMENT'),
('USER:UPDATE:OWN', 'USER', 'UPDATE', 'OWN', 'Update own user profile', 'USER_MANAGEMENT'),

-- Role permissions
('ROLE:CREATE:TENANT', 'ROLE', 'CREATE', 'TENANT', 'Create roles within tenant', 'ROLE_MANAGEMENT'),
('ROLE:READ:TENANT', 'ROLE', 'READ', 'TENANT', 'Read roles within tenant', 'ROLE_MANAGEMENT'),
('ROLE:UPDATE:TENANT', 'ROLE', 'UPDATE', 'TENANT', 'Update roles within tenant', 'ROLE_MANAGEMENT'),
('ROLE:DELETE:TENANT', 'ROLE', 'DELETE', 'TENANT', 'Delete roles within tenant', 'ROLE_MANAGEMENT'),

-- Tenant permissions
('TENANT:CREATE:GLOBAL', 'TENANT', 'CREATE', 'GLOBAL', 'Create tenants globally', 'TENANT_MANAGEMENT'),
('TENANT:READ:GLOBAL', 'TENANT', 'READ', 'GLOBAL', 'Read tenants globally', 'TENANT_MANAGEMENT'),
('TENANT:UPDATE:GLOBAL', 'TENANT', 'UPDATE', 'GLOBAL', 'Update tenants globally', 'TENANT_MANAGEMENT'),
('TENANT:DELETE:GLOBAL', 'TENANT', 'DELETE', 'GLOBAL', 'Delete tenants globally', 'TENANT_MANAGEMENT'),

-- Permission permissions
('PERMISSION:READ:GLOBAL', 'PERMISSION', 'READ', 'GLOBAL', 'Read permissions globally', 'PERMISSION_MANAGEMENT'),

-- Ticket permissions
('TICKET:CREATE:TENANT', 'TICKET', 'CREATE', 'TENANT', 'Create tickets within tenant', 'TICKET_MANAGEMENT'),
('TICKET:READ:TENANT', 'TICKET', 'READ', 'TENANT', 'Read tickets within tenant', 'TICKET_MANAGEMENT'),
('TICKET:UPDATE:TENANT', 'TICKET', 'UPDATE', 'TENANT', 'Update tickets within tenant', 'TICKET_MANAGEMENT'),
('TICKET:DELETE:TENANT', 'TICKET', 'DELETE', 'TENANT', 'Delete tickets within tenant', 'TICKET_MANAGEMENT'),
('TICKET:READ:TEAM', 'TICKET', 'READ', 'TEAM', 'Read team tickets', 'TICKET_MANAGEMENT'),
('TICKET:UPDATE:TEAM', 'TICKET', 'UPDATE', 'TEAM', 'Update team tickets', 'TICKET_MANAGEMENT'),
('TICKET:READ:OWN', 'TICKET', 'READ', 'OWN', 'Read own tickets', 'TICKET_MANAGEMENT'),

-- Asset permissions
('ASSET:CREATE:TENANT', 'ASSET', 'CREATE', 'TENANT', 'Create assets within tenant', 'ASSET_MANAGEMENT'),
('ASSET:READ:TENANT', 'ASSET', 'READ', 'TENANT', 'Read assets within tenant', 'ASSET_MANAGEMENT'),
('ASSET:UPDATE:TENANT', 'ASSET', 'UPDATE', 'TENANT', 'Update assets within tenant', 'ASSET_MANAGEMENT'),
('ASSET:DELETE:TENANT', 'ASSET', 'DELETE', 'TENANT', 'Delete assets within tenant', 'ASSET_MANAGEMENT'),

-- Knowledge permissions
('KNOWLEDGE:CREATE:TENANT', 'KNOWLEDGE', 'CREATE', 'TENANT', 'Create knowledge articles within tenant', 'KNOWLEDGE_MANAGEMENT'),
('KNOWLEDGE:READ:TENANT', 'KNOWLEDGE', 'READ', 'TENANT', 'Read knowledge articles within tenant', 'KNOWLEDGE_MANAGEMENT'),
('KNOWLEDGE:UPDATE:TENANT', 'KNOWLEDGE', 'UPDATE', 'TENANT', 'Update knowledge articles within tenant', 'KNOWLEDGE_MANAGEMENT'),
('KNOWLEDGE:DELETE:TENANT', 'KNOWLEDGE', 'DELETE', 'TENANT', 'Delete knowledge articles within tenant', 'KNOWLEDGE_MANAGEMENT'),
('KNOWLEDGE:READ:PUBLIC', 'KNOWLEDGE', 'READ', 'GLOBAL', 'Read public knowledge articles', 'KNOWLEDGE_MANAGEMENT'),

-- Notification permissions
('NOTIFICATION:READ:OWN', 'NOTIFICATION', 'READ', 'OWN', 'Read own notifications', 'NOTIFICATION_MANAGEMENT'),
('NOTIFICATION:UPDATE:OWN', 'NOTIFICATION', 'UPDATE', 'OWN', 'Update own notifications', 'NOTIFICATION_MANAGEMENT'),

-- Reporting permissions
('REPORT:CREATE:TENANT', 'REPORT', 'CREATE', 'TENANT', 'Create reports within tenant', 'REPORTING'),
('REPORT:READ:TENANT', 'REPORT', 'READ', 'TENANT', 'Read reports within tenant', 'REPORTING'),
('REPORT:EXPORT:TENANT', 'REPORT', 'EXPORT', 'TENANT', 'Export reports within tenant', 'REPORTING'),

-- Integration permissions
('INTEGRATION:CREATE:TENANT', 'INTEGRATION', 'CREATE', 'TENANT', 'Create integrations within tenant', 'INTEGRATION_MANAGEMENT'),
('INTEGRATION:READ:TENANT', 'INTEGRATION', 'READ', 'TENANT', 'Read integrations within tenant', 'INTEGRATION_MANAGEMENT'),
('INTEGRATION:UPDATE:TENANT', 'INTEGRATION', 'UPDATE', 'TENANT', 'Update integrations within tenant', 'INTEGRATION_MANAGEMENT'),
('INTEGRATION:DELETE:TENANT', 'INTEGRATION', 'DELETE', 'TENANT', 'Delete integrations within tenant', 'INTEGRATION_MANAGEMENT'),

-- SLA permissions
('SLA:CREATE:TENANT', 'SLA', 'CREATE', 'TENANT', 'Create SLA definitions within tenant', 'SLA_MANAGEMENT'),
('SLA:READ:TENANT', 'SLA', 'READ', 'TENANT', 'Read SLA definitions within tenant', 'SLA_MANAGEMENT'),
('SLA:UPDATE:TENANT', 'SLA', 'UPDATE', 'TENANT', 'Update SLA definitions within tenant', 'SLA_MANAGEMENT'),
('SLA:DELETE:TENANT', 'SLA', 'DELETE', 'TENANT', 'Delete SLA definitions within tenant', 'SLA_MANAGEMENT'),

-- Platform permissions
('FEATURE_FLAG:READ:TENANT', 'FEATURE_FLAG', 'READ', 'TENANT', 'Read feature flags within tenant', 'PLATFORM'),
('FEATURE_FLAG:UPDATE:TENANT', 'FEATURE_FLAG', 'UPDATE', 'TENANT', 'Update feature flags within tenant', 'PLATFORM'),
('SETTING:READ:TENANT', 'SETTING', 'READ', 'TENANT', 'Read settings within tenant', 'PLATFORM'),
('SETTING:UPDATE:TENANT', 'SETTING', 'UPDATE', 'TENANT', 'Update settings within tenant', 'PLATFORM'),
('AUDIT:READ:TENANT', 'AUDIT', 'READ', 'TENANT', 'Read audit logs within tenant', 'PLATFORM')

ON CONFLICT (permission_key) DO NOTHING;

-- Insert default system roles
INSERT INTO iam.roles (id, name, description, tenant_id, system_role, created_at, updated_at, version)
VALUES
    ('00000000-0000-0000-0000-000000000001', 'SUPER_ADMIN', 'Super administrator with full access', '00000000-0000-0000-0000-000000000000', true, NOW(), NOW(), 0),
    ('00000000-0000-0000-0000-000000000002', 'ADMIN', 'Tenant administrator', '00000000-0000-0000-0000-000000000000', true, NOW(), NOW(), 0),
    ('00000000-0000-0000-0000-000000000003', 'MANAGER', 'Team manager', '00000000-0000-0000-0000-000000000000', true, NOW(), NOW(), 0),
    ('00000000-0000-0000-0000-000000000004', 'TEAM_LEAD', 'Team lead', '00000000-0000-0000-0000-000000000000', true, NOW(), NOW(), 0),
    ('00000000-0000-0000-0000-000000000005', 'AGENT', 'Support agent', '00000000-0000-0000-0000-000000000000', true, NOW(), NOW(), 0),
    ('00000000-0000-0000-0000-000000000006', 'END_USER', 'End user', '00000000-0000-0000-0000-000000000000', true, NOW(), NOW(), 0)

ON CONFLICT (name, tenant_id) DO NOTHING;

-- Assign all permissions to SUPER_ADMIN
INSERT INTO iam.role_permissions (role_id, permission)
SELECT '00000000-0000-0000-0000-000000000001', permission_key FROM iam.permissions
ON CONFLICT DO NOTHING;

-- Assign admin permissions to ADMIN role
INSERT INTO iam.role_permissions (role_id, permission)
SELECT '00000000-0000-0000-0000-000000000002', permission_key FROM iam.permissions
WHERE scope IN ('GLOBAL', 'TENANT') AND permission_key NOT LIKE 'TENANT:%'
ON CONFLICT DO NOTHING;

-- Assign manager permissions to MANAGER role
INSERT INTO iam.role_permissions (role_id, permission)
SELECT '00000000-0000-0000-0000-000000000003', permission_key FROM iam.permissions
WHERE scope IN ('TENANT', 'TEAM', 'OWN') AND resource IN ('USER', 'TICKET', 'ASSET', 'KNOWLEDGE', 'REPORT', 'NOTIFICATION')
ON CONFLICT DO NOTHING;

-- Assign agent permissions to AGENT role
INSERT INTO iam.role_permissions (role_id, permission)
SELECT '00000000-0000-0000-0000-000000000005', permission_key FROM iam.permissions
WHERE scope IN ('TENANT', 'TEAM', 'OWN') AND resource IN ('TICKET', 'ASSET', 'KNOWLEDGE', 'NOTIFICATION')
ON CONFLICT DO NOTHING;

-- Assign end user permissions to END_USER role
INSERT INTO iam.role_permissions (role_id, permission)
SELECT '00000000-0000-0000-0000-000000000006', permission_key FROM iam.permissions
WHERE scope = 'OWN' AND resource IN ('TICKET', 'KNOWLEDGE', 'NOTIFICATION')
ON CONFLICT DO NOTHING;