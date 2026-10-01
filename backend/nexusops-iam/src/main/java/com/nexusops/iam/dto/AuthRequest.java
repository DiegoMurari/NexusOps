package com.nexusops.iam.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthRequest {

    @NotBlank
    private String username;

    @NotBlank
    private String password;

    private String mfaCode;

    private Boolean rememberMe = false;
}