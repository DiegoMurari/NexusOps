package com.nexusops.asset.repository;

import com.nexusops.asset.domain.Asset;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AssetRepository extends JpaRepository<Asset, String> {

    Optional<Asset> findByIdAndTenantId(String id, String tenantId);

    java.util.List<Asset> findByTenantIdAndIdIn(String tenantId, java.util.Collection<String> ids);

    Optional<Asset> findByAssetTagAndTenantId(String assetTag, String tenantId);

    Page<Asset> findByTenantId(String tenantId, Pageable pageable);

    Page<Asset> findByTenantIdAndType(String tenantId, Asset.AssetType type, Pageable pageable);

    Page<Asset> findByTenantIdAndLifecycleStatus(String tenantId, Asset.LifecycleStatus lifecycleStatus, Pageable pageable);

    Page<Asset> findByTenantIdAndAssignedToId(String tenantId, String assignedToId, Pageable pageable);

    Page<Asset> findByTenantIdAndLocationId(String tenantId, String locationId, Pageable pageable);

    @Query("SELECT a FROM Asset a WHERE a.tenantId = :tenantId AND " +
        "(LOWER(a.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
        "OR LOWER(a.assetTag) LIKE LOWER(CONCAT('%', :search, '%')) " +
        "OR LOWER(a.serialNumber) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Asset> search(@Param("tenantId") String tenantId, @Param("search") String search, Pageable pageable);

    long countByTenantId(String tenantId);

    long countByTenantIdAndLifecycleStatus(String tenantId, Asset.LifecycleStatus lifecycleStatus);
}
