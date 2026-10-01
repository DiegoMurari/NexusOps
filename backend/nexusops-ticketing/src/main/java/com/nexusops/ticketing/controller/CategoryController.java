package com.nexusops.ticketing.controller;

import com.nexusops.ticketing.dto.CategoryDto;
import com.nexusops.ticketing.dto.CreateCategoryRequest;
import com.nexusops.ticketing.service.CategoryService;
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
@RequestMapping("/categories")
@RequiredArgsConstructor
@Tag(name = "Categories", description = "Ticket category management endpoints")
public class CategoryController {

    private final CategoryService categoryService;
    private final SecurityUtils securityUtils;

    @PostMapping
    @PreAuthorize("hasPermission('TICKET', 'CREATE')")
    @Operation(summary = "Create a new category")
    public ResponseEntity<CategoryDto> createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        String createdBy = securityUtils.getCurrentUserId().orElseThrow();
        return ResponseEntity.ok(categoryService.createCategory(request, createdBy));
    }

    @GetMapping
    @PreAuthorize("hasPermission('TICKET', 'READ')")
    @Operation(summary = "List categories for current tenant")
    public ResponseEntity<List<CategoryDto>> listCategories() {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        return ResponseEntity.ok(categoryService.findByTenantId(tenantId));
    }

    @GetMapping("/roots")
    @PreAuthorize("hasPermission('TICKET', 'READ')")
    @Operation(summary = "List root categories")
    public ResponseEntity<List<CategoryDto>> listRootCategories() {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        return ResponseEntity.ok(categoryService.findRootCategories(tenantId));
    }

    @GetMapping("/{parentId}/children")
    @PreAuthorize("hasPermission('TICKET', 'READ')")
    @Operation(summary = "List subcategories")
    public ResponseEntity<List<CategoryDto>> listSubCategories(@PathVariable String parentId) {
        return ResponseEntity.ok(categoryService.findSubCategories(parentId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('TICKET', 'READ')")
    @Operation(summary = "Get category by ID")
    public ResponseEntity<CategoryDto> getCategory(@PathVariable String id) {
        return categoryService.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasPermission('TICKET', 'UPDATE')")
    @Operation(summary = "Update category")
    public ResponseEntity<CategoryDto> updateCategory(
            @PathVariable String id,
            @RequestBody CategoryDto request) {
        String updatedBy = securityUtils.getCurrentUserId().orElseThrow();
        return ResponseEntity.ok(categoryService.updateCategory(id, request, updatedBy));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission('TICKET', 'DELETE')")
    @Operation(summary = "Delete category")
    public ResponseEntity<Void> deleteCategory(@PathVariable String id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.ok().build();
    }
}