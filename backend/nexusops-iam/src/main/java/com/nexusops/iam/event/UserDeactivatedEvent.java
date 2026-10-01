package com.nexusops.iam.event;

import com.nexusops.shared.event.DomainEvent;

public class UserDeactivatedEvent extends DomainEvent {

    private final String email;
    private final String tenantId;
    private final String deactivatedBy;

    public UserDeactivatedEvent(String aggregateId, long aggregateVersion, String email, String tenantId, String deactivatedBy) {
        super(aggregateId, aggregateVersion);
        this.email = email;
        this.tenantId = tenantId;
        this.deactivatedBy = deactivatedBy;
    }

    public String getEmail() {
        return email;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getDeactivatedBy() {
        return deactivatedBy;
    }
}