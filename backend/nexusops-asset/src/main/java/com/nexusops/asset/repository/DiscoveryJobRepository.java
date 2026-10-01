package com.nexusops.asset.repository;

import com.nexusops.asset.domain.DiscoveryJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DiscoveryJobRepository extends JpaRepository<DiscoveryJob, String> {

    Optional<DiscoveryJob> findByIdAndTenantId(String id, String tenantId);

    List<DiscoveryJob> findByTenantId(String tenantId);

    List<DiscoveryJob> findByTenantIdAndStatus(String tenantId, DiscoveryJob.JobStatus status);
}
