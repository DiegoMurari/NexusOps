# ADR-010: Observability Strategy

## Status
Accepted

## Context
NexusOps requires comprehensive observability across:
- **Metrics**: System health, business KPIs, SLOs
- **Logs**: Structured, correlated, searchable
- **Traces**: Distributed request flows, latency analysis
- **Profiles**: CPU/memory allocation, bottleneck identification
- **Alerting**: Actionable alerts, on-call routing, error budgets

## Decision
Implement **OpenTelemetry** as the unified instrumentation layer, exporting to **Prometheus** (metrics), **Loki** (logs), **Tempo** (traces), and **Pyroscope** (profiles), visualized in **Grafana**.

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                      Application Services                           │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌───────────┐  │
│  │   Backend   │  │  Frontend   │  │  Workers    │  │  Infra    │  │
│  │  (Spring)   │  │  (Angular)  │  │  (Jobs)     │  │  (K8s)    │  │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘  └─────┬─────┘  │
└─────────┼────────────────┼────────────────┼─────────────┼──────────┘
          │                │                │             │
          ▼                ▼                ▼             ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    OpenTelemetry Collector                          │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │ Receivers: OTLP (gRPC/HTTP), Prometheus, Fluent Bit        │   │
│  │ Processors: Batch, Memory Limiter, Resource, Transform     │   │
│  │ Exporters: Prometheus, Loki, Tempo, Pyroscope              │   │
│  └─────────────────────────────────────────────────────────────┘   │
└────────────────────────────┬──────────────────────────────────────┘
                             │
        ┌────────────────────┼────────────────────┐
        ▼                    ▼                    ▼
┌───────────────┐    ┌───────────────┐    ┌───────────────┐
│  Prometheus   │    │     Loki      │    │    Tempo      │
│  (Metrics)    │    │    (Logs)     │    │  (Traces)     │
└───────┬───────┘    └───────┬───────┘    └───────┬───────┘
        │                    │                    │
        └────────────────────┼────────────────────┘
                             ▼
                    ┌───────────────┐
                    │    Grafana    │
                    │  (Dashboards, │
                    │   Alerting,   │
                    │   Explore)    │
                    └───────────────┘
```

## Metrics (Prometheus)

### Application Metrics (Spring Boot Actuator + Micrometer)
```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always
  metrics:
    tags:
      application: ${spring.application.name}
      environment: ${spring.profiles.active}
    export:
      prometheus:
        enabled: true
        step: 30s
```

### Key Metrics Categories

#### JVM Metrics
| Metric | Description | Alert Threshold |
|--------|-------------|-----------------|
| `jvm_memory_used_bytes` | Heap/non-heap usage | > 85% max |
| `jvm_gc_pause_seconds` | GC pause duration | > 500ms |
| `jvm_threads_live` | Live thread count | > 500 |
| `jvm_classes_loaded` | Loaded classes | Trend up |

#### HTTP Metrics
| Metric | Description | Alert Threshold |
|--------|-------------|-----------------|
| `http_server_requests_seconds` | Request latency (histogram) | P95 > 500ms |
| `http_server_requests_total` | Request count by status | 5xx rate > 1% |
| `http_server_active_requests` | In-flight requests | > 1000 |

#### Business Metrics
| Metric | Description | Alert Threshold |
|--------|-------------|-----------------|
| `tickets_created_total` | Tickets created counter | Trend down |
| `tickets_resolved_total` | Tickets resolved counter | Trend down |
| `sla_breach_total` | SLA breaches counter | > 0 |
| `active_users` | Concurrent active users | Capacity |

#### Database Metrics
| Metric | Description | Alert Threshold |
|--------|-------------|-----------------|
| `hikaricp_connections_active` | Active DB connections | > 80% pool |
| `hikaricp_connections_pending` | Waiting for connection | > 0 |

### Custom Metrics (Micrometer)
```java
@Component
public class BusinessMetrics {
    private final Counter ticketsCreated;
    private final Timer ticketResolutionTime;
    private final Gauge activeTickets;

    public BusinessMetrics(MeterRegistry registry, TicketRepository repository) {
        ticketsCreated = Counter.builder("tickets.created")
            .tag("type", "incident")
            .register(registry);
        
        ticketResolutionTime = Timer.builder("tickets.resolution.time")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);
        
