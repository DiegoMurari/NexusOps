package com.nexusops.asset.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateDiscoveryJobRequest {

    @NotNull
    @Size(min = 1, max = 255)
    private String name;

    @NotNull
    @Size(min = 1, max = 50)
    private String sourceType;

    private String configuration;

    private String normalizationRules;

    @Size(max = 100)
    private String scheduleCron;
}
