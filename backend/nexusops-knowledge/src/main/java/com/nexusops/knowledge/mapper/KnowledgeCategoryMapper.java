package com.nexusops.knowledge.mapper;

import com.nexusops.knowledge.domain.KnowledgeCategory;
import com.nexusops.knowledge.dto.CategoryDto;
import com.nexusops.knowledge.dto.CreateCategoryRequest;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface KnowledgeCategoryMapper {

    CategoryDto toDto(KnowledgeCategory category);

    KnowledgeCategory toEntity(CreateCategoryRequest request);
}
