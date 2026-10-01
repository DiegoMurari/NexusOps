# ADR-012: Phased Implementation Plan

## Status
Accepted

## Context
NexusOps is a complex platform requiring 22 weeks to production launch. A phased approach ensures:
- Early value delivery
- Risk mitigation
- Team velocity validation
- Stakeholder feedback incorporation
- Architecture validation

## Decision
Implement in **5 phases over 22 weeks** with clear milestones and exit criteria.

## Phase Overview

| Phase | Duration | Focus | Key Deliverable |
|-------|----------|-------|-----------------|
| **Phase 0: Foundation** | Weeks 1-2 | Infra, CI/CD, Architecture Guardrails | Dev environment deploys in <10 min |
| **Phase 1: Core Platform** | Weeks 3-6 | Auth, Multi-tenancy, Admin UI | Login + MFA + Admin works |
| **Phase 2: Ticketing Core** | Weeks 7-12 | Ticket lifecycle, SLA, Real-time | Create→Resolve→Close in UI |
| **Phase 3: Asset & Knowledge** | Weeks 13-18 | CMDB, KB, Integrations, Reports | Assets linked, KB searchable |
| **Phase 4: Hardening** | Weeks 19-22 | Performance, Security, DR, Launch | 10K users, P95<500ms, 99.9% uptime |

---

## Phase 0: Foundation (Weeks 1-2)

### Objective
Establish development environment, CI/CD pipeline, architecture guardrails, and cloud infrastructure (dev/staging).

### Sprint 0.1-0.2 (Week 1): Repository & Backend Scaffold

| Step | Tasks | Owner | Deliverable |
|------|-------|-------|-------------|
| **0.1** | Git init, branch protection, CODEOWNERS, conventional commits, Dependabot | DevOps | Protected main branch, PR template |
| **0.2** | Maven multi-module (11 modules), Spring Modulith, ArchUnit rules, shared-kernel | Backend Lead | `./mvnw compile` passes, ArchUnit tests pass |
| **0.3** | Angular 17+ scaffold, Signals, Standalone, Material 3, ESLint/Prettier/Husky | Frontend Lead | `npm run build` passes, lint clean |
| **0.4** | GitHub Actions: lint, test, security, Docker build, deploy to dev | DevOps | PR validation < 10 min |
| **0.5** | Docker Compose: PostgreSQL, Redis, Mailhog, Prometheus/Grafana/Loki/Tempo | DevOps | `make dev-up` starts stack < 3 min |
| **0.6** | Terraform: VPC, EKS, RDS, ElastiCache, ALB, S3, Secrets Manager (dev/staging) | DevOps | `terraform apply` succeeds |
| **0.7** | K8s: Kustomize base + overlays, ArgoCD bootstrap, External Secrets Operator | DevOps | `kubectl apply -k` works, ArgoCD syncs |
| **0.8** | Observability: OTel Collector → Prometheus/Grafana/Loki/Tempo, basic dashboards | DevOps | Metrics/logs/traces visible in Grafana |
| **0.9** | Security: CodeQL, Trivy, Dependency Check, TruffleHog, SBOM, Cosign | DevOps | No CRITICAL/HIGH in CI |
| **0.10** | ADRs 001-011, CONTRIBUTING.md, README.md, team onboarding | Tech Lead | All docs accepted |

### Sprint 0.3-0.4 (Week 2): Integration & Validation

| Step | Tasks | Owner | Deliverable |
|------|-------|-------|-------------|
| **0.11** | End-to-end: dev deploy → smoke test → staging deploy | DevOps | Feature branch deploys to dev |
| **0.12** | Load test baseline (k6): 100 users, P95 < 1s | QA | Baseline metrics captured |
| **0.13** | Architecture review: module boundaries, event flow, data model | Tech Lead | Signed off architecture |
| **0.14** | Team velocity measurement: story points completed | PM | Velocity baseline established |

### Phase 0 Exit Criteria
- [ ] `main` branch protected, PRs require approval + CI pass
- [ ] All 11 Maven modules compile, ArchUnit passes
- [ ] Angular builds, lint passes, unit tests >80% coverage
- [ ] CI pipeline: lint → test → security → Docker build → deploy dev
- [ ] Feature branch → auto-deploy to dev in < 10 min
- [ ] Main merge → auto-deploy to staging
- [ ] Terraform applies clean for dev/staging
- [ ] K8s manifests apply, ArgoCD syncs
- [ ] Observability stack shows metrics/logs/traces
- [ ] Security scans pass (no CRITICAL/HIGH)
- [ ] All ADRs documented and accepted

