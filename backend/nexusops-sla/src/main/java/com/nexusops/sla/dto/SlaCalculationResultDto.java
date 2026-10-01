package com.nexusops.sla.dto;

import lombok.*;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SlaCalculationResultDto {

    private String ticketId;
    private String slaDefinitionId;
    private Instant responseDueAt;
    private Instant resolutionDueAt;
    private Long responseRemainingMinutes;
    private Long resolutionRemainingMinutes;
    private Integer responsePercentage;
    private Integer resolutionPercentage;
    private Boolean responseBreachImminent;
    private Boolean resolutionBreachImminent;
    private Boolean paused;
    private Instant pausedAt;
}