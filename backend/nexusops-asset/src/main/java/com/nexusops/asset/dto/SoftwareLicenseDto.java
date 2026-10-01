package com.nexusops.asset.dto;

import com.nexusops.asset.domain.SoftwareLicense;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SoftwareLicenseDto {

    private String id;
    private String name;
    private String productName;
    private String vendor;
    private String productVersion;
    private SoftwareLicense.LicenseType licenseType;
    private String tenantId;
    private int totalSeats;
    private int usedSeats;
    private SoftwareLicense.ComplianceStatus complianceStatus;
    private String entitlements;
    private Instant purchaseDate;
    private Instant expirationDate;
    private Instant renewalDate;
    private BigDecimal cost;
    private double utilizationPercentage;
    private Instant createdAt;
    private Instant updatedAt;
}