---

## Phase 1: Core Platform & IAM (Weeks 3-6)

### Objective
Implement authentication, authorization, multi-tenancy foundation, and admin UI.

### Sprint 1 (Weeks 3-4): IAM & Authentication

| Step | Tasks | Module | Deliverable |
|------|-------|--------|-------------|
| **1.1** | Shared Kernel: DomainEvent, TransactionalEventPublisher, Exception hierarchy, JwtTokenProvider, PermissionEvaluator | shared-kernel | Unit tests pass, coverage ≥85% |
| **1.2** | IAM Domain: User, Role, Permission, Tenant entities, Flyway migrations, repositories | iam | Entities persist, queries work |
| **1.3** | JWT Auth: RS256, 15min access, 7d rotating refresh, login/refresh/logout endpoints | iam | Login returns tokens, refresh rotates |
| **1.4** | MFA TOTP: Setup (QR), verify, recovery codes, login challenge flow | iam | MFA enroll → verify → login works |
| **1.5** | RBAC/ABAC: Spring Security config, @PreAuthorize, default roles, scope hierarchy | iam | All endpoints protected, ABAC works |
| **1.6** | Audit Logging: Immutable audit_logs, TenantContext, AuditListener, security events | platform | Audit logs captured |
| **1.7** | Angular Auth: AuthService, JWT interceptor, AuthGuard, MfaGuard, Login/MFA pages | frontend | Login + MFA challenge works |

### Sprint 2 (Weeks 5-6): Platform Services & Admin UI

| Step | Tasks | Module | Deliverable |
|------|-------|--------|-------------|
| **1.8** | Multi-tenancy: Tenant entity, Hibernate @Filter, TenantInterceptor, provisioning API | platform | Tenant isolation verified |
| **1.9** | Feature Flags: Unleash integration, Redis cache, targeting rules, Angular directive | platform | Flags toggle in real-time |
| **1.10** | System Settings: Key-value with JSON Schema, public/private, Redis cache | platform | Settings CRUD works |
| **1.11** | OpenAPI Config: Global + module-specific, RFC 9457 errors, Swagger UI | shared-kernel | Spec valid, UI loads |
| **1.12** | Error Handling: GlobalExceptionHandler, MapStruct mappers, pagination | shared-kernel | RFC 9457 format, DTOs map |
| **1.13** | Test Base: Testcontainers config, Contract base classes, data mothers | all | Base classes work |
| **1.14** | Angular Admin: Dashboard, tenant settings, feature flags, audit viewer | frontend | Admin UI functional |

### Phase 1 Exit Criteria
- [ ] Full auth flow: login → MFA → refresh → logout
- [ ] RBAC enforced on all endpoints
- [ ] ABAC works for OWN/TEAM/TENANT scopes
- [ ] Audit logs captured for auth/admin actions
- [ ] Multi-tenancy: queries filtered by tenant_id
- [ ] Feature flags toggle real-time
- [ ] OpenAPI spec generated and valid
- [ ] Contract tests passing
- [ ] 80%+ coverage on iam/platform
- [ ] Deployed to staging, smoke tests pass

---

## Phase 2: Ticketing Core (Weeks 7-12)

### Objective
Complete ticket lifecycle management with SLA, workflows, real-time updates, and search.

### Sprint 3 (Weeks 7-8): Ticket Domain & API

| Step | Tasks | Module | Deliverable |
|------|-------|--------|-------------|
| **2.1** | Ticket Domain: Incident/Problem/Change aggregates, state machine, events | ticketing | All types persist, transitions validated |
| **2.2** | Ticket CRUD API: GET/POST/PATCH, transitions, assign, comments, attachments, timeline | ticketing | Full CRUD via API |
| **2.3** | Assignment Logic: Round-robin, skills-based, workload-aware, reassign on deactivation | ticketing | Assignment strategies work |
| **2.4** | Comments & Attachments: Internal/public comments, S3 upload, ClamAV scan, thumbnails | ticketing | Attachments upload → scan → thumbnail |
| **2.5** | Time Tracking: Manual entry, start/stop timer, reports | ticketing | Time entries log correctly |
| **2.6** | Angular Ticket UI: List (virtual scroll, filters), Detail (timeline, transitions, rich text) | frontend | Full ticket UI works |

