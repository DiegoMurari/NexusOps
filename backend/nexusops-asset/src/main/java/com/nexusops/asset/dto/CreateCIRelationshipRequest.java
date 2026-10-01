package com.nexusops.asset.dto;

import com.nexusops.asset.domain.CIRelationship;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateCIRelationshipRequest {

    @NotNull
    private String sourceId;

    @NotNull
    private String targetId;

    @NotNull
    private CIRelationship.RelationshipType relationshipType;

    @Size(max = 500)
    private String description;
}
