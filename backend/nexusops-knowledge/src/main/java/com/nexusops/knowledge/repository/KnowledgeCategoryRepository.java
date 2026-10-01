package com.nexusops.knowledge.repository;

import com.nexusops.knowledge.domain.KnowledgeCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KnowledgeCategoryRepository extends JpaRepository<KnowledgeCategory, String> {

    Optional<KnowledgeCategory> findByIdAndTenantId(String id, String tenantId);

    List<KnowledgeCategory> findByTenantId(String tenantId);

    List<KnowledgeCategory> findByTenantIdAndParentIdIsNull(String tenantId);

    List<KnowledgeCategory> findByTenantIdAndParentId(String tenantId, String parentId);

    boolean existsBySlugAndTenantId(String slug, String tenantId);
}
