package com.nexusops.iam.infrastructure.repository;

import com.nexusops.iam.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByJti(String jti);

    @Modifying
    @Query("UPDATE RefreshToken r SET r.revokedAt = :now WHERE r.jti = :jti AND r.revokedAt IS NULL")
    void revokeByJti(@Param("jti") String jti, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE RefreshToken r SET r.revokedAt = :now WHERE r.user.id = :userId AND r.revokedAt IS NULL")
    void revokeAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);
}
