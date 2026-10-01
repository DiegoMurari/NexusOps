package com.nexusops.notification.domain;

import com.nexusops.shared.tenancy.TenantAware;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "subscriptions", schema = "notification", indexes = {
    @Index(name = "idx_subscriptions_user", columnList = "user_id"),
    @Index(name = "idx_subscriptions_tenant", columnList = "tenant_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subscription implements TenantAware {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @ElementCollection
    @CollectionTable(name = "subscription_channels", schema = "notification", joinColumns = @JoinColumn(name = "subscription_id"))
    @Column(name = "channel")
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Set<Channel> enabledChannels = EnumSet.allOf(Channel.class);

    @ElementCollection
    @CollectionTable(name = "subscription_categories", schema = "notification", joinColumns = @JoinColumn(name = "subscription_id"))
    @Column(name = "category")
    @Builder.Default
    private Set<String> enabledCategories = new HashSet<>();

    @Column(name = "quiet_hours_start", length = 5)
    private String quietHoursStart;

    @Column(name = "quiet_hours_end", length = 5)
    private String quietHoursEnd;

    @Column(name = "timezone", length = 50)
    @Builder.Default
    private String timezone = "UTC";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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

    public enum Channel {
        IN_APP,
        EMAIL,
        WEBSOCKET,
        SSE,
        PUSH
    }
}