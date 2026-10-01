package com.nexusops.reporting.domain;

import com.nexusops.shared.tenancy.TenantAware;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "widgets", schema = "reporting", indexes = {
    @Index(name = "idx_widgets_dashboard", columnList = "dashboard_id"),
    @Index(name = "idx_widgets_tenant", columnList = "tenant_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Widget implements TenantAware {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "dashboard_id", nullable = false, length = 36)
    private String dashboardId;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Builder.Default
    @Column(name = "type", length = 20)
    @Enumerated(EnumType.STRING)
    private WidgetType type = WidgetType.METRIC;

    @Column(name = "query", columnDefinition = "text")
    private String query;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameters", columnDefinition = "jsonb")
    private String parameters;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "visualization_config", columnDefinition = "jsonb")
    private String visualizationConfig;

    @Builder.Default
    @Column(name = "position_x", nullable = false)
    private int positionX = 0;

    @Builder.Default
    @Column(name = "position_y", nullable = false)
    private int positionY = 0;

    @Builder.Default
    @Column(name = "width", nullable = false)
    private int width = 1;

    @Builder.Default
    @Column(name = "height", nullable = false)
    private int height = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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

    public enum WidgetType {
        METRIC,
        CHART,
        TABLE,
        GAUGE,
        TEXT
    }
}