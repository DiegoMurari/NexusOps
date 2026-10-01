package com.nexusops.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateArticleRequest {

    @NotBlank
    @Size(max = 500)
    private String title;

    private String slug;

    private String content;

    private String contentHtml;

    @Size(max = 2000)
    private String excerpt;

    private String categoryId;

    @Size(max = 255)
    private String seoTitle;

    @Size(max = 500)
    private String seoDescription;

    @Size(max = 500)
    private String seoKeywords;

    private Boolean featured;

    private Boolean allowComments;

    private Set<String> tags;
}
