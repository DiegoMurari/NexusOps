# ADR-005: Testing Strategy

## Status
Accepted

## Context
NexusOps needs a comprehensive testing strategy that:
- Validates correctness at all levels
- Runs fast in CI/CD (< 10 min for PR validation)
- Provides confidence for continuous deployment
- Covers frontend, backend, and infrastructure
- Enforces quality gates

## Decision
Implement a **Test Pyramid** with emphasis on unit tests, supported by integration, contract, and E2E tests.

## Test Pyramid

```
                    ┌─────────────────┐
                    │   E2E Tests     │  ← 5% (Playwright)
                    │   (Critical     │
                    │    User Flows)  │
                    ├─────────────────┤
                    │ Contract Tests  │  ← 10% (Spring Cloud Contract)
                    │ (API Contracts) │
                    ├─────────────────┤
                    │ Integration     │  ← 25% (Testcontainers)
                    │ Tests           │
                    ├─────────────────┤
                    │   Unit Tests    │  ← 60% (JUnit/Mockito, Jest)
                    │  (Domain Logic) │
                    └─────────────────┘
```

## Backend Testing

### Unit Tests (JUnit 5 + Mockito)
- **Scope**: Single class, isolated dependencies
- **Target**: Domain logic, services, mappers, utilities
- **Coverage**: ≥85% line, ≥80% branch
- **Execution**: Parallel, < 30 seconds total
- **Patterns**:
  ```java
  @ExtendWith(MockitoExtension.class)
  class TicketServiceTest {
      @Mock TicketRepository repository;
      @Mock SlaCalculationService slaService;
      @InjectMocks TicketService service;

      @Test
      void shouldCreateTicketWithDefaultSla() {
          // Given
          when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
          when(slaService.findApplicableSla(any(), any())).thenReturn(defaultSla);

          // When
          TicketDto result = service.createTicket(request);

          // Then
          assertThat(result.getStatus()).isEqualTo(TicketStatus.OPEN);
          verify(slaService).startSlaTimer(result.getId());
      }
  }
  ```

### Integration Tests (Testcontainers)
- **Scope**: Multiple modules, real PostgreSQL/Redis
- **Target**: Repository queries, Spring configuration, event publishing
- **Execution**: Parallel per module, < 2 minutes per module
- **Patterns**:
  ```java
  @SpringBootTest
  @Testcontainers
  @ActiveProfiles("test")
  class TicketRepositoryIntegrationTest {
      @Container
      static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
          .withDatabaseName("nexusops_test")
          .withUsername("test")
          .withPassword("test");

      @Container
      static GenericContainer<?> redis = new GenericContainer<>("redis:7")
          .withExposedPorts(6379);

      @DynamicPropertySource
      static void properties(DynamicPropertyRegistry registry) {
          registry.add("spring.datasource.url", postgres::getJdbcUrl);
          registry.add("spring.datasource.username", postgres::getUsername);
          registry.add("spring.datasource.password", postgres::getPassword);
          registry.add("spring.redis.host", redis::getHost);
          registry.add("spring.redis.port", redis::getFirstMappedPort);
      }

      @Autowired TicketRepository repository;

      @Test
      void shouldFindTicketsByAssigneeAndStatus() {
          // Given
          Ticket ticket = TicketMother.openTicket().assigneeId("user-1").build();
          repository.save(ticket);

          // When
          List<Ticket> results = repository.findByAssigneeIdAndStatus("user-1", TicketStatus.OPEN);

          // Then
          assertThat(results).hasSize(1);
      }
  }
  ```

### Contract Tests (Spring Cloud Contract)
- **Scope**: API contracts between producer (backend) and consumer (frontend)
- **Target**: REST API request/response validation
- **Execution**: Producer generates stubs, consumer verifies against stubs
- **Patterns**:
  ```groovy
  // contracts/ticket/getTicket.groovy
  Contract.make {
      request {
          method 'GET'
          urlPath '/api/v1/tickets/123'
          headers {
            header 'Authorization': 'Bearer $(regex(jwt-regex))'
          }
      }
      response {
          status 200
          headers {
            header 'Content-Type': 'application/json'
          }
          body([
              id: 123,
              ticketNumber: 'INC-2024-001',
              title: 'Server down',
              status: 'OPEN',
              priority: 'HIGH'
          ])
      }
  }
  ```

