package com.nexusops.ticketing.event;

import com.nexusops.shared.event.DomainEvent;

public class TicketResolvedEvent extends DomainEvent {

    private final String ticketId;
    private final String ticketNumber;
    private final String resolution;
    private final String resolvedBy;
    private final String tenantId;

    public TicketResolvedEvent(String aggregateId, long aggregateVersion, String ticketId, String ticketNumber,
                               String resolution, String resolvedBy, String tenantId) {
        super(aggregateId, aggregateVersion);
        this.ticketId = ticketId;
        this.ticketNumber = ticketNumber;
        this.resolution = resolution;
        this.resolvedBy = resolvedBy;
        this.tenantId = tenantId;
    }

    public String getTicketId() {
        return ticketId;
    }

    public String getTicketNumber() {
        return ticketNumber;
    }

    public String getResolution() {
        return resolution;
    }

    public String getResolvedBy() {
        return resolvedBy;
    }

    public String getTenantId() {
        return tenantId;
    }
}