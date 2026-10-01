package com.nexusops.knowledge.mapper;

import com.nexusops.knowledge.domain.Article;
import com.nexusops.knowledge.dto.ArticleDto;
import com.nexusops.knowledge.dto.CreateArticleRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ArticleMapper {

    @Mapping(target = "helpfulPercentage", expression = "java(article.getHelpfulPercentage())")
    ArticleDto toDto(Article article);

    Article toEntity(CreateArticleRequest request);
}
