package com.nexusops.platform.dto;

import com.nexusops.platform.domain.Tenant;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateTenantRequest {

    @NotBlank
    @Size(max = 255)
    private String name;

    @NotBlank
    @Size(max = 255)
    private String domain;

    @NotNull
    private Tenant.TenantStatus status = Tenant.TenantStatus.ACTIVE;

    private String subscriptionTier = "FREE";

    private String settings = "{}";

    private int maxUsers = 100;

    private int maxAssets = 1000;

    private String contactEmail;

    private String contactName;
}