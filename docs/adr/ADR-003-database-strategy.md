# ADR-003: Database Strategy

## Status
Accepted

## Context
NexusOps requires a relational database with:
- ACID transactions for financial/compliance data
- Multi-tenancy support
- Complex queries (reporting, search)
- JSON document storage (settings, specs)
- Full-text search
- Horizontal scaling path

## Decision
Use **PostgreSQL 16** with **logical schemas per module** within a single database instance.

## Schema Strategy

### Logical Schemas per Module
```
nexusops (database)
├── iam              -- Users, roles, permissions, MFA
├── platform         -- Tenants, feature flags, settings, audit logs
├── sla              -- SLA definitions, calendars, escalations
├── ticketing        -- Tickets, comments, attachments, time entries
├── asset            -- Assets, CI relationships, licenses
├── knowledge        -- Articles, categories, translations
├── notification     -- Notifications, templates, preferences
├── reporting        -- Materialized views, report definitions
├── integration      -- Webhooks, connectors, sync jobs
└── audit            -- Cross-module audit logs (partitioned)
```

### Benefits
- **Isolation**: Module tables grouped logically
- **ACID**: Cross-module transactions possible when needed
- **Extraction**: Schemas map 1:1 to future microservice databases
- **Permissions**: Schema-level GRANT for module isolation
- **Operations**: Single backup, single connection pool

## Multi-Tenancy Approach

### Shared Database, Shared Schema (with Tenant ID)
- All tenant data in same tables
- `tenant_id` column on all tenant-scoped entities
- Hibernate Filters for automatic tenant filtering
- Row-level security policies for defense in depth

### Why Not Separate Databases/Schemas Per Tenant?
- Operational complexity (migrations, backups, connections)
- Cost (thousands of databases)
- Overkill for SaaS with moderate tenant count

### Implementation
```java
@Entity
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = "string"))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class Ticket implements TenantAware {
    @Column(name = "tenant_id", nullable = false)
    private String tenantId;
}
```

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

// Hibernate Filter activation
@Configuration
public class HibernateConfig {
    @PersistenceContext
    EntityManager entityManager;

    @PostConstruct
    public void enableTenantFilter() {
        Session session = entityManager.unwrap(Session.class);
        session.enableFilter("tenantFilter")
            .setParameter("tenantId", TenantContext.getTenantId());
    }
}
```

## Primary Keys

### UUIDv7 (Time-ordered UUID)
- **Format**: `018f0a1b-2c3d-7e8f-9a0b-c1d2e3f4a5b6`
- **Benefits**: 
  - Time-ordered (better index locality)
  - 48-bit timestamp + 74-bit random
  - No coordination needed
  - Sortable, URL-safe
- **Implementation**: Custom generator or `uuid_generate_v7()` (PostgreSQL 16+)

```sql
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
-- Or use gen_random_uuid() with timestamp prefix
```

## Partitioning Strategy

### Audit Logs (Monthly Partitioning)
```sql
CREATE TABLE audit.audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(36),
    aggregate_type VARCHAR(100),
    user_id VARCHAR(36),
    tenant_id VARCHAR(36) NOT NULL,
    payload JSONB,
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
) PARTITION BY RANGE (created_at);

-- Monthly partitions
CREATE TABLE audit.audit_logs_2024_01 PARTITION OF audit.audit_logs
    FOR VALUES FROM ('2024-01-01') TO ('2024-02-01');
```

### Benefits
- Fast partition pruning
- Easy archival/deletion of old data
- Parallel query execution

## Indexing Strategy

### Standard Indexes
- Primary keys (automatic)
- Foreign keys (explicit for join performance)
- Tenant ID + Status composite indexes
- CreatedAt for time-range queries

### Specialized Indexes
```sql
-- GIN for JSONB queries
CREATE INDEX idx_ticket_specs ON ticketing.tickets USING GIN (specs);

-- GIN for full-text search
CREATE INDEX idx_ticket_search ON ticketing.tickets USING GIN (
    to_tsvector('english', coalesce(title,'') || ' ' || coalesce(description,''))
);

-- Partial indexes for common filters
CREATE INDEX idx_ticket_open ON ticketing.tickets (tenant_id, created_at)
    WHERE status IN ('OPEN', 'IN_PROGRESS', 'WAITING');

-- Covering indexes for common queries
CREATE INDEX idx_ticket_assignee_status ON ticketing.tickets (assignee_id, status)
    INCLUDE (title, priority, created_at);
```

## Connection Pooling

### HikariCP (Application Level)
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      idle-timeout: 300000
      connection-timeout: 20000
      leak-detection-threshold: 60000
```

### PgBouncer (Cluster Level - Transaction Mode)
- Reduces PostgreSQL connection overhead
- Enables higher application connection limits
- Required for serverless/lambda patterns

## Read Replicas

### Configuration
- Primary: Read/Write
- Replica 1: Read-only (reporting, analytics)
- Replica 2: Read-only (search, exports)

### Routing
```java
@Configuration
public class DataSourceConfig {
    @Bean
    @Primary
    DataSource primaryDataSource() { ... }

    @Bean
    DataSource replicaDataSource() { ... }

    @Bean
    RoutingDataSource routingDataSource() {
        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put("primary", primaryDataSource());
        targetDataSources.put("replica", replicaDataSource());
        
        RoutingDataSource rds = new RoutingDataSource();
        rds.setTargetDataSources(targetDataSources);
        rds.setDefaultTargetDataSource(primaryDataSource());
        return rds;
    }
}

// Usage
@Transactional(readOnly = true)
public List<TicketDto> searchTickets(...) {
    DataSourceContextHolder.setDataSourceType("replica");
    return ticketRepository.search(...);
}
```

## Migration Strategy

### Flyway
- Versioned migrations: `V1_0_0__initial_schema.sql`
- Repeatable migrations: `R__views.sql`
- Callbacks: `afterMigrate.sql` for grants
- Baseline on migrate for existing databases

### Naming Convention
```
V{version}__{description}.sql
R__{description}.sql
```

### Per-Module Migrations
```
nexusops-iam/
└── src/main/resources/db/migration/
    ├── V1_0_0__iam_initial_schema.sql
    ├── V1_0_1__add_mfa_columns.sql
    └── V1_1_0__add_oauth2_tables.sql
```

## Performance Tuning

### PostgreSQL Configuration
```postgresql
# Memory
shared_buffers = 25% RAM
effective_cache_size = 75% RAM
work_mem = 64MB
maintenance_work_mem = 512MB

# Parallelism
max_worker_processes = CPU cores
max_parallel_workers_per_gather = CPU cores / 2
parallel_leader_participation = on

# WAL
wal_buffers = 16MB
checkpoint_completion_target = 0.9
max_wal_size = 4GB

# Planner
random_page_cost = 1.1  # SSD
effective_io_concurrency = 200
```

### Monitoring
- `pg_stat_statements` for query analysis
- `auto_explain` for slow query logging
- `pg_stat_user_tables` for bloat detection

## Backup & Recovery

### RDS Automated Backups
- Retention: 30 days
- Window: 03:00-04:00 UTC
- Point-in-time recovery: Enabled

### Cross-Region Replica (Production)
- Async replication to us-west-2
- RPO < 5 minutes
- Promotion tested quarterly

## Security

### Encryption
- At rest: AWS KMS (RDS encryption)
- In transit: TLS 1.3 (force SSL)
- Column-level: `pgcrypto` for PII

### Access Control
- IAM authentication for admin access
- Database users per module (least privilege)
- Row-level security for multi-tenancy