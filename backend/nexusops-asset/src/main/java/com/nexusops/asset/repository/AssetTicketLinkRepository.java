package com.nexusops.asset.repository;

import com.nexusops.asset.domain.AssetTicketLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AssetTicketLinkRepository extends JpaRepository<AssetTicketLink, String> {

    List<AssetTicketLink> findByTenantIdAndAssetIdOrderByLinkedAtDesc(String tenantId, String assetId);

    List<AssetTicketLink> findByTenantIdAndTicketIdOrderByLinkedAtDesc(String tenantId, String ticketId);

    Optional<AssetTicketLink> findByTenantIdAndAssetIdAndTicketId(String tenantId, String assetId, String ticketId);
}
