package com.nexusops.asset.domain;

import com.nexusops.shared.tenancy.TenantAware;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/** Uma linha do histórico de um ativo: criação, campo alterado ou vínculo com ticket. Nunca é editada. */
@Entity
@Table(name = "asset_history", schema = "asset", indexes = {
    @Index(name = "idx_asset_history_asset", columnList = "tenant_id, asset_id, created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssetHistory implements TenantAware {

    public static final String CREATED = "CREATED";
    public static final String FIELD_CHANGED = "FIELD_CHANGED";
    public static final String TICKET_LINKED = "TICKET_LINKED";
    public static final String TICKET_UNLINKED = "TICKET_UNLINKED";

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "asset_id", nullable = false, length = 36)
    private String assetId;

    @Column(name = "event_type", nullable = false, length = 30)
    private String eventType;

    @Column(name = "field_name", length = 60)
    private String fieldName;

    @Column(name = "old_value", length = 500)
    private String oldValue;

    @Column(name = "new_value", length = 500)
    private String newValue;

    @Column(name = "actor", length = 255)
    private String actor;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
