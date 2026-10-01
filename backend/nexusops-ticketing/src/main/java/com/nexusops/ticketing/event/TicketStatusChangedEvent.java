package com.nexusops.ticketing.event;

import com.nexusops.shared.event.DomainEvent;

public class TicketStatusChangedEvent extends DomainEvent {

    private final String ticketNumber;
    private final String oldStatus;
    private final String newStatus;
    private final String changedBy;
    private final String tenantId;

    public TicketStatusChangedEvent(String aggregateId, long aggregateVersion, String ticketNumber,
                                    String oldStatus, String newStatus, String changedBy, String tenantId) {
        super(aggregateId, aggregateVersion);
        this.ticketNumber = ticketNumber;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.changedBy = changedBy;
        this.tenantId = tenantId;
    }

    public String getTicketNumber() {
        return ticketNumber;
    }

    public String getOldStatus() {
        return oldStatus;
    }

    public String getNewStatus() {
        return newStatus;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public String getTenantId() {
        return tenantId;
    }
}