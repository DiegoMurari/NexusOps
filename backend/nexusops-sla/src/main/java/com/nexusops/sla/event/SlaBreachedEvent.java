package com.nexusops.sla.event;

import com.nexusops.shared.event.DomainEvent;

import java.time.Instant;

public class SlaBreachedEvent extends DomainEvent {

    private final String ticketId;
    private final String slaDefinitionId;
    private final String tenantId;
    private final String breachType;
    private final Integer responseTimeMinutes;
    private final Integer resolutionTimeMinutes;
    private final Instant breachTime;

    public SlaBreachedEvent(String aggregateId, long aggregateVersion, String ticketId, String slaDefinitionId,
                           String tenantId, String breachType, Integer responseTimeMinutes,
                           Integer resolutionTimeMinutes, Instant breachTime) {
        super(aggregateId, aggregateVersion);
        this.ticketId = ticketId;
        this.slaDefinitionId = slaDefinitionId;
        this.tenantId = tenantId;
        this.breachType = breachType;
        this.responseTimeMinutes = responseTimeMinutes;
        this.resolutionTimeMinutes = resolutionTimeMinutes;
        this.breachTime = breachTime;
    }

    public String getTicketId() {
        return ticketId;
    }

    public String getSlaDefinitionId() {
        return slaDefinitionId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getBreachType() {
        return breachType;
    }

    public Integer getResponseTimeMinutes() {
        return responseTimeMinutes;
    }

    public Integer getResolutionTimeMinutes() {
        return resolutionTimeMinutes;
    }

    public Instant getBreachTime() {
        return breachTime;
    }
}