package com.nexusops.knowledge.repository;

import com.nexusops.knowledge.domain.ArticleFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ArticleFeedbackRepository extends JpaRepository<ArticleFeedback, String> {

    List<ArticleFeedback> findByArticleIdAndTenantId(String articleId, String tenantId);
}
