package com.nexusops.shared.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Exposed as a Spring bean so it can be constructor-injected where existing
 * controllers already expect an instance (e.g. {@code private final SecurityUtils securityUtils;}
 * via {@code @RequiredArgsConstructor}); methods stay static so direct static
 * usage (SecurityUtils.getCurrentUserId()) keeps working too.
 */
@Component
public final class SecurityUtils {

    public static Optional<Authentication> getAuthentication() {
        SecurityContext context = SecurityContextHolder.getContext();
        return Optional.ofNullable(context.getAuthentication())
            .filter(Authentication::isAuthenticated);
    }

    public static Optional<UserDetails> getUserDetails() {
        return getAuthentication()
            .map(Authentication::getPrincipal)
            .filter(UserDetails.class::isInstance)
            .map(UserDetails.class::cast);
    }

    public static Optional<String> getCurrentUsername() {
        return getUserDetails().map(UserDetails::getUsername);
    }

    public static Set<String> getCurrentUserRoles() {
        return getAuthentication()
            .map(a -> a.getAuthorities().stream()
                .map(Object::toString)
                .filter(r -> r.startsWith("ROLE_"))
                .collect(Collectors.toSet()))
            .orElse(Set.of());
    }

    public static Set<String> getCurrentUserPermissions() {
        return getAuthentication()
            .map(a -> a.getAuthorities().stream()
                .map(Object::toString)
                .filter(p -> p.startsWith("PERM_"))
                .collect(Collectors.toSet()))
            .orElse(Set.of());
    }

    public static boolean hasRole(String role) {
        return getCurrentUserRoles().contains("ROLE_" + role);
    }

    public static boolean hasPermission(String permission) {
        return getCurrentUserPermissions().contains("PERM_" + permission);
    }

    public static boolean hasAnyRole(String... roles) {
        return getCurrentUserRoles().stream()
            .anyMatch(r -> {
                for (String role : roles) {
                    if (r.equals("ROLE_" + role)) return true;
                }
                return false;
            });
    }

    public static boolean hasAnyPermission(String... permissions) {
        return getCurrentUserPermissions().stream()
            .anyMatch(p -> {
                for (String perm : permissions) {
                    if (p.equals("PERM_" + perm)) return true;
                }
                return false;
            });
    }

    public static boolean isCurrentUser(String username) {
        return getCurrentUsername().map(username::equals).orElse(false);
    }

    public static Optional<String> getCurrentUserId() {
        return getAuthentication()
            .map(Authentication::getName);
    }

    public static Optional<String> getCurrentTenantId() {
        return getAuthentication()
            .map(auth -> auth.getAuthorities().stream()
                .filter(a -> a.getAuthority().startsWith("TENANT_"))
                .map(a -> a.getAuthority().substring("TENANT_".length()))
                .findFirst()
                .orElse(null));
    }
}