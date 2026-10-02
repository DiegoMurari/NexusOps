package com.nexusops.knowledge.service;

import com.nexusops.knowledge.domain.Article;
import com.nexusops.knowledge.domain.KnowledgeCategory;
import com.nexusops.knowledge.dto.ArticleDto;
import com.nexusops.knowledge.dto.CreateArticleRequest;
import com.nexusops.knowledge.dto.UpdateArticleRequest;
import com.nexusops.knowledge.mapper.ArticleMapper;
import com.nexusops.knowledge.repository.ArticleRepository;
import com.nexusops.knowledge.repository.KnowledgeCategoryRepository;
import com.nexusops.shared.exception.ResourceNotFoundException;
import com.nexusops.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Transactional
public class ArticleService {

    private static final char ESCAPE = '\\';

    private final ArticleRepository articleRepository;
    private final KnowledgeCategoryRepository categoryRepository;
    private final ArticleMapper articleMapper;

    public ArticleDto createArticle(CreateArticleRequest request, String tenantId, String authorId) {
        KnowledgeCategory category = resolveCategory(request.getCategoryId(), tenantId);

        Article article = articleMapper.toEntity(request);
        article.setTenantId(tenantId);
        article.setAuthorId(authorId);
        article.setCreatedBy(authorId);
        article.setUpdatedBy(authorId);
        article.setStatus(Article.ArticleStatus.DRAFT);
        article.setSlug(generateUniqueSlug(
            request.getSlug() != null && !request.getSlug().isBlank() ? request.getSlug() : request.getTitle(),
            tenantId));
        if (request.getFeatured() != null) article.setFeatured(request.getFeatured());
        if (request.getAllowComments() != null) article.setAllowComments(request.getAllowComments());

        Article saved = articleRepository.save(article);

        if (category != null) {
            category.setArticleCount(category.getArticleCount() + 1);
            categoryRepository.save(category);
        }

        return articleMapper.toDto(saved);
    }

