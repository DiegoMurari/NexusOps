package com.nexusops.iam.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateUserRequest {

    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Size(min = 12)
    private String password;

    @Size(max = 100)
    private String firstName;

    @Size(max = 100)
    private String lastName;

    /** Ignored: the new user always belongs to the caller's tenant. */
    private String tenantId;

    private Set<String> roles;
}