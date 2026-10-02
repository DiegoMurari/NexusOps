package com.nexusops.knowledge.repository;

import com.nexusops.knowledge.domain.Article;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ArticleRepository extends JpaRepository<Article, String>, JpaSpecificationExecutor<Article> {

    Optional<Article> findByIdAndTenantId(String id, String tenantId);

    java.util.List<Article> findByTenantIdAndIdIn(String tenantId, java.util.Collection<String> ids);

    Optional<Article> findBySlugAndTenantId(String slug, String tenantId);

    Page<Article> findByTenantId(String tenantId, Pageable pageable);

    Page<Article> findByTenantIdAndStatus(String tenantId, Article.ArticleStatus status, Pageable pageable);

    Page<Article> findByTenantIdAndCategoryId(String tenantId, String categoryId, Pageable pageable);

    Page<Article> findByTenantIdAndFeaturedTrue(String tenantId, Pageable pageable);


    boolean existsBySlugAndTenantId(String slug, String tenantId);

    long countByTenantId(String tenantId);

    long countByTenantIdAndCategoryId(String tenantId, String categoryId);
}
