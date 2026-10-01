package com.nexusops.ticketing.event;

import com.nexusops.shared.event.DomainEvent;

public class CommentAddedEvent extends DomainEvent {

    private final String ticketId;
    private final String ticketNumber;
    private final String authorId;
    private final boolean publicComment;
    private final String tenantId;

    public CommentAddedEvent(String aggregateId, long aggregateVersion, String ticketId, String ticketNumber,
                             String authorId, boolean publicComment, String tenantId) {
        super(aggregateId, aggregateVersion);
        this.ticketId = ticketId;
        this.ticketNumber = ticketNumber;
        this.authorId = authorId;
        this.publicComment = publicComment;
        this.tenantId = tenantId;
    }

    public String getTicketId() {
        return ticketId;
    }

    public String getTicketNumber() {
        return ticketNumber;
    }

    public String getAuthorId() {
        return authorId;
    }

    public boolean isPublicComment() {
        return publicComment;
    }

    public String getTenantId() {
        return tenantId;
    }
}