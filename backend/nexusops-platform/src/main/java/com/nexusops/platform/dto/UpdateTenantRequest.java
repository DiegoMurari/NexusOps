package com.nexusops.platform.dto;

import com.nexusops.platform.domain.Tenant;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateTenantRequest {

    @Size(max = 255)
    private String name;

    private Tenant.TenantStatus status;

    private String subscriptionTier;

    private String settings;

    private int maxUsers;

    private int maxAssets;

    private String contactEmail;

    private String contactName;
}