        activeTickets = Gauge.builder("tickets.active", repository, r -> r.countByStatus(TicketStatus.OPEN))
            .register(registry);
    }

    public void recordTicketCreated(String type) {
        ticketsCreated.increment(Tag.of("type", type));
    }

    public void recordResolutionTime(Duration duration) {
        ticketResolutionTime.record(duration);
    }
}
```

## Logging (Loki)

### Structured Logging (Logback + JSON)
```xml
<!-- logback-spring.xml -->
<configuration>
    <springProfile name="prod">
        <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
            <encoder class="net.logstash.logback.encoder.LogstashEncoder">
                <customFields>{"application":"nexusops","environment":"${spring.profiles.active}"}</customFields>
                <fieldNames>
                    <timestamp>@timestamp</timestamp>
                    <level>[level]</level>
                    <logger>[logger]</logger>
                    <message>[message]</message>
                    <thread>[thread]</thread>
                    <class>[class]</class>
                    <method>[method]</method>
                    <line>[line]</line>
                </fieldNames>
            </encoder>
        </appender>
        <root level="INFO">
            <appender-ref ref="CONSOLE"/>
        </root>
    </springProfile>
</configuration>
```

### MDC Context for Correlation
```java
@Component
public class LoggingFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) {
        String traceId = TraceContext.getTraceId();
        String spanId = TraceContext.getSpanId();
        String userId = SecurityUtils.getCurrentUsername().orElse("anonymous");
        String tenantId = TenantContext.getTenantId();

        MDC.put("trace_id", traceId);
        MDC.put("span_id", spanId);
        MDC.put("user_id", userId);
        MDC.put("tenant_id", tenantId);

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
```

### Log Queries (LogQL)
```logql
# Error rate by service
sum by (service) (rate({application="nexusops"} |= "ERROR" [5m]))

# Slow queries
{application="nexusops", logger=~".*SQL.*"} |~ "duration.*[0-9]{4,}"

# User activity
{application="nexusops"} | json | user_id != "anonymous" | count by (user_id) over 1h
```

## Tracing (Tempo)

### OpenTelemetry Auto-Instrumentation (Java)
```yaml
# Kubernetes deployment
spec:
  template:
    metadata:
      annotations:
        instrumentation.opentelemetry.io/inject-java: "true"
        instrumentation.opentelemetry.io/java-image: "ghcr.io/open-telemetry/opentelemetry-java-instrumentation:latest"
    spec:
      containers:
        - name: backend
          env:
            - name: OTEL_SERVICE_NAME
              value: "nexusops-backend"
            - name: OTEL_EXPORTER_OTLP_ENDPOINT
              value: "http://otel-collector:4317"
            - name: OTEL_PROPAGATORS
              value: "tracecontext,baggage"
            - name: OTEL_TRACES_SAMPLER
              value: "traceidratio"
            - name: OTEL_TRACES_SAMPLER_ARG
              value: "0.1"  # 10% sampling
```

### Manual Instrumentation (Spring)
```java
@Configuration
public class TracingConfig {
    @Bean
    public WebMvcTagsProvider webMvcTagsProvider() {
        return new WebMvcTagsProvider() {
            @Override
            public Iterable<Tag> httpRequestTags(HttpServletRequest request, HttpServletResponse response, Object handler, Throwable exception) {
                return List.of(
                    Tag.of("user_id", SecurityUtils.getCurrentUsername().orElse("anonymous")),
                    Tag.of("tenant_id", TenantContext.getTenantId())
                );
            }
        }
    }
}

@Service
@RequiredArgsConstructor
public class TicketService {
    private final Tracer tracer;

    public TicketDto createTicket(CreateTicketRequest request) {
        return tracer.nextSpan()
            .name("create-ticket")
            .tag("ticket.type", request.getType())
            .tag("tenant.id", TenantContext.getTenantId())
            .start()
            .tryWithSpan(span -> {
                Ticket ticket = doCreateTicket(request);
                span.tag("ticket.id", ticket.getId().toString());
                return mapToDto(ticket);
            })
            .end()
            .get();
    }
}
```

### Trace Queries (TraceQL)
```traceql
# Find slow traces
{span.http.status_code >= 500} | avg(duration) by (span.http.route) > 1s

# Error traces
{span.status = "error" && span.http.route = "/api/v1/tickets/*"}

# Database queries
{span.db.system = "postgresql" && span.duration > 500ms}
```

## Profiling (Pyroscope)

### Java Agent
```yaml
# Kubernetes deployment
spec:
  template:
    spec:
      containers:
        - name: backend
          env:
            - name: PYROSCOPE_APPLICATION_NAME
              value: "nexusops.backend"
            - name: PYROSCOPE_SERVER_ADDRESS
              value: "http://pyroscope:4040"
            - name: PYROSCOPE_PROFILER_TYPE
              value: "cpu,alloc,lock"
            - name: PYROSCOPE_UPLOAD_INTERVAL
              value: "30s"
```

### Continuous Profiling Queries
```python
# CPU hotspots
pyroscope.query("nexusops.backend", "cpu", "5m")

# Memory allocation
pyroscope.query("nexusops.backend", "alloc", "5m")

# Lock contention
pyroscope.query("nexusops.backend", "lock", "5m")
```

## SLOs & Error Budgets

### Service Level Objectives
| Service | SLI | SLO Target | Window |
|---------|-----|------------|--------|
| API Availability | Successful requests / Total requests | 99.9% | 30 days |
| API Latency (P95) | Request latency P95 | < 500ms | 30 days |
| Ticket Creation | Create ticket success rate | 99.5% | 30 days |
| Search | Search request success rate | 99% | 30 days |
| Real-time | WebSocket message delivery | 99.9% | 30 days |

### Error Budget Calculation
```
Error Budget = (1 - SLO) * Total Requests in Window
Burn Rate = Current Error Rate / (1 - SLO)

Alert if:
- Burn Rate > 2 for 1 hour (fast burn)
- Burn Rate > 1 for 6 hours (slow burn)
```

### Error Budget Alerts (Prometheus)
```yaml
groups:
  - name: error-budget
    rules:
      - alert: HighErrorBudgetBurnRate
        expr: |
          (
            sum(rate(http_server_requests_seconds_count{status=~"5.."}[1h]))
            /
            sum(rate(http_server_requests_seconds_count[1h]))
          ) > 0.002  # 0.2% for 99.9% SLO
        for: 1h
        labels:
          severity: critical
        annotations:
          summary: "Error budget burning fast"
          
