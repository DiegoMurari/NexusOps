package com.nexusops.iam.service;

import com.nexusops.iam.domain.RefreshToken;
import com.nexusops.iam.domain.Role;
import com.nexusops.iam.domain.User;
import com.nexusops.iam.dto.AuthRequest;
import com.nexusops.iam.dto.AuthResponse;
import com.nexusops.iam.dto.MfaVerifyRequest;
import com.nexusops.iam.dto.RefreshTokenRequest;
import com.nexusops.iam.event.LoginAttemptedEvent;
import com.nexusops.iam.infrastructure.repository.RefreshTokenRepository;
import com.nexusops.iam.infrastructure.repository.RoleRepository;
import com.nexusops.iam.infrastructure.repository.UserRepository;
import com.nexusops.shared.exception.ResourceNotFoundException;
import com.nexusops.shared.exception.ValidationException;
import com.nexusops.shared.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final MfaService mfaService;
    private final LoginAttemptService loginAttemptService;
    private final ApplicationEventPublisher eventPublisher;

    public AuthResponse login(AuthRequest request) {
        User user = userRepository.findByEmail(request.getUsername())
            .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) {
            publishLogin(user, false, "ACCOUNT_LOCKED");
            throw new LockedException("Account is temporarily locked due to too many failed login attempts");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            loginAttemptService.registerFailedLogin(user);
            publishLogin(user, false, "BAD_CREDENTIALS");
            throw new BadCredentialsException("Invalid credentials");
        }

        if (user.getStatus() != User.UserStatus.ACTIVE) {
            publishLogin(user, false, "ACCOUNT_INACTIVE");
            throw new ValidationException("Account is not active");
        }

        if (user.getMfaEnabled() && request.getMfaCode() == null) {
            throw new ValidationException("MFA_REQUIRED");
        }

        if (user.getMfaEnabled() && request.getMfaCode() != null) {
            if (!mfaService.verifyTotp(user, request.getMfaCode())) {
                loginAttemptService.registerFailedLogin(user);
                publishLogin(user, false, "BAD_MFA_CODE");
                throw new ValidationException("Invalid MFA code");
            }
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);

        publishLogin(user, true, null);
        return generateAuthResponse(user);
    }

    private void publishLogin(User user, boolean success, String reason) {
        eventPublisher.publishEvent(new LoginAttemptedEvent(user.getTenantId(), user.getEmail(), success, reason));
    }

    public AuthResponse verifyMfa(MfaVerifyRequest request) {
        // This would be used for MFA challenge flow
        throw new UnsupportedOperationException("Use login with mfaCode");
    }

    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String rawToken = request.getRefreshToken();
        if (!jwtTokenProvider.validateToken(rawToken)) {
            throw new ValidationException("Invalid refresh token");
        }

        if (!jwtTokenProvider.isRefreshToken(rawToken)) {
            throw new ValidationException("Token is not a refresh token");
        }

        String jti = jwtTokenProvider.getJti(rawToken);
        RefreshToken stored = refreshTokenRepository.findByJti(jti)
            .orElseThrow(() -> new ValidationException("Refresh token has been revoked or does not exist"));
        if (!stored.isActive()) {
            throw new ValidationException("Refresh token has been revoked or expired");
        }

        String username = jwtTokenProvider.getUsername(rawToken);
        User user = userRepository.findByEmail(username)
            .orElseThrow(() -> new ResourceNotFoundException("User", username));

        // Rotation: the old refresh token is single-use.
        refreshTokenRepository.revokeByJti(jti, Instant.now());

        return generateAuthResponse(user);
    }

    public void logout(String refreshToken) {
        if (refreshToken == null || !jwtTokenProvider.validateToken(refreshToken)) {
            return;
        }
        String jti = jwtTokenProvider.getJti(refreshToken);
        refreshTokenRepository.revokeByJti(jti, Instant.now());
    }

    public void logoutAll(String username) {
        User user = userRepository.findByEmail(username)
            .orElseThrow(() -> new ResourceNotFoundException("User", username));
        refreshTokenRepository.revokeAllForUser(user.getId(), Instant.now());
    }

    private AuthResponse generateAuthResponse(User user) {
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

        String accessToken = jwtTokenProvider.generateAccessToken(
            org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .authorities(roles.stream().map(r -> (org.springframework.security.core.GrantedAuthority) () -> "ROLE_" + r).toArray(org.springframework.security.core.GrantedAuthority[]::new))
                .build(),
            permissions,
            user.getTenantId()
        );

        String refreshToken = jwtTokenProvider.generateRefreshToken(
            org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .authorities(roles.stream().map(r -> (org.springframework.security.core.GrantedAuthority) () -> "ROLE_" + r).toArray(org.springframework.security.core.GrantedAuthority[]::new))
                .build(),
            user.getTenantId()
        );

        String refreshJti = jwtTokenProvider.getJti(refreshToken);
        refreshTokenRepository.save(RefreshToken.builder()
            .user(user)
            .jti(refreshJti)
            .expiresAt(jwtTokenProvider.getExpiration(refreshToken))
            .build());

        user.updateLastLogin();
        userRepository.save(user);

        return AuthResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .expiresIn(jwtTokenProvider.getAccessTokenExpirationMs() / 1000)
            .tokenType("Bearer")
            .user(AuthResponse.UserInfo.builder()
                .id(user.getId().toString())
                .username(user.getEmail())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .roles(roles)
                .permissions(permissions)
                .mfaEnabled(user.getMfaEnabled())
                .tenantId(user.getTenantId())
                .build())
            .build();
    }
}