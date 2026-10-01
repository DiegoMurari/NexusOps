package com.nexusops.knowledge.repository;

import com.nexusops.knowledge.domain.Article;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ArticleRepository extends JpaRepository<Article, String> {

    Optional<Article> findByIdAndTenantId(String id, String tenantId);

    Optional<Article> findBySlugAndTenantId(String slug, String tenantId);

    Page<Article> findByTenantId(String tenantId, Pageable pageable);

    Page<Article> findByTenantIdAndStatus(String tenantId, Article.ArticleStatus status, Pageable pageable);

    Page<Article> findByTenantIdAndCategoryId(String tenantId, String categoryId, Pageable pageable);

    Page<Article> findByTenantIdAndFeaturedTrue(String tenantId, Pageable pageable);

    @Query("SELECT a FROM Article a WHERE a.tenantId = :tenantId AND " +
        "(LOWER(a.title) LIKE LOWER(CONCAT('%', :search, '%')) " +
        "OR LOWER(a.excerpt) LIKE LOWER(CONCAT('%', :search, '%')) " +
        "OR LOWER(a.content) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Article> search(@Param("tenantId") String tenantId, @Param("search") String search, Pageable pageable);

    boolean existsBySlugAndTenantId(String slug, String tenantId);

    long countByTenantId(String tenantId);

    long countByTenantIdAndCategoryId(String tenantId, String categoryId);
}