### Sprint 4 (Weeks 9-10): SLA & Workflow Engine

| Step | Tasks | Module | Deliverable |
|------|-------|--------|-------------|
| **2.7** | SLA Domain: Definitions, BusinessCalendar, EscalationRule, SlaBreach entities | sla | SLA definitions CRUD |
| **2.8** | SLA Calculation: Business hours, pause/resume, breach prediction (80%/90%), Redis cache | sla | SLA times accurate, breach events fire |
| **2.9** | Escalation Engine: Scheduled job, notify/reassign/webhook actions, levels | sla + notification | Escalations fire, actions execute |
| **2.10** | Ticket-SLA Integration: Auto-apply SLA, pause on hold, recalc on change | ticketing + sla | SLA auto-applies, timer pauses |
| **2.11** | Workflow Designer: Visual config, conditions, actions, versioning | ticketing | Custom workflows enforce transitions |
| **2.12** | SLA Dashboard: Compliance %, breaches, trends, agent/team performance | reporting + frontend | Dashboard shows real-time compliance |

### Sprint 5 (Weeks 11-12): Real-time & Search

| Step | Tasks | Module | Deliverable |
|------|-------|--------|-------------|
| **2.13** | WebSocket/SSE: STOMP over WS, SSE fallback, JWT auth, Redis session registry | notification | WS connects, SSE fallback works |
| **2.14** | Real-time Ticket Updates: Domain events → NotificationService → broadcast | ticketing + notification | Updates appear instantly |
| **2.15** | In-App Notifications: Bell icon, mark read, preferences, templates | notification | Notification center works |
| **2.16** | Email Notifications: Async, Thymeleaf, retry/backoff, DLQ | notification | Emails sent async, retries work |
| **2.17** | Full-Text Search: PostgreSQL tsvector, GIN index, highlighting, saved searches | ticketing | Search < 500ms, highlights work |
| **2.18** | Angular Real-time: Signals-based, WS/SSE auto-fallback, toasts, search UI | frontend | Real-time toasts, search with highlights |

### Phase 2 Exit Criteria
- [ ] Full ticket lifecycle: create → resolve → close
- [ ] State machine prevents invalid transitions
- [ ] Real-time updates work (WS + SSE fallback)
- [ ] Search returns relevant results < 500ms
- [ ] SLA calculations accurate (business hours, holidays)
- [ ] Escalations fire correctly at thresholds
- [ ] Workflow engine enforces custom transitions
- [ ] 80%+ coverage on ticketing/sla/notification
- [ ] Load test: 1000 concurrent users, P95 < 500ms

---

## Phase 3: Asset & Knowledge (Weeks 13-18)

### Objective
Implement CMDB with relationship graph, Knowledge Base with versioning/public portal, and integrations/reporting foundation.

### Sprint 6 (Weeks 13-14): Asset Management & CMDB

| Step | Tasks | Module | Deliverable |
|------|-------|--------|-------------|
| **3.1** | Asset Domain: Asset, CI Relationships (graph), SoftwareLicense, DiscoveryJob | asset | Assets CRUD, relationships enforce no cycles |
| **3.2** | Asset Lifecycle API: PROCURED→DEPLOYED→MAINTENANCE→RETIRED→DISPOSED, bulk import/export | asset | Lifecycle enforced, graph API returns D3.js data |
| **3.3** | License Compliance: Used vs total seats, status (COMPLIANT/AT_RISK/NON_COMPLIANT), alerts | asset | Compliance status accurate, alerts fire |
| **3.4** | Discovery Framework: Adapter pattern (SCCM/Jamf/Intune/AWS), deduplication | asset | Framework ready, mock adapter works |
| **3.5** | Angular Asset UI: Grid, Detail, D3.js force-directed graph, Import wizard | frontend | Asset UI complete, graph renders |

### Sprint 7 (Weeks 15-16): Knowledge Base

