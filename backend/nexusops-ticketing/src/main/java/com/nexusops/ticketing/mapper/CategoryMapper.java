package com.nexusops.ticketing.mapper;

import com.nexusops.ticketing.domain.Category;
import com.nexusops.ticketing.dto.CategoryDto;
import com.nexusops.ticketing.dto.CreateCategoryRequest;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface CategoryMapper {

    CategoryMapper INSTANCE = Mappers.getMapper(CategoryMapper.class);

    CategoryDto toDto(Category category);

    Category toEntity(CreateCategoryRequest request);
}