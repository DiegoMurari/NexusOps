package com.nexusops.ticketing.domain;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "changes", schema = "ticketing")
@DiscriminatorValue("CHANGE")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Change extends Ticket {

    @Column(name = "risk_level", length = 20)
    @Enumerated(EnumType.STRING)
    private RiskLevel riskLevel;

    @Column(name = "implementation_plan", columnDefinition = "text")
    private String implementationPlan;

    @Column(name = "backout_plan", columnDefinition = "text")
    private String backoutPlan;

    @Column(name = "change_window_start")
    private java.time.Instant changeWindowStart;

    @Column(name = "change_window_end")
    private java.time.Instant changeWindowEnd;

    @Builder.Default
    @Column(name = "approval_status", length = 20)
    @Enumerated(EnumType.STRING)
    private ApprovalStatus approvalStatus = ApprovalStatus.PENDING;

    @Column(name = "cab_date")
    private java.time.Instant cabDate;

    @Column(name = "cab_notes", columnDefinition = "text")
    private String cabNotes;

    public enum RiskLevel {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    public enum ApprovalStatus {
        PENDING,
        APPROVED,
        REJECTED,
        CAB_REVIEW,
        EMERGENCY
    }
}