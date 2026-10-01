package com.nexusops.iam.service;

import com.nexusops.iam.domain.Permission;
import com.nexusops.iam.domain.Role;
import com.nexusops.iam.dto.CreateRoleRequest;
import com.nexusops.iam.dto.UpdateRoleRequest;
import com.nexusops.iam.dto.RoleResponse;
import com.nexusops.iam.infrastructure.repository.PermissionRepository;
import com.nexusops.iam.infrastructure.repository.RoleRepository;
import com.nexusops.iam.infrastructure.repository.UserRepository;
import com.nexusops.iam.mapper.RoleMapper;
import com.nexusops.shared.exception.ResourceNotFoundException;
import com.nexusops.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Role administration. Roles are resolved by name at login, so custom role names must be globally
 * unique. System roles are immutable, every operation is scoped to the caller's tenant, and a caller
 * can never grant a permission they do not hold themselves (no privilege escalation).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class RoleService {

    private static final Pattern ROLE_NAME = Pattern.compile("^[A-Z][A-Z0-9_]{1,49}$");

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final RoleMapper roleMapper;

    @Transactional(readOnly = true)
    public List<RoleResponse> listRoles(String tenantId) {
        return roleRepository.findVisible(tenantId).stream().map(roleMapper::toResponse).toList();
    }

    public RoleResponse createRole(CreateRoleRequest request, String tenantId, Set<String> callerPermissions) {
        String name = request.getName() == null ? "" : request.getName().trim().toUpperCase();
        if (!ROLE_NAME.matcher(name).matches()) {
            throw new ValidationException("Role name must be 2-50 characters: A-Z, 0-9 and underscore, starting with a letter");
        }
        if (roleRepository.findByName(name).isPresent()) {
            throw new ValidationException("Role with name " + name + " already exists");
        }
        Set<String> permissions = validatedPermissions(request.getPermissions(), callerPermissions);

        Role role = Role.builder()
            .name(name)
            .description(blankToNull(request.getDescription()))
            .tenantId(tenantId)
            .isSystem(false)
            .permissions(permissions)
            .build();
        return roleMapper.toResponse(roleRepository.save(role));
    }

    @Transactional(readOnly = true)
    public RoleResponse getRoleById(UUID id, String tenantId) {
        return roleMapper.toResponse(findVisible(id, tenantId));
    }

    @Transactional(readOnly = true)
    public RoleResponse getRoleByName(String name, String tenantId) {
        Role role = roleRepository.findByName(name)
            .filter(r -> isVisible(r, tenantId))
            .orElseThrow(() -> new ResourceNotFoundException("Role", name));
        return roleMapper.toResponse(role);
    }

    public RoleResponse updateRole(UUID id, UpdateRoleRequest request, String tenantId, Set<String> callerPermissions) {
        Role role = findModifiable(id, tenantId);
        if (request.getDescription() != null) {
            role.setDescription(blankToNull(request.getDescription()));
        }
        if (request.getPermissions() != null) {
            Set<String> permissions = validatedPermissions(request.getPermissions(), callerPermissions);
            role.getPermissions().clear();
            role.getPermissions().addAll(permissions);
        }
        return roleMapper.toResponse(roleRepository.save(role));
    }

    public void deleteRole(UUID id, String tenantId) {
        Role role = findModifiable(id, tenantId);
        if (userRepository.countByRole(role.getName()) > 0) {
            throw new ValidationException("Role is assigned to users and cannot be deleted");
        }
        roleRepository.delete(role);
    }

    public RoleResponse assignPermission(UUID roleId, String permission, String tenantId, Set<String> callerPermissions) {
        Role role = findModifiable(roleId, tenantId);
        role.getPermissions().addAll(validatedPermissions(Set.of(permission), callerPermissions));
        return roleMapper.toResponse(roleRepository.save(role));
    }

    public RoleResponse removePermission(UUID roleId, String permission, String tenantId) {
        Role role = findModifiable(roleId, tenantId);
        role.getPermissions().remove(permission);
        return roleMapper.toResponse(roleRepository.save(role));
    }

    private Role findVisible(UUID id, String tenantId) {
        return roleRepository.findById(id)
            .filter(r -> isVisible(r, tenantId))
            .orElseThrow(() -> new ResourceNotFoundException("Role", id));
    }

    private Role findModifiable(UUID id, String tenantId) {
        Role role = findVisible(id, tenantId);
        if (Boolean.TRUE.equals(role.getIsSystem())) {
            throw new ValidationException("System roles cannot be modified");
        }
        return role;
    }

    private static boolean isVisible(Role role, String tenantId) {
        return Boolean.TRUE.equals(role.getIsSystem()) || tenantId != null && tenantId.equals(role.getTenantId());
    }

    /** Keys must exist in the catalog and the caller must already hold each of them. */
    private Set<String> validatedPermissions(Set<String> requested, Set<String> callerPermissions) {
        if (requested == null || requested.isEmpty()) {
            return new HashSet<>();
        }
        Set<String> keys = new HashSet<>();
        for (String key : requested) {
            if (key == null || key.isBlank()) {
                throw new ValidationException("Permission must not be blank");
            }
            keys.add(key.trim());
        }
        Set<String> known = new HashSet<>();
        permissionRepository.findByKeys(List.copyOf(keys)).stream().map(Permission::getPermissionKey).forEach(known::add);
        for (String key : keys) {
            if (!known.contains(key)) {
                throw new ValidationException("Unknown permission: " + key);
            }
            if (!callerPermissions.contains(key)) {
                throw new ValidationException("You cannot grant a permission you do not hold: " + key);
            }
        }
        return keys;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
