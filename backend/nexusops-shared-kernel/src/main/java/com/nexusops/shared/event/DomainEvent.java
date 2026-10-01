package com.nexusops.shared.event;

import java.time.Instant;
import java.util.UUID;

public abstract class DomainEvent {
    private final String eventId;
    private final Instant occurredAt;
    private final String aggregateId;
    private final long aggregateVersion;

    protected DomainEvent(String aggregateId, long aggregateVersion) {
        this.eventId = UUID.randomUUID().toString();
        this.occurredAt = Instant.now();
        this.aggregateId = aggregateId;
        this.aggregateVersion = aggregateVersion;
    }

    public String getEventId() {
        return eventId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public long getAggregateVersion() {
        return aggregateVersion;
    }

    public String getEventType() {
        return this.getClass().getSimpleName();
    }
}