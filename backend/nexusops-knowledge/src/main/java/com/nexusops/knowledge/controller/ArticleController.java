package com.nexusops.knowledge.controller;

import com.nexusops.knowledge.domain.Article;
import com.nexusops.knowledge.dto.ArticleDto;
import com.nexusops.knowledge.dto.CreateArticleRequest;
import com.nexusops.knowledge.dto.LinkedArticleDto;
import com.nexusops.knowledge.dto.LinkedTicketDto;
import com.nexusops.knowledge.dto.UpdateArticleRequest;
import com.nexusops.knowledge.service.ArticleService;
import com.nexusops.knowledge.service.ArticleTicketLinkService;
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

import java.util.List;

@RestController
@RequestMapping("/articles")
@RequiredArgsConstructor
@Tag(name = "Knowledge Articles", description = "Knowledge base article management endpoints")
public class ArticleController {

    private final ArticleService articleService;
    private final ArticleTicketLinkService linkService;
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
        return ResponseEntity.ok(articleService.list(tenantId, status, categoryId, featured, search, canManage(), pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('KNOWLEDGE', 'READ')")
    @Operation(summary = "Get article by ID and record a view")
    public ResponseEntity<ArticleDto> getArticle(@PathVariable String id) {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        return ResponseEntity.ok(articleService.getAndRecordView(id, tenantId, canManage()));
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

    @GetMapping("/{id}/tickets")
    @PreAuthorize("hasPermission('KNOWLEDGE', 'READ')")
    @Operation(summary = "Tickets linked to an article")
    public ResponseEntity<List<LinkedTicketDto>> linkedTickets(@PathVariable String id) {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        return ResponseEntity.ok(linkService.forArticle(id, tenantId, canManage()));
    }

    @PutMapping("/{id}/tickets/{ticketId}")
    @PreAuthorize("hasPermission('KNOWLEDGE', 'UPDATE')")
    @Operation(summary = "Link a ticket to an article (idempotent)")
    public ResponseEntity<LinkedTicketDto> linkTicket(@PathVariable String id, @PathVariable String ticketId) {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        String actor = securityUtils.getCurrentUserId().orElseThrow();
        return ResponseEntity.ok(linkService.link(id, ticketId, tenantId, actor, canManage()));
    }

    @DeleteMapping("/{id}/tickets/{ticketId}")
    @PreAuthorize("hasPermission('KNOWLEDGE', 'UPDATE')")
    @Operation(summary = "Unlink a ticket from an article")
    public ResponseEntity<Void> unlinkTicket(@PathVariable String id, @PathVariable String ticketId) {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        linkService.unlink(id, ticketId, tenantId, canManage());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/by-ticket/{ticketId}")
    @PreAuthorize("hasPermission('KNOWLEDGE', 'READ')")
    @Operation(summary = "Articles linked to a ticket")
    public ResponseEntity<List<LinkedArticleDto>> articlesOfTicket(@PathVariable String ticketId) {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        return ResponseEntity.ok(linkService.forTicket(ticketId, tenantId, canManage()));
    }

    /** Quem pode editar a base também enxerga rascunhos e arquivados; os demais só os publicados. */
    private static boolean canManage() {
        return SecurityUtils.getCurrentUserPermissions().stream().anyMatch(p -> p.startsWith("PERM_KNOWLEDGE:UPDATE:"));
    }
}
