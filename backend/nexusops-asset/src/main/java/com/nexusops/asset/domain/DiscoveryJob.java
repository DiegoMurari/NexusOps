package com.nexusops.asset.domain;

import com.nexusops.shared.tenancy.TenantAware;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "discovery_jobs", schema = "asset", indexes = {
    @Index(name = "idx_discovery_job_tenant", columnList = "tenant_id"),
    @Index(name = "idx_discovery_job_status", columnList = "status"),
    @Index(name = "idx_discovery_job_source", columnList = "source_type")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiscoveryJob implements TenantAware {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "source_type", nullable = false, length = 50)
    private String sourceType;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private JobStatus status = JobStatus.PENDING;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "configuration", columnDefinition = "jsonb")
    private String configuration;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "normalization_rules", columnDefinition = "jsonb")
    private String normalizationRules;

    @Column(name = "schedule_cron", length = 100)
    private String scheduleCron;

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @Column(name = "next_run_at")
    private Instant nextRunAt;

    @Column(name = "last_run_status", length = 20)
    @Enumerated(EnumType.STRING)
    private JobStatus lastRunStatus;

    @Column(name = "last_run_duration_seconds")
    private Long lastRunDurationSeconds;

    @Column(name = "assets_discovered")
    @Builder.Default
    private Integer assetsDiscovered = 0;

    @Column(name = "assets_updated")
    @Builder.Default
    private Integer assetsUpdated = 0;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

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
    @Builder.Default
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

    public enum JobStatus {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED
    }
}