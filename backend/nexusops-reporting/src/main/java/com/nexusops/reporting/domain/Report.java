package com.nexusops.reporting.domain;

import com.nexusops.shared.tenancy.TenantAware;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reports", schema = "reporting", indexes = {
    @Index(name = "idx_reports_tenant", columnList = "tenant_id"),
    @Index(name = "idx_reports_owner", columnList = "owner_id"),
    @Index(name = "idx_reports_type", columnList = "report_type")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Report implements TenantAware {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "owner_id", nullable = false, length = 255)
    private String ownerId;

    @Builder.Default
    @Column(name = "report_type", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private ReportType reportType = ReportType.CUSTOM;

    @Column(name = "query", columnDefinition = "text")
    private String query;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameters", columnDefinition = "jsonb")
    private String parameters;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "visualization_config", columnDefinition = "jsonb")
    private String visualizationConfig;

    @Column(name = "schedule_cron", length = 100)
    private String scheduleCron;

    @Builder.Default
    @Column(name = "timezone", length = 50)
    private String timezone = "UTC";

    @Builder.Default
    @Column(name = "public", nullable = false)
    private boolean publicReport = false;

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

    public enum ReportType {
        CUSTOM,
        TICKET_SUMMARY,
        SLA_COMPLIANCE,
        AGENT_PERFORMANCE,
        CATEGORY_DISTRIBUTION,
        TREND_ANALYSIS
    }
}