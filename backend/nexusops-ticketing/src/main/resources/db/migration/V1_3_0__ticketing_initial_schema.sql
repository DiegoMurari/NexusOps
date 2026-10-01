-- NexusOps Ticketing Module - Initial Schema
-- Version: 1.0.0
-- Description: Create Ticketing schema and tables

CREATE SCHEMA IF NOT EXISTS ticketing;

-- Categories table
CREATE TABLE ticketing.categories (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    tenant_id VARCHAR(36) NOT NULL,
    parent_id VARCHAR(36),
    icon VARCHAR(100),
    color VARCHAR(7),
    sort_order INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    sla_definition_id VARCHAR(36),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by VARCHAR(36),
    updated_by VARCHAR(36),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_categories_tenant ON ticketing.categories(tenant_id);
CREATE INDEX idx_categories_parent ON ticketing.categories(parent_id);
CREATE INDEX idx_categories_active ON ticketing.categories(active);

-- Tickets table (base table for inheritance)
CREATE TABLE ticketing.tickets (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    ticket_number VARCHAR(50) NOT NULL UNIQUE,
    title VARCHAR(500) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    urgency VARCHAR(20),
    impact VARCHAR(20),
    tenant_id VARCHAR(36) NOT NULL,
    category_id VARCHAR(36),
    assignee_id VARCHAR(36),
    reporter_id VARCHAR(36) NOT NULL,
    group_id VARCHAR(36),
    sla_definition_id VARCHAR(36),
    response_due_at TIMESTAMP WITH TIME ZONE,
    resolution_due_at TIMESTAMP WITH TIME ZONE,
    first_response_at TIMESTAMP WITH TIME ZONE,
    resolved_at TIMESTAMP WITH TIME ZONE,
    closed_at TIMESTAMP WITH TIME ZONE,
    ci_reference VARCHAR(100),
    tags JSONB,
    custom_fields JSONB,
    ticket_type VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by VARCHAR(36),
    updated_by VARCHAR(36),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_tickets_tenant ON ticketing.tickets(tenant_id);
CREATE INDEX idx_tickets_number ON ticketing.tickets(ticket_number);
CREATE INDEX idx_tickets_status ON ticketing.tickets(status);
CREATE INDEX idx_tickets_priority ON ticketing.tickets(priority);
CREATE INDEX idx_tickets_assignee ON ticketing.tickets(assignee_id);
CREATE INDEX idx_tickets_reporter ON ticketing.tickets(reporter_id);
CREATE INDEX idx_tickets_category ON ticketing.tickets(category_id);
CREATE INDEX idx_tickets_created ON ticketing.tickets(created_at);

-- Incidents table
CREATE TABLE ticketing.incidents (
    id VARCHAR(36) PRIMARY KEY REFERENCES ticketing.tickets(id) ON DELETE CASCADE,
    root_cause TEXT,
    known_error TEXT,
    workaround TEXT,
    related_incident_id VARCHAR(36)
);

-- Problems table
CREATE TABLE ticketing.problems (
    id VARCHAR(36) PRIMARY KEY REFERENCES ticketing.tickets(id) ON DELETE CASCADE,
    risk_level VARCHAR(20),
    implementation_plan TEXT,
    backout_plan TEXT,
    change_window_start TIMESTAMP WITH TIME ZONE,
    change_window_end TIMESTAMP WITH TIME ZONE,
    approval_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    cab_date TIMESTAMP WITH TIME ZONE,
    cab_notes TEXT
);

-- Changes table
CREATE TABLE ticketing.changes (
    id VARCHAR(36) PRIMARY KEY REFERENCES ticketing.tickets(id) ON DELETE CASCADE,
    risk_level VARCHAR(20),
    implementation_plan TEXT,
    backout_plan TEXT,
    change_window_start TIMESTAMP WITH TIME ZONE,
    change_window_end TIMESTAMP WITH TIME ZONE,
    approval_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    cab_date TIMESTAMP WITH TIME ZONE,
    cab_notes TEXT
);

-- Comments table
CREATE TABLE ticketing.comments (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    ticket_id VARCHAR(36) NOT NULL REFERENCES ticketing.tickets(id) ON DELETE CASCADE,
    tenant_id VARCHAR(36) NOT NULL,
    author_id VARCHAR(36) NOT NULL,
    content TEXT NOT NULL,
    content_html TEXT,
    public_comment BOOLEAN NOT NULL DEFAULT FALSE,
    mentions JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_comments_ticket ON ticketing.comments(ticket_id);
CREATE INDEX idx_comments_author ON ticketing.comments(author_id);
CREATE INDEX idx_comments_created ON ticketing.comments(created_at);

-- Attachments table
CREATE TABLE ticketing.attachments (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    ticket_id VARCHAR(36) NOT NULL REFERENCES ticketing.tickets(id) ON DELETE CASCADE,
    tenant_id VARCHAR(36) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL,
    mime_type VARCHAR(100),
    s3_key VARCHAR(500) NOT NULL,
    s3_bucket VARCHAR(100),
    checksum VARCHAR(64),
    uploader_id VARCHAR(36) NOT NULL,
    virus_scan_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    virus_scan_result TEXT,
    thumbnail_s3_key VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_attachments_ticket ON ticketing.attachments(ticket_id);
CREATE INDEX idx_attachments_uploader ON ticketing.attachments(uploader_id);
CREATE INDEX idx_attachments_scan ON ticketing.attachments(virus_scan_status);

-- Time Entries table
CREATE TABLE ticketing.time_entries (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    ticket_id VARCHAR(36) NOT NULL REFERENCES ticketing.tickets(id) ON DELETE CASCADE,
    tenant_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    description TEXT,
    start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    end_time TIMESTAMP WITH TIME ZONE,
    duration_minutes INTEGER,
    billable BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_time_entries_ticket ON ticketing.time_entries(ticket_id);
CREATE INDEX idx_time_entries_user ON ticketing.time_entries(user_id);
CREATE INDEX idx_time_entries_date ON ticketing.time_entries(start_time);

-- Insert default categories
INSERT INTO ticketing.categories (id, name, description, tenant_id, parent_id, icon, color, sort_order, active, created_at, updated_at, version)
VALUES
('00000000-0000-0000-0000-000000000101', 'Hardware', 'Hardware related issues',
 '00000000-0000-0000-0000-000000000000', NULL, 'laptop', '#3B82F6', 1, true, NOW(), NOW(), 0),
('00000000-0000-0000-0000-000000000102', 'Software', 'Software related issues',
 '00000000-0000-0000-0000-000000000000', NULL, 'monitor', '#10B981', 2, true, NOW(), NOW(), 0),
('00000000-0000-0000-0000-000000000103', 'Network', 'Network connectivity issues',
 '00000000-0000-0000-0000-000000000000', NULL, 'wifi', '#F59E0B', 3, true, NOW(), NOW(), 0),
('00000000-0000-0000-0000-000000000104', 'Access', 'Access and permission requests',
 '00000000-0000-0000-0000-000000000000', NULL, 'key', '#EF4444', 4, true, NOW(), NOW(), 0),
('00000000-0000-0000-0000-000000000105', 'Service Request', 'General service requests',
 '00000000-0000-0000-0000-000000000000', NULL, 'clipboard', '#8B5CF6', 5, true, NOW(), NOW(), 0)

ON CONFLICT (id) DO NOTHING;