### Architecture Tests (ArchUnit)
- **Scope**: Module boundaries, dependency rules
- **Target**: Enforce modular monolith constraints
- **Execution**: Every build, fail fast
- **Rules**:
  ```java
  @AnalyzeClasses(packages = "com.nexusops")
  class ArchitectureTest {
      @ArchTest
      static final ArchRule modules_should_not_access_internals_of_other_modules =
          noClasses()
              .that().resideInAPackage("..iam..")
              .should().accessClassesThat()
              .resideInAPackage("..ticketing..")
              .orShould().accessClassesThat()
              .resideInAPackage("..asset..")
              .as("IAM module should not access Ticketing or Asset internals");

      @ArchTest
      static final ArchRule shared_kernel_should_be_accessible_by_all =
          classes()
              .that().resideInAPackage("..shared..")
              .should().onlyBeAccessed()
              .byClassesThat().resideInAnyPackage("..iam..", "..ticketing..", "..asset..", "..knowledge..")
              .as("Shared kernel should only be accessed by modules");

      @ArchTest
      static final ArchRule domain_events_should_be_published_via_publisher =
          methods()
              .that().areAnnotatedWith(EventListener.class)
              .should().onlyBeCalled()
              .byClassesThat().resideInAPackage("..shared.event..")
              .as("Domain events should be published via TransactionalEventPublisher");
  }
  ```

## Frontend Testing

### Unit Tests (Jest + Angular Testing Utilities)
- **Scope**: Components, services, pipes, guards in isolation
- **Coverage**: ≥80% lines, ≥75% branches
- **Patterns**:
  ```typescript
  describe('TicketListComponent', () => {
    let component: TicketListComponent;
    let fixture: ComponentFixture<TicketListComponent>;
    let ticketService: jasmine.SpyObj<TicketService>;

    beforeEach(() => {
      ticketService = jasmine.createSpyObj('TicketService', ['getTickets']);
      TestBed.configureTestingModule({
        imports: [TicketListComponent],
        providers: [{ provide: TicketService, useValue: ticketService }]
      });
      fixture = TestBed.createComponent(TicketListComponent);
      component = fixture.componentInstance;
    });

    it('should load tickets on init', () => {
      ticketService.getTickets.and.returnValue(of({ content: [], totalElements: 0 }));
      fixture.detectChanges();
      expect(ticketService.getTickets).toHaveBeenCalled();
    });
  });
  ```

### Component Tests (Testing Library)
- **Scope**: Component rendering, user interactions
- **Patterns**:
  ```typescript
  test('should show ticket details when row clicked', async () => {
    render(<TicketListComponent />);
    const ticketRow = screen.getByRole('row', { name: /INC-2024-001/i });
    fireEvent.click(ticketRow);
    await waitFor(() => {
      expect(screen.getByText('Server down')).toBeInTheDocument();
    });
  });
  ```

### E2E Tests (Playwright)
- **Scope**: Critical user journeys across frontend + backend
- **Target**: Login, ticket lifecycle, admin flows
- **Execution**: Parallel, headed mode for debugging
- **Patterns**:
  ```typescript
  // tests/e2e/ticket-lifecycle.spec.ts
  test.describe('Ticket Lifecycle', () => {
    test.beforeEach(async ({ page }) => {
      await page.goto('/login');
      await page.fill('[name="username"]', 'agent@nexusops.com');
      await page.fill('[name="password"]', 'password123');
      await page.click('button[type="submit"]');
      await expect(page).toHaveURL('/dashboard');
    });

    test('create → assign → resolve → close', async ({ page }) => {
      // Create ticket
      await page.click('text=New Ticket');
      await page.fill('[name="title"]', 'Email not working');
      await page.selectOption('[name="category"]', 'EMAIL');
      await page.click('button:has-text("Create")');
      await expect(page.locator('text=Ticket created')).toBeVisible();

      // Assign to self
      await page.click('button:has-text("Assign to me")');
      await expect(page.locator('text=Assigned to you')).toBeVisible();

      // Add comment
      await page.fill('[name="comment"]', 'Investigating...');
      await page.click('button:has-text("Add Comment")');
      await expect(page.locator('text=Investigating...')).toBeVisible();

      // Resolve
      await page.selectOption('[name="status"]', 'RESOLVED');
      await page.fill('[name="resolution"]', 'Reset password');
      await page.click('button:has-text("Save")');
      await expect(page.locator('text=RESOLVED')).toBeVisible();

      // Close
      await page.selectOption('[name="status"]', 'CLOSED');
      await page.click('button:has-text("Save")');
      await expect(page.locator('text=CLOSED')).toBeVisible();
    });
  });
  ```

