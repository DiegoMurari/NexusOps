package com.nexusops.shared.audit;

import com.nexusops.shared.event.DomainEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class AuditListener {

    @EventListener
    public void handleDomainEvent(DomainEvent event) {
    }

    public void audit(String action, String entityType, String entityId, String userId, String tenantId, Object details) {
    }
}