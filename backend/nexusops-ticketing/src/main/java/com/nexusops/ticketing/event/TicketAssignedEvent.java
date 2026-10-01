package com.nexusops.ticketing.event;

import com.nexusops.shared.event.DomainEvent;

public class TicketAssignedEvent extends DomainEvent {

    private final String ticketNumber;
    private final String oldAssigneeId;
    private final String newAssigneeId;
    private final String assignedBy;
    private final String tenantId;

    public TicketAssignedEvent(String aggregateId, long aggregateVersion, String ticketNumber,
                               String oldAssigneeId, String newAssigneeId, String assignedBy, String tenantId) {
        super(aggregateId, aggregateVersion);
        this.ticketNumber = ticketNumber;
        this.oldAssigneeId = oldAssigneeId;
        this.newAssigneeId = newAssigneeId;
        this.assignedBy = assignedBy;
        this.tenantId = tenantId;
    }

    public String getTicketNumber() {
        return ticketNumber;
    }

    public String getOldAssigneeId() {
        return oldAssigneeId;
    }

    public String getNewAssigneeId() {
        return newAssigneeId;
    }

    public String getAssignedBy() {
        return assignedBy;
    }

    public String getTenantId() {
        return tenantId;
    }
}