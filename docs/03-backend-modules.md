# NexusOps Backend Modules

Complete breakdown of the 10 bounded contexts in the NexusOps modular monolith.

## Module Dependency Graph

```
nexusops-shared-kernel (no dependencies)
       ▲
       │
nexusops-iam ◄──────────────────────┐
       ▲                             │
       │                             │
nexusops-platform ◄─────────────────┤
       ▲                             │
       │                             │
nexusops-sla ◄──────────────────────┤
       ▲                             │
       │                             │
nexusops-ticketing ◄────────────────┤
       ▲                             │
       │                             │
nexusops-asset ◄────────────────────┤
       ▲                             │
       │                             │
nexusops-knowledge ◄────────────────┤
       ▲                             │
       │                             │
nexusops-notification ◄─────────────┤
       ▲                             │
       │                             │
nexusops-reporting ◄────────────────┤
       ▲                             │
       │                             │
nexusops-integration ◄──────────────┤
       ▲                             │
       │                             │
nexusops-bootstrap (depends on ALL) ┘
```

## 1. nexusops-shared-kernel

**Purpose**: Cross-cutting concerns shared by all modules

### Key Components

| Package | Responsibility |
|---------|----------------|
| `config` | OpenAPI configuration, global exception handler |
| `event` | `DomainEvent` base class, `TransactionalEventPublisher` |
| `exception` | `BusinessException`, `ResourceNotFoundException`, `ValidationException`, `GlobalExceptionHandler` |
| `security` | `JwtTokenProvider` (RS256), `NexusPermissionEvaluator` (ABAC), `SecurityUtils` |
| `tenancy` | `TenantContext` (ThreadLocal + Hibernate Filter) |
| `audit` | `AuditListener` (consumes domain events → audit logs) |
| `validation` | Custom Bean Validation annotations |

### Public API
- `DomainEvent` - Base class for all domain events
- `TransactionalEventPublisher` - Publishes events after transaction commit
- `JwtTokenProvider` - JWT generation/validation (RS256)
- `NexusPermissionEvaluator` - ABAC permission evaluation
- `TenantContext` - Current tenant resolution
- `SecurityUtils` - Current user/role/permission helpers

### ArchUnit Rules
```java
@ArchTest
static final ArchRule shared_kernel_should_not_depend_on_modules =
    noClasses().that().resideInAPackage("..shared..")
        .should().accessClassesThat().resideInAnyPackage(
            "..iam..", "..ticketing..", "..asset..", "..knowledge..",
            "..sla..", "..notification..", "..reporting..", "..integration..", "..platform..");
```

---

## 2. nexusops-iam

**Purpose**: Identity & Access Management - Authentication, Authorization, MFA, User Management

### Domain Model

| Entity | Description |
|--------|-------------|
| `User` | Email, passwordHash, status, MFA fields, tenantId |
| `Role` | Name, description, permissions set |
| `Permission` | Resource:action:scope (e.g., `TICKET:READ:TENANT`) |
| `Tenant` | Name, domain, settings, subscription |

### Key Services

| Service | Responsibility |
|---------|----------------|
| `UserService` | CRUD, password reset, activation/deactivation |
| `RoleService` | Role CRUD, permission assignment |
| `MfaService` | TOTP setup/verify/disable, recovery codes |
| `AuthService` | Login, refresh, logout, token revocation |
| `PermissionService` | Permission evaluation, scope resolution |

### Domain Events
| Event | Trigger | Consumers |
|-------|---------|-----------|
| `UserCreatedEvent` | User registered | Platform (tenant setup), Notification |
| `UserDeactivatedEvent` | User deactivated | Ticketing (reassign), Asset (reassign) |
| `RoleAssignedEvent` | Role assigned to user | Audit, Notification |
| `MfaEnabledEvent` | MFA enabled | Audit, Security monitoring |

