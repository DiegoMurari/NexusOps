-- NexusOps SLA Module - Initial Schema
-- Version: 1.0.0
-- Description: Create SLA schema and tables

CREATE SCHEMA IF NOT EXISTS sla;

-- SLA Definitions table
CREATE TABLE sla.sla_definitions (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    tenant_id VARCHAR(36) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    applies_to_type VARCHAR(50),
    applies_to_category VARCHAR(100),
    applies_to_priority VARCHAR(50),
    applies_to_customer_tier VARCHAR(50),
    response_time_minutes INTEGER,
    resolution_time_minutes INTEGER,
    business_calendar_id VARCHAR(36),
    pause_on_hold BOOLEAN NOT NULL DEFAULT TRUE,
    stop_on_first_response BOOLEAN NOT NULL DEFAULT TRUE,
    breach_warning_percentage_80 BOOLEAN NOT NULL DEFAULT TRUE,
    breach_warning_percentage_90 BOOLEAN NOT NULL DEFAULT TRUE,
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by VARCHAR(36),
    updated_by VARCHAR(36)
);

CREATE INDEX idx_sla_definitions_tenant ON sla.sla_definitions(tenant_id);
CREATE INDEX idx_sla_definitions_active ON sla.sla_definitions(active);
CREATE INDEX idx_sla_definitions_tenant_name ON sla.sla_definitions(tenant_id, name);

-- Business Calendars table
CREATE TABLE sla.business_calendars (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    tenant_id VARCHAR(36) NOT NULL,
    timezone VARCHAR(50) NOT NULL DEFAULT 'UTC',
    default_calendar BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by VARCHAR(36),
    updated_by VARCHAR(36),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_business_calendars_tenant ON sla.business_calendars(tenant_id);
CREATE INDEX idx_business_calendars_default ON sla.business_calendars(default_calendar);

-- Business Hours table
CREATE TABLE sla.business_hours (
    calendar_id VARCHAR(36) NOT NULL REFERENCES sla.business_calendars(id) ON DELETE CASCADE,
    day_of_week VARCHAR(20) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    working_day BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (calendar_id, day_of_week)
);

-- Holidays table
CREATE TABLE sla.holidays (
    calendar_id VARCHAR(36) NOT NULL REFERENCES sla.business_calendars(id) ON DELETE CASCADE,
    holiday_date DATE NOT NULL,
    PRIMARY KEY (calendar_id, holiday_date)
);

-- Calendar Exceptions table
CREATE TABLE sla.calendar_exceptions (
    calendar_id VARCHAR(36) NOT NULL REFERENCES sla.business_calendars(id) ON DELETE CASCADE,
    exception_date DATE NOT NULL,
    start_time TIME,
    end_time TIME,
    working_day BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (calendar_id, exception_date)
);

-- Escalation Rules table
CREATE TABLE sla.escalation_rules (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    tenant_id VARCHAR(36) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    trigger_type VARCHAR(20) NOT NULL,
    trigger_value INTEGER,
    trigger_percentage INTEGER,
    sla_definition_id VARCHAR(36),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by VARCHAR(36),
    updated_by VARCHAR(36),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_escalation_rules_tenant ON sla.escalation_rules(tenant_id);
CREATE INDEX idx_escalation_rules_active ON sla.escalation_rules(active);
CREATE INDEX idx_escalation_rules_sla ON sla.escalation_rules(sla_definition_id);

-- Escalation Actions table
CREATE TABLE sla.escalation_actions (
    rule_id VARCHAR(36) NOT NULL REFERENCES sla.escalation_rules(id) ON DELETE CASCADE,
    action_type VARCHAR(20) NOT NULL,
    target_user_id VARCHAR(36),
    target_group_id VARCHAR(36),
    webhook_url VARCHAR(500),
    template_key VARCHAR(100),
    delay_minutes INTEGER NOT NULL DEFAULT 0,
    repeat_interval_minutes INTEGER,
    max_repeats INTEGER,
    PRIMARY KEY (rule_id, action_type, target_user_id, target_group_id, webhook_url)
);

-- SLA Breaches table
CREATE TABLE sla.sla_breaches (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    ticket_id VARCHAR(36) NOT NULL,
    sla_definition_id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(36) NOT NULL,
    breach_type VARCHAR(20) NOT NULL,
    breach_time TIMESTAMP WITH TIME ZONE NOT NULL,
    acknowledged BOOLEAN NOT NULL DEFAULT FALSE,
    acknowledged_by VARCHAR(36),
    acknowledged_at TIMESTAMP WITH TIME ZONE,
    escalated BOOLEAN NOT NULL DEFAULT FALSE,
    escalated_at TIMESTAMP WITH TIME ZONE,
    resolved BOOLEAN NOT NULL DEFAULT FALSE,
    resolved_at TIMESTAMP WITH TIME ZONE,
    response_time_minutes INTEGER,
    resolution_time_minutes INTEGER,
    breach_percentage INTEGER,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_sla_breaches_ticket ON sla.sla_breaches(ticket_id);
CREATE INDEX idx_sla_breaches_tenant ON sla.sla_breaches(tenant_id);
CREATE INDEX idx_sla_breaches_status ON sla.sla_breaches(acknowledged, escalated, resolved);
CREATE INDEX idx_sla_breaches_breach_time ON sla.sla_breaches(breach_time);

-- Insert default business calendar for system tenant
INSERT INTO sla.business_calendars (id, name, description, tenant_id, timezone, default_calendar, created_at, updated_at, version)
VALUES ('00000000-0000-0000-0000-000000000001', 'Default Business Calendar', 'Standard 9-5 business hours, Mon-Fri',
        '00000000-0000-0000-0000-000000000000', 'UTC', true, NOW(), NOW(), 0)
ON CONFLICT (id) DO NOTHING;

-- Insert default business hours
INSERT INTO sla.business_hours (calendar_id, day_of_week, start_time, end_time, working_day) VALUES
('00000000-0000-0000-0000-000000000001', 'MONDAY', '09:00:00', '17:00:00', true),
('00000000-0000-0000-0000-000000000001', 'TUESDAY', '09:00:00', '17:00:00', true),
('00000000-0000-0000-0000-000000000001', 'WEDNESDAY', '09:00:00', '17:00:00', true),
('00000000-0000-0000-0000-000000000001', 'THURSDAY', '09:00:00', '17:00:00', true),
('00000000-0000-0000-0000-000000000001', 'FRIDAY', '09:00:00', '17:00:00', true),
('00000000-0000-0000-0000-000000000001', 'SATURDAY', '00:00:00', '00:00:00', false),
('00000000-0000-0000-0000-000000000001', 'SUNDAY', '00:00:00', '00:00:00', false)
ON CONFLICT DO NOTHING;
