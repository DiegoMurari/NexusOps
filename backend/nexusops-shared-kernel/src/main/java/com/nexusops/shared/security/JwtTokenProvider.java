package com.nexusops.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class JwtTokenProvider {

    @Value("${nexusops.jwt.access-token-expiration-ms:900000}")
    private long accessTokenExpirationMs;

    @Value("${nexusops.jwt.refresh-token-expiration-ms:604800000}")
    private long refreshTokenExpirationMs;

    @Value("${nexusops.jwt.issuer:nexusops}")
    private String issuer;

    @Value("${nexusops.jwt.private-key:}")
    private String privateKeyPem;

    @Value("${nexusops.jwt.public-key:}")
    private String publicKeyPem;

    private final Environment environment;

    private Key signingKey;
    private SecretKey verificationKey;

    public JwtTokenProvider(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void init() {
        if (privateKeyPem != null && !privateKeyPem.isBlank()) {
            this.signingKey = Keys.hmacShaKeyFor(privateKeyPem.getBytes(StandardCharsets.UTF_8));
        } else if (environment.matchesProfiles("dev", "test")) {
            this.signingKey = Keys.secretKeyFor(io.jsonwebtoken.SignatureAlgorithm.HS256);
        } else {
            throw new IllegalStateException(
                "nexusops.jwt.private-key (JWT_PRIVATE_KEY) must be set outside the dev/test profiles; "
                    + "refusing to boot with a randomly generated signing key.");
        }
        if (publicKeyPem != null && !publicKeyPem.isBlank()) {
            this.verificationKey = Keys.hmacShaKeyFor(publicKeyPem.getBytes(StandardCharsets.UTF_8));
        } else {
            this.verificationKey = (SecretKey) this.signingKey;
        }
    }

    public String generateAccessToken(UserDetails userDetails, Set<String> permissions, String tenantId) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(accessTokenExpirationMs);

        return Jwts.builder()
            .setSubject(userDetails.getUsername())
            .setIssuer(issuer)
            .setAudience("nexusops-api")
            .setId(UUID.randomUUID().toString())
            .setIssuedAt(Date.from(now))
            .setExpiration(Date.from(expiry))
            .claim("tenant_id", tenantId)
            .claim("permissions", permissions)
            .claim("roles", userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(r -> r.startsWith("ROLE_"))
                .collect(Collectors.toSet()))
            .claim("token_type", "access")
            .signWith(signingKey, io.jsonwebtoken.SignatureAlgorithm.HS256)
            .compact();
    }

    public String generateRefreshToken(UserDetails userDetails, String tenantId) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(refreshTokenExpirationMs);

        return Jwts.builder()
            .setSubject(userDetails.getUsername())
            .setIssuer(issuer)
            .setAudience("nexusops-api")
            .setId(UUID.randomUUID().toString())
            .setIssuedAt(Date.from(now))
            .setExpiration(Date.from(expiry))
            .claim("tenant_id", tenantId)
            .claim("token_type", "refresh")
            .signWith(signingKey, io.jsonwebtoken.SignatureAlgorithm.HS256)
            .compact();
    }

    public Jws<Claims> parseToken(String token) {
        return Jwts.parser()
            .verifyWith(verificationKey)
            .requireIssuer(issuer)
            .requireAudience("nexusops-api")
            .build()
            .parseSignedClaims(token);
    }

    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Claims getClaims(String token) {
        return parseToken(token).getBody();
    }

    public String getUsername(String token) {
        return getClaims(token).getSubject();
    }

    public String getJti(String token) {
        return getClaims(token).getId();
    }

    public String getTenantId(String token) {
        return getClaims(token).get("tenant_id", String.class);
    }

    public Set<String> getPermissions(String token) {
        List<String> perms = getClaims(token).get("permissions", List.class);
        return perms != null ? Set.copyOf(perms) : Set.of();
    }

    public boolean isAccessToken(String token) {
        return "access".equals(getClaims(token).get("token_type", String.class));
    }

    public boolean isRefreshToken(String token) {
        return "refresh".equals(getClaims(token).get("token_type", String.class));
    }

    public Instant getExpiration(String token) {
        return getClaims(token).getExpiration().toInstant();
    }

    public long getAccessTokenExpirationMs() {
        return accessTokenExpirationMs;
    }

    public long getRefreshTokenExpirationMs() {
        return refreshTokenExpirationMs;
    }
}