### REST API
| Endpoint | Method | Description |
|----------|--------|-------------|
| `/auth/login` | POST | Username/password + optional MFA |
| `/auth/mfa/verify` | POST | Verify TOTP code |
| `/auth/refresh` | POST | Rotate refresh token |
| `/auth/logout` | POST | Single session logout |
| `/auth/logout-all` | POST | All sessions logout |
| `/auth/mfa/setup` | POST | Initiate MFA setup (returns QR) |
| `/auth/mfa/enable` | POST | Enable MFA with code |
| `/auth/mfa/disable` | POST | Disable MFA with password |
| `/users` | GET/POST | List/create users |
| `/users/{id}` | GET/PATCH/DELETE | User CRUD |
| `/users/{id}/roles` | POST | Assign roles |
| `/roles` | GET/POST | Role CRUD |
| `/permissions` | GET | List all permissions |

### Security Configuration
- Spring Security filter chain (stateless JWT)
- `JwtAuthConverter` - Extracts roles/permissions from JWT
- Method security: `@PreAuthorize("hasPermission(#id, 'TICKET', 'READ')")`
- Default roles: `END_USER`, `AGENT`, `TEAM_LEAD`, `MANAGER`, `ADMIN`, `SUPER_ADMIN`

---

## 3. nexusops-platform

**Purpose**: Platform services - Multi-tenancy, Feature Flags, System Settings, Audit Logs

### Domain Model

| Entity | Description |
|--------|-------------|
| `Tenant` | Name, domain, settings (JSONB), subscription, status |
| `FeatureFlag` | Key, enabled, rollout%, targeting rules (JSONB), variants |
| `SystemSetting` | Key, typed value (string/number/boolean/JSON), public/private |
| `AuditLog` | Append-only, partitioned by month, event_id, payload (JSONB) |

### Key Services

| Service | Responsibility |
|---------|----------------|
| `TenantService` | Provisioning, settings, domain mapping |
| `FeatureFlagService` | Evaluation, rollout, targeting (Unleash client) |
| `SettingsService` | CRUD with JSON Schema validation, Redis cache |
| `AuditService` | Query, export, compliance reporting |

### Domain Events
| Event | Trigger | Consumers |
|-------|---------|-----------|
| `TenantCreatedEvent` | Tenant provisioned | IAM (default roles), Notification |
| `FeatureFlagChangedEvent` | Flag toggled | All modules (cache invalidation) |
| `SystemSettingChangedEvent` | Setting changed | Relevant modules |

### REST API
| Endpoint | Method | Description |
|----------|--------|-------------|
| `/tenants` | GET/POST | List/provision tenants (SUPER_ADMIN) |
| `/tenants/{id}` | GET/PATCH/DELETE | Tenant CRUD |
| `/feature-flags` | GET/POST | List/create flags |
| `/feature-flags/{key}` | GET/PATCH/DELETE | Flag CRUD |
| `/feature-flags/{key}/evaluate` | POST | Evaluate flag for context |
| `/settings` | GET/POST | System settings (private) |
| `/settings/public` | GET | Public settings (no auth) |
| `/audit-logs` | GET | Query audit logs (ADMIN) |

### Multi-tenancy Implementation
```java
// TenantContext from JWT
@Component
public class TenantInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String tenantId = JwtUtil.extractTenantId(request);
        TenantContext.setTenantId(tenantId);
        return true;
    }
}

// Hibernate Filter on TenantAware entities
@Entity
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = "string"))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class Ticket implements TenantAware {
    @Column(name = "tenant_id", nullable = false)
    private String tenantId;
}
```

---

## 4. nexusops-sla

**Purpose**: Service Level Agreements - Definitions, Calculations, Escalations, Business Calendars

### Domain Model

| Entity | Description |
|--------|-------------|
| `SlaDefinition` | Applies to (type/category/priority/customerTier), response/resolution time, calendar |
| `BusinessCalendar` | Working hours, holidays, exceptions, timezone |
| `EscalationRule` | Trigger (response/resolution/percentage), actions (notify/reassign/webhook) |
| `SlaBreach` | Ticket reference, breach type, timestamp, acknowledged, escalated |

### Key Services

