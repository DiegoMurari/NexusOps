package com.nexusops.ticketing.repository;

import com.nexusops.ticketing.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, String> {

    List<Category> findByTenantId(String tenantId);

    List<Category> findByTenantIdAndParentIdIsNull(String tenantId);

    List<Category> findByParentId(String parentId);

    Optional<Category> findByTenantIdAndName(String tenantId, String name);

    List<Category> findByTenantIdAndActiveTrue(String tenantId);
}