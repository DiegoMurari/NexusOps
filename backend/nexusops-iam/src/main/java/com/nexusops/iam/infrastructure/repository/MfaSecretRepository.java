package com.nexusops.iam.infrastructure.repository;

import com.nexusops.iam.domain.MfaSecret;
import com.nexusops.iam.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MfaSecretRepository extends JpaRepository<MfaSecret, UUID> {

    Optional<MfaSecret> findByUser(User user);

    Optional<MfaSecret> findByUserId(UUID userId);

    boolean existsByUser(User user);
}