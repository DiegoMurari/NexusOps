package com.nexusops.knowledge.dto;

import com.nexusops.knowledge.domain.Article;
import lombok.*;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ArticleDto {

    private String id;
    private String title;
    private String slug;
    private String content;
    private String contentHtml;
    private String excerpt;
    private Article.ArticleStatus status;
    private String tenantId;
    private String categoryId;
    private String authorId;
    private int version;
    private Instant publishedAt;
    private String publishedBy;
    private String seoTitle;
    private String seoDescription;
    private String seoKeywords;
    private long viewCount;
    private long helpfulCount;
    private long notHelpfulCount;
    private double helpfulPercentage;
    private boolean featured;
    private boolean allowComments;
    private Set<String> tags;
    private Map<String, String> translations;
    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;
    private String updatedBy;
}
