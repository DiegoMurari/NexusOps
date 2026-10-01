package com.nexusops.platform.dto;

import lombok.*;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeatureFlagEvaluationRequest {

    private String userId;
    private String tenantId;
    private Map<String, Object> context;
}