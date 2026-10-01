package com.nexusops.knowledge.service;

import com.nexusops.knowledge.domain.Tag;
import com.nexusops.knowledge.dto.CreateTagRequest;
import com.nexusops.knowledge.dto.TagDto;
import com.nexusops.knowledge.mapper.TagMapper;
import com.nexusops.knowledge.repository.TagRepository;
import com.nexusops.shared.exception.ResourceNotFoundException;
import com.nexusops.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class TagService {

    private final TagRepository tagRepository;
    private final TagMapper tagMapper;

    public TagDto createTag(CreateTagRequest request, String tenantId) {
        if (tagRepository.findByNameAndTenantId(request.getName(), tenantId).isPresent()) {
            throw new ValidationException("Tag " + request.getName() + " already exists in tenant");
        }
        Tag tag = tagMapper.toEntity(request);
        tag.setTenantId(tenantId);
        return tagMapper.toDto(tagRepository.save(tag));
    }

    @Transactional(readOnly = true)
    public List<TagDto> findByTenantId(String tenantId) {
        return tagRepository.findByTenantId(tenantId).stream().map(tagMapper::toDto).toList();
    }

    public void deleteTag(String id, String tenantId) {
        Tag tag = tagRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Tag", id));
        tagRepository.delete(tag);
    }
}
