package com.nexusops.iam.service;

import com.nexusops.iam.domain.Role;
import com.nexusops.iam.domain.User;
import com.nexusops.iam.dto.CreateUserRequest;
import com.nexusops.iam.dto.UpdateProfileRequest;
import com.nexusops.iam.dto.UpdateUserRequest;
import com.nexusops.iam.dto.UserResponse;
import com.nexusops.iam.infrastructure.repository.RoleRepository;
import com.nexusops.iam.infrastructure.repository.UserRepository;
import com.nexusops.iam.mapper.UserMapper;
import com.nexusops.shared.directory.LocationDirectory;
import com.nexusops.shared.exception.ResourceNotFoundException;
import com.nexusops.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * User administration. The tenant always comes from the caller (never the request), every lookup is
 * tenant scoped, and assigned roles must exist, be visible to the tenant and not exceed the
 * permissions of whoever assigns them.
 *
 * <p>Administrative accounts are special (ADR-013): only a SUPER_ADMIN can create, change or remove
 * an ADMIN or SUPER_ADMIN, nobody changes their own roles or deletes themselves, and the tenant always
 * keeps at least one active super administrator.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    static final String DEFAULT_ROLE = "END_USER";
    static final String SUPER_ADMIN = "SUPER_ADMIN";
    static final Set<String> PRIVILEGED_ROLES = Set.of(SUPER_ADMIN, "ADMIN");

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final LocationDirectory locationDirectory;

    public UserResponse createUser(CreateUserRequest request, String createdBy, String tenantId,
                                   Set<String> callerPermissions, Set<String> callerRoles) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ValidationException("User with email " + request.getEmail() + " already exists");
        }

        // O privilégio vem antes do teto de permissões: quem não é super admin recebe 403, não um 422 confuso.
        requireSuperAdminForPrivileged(trimmed(request.getRoles()), callerRoles);
        Set<String> roles = validatedRoles(request.getRoles(), tenantId, callerPermissions);

        User user = userMapper.toEntity(request);
        user.setTenantId(tenantId);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setCreatedBy(createdBy);
        user.setUpdatedBy(createdBy);
        user.setRoles(roles);
        applyLocation(user, request.getDefaultLocationId(), tenantId);

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

    public UserResponse updateUser(UUID id, UpdateUserRequest request, String updatedBy, String tenantId,
                                   Set<String> callerPermissions, Set<String> callerRoles) {
        User user = findInTenant(id, tenantId);
        boolean self = isSelf(user, updatedBy);

        // Mexer na conta de um administrador exige super admin; o próprio usuário só edita o que não é papel.
        if (!self) {
            requireSuperAdminForPrivileged(user.getRoles(), callerRoles);
        }

        if (request.getRoles() != null) {
            changeRoles(user, request.getRoles(), tenantId, callerPermissions, callerRoles, self);
        }
        if (request.getStatus() != null) {
            changeStatus(user, request.getStatus(), tenantId, self);
        }

        userMapper.updateEntity(user, request);
        applyLocation(user, request.getDefaultLocationId(), tenantId);
        user.setTenantId(tenantId);
        user.setUpdatedBy(updatedBy);

        User saved = userRepository.save(user);
        return userMapper.toResponse(saved);
    }

    public void deleteUser(UUID id, String tenantId, String actor, Set<String> callerRoles) {
        User user = findInTenant(id, tenantId);
        if (isSelf(user, actor)) {
            throw new ValidationException("You cannot delete your own account");
        }
        requireSuperAdminForPrivileged(user.getRoles(), callerRoles);
        if (user.getRoles().contains(SUPER_ADMIN)) {
            requireAnotherActiveSuperAdmin(tenantId);
        }
        userRepository.delete(user);
    }

    /** The caller's own profile. Any authenticated user can read it. */
    @Transactional(readOnly = true)
    public UserResponse getProfile(String email, String tenantId) {
        return getUserByEmail(email, tenantId);
    }

    /** Self-service profile edit: names, contact, job data and default location. Never roles or status. */
    public UserResponse updateProfile(String email, UpdateProfileRequest request, String tenantId) {
        User user = userRepository.findByEmailAndTenantId(email, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("User", email));

        if (request.getFirstName() != null) user.setFirstName(request.getFirstName());
        if (request.getLastName() != null) user.setLastName(request.getLastName());
        if (request.getPhone() != null) user.setPhone(request.getPhone());
        if (request.getJobTitle() != null) user.setJobTitle(request.getJobTitle());
        if (request.getDepartment() != null) user.setDepartment(request.getDepartment());
        applyLocation(user, request.getDefaultLocationId(), tenantId);
        user.setUpdatedBy(email);

        return userMapper.toResponse(userRepository.save(user));
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

    private static boolean isSelf(User user, String actor) {
        return actor != null && user.getEmail() != null && user.getEmail().equalsIgnoreCase(actor);
    }

    private void changeRoles(User user, Set<String> requested, String tenantId, Set<String> callerPermissions,
                             Set<String> callerRoles, boolean self) {
        if (self) {
            throw new ValidationException("You cannot change your own roles");
        }
        requireSuperAdminForPrivileged(trimmed(requested), callerRoles);
        Set<String> names = validatedRoles(requested, tenantId, callerPermissions);
        if (user.getRoles().contains(SUPER_ADMIN) && !names.contains(SUPER_ADMIN)) {
            requireAnotherActiveSuperAdmin(tenantId);
        }
        user.setRoles(names);
    }

    private void changeStatus(User user, String requested, String tenantId, boolean self) {
        User.UserStatus status;
        try {
            status = User.UserStatus.valueOf(requested.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Unknown status: " + requested);
        }
        if (status != User.UserStatus.ACTIVE) {
            if (self) {
                throw new ValidationException("You cannot deactivate your own account");
            }
            if (user.getRoles().contains(SUPER_ADMIN)) {
                requireAnotherActiveSuperAdmin(tenantId);
            }
        }
    }

    private static Set<String> trimmed(Set<String> roles) {
        if (roles == null) {
            return Set.of();
        }
        return roles.stream().filter(r -> r != null).map(String::trim).collect(Collectors.toSet());
    }

    /** Only a SUPER_ADMIN can grant, change or remove an ADMIN or SUPER_ADMIN account. */
    private static void requireSuperAdminForPrivileged(Set<String> roles, Set<String> callerRoles) {
        boolean privileged = roles != null && roles.stream().anyMatch(PRIVILEGED_ROLES::contains);
        if (privileged && (callerRoles == null || !callerRoles.contains(SUPER_ADMIN))) {
            throw new AccessDeniedException(
                "Only a super administrator can create, change or remove ADMIN and SUPER_ADMIN accounts");
        }
    }

    private void requireAnotherActiveSuperAdmin(String tenantId) {
        if (userRepository.countActiveByRoleAndTenantId(SUPER_ADMIN, tenantId) <= 1) {
            throw new ValidationException("The tenant must keep at least one active super administrator");
        }
    }

    /** Null keeps the current value, blank clears it, anything else must be an active location of the tenant. */
    private void applyLocation(User user, String requested, String tenantId) {
        if (requested == null) {
            return;
        }
        if (requested.isBlank()) {
            user.setDefaultLocationId(null);
            return;
        }
        String id = requested.trim();
        if (!locationDirectory.isActiveInTenant(id, tenantId)) {
            throw new ValidationException("Unknown or inactive location: " + id);
        }
        user.setDefaultLocationId(id);
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
