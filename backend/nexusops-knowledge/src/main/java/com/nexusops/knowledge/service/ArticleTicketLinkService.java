package com.nexusops.knowledge.service;

import com.nexusops.knowledge.domain.Article;
import com.nexusops.knowledge.domain.ArticleTicketLink;
import com.nexusops.knowledge.dto.LinkedArticleDto;
import com.nexusops.knowledge.dto.LinkedTicketDto;
import com.nexusops.knowledge.repository.ArticleRepository;
import com.nexusops.knowledge.repository.ArticleTicketLinkRepository;
import com.nexusops.shared.directory.TicketDirectory;
import com.nexusops.shared.directory.TicketDirectory.TicketRef;
import com.nexusops.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Vínculos entre artigos e tickets. O ticket é validado no tenant pelo diretório de tickets. Quem não
 * pode editar a base ({@code canManage = false}) só enxerga e vincula artigos publicados.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ArticleTicketLinkService {

    private final ArticleRepository articleRepository;
    private final ArticleTicketLinkRepository linkRepository;
    private final TicketDirectory ticketDirectory;

    /** Vincular de novo é inofensivo: devolve o vínculo existente, sem duplicar. */
    public LinkedTicketDto link(String articleId, String ticketId, String tenantId, String actor, boolean canManage) {
        requireVisibleArticle(articleId, tenantId, canManage);
        TicketRef ticket = ticketDirectory.find(ticketId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));
        ArticleTicketLink link = linkRepository.findByTenantIdAndArticleIdAndTicketId(tenantId, articleId, ticketId)
            .orElseGet(() -> linkRepository.save(ArticleTicketLink.builder()
                .tenantId(tenantId).articleId(articleId).ticketId(ticketId).linkedBy(actor).build()));
        return toTicketDto(link, ticket);
    }

    public void unlink(String articleId, String ticketId, String tenantId, boolean canManage) {
        requireVisibleArticle(articleId, tenantId, canManage);
        linkRepository.findByTenantIdAndArticleIdAndTicketId(tenantId, articleId, ticketId).ifPresent(linkRepository::delete);
    }

    @Transactional(readOnly = true)
    public List<LinkedTicketDto> forArticle(String articleId, String tenantId, boolean canManage) {
        requireVisibleArticle(articleId, tenantId, canManage);
        List<ArticleTicketLink> links = linkRepository.findByTenantIdAndArticleIdOrderByLinkedAtDesc(tenantId, articleId);
        Map<String, TicketRef> tickets = ticketDirectory.findByIds(
            links.stream().map(ArticleTicketLink::getTicketId).toList(), tenantId);
        return links.stream()
            .filter(l -> tickets.containsKey(l.getTicketId()))
            .map(l -> toTicketDto(l, tickets.get(l.getTicketId())))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<LinkedArticleDto> forTicket(String ticketId, String tenantId, boolean canManage) {
        List<ArticleTicketLink> links = linkRepository.findByTenantIdAndTicketIdOrderByLinkedAtDesc(tenantId, ticketId);
        Map<String, Article> articles = new HashMap<>();
        articleRepository.findByTenantIdAndIdIn(tenantId, links.stream().map(ArticleTicketLink::getArticleId).toList())
            .stream()
            .filter(a -> canManage || a.getStatus() == Article.ArticleStatus.PUBLISHED)
            .forEach(a -> articles.put(a.getId(), a));
        return links.stream()
            .filter(l -> articles.containsKey(l.getArticleId()))
            .map(l -> {
                Article a = articles.get(l.getArticleId());
                return LinkedArticleDto.builder()
                    .articleId(a.getId())
                    .title(a.getTitle())
                    .status(a.getStatus().name())
                    .linkedBy(l.getLinkedBy())
                    .linkedAt(l.getLinkedAt())
                    .build();
            })
            .toList();
    }

    /** Artigo inexistente, de outro tenant ou fora da visibilidade do chamador: sempre 404. */
    private void requireVisibleArticle(String articleId, String tenantId, boolean canManage) {
        articleRepository.findByIdAndTenantId(articleId, tenantId)
            .filter(a -> canManage || a.getStatus() == Article.ArticleStatus.PUBLISHED)
            .orElseThrow(() -> new ResourceNotFoundException("Article", articleId));
    }

    private static LinkedTicketDto toTicketDto(ArticleTicketLink link, TicketRef ticket) {
        return LinkedTicketDto.builder()
            .ticketId(ticket.id())
            .ticketNumber(ticket.number())
            .title(ticket.title())
            .status(ticket.status())
            .linkedBy(link.getLinkedBy())
            .linkedAt(link.getLinkedAt())
            .build();
    }
}
