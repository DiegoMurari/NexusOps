package com.nexusops.sla.domain;

import com.nexusops.shared.tenancy.TenantAware;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "escalation_rules", schema = "sla", indexes = {
    @Index(name = "idx_escalation_rules_tenant", columnList = "tenant_id"),
    @Index(name = "idx_escalation_rules_active", columnList = "active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EscalationRule implements TenantAware {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Builder.Default
    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "trigger_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private TriggerType triggerType;

    @Column(name = "trigger_value")
    private Integer triggerValue;

    @Column(name = "trigger_percentage")
    private Integer triggerPercentage;

    @Column(name = "sla_definition_id", length = 36)
    private String slaDefinitionId;

    @ElementCollection
    @CollectionTable(name = "escalation_actions", schema = "sla", joinColumns = @JoinColumn(name = "rule_id"))
    @Builder.Default
    private List<EscalationAction> actions = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", length = 36)
    private String createdBy;

    @Column(name = "updated_by", length = 36)
    private String updatedBy;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public enum TriggerType {
        RESPONSE_TIME,
        RESOLUTION_TIME,
        PERCENTAGE_THRESHOLD
    }

    @Embeddable
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EscalationAction {
        @Column(name = "action_type", length = 20)
        @Enumerated(EnumType.STRING)
        private ActionType type;

        @Column(name = "target_user_id", length = 36)
        private String targetUserId;

        @Column(name = "target_group_id", length = 36)
        private String targetGroupId;

        @Column(name = "webhook_url", length = 500)
        private String webhookUrl;

        @Column(name = "template_key", length = 100)
        private String templateKey;

        @Column(name = "delay_minutes")
        @Builder.Default
        private Integer delayMinutes = 0;

        @Column(name = "repeat_interval_minutes")
        private Integer repeatIntervalMinutes;

        @Column(name = "max_repeats")
        private Integer maxRepeats;

        public enum ActionType {
            NOTIFY_USER,
            NOTIFY_GROUP,
            REASSIGN,
            WEBHOOK,
            CREATE_TASK
        }
    }
}