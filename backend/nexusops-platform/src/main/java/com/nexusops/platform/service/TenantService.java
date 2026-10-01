package com.nexusops.platform.service;

import com.nexusops.platform.domain.Tenant;
import com.nexusops.platform.dto.CreateTenantRequest;
import com.nexusops.platform.dto.TenantDto;
import com.nexusops.platform.dto.UpdateTenantRequest;
import com.nexusops.platform.event.TenantCreatedEvent;
import com.nexusops.platform.mapper.TenantMapper;
import com.nexusops.platform.repository.TenantRepository;
import com.nexusops.shared.event.TransactionalEventPublisher;
import com.nexusops.shared.exception.ResourceNotFoundException;
import com.nexusops.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class TenantService {

    private final TenantRepository tenantRepository;
    private final TenantMapper tenantMapper;
    private final TransactionalEventPublisher eventPublisher;

    public TenantDto createTenant(CreateTenantRequest request, String createdBy) {
        if (tenantRepository.existsByDomain(request.getDomain())) {
            throw new ValidationException("Tenant with domain " + request.getDomain() + " already exists");
        }

        Tenant tenant = tenantMapper.toEntity(request);
        tenant.setCreatedBy(createdBy);
        tenant.setUpdatedBy(createdBy);

        Tenant saved = tenantRepository.save(tenant);

        eventPublisher.publishAfterCommit(new TenantCreatedEvent(
            saved.getId(), saved.getVersion(),
            saved.getName(), saved.getDomain(), saved.getSubscriptionTier()
        ));

        return tenantMapper.toDto(saved);
    }

    @Transactional(readOnly = true)
    public Optional<TenantDto> findById(String id) {
        return tenantRepository.findById(id).map(tenantMapper::toDto);
    }

    @Transactional(readOnly = true)
    public Optional<TenantDto> findByDomain(String domain) {
        return tenantRepository.findByDomain(domain).map(tenantMapper::toDto);
    }

    @Transactional(readOnly = true)
    public List<TenantDto> findAll() {
        return tenantRepository.findAll().stream()
            .map(tenantMapper::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<TenantDto> findByStatus(Tenant.TenantStatus status) {
        return tenantRepository.findByStatus(status).stream()
            .map(tenantMapper::toDto)
            .toList();
    }

    public TenantDto updateTenant(String id, UpdateTenantRequest request, String updatedBy) {
        Tenant tenant = tenantRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Tenant", id));

        if (request.getName() != null) tenant.setName(request.getName());
        if (request.getStatus() != null) tenant.setStatus(request.getStatus());
        if (request.getSubscriptionTier() != null) tenant.setSubscriptionTier(request.getSubscriptionTier());
        if (request.getSettings() != null) tenant.setSettings(request.getSettings());
        if (request.getMaxUsers() > 0) tenant.setMaxUsers(request.getMaxUsers());
        if (request.getMaxAssets() > 0) tenant.setMaxAssets(request.getMaxAssets());
        if (request.getContactEmail() != null) tenant.setContactEmail(request.getContactEmail());
        if (request.getContactName() != null) tenant.setContactName(request.getContactName());

        tenant.setUpdatedBy(updatedBy);
        tenant.setUpdatedAt(Instant.now());

        return tenantMapper.toDto(tenantRepository.save(tenant));
    }

    public void deleteTenant(String id) {
        Tenant tenant = tenantRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Tenant", id));

        tenant.setStatus(Tenant.TenantStatus.DELETED);
        tenant.setDomain("deleted_" + tenant.getId() + "_" + tenant.getDomain());
        tenantRepository.save(tenant);
    }

    @Transactional(readOnly = true)
    public boolean existsByDomain(String domain) {
        return tenantRepository.existsByDomain(domain);
    }
}