package com.nexusops.iam.dto;

import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MfaSetupResponse {

    private String secret;
    private String qrCodeUrl;
    private List<String> recoveryCodes;
}