package com.nexusops.knowledge.domain;

import com.nexusops.shared.tenancy.TenantAware;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "articles", schema = "knowledge", indexes = {
    @Index(name = "idx_articles_tenant", columnList = "tenant_id"),
    @Index(name = "idx_articles_status", columnList = "status"),
    @Index(name = "idx_articles_category", columnList = "category_id"),
    @Index(name = "idx_articles_author", columnList = "author_id")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uq_articles_tenant_slug", columnNames = {"tenant_id", "slug"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Article implements TenantAware {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "slug", nullable = false, length = 500)
    private String slug;

    @Column(name = "content", columnDefinition = "text")
    private String content;

    @Column(name = "content_html", columnDefinition = "text")
    private String contentHtml;

    @Column(name = "excerpt", columnDefinition = "text")
    private String excerpt;

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ArticleStatus status = ArticleStatus.DRAFT;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "category_id", length = 36)
    private String categoryId;

    @Column(name = "author_id", nullable = false, length = 36)
    private String authorId;

    @Column(name = "version", nullable = false)
    @Builder.Default
    private int version = 1;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "published_by", length = 36)
    private String publishedBy;

    @Column(name = "seo_title", length = 255)
    private String seoTitle;

    @Column(name = "seo_description", length = 500)
    private String seoDescription;

    @Column(name = "seo_keywords", length = 500)
    private String seoKeywords;

    @Column(name = "view_count", nullable = false)
    @Builder.Default
    private long viewCount = 0;

    @Column(name = "helpful_count", nullable = false)
    @Builder.Default
    private long helpfulCount = 0;

    @Column(name = "not_helpful_count", nullable = false)
    @Builder.Default
    private long notHelpfulCount = 0;

    @Column(name = "featured", nullable = false)
    @Builder.Default
    private boolean featured = false;

    @Column(name = "allow_comments", nullable = false)
    @Builder.Default
    private boolean allowComments = true;

    @ElementCollection
    @CollectionTable(name = "article_tags", schema = "knowledge", joinColumns = @JoinColumn(name = "article_id"))
    @Column(name = "tag")
    @Builder.Default
    private Set<String> tags = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "article_translations", schema = "knowledge", joinColumns = @JoinColumn(name = "article_id"))
    @MapKeyColumn(name = "locale")
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "translation", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, String> translations = new HashMap<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", length = 36)
    private String createdBy;

    @Column(name = "updated_by", length = 36)
    private String updatedBy;

    @Version
    @Column(name = "jpa_version", nullable = false)
    private Long jpaVersion = 0L;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (slug == null && title != null) {
            slug = generateSlug(title);
        }
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    private String generateSlug(String title) {
        String base = title.toLowerCase()
            .replaceAll("[^a-z0-9\\s-]", "")
            .replaceAll("\\s+", "-")
            .substring(0, Math.min(title.length(), 200));
        return base + "-" + System.currentTimeMillis();
    }

    public double getHelpfulPercentage() {
        long total = helpfulCount + notHelpfulCount;
        return total > 0 ? (double) helpfulCount / total * 100 : 0;
    }

    public enum ArticleStatus {
        DRAFT,
        REVIEW,
        PUBLISHED,
        ARCHIVED
    }
}