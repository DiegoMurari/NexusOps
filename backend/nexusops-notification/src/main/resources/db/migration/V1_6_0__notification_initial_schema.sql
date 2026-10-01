-- NexusOps Notification Module - Initial Schema
-- Version: 1.0.0
-- Description: Create Notification schema and tables

CREATE SCHEMA IF NOT EXISTS notification;

-- Notifications table
CREATE TABLE notification.notifications (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(36) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    template_key VARCHAR(100),
    subject VARCHAR(500),
    content TEXT,
    content_html TEXT,
    variables JSONB,
    priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMP WITH TIME ZONE,
    sent_at TIMESTAMP WITH TIME ZONE,
    failed_at TIMESTAMP WITH TIME ZONE,
    failure_reason TEXT,
    retry_count INTEGER NOT NULL DEFAULT 0,
    max_retries INTEGER NOT NULL DEFAULT 3,
    correlation_id VARCHAR(36),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_notifications_recipient ON notification.notifications(recipient_id);
CREATE INDEX idx_notifications_tenant ON notification.notifications(tenant_id);
CREATE INDEX idx_notifications_status ON notification.notifications(status);
CREATE INDEX idx_notifications_priority ON notification.notifications(priority);
CREATE INDEX idx_notifications_created ON notification.notifications(created_at);

-- Templates table
CREATE TABLE notification.templates (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid(),
    template_key VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    tenant_id VARCHAR(36),
    channel VARCHAR(20) NOT NULL,
    subject_template VARCHAR(500),
    content_template TEXT NOT NULL,
    content_html_template TEXT,
    variables_schema JSONB,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by VARCHAR(36),
    updated_by VARCHAR(36),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_templates_key ON notification.templates(template_key);
CREATE INDEX idx_templates_tenant ON notification.templates(tenant_id);
CREATE INDEX idx_templates_channel ON notification.templates(channel);

-- Subscriptions table
CREATE TABLE notification.subscriptions (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(36) NOT NULL,
    quiet_hours_start VARCHAR(5),
    quiet_hours_end VARCHAR(5),
    timezone VARCHAR(50) DEFAULT 'UTC',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_subscriptions_user ON notification.subscriptions(user_id);
CREATE INDEX idx_subscriptions_tenant ON notification.subscriptions(tenant_id);

-- Subscription Channels junction table
CREATE TABLE notification.subscription_channels (
    subscription_id VARCHAR(36) NOT NULL REFERENCES notification.subscriptions(id) ON DELETE CASCADE,
    channel VARCHAR(20) NOT NULL,
    PRIMARY KEY (subscription_id, channel)
);

-- Subscription Categories junction table
CREATE TABLE notification.subscription_categories (
    subscription_id VARCHAR(36) NOT NULL REFERENCES notification.subscriptions(id) ON DELETE CASCADE,
    category VARCHAR(100) NOT NULL,
    PRIMARY KEY (subscription_id, category)
);

-- Insert default templates
INSERT INTO notification.templates (template_key, name, channel, subject_template, content_template, content_html_template, active) VALUES
('ticket.created', 'Ticket Created', 'EMAIL', 'New Ticket: {{ticketNumber}} - {{title}}', 'A new ticket has been created.\n\nTicket: {{ticketNumber}}\nTitle: {{title}}\nPriority: {{priority}}\n\nView ticket: {{ticketUrl}}', '<p>A new ticket has been created.</p><p><strong>Ticket:</strong> {{ticketNumber}}<br><strong>Title:</strong> {{title}}<br><strong>Priority:</strong> {{priority}}</p><p><a href="{{ticketUrl}}">View ticket</a></p>', true),
('ticket.assigned', 'Ticket Assigned', 'EMAIL', 'Ticket Assigned: {{ticketNumber}}', 'You have been assigned to ticket {{ticketNumber}}.\n\nTitle: {{title}}\nPriority: {{priority}}\n\nView ticket: {{ticketUrl}}', '<p>You have been assigned to ticket <strong>{{ticketNumber}}</strong>.</p><p><strong>Title:</strong> {{title}}<br><strong>Priority:</strong> {{priority}}</p><p><a href="{{ticketUrl}}">View ticket</a></p>', true),
('ticket.comment', 'New Comment on Ticket', 'EMAIL', 'New Comment on {{ticketNumber}}', 'A new comment has been added to ticket {{ticketNumber}}.\n\nComment: {{comment}}\n\nView ticket: {{ticketUrl}}', '<p>A new comment has been added to ticket <strong>{{ticketNumber}}</strong>.</p><p>{{comment}}</p><p><a href="{{ticketUrl}}">View ticket</a></p>', true),
('sla.breach.warning', 'SLA Breach Warning', 'EMAIL', 'SLA Breach Warning: {{ticketNumber}}', 'SLA breach is imminent for ticket {{ticketNumber}}.\n\nType: {{breachType}}\nPercentage: {{percentage}}%\nTime remaining: {{remainingMinutes}} minutes', '<p>SLA breach is imminent for ticket <strong>{{ticketNumber}}</strong>.</p><p><strong>Type:</strong> {{breachType}}<br><strong>Percentage:</strong> {{percentage}}%<br><strong>Time remaining:</strong> {{remainingMinutes}} minutes</p>', true),
('sla.breached', 'SLA Breached', 'EMAIL', 'SLA Breached: {{ticketNumber}}', 'SLA has been breached for ticket {{ticketNumber}}.\n\nType: {{breachType}}\n\nView ticket: {{ticketUrl}}', '<p>SLA has been breached for ticket <strong>{{ticketNumber}}</strong>.</p><p><strong>Type:</strong> {{breachType}}</p><p><a href="{{ticketUrl}}">View ticket</a></p>', true),
('user.mentioned', 'You were mentioned', 'EMAIL', 'You were mentioned in {{entityType}} {{entityNumber}', 'You were mentioned by {{authorName}} in {{entityType}} {{entityNumber}}.\n\nComment: {{comment}}\n\nView: {{entityUrl}}', '<p>You were mentioned by <strong>{{authorName}}</strong> in <strong>{{entityType}} {{entityNumber}}</strong>.</p><p>{{comment}}</p><p><a href="{{entityUrl}}">View</a></p>', true)

ON CONFLICT (template_key) DO NOTHING;