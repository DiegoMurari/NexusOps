package com.nexusops.iam.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MfaDisableRequest {

    @NotBlank
    private String password;
}