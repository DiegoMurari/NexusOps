package com.nexusops.knowledge.controller;

import com.nexusops.knowledge.dto.CreateTagRequest;
import com.nexusops.knowledge.dto.TagDto;
import com.nexusops.knowledge.service.TagService;
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
@RequestMapping("/knowledge-tags")
@RequiredArgsConstructor
@Tag(name = "Knowledge Tags", description = "Knowledge base tag catalog endpoints")
public class TagController {

    private final TagService tagService;
    private final SecurityUtils securityUtils;

    @PostMapping
    @PreAuthorize("hasPermission('KNOWLEDGE', 'CREATE')")
    @Operation(summary = "Create a new tag")
    public ResponseEntity<TagDto> createTag(@Valid @RequestBody CreateTagRequest request) {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        return ResponseEntity.ok(tagService.createTag(request, tenantId));
    }

    @GetMapping
    @PreAuthorize("hasPermission('KNOWLEDGE', 'READ')")
    @Operation(summary = "List all tags")
    public ResponseEntity<List<TagDto>> listTags() {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        return ResponseEntity.ok(tagService.findByTenantId(tenantId));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission('KNOWLEDGE', 'DELETE')")
    @Operation(summary = "Delete tag")
    public ResponseEntity<Void> deleteTag(@PathVariable String id) {
        String tenantId = securityUtils.getCurrentTenantId().orElseThrow();
        tagService.deleteTag(id, tenantId);
        return ResponseEntity.ok().build();
    }
}
