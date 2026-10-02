package com.nexusops.iam.event;

/**
 * Published when a user ends a session (single device or every device), so audit can record it without
 * iam depending on it. Never carries tokens.
 */
public record SessionEndedEvent(String tenantId, String username, boolean allDevices) {
}
