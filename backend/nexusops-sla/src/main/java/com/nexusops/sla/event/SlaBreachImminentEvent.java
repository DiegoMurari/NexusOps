package com.nexusops.sla.event;

import com.nexusops.shared.event.DomainEvent;

public class SlaBreachImminentEvent extends DomainEvent {

    private final String ticketId;
    private final String slaDefinitionId;
    private final String tenantId;
    private final String breachType;
    private final Integer percentage;
    private final Integer remainingMinutes;

    public SlaBreachImminentEvent(String aggregateId, long aggregateVersion, String ticketId, String slaDefinitionId,
                                  String tenantId, String breachType, Integer percentage, Integer remainingMinutes) {
        super(aggregateId, aggregateVersion);
        this.ticketId = ticketId;
        this.slaDefinitionId = slaDefinitionId;
        this.tenantId = tenantId;
        this.breachType = breachType;
        this.percentage = percentage;
        this.remainingMinutes = remainingMinutes;
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

    public Integer getPercentage() {
        return percentage;
    }

    public Integer getRemainingMinutes() {
        return remainingMinutes;
    }
}