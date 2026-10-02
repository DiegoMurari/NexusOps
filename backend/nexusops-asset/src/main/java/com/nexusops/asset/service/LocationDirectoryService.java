package com.nexusops.asset.service;

import com.nexusops.asset.domain.Location;
import com.nexusops.asset.repository.LocationRepository;
import com.nexusops.shared.directory.LocationDirectory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LocationDirectoryService implements LocationDirectory {

    private final LocationRepository locationRepository;

    @Override
    public boolean isActiveInTenant(String locationId, String tenantId) {
        if (locationId == null || tenantId == null) {
            return false;
        }
        return locationRepository.findByIdAndTenantId(locationId, tenantId)
            .map(Location::getActive)
            .orElse(false);
    }
}
