package com.nexusops.asset.repository;

import com.nexusops.asset.domain.AssetHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AssetHistoryRepository extends JpaRepository<AssetHistory, String> {

    Page<AssetHistory> findByTenantIdAndAssetIdOrderByCreatedAtDesc(String tenantId, String assetId, Pageable pageable);
}
