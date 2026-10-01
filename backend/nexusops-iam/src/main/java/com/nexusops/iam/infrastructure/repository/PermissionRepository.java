package com.nexusops.iam.infrastructure.repository;

import com.nexusops.iam.domain.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, UUID> {

    Optional<Permission> findByPermissionKey(String permissionKey);

    List<Permission> findByResource(String resource);

    List<Permission> findByCategory(String category);

    List<Permission> findByResourceAndAction(String resource, String action);

    Optional<Permission> findByResourceAndActionAndScope(String resource, String action, String scope);

    List<Permission> findByScope(String scope);

    @Query("SELECT p FROM Permission p WHERE p.permissionKey IN :keys")
    List<Permission> findByKeys(@Param("keys") List<String> keys);
}