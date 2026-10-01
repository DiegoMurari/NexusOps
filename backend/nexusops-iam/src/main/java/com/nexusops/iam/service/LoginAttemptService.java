package com.nexusops.iam.service;

import com.nexusops.iam.domain.User;
import com.nexusops.iam.infrastructure.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Persists failed-login/lockout state in its own transaction (REQUIRES_NEW)
 * so the counter survives even though the caller (AuthService.login) is
 * about to throw and roll back its own transaction. A plain call from
 * within AuthService would bypass Spring's AOP proxy (self-invocation) and
 * silently ignore @Transactional, hence this is a separate bean.
 */
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCKOUT_MINUTES = 15;

    private final UserRepository userRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registerFailedLogin(User user) {
        int attempts = (user.getFailedLoginAttempts() != null ? user.getFailedLoginAttempts() : 0) + 1;
        user.setFailedLoginAttempts(attempts);
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            user.setLockedUntil(Instant.now().plus(LOCKOUT_MINUTES, ChronoUnit.MINUTES));
        }
        userRepository.save(user);
    }
}