      - alert: ErrorBudgetExhausted
        expr: |
          (
            sum(increase(http_server_requests_seconds_count{status=~"5.."}[30d]))
            /
            sum(increase(http_server_requests_seconds_count[30d]))
          ) > 0.001  # 0.1% for 99.9% SLO
        for: 6h
        labels:
          severity: warning
        annotations:
          summary: "Error budget exhausted for the month"
```

## Dashboards (Grafana)

### System Overview Dashboard
- Cluster health (CPU, memory, disk, network)
- Node status, pod status
- HPA scaling activity
- Deployment status

### Application Dashboard (RED Method)
- **Rate**: Requests per second by endpoint
- **Errors**: Error rate by status code
- **Duration**: Latency percentiles (P50, P95, P99)

### Business Dashboard
- Tickets: Created, resolved, active by status
- SLA: Compliance %, breaches, trends
- Users: Active users, sessions, MFA adoption
- Assets: Count by type, license compliance

### Database Dashboard
- Connections: Active, idle, waiting
- Queries: QPS, slow queries, cache hit ratio
- Storage: Used, free, growth rate

### SLO Dashboard
- Error budget remaining (%)
- Burn rate (1h, 6h, 24h)
- SLO compliance trend

## Alerting

### Alert Routing
```yaml
# Alertmanager config
route:
  group_by: ['alertname', 'service', 'severity']
  group_wait: 30s
  group_interval: 5m
  repeat_interval: 4h
  receiver: 'default'
  routes:
    - match:
        severity: critical
      receiver: 'pagerduty'
      continue: true
    - match:
        severity: warning
      receiver: 'slack-warning'
    - match:
        team: platform
      receiver: 'platform-team'

receivers:
  - name: 'default'
    email_configs:
      - to: 'alerts@nexusops.com'
  - name: 'pagerduty'
    pagerduty_configs:
      - service_key: '<PAGERDUTY_KEY>'
  - name: 'slack-warning'
    slack_configs:
      - channel: '#alerts-warning'
        title: '⚠️ Warning: {{ .GroupLabels.alertname }}'
```

### Critical Alerts
| Alert | Condition | Severity | Action |
|-------|-----------|----------|--------|
| ServiceDown | Health endpoint failing | Critical | Page on-call |
| HighErrorRate | 5xx > 5% for 5min | Critical | Page on-call |
| HighLatency | P95 > 2s for 5min | Critical | Page on-call |
| DatabaseConnections | > 90% pool used | Warning | Slack |
| DiskSpace | < 10% free | Warning | Slack |
| CertificateExpiry | < 30 days | Warning | Slack |
| SLACompliance | < 95% | Warning | Slack |

## Cost Optimization

### Retention Policies
| Data Type | Retention | Storage |
|-----------|-----------|---------|
| Metrics (Prometheus) | 15d (high-res), 1y (downsampled) | ~50GB |
| Logs (Loki) | 30d | ~200GB |
| Traces (Tempo) | 7d (10% sampling) | ~100GB |
| Profiles (Pyroscope) | 14d | ~20GB |

### Sampling Strategy
```java
// Tail-based sampling for traces
@Bean
public Sampler traceSampler() {
    return ParentBasedSampler.of(
        TraceIdRatioBasedSampler.of(0.1)  // 10% head sampling
    );
}

// Always sample errors
@Bean
public Sampler errorSampler() {
    return TraceIdRatioBasedSampler.of(1.0);  // 100% for errors
}
```