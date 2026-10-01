package com.nexusops.ticketing.domain;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "incidents", schema = "ticketing")
@DiscriminatorValue("INCIDENT")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Incident extends Ticket {

    @Column(name = "root_cause", columnDefinition = "text")
    private String rootCause;

    @Column(name = "known_error", columnDefinition = "text")
    private String knownError;

    @Column(name = "workaround", columnDefinition = "text")
    private String workaround;

    @Column(name = "related_incident_id", length = 36)
    private String relatedIncidentId;
}