| Service | Responsibility |
|---------|----------------|
| `SlaCalculationService` | Business hours calculation, pause/resume, breach prediction, Redis caching |
| `EscalationService` | Scheduled job (every minute), evaluates breaches, executes actions |
| `SlaDefinitionService` | CRUD, validation, versioning |

### Domain Events
| Event | Trigger | Consumers |
|-------|---------|-----------|
| `SlaDefinitionChangedEvent` | SLA created/updated | Ticketing (recalc) |
| `SlaBreachImminentEvent` | 80%/90% threshold reached | Notification (alert), Escalation |
| `SlaBreachedEvent` | SLA breached | Notification, Escalation, Reporting |

### REST API
| Endpoint | Method | Description |
|----------|--------|-------------|
| `/sla/definitions` | GET/POST | SLA definitions CRUD |
| `/sla/definitions/{id}` | GET/PATCH/DELETE | Definition CRUD |
| `/sla/calendars` | GET/POST | Business calendars CRUD |
| `/sla/escalation-rules` | GET/POST | Escalation rules CRUD |
| `/sla/breaches` | GET | Active breaches |
| `/sla/breaches/{id}/acknowledge` | POST | Acknowledge breach |
| `/sla/compliance` | GET | Compliance dashboard data |

### SLA Calculation
- Business hours only (calendar-aware)
- Pause on `ON_HOLD` status
- Stop response timer on first public comment
- Breach prediction at 80% and 90% thresholds
- Cached in Redis (1min TTL)

---

## 5. nexusops-ticketing

**Purpose**: Ticket Management - Incidents, Problems, Changes, Comments, Attachments, Time Tracking, Workflows

### Domain Model

| Entity | Description |
|--------|-------------|
| `Ticket` (abstract) | Aggregate root, ticketNumber, title, description, status, priority, category, assignee, reporter, tenant |
| `Incident` | Extends Ticket, urgency, impact, ciReference |
| `Problem` | Extends Ticket, rootCause, knownError, workaround |
| `Change` | Extends Ticket, riskLevel, implementationPlan, backoutPlan, changeWindow |
| `Comment` | Internal/public, mentions, markdown/html |
| `Attachment` | S3 key, virus scan status, thumbnails |
| `TimeEntry` | Start/end, duration, billable, description |
| `Category` | Hierarchical, SLA mapping |

### State Machine
```
OPEN → IN_PROGRESS → WAITING → IN_PROGRESS → RESOLVED → CLOSED
                    ↓              ↓
              ON_HOLD ←──────────┘
                    ↓
              REOPENED → IN_PROGRESS
```

### Key Services

| Service | Responsibility |
|---------|----------------|
| `TicketService` | CRUD, transitions, assignment, search |
| `AssignmentService` | Round-robin, skills-based, workload-aware |
| `WorkflowService` | Custom workflows, conditions, actions |
| `CommentService` | Comments, mentions, public/internal |
| `AttachmentService` | S3 upload, ClamAV scan, thumbnails |
| `TimeTrackingService` | Manual entry, timer, reports |
| `TicketSearchService` | Full-text search (PostgreSQL tsvector) |

### Domain Events
| Event | Trigger | Consumers |
|-------|---------|-----------|
| `TicketCreatedEvent` | Ticket created | SLA (start timer), Notification, Reporting |
| `TicketAssignedEvent` | Assignee changed | Notification, Reporting |
| `TicketStatusChangedEvent` | Status transition | SLA (pause/resume), Notification, Reporting |
| `CommentAddedEvent` | Comment added | Notification (mentions), Reporting |
| `AttachmentAddedEvent` | Attachment uploaded | Notification, Reporting |
| `TicketResolvedEvent` | Resolved | Knowledge (suggest article), SLA (stop), Reporting |
| `TicketClosedEvent` | Closed | Reporting |

