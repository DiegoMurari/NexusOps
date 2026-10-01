package com.nexusops.platform.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeatureFlagEvaluationResponse {

    private String key;
    private boolean enabled;
    private String variant;
}