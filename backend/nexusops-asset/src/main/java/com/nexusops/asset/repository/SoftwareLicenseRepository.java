package com.nexusops.asset.repository;

import com.nexusops.asset.domain.SoftwareLicense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SoftwareLicenseRepository extends JpaRepository<SoftwareLicense, String> {

    Optional<SoftwareLicense> findByIdAndTenantId(String id, String tenantId);

    Page<SoftwareLicense> findByTenantId(String tenantId, Pageable pageable);

    Page<SoftwareLicense> findByTenantIdAndComplianceStatus(String tenantId, SoftwareLicense.ComplianceStatus status, Pageable pageable);
}
