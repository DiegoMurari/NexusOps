package com.nexusops.asset.dto;

import com.nexusops.asset.domain.SoftwareLicense;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateSoftwareLicenseRequest {

    @NotNull
    @Size(min = 1, max = 255)
    private String name;

    @NotNull
    @Size(min = 1, max = 255)
    private String productName;

    @Size(max = 100)
    private String vendor;

    @Size(max = 50)
    private String productVersion;

    private SoftwareLicense.LicenseType licenseType;

    @NotNull
    @Min(0)
    private Integer totalSeats;

    private String entitlements;

    private Instant purchaseDate;

    private Instant expirationDate;

    private Instant renewalDate;

    private BigDecimal cost;
}
