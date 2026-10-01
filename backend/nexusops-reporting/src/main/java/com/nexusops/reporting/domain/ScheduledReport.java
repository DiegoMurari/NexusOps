package com.nexusops.reporting.domain;

import com.nexusops.shared.tenancy.TenantAware;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "scheduled_reports", schema = "reporting", indexes = {
    @Index(name = "idx_sched_reports_tenant", columnList = "tenant_id"),
    @Index(name = "idx_sched_reports_report", columnList = "report_id"),
    @Index(name = "idx_sched_reports_next_run", columnList = "next_run_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduledReport implements TenantAware {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "report_id", nullable = false, length = 36)
    private String reportId;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "schedule_cron", nullable = false, length = 100)
    private String scheduleCron;

    @Builder.Default
    @Column(name = "timezone", length = 50)
    private String timezone = "UTC";

    @Builder.Default
    @Column(name = "format", length = 20)
    @Enumerated(EnumType.STRING)
    private ExportFormat format = ExportFormat.PDF;

    @Builder.Default
    @Column(name = "delivery_method", length = 20)
    @Enumerated(EnumType.STRING)
    private DeliveryMethod deliveryMethod = DeliveryMethod.EMAIL;

    @ElementCollection
    @CollectionTable(name = "scheduled_report_recipients", schema = "reporting", joinColumns = @JoinColumn(name = "scheduled_report_id"))
    @Column(name = "recipient")
    @Builder.Default
    private List<String> recipients = new ArrayList<>();

    @Builder.Default
    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @Column(name = "next_run_at")
    private Instant nextRunAt;

    @Column(name = "last_run_status", length = 20)
    private String lastRunStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    @Builder.Default
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

    public enum ExportFormat {
        PDF,
        EXCEL,
        CSV
    }

    public enum DeliveryMethod {
        EMAIL,
        SFTP,
        S3
    }
}