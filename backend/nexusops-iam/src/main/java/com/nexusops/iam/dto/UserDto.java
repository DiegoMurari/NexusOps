package com.nexusops.iam.dto;

import com.nexusops.iam.domain.User;
import lombok.*;

import java.time.Instant;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDto {

    private String id;
    private String email;
    private String firstName;
    private String lastName;
    private String phone;
    private User.UserStatus status;
    private String tenantId;
    private String avatarUrl;
    private Instant lastLoginAt;
    private boolean emailVerified;
    private boolean mustChangePassword;
    private Set<String> roles;
    private Set<String> permissions;
    private boolean mfaEnabled;
    private Instant createdAt;
    private Instant updatedAt;

    public String getFullName() {
        return (firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "");
    }
}