| Step | Tasks | Module | Deliverable |
|------|-------|--------|-------------|
| **3.6** | KB Domain: Article (versioning, workflow), Category tree, Tags, Translations, Feedback | knowledge | Articles version, workflow enforced |
| **3.7** | Rich Text Editor: TipTap integration, sanitization, images, SEO fields | knowledge | Editor saves sanitized HTML, versions tracked |
| **3.8** | Article Workflow: DRAFT→REVIEW→PUBLISH→ARCHIVE, approvals, scheduled publish | knowledge | Workflow enforced, scheduled publish works |
| **3.9** | KB Search & Analytics: Full-text, filters, suggestions, views/helpful% | knowledge | Search fast & relevant, analytics tracked |
| **3.10** | Article Suggestions: On ticket create/resolve, cross-module events | ticketing + knowledge | Suggestions appear on ticket create/resolve |
| **3.11** | Angular KB Internal: Editor (TipTap), Viewer, Category tree, Search | frontend | Full KB authoring flow |
| **3.12** | Public KB Portal: SEO-friendly, meta tags, sitemap, read-only, search | frontend | Public portal accessible, SEO tags present |

### Sprint 8 (Weeks 17-18): Integrations & Reporting Foundation

| Step | Tasks | Module | Deliverable |
|------|-------|--------|-------------|
| **3.13** | Outbound Webhooks: HMAC-SHA256, retry/backoff, DLQ, test delivery | integration | Webhooks deliver, HMAC verified |
| **3.14** | Jira Connector: Bi-sync, field mapping, webhook receiver, conflict resolution | integration | Tickets sync bi-directionally |
| **3.15** | Slack/Teams Bot: Slash commands, interactive messages, rich cards, OAuth | integration | Commands work, notifications render |
| **3.16** | Reporting CQRS: Event-driven projections, materialized views, pg_cron refresh | reporting | Read models updated, views fresh |
| **3.17** | Scheduled Reports: PDF/Excel/CSV, cron schedules, email/SFTP/S3 delivery | reporting | Exports generate, schedules run |
| **3.18** | Angular Integration/Reporting UI: Webhook config, connector config, dashboard builder | frontend | Integrations configurable, dashboard builder works |

### Phase 3 Exit Criteria
- [ ] Asset CMDB with relationship graph (D3.js)
- [ ] License compliance dashboard
- [ ] KB with versioning + public portal (SEO)
- [ ] Jira bi-sync working
- [ ] Slack/Teams notifications
- [ ] Custom dashboards with scheduled reports
- [ ] 80%+ coverage on asset/knowledge/integration/reporting
- [ ] All modules integrated, cross-module events working

---

## Phase 4: Hardening & Production Ready (Weeks 19-22)

### Objective
Performance optimization, security hardening, compliance, disaster recovery, and production launch.

### Sprint 9 (Weeks 19-20): Performance, Scale, Reliability

| Step | Tasks | Deliverable |
|------|-------|-------------|
| **4.1** | Load Testing: k6 scripts (ticket CRUD, search, WS, reports), stages: ramp→sustained→stress | Baseline established, bottlenecks identified |
| **4.2** | Query Optimization: pg_stat_statements, missing indexes, N+1 fixes, HikariCP tuning | Slow queries optimized, P95 improved |
| **4.3** | Caching Strategy: Redis for sessions, details, SLA, reports, flags; pub/sub invalidation | Cache hit rate > 80%, invalidation works |
| **4.4** | CDN & Frontend Optimization: CloudFront, compression, code splitting, bundle analysis | Lighthouse > 90, chunks < 200KB gzipped |
| **4.5** | Read Replicas & PgBouncer: RDS replica, PgBouncer sidecar, route reporting queries | Replica handles reporting, lag < 1s |
| **4.6** | Chaos Engineering: Pod kill, network latency/partition, CPU stress, disk fill | All experiments pass, recovery < 30s |
| **4.7** | HPA/VPA Tuning: Custom metrics (http_rps), VPA recommendations, Cluster Autoscaler | Smooth scaling, no thrashing |
| **4.8** | Frontend Performance: Lazy routes, quicklink preload, virtual scroll, bundle budgets | Initial load < 2s, budgets met |

### Sprint 10 (Weeks 21-22): Security, Compliance, Launch

