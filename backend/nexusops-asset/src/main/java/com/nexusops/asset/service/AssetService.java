package com.nexusops.asset.service;

import com.nexusops.asset.domain.Asset;
import com.nexusops.asset.dto.AssetDto;
import com.nexusops.asset.dto.CreateAssetRequest;
import com.nexusops.asset.dto.UpdateAssetRequest;
import com.nexusops.asset.mapper.AssetMapper;
import com.nexusops.asset.repository.AssetRepository;
import com.nexusops.shared.exception.ResourceNotFoundException;
import com.nexusops.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Transactional
public class AssetService {

    private final AssetRepository assetRepository;
    private final AssetMapper assetMapper;

    public AssetDto createAsset(CreateAssetRequest request, String tenantId, String createdBy) {
        if (request.getAssetTag() != null && !request.getAssetTag().isBlank()
            && assetRepository.findByAssetTagAndTenantId(request.getAssetTag(), tenantId).isPresent()) {
            throw new ValidationException("Asset with tag " + request.getAssetTag() + " already exists in tenant");
        }

        Asset asset = assetMapper.toEntity(request);
        asset.setTenantId(tenantId);
        asset.setLifecycleStatus(request.getLifecycleStatus() != null ? request.getLifecycleStatus() : Asset.LifecycleStatus.PROCURED);
        asset.setCreatedBy(createdBy);
        asset.setUpdatedBy(createdBy);

        return assetMapper.toDto(assetRepository.save(asset));
    }

    @Transactional(readOnly = true)
    public AssetDto findByIdOrThrow(String id, String tenantId) {
        Asset asset = assetRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Asset", id));
        return assetMapper.toDto(asset);
    }

    @Transactional(readOnly = true)
    public Page<AssetDto> list(String tenantId, Asset.AssetType type, Asset.LifecycleStatus status,
                                String assignedToId, String locationId, String search, Pageable pageable) {
        Page<Asset> page;
        if (search != null && !search.isBlank()) {
            page = assetRepository.search(tenantId, search.trim(), pageable);
        } else if (type != null) {
            page = assetRepository.findByTenantIdAndType(tenantId, type, pageable);
        } else if (status != null) {
            page = assetRepository.findByTenantIdAndLifecycleStatus(tenantId, status, pageable);
        } else if (assignedToId != null) {
            page = assetRepository.findByTenantIdAndAssignedToId(tenantId, assignedToId, pageable);
        } else if (locationId != null) {
            page = assetRepository.findByTenantIdAndLocationId(tenantId, locationId, pageable);
        } else {
            page = assetRepository.findByTenantId(tenantId, pageable);
        }
        return page.map(assetMapper::toDto);
    }

    public AssetDto updateAsset(String id, String tenantId, UpdateAssetRequest request, String updatedBy) {
        Asset asset = assetRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Asset", id));

        if (request.getName() != null) asset.setName(request.getName());
        if (request.getDescription() != null) asset.setDescription(request.getDescription());
        if (request.getType() != null) asset.setType(request.getType());
        if (request.getLifecycleStatus() != null) asset.setLifecycleStatus(request.getLifecycleStatus());
        if (request.getManufacturer() != null) asset.setManufacturer(request.getManufacturer());
        if (request.getModel() != null) asset.setModel(request.getModel());
        if (request.getSerialNumber() != null) asset.setSerialNumber(request.getSerialNumber());
        if (request.getSpecifications() != null) asset.setSpecifications(request.getSpecifications());
        if (request.getLocationId() != null) asset.setLocationId(request.getLocationId());
        if (request.getAssignedToId() != null) asset.setAssignedToId(request.getAssignedToId());
        if (request.getPurchaseDate() != null) asset.setPurchaseDate(request.getPurchaseDate());
        if (request.getWarrantyExpiration() != null) asset.setWarrantyExpiration(request.getWarrantyExpiration());
        if (request.getPurchaseCost() != null) asset.setPurchaseCost(request.getPurchaseCost());
        if (request.getDepreciationMethod() != null) asset.setDepreciationMethod(request.getDepreciationMethod());
        asset.setUpdatedBy(updatedBy);
        asset.setUpdatedAt(Instant.now());

        return assetMapper.toDto(assetRepository.save(asset));
    }

    public void deleteAsset(String id, String tenantId) {
        Asset asset = assetRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Asset", id));
        assetRepository.delete(asset);
    }

    @Transactional(readOnly = true)
    public long countByTenantId(String tenantId) {
        return assetRepository.countByTenantId(tenantId);
    }
}
