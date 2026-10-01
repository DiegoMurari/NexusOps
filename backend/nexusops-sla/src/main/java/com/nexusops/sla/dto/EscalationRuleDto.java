package com.nexusops.sla.dto;

import com.nexusops.sla.domain.EscalationRule;
import lombok.*;

import java.time.Instant;
import java.util.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EscalationRuleDto {

    private String id;
    private String name;
    private String description;
    private String tenantId;
    private boolean active;
    private EscalationRule.TriggerType triggerType;
    private Integer triggerValue;
    private Integer triggerPercentage;
    private String slaDefinitionId;
    private List<EscalationActionDto> actions;
    private Instant createdAt;
    private Instant updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EscalationActionDto {
        private EscalationRule.EscalationAction.ActionType type;
        private String targetUserId;
        private String targetGroupId;
        private String webhookUrl;
        private String templateKey;
        private Integer delayMinutes = 0;
        private Integer repeatIntervalMinutes;
        private Integer maxRepeats;
    }
}