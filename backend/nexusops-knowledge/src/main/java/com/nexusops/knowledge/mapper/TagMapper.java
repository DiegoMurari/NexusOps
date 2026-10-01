package com.nexusops.knowledge.mapper;

import com.nexusops.knowledge.domain.Tag;
import com.nexusops.knowledge.dto.CreateTagRequest;
import com.nexusops.knowledge.dto.TagDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TagMapper {

    TagDto toDto(Tag tag);

    Tag toEntity(CreateTagRequest request);
}
