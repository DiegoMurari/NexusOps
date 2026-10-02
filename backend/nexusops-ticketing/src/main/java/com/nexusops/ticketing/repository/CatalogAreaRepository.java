package com.nexusops.ticketing.repository;

import com.nexusops.ticketing.domain.CatalogArea;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CatalogAreaRepository extends JpaRepository<CatalogArea, String> {

    List<CatalogArea> findByTenantIdOrderBySortOrderAscNameAsc(String tenantId);

    List<CatalogArea> findByTenantIdAndActiveTrueOrderBySortOrderAscNameAsc(String tenantId);

    Optional<CatalogArea> findByIdAndTenantId(String id, String tenantId);

    boolean existsByTenantIdAndNameIgnoreCase(String tenantId, String name);

    boolean existsByTenantIdAndNameIgnoreCaseAndIdNot(String tenantId, String name, String id);
}
