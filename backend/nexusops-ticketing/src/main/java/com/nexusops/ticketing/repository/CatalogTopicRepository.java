package com.nexusops.ticketing.repository;

import com.nexusops.ticketing.domain.CatalogTopic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CatalogTopicRepository extends JpaRepository<CatalogTopic, String> {

    List<CatalogTopic> findByTenantIdOrderBySortOrderAscNameAsc(String tenantId);

    List<CatalogTopic> findByTenantIdAndActiveTrueOrderBySortOrderAscNameAsc(String tenantId);

    List<CatalogTopic> findByAreaIdAndTenantIdOrderBySortOrderAscNameAsc(String areaId, String tenantId);

    Optional<CatalogTopic> findByIdAndTenantId(String id, String tenantId);

    boolean existsByAreaId(String areaId);

    boolean existsByDefaultQueueId(String queueId);

    boolean existsByAreaIdAndNameIgnoreCase(String areaId, String name);

    boolean existsByAreaIdAndNameIgnoreCaseAndIdNot(String areaId, String name, String id);
}
