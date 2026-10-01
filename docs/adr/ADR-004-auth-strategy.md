# ADR-004: Authentication & Authorization Strategy

## Status
Accepted

## Context
NexusOps requires:
- Secure authentication for employees, customers, partners
- Multi-factor authentication (MFA)
- Role-based (RBAC) and attribute-based (ABAC) access control
- Token-based stateless authentication
- OAuth 2.1 / OIDC compliance for future integrations
- Session management with refresh token rotation
- Audit trail for security events

## Decision
Implement **JWT RS256** with **Embedded Spring Authorization Server** for OAuth 2.1/OIDC, **MFA TOTP**, and **RBAC/ABAC** hybrid authorization.

## Authentication

### JWT Access Tokens (RS256)
- **Algorithm**: RS256 (asymmetric, public key verification)
- **Expiration**: 15 minutes
- **Claims**:
  ```json
  {
    "sub": "user@example.com",
    "iss": "nexusops",
    "aud": "nexusops-api",
    "jti": "unique-token-id",
    "iat": 1704067200,
    "exp": 1704068100,
    "tenant_id": "tenant-123",
    "permissions": ["TICKET:READ:TENANT", "TICKET:WRITE:OWN"],
    "roles": ["ROLE_AGENT", "ROLE_TEAM_LEAD"],
    "token_type": "access"
  }
  ```

### JWT Refresh Tokens (Rotating)
- **Expiration**: 7 days
- **Rotation**: New refresh token issued on each use
- **Theft Detection**: If same refresh token used twice → revoke all user sessions
- **Storage**: HTTP-only Secure SameSite=Strict cookie + DB hash

### Token Revocation
- **Access Tokens**: Short-lived (15min), rely on expiration
- **Refresh Tokens**: Blacklist in Redis (immediate revocation)
- **Logout All**: Increment user `tokenVersion` claim, invalidate all tokens

### Login Flow
```
1. POST /auth/login { username, password, mfaCode?, rememberMe? }
2. Validate credentials
3. If MFA enabled & no code → 401 with "MFA_REQUIRED"
4. If MFA code provided → verify TOTP
5. Generate access + refresh tokens
6. Store refresh token hash in DB
7. Return tokens + user info
```

