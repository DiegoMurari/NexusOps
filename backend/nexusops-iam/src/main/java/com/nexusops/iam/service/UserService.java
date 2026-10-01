package com.nexusops.iam.service;

import com.nexusops.iam.domain.Role;
import com.nexusops.iam.domain.User;
import com.nexusops.iam.dto.CreateUserRequest;
import com.nexusops.iam.dto.UpdateUserRequest;
import com.nexusops.iam.dto.UserResponse;
import com.nexusops.iam.infrastructure.repository.RoleRepository;
import com.nexusops.iam.infrastructure.repository.UserRepository;
import com.nexusops.iam.mapper.UserMapper;
import com.nexusops.shared.exception.ResourceNotFoundException;
import com.nexusops.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * User administration. The tenant always comes from the caller (never the request), every lookup is
 * tenant scoped, and assigned roles must exist, be visible to the tenant and not exceed the
 * permissions of whoever assigns them.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    static final String DEFAULT_ROLE = "END_USER";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserResponse createUser(CreateUserRequest request, String createdBy, String tenantId,
                                   Set<String> callerPermissions) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ValidationException("User with email " + request.getEmail() + " already exists");
        }

        User user = userMapper.toEntity(request);
        user.setTenantId(tenantId);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setCreatedBy(createdBy);
        user.setUpdatedBy(createdBy);
        user.setRoles(validatedRoles(request.getRoles(), tenantId, callerPermissions));

        User saved = userRepository.save(user);
        return userMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID id, String tenantId) {
        return userMapper.toResponse(findInTenant(id, tenantId));
    }

    @Transactional(readOnly = true)
    public UserResponse getUserByEmail(String email, String tenantId) {
        User user = userRepository.findByEmailAndTenantId(email, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("User", email));
        return userMapper.toResponse(user);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getUsersByTenant(String tenantId) {
        return userRepository.findActiveByTenantId(tenantId).stream()
            .map(userMapper::toResponse)
            .collect(Collectors.toList());
    }

    public UserResponse updateUser(UUID id, UpdateUserRequest request, String updatedBy, String tenantId) {
        User user = findInTenant(id, tenantId);

        userMapper.updateEntity(user, request);
        user.setTenantId(tenantId);
        user.setUpdatedBy(updatedBy);

        User saved = userRepository.save(user);
        return userMapper.toResponse(saved);
    }

    public void deleteUser(UUID id, String tenantId) {
        userRepository.delete(findInTenant(id, tenantId));
    }

    public UserResponse changePassword(UUID id, String currentPassword, String newPassword, String tenantId) {
        User user = findInTenant(id, tenantId);

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ValidationException("Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        User saved = userRepository.save(user);
        return userMapper.toResponse(saved);
    }

    private User findInTenant(UUID id, String tenantId) {
        return userRepository.findById(id)
            .filter(u -> tenantId != null && tenantId.equals(u.getTenantId()))
            .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    /** Roles must exist for the tenant and every permission they carry must be held by the caller. */
    private Set<String> validatedRoles(Set<String> requested, String tenantId, Set<String> callerPermissions) {
        if (requested == null || requested.isEmpty()) {
            return new HashSet<>(Set.of(DEFAULT_ROLE));
        }
        Set<String> names = new HashSet<>();
        for (String requestedName : requested) {
            String name = requestedName == null ? "" : requestedName.trim();
            Role role = roleRepository.findByName(name)
                .filter(r -> Boolean.TRUE.equals(r.getIsSystem()) || tenantId.equals(r.getTenantId()))
                .orElseThrow(() -> new ValidationException("Unknown role: " + requestedName));
            if (!callerPermissions.containsAll(role.getPermissions())) {
                throw new ValidationException("You cannot assign a role with permissions you do not hold: " + name);
            }
            names.add(name);
        }
        return names;
    }
}
