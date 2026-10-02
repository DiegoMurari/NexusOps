package com.nexusops.knowledge.repository;

import com.nexusops.knowledge.domain.ArticleTicketLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ArticleTicketLinkRepository extends JpaRepository<ArticleTicketLink, String> {

    List<ArticleTicketLink> findByTenantIdAndArticleIdOrderByLinkedAtDesc(String tenantId, String articleId);

    List<ArticleTicketLink> findByTenantIdAndTicketIdOrderByLinkedAtDesc(String tenantId, String ticketId);

    Optional<ArticleTicketLink> findByTenantIdAndArticleIdAndTicketId(String tenantId, String articleId, String ticketId);
}
