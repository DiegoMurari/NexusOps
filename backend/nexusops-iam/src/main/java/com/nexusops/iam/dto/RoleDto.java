package com.nexusops.iam.dto;

import com.nexusops.iam.domain.Role;
import lombok.*;

import java.time.Instant;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoleDto {

    private String id;
    private String name;
    private String description;
    private String tenantId;
    private boolean systemRole;
    private Set<String> permissions;
    private Instant createdAt;
    private Instant updatedAt;
}