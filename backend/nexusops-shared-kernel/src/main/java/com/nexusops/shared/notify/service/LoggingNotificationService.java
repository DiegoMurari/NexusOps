package com.nexusops.shared.notify.service;

import com.nexusops.shared.notify.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Fallback NotificationService: logs instead of delivering. Only activates
 * when nexusops-notification (or another real provider) isn't on the
 * classpath, so callers like SLA's EscalationService always have a bean to
 * inject regardless of which modules are wired into bootstrap.
 */
@Slf4j
@Service
@ConditionalOnMissingBean(NotificationService.class)
public class LoggingNotificationService implements NotificationService {

    @Override
    public void sendNotification(String tenantId, String userId, String templateKey, Map<String, Object> variables) {
        log.info("Notification (not delivered - no notification backend wired): tenantId={}, userId={}, templateKey={}, variables={}",
            tenantId, userId, templateKey, variables);
    }
}