### REST API
| Endpoint | Method | Description |
|----------|--------|-------------|
| `/tickets` | GET/POST | List (paginated, filtered) / Create |
| `/tickets/{id}` | GET/PATCH | Read / Update |
| `/tickets/{id}/transition` | POST | State transition |
| `/tickets/{id}/assign` | POST | Assign to user/group |
| `/tickets/{id}/comments` | GET/POST | Comments |
| `/tickets/{id}/attachments` | GET/POST | Attachments (presigned URLs) |
| `/tickets/{id}/timeline` | GET | Unified timeline |
| `/tickets/{id}/time-entries` | GET/POST | Time tracking |
| `/tickets/export` | POST | Export (CSV/Excel/PDF) |
| `/tickets/search` | GET | Full-text search |

### Workflow Engine
- JSON-based workflow definition
- Conditions: field values, user roles, custom SpEL scripts
- Actions: set field, notify, webhook, create task
- Versioning with migration support

---

## 6. nexusops-asset

**Purpose**: CMDB - Assets, CI Relationships, Software Licenses, Discovery

### Domain Model

| Entity | Description |
|--------|-------------|
| `Asset` | Type (HW/SW/Cloud/Virtual), specs (JSONB), lifecycle status, location |
| `CIRelationship` | Graph: DEPENDS_ON, CONNECTED_TO, HOSTS, CONTAINS (no cycles) |
| `SoftwareLicense` | Product, seats, compliance status (computed), entitlements (JSONB) |
| `DiscoveryJob` | Source, schedule, status, last run, normalization rules |
| `Location` | Hierarchical (datacenter → rack → unit) |

### Key Services

| Service | Responsibility |
|---------|----------------|
| `AssetService` | CRUD, lifecycle (PROCURED→DEPLOYED→MAINTENANCE→RETIRED→DISPOSED) |
| `RelationshipService` | Graph operations, cycle detection, D3.js export |
| `LicenseComplianceService` | Used vs total seats, status (COMPLIANT/AT_RISK/AT_CAPACITY/NON_COMPLIANT) |
| `DiscoveryService` | Adapter framework, normalization, deduplication, change detection |

### Domain Events
| Event | Trigger | Consumers |
|-------|---------|-----------|
| `AssetCreatedEvent` | Asset created | Ticketing (CI reference), Reporting |
| `AssetLifecycleChangedEvent` | Status changed | Reporting, Notification |
| `RelationshipChangedEvent` | CI relationship added/removed | Reporting |
| `LicenseComplianceBreachedEvent` | Non-compliant | Notification, Reporting |

### REST API
| Endpoint | Method | Description |
|----------|--------|-------------|
| `/assets` | GET/POST | List (virtual scroll, filters) / Create |
| `/assets/{id}` | GET/PATCH/DELETE | Asset CRUD |
| `/assets/{id}/relationships` | GET/POST | CI relationship graph |
| `/assets/import` | POST | Bulk import (CSV/Excel with validation) |
| `/assets/export` | GET | Export (CSV/Excel) |
| `/licenses` | GET/POST | License CRUD |
| `/licenses/compliance` | GET | Compliance dashboard |
| `/discovery/jobs` | GET/POST | Discovery jobs |

### Discovery Framework
```java
public interface DiscoveryAdapter {
    String getSourceType(); // SCCM, JAMF, INTUNE, AWS
    List<Asset> discover();
    Asset normalize(RawAsset raw);
}

@Component
public class DiscoveryService {
    private final List<DiscoveryAdapter> adapters;
    
    @Scheduled(cron = "${discovery.schedule:0 2 * * *}")
    public void runScheduledDiscovery() {
        for (DiscoveryAdapter adapter : adapters) {
            List<Asset> discovered = adapter.discover();
            for (Asset asset : discovered) {
                upsertAsset(asset);
            }
        }
    }
}
```

---

## 7. nexusops-knowledge

**Purpose**: Knowledge Base - Articles, Categories, Versioning, Public Portal, SEO

### Domain Model

| Entity | Description |
|--------|-------------|
| `Article` | Versioning, status (DRAFT/REVIEW/PUBLISHED/ARCHIVED), content (HTML/Markdown), translations (Map), SEO fields |
| `Category` | Hierarchical (self-referencing), description, icon |
| `Tag` | Simple tags for filtering |
| `ArticleTranslation` | Locale, title, content, SEO |
| `ArticleFeedback` | Helpful/not helpful, comment |

