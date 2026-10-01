package com.nexusops.sla.dto;

import lombok.*;

import java.time.Instant;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SlaComplianceDto {

    private String tenantId;
    private Instant periodStart;
    private Instant periodEnd;
    private Long totalTickets;
    private Long ticketsWithSla;
    private Long responseBreaches;
    private Long resolutionBreaches;
    private Double responseComplianceRate;
    private Double resolutionComplianceRate;
    private Double overallComplianceRate;
    private Long avgResponseTimeMinutes;
    private Long avgResolutionTimeMinutes;
    private Map<String, Long> breachesByCategory;
    private Map<String, Long> breachesByPriority;
}