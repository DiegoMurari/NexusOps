package com.nexusops.asset.dto;

import com.nexusops.asset.domain.CIRelationship;
import lombok.*;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CIRelationshipDto {

    private String id;
    private String sourceId;
    private String targetId;
    private CIRelationship.RelationshipType relationshipType;
    private String tenantId;
    private String description;
    private Instant createdAt;
    private String createdBy;
}
