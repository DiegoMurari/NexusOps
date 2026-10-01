package com.nexusops.platform.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "feature_flags", schema = "platform", indexes = {
    @Index(name = "idx_feature_flags_key", columnList = "key", unique = true),
    @Index(name = "idx_feature_flags_enabled", columnList = "enabled")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeatureFlag {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "key", nullable = false, unique = true, length = 100)
    private String key;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Builder.Default
    @Column(name = "enabled", nullable = false)
    private boolean enabled = false;

    @Builder.Default
    @Column(name = "rollout_percentage", nullable = false)
    private int rolloutPercentage = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "targeting_rules", columnDefinition = "jsonb")
    private String targetingRules;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "variants", columnDefinition = "jsonb")
    private String variants;

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

    public boolean evaluate(String userId, String tenantId, Map<String, Object> context) {
        if (!enabled) {
            return false;
        }

        if (rolloutPercentage >= 100) {
            return true;
        }

        if (rolloutPercentage > 0) {
            int hash = Math.abs((userId + tenantId + key).hashCode()) % 100;
            return hash < rolloutPercentage;
        }

        return false;
    }
}