package com.nexusops.asset.service;

import com.nexusops.asset.domain.DiscoveryJob;
import com.nexusops.asset.dto.CreateDiscoveryJobRequest;
import com.nexusops.asset.dto.DiscoveryJobDto;
import com.nexusops.asset.mapper.DiscoveryJobMapper;
import com.nexusops.asset.repository.DiscoveryJobRepository;
import com.nexusops.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class DiscoveryJobService {

    private final DiscoveryJobRepository discoveryJobRepository;
    private final DiscoveryJobMapper discoveryJobMapper;

    public DiscoveryJobDto createJob(CreateDiscoveryJobRequest request, String tenantId, String createdBy) {
        DiscoveryJob job = discoveryJobMapper.toEntity(request);
        job.setTenantId(tenantId);
        job.setCreatedBy(createdBy);
        job.setUpdatedBy(createdBy);

        return discoveryJobMapper.toDto(discoveryJobRepository.save(job));
    }

    @Transactional(readOnly = true)
    public List<DiscoveryJobDto> findByTenantId(String tenantId) {
        return discoveryJobRepository.findByTenantId(tenantId).stream().map(discoveryJobMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public DiscoveryJobDto findByIdOrThrow(String id, String tenantId) {
        DiscoveryJob job = discoveryJobRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("DiscoveryJob", id));
        return discoveryJobMapper.toDto(job);
    }

    public void deleteJob(String id, String tenantId) {
        DiscoveryJob job = discoveryJobRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("DiscoveryJob", id));
        discoveryJobRepository.delete(job);
    }
}
