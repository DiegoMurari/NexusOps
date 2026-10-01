package com.nexusops.knowledge.mapper;

import com.nexusops.knowledge.domain.ArticleFeedback;
import com.nexusops.knowledge.dto.ArticleFeedbackDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ArticleFeedbackMapper {

    ArticleFeedbackDto toDto(ArticleFeedback feedback);
}
