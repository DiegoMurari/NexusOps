package com.nexusops.asset.dto;

import com.nexusops.asset.domain.Asset;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateAssetRequest {

    @Size(max = 100)
    private String assetTag;

    @NotNull
    @Size(min = 1, max = 255)
    private String name;

    @Size(max = 4000)
    private String description;

    @NotNull
    private Asset.AssetType type;

    private Asset.LifecycleStatus lifecycleStatus;

    @Size(max = 100)
    private String manufacturer;

    @Size(max = 100)
    private String model;

    @Size(max = 100)
    private String serialNumber;

    private String specifications;

    private String locationId;

    private String assignedToId;

    private Instant purchaseDate;

    private Instant warrantyExpiration;

    private BigDecimal purchaseCost;

    @Size(max = 20)
    private String depreciationMethod;
}
