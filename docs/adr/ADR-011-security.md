# ADR-011: Security Strategy

## Status
Accepted

## Context
NexusOps handles sensitive IT operations data and requires:
- Supply chain security (SBOM, signing, vulnerability scanning)
- Runtime security (network policies, pod security, encryption)
- Data protection (encryption at rest/transit, PII handling)
- Access control (RBAC, ABAC, MFA, audit logging)
- Compliance readiness (SOC 2, GDPR)
- Incident response capability

## Decision
Implement **Defense in Depth** security across all layers: supply chain, infrastructure, application, data, and operations.

## Supply Chain Security

### SBOM Generation
```yaml
# CI Pipeline - Every Build
- name: Generate SBOM (Backend)
  uses: anchore/sbom-action@v0
  with:
    path: ./backend
    format: spdx-json
    output-file: sbom-backend.spdx.json

- name: Generate SBOM (Frontend)
  uses: anchore/sbom-action@v0
  with:
    path: ./frontend
    format: spdx-json
    output-file: sbom-frontend.spdx.json

- name: Upload SBOM to GitHub
  uses: github/dependency-graph-submission-api@main
  with:
    sbom: sbom-backend.spdx.json
```

### Container Image Signing (Cosign)
```bash
# Sign image
cosign sign --yes ghcr.io/nexusops/nexusops-backend@sha256:abc123

# Verify in admission controller (Kyverno)
apiVersion: kyverno.io/v1
kind: ClusterPolicy
metadata:
  name: verify-image-signatures
spec:
  validationFailureAction: Enforce
  rules:
    - name: check-cosign-signature
      match:
        any:
        - resources:
            kinds: ["Pod"]
      verifyImages:
        - image: "ghcr.io/nexusops/*"
          key: |
            -----BEGIN PUBLIC KEY-----
            ...
            -----END PUBLIC KEY-----
          annotations:
            - key: "github-workflow-repository"
              value: "nexusops/nexusops"
```

### Dependency Scanning
```yaml
# CI Pipeline
- name: OWASP Dependency Check
  uses: dependency-check/Dependency-Check_Action@main
  with:
    project: 'NexusOps'
    path: '.'
    format: 'HTML'
    args: '--failOnCVSS 7 --suppressionFile dependency-check-suppressions.xml'

- name: Trivy Filesystem Scan
  uses: aquasecurity/trivy-action@master
  with:
    scan-type: 'fs'
    scan-ref: '.'
    format: 'sarif'
    output: 'trivy-results.sarif'
    severity: 'CRITICAL,HIGH'

- name: Upload Trivy Results
  uses: github/codeql-action/upload-sarif@v3
  with:
    sarif_file: 'trivy-results.sarif'
```

### Secret Scanning
```yaml
- name: TruffleHog
  uses: trufflesecurity/trufflehog@main
  with:
    path: ./
    base: main
    head: HEAD
```

### Dependabot Configuration
```yaml
# .github/dependabot.yml
version: 2
updates:
  - package-ecosystem: "maven"
    directory: "/backend"
    schedule:
      interval: "weekly"
    labels: ["dependencies", "backend"]
    groups:
      spring-boot:
        patterns: ["org.springframework.boot:*"]
      security:
        patterns: ["*"]
        update-types: ["security"]
  
  - package-ecosystem: "npm"
    directory: "/frontend"
    schedule:
      interval: "weekly"
    labels: ["dependencies", "frontend"]
  
  - package-ecosystem: "github-actions"
    directory: "/"
    schedule:
      interval: "weekly"
  
  - package-ecosystem: "docker"
    directory: "/backend"
    schedule:
      interval: "weekly"
    labels: ["docker", "backend"]
  
  - package-ecosystem: "docker"
    directory: "/frontend"
    schedule:
      interval: "weekly"
    labels: ["docker", "frontend"]
```

## Infrastructure Security

