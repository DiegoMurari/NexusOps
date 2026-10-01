package com.nexusops.platform.dto;

import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateFeatureFlagRequest {

    @Size(max = 255)
    private String name;

    @Size(max = 1000)
    private String description;

    private Boolean enabled;

    private Integer rolloutPercentage;

    private String targetingRules;

    private String variants;
}