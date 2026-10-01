package com.nexusops.platform.dto;

import com.nexusops.platform.domain.FeatureFlag;
import lombok.*;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeatureFlagDto {

    private String id;
    private String key;
    private String name;
    private String description;
    private boolean enabled;
    private int rolloutPercentage;
    private String targetingRules;
    private String variants;
    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;
    private String updatedBy;
}