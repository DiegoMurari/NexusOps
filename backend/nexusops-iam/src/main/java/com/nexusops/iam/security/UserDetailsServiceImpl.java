package com.nexusops.iam.security;

import com.nexusops.iam.domain.Role;
import com.nexusops.iam.domain.User;
import com.nexusops.iam.infrastructure.repository.RoleRepository;
import com.nexusops.iam.infrastructure.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

        Set<String> roles = user.getRoles() != null ? user.getRoles() : Set.of();
        Set<String> permissions = new HashSet<>();
        if (user.getPermissions() != null) {
            permissions.addAll(user.getPermissions());
        }
        for (String roleName : roles) {
            roleRepository.findByName(roleName)
                .map(Role::getPermissions)
                .ifPresent(permissions::addAll);
        }

        List<GrantedAuthority> authorities = new ArrayList<>();
        roles.forEach(r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r)));
        permissions.forEach(p -> authorities.add(new SimpleGrantedAuthority("PERM_" + p)));
        if (user.getTenantId() != null) {
            authorities.add(new SimpleGrantedAuthority("TENANT_" + user.getTenantId()));
        }

        return new NexusUserDetails(user, authorities);
    }

    @Transactional(readOnly = true)
    public User loadUserById(UUID id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + id));
    }

    public static class NexusUserDetails implements UserDetails {
        private final User user;
        private final Collection<? extends GrantedAuthority> authorities;

        public NexusUserDetails(User user, Collection<? extends GrantedAuthority> authorities) {
            this.user = user;
            this.authorities = authorities;
        }

        @Override
        public Collection<? extends GrantedAuthority> getAuthorities() {
            return authorities;
        }

        @Override
        public String getPassword() {
            return user.getPasswordHash();
        }

        @Override
        public String getUsername() {
            return user.getEmail();
        }

        @Override
        public boolean isAccountNonExpired() {
            return true;
        }

        @Override
        public boolean isAccountNonLocked() {
            return user.getStatus() == User.UserStatus.ACTIVE;
        }

        @Override
        public boolean isCredentialsNonExpired() {
            return true;
        }

        @Override
        public boolean isEnabled() {
            return user.getStatus() == User.UserStatus.ACTIVE;
        }

        public User getUser() {
            return user;
        }
    }
}