### Key Services

| Service | Responsibility |
|---------|----------------|
| `ArticleService` | CRUD, versioning, diff, content sanitization |
| `ArticleWorkflowService` | DRAFT→REVIEW→PUBLISH→ARCHIVE, approvals, scheduled publish |
| `ArticleSearchService` | Full-text search, filters, suggestions |
| `ArticleAnalyticsService` | Views, helpful%, feedback |
| `ArticleSuggestionService` | Cross-module suggestions for tickets |

### Domain Events
| Event | Trigger | Consumers |
|-------|---------|-----------|
| `ArticlePublishedEvent` | Article published | Notification, Reporting, Search index |
| `ArticleFeedbackEvent` | Feedback submitted | Reporting, Author notification |
| `ArticleSuggestionCreatedEvent` | Suggestion generated | Ticketing (UI) |

### REST API
| Endpoint | Method | Description |
|----------|--------|-------------|
| `/articles` | GET/POST | List (paginated, filtered) / Create draft |
| `/articles/{id}` | GET/PATCH/DELETE | Article CRUD |
| `/articles/{id}/versions` | GET | Version history |
| `/articles/{id}/publish` | POST | Publish workflow |
| `/articles/{id}/translate` | POST | Add translation |
| `/categories` | GET/POST | Category tree CRUD |
| `/tags` | GET/POST | Tags |
| `/search` | GET | Full-text search with filters |
| `/suggestions` | GET | Suggestions for ticket context |
| `/public/articles` | GET | Public portal (no auth) |
| `/public/categories` | GET | Public category tree |

### Public Portal
- Separate Angular routes (`/kb/*`)
- SEO: meta tags, structured data (Article), sitemap.xml
- Read-only, no authentication required
- CloudFront cached

---

## 8. nexusops-notification

**Purpose**: Notifications - Real-time (WebSocket/SSE), Email, In-App, Templates, Preferences

### Domain Model

| Entity | Description |
|--------|-------------|
| `Notification` | Recipient, channel (WS/EMAIL/IN_APP/PUSH), template, variables, priority, status |
| `Template` | Key, subject, Thymeleaf content (HTML + text), channel |
| `Subscription` | User, channel preferences, categories, quiet hours |

### Key Services

| Service | Responsibility |
|---------|----------------|
| `NotificationService` | Create, send, retry, DLQ |
| `WebSocketService` | STOMP over WebSocket, JWT auth, session registry (Redis) |
| `SseService` | Server-Sent Events fallback |
| `EmailService` | Async (@Async), Thymeleaf templates, retry/backoff, DLQ |
| `TemplateService` | Thymeleaf rendering, management API |
| `PreferenceService` | User notification preferences |

### Real-time Architecture
```
WebSocket (STOMP) Primary
    ├── /ws/notifications (JWT in handshake)
    ├── /user/queue/* (user-specific)
    └── /topic/tickets.{id}.updates (topic subscriptions)

SSE Fallback
    └── /api/v1/notifications/stream (JWT query param)
```

### Domain Events
| Event | Trigger | Consumers |
|-------|---------|-----------|
| `NotificationCreatedEvent` | Notification queued | WebSocket/SSE broadcast |
| `EmailDeliveryFailedEvent` | Max retries exceeded | DLQ, Alert |

### REST API
| Endpoint | Method | Description |
|----------|--------|-------------|
| `/notifications` | GET | List (paginated, filtered) |
| `/notifications/unread-count` | GET | Unread count |
| `/notifications/{id}/read` | PATCH | Mark as read |
| `/notifications/read-all` | POST | Mark all as read |
| `/notifications/preferences` | GET/PATCH | User preferences |
| `/templates` | GET/POST | Template CRUD |
| `/templates/{key}/test` | POST | Test delivery |

---

## 9. nexusops-reporting

**Purpose**: Reporting & Analytics - CQRS Read Models, Dashboards, Scheduled Reports, Exports

### Domain Model

