# ADR-001: Architecture Style - Modular Monolith

## Status
Accepted

## Context
NexusOps is a Service Desk & IT Operations platform that needs to be:
- Developed by a small team (8-12 engineers)
- Deployed and operated initially as a single unit
- Evolvable towards microservices when scale demands
- Interview-ready for Java, Angular, Full Stack, Cloud, DevOps roles

## Decision
We will implement NexusOps as a **Modular Monolith** using **Spring Modulith** with the following characteristics:

1. **Single deployable unit** (JAR) containing all modules
2. **Strict module boundaries** enforced by ArchUnit tests
3. **Domain-driven design** with 10 bounded contexts
4. **Event-driven communication** between modules via TransactionalEventPublisher
5. **Shared kernel** for cross-cutting concerns (events, exceptions, security, audit)
6. **Logical database schemas** per module within single PostgreSQL instance
7. **Extraction-ready** design for future microservice migration

## Modules (Bounded Contexts)
| Module | Responsibility | Database Schema |
|--------|----------------|-----------------|
| shared-kernel | Events, exceptions, security utils, audit | - |
| iam | Authentication, authorization, MFA, users | iam |
| platform | Multi-tenancy, feature flags, settings, audit | platform |
| sla | SLA definitions, business calendars, escalations | sla |
| ticketing | Incidents, problems, changes, comments, attachments | ticketing |
| asset | CMDB, CI relationships, software licenses | asset |
| knowledge | Articles, categories, versioning, public portal | knowledge |
| notification | Real-time (WS/SSE), email, in-app, templates | notification |
| reporting | CQRS read models, dashboards, scheduled reports | reporting |
| integration | Webhooks, Jira, Slack, Teams connectors | integration |

## Consequences

### Positive
- **Simpler operations**: Single deployment, single database, simpler networking
- **ACID transactions** across modules when needed (same database)
- **Faster development**: No distributed system complexity, easier debugging
- **Team autonomy**: Modules owned by feature teams, clear boundaries
- **Easy extraction**: Spring Modulith + ArchUnit + domain events make future extraction straightforward
- **Cost effective**: Single RDS instance, single EKS deployment for MVP

### Negative
- **Scaling limitations**: All modules scale together (mitigated by read replicas, caching)
- **Technology lock-in**: All modules share Java/Spring stack (acceptable for this project)
- **Failure domain**: Bug in one module can affect others (mitigated by circuit breakers, bulkheads)
- **Deployment coupling**: Full redeploy for any change (mitigated by feature flags)

## Extraction Criteria
When ANY of these conditions are met, extract modules to microservices:
1. Team size > 15 engineers
2. Module needs independent scaling (>10x traffic vs others)
3. Module requires different technology stack
4. Module needs independent deployment cadence
5. Regulatory/data residency requirements

## Alternatives Considered
1. **Pure Microservices** - Rejected: Too much operational overhead for team size, distributed complexity
2. **Majestic Monolith** - Rejected: No module boundaries, leads to spaghetti code
3. **Modular Monolith (Spring Modulith)** - Accepted: Best balance of simplicity and future-proofing

## References
- [Spring Modulith Documentation](https://spring.io/projects/spring-modulith)
- [ArchUnit](https://www.archunit.org/)
- [Modular Monolith vs Microservices](https://martinfowler.com/articles/modular-monoliths.html)