| Step | Tasks | Deliverable |
|------|-------|-------------|
| **4.9** | Penetration Testing: External (OWASP Top 10, API, auth bypass), remediate, retest | No CRITICAL/HIGH findings |
| **4.10** | SOC 2 Evidence: Access control, encryption, change management, incident response, monitoring | Auditor-ready documentation |
| **4.11** | GDPR Compliance: Right to erasure (anonymize), data portability (JSON export), consent, DPIA | Erasure/export flows work |
| **4.12** | DR Drill: RDS cross-region promotion, S3 replication, EKS rebuild, ArgoCD sync | RTO < 1hr, RPO < 5min |
| **4.13** | Runbooks: Deploy, rollback, scale, DB failover, Redis failover, cert/secret rotation, SEV levels | All critical paths documented, tested |
| **4.14** | Production Deploy: Blue-green backend, rolling frontend, pre-deploy validation, smoke tests | Blue-green succeeds, rollback tested |
| **4.15** | Post-Launch: SLO dashboards, error budget alerts, on-call rotation (PagerDuty), business KPIs | SLO dashboards live, on-call active |

### Phase 4 Exit Criteria (Launch Ready)
- [ ] Load test: 10K concurrent users, P95 < 500ms, error rate < 0.1%
- [ ] Chaos tests pass: no data loss, < 30s recovery
- [ ] Pen test: no critical/high findings
- [ ] DR drill: RTO < 1hr, RPO < 5min
- [ ] All runbooks documented and tested
- [ ] Blue-green deployment to prod successful
- [ ] SLO dashboards live with error budget alerts
- [ ] Team on-call rotation established

---

## Milestone Summary

| Milestone | Target Week | Key Deliverable | Success Metric |
|-----------|-------------|-----------------|----------------|
| **M0: Foundation Ready** | 2 | Dev env, CI/CD, Infra, Arch guardrails | Deploy to dev in < 10 min |
| **M1: Core Platform** | 6 | Auth, RBAC, Tenancy, Admin UI | Login + MFA + Admin works |
| **M2: Ticketing MVP** | 12 | Full ticket lifecycle, SLA, Real-time | Create→Resolve→Close in UI |
| **M3: Asset & Knowledge** | 18 | CMDB, KB, Integrations, Reports | Assets linked, KB searchable |
| **M4: Production Launch** | 22 | Hardened, compliant, monitored | 10K users, P95<500ms, 99.9% uptime |

---

## Team Capacity & Roles

| Role | Count | Responsibilities |
|------|-------|------------------|
| Backend Engineers | 3 | Module implementation, APIs, domain logic, tests |
| Frontend Engineers | 3 | Components, state management, E2E tests |
| DevOps Engineers | 2 | Infrastructure, CI/CD, monitoring, security |
| QA Engineer | 1 | Test automation, contract tests, E2E, performance |
| Product Manager | 1 | Backlog grooming, stakeholder demos, release coordination |
| Tech Lead | 1 | Architecture decisions, code reviews, cross-team coordination |

### Sprint Capacity (2-week sprints)
- Backend: ~60 story points/sprint
- Frontend: ~60 story points/sprint
- DevOps: Infrastructure, CI/CD, monitoring
- QA: Test automation, contract, E2E
- 20% tech debt budget per sprint

---

## Risk Mitigation

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| Spring Modulith boundaries erosion | Medium | High | ArchUnit in CI from day 1, prototype in Phase 0 |
| Team velocity unknown | High | High | Measure in Phase 0, adjust scope at M1 |
| DB performance at scale | Medium | High | Synthetic load test at M2, read replicas ready |
| Security audit findings | Medium | High | Shift-left: CodeQL/Trivy/DepCheck in every PR |
| Third-party API changes | Medium | Medium | Adapter pattern, mock in CI, circuit breakers |
| Angular Signals instability | Low | Medium | Use stable APIs, fallback to RxJS |
| Event consistency (dual-write) | Medium | Medium | TransactionalEventPublisher, idempotent consumers |
| Multi-tenancy data leakage | Low | High | TenantContext + Hibernate Filter from day 1 |

---

## Definition of Done (Per Story)

- [ ] Code complete and self-reviewed
- [ ] Unit tests written (≥85% coverage for new code)
- [ ] Integration tests for repository/service layer
- [ ] Contract tests for API changes
- [ ] Documentation updated (OpenAPI, ADR if architectural)
- [ ] Code formatted (Spotless/Prettier)
- [ ] Lint passes (Checkstyle/SpotBugs/ESLint)
- [ ] Security scan passes (no new CRITICAL/HIGH)
- [ ] Peer reviewed (1 approval minimum)
- [ ] Merged to main, deployed to dev
- [ ] Smoke test passes in dev
- [ ] Deployed to staging (on main merge)