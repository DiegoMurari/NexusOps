package com.nexusops.iam.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;
import java.util.stream.Collectors;

/** The permission keys held by the current caller (PERM_ authorities, prefix stripped). */
public final class CallerPermissions {

    private static final String PREFIX = "PERM_";
    private static final String ROLE_PREFIX = "ROLE_";

    private CallerPermissions() {
    }

    /** The role names held by the current caller (ROLE_ authorities, prefix stripped). */
    public static Set<String> currentRoles() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return Set.of();
        }
        return auth.getAuthorities().stream()
            .map(a -> a.getAuthority())
            .filter(a -> a.startsWith(ROLE_PREFIX))
            .map(a -> a.substring(ROLE_PREFIX.length()))
            .collect(Collectors.toSet());
    }

    public static Set<String> current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return Set.of();
        }
        return auth.getAuthorities().stream()
            .map(a -> a.getAuthority())
            .filter(a -> a.startsWith(PREFIX))
            .map(a -> a.substring(PREFIX.length()))
            .collect(Collectors.toSet());
    }
}