### MFA TOTP (RFC 6238)
- **Algorithm**: HMAC-SHA1, 6 digits, 30-second window
- **Setup**: QR code (otpauth://), secret encrypted at rest
- **Recovery Codes**: 10 single-use codes, bcrypt hashed
- **WebAuthn**: Scaffold for future (FIDO2)

## Authorization

### Permission Model
```
RESOURCE:ACTION:SCOPE
```

| Resource | Actions | Scopes |
|----------|---------|--------|
| TICKET | CREATE, READ, UPDATE, DELETE, TRANSITION, ASSIGN, COMMENT | OWN, TEAM, TENANT, GLOBAL |
| ASSET | CREATE, READ, UPDATE, DELETE, RELATE | TENANT, GLOBAL |
| KNOWLEDGE | CREATE, READ, UPDATE, DELETE, PUBLISH, REVIEW | TENANT, GLOBAL |
| SLA | CREATE, READ, UPDATE, DELETE | TENANT, GLOBAL |
| REPORT | CREATE, READ, EXECUTE, EXPORT | TENANT, GLOBAL |
| ADMIN | USER_MGMT, TENANT_MGMT, SETTINGS, AUDIT | GLOBAL |

### Default Roles
| Role | Permissions | Use Case |
|------|-------------|----------|
| END_USER | TICKET:CREATE:OWN, TICKET:READ:OWN, TICKET:COMMENT:OWN, KNOWLEDGE:READ:TENANT | Requesters |
| AGENT | TICKET:*:TEAM, KNOWLEDGE:READ:TENANT, ASSET:READ:TENANT | Support agents |
| TEAM_LEAD | AGENT + TICKET:ASSIGN:TEAM, REPORT:READ:TEAM | Team leads |
| MANAGER | TEAM_LEAD + TICKET:*:TENANT, SLA:READ:TENANT, REPORT:*:TENANT | Managers |
| ADMIN | All except SUPER_ADMIN | Platform admins |
| SUPER_ADMIN | *:* | System administrators |

### Scope Hierarchy
```
OWN ⊂ TEAM ⊂ TENANT ⊂ GLOBAL
```

### ABAC Implementation
```java
@PreAuthorize("hasPermission(#ticket, 'TICKET:UPDATE:OWN')")
public TicketDto updateTicket(Long id, TicketUpdateRequest request);

@Component
public class NexusPermissionEvaluator implements PermissionEvaluator {
    @Override
    public boolean hasPermission(Authentication auth, Object target, Object permission) {
        if (target instanceof HasOwner owner) {
            String userId = auth.getName();
            String userTenant = getTenantId(auth);
            String userTeam = getTeamId(auth);
            
            // Check OWN scope
            if (permission.endsWith(":OWN")) {
                return owner.getOwnerId().equals(userId);
            }
            // Check TEAM scope
            if (permission.endsWith(":TEAM")) {
                return owner.getTeamId().equals(userTeam);
            }
            // Check TENANT scope
            if (permission.endsWith(":TENANT")) {
                return owner.getTenantId().equals(userTenant);
            }
        }
        // Check GLOBAL permissions
        return auth.getAuthorities().contains("PERM_" + permission);
    }
}
```

### Method Security
```java
@RestController
@RequestMapping("/tickets")
public class TicketController {
    @PreAuthorize("hasPermission(#id, 'TICKET', 'READ')")
    @GetMapping("/{id}")
    public TicketDto getTicket(@PathVariable Long id) { ... }

    @PreAuthorize("hasPermission(#id, 'TICKET', 'TRANSITION')")
    @PostMapping("/{id}/transition")
    public TicketDto transition(@PathVariable Long id, @RequestBody TransitionRequest req) { ... }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) { ... }
}
```

## OAuth 2.1 / OIDC (Embedded Authorization Server)

### Why Embedded?
- No external IdP dependency for core auth
- Full control over token format, claims, flows
- Easy migration to external IdP later (Keycloak, Auth0, Azure AD)
- Single deployment unit

### Supported Flows
1. **Authorization Code + PKCE** (SPA, mobile)
2. **Client Credentials** (machine-to-machine)
3. **Device Authorization Grant** (CLI, IoT)
4. **Refresh Token** (token rotation)

### Endpoints
```
GET  /oauth2/authorize           - Authorization endpoint
POST /oauth2/token               - Token endpoint
POST /oauth2/introspect          - Token introspection
POST /oauth2/revoke              - Token revocation
GET  /.well-known/oauth-authorization-server
GET  /.well-known/openid-configuration
GET  /oauth2/jwks                - JWKS endpoint
```

### Client Registration
```java
@Bean
RegisteredClientRepository registeredClientRepository() {
    RegisteredClient webClient = RegisteredClient.withId(UUID.randomUUID().toString())
        .clientId("nexusops-web")
        .clientSecret("{bcrypt}$2a$10$...")
        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
        .redirectUri("https://nexusops.com/login/oauth2/code/nexusops")
        .redirectUri("http://localhost:4200/login/oauth2/code/nexusops")
        .scope(OidcScopes.OPENID)
        .scope(OidcScopes.PROFILE)
        .scope("ticketing:read")
        .scope("ticketing:write")
        .clientSettings(ClientSettings.builder()
            .requireProofKey(true)  // PKCE required
            .requireAuthorizationConsent(false)
            .build())
        .tokenSettings(TokenSettings.builder()
            .accessTokenTimeToLive(Duration.ofMinutes(15))
            .refreshTokenTimeToLive(Duration.ofDays(7))
            .reuseRefreshTokens(false)
            .build())
        .build();
}
```

## Session Management

### Concurrent Sessions
- Configurable limit (default: 5)
- View active sessions in profile
- Revoke individual or all sessions

### Device Fingerprinting
- User-Agent + IP hash stored with refresh token
- Anomaly detection (new device/location)
- Email notification on new login

## Security Events Audit

### Events Logged
| Event | Details |
|-------|---------|
| LOGIN_SUCCESS | username, ip, user-agent, mfa-used |
| LOGIN_FAILED | username, ip, reason (bad_creds, mfa_failed, locked) |
| LOGOUT | username, session-id |
| TOKEN_REFRESH | username, token-id |
| TOKEN_REVOKED | username, reason |
| MFA_ENABLED | username |
| MFA_DISABLED | username |
| PASSWORD_CHANGED | username |
| ROLE_ASSIGNED | username, role, assigned-by |
| PERMISSION_GRANTED | username, permission, granted-by |

### Storage
- Immutable append-only table
- Monthly partitioning
- 7-year retention (compliance)

## Password Policy
- Min 12 characters
- No complexity requirements (NIST 800-63B)
- Breach check via HaveIBeenPwned API
- Argon2id hashing (Spring Security 6+)

## Account Lockout
- 5 failed attempts → 15 min lockout
- Progressive delays (exponential backoff)
- Admin unlock or self-service via email

## Future Extensibility

### External IdP Integration
- SAML 2.0 / OIDC federation
- Just-in-time provisioning
- Attribute mapping (groups → roles)

### Passkeys / WebAuthn
- Credential registration
- Passwordless login
- Cross-device sync (iCloud Keychain, Google Password Manager)