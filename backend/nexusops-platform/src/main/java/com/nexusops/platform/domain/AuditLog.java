package com.nexusops.platform.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_logs", schema = "platform", indexes = {
    @Index(name = "idx_audit_logs_event_id", columnList = "event_id"),
    @Index(name = "idx_audit_logs_tenant", columnList = "tenant_id"),
    @Index(name = "idx_audit_logs_user", columnList = "user_id"),
    @Index(name = "idx_audit_logs_resource", columnList = "resource_type, resource_id"),
    @Index(name = "idx_audit_logs_timestamp", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "event_id", nullable = false, length = 100)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "aggregate_id", length = 36)
    private String aggregateId;

    @Column(name = "aggregate_type", length = 100)
    private String aggregateType;

    @Column(name = "tenant_id", length = 36)
    private String tenantId;

    @Column(name = "user_id", length = 36)
    private String userId;

    @Column(name = "resource_type", length = 100)
    private String resourceType;

    @Column(name = "resource_id", length = 36)
    private String resourceId;

    @Column(name = "action", length = 50)
    private String action;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb")
    private String payload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "previous_state", columnDefinition = "jsonb")
    private String previousState;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "correlation_id", length = 36)
    private String correlationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        createdAt = Instant.now();
    }
}