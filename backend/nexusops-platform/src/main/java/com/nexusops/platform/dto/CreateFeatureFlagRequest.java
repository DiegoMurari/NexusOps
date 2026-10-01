package com.nexusops.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateFeatureFlagRequest {

    @NotBlank
    @Size(max = 100)
    private String key;

    @NotBlank
    @Size(max = 255)
    private String name;

    @Size(max = 1000)
    private String description;

    @NotNull
    private boolean enabled = false;

    private int rolloutPercentage = 0;

    private String targetingRules;

    private String variants;
}