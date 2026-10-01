package com.nexusops.knowledge.service;

import com.nexusops.knowledge.domain.Article;
import com.nexusops.knowledge.domain.ArticleFeedback;
import com.nexusops.knowledge.dto.ArticleFeedbackDto;
import com.nexusops.knowledge.dto.CreateArticleFeedbackRequest;
import com.nexusops.knowledge.mapper.ArticleFeedbackMapper;
import com.nexusops.knowledge.repository.ArticleFeedbackRepository;
import com.nexusops.knowledge.repository.ArticleRepository;
import com.nexusops.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ArticleFeedbackService {

    private final ArticleFeedbackRepository feedbackRepository;
    private final ArticleRepository articleRepository;
    private final ArticleFeedbackMapper feedbackMapper;

    public ArticleFeedbackDto submitFeedback(String articleId, String tenantId, CreateArticleFeedbackRequest request, String userId) {
        Article article = articleRepository.findByIdAndTenantId(articleId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Article", articleId));

        ArticleFeedback feedback = ArticleFeedback.builder()
            .articleId(articleId)
            .tenantId(tenantId)
            .userId(userId)
            .helpful(request.getHelpful())
            .comment(request.getComment())
            .build();
        ArticleFeedback saved = feedbackRepository.save(feedback);

        if (request.getHelpful()) {
            article.setHelpfulCount(article.getHelpfulCount() + 1);
        } else {
            article.setNotHelpfulCount(article.getNotHelpfulCount() + 1);
        }
        articleRepository.save(article);

        return feedbackMapper.toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<ArticleFeedbackDto> findByArticleId(String articleId, String tenantId) {
        return feedbackRepository.findByArticleIdAndTenantId(articleId, tenantId).stream()
            .map(feedbackMapper::toDto).toList();
    }
}
