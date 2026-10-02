package com.nexusops.iam.service;

import com.nexusops.iam.domain.User;
import com.nexusops.iam.infrastructure.repository.UserRepository;
import com.nexusops.shared.directory.UserDirectory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.PageRequest;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserDirectoryService implements UserDirectory {

    private final UserRepository userRepository;

    @Override
    public Optional<UserRef> findActive(String userId, String tenantId) {
        return parse(userId)
            .flatMap(userRepository::findById)
            .filter(u -> tenantId != null && tenantId.equals(u.getTenantId()))
            .filter(u -> u.getStatus() == User.UserStatus.ACTIVE)
            .map(UserDirectoryService::toRef);
    }

    @Override
    public Optional<UserRef> findActiveByEmail(String email, String tenantId) {
        if (email == null || tenantId == null) {
            return Optional.empty();
        }
        return userRepository.findByEmailAndTenantId(email, tenantId)
            .filter(u -> u.getStatus() == User.UserStatus.ACTIVE)
            .map(UserDirectoryService::toRef);
    }

    @Override
    public List<UserRef> searchActive(String tenantId, String query, int limit) {
        if (tenantId == null || limit <= 0) {
            return List.of();
        }
        String text = query == null ? "" : query.trim().toLowerCase();
        String pattern = "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return userRepository.searchActive(tenantId, pattern, PageRequest.of(0, Math.min(limit, 50))).stream()
            .map(UserDirectoryService::toRef)
            .toList();
    }

    @Override
    public Map<String, UserRef> findByIds(Collection<String> userIds, String tenantId) {
        Map<String, UserRef> result = new HashMap<>();
        for (String id : userIds) {
            parse(id).flatMap(userRepository::findById)
                .filter(u -> tenantId != null && tenantId.equals(u.getTenantId()))
                .ifPresent(u -> result.put(id, toRef(u)));
        }
        return result;
    }

    private static UserRef toRef(User u) {
        String name = ((u.getFirstName() == null ? "" : u.getFirstName()) + " "
            + (u.getLastName() == null ? "" : u.getLastName())).trim();
        return new UserRef(u.getId().toString(), name.isEmpty() ? u.getEmail() : name, u.getEmail(),
            u.getStatus() == User.UserStatus.ACTIVE);
    }

    private static Optional<UUID> parse(String id) {
        try {
            return id == null ? Optional.empty() : Optional.of(UUID.fromString(id));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
