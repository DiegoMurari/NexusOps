package com.nexusops.ticketing.controller;

import com.nexusops.shared.security.SecurityUtils;
import com.nexusops.ticketing.dto.CatalogAreaDto;
import com.nexusops.ticketing.dto.CatalogAreaRequest;
import com.nexusops.ticketing.dto.CatalogTopicDto;
import com.nexusops.ticketing.dto.CatalogTopicRequest;
import com.nexusops.ticketing.dto.PortalCatalogDto;
import com.nexusops.ticketing.service.CatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/catalog")
@RequiredArgsConstructor
@Tag(name = "Catalog", description = "Service catalog: areas and topics")
public class CatalogController {

    private static final String ADMINS = "hasAnyRole('SUPER_ADMIN','ADMIN')";

    private final CatalogService catalogService;
    private final SecurityUtils securityUtils;

    /** O catálogo como o solicitante o vê. Qualquer usuário autenticado. */
    @GetMapping("/portal")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Catalog for the requester Portal (no queues, priorities or SLAs)")
    public ResponseEntity<PortalCatalogDto> portal() {
        return ResponseEntity.ok(catalogService.portal(tenant()));
    }

    @GetMapping("/areas")
    @PreAuthorize(ADMINS)
    public ResponseEntity<List<CatalogAreaDto>> areas() {
        return ResponseEntity.ok(catalogService.listAreas(tenant()));
    }

    @PostMapping("/areas")
    @PreAuthorize(ADMINS)
    public ResponseEntity<CatalogAreaDto> createArea(@Valid @RequestBody CatalogAreaRequest request) {
        return ResponseEntity.ok(catalogService.createArea(request, currentUser(), tenant()));
    }

    @PatchMapping("/areas/{id}")
    @PreAuthorize(ADMINS)
    public ResponseEntity<CatalogAreaDto> updateArea(@PathVariable String id, @Valid @RequestBody CatalogAreaRequest request) {
        return ResponseEntity.ok(catalogService.updateArea(id, request, currentUser(), tenant()));
    }

    @DeleteMapping("/areas/{id}")
    @PreAuthorize(ADMINS)
    public ResponseEntity<Void> deleteArea(@PathVariable String id) {
        catalogService.deleteArea(id, tenant());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/topics")
    @PreAuthorize(ADMINS)
    public ResponseEntity<List<CatalogTopicDto>> topics(@RequestParam(required = false) String areaId) {
        return ResponseEntity.ok(catalogService.listTopics(tenant(), areaId));
    }

    @PostMapping("/topics")
    @PreAuthorize(ADMINS)
    public ResponseEntity<CatalogTopicDto> createTopic(@Valid @RequestBody CatalogTopicRequest request) {
        return ResponseEntity.ok(catalogService.createTopic(request, currentUser(), tenant()));
    }

    @PatchMapping("/topics/{id}")
    @PreAuthorize(ADMINS)
    @Operation(summary = "Edit a topic (null keeps; blank clears queue, priority, SLA and category)")
    public ResponseEntity<CatalogTopicDto> updateTopic(@PathVariable String id, @Valid @RequestBody CatalogTopicRequest request) {
        return ResponseEntity.ok(catalogService.updateTopic(id, request, currentUser(), tenant()));
    }

    @DeleteMapping("/topics/{id}")
    @PreAuthorize(ADMINS)
    public ResponseEntity<Void> deleteTopic(@PathVariable String id) {
        catalogService.deleteTopic(id, tenant());
        return ResponseEntity.noContent().build();
    }

    private String tenant() {
        return securityUtils.getCurrentTenantId().orElseThrow();
    }

    private String currentUser() {
        return securityUtils.getCurrentUserId().orElseThrow();
    }
}
