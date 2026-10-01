package com.nexusops.ticketing.mapper;

import com.nexusops.ticketing.domain.Comment;
import com.nexusops.ticketing.dto.CommentDto;
import com.nexusops.ticketing.dto.CreateCommentRequest;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface CommentMapper {

    CommentMapper INSTANCE = Mappers.getMapper(CommentMapper.class);

    CommentDto toDto(Comment comment);

    Comment toEntity(CreateCommentRequest request);
}