## Test Data Management

### Test Data Builders (Mother Pattern)
```java
public class TicketMother {
    private String title = "Test Ticket";
    private TicketStatus status = TicketStatus.OPEN;
    private Priority priority = Priority.MEDIUM;
    private String assigneeId;
    private String reporterId = "user-1";
    private String tenantId = "tenant-1";

    public static TicketMother aTicket() { return new TicketMother(); }

    public TicketMother withTitle(String title) { this.title = title; return this; }
    public TicketMother withStatus(TicketStatus status) { this.status = status; return this; }
    public TicketMother assignedTo(String assigneeId) { this.assigneeId = assigneeId; return this; }

    public Ticket build() {
        Ticket ticket = new Ticket();
        ticket.setTitle(title);
        ticket.setStatus(status);
        ticket.setPriority(priority);
        ticket.setAssigneeId(assigneeId);
        ticket.setReporterId(reporterId);
        ticket.setTenantId(tenantId);
        return ticket;
    }
}
```

### Test Fixtures
- Shared test data in `src/test/resources/fixtures/`
- JSON files for API contract tests
- SQL scripts for complex integration test setup

## CI/CD Integration

### Pipeline Stages
```yaml
# PR Validation (< 10 min)
- backend-lint (format, checkstyle, spotbugs)
- backend-unit-tests (parallel, unit only)
- frontend-lint (format, eslint)
- frontend-unit-tests (jest, coverage)
- archunit-tests (module boundaries)

# Post-Merge / Nightly
- backend-integration-tests (testcontainers)
- backend-contract-tests (producer + consumer)
- frontend-e2e-tests (playwright)
- performance-tests (k6, weekly)
- security-scans (codeql, trivy, dependency-check)
```

### Quality Gates
| Metric | Threshold | Action |
|--------|-----------|--------|
| Unit Coverage (Backend) | ≥85% | Fail build |
| Unit Coverage (Frontend) | ≥80% | Fail build |
| Branch Coverage | ≥75% | Warn |
| Integration Tests | 100% pass | Fail build |
| Contract Tests | 100% pass | Fail build |
| E2E Tests | 100% pass | Fail build |
| ArchUnit | 0 violations | Fail build |
| Critical Vulnerabilities | 0 | Fail build |

## Test Environment Strategy

| Environment | Purpose | Data | Reset |
|-------------|---------|------|-------|
| Local (Testcontainers) | Unit/Integration | Synthetic | Per test |
| CI (Testcontainers) | Integration/Contract | Synthetic | Per pipeline |
| Dev | Manual testing, E2E | Seeded + synthetic | Daily |
| Staging | Pre-production validation | Production-like | Per deploy |
| Production | Monitoring only | Real | Never |

## Performance Testing (k6)

### Scenarios
```javascript
// tests/performance/ticket-api.js
export const options = {
  stages: [
    { duration: '2m', target: 100 },   // Ramp up
    { duration: '5m', target: 100 },   // Steady state
    { duration: '2m', target: 500 },   // Stress
    { duration: '2m', target: 0 },     // Ramp down
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
  },
};

export default function() {
  const token = login();
  
  // List tickets
  http.get(`${BASE_URL}/tickets?page=0&size=20`, { headers: authHeaders(token) });
  
  // Create ticket
  http.post(`${BASE_URL}/tickets`, JSON.stringify(createTicketPayload()), { headers: authHeaders(token) });
  
  // Get ticket details
  http.get(`${BASE_URL}/tickets/${ticketId}`, { headers: authHeaders(token) });
  
  // Transition ticket
  http.patch(`${BASE_URL}/tickets/${ticketId}/transition`, JSON.stringify({ status: 'IN_PROGRESS' }), { headers: authHeaders(token) });
}
```

## Chaos Engineering

### Experiments
- Pod kill (random)
- Network latency (100-500ms)
- Network partition (AZ isolation)
- CPU stress (80% for 60s)
- Disk fill (90% capacity)
- Dependency failure (DB, Redis, external APIs)

### Validation
- No data loss
- Recovery < 30 seconds
- HPA scales correctly
- PDB respected
- Error rate < 1% during chaos

## Mutation Testing (Optional)
- **Tool**: Pitest (Java), Stryker (TypeScript)
- **Target**: Critical domain logic
- **Threshold**: ≥80% mutation score
- **Frequency**: Weekly / pre-release