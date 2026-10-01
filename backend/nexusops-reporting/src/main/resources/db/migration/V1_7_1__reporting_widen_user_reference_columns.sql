-- The authenticated principal name (an e-mail address) is stored as owner/creator reference,
-- so these columns must be wider than VARCHAR(36).
ALTER TABLE reporting.reports ALTER COLUMN owner_id TYPE VARCHAR(255);
ALTER TABLE reporting.reports ALTER COLUMN created_by TYPE VARCHAR(255);
ALTER TABLE reporting.reports ALTER COLUMN updated_by TYPE VARCHAR(255);
ALTER TABLE reporting.dashboards ALTER COLUMN owner_id TYPE VARCHAR(255);
ALTER TABLE reporting.dashboards ALTER COLUMN created_by TYPE VARCHAR(255);
ALTER TABLE reporting.dashboards ALTER COLUMN updated_by TYPE VARCHAR(255);
ALTER TABLE reporting.scheduled_reports ALTER COLUMN created_by TYPE VARCHAR(255);
ALTER TABLE reporting.scheduled_reports ALTER COLUMN updated_by TYPE VARCHAR(255);
