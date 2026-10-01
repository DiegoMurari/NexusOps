package com.nexusops.integration.domain;

import com.nexusops.shared.tenancy.TenantAware;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sync_jobs", schema = "integration", indexes = {
    @Index(name = "idx_sync_jobs_connector", columnList = "connector_id"),
    @Index(name = "idx_sync_jobs_tenant", columnList = "tenant_id"),
    @Index(name = "idx_sync_jobs_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncJob implements TenantAware {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "connector_id", nullable = false, length = 36)
    private String connectorId;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "status", length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private SyncStatus status = SyncStatus.PENDING;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "records_processed", nullable = false)
    @Builder.Default
    private int recordsProcessed = 0;

    @Column(name = "records_created", nullable = false)
    @Builder.Default
    private int recordsCreated = 0;

    @Column(name = "records_updated", nullable = false)
    @Builder.Default
    private int recordsUpdated = 0;

    @Column(name = "records_failed", nullable = false)
    @Builder.Default
    private int recordsFailed = 0;

    @Column(name = "conflicts_detected", nullable = false)
    @Builder.Default
    private int conflictsDetected = 0;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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

    public enum SyncStatus {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED,
        PARTIAL
    }
}