### Network Segmentation
```hcl
# VPC Flow Logs
resource "aws_flow_log" "vpc" {
  iam_role_arn      = aws_iam_role.flow_logs.arn
  log_destination   = aws_cloudwatch_log_group.vpc_flow_logs.arn
  traffic_type      = "ALL"
  vpc_id            = aws_vpc.main.id
}

# Security Groups - Least Privilege
resource "aws_security_group" "backend" {
  name        = "nexusops-backend"
  description = "Backend application security group"
  vpc_id      = var.vpc_id

  # Ingress from ALB only
  ingress {
    from_port       = 8080
    to_port         = 8080
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  # Egress to RDS, Redis, S3 (via VPC endpoints)
  egress {
    from_port   = 5432
    to_port     = 5432
    protocol    = "tcp"
    security_groups = [aws_security_group.rds.id]
  }
  egress {
    from_port   = 6379
    to_port     = 6379
    protocol    = "tcp"
    security_groups = [aws_security_group.elasticache.id]
  }
  # DNS
  egress {
    from_port   = 53
    to_port     = 53
    protocol    = "tcp"
    cidr_blocks = ["10.0.0.0/16"]
  }
  egress {
    from_port   = 53
    to_port     = 53
    protocol    = "udp"
    cidr_blocks = ["10.0.0.0/16"]
  }
}
```

### Encryption

#### At Rest
```hcl
# KMS Keys with Key Rotation
resource "aws_kms_key" "app_secrets" {
  description             = "Application secrets encryption"
  deletion_window_in_days = 10
  enable_key_rotation     = true
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid    = "Allow administration"
      Effect = "Allow"
      Principal = { AWS = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:root" }
      Action = ["kms:*"]
      Resource = "*"
    }, {
      Sid    = "Allow application services"
      Effect = "Allow"
      Principal = { AWS = "*" }
      Action = ["kms:Encrypt", "kms:Decrypt", "kms:ReEncrypt*", "kms:GenerateDataKey*"]
      Resource = "*"
      Condition = { StringEquals = { "kms:CallerAccount" = data.aws_caller_identity.current.account_id } }
    }]
  })
}

# RDS Encryption
resource "aws_db_instance" "main" {
  storage_encrypted = true
  kms_key_id        = aws_kms_key.rds.arn
}

# EBS Encryption
resource "aws_ebs_encryption_by_default" "main" {
  enabled = true
}

# S3 Encryption
resource "aws_s3_bucket_server_side_encryption_configuration" "default" {
  bucket = aws_s3_bucket.main.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "aws:kms"
      kms_master_key_id = aws_kms_key.s3.arn
    }
  }
}
```

#### In Transit
```hcl
# ALB TLS 1.3
resource "aws_lb_listener" "https" {
  ssl_policy = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn = var.acm_certificate_arn
}

# Redis TLS
resource "aws_elasticache_replication_group" "main" {
  transit_encryption_enabled = true
  at_rest_encryption_enabled = true
}

# RDS TLS
resource "aws_db_instance" "main" {
  # Force SSL via parameter group
}
```

```yaml
# Spring Boot - Force SSL
spring:
  datasource:
    hikari:
      data-source-properties:
        sslmode: require
        sslcert: /certs/postgresql.crt
        sslkey: /certs/postgresql.key
        sslrootcert: /certs/root.crt
```

### Pod Security Standards
```yaml
# Namespace labels
apiVersion: v1
kind: Namespace
metadata:
  name: nexusops-prod
  labels:
    pod-security.kubernetes.io/enforce: restricted
    pod-security.kubernetes.io/audit: restricted
    pod-security.kubernetes.io/warn: restricted
```

```yaml
# Pod Security Context
spec:
  securityContext:
    runAsNonRoot: true
    runAsUser: 1000
    runAsGroup: 1000
    fsGroup: 1000
    seccompProfile:
      type: RuntimeDefault
  containers:
    - name: backend
      securityContext:
        allowPrivilegeEscalation: false
        readOnlyRootFilesystem: true
        capabilities:
          drop: ["ALL"]
        runAsNonRoot: true
        runAsUser: 1000
```

## Application Security

### Authentication & Authorization
```java
// JWT RS256 with rotation
@Configuration
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers("/api/v1/actuator/health").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .decoder(jwtDecoder())
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .csrf(csrf -> csrf.disable())
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp
                    .policyDirectives("default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; connect-src 'self' wss: https:")
                )
                .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                .xssProtection(HeadersConfigurer.XXssConfig::disable)
            );
        return http.build();
    }
}
```

