-- NexusOps IAM Module - Account Lockout + Refresh Token Revocation
-- Version: 1.0.2
-- Description: Add failed-login lockout tracking to users, and a
-- refresh_tokens table so logout/refresh can actually revoke tokens
-- (previously TODO-stubbed: no server-side revocation existed at all).

ALTER TABLE iam.users
    ADD COLUMN IF NOT EXISTS failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS locked_until TIMESTAMP WITH TIME ZONE;

CREATE TABLE IF NOT EXISTS iam.refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES iam.users(id) ON DELETE CASCADE,
    jti VARCHAR(36) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user ON iam.refresh_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_jti ON iam.refresh_tokens(jti);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_expires ON iam.refresh_tokens(expires_at);
