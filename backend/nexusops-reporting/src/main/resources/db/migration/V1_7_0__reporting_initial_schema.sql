-- NexusOps Reporting Module - Initial Schema
-- Version: 1.0.0
-- Description: Create Reporting schema and tables

CREATE SCHEMA IF NOT EXISTS reporting;

-- Reports table
CREATE TABLE reporting.reports (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    tenant_id VARCHAR(36) NOT NULL,
    owner_id VARCHAR(36) NOT NULL,
    report_type VARCHAR(30) NOT NULL DEFAULT 'CUSTOM',
    query TEXT,
    parameters JSONB,
    visualization_config JSONB,
    schedule_cron VARCHAR(100),
    timezone VARCHAR(50) DEFAULT 'UTC',
    public BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by VARCHAR(36),
    updated_by VARCHAR(36),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_reports_tenant ON reporting.reports(tenant_id);
CREATE INDEX idx_reports_owner ON reporting.reports(owner_id);
CREATE INDEX idx_reports_type ON reporting.reports(report_type);

-- Dashboards table
CREATE TABLE reporting.dashboards (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    tenant_id VARCHAR(36) NOT NULL,
    owner_id VARCHAR(36) NOT NULL,
    layout_config JSONB,
    public BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by VARCHAR(36),
    updated_by VARCHAR(36),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_dashboards_tenant ON reporting.dashboards(tenant_id);
CREATE INDEX idx_dashboards_owner ON reporting.dashboards(owner_id);

-- Widgets table
CREATE TABLE reporting.widgets (
    id VARCHAR(36) PRIMARY KEY,
    dashboard_id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(36) NOT NULL,
    title VARCHAR(255) NOT NULL,
    type VARCHAR(20) NOT NULL DEFAULT 'METRIC',
    query TEXT,
    parameters JSONB,
    visualization_config JSONB,
    position_x INTEGER NOT NULL DEFAULT 0,
    position_y INTEGER NOT NULL DEFAULT 0,
    width INTEGER NOT NULL DEFAULT 1,
    height INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_widgets_dashboard ON reporting.widgets(dashboard_id);
CREATE INDEX idx_widgets_tenant ON reporting.widgets(tenant_id);

-- Scheduled Reports table
CREATE TABLE reporting.scheduled_reports (
    id VARCHAR(36) PRIMARY KEY,
    report_id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(36) NOT NULL,
    name VARCHAR(255) NOT NULL,
    schedule_cron VARCHAR(100) NOT NULL,
    timezone VARCHAR(50) DEFAULT 'UTC',
    format VARCHAR(20) NOT NULL DEFAULT 'PDF',
    delivery_method VARCHAR(20) NOT NULL DEFAULT 'EMAIL',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    last_run_at TIMESTAMP WITH TIME ZONE,
    next_run_at TIMESTAMP WITH TIME ZONE,
    last_run_status VARCHAR(20),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by VARCHAR(36),
    updated_by VARCHAR(36),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_sched_reports_tenant ON reporting.scheduled_reports(tenant_id);
CREATE INDEX idx_sched_reports_report ON reporting.scheduled_reports(report_id);
CREATE INDEX idx_sched_reports_next_run ON reporting.scheduled_reports(next_run_at);

-- Scheduled Report Recipients junction table
CREATE TABLE reporting.scheduled_report_recipients (
    scheduled_report_id VARCHAR(36) NOT NULL REFERENCES reporting.scheduled_reports(id) ON DELETE CASCADE,
    recipient VARCHAR(255) NOT NULL,
    PRIMARY KEY (scheduled_report_id, recipient)
);