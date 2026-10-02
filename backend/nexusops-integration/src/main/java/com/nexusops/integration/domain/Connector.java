package com.nexusops.integration.domain;

import com.nexusops.shared.tenancy.TenantAware;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "connectors", schema = "integration", indexes = {
    @Index(name = "idx_connectors_tenant", columnList = "tenant_id"),
    @Index(name = "idx_connectors_type", columnList = "type"),
    @Index(name = "idx_connectors_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Connector implements TenantAware {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ConnectorType type;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    /** Non-secret settings (base URL, project key, channel...). Credentials are never stored. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "configuration", columnDefinition = "jsonb")
    private String configuration;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "field_mapping", columnDefinition = "jsonb")
    private String fieldMapping;

    @Column(name = "status", length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ConnectorStatus status = ConnectorStatus.DISCONNECTED;

    @Column(name = "last_sync_at")
    private Instant lastSyncAt;

    @Column(name = "last_sync_status", length = 20)
    private String lastSyncStatus;

    @Column(name = "sync_schedule_cron", length = 100)
    private String syncScheduleCron;

    /** Conector desligado não é verificado nem usado; a configuração é mantida. */
    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private boolean enabled = true;

    /** Última verificação de alcance do endereço configurado. SUCCESS ou FAILURE. */
    @Column(name = "last_check_at")
    private Instant lastCheckAt;

    @Column(name = "last_check_status", length = 20)
    private String lastCheckStatus;

    @Column(name = "last_check_message", length = 255)
    private String lastCheckMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Column(name = "updated_by", length = 255)
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

    public enum ConnectorType {
        JIRA,
        SLACK,
        TEAMS,
        SERVICENOW,
        ZENDESK,
        CUSTOM
    }

    public enum ConnectorStatus {
        CONNECTED,
        DISCONNECTED,
        ERROR,
        SYNCING,
        AUTH_EXPIRED
    }
}
