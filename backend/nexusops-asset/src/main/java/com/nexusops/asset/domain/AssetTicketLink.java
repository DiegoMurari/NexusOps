package com.nexusops.asset.domain;

import com.nexusops.shared.tenancy.TenantAware;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/** Vínculo entre um ativo e um ticket (o ticket é só referenciado por id). */
@Entity
@Table(name = "asset_ticket_links", schema = "asset", uniqueConstraints = {
    @UniqueConstraint(name = "uk_asset_ticket", columnNames = {"asset_id", "ticket_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssetTicketLink implements TenantAware {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "asset_id", nullable = false, length = 36)
    private String assetId;

    @Column(name = "ticket_id", nullable = false, length = 36)
    private String ticketId;

    @Column(name = "linked_by", length = 255)
    private String linkedBy;

    @Column(name = "linked_at", nullable = false, updatable = false)
    private Instant linkedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (linkedAt == null) {
            linkedAt = Instant.now();
        }
    }
}
