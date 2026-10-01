package com.nexusops.shared.notify;

import java.util.Map;

/**
 * Cross-module notification port. Lives in shared-kernel (not inside a single
 * consumer module) so any module can depend on it as a provider without an
 * inverted dependency edge - e.g. nexusops-notification implements this without
 * needing to depend on nexusops-sla, which only consumes it.
 *
 * tenantId is an explicit parameter (not read from SecurityUtils) because
 * callers like SLA's EscalationService run on a @Scheduled background thread
 * with no request-bound SecurityContext/Authentication to read a tenant from.
 */
public interface NotificationService {

    void sendNotification(String tenantId, String userId, String templateKey, Map<String, Object> variables);
}
