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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArticleServiceTest {

    @Mock
    private ArticleRepository articleRepository;

    @Mock
    private KnowledgeCategoryRepository categoryRepository;

    @Mock
    private ArticleMapper articleMapper;

    private ArticleService articleService;

    private static final String TENANT_ID = "tenant-1";

    @BeforeEach
    void setUp() {
        articleService = new ArticleService(articleRepository, categoryRepository, articleMapper);
    }

    @Test
    void createArticle_generatesUniqueSlugFromTitle() {
        CreateArticleRequest request = CreateArticleRequest.builder()
            .title("Como Resetar a VPN")
            .build();

        when(articleRepository.existsBySlugAndTenantId("como-resetar-a-vpn", TENANT_ID)).thenReturn(false);
        when(articleMapper.toEntity(request)).thenReturn(new Article());
        when(articleRepository.save(any(Article.class))).thenAnswer(inv -> inv.getArgument(0));
        when(articleMapper.toDto(any(Article.class))).thenReturn(ArticleDto.builder().build());

        articleService.createArticle(request, TENANT_ID, "user-1");

        ArgumentCaptor<Article> captor = ArgumentCaptor.forClass(Article.class);
        verify(articleRepository).save(captor.capture());
        assertThat(captor.getValue().getSlug()).isEqualTo("como-resetar-a-vpn");
        assertThat(captor.getValue().getStatus()).isEqualTo(Article.ArticleStatus.DRAFT);
    }

    @Test
    void createArticle_appendsSuffixWhenSlugCollides() {
        CreateArticleRequest request = CreateArticleRequest.builder()
            .title("Guia VPN")
            .build();

        when(articleRepository.existsBySlugAndTenantId("guia-vpn", TENANT_ID)).thenReturn(true);
        when(articleRepository.existsBySlugAndTenantId("guia-vpn-2", TENANT_ID)).thenReturn(false);
        when(articleMapper.toEntity(request)).thenReturn(new Article());
        when(articleRepository.save(any(Article.class))).thenAnswer(inv -> inv.getArgument(0));
        when(articleMapper.toDto(any(Article.class))).thenReturn(ArticleDto.builder().build());

        articleService.createArticle(request, TENANT_ID, "user-1");

        ArgumentCaptor<Article> captor = ArgumentCaptor.forClass(Article.class);
        verify(articleRepository).save(captor.capture());
        assertThat(captor.getValue().getSlug()).isEqualTo("guia-vpn-2");
    }

    @Test
    void createArticle_incrementsCategoryArticleCount() {
        CreateArticleRequest request = CreateArticleRequest.builder()
            .title("Artigo")
            .categoryId("cat-1")
            .build();

        KnowledgeCategory category = KnowledgeCategory.builder().id("cat-1").tenantId(TENANT_ID).articleCount(2).build();
        when(categoryRepository.findByIdAndTenantId("cat-1", TENANT_ID)).thenReturn(Optional.of(category));
        when(articleRepository.existsBySlugAndTenantId(anyString(), eq(TENANT_ID))).thenReturn(false);
        when(articleMapper.toEntity(request)).thenReturn(new Article());
        when(articleRepository.save(any(Article.class))).thenAnswer(inv -> inv.getArgument(0));
        when(articleMapper.toDto(any(Article.class))).thenReturn(ArticleDto.builder().build());

        articleService.createArticle(request, TENANT_ID, "user-1");

        ArgumentCaptor<KnowledgeCategory> captor = ArgumentCaptor.forClass(KnowledgeCategory.class);
        verify(categoryRepository).save(captor.capture());
        assertThat(captor.getValue().getArticleCount()).isEqualTo(3);
    }

    @Test
    void updateArticle_movesCategoryCountsWhenCategoryChanges() {
        Article article = Article.builder()
            .id("art-1")
            .tenantId(TENANT_ID)
            .categoryId("cat-old")
            .version(1)
            .build();
        KnowledgeCategory oldCategory = KnowledgeCategory.builder().id("cat-old").tenantId(TENANT_ID).articleCount(1).build();
        KnowledgeCategory newCategory = KnowledgeCategory.builder().id("cat-new").tenantId(TENANT_ID).articleCount(4).build();

        when(articleRepository.findByIdAndTenantId("art-1", TENANT_ID)).thenReturn(Optional.of(article));
        when(categoryRepository.findByIdAndTenantId("cat-new", TENANT_ID)).thenReturn(Optional.of(newCategory));
        when(categoryRepository.findByIdAndTenantId("cat-old", TENANT_ID)).thenReturn(Optional.of(oldCategory));
        when(articleRepository.save(any(Article.class))).thenAnswer(inv -> inv.getArgument(0));
        when(articleMapper.toDto(any(Article.class))).thenReturn(ArticleDto.builder().build());

        UpdateArticleRequest request = UpdateArticleRequest.builder().categoryId("cat-new").build();
        articleService.updateArticle("art-1", TENANT_ID, request, "user-1");

        assertThat(oldCategory.getArticleCount()).isEqualTo(0);
        assertThat(newCategory.getArticleCount()).isEqualTo(5);
    }

    @Test
    void publish_setsStatusPublishedAtAndPublishedBy() {
        Article article = Article.builder().id("art-1").tenantId(TENANT_ID).status(Article.ArticleStatus.DRAFT).build();
        when(articleRepository.findByIdAndTenantId("art-1", TENANT_ID)).thenReturn(Optional.of(article));
        when(articleRepository.save(any(Article.class))).thenAnswer(inv -> inv.getArgument(0));
        when(articleMapper.toDto(any(Article.class))).thenReturn(ArticleDto.builder().build());

        articleService.publish("art-1", TENANT_ID, "publisher-1");

        assertThat(article.getStatus()).isEqualTo(Article.ArticleStatus.PUBLISHED);
        assertThat(article.getPublishedBy()).isEqualTo("publisher-1");
        assertThat(article.getPublishedAt()).isNotNull();
    }

    @Test
    void deleteArticle_decrementsCategoryArticleCount() {
        Article article = Article.builder().id("art-1").tenantId(TENANT_ID).categoryId("cat-1").build();
        KnowledgeCategory category = KnowledgeCategory.builder().id("cat-1").tenantId(TENANT_ID).articleCount(1).build();

        when(articleRepository.findByIdAndTenantId("art-1", TENANT_ID)).thenReturn(Optional.of(article));
        when(categoryRepository.findByIdAndTenantId("cat-1", TENANT_ID)).thenReturn(Optional.of(category));

        articleService.deleteArticle("art-1", TENANT_ID);

        assertThat(category.getArticleCount()).isEqualTo(0);
        verify(articleRepository).delete(article);
    }

    @Test
    void findByIdOrThrow_throwsWhenNotFoundInTenant() {
        when(articleRepository.findByIdAndTenantId("missing", TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> articleService.findByIdOrThrow("missing", TENANT_ID))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getAndRecordView_incrementsViewCount() {
        Article article = Article.builder().id("art-1").tenantId(TENANT_ID).viewCount(5).build();
        when(articleRepository.findByIdAndTenantId("art-1", TENANT_ID)).thenReturn(Optional.of(article));
        when(articleRepository.save(any(Article.class))).thenAnswer(inv -> inv.getArgument(0));
        when(articleMapper.toDto(any(Article.class))).thenReturn(ArticleDto.builder().build());

        articleService.getAndRecordView("art-1", TENANT_ID);

        assertThat(article.getViewCount()).isEqualTo(6);
    }
}