### Input Validation
```java
@RestController
@RequestMapping("/api/v1/tickets")
@Validated
public class TicketController {
    @PostMapping
    public TicketDto createTicket(
        @Valid @RequestBody CreateTicketRequest request,
        @RequestHeader("X-Request-ID") String requestId
    ) {
        // Request validated by @Valid
        // Additional business validation in service
        return ticketService.createTicket(request);
    }
}

public class CreateTicketRequest {
    @NotBlank
    @Size(max = 200)
    private String title;

    @NotBlank
    @Size(max = 10000)
    private String description;

    @NotNull
    private TicketType type;

    @NotNull
    private Priority priority;

    @Pattern(regexp = "^[a-zA-Z0-9-_]+$")
    private String categoryCode;
}
```

### Output Encoding (XSS Prevention)
```typescript
// Angular - Use built-in sanitization
@Component({
  template: `
    <div [innerHTML]="ticket.description | safeHtml"></div>
    <!-- Or use Angular's built-in interpolation -->
    <p>{{ ticket.description }}</p>
  `
})
export class TicketDetailComponent {
  // No manual innerHTML unless using DomSanitizer
}
```

### Content Security Policy
```java
// Spring Security CSP
headers.headers(
    contentSecurityPolicy = "default-src 'self'; " +
        "script-src 'self' 'unsafe-inline' 'unsafe-eval'; " +
        "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; " +
        "font-src 'self' https://fonts.gstatic.com; " +
        "img-src 'self' data: https:; " +
        "connect-src 'self' wss: https:; " +
        "frame-ancestors 'none'; " +
        "base-uri 'self'; " +
        "form-action 'self';"
)
```

## Data Protection

### PII Handling
```java
@Entity
public class User {
    @Column(name = "email", nullable = false, unique = true)
    @Convert(converter = EmailEncryptionConverter.class)
    private String email;

    @Column(name = "phone")
    @Convert(converter = PhoneEncryptionConverter.class)
    private String phone;

    @Column(name = "full_name")
    @Convert(converter = NameEncryptionConverter.class)
    private String fullName;
}

// AttributeConverter with AES-GCM
@Converter
public class EmailEncryptionConverter implements AttributeConverter<String, String> {
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private final Key key;

    @Override
    public String convertToDatabaseColumn(String plainText) {
        if (plainText == null) return null;
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            byte[] iv = new byte[12];
            SecureRandom random = new SecureRandom();
            random.nextBytes(iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            // Prepend IV to ciphertext
            byte[] result = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(cipherText, 0, result, iv.length, cipherText.length);
            return Base64.getEncoder().encodeToString(result);
        } catch (Exception e) {
            throw new EncryptionException("Failed to encrypt", e);
        }
    }
}
```

### GDPR Compliance
```java
@Service
public class GdprService {
    @Transactional
    public void eraseUserData(String userId) {
        // Anonymize user
        User user = userRepository.findById(userId).orElseThrow();
        user.setEmail("erased-" + userId + "@nexusops.local");
        user.setFullName("Erased User");
        user.setPhone(null);
        user.setStatus(UserStatus.ERASED);
        userRepository.save(user);

        // Anonymize related data
        ticketRepository.anonymizeByUserId(userId);
        commentRepository.anonymizeByUserId(userId);
        timeEntryRepository.anonymizeByUserId(userId);

        // Log erasure
        auditService.logGdprErasure(userId);
    }

    public UserDataExport exportUserData(String userId) {
        User user = userRepository.findById(userId).orElseThrow();
        return UserDataExport.builder()
            .profile(user)
            .tickets(ticketRepository.findByUserId(userId))
            .comments(commentRepository.findByUserId(userId))
            .timeEntries(timeEntryRepository.findByUserId(userId))
            .assets(assetRepository.findByUserId(userId))
            .build();
    }
}
```

### Data Retention
```yaml
# Retention policies
retention:
  audit_logs: 7 years
  tickets: 10 years (configurable per tenant)
  comments: 10 years
  attachments: 5 years
  sessions: 30 days
  metrics: 15 days (high-res), 1 year (downsampled)
  traces: 7 days
  backups: 30 days (RDS), 90 days (cross-region)
```