| Entity | Description |
|--------|-------------|
| `Report` | SQL/JPQL + parameters, visualization config (table/chart), sharing |
| `Dashboard` | Grid layout, widgets, sharing, scheduling |
| `Widget` | Type (metric/chart/table), query, visualization config |
| `ScheduledReport` | Cron, timezone, recipients, format, delivery (email/SFTP/S3) |
| `MaterializedView` | pg_cron refreshed, partitioned |

### CQRS Architecture
```
Domain Events (all modules)
        │
        ▼
┌───────────────────┐
│ TicketReadModelProjector │  ← Consumes all domain events
│ AssetReadModelProjector  │
│ SlaReadModelProjector    │
└───────────┬─────────┘
            │
            ▼
   Materialized Views
   (mv_daily_ticket_stats,
    mv_sla_compliance,
    mv_asset_utilization,
    mv_kb_analytics)
```

### Key Services

| Service | Responsibility |
|---------|----------------|
| `ReportService` | Execute, parameter binding, caching |
| `DashboardService` | CRUD, layout, sharing, widgets |
| `ExportService` | PDF (iText), Excel (POI), CSV |
| `ScheduledReportService` | Cron execution, delivery |

### Domain Events
| Event | Trigger | Consumers |
|-------|---------|-----------|
| `ReportGeneratedEvent` | Report executed | Notification (delivery) |
| `ScheduledReportCompletedEvent` | Schedule run | Notification, Audit |

### REST API
| Endpoint | Method | Description |
|----------|--------|-------------|
| `/reports` | GET/POST | Report CRUD |
| `/reports/{id}/execute` | POST | Execute with parameters |
| `/reports/{id}/export` | POST | Export (PDF/Excel/CSV) |
| `/dashboards` | GET/POST | Dashboard CRUD |
| `/dashboards/{id}/widgets` | GET/POST | Widget management |
| `/scheduled-reports` | GET/POST | Schedule CRUD |
| `/scheduled-reports/{id}/run` | POST | Manual trigger |

---

## 10. nexusops-integration

**Purpose**: External Integrations - Webhooks, Jira, Slack, Teams, Connectors

### Domain Model

| Entity | Description |
|--------|-------------|
| `Webhook` | Target URL, HMAC secret, events[], retry policy, status |
| `Connector` | Type (JIRA/SLACK/TEAMS), config (OAuth, field mapping), status |
| `SyncJob` | Connector, schedule, last run, status, conflicts |

### Key Services

| Service | Responsibility |
|---------|----------------|
| `WebhookDeliveryService` | Async delivery, HMAC-SHA256, exponential backoff, DLQ |
| `JiraConnector` | Bi-sync, field mapping, webhook receiver, conflict resolution |
| `SlackConnector` | Bolt SDK, slash commands, interactive messages, OAuth |
| `TeamsConnector` | Bot Framework, commands, notifications, OAuth |
| `SyncJobService` | Scheduled sync, conflict resolution (last-write-wins + manual) |

### Domain Events
| Event | Trigger | Consumers |
|-------|---------|-----------|
| `WebhookDeliveredEvent` | Delivery success/failed | Reporting, DLQ alert |
| `SyncCompletedEvent` | Sync job completed | Notification, Audit |

### REST API
| Endpoint | Method | Description |
|----------|--------|-------------|
| `/webhooks` | GET/POST | Webhook CRUD |
| `/webhooks/{id}/test` | POST | Test delivery |
| `/connectors` | GET/POST | Connector CRUD |
| `/connectors/{id}/sync` | POST | Manual sync |
| `/connectors/jira/mapping` | GET/PATCH | Field mapping |
| `/integrations/slack/oauth` | GET | OAuth callback |
| `/integrations/teams/oauth` | GET | OAuth callback |

---

## Cross-Module Communication

### Event Publishing
```java
@Service
@RequiredArgsConstructor
public class TicketService {
    private final TransactionalEventPublisher eventPublisher;
    
    public TicketDto createTicket(CreateTicketRequest request) {
        Ticket ticket = doCreateTicket(request);
        eventPublisher.publishAfterCommit(new TicketCreatedEvent(
            ticket.getId(), ticket.getTicketNumber(), ticket.getTenantId()
        ));
        return mapToDto(ticket);
    }
}
```

