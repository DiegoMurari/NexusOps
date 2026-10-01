package com.nexusops.asset.service;

import com.nexusops.asset.domain.SoftwareLicense;
import com.nexusops.asset.dto.CreateSoftwareLicenseRequest;
import com.nexusops.asset.dto.SoftwareLicenseDto;
import com.nexusops.asset.dto.UpdateSoftwareLicenseRequest;
import com.nexusops.asset.mapper.SoftwareLicenseMapper;
import com.nexusops.asset.repository.SoftwareLicenseRepository;
import com.nexusops.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Transactional
public class SoftwareLicenseService {

    private final SoftwareLicenseRepository licenseRepository;
    private final SoftwareLicenseMapper licenseMapper;

    public SoftwareLicenseDto createLicense(CreateSoftwareLicenseRequest request, String tenantId, String createdBy) {
        SoftwareLicense license = licenseMapper.toEntity(request);
        license.setTenantId(tenantId);
        license.setLicenseType(request.getLicenseType() != null ? request.getLicenseType() : SoftwareLicense.LicenseType.PERPETUAL);
        license.setTotalSeats(request.getTotalSeats());
        license.setCreatedBy(createdBy);
        license.setUpdatedBy(createdBy);

        return licenseMapper.toDto(licenseRepository.save(license));
    }

    @Transactional(readOnly = true)
    public Page<SoftwareLicenseDto> list(String tenantId, SoftwareLicense.ComplianceStatus status, Pageable pageable) {
        Page<SoftwareLicense> page = status != null
            ? licenseRepository.findByTenantIdAndComplianceStatus(tenantId, status, pageable)
            : licenseRepository.findByTenantId(tenantId, pageable);
        return page.map(licenseMapper::toDto);
    }

    @Transactional(readOnly = true)
    public SoftwareLicenseDto findByIdOrThrow(String id, String tenantId) {
        SoftwareLicense license = licenseRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("SoftwareLicense", id));
        return licenseMapper.toDto(license);
    }

    public SoftwareLicenseDto updateLicense(String id, String tenantId, UpdateSoftwareLicenseRequest request, String updatedBy) {
        SoftwareLicense license = licenseRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("SoftwareLicense", id));

        if (request.getName() != null) license.setName(request.getName());
        if (request.getVendor() != null) license.setVendor(request.getVendor());
        if (request.getProductVersion() != null) license.setProductVersion(request.getProductVersion());
        if (request.getLicenseType() != null) license.setLicenseType(request.getLicenseType());
        if (request.getTotalSeats() != null) license.setTotalSeats(request.getTotalSeats());
        if (request.getUsedSeats() != null) license.setUsedSeats(request.getUsedSeats());
        if (request.getEntitlements() != null) license.setEntitlements(request.getEntitlements());
        if (request.getExpirationDate() != null) license.setExpirationDate(request.getExpirationDate());
        if (request.getRenewalDate() != null) license.setRenewalDate(request.getRenewalDate());
        if (request.getCost() != null) license.setCost(request.getCost());
        license.setUpdatedBy(updatedBy);
        license.setUpdatedAt(Instant.now());

        return licenseMapper.toDto(licenseRepository.save(license));
    }

    public void deleteLicense(String id, String tenantId) {
        SoftwareLicense license = licenseRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("SoftwareLicense", id));
        licenseRepository.delete(license);
    }
}
