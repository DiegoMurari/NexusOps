package com.nexusops.sla.dto;

import com.nexusops.sla.domain.SlaBreach;
import lombok.*;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SlaBreachDto {

    private String id;
    private String ticketId;
    private String slaDefinitionId;
    private String tenantId;
    private SlaBreach.BreachType breachType;
    private Instant breachTime;
    private boolean acknowledged;
    private String acknowledgedBy;
    private Instant acknowledgedAt;
    private boolean escalated;
    private Instant escalatedAt;
    private boolean resolved;
    private Instant resolvedAt;
    private Integer responseTimeMinutes;
    private Integer resolutionTimeMinutes;
    private Integer breachPercentage;
    private Instant createdAt;
    private Instant updatedAt;
}