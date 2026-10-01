package com.nexusops.iam.dto;

import lombok.*;

import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {

    private String accessToken;
    private String refreshToken;
    private Long expiresIn;
    private String tokenType;
    private UserInfo user;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UserInfo {
        private String id;
        private String username;
        private String email;
        private String firstName;
        private String lastName;
        private Set<String> roles;
        private Set<String> permissions;
        private Boolean mfaEnabled;
        private String tenantId;
    }
}