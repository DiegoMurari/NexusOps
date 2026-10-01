package com.nexusops.iam.dto;

import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateRoleRequest {

    @Size(max = 255)
    private String description;

    private Set<String> permissions;
}