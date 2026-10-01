package com.nexusops.knowledge.controller;

import com.nexusops.knowledge.dto.CategoryDto;
import com.nexusops.knowledge.dto.CreateCategoryRequest;
import com.nexusops.knowledge.dto.UpdateCategoryRequest;
import com.nexusops.knowledge.service.KnowledgeCategoryService;
import com.nexusops.shared.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/knowledge-categories")
@RequiredArgsConstructor
@Tag(name = "Knowledge Categories", description = "Knowledge base category management endpoints")
public class KnowledgeCategoryController {

    private final KnowledgeCategoryService categoryService;
    private final SecurityUtils securityUtils;

    @PostMapping
    @PreAuthorize("hasPermission('KNOWLEDGE', 'CREATE')")
    @Operation(summary = "Create a new knowledge category")
    public ResponseEntity<CategoryDto> createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        String createdBy = securityUtils.getCurrentUserId().orElseThrow();
        return ResponseEntity.ok(categoryService.createCategory(request, tenantId, createdBy));
    }

    @GetMapping
    @PreAuthorize("hasPermission('KNOWLEDGE', 'READ')")
    @Operation(summary = "List all knowledge categories")
    public ResponseEntity<List<CategoryDto>> listCategories() {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        return ResponseEntity.ok(categoryService.findByTenantId(tenantId));
    }

    @GetMapping("/roots")
    @PreAuthorize("hasPermission('KNOWLEDGE', 'READ')")
    @Operation(summary = "List top-level knowledge categories")
    public ResponseEntity<List<CategoryDto>> listRoots() {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        return ResponseEntity.ok(categoryService.findRoots(tenantId));
    }

    @GetMapping("/{parentId}/children")
    @PreAuthorize("hasPermission('KNOWLEDGE', 'READ')")
    @Operation(summary = "List child categories")
    public ResponseEntity<List<CategoryDto>> listChildren(@PathVariable String parentId) {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        return ResponseEntity.ok(categoryService.findChildren(parentId, tenantId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('KNOWLEDGE', 'READ')")
    @Operation(summary = "Get category by ID")
    public ResponseEntity<CategoryDto> getCategory(@PathVariable String id) {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        return ResponseEntity.ok(categoryService.findByIdOrThrow(id, tenantId));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasPermission('KNOWLEDGE', 'UPDATE')")
    @Operation(summary = "Update category")
    public ResponseEntity<CategoryDto> updateCategory(@PathVariable String id, @Valid @RequestBody UpdateCategoryRequest request) {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        String updatedBy = securityUtils.getCurrentUserId().orElseThrow();
        return ResponseEntity.ok(categoryService.updateCategory(id, tenantId, request, updatedBy));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission('KNOWLEDGE', 'DELETE')")
    @Operation(summary = "Delete category")
    public ResponseEntity<Void> deleteCategory(@PathVariable String id) {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        categoryService.deleteCategory(id, tenantId);
        return ResponseEntity.ok().build();
    }
}
