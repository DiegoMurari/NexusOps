package com.nexusops.asset.repository;

import com.nexusops.asset.domain.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LocationRepository extends JpaRepository<Location, String> {

    Optional<Location> findByIdAndTenantId(String id, String tenantId);

    List<Location> findByTenantId(String tenantId);

    List<Location> findByTenantIdAndParentIdIsNull(String tenantId);

    List<Location> findByParentId(String parentId);
}
