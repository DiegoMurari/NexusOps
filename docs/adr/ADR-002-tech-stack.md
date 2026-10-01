# ADR-002: Technology Stack Decisions

## Status
Accepted

## Backend Stack

### Java 21 (LTS)
- **Reasoning**: Latest LTS, virtual threads (Project Loom), record patterns, pattern matching for switch, sequenced collections
- **Benefits**: Simplified concurrency, reduced boilerplate, better performance for I/O-bound workloads

### Spring Boot 3.2+
- **Reasoning**: Jakarta EE 9+ baseline, native image support (GraalVM), AOT compilation, observability built-in
- **Benefits**: Modern Spring, smaller memory footprint, faster startup

### Spring Modulith 1.2+
- **Reasoning**: Official Spring project for modular monoliths, ArchUnit integration, documentation generation
- **Benefits**: Enforced module boundaries, domain event support, easy extraction path

### Spring Security 6+
- **Reasoning**: OAuth 2.1/OIDC support, reactive support, modern security patterns
- **Benefits**: Embedded Authorization Server, JWT with RS256, MFA TOTP

### Spring Data JPA + Hibernate 6
- **Reasoning**: Standard ORM, Hibernate 6 performance improvements, Jakarta Persistence 3.1
- **Benefits**: Type-safe queries, entity graphs, second-level cache ready

### PostgreSQL 16
- **Reasoning**: Advanced features (parallel query, partitioning, logical replication), JSONB, full-text search
- **Benefits**: ACID, rich data types, extensions (pg_stat_statements, auto_explain)

### Redis 7
- **Reasoning**: High performance, Redis Stack modules (search, JSON), ACL, TLS
- **Benefits**: Caching, sessions, rate limiting, pub/sub for cache invalidation

### Flyway
- **Reasoning**: Versioned migrations, repeatable migrations, callbacks, baseline
- **Benefits**: Reliable schema evolution, CI/CD integration

### MapStruct
- **Reasoning**: Compile-time mapping, zero runtime overhead, Spring integration
- **Benefits**: Type-safe DTO mapping, no reflection

### Lombok
- **Reasoning**: Boilerplate reduction, compile-time code generation
- **Benefits**: Cleaner code, less getter/setter/builder noise

## Frontend Stack

### Angular 17+
- **Reasoning**: Signals (fine-grained reactivity), standalone components, new control flow, zoneless-ready
- **Benefits**: Better performance, simpler mental model, future-proof

### TypeScript 5.3+
- **Reasoning**: Strict typing, decorators, satisfies operator, const type parameters
- **Benefits**: Type safety, better IDE support, catch bugs at compile time

### Angular Material 3
- **Reasoning**: Material Design 3, component library, theming, accessibility
- **Benefits**: Consistent UI, accessible components, dark mode support

### RxJS 7
- **Reasoning**: Reactive programming, observables, operators, interop with Signals
- **Benefits**: Async handling, complex event streams, HTTP, WebSocket

### pnpm
- **Reasoning**: Fast, disk-efficient, strict dependency resolution
- **Benefits**: Faster installs, monorepo support, no phantom dependencies

### Playwright
- **Reasoning**: Cross-browser, auto-waiting, trace viewer, parallel execution
- **Benefits**: Reliable E2E tests, debugging with traces, CI integration

## Infrastructure Stack

### Docker (Multi-stage, Distroless)
- **Reasoning**: Minimal attack surface, no shell, no package manager
- **Benefits**: Security, smaller images, faster deployments

### Kubernetes (Kustomize + ArgoCD)
- **Reasoning**: Native kubectl, GitOps, no Helm complexity, overlays for environments
- **Benefits**: Declarative, audit trail, rollback, progressive delivery

### Terraform
- **Reasoning**: Infrastructure as Code, state management, module ecosystem
- **Benefits**: Reproducible, versioned, reviewable infrastructure changes

### AWS (EKS, RDS, ElastiCache, ALB, S3, CloudFront)
- **Reasoning**: Managed services, global presence, enterprise features
- **Benefits**: Operational simplicity, security, compliance, scaling

### GitHub Actions
- **Reasoning**: Native GitHub integration, reusable workflows, OIDC for AWS
- **Benefits**: CI/CD in same platform, security scanning, artifact management

## Observability Stack

### OpenTelemetry
- **Reasoning**: Vendor-neutral, auto-instrumentation, context propagation
- **Benefits**: Unified traces/metrics/logs, no vendor lock-in

### Prometheus + Grafana
- **Reasoning**: Industry standard, PromQL, alerting, dashboarding
- **Benefits**: Rich ecosystem, cost-effective, scalable

### Loki
- **Reasoning**: Log aggregation, label-based, cost-effective (no full-text index)
- **Benefits**: Correlated with traces/metrics, Grafana integration

### Tempo
- **Reasoning**: Trace storage, object storage backend, Grafana integration
- **Benefits:** Cost-effective, scalable, no indexing

### Pyroscope
- **Reasoning**: Continuous profiling, flame graphs, CPU/memory allocation
- **Benefits:** Performance optimization, bottleneck identification

## Security Stack

### CodeQL
- **Reasoning**: Semantic code analysis, GitHub native, query language
- **Benefits:** Deep security analysis, custom queries

### Trivy
- **Reasoning**: Container scanning, filesystem scanning, config audit
- **Benefits**: Comprehensive, fast, CI/CD integration

### Dependency Check (OWASP)
- **Reasoning**: CVE detection, NVD integration, suppressions
- **Benefits**: Supply chain security, automated updates

### Cosign/Sigstore
- **Reasoning**: Container signing, keyless signing, transparency log
- **Benefits**: Supply chain integrity, verification

### TruffleHog
- **Reasoning**: Secret scanning, git history, high entropy detection
- **Benefits**: Prevent credential leaks, CI/CD integration

## Testing Stack

### JUnit 5 + Mockito
- **Reasoning**: Standard Java testing, parameterized tests, extensions
- **Benefits**: Rich ecosystem, IDE integration, parallel execution

### Testcontainers
- **Reasoning**: Real databases in tests, disposable containers, Spring integration
- **Benefits**: Reliable integration tests, no shared test DB

### Spring Cloud Contract
- **Reasoning**: Consumer-driven contracts, stub generation, Maven/Gradle plugins
- **Benefits**: API compatibility, independent deployment

### Angular Testing (Jest)
- **Reasoning**: Fast, snapshot testing, parallel, TypeScript native
- **Benefits**: Unit/component testing, code coverage

### Playwright
- **Reasoning**: E2E testing, cross-browser, auto-wait, traces
- **Benefits**: Real user flows, reliable, debuggable

### k6
- **Reasoning**: Load testing, JavaScript scripting, thresholds, Grafana
- **Benefits**: Performance validation, CI/CD integration