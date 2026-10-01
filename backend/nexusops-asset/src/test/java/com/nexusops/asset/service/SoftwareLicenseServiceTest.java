package com.nexusops.asset.service;

import com.nexusops.asset.domain.SoftwareLicense;
import com.nexusops.asset.dto.SoftwareLicenseDto;
import com.nexusops.asset.dto.UpdateSoftwareLicenseRequest;
import com.nexusops.asset.mapper.SoftwareLicenseMapper;
import com.nexusops.asset.repository.SoftwareLicenseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SoftwareLicenseServiceTest {

    @Mock
    private SoftwareLicenseRepository licenseRepository;
    @Mock
    private SoftwareLicenseMapper licenseMapper;

    private SoftwareLicenseService service;

    @BeforeEach
    void setUp() {
        service = new SoftwareLicenseService(licenseRepository, licenseMapper);
    }

    @Test
    void updateLicense_appliesSeatChangesAndPersists() {
        SoftwareLicense license = SoftwareLicense.builder()
            .id("lic1")
            .name("Adobe Creative Cloud")
            .totalSeats(10)
            .usedSeats(5)
            .build();
        when(licenseRepository.findByIdAndTenantId("lic1", "tenant-1")).thenReturn(Optional.of(license));
        when(licenseRepository.save(license)).thenReturn(license);
        when(licenseMapper.toDto(license)).thenReturn(SoftwareLicenseDto.builder().id("lic1").build());

        UpdateSoftwareLicenseRequest request = UpdateSoftwareLicenseRequest.builder().usedSeats(10).build();
        service.updateLicense("lic1", "tenant-1", request, "user-1");

        assertThat(license.getUsedSeats()).isEqualTo(10);
        ArgumentCaptor<SoftwareLicense> captor = ArgumentCaptor.forClass(SoftwareLicense.class);
        verify(licenseRepository).save(captor.capture());
        assertThat(captor.getValue().getUsedSeats()).isEqualTo(10);
    }

    @Test
    void updateLicense_leavesUntouchedFieldsUnchanged() {
        SoftwareLicense license = SoftwareLicense.builder()
            .id("lic1")
            .name("Adobe Creative Cloud")
            .vendor("Adobe")
            .totalSeats(10)
            .usedSeats(5)
            .build();
        when(licenseRepository.findByIdAndTenantId("lic1", "tenant-1")).thenReturn(Optional.of(license));
        when(licenseRepository.save(license)).thenReturn(license);
        when(licenseMapper.toDto(license)).thenReturn(SoftwareLicenseDto.builder().id("lic1").build());

        service.updateLicense("lic1", "tenant-1", UpdateSoftwareLicenseRequest.builder().totalSeats(20).build(), "user-1");

        assertThat(license.getVendor()).isEqualTo("Adobe");
        assertThat(license.getUsedSeats()).isEqualTo(5);
        assertThat(license.getTotalSeats()).isEqualTo(20);
    }
}
