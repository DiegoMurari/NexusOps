package com.nexusops.knowledge.controller;

import com.nexusops.knowledge.dto.ArticleFeedbackDto;
import com.nexusops.knowledge.dto.CreateArticleFeedbackRequest;
import com.nexusops.knowledge.service.ArticleFeedbackService;
import com.nexusops.shared.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/articles/{articleId}/feedback")
@RequiredArgsConstructor
@Tag(name = "Article Feedback", description = "Knowledge article helpfulness feedback endpoints")
public class ArticleFeedbackController {

    private final ArticleFeedbackService feedbackService;
    private final SecurityUtils securityUtils;

    @PostMapping
    @PreAuthorize("hasPermission('KNOWLEDGE', 'READ')")
    @Operation(summary = "Submit helpfulness feedback for an article")
    public ResponseEntity<ArticleFeedbackDto> submitFeedback(@PathVariable String articleId,
                                                               @Valid @RequestBody CreateArticleFeedbackRequest request) {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        String userId = securityUtils.getCurrentUserId().orElseThrow();
        return ResponseEntity.ok(feedbackService.submitFeedback(articleId, tenantId, request, userId));
    }

    @GetMapping
    @PreAuthorize("hasPermission('KNOWLEDGE', 'UPDATE')")
    @Operation(summary = "List feedback for an article")
    public ResponseEntity<List<ArticleFeedbackDto>> listFeedback(@PathVariable String articleId) {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        return ResponseEntity.ok(feedbackService.findByArticleId(articleId, tenantId));
    }
}
