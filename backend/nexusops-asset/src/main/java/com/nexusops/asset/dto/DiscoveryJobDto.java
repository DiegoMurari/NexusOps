package com.nexusops.asset.dto;

import com.nexusops.asset.domain.DiscoveryJob;
import lombok.*;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiscoveryJobDto {

    private String id;
    private String name;
    private String sourceType;
    private String tenantId;
    private DiscoveryJob.JobStatus status;
    private String configuration;
    private String normalizationRules;
    private String scheduleCron;
    private Instant lastRunAt;
    private Instant nextRunAt;
    private DiscoveryJob.JobStatus lastRunStatus;
    private Long lastRunDurationSeconds;
    private Integer assetsDiscovered;
    private Integer assetsUpdated;
    private String errorMessage;
    private Instant createdAt;
    private Instant updatedAt;
}
