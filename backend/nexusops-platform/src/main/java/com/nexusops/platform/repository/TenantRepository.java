package com.nexusops.platform.repository;

import com.nexusops.platform.domain.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, String> {

    Optional<Tenant> findByDomain(String domain);

    boolean existsByDomain(String domain);

    List<Tenant> findByStatus(Tenant.TenantStatus status);

    Optional<Tenant> findByName(String name);
}