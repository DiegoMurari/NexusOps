package com.nexusops.ticketing.service;

import com.nexusops.ticketing.domain.Category;
import com.nexusops.ticketing.dto.CategoryDto;
import com.nexusops.ticketing.dto.CreateCategoryRequest;
import com.nexusops.ticketing.mapper.CategoryMapper;
import com.nexusops.ticketing.repository.CategoryRepository;
import com.nexusops.shared.exception.ResourceNotFoundException;
import com.nexusops.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    public CategoryDto createCategory(CreateCategoryRequest request, String createdBy) {
        if (categoryRepository.findByTenantIdAndName(request.getTenantId(), request.getName()).isPresent()) {
            throw new ValidationException("Category with name " + request.getName() + " already exists in tenant");
        }

        Category category = categoryMapper.toEntity(request);
        category.setCreatedBy(createdBy);
        category.setUpdatedBy(createdBy);

        return categoryMapper.toDto(categoryRepository.save(category));
    }

    @Transactional(readOnly = true)
    public List<CategoryDto> findByTenantId(String tenantId) {
        return categoryRepository.findByTenantId(tenantId).stream()
            .map(categoryMapper::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryDto> findRootCategories(String tenantId) {
        return categoryRepository.findByTenantIdAndParentIdIsNull(tenantId).stream()
            .map(categoryMapper::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryDto> findSubCategories(String parentId) {
        return categoryRepository.findByParentId(parentId).stream()
            .map(categoryMapper::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public Optional<CategoryDto> findById(String id) {
        return categoryRepository.findById(id).map(categoryMapper::toDto);
    }

    public CategoryDto updateCategory(String id, CategoryDto request, String updatedBy) {
        Category category = categoryRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Category", id));

        category.setName(request.getName());
        category.setDescription(request.getDescription());
        category.setParentId(request.getParentId());
        category.setIcon(request.getIcon());
        category.setColor(request.getColor());
        category.setSortOrder(request.getSortOrder());
        category.setActive(request.isActive());
        category.setSlaDefinitionId(request.getSlaDefinitionId());
        category.setUpdatedBy(updatedBy);
        category.setUpdatedAt(Instant.now());

        return categoryMapper.toDto(categoryRepository.save(category));
    }

    public void deleteCategory(String id) {
        Category category = categoryRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Category", id));

        if (!categoryRepository.findByParentId(id).isEmpty()) {
            throw new ValidationException("Cannot delete category with subcategories");
        }

        categoryRepository.delete(category);
    }
}