### Event Consumption
```java
@Component
@RequiredArgsConstructor
public class SlaTicketIntegration {
    private final SlaCalculationService slaService;
    
    @EventListener
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTicketCreated(TicketCreatedEvent event) {
        slaService.startSlaTimer(event.getTicketId());
    }
}
```

### Module Boundary Enforcement (ArchUnit)
```java
@ArchTest
static final ArchRule modules_should_only_access_shared_kernel_and_allowed_modules =
    classes().that().resideInAPackage("..ticketing..")
        .should().onlyBeAccessed().byClassesThat()
        .resideInAnyPackage(
            "..shared..",           // Shared kernel
            "..ticketing..",        // Self
            "..sla..",              // Allowed: SLA
            "..notification..",     // Allowed: Notifications
            "..reporting..",        // Allowed: Reporting
            "..integration..",      // Allowed: Integrations
            "..platform..",         // Allowed: Platform
            "..iam.."               // Allowed: IAM
        )
        .as("Ticketing should only be accessed by allowed modules");
```

---

## Database Schema per Module

| Module | Schema | Tables | Partitioning |
|--------|--------|--------|--------------|
| iam | `iam` | users, roles, permissions, tenants, mfa_secrets | - |
| platform | `platform` | tenants, feature_flags, system_settings, audit_logs | audit_logs (monthly) |
| sla | `sla` | sla_definitions, business_calendars, escalation_rules, sla_breaches | - |
| ticketing | `ticketing` | tickets, comments, attachments, time_entries, categories | - |
| asset | `asset` | assets, ci_relationships, software_licenses, discovery_jobs, locations | - |
| knowledge | `knowledge` | articles, categories, tags, article_translations, article_feedback | - |
| notification | `notification` | notifications, templates, subscriptions, preferences | - |
| reporting | `reporting` | reports, dashboards, widgets, scheduled_reports, materialized_views | - |
| integration | `integration` | webhooks, connectors, sync_jobs | - |

---

## Configuration

Each module can have its own `application-{module}.yml` for module-specific configuration, loaded via Spring's `@EnableConfigurationProperties`.

```yaml
# application-iam.yml
nexusops:
  iam:
    mfa:
      issuer: "NexusOps"
      window: 1
    password:
      min-length: 12
      breach-check: true
    session:
      max-concurrent: 5
```

```yaml
# application-ticketing.yml
nexusops:
  ticketing:
    attachment:
      max-size: 50MB
      allowed-types: ["image/*", "application/pdf", "text/*"]
      virus-scan: true
    search:
      highlight-fragments: 3
      max-results: 100
```

---

## Testing Strategy per Module

| Module | Unit Tests | Integration Tests | Contract Tests | ArchUnit |
|--------|------------|-------------------|----------------|----------|
| shared-kernel | ✅ Core utilities | ✅ Event publishing | - | ✅ |
| iam | ✅ Auth, MFA, permissions | ✅ Repositories, Security | ✅ Auth API | ✅ |
| platform | ✅ Tenancy, flags, settings | ✅ Hibernate filters | ✅ Tenant API | ✅ |
| sla | ✅ Calculation, calendar | ✅ Breach detection | ✅ SLA API | ✅ |
| ticketing | ✅ State machine, workflow | ✅ Full lifecycle | ✅ Ticket API | ✅ |
| asset | ✅ Graph, license compliance | ✅ Discovery framework | ✅ Asset API | ✅ |
| knowledge | ✅ Workflow, versioning | ✅ Search, suggestions | ✅ KB API | ✅ |
| notification | ✅ Templates, preferences | ✅ WS/SSE, email | ✅ Notification API | ✅ |
| reporting | ✅ Projections, exports | ✅ Materialized views | ✅ Report API | ✅ |
| integration | ✅ Webhooks, connectors | ✅ Jira/Slack sync | ✅ Webhook API | ✅ |