    @Transactional(readOnly = true)
    /**
     * Listagem com filtros combináveis. Quem não pode editar a base ({@code canManage = false}) só vê artigos
     * publicados, qualquer que seja o filtro de situação pedido.
     */
    public Page<ArticleDto> list(String tenantId, Article.ArticleStatus status, String categoryId,
                                  Boolean featured, String search, boolean canManage, Pageable pageable) {
        Specification<Article> spec = (root, query, cb) -> cb.equal(root.get("tenantId"), tenantId);
        if (!canManage) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), Article.ArticleStatus.PUBLISHED));
        } else if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (categoryId != null && !categoryId.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("categoryId"), categoryId));
        }
        if (Boolean.TRUE.equals(featured)) {
            spec = spec.and((root, query, cb) -> cb.isTrue(root.get("featured")));
        }
        if (search != null && !search.isBlank()) {
            String like = "%" + escapeLike(search.trim().toLowerCase()) + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("title")), like, ESCAPE),
                cb.like(cb.lower(root.get("excerpt")), like, ESCAPE),
                cb.like(cb.lower(root.get("content")), like, ESCAPE)));
        }
        return articleRepository.findAll(spec, pageable).map(articleMapper::toDto);
    }

    /** Rascunhos e arquivados só existem para quem pode editar; para os demais são 404, e não contam visualização. */
    public ArticleDto getAndRecordView(String id, String tenantId, boolean canManage) {
        Article article = articleRepository.findByIdAndTenantId(id, tenantId)
            .filter(a -> canManage || a.getStatus() == Article.ArticleStatus.PUBLISHED)
            .orElseThrow(() -> new ResourceNotFoundException("Article", id));
        if (article.getStatus() == Article.ArticleStatus.PUBLISHED) {
            article.setViewCount(article.getViewCount() + 1);
            article = articleRepository.save(article);
        }
        return articleMapper.toDto(article);
    }

    /** Escapa os curingas do LIKE para que o texto buscado seja casado literalmente. */
    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    @Transactional(readOnly = true)
    public ArticleDto findByIdOrThrow(String id, String tenantId) {
        Article article = articleRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Article", id));
        return articleMapper.toDto(article);
    }

    public ArticleDto updateArticle(String id, String tenantId, UpdateArticleRequest request, String updatedBy) {
        Article article = articleRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Article", id));

        if (request.getTitle() != null) article.setTitle(request.getTitle());
        if (request.getContent() != null) article.setContent(request.getContent());
        if (request.getContentHtml() != null) article.setContentHtml(request.getContentHtml());
        if (request.getExcerpt() != null) article.setExcerpt(request.getExcerpt());
        if (request.getSeoTitle() != null) article.setSeoTitle(request.getSeoTitle());
        if (request.getSeoDescription() != null) article.setSeoDescription(request.getSeoDescription());
        if (request.getSeoKeywords() != null) article.setSeoKeywords(request.getSeoKeywords());
        if (request.getFeatured() != null) article.setFeatured(request.getFeatured());
        if (request.getAllowComments() != null) article.setAllowComments(request.getAllowComments());
        if (request.getTags() != null) article.setTags(request.getTags());

        if (request.getCategoryId() != null && !request.getCategoryId().equals(article.getCategoryId())) {
            KnowledgeCategory newCategory = resolveCategory(request.getCategoryId(), tenantId);
            adjustCategoryCount(article.getCategoryId(), tenantId, -1);
            article.setCategoryId(request.getCategoryId());
            if (newCategory != null) {
                newCategory.setArticleCount(newCategory.getArticleCount() + 1);
                categoryRepository.save(newCategory);
            }
        }

        article.setVersion(article.getVersion() + 1);
        article.setUpdatedBy(updatedBy);
        article.setUpdatedAt(Instant.now());

        return articleMapper.toDto(articleRepository.save(article));
    }

    public ArticleDto publish(String id, String tenantId, String publishedBy) {
        Article article = articleRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Article", id));
        article.setStatus(Article.ArticleStatus.PUBLISHED);
        article.setPublishedAt(Instant.now());
        article.setPublishedBy(publishedBy);
        return articleMapper.toDto(articleRepository.save(article));
    }

    public ArticleDto archive(String id, String tenantId) {
        Article article = articleRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Article", id));
        article.setStatus(Article.ArticleStatus.ARCHIVED);
        return articleMapper.toDto(articleRepository.save(article));
    }

    public void deleteArticle(String id, String tenantId) {
        Article article = articleRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Article", id));
        adjustCategoryCount(article.getCategoryId(), tenantId, -1);
        articleRepository.delete(article);
    }

    @Transactional(readOnly = true)
    public long countByTenantId(String tenantId) {
        return articleRepository.countByTenantId(tenantId);
    }

    private KnowledgeCategory resolveCategory(String categoryId, String tenantId) {
        if (categoryId == null) return null;
        return categoryRepository.findByIdAndTenantId(categoryId, tenantId)
            .orElseThrow(() -> new ValidationException("Category " + categoryId + " not found in tenant"));
    }

    private void adjustCategoryCount(String categoryId, String tenantId, int delta) {
        if (categoryId == null) return;
        categoryRepository.findByIdAndTenantId(categoryId, tenantId).ifPresent(category -> {
            category.setArticleCount(Math.max(0, category.getArticleCount() + delta));
            categoryRepository.save(category);
        });
    }

    private String generateUniqueSlug(String source, String tenantId) {
        String base = source.toLowerCase()
            .replaceAll("[^a-z0-9\\s-]", "")
            .trim()
            .replaceAll("\\s+", "-");
        if (base.isBlank()) base = "artigo";
        if (base.length() > 480) base = base.substring(0, 480);

        String slug = base;
        int suffix = 2;
        while (articleRepository.existsBySlugAndTenantId(slug, tenantId)) {
            slug = base + "-" + suffix++;
        }
        return slug;
    }
}
