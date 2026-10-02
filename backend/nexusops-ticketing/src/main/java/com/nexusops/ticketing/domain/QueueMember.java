package com.nexusops.ticketing.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "queue_members", schema = "ticketing")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QueueMember {

    public enum MemberRole { MEMBER, LEAD }

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "queue_id", nullable = false, length = 36)
    private String queueId;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "member_role", nullable = false, length = 10)
    private MemberRole role = MemberRole.MEMBER;

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
