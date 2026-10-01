package com.nexusops.asset.service;

import com.nexusops.asset.domain.CIRelationship;
import com.nexusops.asset.dto.CIRelationshipDto;
import com.nexusops.asset.dto.CreateCIRelationshipRequest;
import com.nexusops.asset.mapper.CIRelationshipMapper;
import com.nexusops.asset.repository.AssetRepository;
import com.nexusops.asset.repository.CIRelationshipRepository;
import com.nexusops.shared.exception.ResourceNotFoundException;
import com.nexusops.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class CIRelationshipService {

    private final CIRelationshipRepository relationshipRepository;
    private final AssetRepository assetRepository;
    private final CIRelationshipMapper relationshipMapper;

    public CIRelationshipDto createRelationship(CreateCIRelationshipRequest request, String tenantId, String createdBy) {
        if (request.getSourceId().equals(request.getTargetId())) {
            throw new ValidationException("An asset cannot have a relationship with itself");
        }
        assetRepository.findByIdAndTenantId(request.getSourceId(), tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Asset", request.getSourceId()));
        assetRepository.findByIdAndTenantId(request.getTargetId(), tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Asset", request.getTargetId()));

        CIRelationship relationship = relationshipMapper.toEntity(request);
        relationship.setTenantId(tenantId);
        relationship.setCreatedBy(createdBy);

        return relationshipMapper.toDto(relationshipRepository.save(relationship));
    }

    @Transactional(readOnly = true)
    public List<CIRelationshipDto> findByAssetId(String assetId, String tenantId) {
        return relationshipRepository.findByTenantIdAndAssetId(tenantId, assetId).stream()
            .map(relationshipMapper::toDto)
            .toList();
    }

    public void deleteRelationship(String id, String tenantId) {
        CIRelationship relationship = relationshipRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("CIRelationship", id));
        if (!relationship.getTenantId().equals(tenantId)) {
            throw new ResourceNotFoundException("CIRelationship", id);
        }
        relationshipRepository.delete(relationship);
    }
}
