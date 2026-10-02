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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArticleTicketLinkServiceTest {

    private static final String TENANT = "tenant-1";
    private static final TicketRef TICKET = new TicketRef("t1", "NX-1042", "VPN cai toda hora", "OPEN");

    @Mock
    private ArticleRepository articleRepository;
    @Mock
    private ArticleTicketLinkRepository linkRepository;
    @Mock
    private TicketDirectory ticketDirectory;

    private ArticleTicketLinkService service;

    @BeforeEach
    void setUp() {
        service = new ArticleTicketLinkService(articleRepository, linkRepository, ticketDirectory);
    }

    private Article article(String id, Article.ArticleStatus status) {
        return Article.builder().id(id).tenantId(TENANT).title("Como reconfigurar a VPN").status(status).build();
    }

    @Test
    void link_createsTheLink() {
        when(articleRepository.findByIdAndTenantId("a1", TENANT)).thenReturn(Optional.of(article("a1", Article.ArticleStatus.PUBLISHED)));
        when(ticketDirectory.find("t1", TENANT)).thenReturn(Optional.of(TICKET));
        when(linkRepository.findByTenantIdAndArticleIdAndTicketId(TENANT, "a1", "t1")).thenReturn(Optional.empty());
        when(linkRepository.save(any(ArticleTicketLink.class))).thenAnswer(inv -> inv.getArgument(0));

        LinkedTicketDto dto = service.link("a1", "t1", TENANT, "ana@x.com", true);

        assertThat(dto.getTicketNumber()).isEqualTo("NX-1042");
        assertThat(dto.getLinkedBy()).isEqualTo("ana@x.com");
    }

    @Test
    void link_isIdempotent() {
        when(articleRepository.findByIdAndTenantId("a1", TENANT)).thenReturn(Optional.of(article("a1", Article.ArticleStatus.PUBLISHED)));
        when(ticketDirectory.find("t1", TENANT)).thenReturn(Optional.of(TICKET));
        when(linkRepository.findByTenantIdAndArticleIdAndTicketId(TENANT, "a1", "t1")).thenReturn(Optional.of(
            ArticleTicketLink.builder().articleId("a1").ticketId("t1").linkedBy("bia@x.com").build()));

        LinkedTicketDto dto = service.link("a1", "t1", TENANT, "ana@x.com", true);

        assertThat(dto.getLinkedBy()).isEqualTo("bia@x.com");
        verify(linkRepository, never()).save(any());
    }

    @Test
    void link_refusesATicketOfAnotherTenant() {
        when(articleRepository.findByIdAndTenantId("a1", TENANT)).thenReturn(Optional.of(article("a1", Article.ArticleStatus.PUBLISHED)));
        when(ticketDirectory.find("t-foreign", TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.link("a1", "t-foreign", TENANT, "ana@x.com", true))
            .isInstanceOf(ResourceNotFoundException.class);
        verify(linkRepository, never()).save(any());
    }

    @Test
    void link_refusesAnArticleOfAnotherTenant() {
        when(articleRepository.findByIdAndTenantId("a-foreign", TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.link("a-foreign", "t1", TENANT, "ana@x.com", true))
            .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(ticketDirectory, linkRepository);
    }

    @Test
    void unpublishedArticles_areInvisibleToThoseWhoCannotManage() {
        when(articleRepository.findByIdAndTenantId("a1", TENANT)).thenReturn(Optional.of(article("a1", Article.ArticleStatus.DRAFT)));

        assertThatThrownBy(() -> service.link("a1", "t1", TENANT, "ana@x.com", false))
            .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.forArticle("a1", TENANT, false))
            .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(ticketDirectory, linkRepository);
    }

    @Test
    void unlink_removesAnExistingLink() {
        when(articleRepository.findByIdAndTenantId("a1", TENANT)).thenReturn(Optional.of(article("a1", Article.ArticleStatus.PUBLISHED)));
        ArticleTicketLink link = ArticleTicketLink.builder().articleId("a1").ticketId("t1").build();
        when(linkRepository.findByTenantIdAndArticleIdAndTicketId(TENANT, "a1", "t1")).thenReturn(Optional.of(link));

        service.unlink("a1", "t1", TENANT, true);

        verify(linkRepository).delete(link);
    }

    @Test
    void forTicket_hidesUnpublishedArticlesFromReaders_butEditorsSeeThem() {
        when(linkRepository.findByTenantIdAndTicketIdOrderByLinkedAtDesc(TENANT, "t1")).thenReturn(List.of(
            ArticleTicketLink.builder().articleId("pub").ticketId("t1").build(),
            ArticleTicketLink.builder().articleId("draft").ticketId("t1").build()));
        when(articleRepository.findByTenantIdAndIdIn(TENANT, List.of("pub", "draft"))).thenReturn(List.of(
            article("pub", Article.ArticleStatus.PUBLISHED), article("draft", Article.ArticleStatus.DRAFT)));

        assertThat(service.forTicket("t1", TENANT, false)).extracting(LinkedArticleDto::getArticleId).containsExactly("pub");
        assertThat(service.forTicket("t1", TENANT, true)).extracting(LinkedArticleDto::getArticleId)
            .containsExactlyInAnyOrder("pub", "draft");
    }

    @Test
    void forArticle_listsOnlyTicketsTheDirectoryStillKnows() {
        when(articleRepository.findByIdAndTenantId("a1", TENANT)).thenReturn(Optional.of(article("a1", Article.ArticleStatus.PUBLISHED)));
        when(linkRepository.findByTenantIdAndArticleIdOrderByLinkedAtDesc(TENANT, "a1")).thenReturn(List.of(
            ArticleTicketLink.builder().articleId("a1").ticketId("t1").build(),
            ArticleTicketLink.builder().articleId("a1").ticketId("gone").build()));
        when(ticketDirectory.findByIds(List.of("t1", "gone"), TENANT)).thenReturn(Map.of("t1", TICKET));

        assertThat(service.forArticle("a1", TENANT, true)).extracting(LinkedTicketDto::getTicketNumber)
            .containsExactly("NX-1042");
    }
}
