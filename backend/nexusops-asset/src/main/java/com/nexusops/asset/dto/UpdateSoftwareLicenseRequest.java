package com.nexusops.asset.dto;

import com.nexusops.asset.domain.SoftwareLicense;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateSoftwareLicenseRequest {

    @Size(max = 255)
    private String name;

    @Size(max = 100)
    private String vendor;

    @Size(max = 50)
    private String productVersion;

    private SoftwareLicense.LicenseType licenseType;

    @Min(0)
    private Integer totalSeats;

    @Min(0)
    private Integer usedSeats;

    private String entitlements;

    private Instant expirationDate;

    private Instant renewalDate;

    private BigDecimal cost;
}
