package com.nexusops.iam.dto;

import lombok.*;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoleResponse {

    private UUID id;
    private Long version;
    private String name;
    private String description;
    private String tenantId;
    private Set<String> permissions;
    private Boolean isSystem;
    private Instant createdAt;
    private Instant updatedAt;
}