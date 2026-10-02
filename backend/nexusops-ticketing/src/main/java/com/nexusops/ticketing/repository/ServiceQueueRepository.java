package com.nexusops.ticketing.repository;

import com.nexusops.ticketing.domain.ServiceQueue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceQueueRepository extends JpaRepository<ServiceQueue, String> {

    List<ServiceQueue> findByTenantIdOrderByNameAsc(String tenantId);

    List<ServiceQueue> findByTenantIdAndActiveTrueOrderByNameAsc(String tenantId);

    Optional<ServiceQueue> findByIdAndTenantId(String id, String tenantId);

    boolean existsByTenantIdAndNameIgnoreCase(String tenantId, String name);

    boolean existsByTenantIdAndNameIgnoreCaseAndIdNot(String tenantId, String name, String id);

    boolean existsByTenantIdAndCodeIgnoreCase(String tenantId, String code);

    boolean existsByTenantIdAndCodeIgnoreCaseAndIdNot(String tenantId, String code, String id);
}
