package com.nexusops.asset.service;

import com.nexusops.asset.domain.Asset;
import com.nexusops.asset.domain.AssetHistory;
import com.nexusops.asset.domain.Location;
import com.nexusops.asset.repository.LocationRepository;
import com.nexusops.shared.directory.LocationDirectory;
import com.nexusops.shared.directory.UserDirectory;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional
public class AssetService {

    private final AssetRepository assetRepository;
    private final AssetMapper assetMapper;
    private final AssetHistoryService historyService;
    private final UserDirectory userDirectory;
    private final LocationDirectory locationDirectory;
    private final LocationRepository locationRepository;

    public AssetDto createAsset(CreateAssetRequest request, String tenantId, String createdBy) {
        if (request.getAssetTag() != null && !request.getAssetTag().isBlank()
            && assetRepository.findByAssetTagAndTenantId(request.getAssetTag(), tenantId).isPresent()) {
            throw new ValidationException("Asset with tag " + request.getAssetTag() + " already exists in tenant");
        }
        requireReferences(request.getAssignedToId(), request.getLocationId(), tenantId);

        Asset asset = assetMapper.toEntity(request);
        asset.setTenantId(tenantId);
        asset.setLifecycleStatus(request.getLifecycleStatus() != null ? request.getLifecycleStatus() : Asset.LifecycleStatus.PROCURED);
        asset.setCreatedBy(createdBy);
        asset.setUpdatedBy(createdBy);

        Asset saved = assetRepository.save(asset);
        historyService.record(tenantId, saved.getId(), AssetHistory.CREATED, null, null, saved.getName(), createdBy);
        return assetMapper.toDto(saved);
    }

    @Transactional(readOnly = true)
    public AssetDto findByIdOrThrow(String id, String tenantId) {
        Asset asset = assetRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Asset", id));
        AssetDto dto = assetMapper.toDto(asset);
        dto.setAssignedToName(userLabel(asset.getAssignedToId(), tenantId));
        dto.setLocationName(locationLabel(asset.getLocationId(), tenantId));
        return dto;
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
        requireReferences(request.getAssignedToId(), request.getLocationId(), tenantId);
        Map<String, String> before = snapshot(asset, tenantId);

        if (request.getName() != null) asset.setName(request.getName());
        if (request.getDescription() != null) asset.setDescription(request.getDescription());
        if (request.getType() != null) asset.setType(request.getType());
        if (request.getLifecycleStatus() != null) asset.setLifecycleStatus(request.getLifecycleStatus());
        if (request.getManufacturer() != null) asset.setManufacturer(request.getManufacturer());
        if (request.getModel() != null) asset.setModel(request.getModel());
        if (request.getSerialNumber() != null) asset.setSerialNumber(request.getSerialNumber());
        if (request.getSpecifications() != null) asset.setSpecifications(request.getSpecifications());
        // String vazia limpa o campo; ausente (null) mantém o valor atual.
        if (request.getLocationId() != null) asset.setLocationId(request.getLocationId().isBlank() ? null : request.getLocationId());
        if (request.getAssignedToId() != null) asset.setAssignedToId(request.getAssignedToId().isBlank() ? null : request.getAssignedToId());
        if (request.getPurchaseDate() != null) asset.setPurchaseDate(request.getPurchaseDate());
        if (request.getWarrantyExpiration() != null) asset.setWarrantyExpiration(request.getWarrantyExpiration());
        if (request.getPurchaseCost() != null) asset.setPurchaseCost(request.getPurchaseCost());
        if (request.getDepreciationMethod() != null) asset.setDepreciationMethod(request.getDepreciationMethod());
        asset.setUpdatedBy(updatedBy);
        asset.setUpdatedAt(Instant.now());

        Asset saved = assetRepository.save(asset);
        Map<String, String> after = snapshot(saved, tenantId);
        after.forEach((field, newValue) -> {
            String oldValue = before.get(field);
            if (!Objects.equals(oldValue, newValue)) {
                historyService.record(tenantId, saved.getId(), AssetHistory.FIELD_CHANGED, field, oldValue, newValue, updatedBy);
            }
        });
        return assetMapper.toDto(saved);
    }

    /** Candidatos a responsável: usuários ativos do tenant, por nome ou e-mail (no máximo 10). */
    @Transactional(readOnly = true)
    public List<UserDirectory.UserRef> assignableUsers(String tenantId, String query) {
        return userDirectory.searchActive(tenantId, query, 10);
    }

    /** Responsável e localidade, quando informados, precisam existir e estar ativos no tenant. */
    private void requireReferences(String assignedToId, String locationId, String tenantId) {
        if (assignedToId != null && !assignedToId.isBlank() && userDirectory.findActive(assignedToId, tenantId).isEmpty()) {
            throw new ValidationException("Responsável não encontrado ou inativo neste tenant");
        }
        if (locationId != null && !locationId.isBlank() && !locationDirectory.isActiveInTenant(locationId, tenantId)) {
            throw new ValidationException("Localidade não encontrada ou inativa neste tenant");
        }
    }

    /** Valores legíveis dos campos que importam no histórico (nomes no lugar de identificadores). */
    private Map<String, String> snapshot(Asset a, String tenantId) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("name", a.getName());
        values.put("description", a.getDescription());
        values.put("type", a.getType() == null ? null : a.getType().name());
        values.put("lifecycleStatus", a.getLifecycleStatus() == null ? null : a.getLifecycleStatus().name());
        values.put("manufacturer", a.getManufacturer());
        values.put("model", a.getModel());
        values.put("serialNumber", a.getSerialNumber());
        values.put("location", locationLabel(a.getLocationId(), tenantId));
        values.put("assignedTo", userLabel(a.getAssignedToId(), tenantId));
        values.put("purchaseDate", a.getPurchaseDate() == null ? null : a.getPurchaseDate().toString());
        values.put("warrantyExpiration", a.getWarrantyExpiration() == null ? null : a.getWarrantyExpiration().toString());
        values.put("purchaseCost", a.getPurchaseCost() == null ? null : a.getPurchaseCost().toPlainString());
        values.put("depreciationMethod", a.getDepreciationMethod() == null ? null : a.getDepreciationMethod().toString());
        return values;
    }

    private String userLabel(String userId, String tenantId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        UserDirectory.UserRef ref = userDirectory.findByIds(List.of(userId), tenantId).get(userId);
        return ref != null && ref.name() != null && !ref.name().isBlank() ? ref.name() : userId;
    }

    private String locationLabel(String locationId, String tenantId) {
        if (locationId == null || locationId.isBlank()) {
            return null;
        }
        return locationRepository.findByIdAndTenantId(locationId, tenantId).map(Location::getName).orElse(locationId);
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
