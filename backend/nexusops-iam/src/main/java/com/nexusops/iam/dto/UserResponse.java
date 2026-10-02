package com.nexusops.iam.dto;

import lombok.*;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {

    private UUID id;
    private Long version;
    private String email;
    private String firstName;
    private String lastName;
    private String phone;
    private String jobTitle;
    private String department;
    private String defaultLocationId;
    private String status;
    private String tenantId;
    private Set<String> roles;
    private Set<String> permissions;
    private Boolean mfaEnabled;
    private Instant lastLoginAt;
    private Instant createdAt;
    private Instant updatedAt;
}