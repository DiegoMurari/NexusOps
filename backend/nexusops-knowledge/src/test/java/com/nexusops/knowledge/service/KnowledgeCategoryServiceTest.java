package com.nexusops.knowledge.service;

import com.nexusops.knowledge.domain.KnowledgeCategory;
import com.nexusops.knowledge.dto.CreateCategoryRequest;
import com.nexusops.knowledge.dto.UpdateCategoryRequest;
import com.nexusops.knowledge.mapper.KnowledgeCategoryMapper;
import com.nexusops.knowledge.repository.KnowledgeCategoryRepository;
import com.nexusops.shared.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeCategoryServiceTest {

    @Mock
    private KnowledgeCategoryRepository categoryRepository;

    @Mock
    private KnowledgeCategoryMapper categoryMapper;

    private KnowledgeCategoryService categoryService;

    private static final String TENANT_ID = "tenant-1";

    @BeforeEach
    void setUp() {
        categoryService = new KnowledgeCategoryService(categoryRepository, categoryMapper);
    }

    @Test
    void createCategory_rejectsUnknownParent() {
        CreateCategoryRequest request = CreateCategoryRequest.builder().name("Sub").parentId("missing-parent").build();
        when(categoryRepository.findByIdAndTenantId("missing-parent", TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.createCategory(request, TENANT_ID, "user-1"))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    void updateCategory_rejectsSelfAsParent() {
        KnowledgeCategory category = KnowledgeCategory.builder().id("cat-1").tenantId(TENANT_ID).build();
        when(categoryRepository.findByIdAndTenantId("cat-1", TENANT_ID)).thenReturn(Optional.of(category));

        UpdateCategoryRequest request = UpdateCategoryRequest.builder().parentId("cat-1").build();

        assertThatThrownBy(() -> categoryService.updateCategory("cat-1", TENANT_ID, request, "user-1"))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    void deleteCategory_rejectsWhenArticlesAssigned() {
        KnowledgeCategory category = KnowledgeCategory.builder().id("cat-1").tenantId(TENANT_ID).articleCount(3).build();
        when(categoryRepository.findByIdAndTenantId("cat-1", TENANT_ID)).thenReturn(Optional.of(category));

        assertThatThrownBy(() -> categoryService.deleteCategory("cat-1", TENANT_ID))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("articles");
    }

    @Test
    void deleteCategory_rejectsWhenChildCategoriesExist() {
        KnowledgeCategory category = KnowledgeCategory.builder().id("cat-1").tenantId(TENANT_ID).articleCount(0).build();
        KnowledgeCategory child = KnowledgeCategory.builder().id("cat-2").tenantId(TENANT_ID).parentId("cat-1").build();
        when(categoryRepository.findByIdAndTenantId("cat-1", TENANT_ID)).thenReturn(Optional.of(category));
        when(categoryRepository.findByTenantIdAndParentId(TENANT_ID, "cat-1")).thenReturn(List.of(child));

        assertThatThrownBy(() -> categoryService.deleteCategory("cat-1", TENANT_ID))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("child");
    }

    @Test
    void deleteCategory_succeedsWhenEmptyAndChildless() {
        KnowledgeCategory category = KnowledgeCategory.builder().id("cat-1").tenantId(TENANT_ID).articleCount(0).build();
        when(categoryRepository.findByIdAndTenantId("cat-1", TENANT_ID)).thenReturn(Optional.of(category));
        when(categoryRepository.findByTenantIdAndParentId(TENANT_ID, "cat-1")).thenReturn(Collections.emptyList());

        categoryService.deleteCategory("cat-1", TENANT_ID);
    }
}