## Audit Logging

### Immutable Audit Trail
```java
@Entity
@Table(name = "audit_logs")
@Partitioned
public class AuditLog {
    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "aggregate_id")
    private String aggregateId;

    @Column(name = "aggregate_type")
    private String aggregateType;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "payload", columnDefinition = "jsonb")
    private String payload;

    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
```

### Security Events
```java
@Component
public class SecurityAuditListener {
    @EventListener
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        auditService.log(SecurityAuditEvent.builder()
            .eventType("LOGIN_SUCCESS")
            .userId(event.getAuthentication().getName())
            .ipAddress(getClientIp())
            .userAgent(getUserAgent())
            .mfaUsed(isMfaUsed(event))
            .build());
    }

    @EventListener
    public void onAuthenticationFailure(AuthenticationFailureBadCredentialsEvent event) {
        auditService.log(SecurityAuditEvent.builder()
            .eventType("LOGIN_FAILED")
            .userId(event.getAuthentication().getName())
            .ipAddress(getClientIp())
            .reason("BAD_CREDENTIALS")
            .build());
    }

    @EventListener
    public void onAuthorizationFailure(AccessDeniedEvent event) {
        auditService.log(SecurityAuditEvent.builder()
            .eventType("ACCESS_DENIED")
            .userId(event.getAuthentication().getName())
            .resource(event.getAttributes().get("resource"))
            .action(event.getAttributes().get("action"))
            .build());
    }
}
```

## Incident Response

### Runbooks
```
docs/runbooks/
├── security-incident.md
├── data-breach.md
├── credential-leak.md
├── ddos-attack.md
├── ransomware.md
└── insider-threat.md
```

### Security Incident Response Plan
| Phase | Actions | Owner | Timeline |
|-------|---------|-------|----------|
| Detect | Alert triggers, log analysis | On-call | T+0 |
| Contain | Isolate affected systems, revoke tokens | Platform | T+15min |
| Assess | Determine scope, impact | Security Lead | T+1hr |
| Eradicate | Remove threat, patch vuln | Platform | T+4hr |
| Recover | Restore services, verify integrity | Platform | T+24hr |
| Lessons Learned | Post-mortem, update controls | Security Lead | T+72hr |

## Compliance

### SOC 2 Type II Controls
| Trust Criteria | Implementation |
|----------------|----------------|
| **Security** | Access controls, encryption, monitoring, incident response |
| **Availability** | Multi-AZ, auto-scaling, health checks, SLA monitoring |
| **Processing Integrity** | Audit logs, data validation, error handling, reconciliation |
| **Confidentiality** | Encryption, data classification, access controls, DLP |
| **Privacy** | GDPR compliance, consent management, data minimization |

### Evidence Collection (Automated)
```yaml
# CI/CD Pipeline - Collect compliance evidence
- name: Collect SOC 2 Evidence
  run: |
    # Access control evidence
    aws iam generate-credential-report > evidence/credential-report.csv
    aws iam get-account-authorization-details > evidence/iam-policies.json
    
    # Encryption evidence
    aws kms list-keys --query 'Keys[].KeyId' > evidence/kms-keys.json
    aws rds describe-db-instances --query 'DBInstances[].StorageEncrypted' > evidence/rds-encryption.json
    
    # Change management evidence
    gh api repos/nexusops/nexusops/commits --jq '.[] | {sha, message, author, date}' > evidence/commits.json
    
    # Monitoring evidence
    kubectl get servicemonitor -A -o json > evidence/servicemonitors.json
    kubectl get prometheusrule -A -o json > evidence/alerts.json
```

## Penetration Testing
```yaml
# Annual external pen test
# Scope: API, frontend, infrastructure
# Rules of engagement: No DoS, no data exfiltration
# Report: Executive summary + technical findings
# Remediation: Critical/High within 30 days, Medium within 90 days
```

## Security Training
- Annual secure coding training (OWASP Top 10)
- Incident response tabletop exercises (quarterly)
- Phishing simulations (monthly)
- New hire security onboarding