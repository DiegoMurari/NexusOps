package com.nexusops.ticketing.event;

import com.nexusops.shared.event.DomainEvent;

public class TicketClosedEvent extends DomainEvent {

    private final String ticketId;
    private final String ticketNumber;
    private final String closedBy;
    private final String tenantId;

    public TicketClosedEvent(String aggregateId, long aggregateVersion, String ticketId, String ticketNumber,
                             String closedBy, String tenantId) {
        super(aggregateId, aggregateVersion);
        this.ticketId = ticketId;
        this.ticketNumber = ticketNumber;
        this.closedBy = closedBy;
        this.tenantId = tenantId;
    }

    public String getTicketId() {
        return ticketId;
    }

    public String getTicketNumber() {
        return ticketNumber;
    }

    public String getClosedBy() {
        return closedBy;
    }

    public String getTenantId() {
        return tenantId;
    }
}