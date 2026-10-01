package com.nexusops.shared.security;

import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class NexusPermissionEvaluator implements PermissionEvaluator {

    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        if (permission instanceof String permStr) {
            return evaluatePermission(authentication, permStr, targetDomainObject);
        }
        return false;
    }

    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId, String targetType, Object permission) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        if (permission instanceof String permStr) {
            return evaluatePermission(authentication, permStr, targetId, targetType);
        }
        return false;
    }

    private boolean evaluatePermission(Authentication authentication, String permission, Object target) {
        Set<String> userPermissions = authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .filter(a -> a.startsWith("PERM_"))
            .collect(Collectors.toSet());

        if (userPermissions.contains("PERM_*")) {
            return true;
        }

        // Direct match: caller already passed a full "resource:action:scope" (or similar) key.
        if (userPermissions.contains("PERM_" + permission)) {
            return true;
        }

        // Convention used by every @PreAuthorize("hasPermission('RESOURCE', 'ACTION')") call site:
        // target is the resource name and permission is the action. Real permission keys are
        // "resource:action:scope" (GLOBAL/TENANT/TEAM/OWN/PUBLIC); fine-grained row-level scoping
        // is enforced downstream by tenantId-filtered queries, so any scope for resource:action
        // is sufficient to pass this coarse-grained method-security gate.
        if (target instanceof String resource) {
            String prefix = "PERM_" + resource + ":" + permission + ":";
            if (userPermissions.stream().anyMatch(p -> p.startsWith(prefix))) {
                return true;
            }
        }

        if (target != null && hasScopePermission(authentication, userPermissions, permission, target)) {
            return true;
        }

        return false;
    }

    private boolean evaluatePermission(Authentication authentication, String permission, Object targetId, String targetType) {
        Set<String> userPermissions = authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .filter(a -> a.startsWith("PERM_"))
            .collect(Collectors.toSet());

        if (userPermissions.contains("PERM_*")) {
            return true;
        }

        if (userPermissions.contains("PERM_" + permission)) {
            return true;
        }

        if (targetType != null) {
            String prefix = "PERM_" + targetType + ":" + permission + ":";
            if (userPermissions.stream().anyMatch(p -> p.startsWith(prefix))) {
                return true;
            }
        }

        return false;
    }

    private boolean hasScopePermission(Authentication authentication, Set<String> userPermissions, String permission, Object target) {
        String[] parts = permission.split(":");
        if (parts.length < 2) {
            return false;
        }

        String resource = parts[0];
        String action = parts[1];

        String globalPerm = "PERM_" + resource + ":" + action + ":GLOBAL";
        if (userPermissions.contains(globalPerm)) {
            return true;
        }

        if (target instanceof HasOwner hasOwner) {
            String tenantPerm = "PERM_" + resource + ":" + action + ":TENANT";
            if (userPermissions.contains(tenantPerm) && hasOwner.getTenantId().equals(getTenantId(authentication))) {
                return true;
            }

            String teamPerm = "PERM_" + resource + ":" + action + ":TEAM";
            if (userPermissions.contains(teamPerm) && hasOwner.getTeamId().equals(getTeamId(authentication))) {
                return true;
            }

            String ownPerm = "PERM_" + resource + ":" + action + ":OWN";
            if (userPermissions.contains(ownPerm) && hasOwner.getOwnerId().equals(getUserId(authentication))) {
                return true;
            }
        }

        return false;
    }

    private String getUserId(Authentication authentication) {
        return authentication.getName();
    }

    private String getTenantId(Authentication authentication) {
        return authentication.getAuthorities().stream()
            .filter(a -> a.getAuthority().startsWith("TENANT_"))
            .map(a -> a.getAuthority().substring("TENANT_".length()))
            .findFirst()
            .orElse(null);
    }

    private String getTeamId(Authentication authentication) {
        return authentication.getAuthorities().stream()
            .filter(a -> a.getAuthority().startsWith("TEAM_"))
            .map(a -> a.getAuthority().substring("TEAM_".length()))
            .findFirst()
            .orElse(null);
    }

    public interface HasOwner {
        String getOwnerId();
        String getTenantId();
        String getTeamId();
    }
}