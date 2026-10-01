package com.nexusops.iam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MfaEnableRequest {

    @NotBlank
    @Size(min = 6, max = 6)
    private String code;
}