package com.nexusops.knowledge.service;

import com.nexusops.knowledge.domain.KnowledgeCategory;
import com.nexusops.knowledge.dto.CategoryDto;
import com.nexusops.knowledge.dto.CreateCategoryRequest;
import com.nexusops.knowledge.dto.UpdateCategoryRequest;
import com.nexusops.knowledge.mapper.KnowledgeCategoryMapper;
import com.nexusops.knowledge.repository.KnowledgeCategoryRepository;
import com.nexusops.shared.exception.ResourceNotFoundException;
import com.nexusops.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class KnowledgeCategoryService {

    private final KnowledgeCategoryRepository categoryRepository;
    private final KnowledgeCategoryMapper categoryMapper;

    public CategoryDto createCategory(CreateCategoryRequest request, String tenantId, String createdBy) {
        if (request.getParentId() != null
            && categoryRepository.findByIdAndTenantId(request.getParentId(), tenantId).isEmpty()) {
            throw new ValidationException("Parent category " + request.getParentId() + " not found in tenant");
        }

        KnowledgeCategory category = categoryMapper.toEntity(request);
        category.setTenantId(tenantId);
        category.setSlug(generateUniqueSlug(request.getName(), tenantId));
        if (request.getSortOrder() != null) category.setSortOrder(request.getSortOrder());
        category.setCreatedBy(createdBy);
        category.setUpdatedBy(createdBy);

        return categoryMapper.toDto(categoryRepository.save(category));
    }

    @Transactional(readOnly = true)
    public List<CategoryDto> findByTenantId(String tenantId) {
        return categoryRepository.findByTenantId(tenantId).stream().map(categoryMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryDto> findRoots(String tenantId) {
        return categoryRepository.findByTenantIdAndParentIdIsNull(tenantId).stream().map(categoryMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryDto> findChildren(String parentId, String tenantId) {
        return categoryRepository.findByTenantIdAndParentId(tenantId, parentId).stream().map(categoryMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public CategoryDto findByIdOrThrow(String id, String tenantId) {
        KnowledgeCategory category = categoryRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Category", id));
        return categoryMapper.toDto(category);
    }

    public CategoryDto updateCategory(String id, String tenantId, UpdateCategoryRequest request, String updatedBy) {
        KnowledgeCategory category = categoryRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Category", id));

        if (request.getParentId() != null) {
            if (request.getParentId().equals(id)) {
                throw new ValidationException("Category cannot be its own parent");
            }
            if (categoryRepository.findByIdAndTenantId(request.getParentId(), tenantId).isEmpty()) {
                throw new ValidationException("Parent category " + request.getParentId() + " not found in tenant");
            }
            category.setParentId(request.getParentId());
        }
        if (request.getName() != null) category.setName(request.getName());
        if (request.getDescription() != null) category.setDescription(request.getDescription());
        if (request.getIcon() != null) category.setIcon(request.getIcon());
        if (request.getColor() != null) category.setColor(request.getColor());
        if (request.getSortOrder() != null) category.setSortOrder(request.getSortOrder());
        if (request.getActive() != null) category.setActive(request.getActive());
        category.setUpdatedBy(updatedBy);
        category.setUpdatedAt(Instant.now());

        return categoryMapper.toDto(categoryRepository.save(category));
    }

    public void deleteCategory(String id, String tenantId) {
        KnowledgeCategory category = categoryRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Category", id));

        if (category.getArticleCount() > 0) {
            throw new ValidationException("Cannot delete category with articles assigned to it");
        }
        if (!categoryRepository.findByTenantIdAndParentId(tenantId, id).isEmpty()) {
            throw new ValidationException("Cannot delete category with child categories");
        }

        categoryRepository.delete(category);
    }

    private String generateUniqueSlug(String name, String tenantId) {
        String base = name.toLowerCase()
            .replaceAll("[^a-z0-9\\s-]", "")
            .trim()
            .replaceAll("\\s+", "-");
        if (base.isBlank()) base = "categoria";

        String slug = base;
        int suffix = 2;
        while (categoryRepository.existsBySlugAndTenantId(slug, tenantId)) {
            slug = base + "-" + suffix++;
        }
        return slug;
    }
}
