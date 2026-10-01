package com.nexusops.iam.event;

/**
 * Published by the authentication flow for every login attempt against an existing account, so other
 * modules (audit) can react without iam depending on them. Never carries credentials.
 */
public record LoginAttemptedEvent(String tenantId, String username, boolean success, String reason) {
}
