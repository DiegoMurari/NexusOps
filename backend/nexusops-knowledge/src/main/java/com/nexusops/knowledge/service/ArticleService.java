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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Transactional
public class ArticleService {

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
    public Page<ArticleDto> list(String tenantId, Article.ArticleStatus status, String categoryId,
                                  Boolean featured, String search, Pageable pageable) {
        Page<Article> page;
        if (search != null && !search.isBlank()) {
            page = articleRepository.search(tenantId, search.trim(), pageable);
        } else if (status != null) {
            page = articleRepository.findByTenantIdAndStatus(tenantId, status, pageable);
        } else if (categoryId != null) {
            page = articleRepository.findByTenantIdAndCategoryId(tenantId, categoryId, pageable);
        } else if (Boolean.TRUE.equals(featured)) {
            page = articleRepository.findByTenantIdAndFeaturedTrue(tenantId, pageable);
        } else {
            page = articleRepository.findByTenantId(tenantId, pageable);
        }
        return page.map(articleMapper::toDto);
    }

    public ArticleDto getAndRecordView(String id, String tenantId) {
        Article article = articleRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Article", id));
        article.setViewCount(article.getViewCount() + 1);
        return articleMapper.toDto(articleRepository.save(article));
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
