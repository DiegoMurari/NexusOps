package com.nexusops.asset.dto;

import com.nexusops.asset.domain.Asset;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssetDto {

    private String id;
    private String assetTag;
    private String name;
    private String description;
    private Asset.AssetType type;
    private Asset.LifecycleStatus lifecycleStatus;
    private String tenantId;
    private String manufacturer;
    private String model;
    private String serialNumber;
    private String specifications;
    private String locationId;
    private String assignedToId;
    private Instant purchaseDate;
    private Instant warrantyExpiration;
    private BigDecimal purchaseCost;
    private String depreciationMethod;
    private String discoverySource;
    private Instant lastDiscoveredAt;
    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;
    private String updatedBy;
}
