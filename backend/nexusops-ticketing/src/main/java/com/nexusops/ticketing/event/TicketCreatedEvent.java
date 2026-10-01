package com.nexusops.ticketing.event;

import com.nexusops.shared.event.DomainEvent;

public class TicketCreatedEvent extends DomainEvent {

    private final String ticketNumber;
    private final String title;
    private final String ticketType;
    private final String priority;
    private final String categoryId;
    private final String assigneeId;
    private final String reporterId;
    private final String tenantId;

    public TicketCreatedEvent(String aggregateId, long aggregateVersion, String ticketNumber, String title,
                              String ticketType, String priority, String categoryId,
                              String assigneeId, String reporterId, String tenantId) {
        super(aggregateId, aggregateVersion);
        this.ticketNumber = ticketNumber;
        this.title = title;
        this.ticketType = ticketType;
        this.priority = priority;
        this.categoryId = categoryId;
        this.assigneeId = assigneeId;
        this.reporterId = reporterId;
        this.tenantId = tenantId;
    }

    public String getTicketNumber() {
        return ticketNumber;
    }

    public String getTitle() {
        return title;
    }

    public String getTicketType() {
        return ticketType;
    }

    public String getPriority() {
        return priority;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public String getAssigneeId() {
        return assigneeId;
    }

    public String getReporterId() {
        return reporterId;
    }

    public String getTenantId() {
        return tenantId;
    }
}