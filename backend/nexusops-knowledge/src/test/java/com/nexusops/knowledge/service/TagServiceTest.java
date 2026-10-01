package com.nexusops.knowledge.service;

import com.nexusops.knowledge.domain.Tag;
import com.nexusops.knowledge.dto.CreateTagRequest;
import com.nexusops.knowledge.dto.TagDto;
import com.nexusops.knowledge.mapper.TagMapper;
import com.nexusops.knowledge.repository.TagRepository;
import com.nexusops.shared.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TagServiceTest {

    @Mock
    private TagRepository tagRepository;

    @Mock
    private TagMapper tagMapper;

    private TagService tagService;

    private static final String TENANT_ID = "tenant-1";

    @BeforeEach
    void setUp() {
        tagService = new TagService(tagRepository, tagMapper);
    }

    @Test
    void createTag_rejectsDuplicateNameWithinTenant() {
        when(tagRepository.findByNameAndTenantId("vpn", TENANT_ID))
            .thenReturn(Optional.of(Tag.builder().id("tag-1").name("vpn").tenantId(TENANT_ID).build()));

        CreateTagRequest request = CreateTagRequest.builder().name("vpn").build();

        assertThatThrownBy(() -> tagService.createTag(request, TENANT_ID))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    void createTag_savesWhenNameIsUnique() {
        when(tagRepository.findByNameAndTenantId("rede", TENANT_ID)).thenReturn(Optional.empty());
        when(tagMapper.toEntity(any(CreateTagRequest.class))).thenReturn(new Tag());
        when(tagRepository.save(any(Tag.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tagMapper.toDto(any(Tag.class))).thenReturn(TagDto.builder().build());

        CreateTagRequest request = CreateTagRequest.builder().name("rede").build();
        tagService.createTag(request, TENANT_ID);
    }
}
