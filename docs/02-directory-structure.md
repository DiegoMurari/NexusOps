# NexusOps Directory Structure

Complete monorepo layout for the NexusOps platform.

## Root Structure

```
nexusops/
├── .github/                    # GitHub configuration
│   ├── workflows/              # GitHub Actions CI/CD pipelines
│   │   ├── ci.yml              # Main CI pipeline
│   │   ├── cd-dev.yml          # Dev deployment
│   │   ├── cd-staging.yml      # Staging deployment
│   │   ├── cd-production.yml   # Production deployment
│   │   └── security-scan.yml   # Security scanning
│   ├── actions/                # Reusable GitHub Actions
│   ├── dependabot.yml          # Dependabot configuration
│   └── CODEOWNERS              # Code ownership rules
├── .vscode/                    # VS Code workspace settings
│   ├── settings.json
│   ├── launch.json
│   └── extensions.json
├── docs/                       # Documentation
│   ├── adr/                    # Architecture Decision Records
│   ├── 02-directory-structure.md
│   ├── 03-backend-modules.md
│   ├── 04-api-design.md
│   ├── 05-database-design.md
│   ├── 06-auth-security.md
│   ├── 07-testing-strategy.md
│   ├── 08-docker-compose.md
│   ├── 09-ci-cd.md
│   ├── 10-kubernetes.md
│   ├── 11-aws-infrastructure.md
│   ├── 12-observability.md
│   ├── 13-security.md
│   ├── 14-phased-implementation.md
│   └── README.md
├── ci-cd/                      # CI/CD configuration
│   ├── docker/                 # Docker Compose files
│   │   ├── docker-compose.yml          # Base infrastructure
│   │   ├── docker-compose.dev.yml      # Dev override
│   │   ├── docker-compose.test.yml     # Test override
│   │   └── monitoring/                 # Prometheus, Grafana, Loki, Tempo configs
│   └── github-actions/         # Custom GitHub Actions
├── infrastructure/             # Terraform infrastructure
│   ├── environments/
│   │   ├── dev/                # Dev environment
│   │   │   ├── main.tf
│   │   │   ├── variables.tf
│   │   │   ├── outputs.tf
│   │   │   └── backend.tf
│   │   ├── staging/            # Staging environment
│   │   └── prod/               # Production environment
│   └── modules/                # Reusable Terraform modules
│       ├── vpc/
│       ├── eks/
│       ├── rds/
│       ├── elasticache/
│       ├── alb/
│       ├── s3/
│       ├── secrets/
│       ├── iam/
│       ├── kms/
│       ├── monitoring/
│       └── waf/
├── backend/                    # Spring Boot modular monolith
│   ├── pom.xml                 # Parent POM
│   ├── mvnw                    # Maven wrapper
│   ├── .mvn/                   # Maven wrapper files
│   ├── checkstyle.xml          # Checkstyle configuration
│   ├── spotless.xml            # Spotless configuration
│   ├── nexusops-shared-kernel/ # Shared kernel module
│   │   ├── pom.xml
│   │   └── src/
│   │       ├── main/
│   │       │   ├── java/com/nexusops/shared/
│   │       │   │   ├── config/         # OpenAPI, security config
│   │       │   │   ├── event/          # DomainEvent, TransactionalEventPublisher
│   │       │   │   ├── exception/      # BusinessException, ResourceNotFoundException, ValidationException
│   │       │   │   ├── security/       # JwtTokenProvider, NexusPermissionEvaluator, SecurityUtils
│   │       │   │   ├── tenancy/        # TenantContext
│   │       │   │   ├── audit/          # AuditListener
│   │       │   │   └── validation/     # Custom validators
│   │       │   └── resources/
│   │       │       ├── db/migration/   # Flyway migrations
│   │       │       └── META-INF/
│   │       └── test/
│   │           └── java/com/nexusops/shared/
│   ├── nexusops-iam/           # Identity & Access Management
│   │   ├── pom.xml
│   │   └── src/
│   │       ├── main/
│   │       │   ├── java/com/nexusops/iam/
│   │       │   │   ├── domain/
│   │       │   │   │   ├── model/          # User, Role, Permission, Tenant
│   │       │   │   │   ├── service/        # UserService, RoleService, MfaService
│   │       │   │   │   └── event/          # UserCreatedEvent, RoleAssignedEvent
│   │       │   │   ├── infrastructure/
│   │       │   │   │   ├── repository/     # Spring Data JPA repositories
│   │       │   │   │   ├── security/       # SecurityConfig, JwtAuthConverter
│   │       │   │   │   └── persistence/    # JPA entity mappings
│   │       │   │   ├── api/
│   │       │   │   │   ├── controller/     # AuthController, MfaController, UserController
│   │       │   │   │   ├── dto/            # Request/Response DTOs
│   │       │   │   │   └── mapper/         # MapStruct mappers
│   │       │   │   └── application/        # Application services
│   │       │   └── resources/
│   │       │       ├── db/migration/
│   │       │       └── application-iam.yml
│   │       └── test/
│   │           └── java/com/nexusops/iam/
│   ├── nexusops-platform/      # Platform Services
│   │   ├── pom.xml
│   │   └── src/
│   │       ├── main/
│   │       │   ├── java/com/nexusops/platform/
│   │       │   │   ├── domain/
│   │       │   │   │   ├── model/          # Tenant, FeatureFlag, SystemSetting, AuditLog
│   │       │   │   │   ├── service/        # TenantService, FeatureFlagService, SettingsService
│   │       │   │   │   └── event/          # TenantCreatedEvent, FeatureFlagChangedEvent
│   │       │   │   ├── infrastructure/
│   │       │   │   │   ├── tenancy/        # TenantInterceptor, Hibernate filters
│   │       │   │   │   ├── featureflag/    # Unleash client, cache
│   │       │   │   │   └── audit/          # AuditLogRepository, AuditListener
│   │       │   │   ├── api/
│   │       │   │   │   ├── controller/     # TenantController, FeatureFlagController, SettingsController
│   │       │   │   │   ├── dto/
│   │       │   │   │   └── mapper/
│   │       │   │   └── application/
│   │       │   └── resources/
│   │       │       ├── db/migration/
│   │       │       └── application-platform.yml
│   │       └── test/
│   │           └── java/com/nexusops/platform/
│   ├── nexusops-sla/           # Service Level Agreements
│   │   ├── pom.xml
│   │   └── src/
│   │       ├── main/
│   │       │   ├── java/com/nexusops/sla/
│   │       │   │   ├── domain/
│   │       │   │   │   ├── model/          # SlaDefinition, BusinessCalendar, EscalationRule, SlaBreach
│   │       │   │   │   ├── service/        # SlaCalculationService, EscalationService
│   │       │   │   │   └── event/          # SlaBreachImminentEvent, SlaBreachedEvent
│   │       │   │   ├── infrastructure/
│   │       │   │   │   ├── repository/
│   │       │   │   │   └── cache/          # Redis caching for SLA calculations
│   │       │   │   ├── api/
│   │       │   │   │   ├── controller/
│   │       │   │   │   ├── dto/
│   │       │   │   │   └── mapper/
│   │       │   │   └── application/
│   │       │   └── resources/
│   │       │       ├── db/migration/
│   │       │       └── application-sla.yml
│   │       └── test/
│   │           └── java/com/nexusops/sla/
│   ├── nexusops-ticketing/     # Ticket Management
│   │   ├── pom.xml
│   │   └── src/
│   │       ├── main/
│   │       │   ├── java/com/nexusops/ticketing/
│   │       │   │   ├── domain/
│   │       │   │   │   ├── model/          # Ticket, Incident, Problem, Change, Comment, Attachment, TimeEntry
│   │       │   │   │   ├── policy/         # State machine, transition policies
│   │       │   │   │   ├── service/        # TicketService, AssignmentService, WorkflowService
│   │       │   │   │   └── event/          # TicketCreatedEvent, StatusChangedEvent, CommentAddedEvent
│   │       │   │   ├── infrastructure/
│   │       │   │   │   ├── repository/
│   │       │   │   │   ├── storage/        # S3AttachmentService
│   │       │   │   │   └── search/         # TicketSearchRepository (PostgreSQL full-text)
│   │       │   │   ├── api/
│   │       │   │   │   ├── controller/     # TicketController, CommentController, AttachmentController
│   │       │   │   │   ├── dto/
│   │       │   │   │   └── mapper/
│   │       │   │   └── application/
│   │       │   └── resources/
│   │       │       ├── db/migration/
│   │       │       └── application-ticketing.yml
│   │       └── test/
│   │           └── java/com/nexusops/ticketing/
│   ├── nexusops-asset/         # Asset Management & CMDB
│   │   ├── pom.xml
│   │   └── src/
│   │       ├── main/
│   │       │   ├── java/com/nexusops/asset/
│   │       │   │   ├── domain/
│   │       │   │   │   ├── model/          # Asset, CIRelationship, SoftwareLicense, DiscoveryJob, Location
│   │       │   │   │   ├── service/        # AssetService, LicenseComplianceService, DiscoveryService
│   │       │   │   │   └── event/          # AssetCreatedEvent, RelationshipChangedEvent
│   │       │   │   ├── infrastructure/
│   │       │   │   │   ├── repository/
│   │       │   │   │   ├── discovery/      # DiscoveryAdapter, SCCM/Jamf/Intune/AWS adapters
│   │       │   │   │   └── graph/          # CI relationship graph service
│   │       │   │   ├── api/
│   │       │   │   │   ├── controller/
│   │       │   │   │   ├── dto/
│   │       │   │   │   └── mapper/
│   │       │   │   └── application/
│   │       │   └── resources/
│   │       │       ├── db/migration/
│   │       │       └── application-asset.yml
│   │       └── test/
│   │           └── java/com/nexusops/asset/
│   ├── nexusops-knowledge/     # Knowledge Base
│   │   ├── pom.xml
│   │   └── src/
│   │       ├── main/
│   │       │   ├── java/com/nexusops/knowledge/
│   │       │   │   ├── domain/
│   │       │   │   │   ├── model/          # Article, Category, Tag, ArticleTranslation, ArticleFeedback
│   │       │   │   │   ├── service/        # ArticleService, ArticleWorkflowService, ArticleSearchService
│   │       │   │   │   └── event/          # ArticlePublishedEvent, ArticleFeedbackEvent
│   │       │   │   ├── infrastructure/
│   │       │   │   │   ├── repository/
│   │       │   │   │   ├── search/         # ArticleSearchRepository
│   │       │   │   │   └── content/        # TipTap integration, sanitization
│   │       │   │   ├── api/
│   │       │   │   │   ├── controller/
│   │       │   │   │   ├── dto/
│   │       │   │   │   └── mapper/
│   │       │   │   └── application/
│   │       │   └── resources/
│   │       │       ├── db/migration/
│   │       │       └── application-knowledge.yml
│   │       └── test/
│   │           └── java/com/nexusops/knowledge/
│   ├── nexusops-notification/  # Notifications
│   │   ├── pom.xml
│   │   └── src/
│   │       ├── main/
│   │       │   ├── java/com/nexusops/notification/
│   │       │   │   ├── domain/
│   │       │   │   │   ├── model/          # Notification, Template, Subscription, Preference
│   │       │   │   │   ├── service/        # NotificationService, EmailService, WebSocketService
│   │       │   │   │   └── event/          # NotificationCreatedEvent
│   │       │   │   ├── infrastructure/
│   │       │   │   │   ├── repository/
│   │       │   │   │   ├── email/          # Thymeleaf templates, async sending
│   │       │   │   │   ├── websocket/      # STOMP over WebSocket, SSE fallback
│   │       │   │   │   └── push/           # FCM/APNs for mobile
│   │       │   │   ├── api/
│   │       │   │   │   ├── controller/
│   │       │   │   │   ├── dto/
│   │       │   │   │   └── mapper/
│   │       │   │   └── application/
│   │       │   └── resources/
│   │       │       ├── db/migration/
│   │       │       ├── templates/          # Thymeleaf email templates
│   │       │       └── application-notification.yml
│   │       └── test/
│   │           └── java/com/nexusops/notification/
│   ├── nexusops-reporting/     # Reporting & Analytics
│   │   ├── pom.xml
│   │   └── src/
│   │       ├── main/
│   │       │   ├── java/com/nexusops/reporting/
│   │       │   │   ├── domain/
│   │       │   │   │   ├── model/          # Report, Dashboard, Widget, ScheduledReport
│   │       │   │   │   ├── service/        # ReportService, DashboardService, ExportService
│   │       │   │   │   └── event/          # ReportGeneratedEvent
│   │       │   │   ├── infrastructure/
│   │       │   │   │   ├── query/          # Read models, materialized views
│   │       │   │   │   ├── projector/      # Event-driven projection
│   │       │   │   │   └── export/         # PDF (iText), Excel (POI), CSV
│   │       │   │   ├── api/
│   │       │   │   │   ├── controller/
│   │       │   │   │   ├── dto/
│   │       │   │   │   └── mapper/
│   │       │   │   └── application/
│   │       │   └── resources/
│   │       │       ├── db/migration/
│   │       │       └── application-reporting.yml
│   │       └── test/
│   │           └── java/com/nexusops/reporting/
│   ├── nexusops-integration/   # External Integrations
│   │   ├── pom.xml
│   │   └── src/
│   │       ├── main/
│   │       │   ├── java/com/nexusops/integration/
│   │       │   │   ├── domain/
│   │       │   │   │   ├── model/          # Webhook, Connector, SyncJob
│   │       │   │   │   ├── service/        # WebhookDeliveryService, JiraConnector, SlackConnector
│   │       │   │   │   └── event/          # WebhookDeliveredEvent
│   │       │   │   ├── infrastructure/
│   │       │   │   │   ├── repository/
│   │       │   │   │   ├── webhook/        # WebhookDeliveryService
│   │       │   │   │   └── connector/      # Jira, Slack, Teams adapters
│   │       │   │   ├── api/
│   │       │   │   │   ├── controller/
│   │       │   │   │   ├── dto/
│   │       │   │   │   └── mapper/
│   │       │   │   └── application/
│   │       │   └── resources/
│   │       │       ├── db/migration/
│   │       │       └── application-integration.yml
│   │       └── test/
│   │           └── java/com/nexusops/integration/
│   └── nexusops-bootstrap/     # Application Entry Point
│       ├── pom.xml
│       └── src/
│           ├── main/
│           │   ├── java/com/nexusops/bootstrap/
│           │   │   └── NexusOpsApplication.java
│           │   └── resources/
│           │       ├── application.yml
│           │       ├── application-dev.yml
│           │       ├── application-test.yml
│           │       └── application-prod.yml
│           └── test/
│               └── java/com/nexusops/bootstrap/
├── frontend/                   # Angular 17+ Application
│   ├── package.json
│   ├── pnpm-lock.yaml
│   ├── angular.json
│   ├── tsconfig.json
│   ├── tsconfig.app.json
│   ├── tsconfig.spec.json
│   ├── .eslintrc.json
│   ├── .prettierrc
│   ├── .husky/                 # Husky git hooks
│   ├── nginx.conf              # Production nginx config
│   ├── Dockerfile              # Production Dockerfile
│   ├── Dockerfile.dev          # Development Dockerfile
│   ├── src/
│   │   ├── index.html
│   │   ├── main.ts
│   │   ├── styles.scss
│   │   ├── environments/
│   │   │   ├── environment.ts
│   │   │   └── environment.prod.ts
│   │   ├── app/
│   │   │   ├── app.component.ts
│   │   │   ├── app.routes.ts
│   │   │   ├── core/                    # Core services, guards, interceptors
│   │   │   │   ├── auth/                # AuthService, AuthGuard, MfaGuard, RoleGuard
│   │   │   │   ├── http/                # AuthInterceptor, ErrorInterceptor
│   │   │   │   ├── notification/        # NotificationService (WebSocket/SSE)
│   │   │   │   └── seo/                 # MetaService, SitemapGenerator
│   │   │   ├── features/                # Feature modules (lazy-loaded)
│   │   │   │   ├── dashboard/           # DashboardComponent
│   │   │   │   ├── ticketing/           # TicketList, TicketDetail, TicketCreate, Timeline
│   │   │   │   ├── assets/              # AssetGrid, AssetDetail, RelationshipGraph
│   │   │   │   ├── knowledge/           # Editor, Viewer, CategoryTree, Search, PublicPortal
│   │   │   │   ├── sla/                 # SLADashboard, BreachView
│   │   │   │   ├── reporting/           # DashboardBuilder, ReportViewer, ScheduledReports
│   │   │   │   ├── administration/      # Login, MFA, UserManagement, TenantSettings, AuditLog
│   │   │   │   └── integrations/        # WebhookConfig, ConnectorConfig, JiraMapping
│   │   │   └── shared/                  # Shared components, directives, pipes
│   │   │       ├── components/          # DataTable, Form, Modal, Toast, FileUpload, RichTextEditor
│   │   │       ├── directives/
│   │   │       ├── pipes/
│   │   │       ├── guards/
│   │   │       ├── interceptors/
│   │   │       ├── models/              # TypeScript interfaces
│   │   │       └── services/
│   │   ├── assets/                      # Static assets
│   │   └── environments/
│   └── tests/                          # Playwright E2E tests
│       ├── e2e/
│       │   ├── auth.spec.ts
│       │   ├── ticket-lifecycle.spec.ts
│       │   ├── admin.spec.ts
│       │   └── utils/
│       └── playwright.config.ts
├── k8s/                          # Kubernetes manifests (Kustomize)
│   ├── base/                     # Base manifests
│   │   ├── namespace.yaml
│   │   ├── backend-deployment.yaml
│   │   ├── frontend-deployment.yaml
│   │   ├── backend-service.yaml
│   │   ├── frontend-service.yaml
│   │   ├── ingress.yaml
│   │   ├── configmap.yaml
│   │   ├── secret.yaml
│   │   ├── hpa.yaml
│   │   ├── network-policy.yaml
│   │   ├── pod-disruption-budget.yaml
│   │   ├── service-monitor.yaml
│   │   └── kustomization.yaml
│   ├── overlays/                 # Environment overlays
│   │   ├── dev/
│   │   │   └── kustomization.yaml
│   │   ├── staging/
│   │   │   └── kustomization.yaml
│   │   └── prod/
│   │       └── kustomization.yaml
│   └── argocd/                   # ArgoCD applications
│       ├── applications.yaml
│       └── project.yaml
├── tests/                        # Cross-cutting tests
│   ├── performance/              # k6 load tests
│   │   ├── k6/
│   │   │   ├── ticket-api.js
│   │   │   ├── asset-api.js
│   │   │   ├── ws-load.js
│   │   │   └── thresholds.js
│   │   └── README.md
│   ├── contract/                 # Contract test definitions
│   │   ├── producer/
│   │   └── consumer/
│   └── chaos/                    # Chaos engineering experiments
│       └── litmus/
├── CONTRIBUTING.md               # Contribution guidelines
├── LICENSE                       # Apache 2.0 License
├── README.md                     # Project overview
└── .gitignore                    # Git ignore rules
```

## Key Principles

### Modular Monolith Structure
- Each backend module is a separate Maven module
- Module boundaries enforced by ArchUnit
- Shared kernel for cross-cutting concerns
- Domain events for inter-module communication

### Feature-Based Frontend
- Lazy-loaded feature modules
- Core services in `core/`
- Shared UI components in `shared/`
- Standalone components with Signals

### Infrastructure as Code
- Terraform modules for each AWS resource
- Environment-specific configurations
- Kustomize for Kubernetes manifests
- ArgoCD for GitOps deployments

### Documentation as Code
- ADRs for architectural decisions
- Technical specs alongside code
- Auto-generated API docs (OpenAPI)