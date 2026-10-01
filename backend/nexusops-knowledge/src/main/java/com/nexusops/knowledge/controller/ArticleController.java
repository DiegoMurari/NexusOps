package com.nexusops.knowledge.controller;

import com.nexusops.knowledge.domain.Article;
import com.nexusops.knowledge.dto.ArticleDto;
import com.nexusops.knowledge.dto.CreateArticleRequest;
import com.nexusops.knowledge.dto.UpdateArticleRequest;
import com.nexusops.knowledge.service.ArticleService;
import com.nexusops.shared.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/articles")
@RequiredArgsConstructor
@Tag(name = "Knowledge Articles", description = "Knowledge base article management endpoints")
public class ArticleController {

    private final ArticleService articleService;
    private final SecurityUtils securityUtils;

    @PostMapping
    @PreAuthorize("hasPermission('KNOWLEDGE', 'CREATE')")
    @Operation(summary = "Create a new knowledge article")
    public ResponseEntity<ArticleDto> createArticle(@Valid @RequestBody CreateArticleRequest request) {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        String authorId = securityUtils.getCurrentUserId().orElseThrow();
        return ResponseEntity.ok(articleService.createArticle(request, tenantId, authorId));
    }

    @GetMapping
    @PreAuthorize("hasPermission('KNOWLEDGE', 'READ')")
    @Operation(summary = "List knowledge articles with pagination and filters")
    public ResponseEntity<Page<ArticleDto>> listArticles(
            @RequestParam(required = false) Article.ArticleStatus status,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) Boolean featured,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        return ResponseEntity.ok(articleService.list(tenantId, status, categoryId, featured, search, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('KNOWLEDGE', 'READ')")
    @Operation(summary = "Get article by ID and record a view")
    public ResponseEntity<ArticleDto> getArticle(@PathVariable String id) {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        return ResponseEntity.ok(articleService.getAndRecordView(id, tenantId));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasPermission('KNOWLEDGE', 'UPDATE')")
    @Operation(summary = "Update article")
    public ResponseEntity<ArticleDto> updateArticle(@PathVariable String id, @Valid @RequestBody UpdateArticleRequest request) {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        String updatedBy = securityUtils.getCurrentUserId().orElseThrow();
        return ResponseEntity.ok(articleService.updateArticle(id, tenantId, request, updatedBy));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasPermission('KNOWLEDGE', 'UPDATE')")
    @Operation(summary = "Publish article")
    public ResponseEntity<ArticleDto> publishArticle(@PathVariable String id) {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        String publishedBy = securityUtils.getCurrentUserId().orElseThrow();
        return ResponseEntity.ok(articleService.publish(id, tenantId, publishedBy));
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasPermission('KNOWLEDGE', 'UPDATE')")
    @Operation(summary = "Archive article")
    public ResponseEntity<ArticleDto> archiveArticle(@PathVariable String id) {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        return ResponseEntity.ok(articleService.archive(id, tenantId));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission('KNOWLEDGE', 'DELETE')")
    @Operation(summary = "Delete article")
    public ResponseEntity<Void> deleteArticle(@PathVariable String id) {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        articleService.deleteArticle(id, tenantId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/stats/count")
    @PreAuthorize("hasPermission('KNOWLEDGE', 'READ')")
    @Operation(summary = "Count articles for current tenant")
    public ResponseEntity<Long> countArticles() {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        return ResponseEntity.ok(articleService.countByTenantId(tenantId));
    }
}
