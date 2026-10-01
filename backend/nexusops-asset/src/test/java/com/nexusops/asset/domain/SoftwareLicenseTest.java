package com.nexusops.asset.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SoftwareLicenseTest {

    @Test
    void compliant_whenUsageWellBelowThreshold() {
        SoftwareLicense license = SoftwareLicense.builder().totalSeats(10).usedSeats(5).build();
        license.onUpdate();
        assertThat(license.getComplianceStatus()).isEqualTo(SoftwareLicense.ComplianceStatus.COMPLIANT);
    }

    @Test
    void atRisk_whenUsageAtOrAboveNinetyPercent() {
        SoftwareLicense license = SoftwareLicense.builder().totalSeats(10).usedSeats(9).build();
        license.onUpdate();
        assertThat(license.getComplianceStatus()).isEqualTo(SoftwareLicense.ComplianceStatus.AT_RISK);
    }

    @Test
    void atCapacity_whenUsedEqualsTotal() {
        SoftwareLicense license = SoftwareLicense.builder().totalSeats(10).usedSeats(10).build();
        license.onUpdate();
        assertThat(license.getComplianceStatus()).isEqualTo(SoftwareLicense.ComplianceStatus.AT_CAPACITY);
    }

    @Test
    void nonCompliant_whenUsedExceedsTotal() {
        SoftwareLicense license = SoftwareLicense.builder().totalSeats(10).usedSeats(11).build();
        license.onUpdate();
        assertThat(license.getComplianceStatus()).isEqualTo(SoftwareLicense.ComplianceStatus.NON_COMPLIANT);
    }

    @Test
    void utilizationPercentage_computedFromSeats() {
        SoftwareLicense license = SoftwareLicense.builder().totalSeats(4).usedSeats(1).build();
        assertThat(license.getUtilizationPercentage()).isEqualTo(25.0);
    }

    @Test
    void utilizationPercentage_zeroWhenNoSeatsAllocated() {
        SoftwareLicense license = SoftwareLicense.builder().totalSeats(0).usedSeats(0).build();
        assertThat(license.getUtilizationPercentage()).isEqualTo(0.0);
    }
}
