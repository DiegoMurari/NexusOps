package com.nexusops.asset.service;

import com.nexusops.asset.domain.Location;
import com.nexusops.asset.dto.CreateLocationRequest;
import com.nexusops.asset.dto.LocationDto;
import com.nexusops.asset.mapper.LocationMapper;
import com.nexusops.asset.repository.LocationRepository;
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
public class LocationService {

    private final LocationRepository locationRepository;
    private final LocationMapper locationMapper;

    public LocationDto createLocation(CreateLocationRequest request, String tenantId, String createdBy) {
        if (request.getParentId() != null
            && locationRepository.findByIdAndTenantId(request.getParentId(), tenantId).isEmpty()) {
            throw new ValidationException("Parent location not found in tenant: " + request.getParentId());
        }

        Location location = locationMapper.toEntity(request);
        location.setTenantId(tenantId);
        location.setType(request.getType() != null ? request.getType() : Location.LocationType.SITE);
        location.setCreatedBy(createdBy);
        location.setUpdatedBy(createdBy);

        return locationMapper.toDto(locationRepository.save(location));
    }

    @Transactional(readOnly = true)
    public List<LocationDto> findByTenantId(String tenantId) {
        return locationRepository.findByTenantId(tenantId).stream().map(locationMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<LocationDto> findRoots(String tenantId) {
        return locationRepository.findByTenantIdAndParentIdIsNull(tenantId).stream().map(locationMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<LocationDto> findChildren(String parentId) {
        return locationRepository.findByParentId(parentId).stream().map(locationMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public LocationDto findByIdOrThrow(String id, String tenantId) {
        Location location = locationRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Location", id));
        return locationMapper.toDto(location);
    }

    public LocationDto updateLocation(String id, String tenantId, CreateLocationRequest request, String updatedBy) {
        Location location = locationRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Location", id));

        if (id.equals(request.getParentId())) {
            throw new ValidationException("A location cannot be its own parent");
        }

        location.setName(request.getName());
        location.setDescription(request.getDescription());
        location.setParentId(request.getParentId());
        if (request.getType() != null) location.setType(request.getType());
        location.setAddress(request.getAddress());
        location.setCoordinates(request.getCoordinates());
        location.setUpdatedBy(updatedBy);
        location.setUpdatedAt(Instant.now());

        return locationMapper.toDto(locationRepository.save(location));
    }

    public void deleteLocation(String id, String tenantId) {
        Location location = locationRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Location", id));
        if (!locationRepository.findByParentId(id).isEmpty()) {
            throw new ValidationException("Cannot delete location with child locations");
        }
        locationRepository.delete(location);
    }
}
