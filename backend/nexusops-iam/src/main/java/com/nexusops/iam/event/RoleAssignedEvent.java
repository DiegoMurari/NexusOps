package com.nexusops.iam.event;

import com.nexusops.shared.event.DomainEvent;

public class RoleAssignedEvent extends DomainEvent {

    private final String userId;
    private final String userEmail;
    private final String roleName;
    private final String tenantId;
    private final String assignedBy;

    public RoleAssignedEvent(String aggregateId, long aggregateVersion, String userId, String userEmail, String roleName, String tenantId, String assignedBy) {
        super(aggregateId, aggregateVersion);
        this.userId = userId;
        this.userEmail = userEmail;
        this.roleName = roleName;
        this.tenantId = tenantId;
        this.assignedBy = assignedBy;
    }

    public String getUserId() {
        return userId;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public String getRoleName() {
        return roleName;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getAssignedBy() {
        return assignedBy;
    }
}