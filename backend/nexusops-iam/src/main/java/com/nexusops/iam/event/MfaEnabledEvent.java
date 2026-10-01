package com.nexusops.iam.event;

import com.nexusops.shared.event.DomainEvent;

public class MfaEnabledEvent extends DomainEvent {

    private final String userId;
    private final String userEmail;
    private final String tenantId;

    public MfaEnabledEvent(String aggregateId, long aggregateVersion, String userId, String userEmail, String tenantId) {
        super(aggregateId, aggregateVersion);
        this.userId = userId;
        this.userEmail = userEmail;
        this.tenantId = tenantId;
    }

    public String getUserId() {
        return userId;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public String getTenantId() {
        return tenantId;
    }
}