-- NexusOps Platform Module - Initial Schema
-- Version: 1.0.0
-- Description: Create Platform schema and tables

CREATE SCHEMA IF NOT EXISTS platform;

-- Tenants table (extended from IAM)
CREATE TABLE platform.tenants (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    name VARCHAR(255) NOT NULL,
    domain VARCHAR(255) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    subscription_tier VARCHAR(50) DEFAULT 'FREE',
    subscription_expires TIMESTAMP WITH TIME ZONE,
    settings JSONB NOT NULL DEFAULT '{}',
    max_users INTEGER NOT NULL DEFAULT 100,
    max_assets INTEGER NOT NULL DEFAULT 1000,
    contact_email VARCHAR(255),
    contact_name VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by VARCHAR(36),
    updated_by VARCHAR(36),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_tenants_domain ON platform.tenants(domain);
CREATE INDEX idx_tenants_status ON platform.tenants(status);

-- Insert default tenant (referenced by value, no FK, from iam/sla/ticketing seed data)
INSERT INTO platform.tenants (id, name, domain, status, subscription_tier, settings, max_users, max_assets, created_at, updated_at, version)
VALUES
    ('00000000-0000-0000-0000-000000000000', 'NexusOps Default', 'nexusops.local', 'ACTIVE', 'ENTERPRISE', '{}', 1000, 10000, NOW(), NOW(), 0)
ON CONFLICT (id) DO NOTHING;

-- Feature Flags table
CREATE TABLE platform.feature_flags (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    key VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    rollout_percentage INTEGER NOT NULL DEFAULT 0,
    targeting_rules JSONB,
    variants JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by VARCHAR(36),
    updated_by VARCHAR(36),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_feature_flags_key ON platform.feature_flags(key);
CREATE INDEX idx_feature_flags_enabled ON platform.feature_flags(enabled);

-- System Settings table
CREATE TABLE platform.system_settings (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    setting_key VARCHAR(100) NOT NULL UNIQUE,
    value JSONB,
    value_type VARCHAR(20) NOT NULL DEFAULT 'STRING',
    description VARCHAR(1000),
    is_public BOOLEAN NOT NULL DEFAULT FALSE,
    validation_schema JSONB,
    category VARCHAR(50),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by VARCHAR(36),
    updated_by VARCHAR(36),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_system_settings_key ON platform.system_settings(setting_key);
CREATE INDEX idx_system_settings_public ON platform.system_settings(is_public);
CREATE INDEX idx_system_settings_category ON platform.system_settings(category);

-- Audit Logs table (partitioned by month)
CREATE TABLE platform.audit_logs (
    id VARCHAR(36) NOT NULL DEFAULT gen_random_uuid()::text,
    event_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(36),
    aggregate_type VARCHAR(100),
    tenant_id VARCHAR(36),
    user_id VARCHAR(36),
    resource_type VARCHAR(100),
    resource_id VARCHAR(36),
    action VARCHAR(50),
    payload JSONB,
    previous_state JSONB,
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    correlation_id VARCHAR(36),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

CREATE INDEX idx_audit_logs_event_id ON platform.audit_logs(event_id);
CREATE INDEX idx_audit_logs_tenant ON platform.audit_logs(tenant_id);
CREATE INDEX idx_audit_logs_user ON platform.audit_logs(user_id);
CREATE INDEX idx_audit_logs_resource ON platform.audit_logs(resource_type, resource_id);
CREATE INDEX idx_audit_logs_timestamp ON platform.audit_logs(created_at);

-- Default partition catches all rows regardless of created_at, so inserts
-- never fail while a proper monthly-partition-rollover job is not in place.
CREATE TABLE platform.audit_logs_default PARTITION OF platform.audit_logs DEFAULT;

-- Insert default system settings
INSERT INTO platform.system_settings (setting_key, value, value_type, description, is_public, category) VALUES
('app.name', '"NexusOps"', 'STRING', 'Application name', true, 'GENERAL'),
('app.version', '"1.0.0"', 'STRING', 'Application version', true, 'GENERAL'),
('app.maintenance_mode', 'false', 'BOOLEAN', 'Maintenance mode flag', false, 'GENERAL'),
('security.password.min_length', '12', 'NUMBER', 'Minimum password length', false, 'SECURITY'),
('security.password.max_age_days', '90', 'NUMBER', 'Maximum password age in days', false, 'SECURITY'),
('security.session.max_concurrent', '5', 'NUMBER', 'Maximum concurrent sessions per user', false, 'SECURITY'),
('security.session.idle_timeout_minutes', '30', 'NUMBER', 'Session idle timeout in minutes', false, 'SECURITY'),
('security.mfa.required', 'false', 'BOOLEAN', 'Require MFA for all users', false, 'SECURITY'),
('email.smtp.host', '"localhost"', 'STRING', 'SMTP server host', false, 'EMAIL'),
('email.smtp.port', '1025', 'NUMBER', 'SMTP server port', false, 'EMAIL'),
('email.from_address', '"noreply@nexusops.com"', 'STRING', 'Default from email address', false, 'EMAIL'),
('file.upload.max_size_mb', '50', 'NUMBER', 'Maximum file upload size in MB', false, 'FILE_UPLOAD'),
('file.upload.allowed_types', '["image/*", "application/pdf", "text/*"]', 'JSON', 'Allowed file types for upload', false, 'FILE_UPLOAD'),
('file.upload.virus_scan', 'true', 'BOOLEAN', 'Enable virus scanning for uploads', false, 'FILE_UPLOAD'),
('search.highlight_fragments', '3', 'NUMBER', 'Number of highlight fragments in search results', false, 'SEARCH'),
('search.max_results', '100', 'NUMBER', 'Maximum search results', false, 'SEARCH'),
('notification.email.retry_attempts', '3', 'NUMBER', 'Email delivery retry attempts', false, 'NOTIFICATION'),
('notification.email.retry_delay_seconds', '60', 'NUMBER', 'Email retry delay in seconds', false, 'NOTIFICATION'),
('webhook.delivery.timeout_seconds', '30', 'NUMBER', 'Webhook delivery timeout', false, 'INTEGRATION'),
('webhook.delivery.max_retries', '5', 'NUMBER', 'Maximum webhook delivery retries', false, 'INTEGRATION'),
('sla.calculation.cache_ttl_seconds', '60', 'NUMBER', 'SLA calculation cache TTL', false, 'SLA'),
('report.export.max_rows', '10000', 'NUMBER', 'Maximum rows in report export', false, 'REPORTING')

ON CONFLICT (setting_key) DO NOTHING;

-- Insert default feature flags
INSERT INTO platform.feature_flags (key, name, description, enabled, rollout_percentage) VALUES
('new_ticket_ui', 'New Ticket UI', 'Enable the new ticket management interface', false, 0),
('advanced_reporting', 'Advanced Reporting', 'Enable advanced reporting features', false, 0),
('ai_ticket_classification', 'AI Ticket Classification', 'Enable AI-powered ticket classification', false, 0),
('knowledge_base_v2', 'Knowledge Base V2', 'Enable new knowledge base version', false, 0),
('asset_discovery', 'Asset Discovery', 'Enable automated asset discovery', false, 0),
('slack_integration', 'Slack Integration', 'Enable Slack notifications and commands', false, 0),
('teams_integration', 'Teams Integration', 'Enable Microsoft Teams integration', false, 0),
('jira_sync', 'Jira Sync', 'Enable bi-directional Jira synchronization', false, 0),
('custom_workflows', 'Custom Workflows', 'Enable custom workflow engine', false, 0),
('dark_mode', 'Dark Mode', 'Enable dark mode theme', true, 100)

ON CONFLICT (key) DO NOTHING;