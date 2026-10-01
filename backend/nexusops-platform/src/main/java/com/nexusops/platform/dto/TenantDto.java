package com.nexusops.platform.dto;

import com.nexusops.platform.domain.Tenant;
import lombok.*;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantDto {

    private String id;
    private String name;
    private String domain;
    private Tenant.TenantStatus status;
    private String subscriptionTier;
    private Instant subscriptionExpires;
    private String settings;
    private int maxUsers;
    private int maxAssets;
    private String contactEmail;
    private String contactName;
    private Instant createdAt;
    private Instant updatedAt;
}