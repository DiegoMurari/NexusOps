package com.nexusops.asset.repository;

import com.nexusops.asset.domain.CIRelationship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CIRelationshipRepository extends JpaRepository<CIRelationship, String> {

    List<CIRelationship> findByTenantIdAndSourceId(String tenantId, String sourceId);

    List<CIRelationship> findByTenantIdAndTargetId(String tenantId, String targetId);

    @Query("SELECT r FROM CIRelationship r WHERE r.tenantId = :tenantId AND (r.sourceId = :assetId OR r.targetId = :assetId)")
    List<CIRelationship> findByTenantIdAndAssetId(@Param("tenantId") String tenantId, @Param("assetId") String assetId);
}
