# NexusOps Documentation

This directory contains all architectural documentation for the NexusOps Service Desk & IT Operations Platform.

## Table of Contents

### Architecture Decision Records (ADRs)
| # | Title | Description |
|---|-------|-------------|
| [001](adr/ADR-001-architecture-style.md) | Architecture Style | Modular Monolith vs Microservices decision |
| [002](adr/ADR-002-tech-stack.md) | Technology Stack | All technology choices with justification |
| [003](adr/ADR-003-database-strategy.md) | Database Strategy | PostgreSQL schemas, multi-tenancy, partitioning |
| [004](adr/ADR-004-auth-strategy.md) | Auth Strategy | JWT RS256, MFA TOTP, RBAC/ABAC, OAuth 2.1 |
| [005](adr/ADR-005-testing-strategy.md) | Testing Strategy | Test pyramid, Testcontainers, Contract, E2E |
| [006](adr/ADR-006-docker-strategy.md) | Docker Strategy | Multi-stage, Distroless, Signing, SBOM |
| [007](adr/ADR-007-ci-cd.md) | CI/CD Strategy | GitHub Actions, ArgoCD, GitOps, Blue-Green |
| [008](adr/ADR-008-kubernetes.md) | Kubernetes Strategy | Kustomize, Network Policies, HPA, DR |
| [009](adr/ADR-009-aws-infrastructure.md) | AWS Infrastructure | EKS, RDS, ElastiCache, ALB, Security |
| [010](adr/ADR-010-observability.md) | Observability | OpenTelemetry, Prometheus, Loki, Tempo, Pyroscope |
| [011](adr/ADR-011-security.md) | Security Strategy | Supply chain, runtime, data, compliance |
| [012](adr/ADR-012-phased-implementation.md) | Phased Implementation | 22-week plan with milestones |

### Technical Specifications
| Document | Description |
|----------|-------------|
| [02-directory-structure.md](02-directory-structure.md) | Complete monorepo layout |
| [03-backend-modules.md](03-backend-modules.md) | 10 bounded contexts with APIs and events |
| [04-api-design.md](04-api-design.md) | REST + OpenAPI 3.1, DTOs, errors, WebSocket/SSE |
| [05-database-design.md](05-database-design.md) | Schema strategy, Flyway, Redis, performance |
| [06-auth-security.md](06-auth-security.md) | Detailed auth implementation |
| [07-testing-strategy.md](07-testing-strategy.md) | Test pyramid details |
| [08-docker-compose.md](08-docker-compose.md) | Dockerfiles, compose files |
| [09-ci-cd.md](09-ci-cd.md) | Pipeline details |
| [10-kubernetes.md](10-kubernetes.md) | K8s manifests, Kustomize, ArgoCD |
| [11-aws-infrastructure.md](11-aws-infrastructure.md) | Terraform modules |
| [12-observability.md](12-observability.md) | Observability stack config |
| [13-security.md](13-security.md) | Security implementation |
| [14-phased-implementation.md](14-phased-implementation.md) | Detailed 22-week plan |

## Quick Start

### Prerequisites
- Java 21 (Temurin)
- Node.js 20+ with pnpm
- Docker & Docker Compose
- Maven 3.9+ (or use wrapper)

### Local Development
```bash
# 1. Start infrastructure
cd ci-cd/docker
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d

# 2. Backend
cd ../../backend
./mvnw spring-boot:run -Dspring.profiles.active=dev

# 3. Frontend (new terminal)
cd ../frontend
pnpm install
pnpm run start
```

**Access Points:**
- Frontend: http://localhost:4200
- Backend API: http://localhost:8080/api/v1
- Swagger UI: http://localhost:8080/swagger-ui.html
- Actuator Health: http://localhost:8080/api/v1/actuator/health
- Grafana: http://localhost:3000 (admin/admin)
- Prometheus: http://localhost:9090
- Mailhog: http://localhost:8025

## Tech Stack Summary

| Layer | Technology |
|-------|------------|
| **Frontend** | Angular 17+, TypeScript, Angular Material 3, RxJS 7, Signals |
| **Backend** | Java 21, Spring Boot 3.2+, Spring Modulith, Spring Security, Spring Data JPA |
| **Database** | PostgreSQL 16, Redis 7, Flyway |
| **Auth** | JWT RS256, Embedded Spring Authorization Server (OAuth 2.1/OIDC), MFA TOTP |
| **API** | REST (OpenAPI 3.1), WebSocket (STOMP), SSE |
| **Infrastructure** | Docker, Kubernetes (Kustomize), ArgoCD, Terraform, AWS (EKS, RDS, ElastiCache) |
| **CI/CD** | GitHub Actions, Trivy, CodeQL, Cosign, Dependabot |
| **Observability** | OpenTelemetry, Prometheus, Grafana, Loki, Tempo, Pyroscope |
| **Testing** | JUnit 5, Mockito, Testcontainers, Spring Cloud Contract, Playwright, k6 |

## Architecture Highlights

- **Modular Monolith** (Spring Modulith) - extraction-ready for microservices
- **10 Bounded Contexts** - IAM, Ticketing, Asset, Knowledge, SLA, Notification, Reporting, Integration, Platform, Shared Kernel
- **Domain Events** - TransactionalEventPublisher for cross-module async communication
- **Logical Schemas** - Per-module PostgreSQL schemas within single database
- **Multi-tenancy** - Hibernate Filters + TenantContext from JWT
- **Real-time** - WebSocket (STOMP) primary + SSE fallback
- **CQRS** - Event-driven materialized views for reporting
- **GitOps** - Kustomize + ArgoCD for deployments
- **Security** - Supply chain (SBOM, Cosign), Zero Trust, SOC 2/GDPR ready

## Development Workflow

1. Create feature branch from `main`
2. Implement feature with tests
3. Run local: `./mvnw spotless:apply` (backend), `pnpm run format` (frontend)
4. Push branch → CI validates (lint, test, security, build)
5. Create PR → requires 1 approval + CI pass
6. Squash merge to `main`
7. Auto-deploy to staging via ArgoCD
8. Manual approval → production deploy

## Quality Gates

| Check | Tool | Threshold |
|-------|------|-----------|
| Format | google-java-format, Prettier | Zero violations |
| Lint | Checkstyle, SpotBugs, ESLint | Zero errors |
| Unit Tests | JUnit 5, Jest | ≥80% coverage |
| Integration Tests | Testcontainers, Playwright | All pass |
| Contract Tests | Spring Cloud Contract, Pact | All pass |
| Architecture | ArchUnit | Zero violations |
| Security | CodeQL, Trivy, Dependency Check | No CRITICAL/HIGH |
| Build | Maven, Angular CLI | Success |

## Environments

| Environment | Purpose | Deployment |
|-------------|---------|------------|
| Local | Development | Docker Compose |
| Dev | Integration testing | Auto on feature branches |
| Staging | Pre-production validation | Auto on main merge |
| Production | Live traffic | Manual approval, Blue-Green |

## Contributing

See [CONTRIBUTING.md](../CONTRIBUTING.md) for development workflow, coding standards, and quality gates.

## License

Apache 2.0 - See [LICENSE](../LICENSE)