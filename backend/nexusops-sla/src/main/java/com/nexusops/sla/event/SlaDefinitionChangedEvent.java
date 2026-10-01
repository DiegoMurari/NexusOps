package com.nexusops.sla.event;

import com.nexusops.shared.event.DomainEvent;

public class SlaDefinitionChangedEvent extends DomainEvent {

    private final String name;
    private final String tenantId;
    private final boolean active;
    private final String changedBy;

    public SlaDefinitionChangedEvent(String aggregateId, long aggregateVersion, String name, String tenantId, boolean active, String changedBy) {
        super(aggregateId, aggregateVersion);
        this.name = name;
        this.tenantId = tenantId;
        this.active = active;
        this.changedBy = changedBy;
    }

    public String getName() {
        return name;
    }

    public String getTenantId() {
        return tenantId;
    }

    public boolean isActive() {
        return active;
    }

    public String getChangedBy() {
        return changedBy;
    }
}