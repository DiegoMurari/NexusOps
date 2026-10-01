package com.nexusops.iam.service;

import com.nexusops.iam.domain.Permission;
import com.nexusops.iam.dto.PermissionDto;
import com.nexusops.iam.infrastructure.repository.PermissionRepository;
import com.nexusops.iam.mapper.PermissionMapper;
import com.nexusops.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PermissionService {

    private final PermissionRepository permissionRepository;
    private final PermissionMapper permissionMapper;

    public List<PermissionDto> findAll() {
        return permissionRepository.findAll().stream()
            .map(permissionMapper::toDto)
            .toList();
    }

    public List<PermissionDto> findByResource(String resource) {
        return permissionRepository.findByResource(resource).stream()
            .map(permissionMapper::toDto)
            .toList();
    }

    public List<PermissionDto> findByCategory(String category) {
        return permissionRepository.findByCategory(category).stream()
            .map(permissionMapper::toDto)
            .toList();
    }

    public Optional<PermissionDto> findByKey(String permissionKey) {
        return permissionRepository.findByPermissionKey(permissionKey)
            .map(permissionMapper::toDto);
    }

    public Optional<PermissionDto> findByResourceActionScope(String resource, String action, String scope) {
        return permissionRepository.findByResourceAndActionAndScope(resource, action, scope)
            .map(permissionMapper::toDto);
    }

    public List<PermissionDto> findByKeys(List<String> keys) {
        return permissionRepository.findByKeys(keys).stream()
            .map(permissionMapper::toDto)
            .toList();
    }

    @Transactional
    public PermissionDto createPermission(Permission permission) {
        return permissionMapper.toDto(permissionRepository.save(permission));
    }

    @Transactional
    public void deletePermission(UUID id) {
        if (!permissionRepository.existsById(id)) {
            throw new ResourceNotFoundException("Permission", id.toString());
        }
        permissionRepository.deleteById(id);
    }

    public boolean existsByKey(String permissionKey) {
        return permissionRepository.findByPermissionKey(permissionKey).isPresent();
    }
}
