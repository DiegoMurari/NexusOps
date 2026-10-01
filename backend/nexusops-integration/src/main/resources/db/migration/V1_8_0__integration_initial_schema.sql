-- NexusOps Integration Module - Initial Schema
-- Identifiers are VARCHAR(36) (entities use String ids); user references hold the principal name (e-mail).
-- No OAuth tokens/credentials are persisted: connectors only hold non-secret configuration.

CREATE SCHEMA IF NOT EXISTS integration;

CREATE TABLE integration.webhooks (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    target_url VARCHAR(500) NOT NULL,
    secret VARCHAR(255),
    tenant_id VARCHAR(36) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    retry_policy JSONB,
    timeout_seconds INTEGER NOT NULL DEFAULT 30,
    headers JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_webhooks_tenant ON integration.webhooks(tenant_id);
CREATE INDEX idx_webhooks_status ON integration.webhooks(status);

CREATE TABLE integration.webhook_events (
    webhook_id VARCHAR(36) NOT NULL REFERENCES integration.webhooks(id) ON DELETE CASCADE,
    event VARCHAR(100) NOT NULL,
    PRIMARY KEY (webhook_id, event)
);

CREATE TABLE integration.connectors (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(20) NOT NULL,
    tenant_id VARCHAR(36) NOT NULL,
    configuration JSONB,
    field_mapping JSONB,
    status VARCHAR(20) NOT NULL DEFAULT 'DISCONNECTED',
    last_sync_at TIMESTAMP WITH TIME ZONE,
    last_sync_status VARCHAR(20),
    sync_schedule_cron VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_connectors_tenant ON integration.connectors(tenant_id);
CREATE INDEX idx_connectors_type ON integration.connectors(type);
CREATE INDEX idx_connectors_status ON integration.connectors(status);

CREATE TABLE integration.sync_jobs (
    id VARCHAR(36) PRIMARY KEY,
    connector_id VARCHAR(36) NOT NULL REFERENCES integration.connectors(id) ON DELETE CASCADE,
    tenant_id VARCHAR(36) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    records_processed INTEGER NOT NULL DEFAULT 0,
    records_created INTEGER NOT NULL DEFAULT 0,
    records_updated INTEGER NOT NULL DEFAULT 0,
    records_failed INTEGER NOT NULL DEFAULT 0,
    conflicts_detected INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_sync_jobs_connector ON integration.sync_jobs(connector_id);
CREATE INDEX idx_sync_jobs_tenant ON integration.sync_jobs(tenant_id);
CREATE INDEX idx_sync_jobs_status ON integration.sync_jobs(status);
