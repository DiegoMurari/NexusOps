package com.nexusops.iam.infrastructure.repository;

import com.nexusops.iam.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByName(String name);

    Optional<Role> findByNameAndTenantId(String name, String tenantId);

    /** System roles plus the tenant's own custom roles. */
    @Query("SELECT r FROM Role r WHERE r.isSystem = true OR r.tenantId = :tenantId ORDER BY r.isSystem DESC, r.name")
    List<Role> findVisible(@Param("tenantId") String tenantId);
}
