package com.nexusops.iam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateRoleRequest {

    @NotBlank
    @Size(max = 50)
    private String name;

    @Size(max = 255)
    private String description;

    private String tenantId;

    private Set<String> permissions;
}