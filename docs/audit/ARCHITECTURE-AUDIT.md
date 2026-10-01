# Auditoria de Arquitetura — NexusOps

**Data:** 2026-09-28
**Agente:** `ecc:architect` (somente leitura, nenhum arquivo alterado)
**Escopo:** backend/ (10 módulos Maven, Spring Boot 3.2.3 + Spring Modulith 1.2.1), comparado às ADRs em `docs/adr/`.

## Resumo

A decomposição em bounded contexts e a documentação arquitetural (12 ADRs) são maduras e coerentes com o domínio ITSM. Mas **o monólito modular, hoje, não se integra**: a aplicação sobe sem carregar nenhum módulo de negócio, dois módulos não compilam, o sistema de eventos não tem nenhum consumidor real, e os testes de arquitetura (ArchUnit) não verificam nada de fato.

| Aspecto | Pretendido (ADRs) | Real |
|---|---|---|
| Descoberta de módulos | Spring Modulith com limites verificados | `@SpringBootApplication` sem `scanBasePackages`; módulos ficam fora do component-scan. Sem `ApplicationModules.of(...).verify()`, sem `@ApplicationModule`. |
| Integração | 1 JAR com os 10 módulos | `nexusops-bootstrap/pom.xml` só inclui shared-kernel, iam, platform (demais comentados). Log real mostra "Found 0 JPA repository interfaces". |
| Guardrails | ArchUnit no CI | Testes ficam no shared-kernel, que não vê os outros módulos — não verificam nada. Regras permissivas/tautológicas. |
| Comunicação | Eventos de domínio | 23 classes de evento, nenhum listener real fora do shared-kernel (`AuditListener` vazio). |
| Camadas | domain/service/repository/controller | Padrão consistente em iam, platform, sla, ticketing. IAM tem refactor abandonado no meio (pacotes duplicados). |
| Multi-tenancy | `TenantContext` + Hibernate `@Filter` | `TenantContext` nunca é populado nem lido. Sem `@Filter`/`@FilterDef`. |
| JWT | RS256 assimétrico | `JwtTokenProvider` usa HS256 (`Keys.hmacShaKeyFor`). |
| Java | 17 (informado) | `pom.xml` define `java.version=21`. |

## Pontos fortes concretos

1. Decomposição em bounded contexts coerente com o domínio ITSM — módulos batem com a ADR-001, cada entidade declara seu schema (`@Table(schema="ticketing")`, `schema="sla"`).
2. Zero import cruzado entre módulos de negócio (iam, platform, sla, ticketing) — acoplamento só via shared-kernel (embora isso se deva à falta de integração, não a uma API pública bem desenhada).
3. Estrutura de pacotes consistente nos 4 módulos maduros: `domain`, `repository`, `service`, `controller`, `dto`, `mapper`, `event`.
4. Shared-kernel enxuto e com responsabilidades adequadas: `DomainEvent` (com `eventId`/`aggregateId`/`aggregateVersion`, bom para idempotência), hierarquia de exceções, `GlobalExceptionHandler` (RFC 9457), `TenantAware`.
5. SLA (`SlaCalculationService`) tem a lógica de negócio mais madura do projeto: horário comercial, pausa em ON_HOLD, breach iminente a 80%.
6. Ticketing modela Incident/Problem/Change com herança JOINED e já publica eventos de ciclo de vida.
7. 12 ADRs maduras, com alternativas, critérios de extração e fases com critérios de saída.

## Problemas concretos

### P1 — CRITICAL — Aplicação não carrega nenhum módulo de negócio
- **Arquivo:** `backend/nexusops-bootstrap/src/main/java/com/nexusops/bootstrap/NexusOpsApplication.java`
- `@SpringBootApplication` sem `scanBasePackages`, pacote `com.nexusops.bootstrap`. Component-scan, JPA repositories e entity-scan ficam restritos a esse pacote.
- **Evidência:** `nexusops-bootstrap/logs/nexusops.log` — "Found 0 JPA repository interfaces"; `DefaultSecurityFilterChain` com página de login padrão do Spring Boot.
- **Impacto:** o backend "sobe" mas não expõe nenhum endpoint de negócio real.

### P2 — CRITICAL — `sla` e `ticketing` não compilam
- `sla/service/EscalationService.java:83` usa `Map.of(...)` sem `import java.util.Map`.
- `ticketing/domain/Ticket.java` é `abstract` com `@Builder`; `Incident.java` usa `@Builder` só com os próprios campos, mas `TicketService.createTicket` chama métodos de campos herdados que não existem sem `@SuperBuilder`.
- `Incident` contém `rootCause`/`knownError`/`workaround` — conceitos de Problem, não de Incident.
- **Impacto:** dois dos quatro módulos "maduros" nunca rodaram — por isso estão comentados no bootstrap.

### P3 — CRITICAL — Colisões que quebram o contexto quando os módulos forem integrados
- Entidades JPA duplicadas no mesmo persistence unit: `iam.domain.Tenant` (schema iam) e `platform.domain.Tenant` (schema platform); `ticketing.domain.Category` e `knowledge.domain.Category`.
- Beans duplicados: `TenantController/Service/Repository/Mapper` em iam e platform; `SlaCalculationService` em ticketing e sla; `SecurityConfig`/`JwtAuthenticationFilter`/`UserRepository` duplicados dentro do próprio iam.
- **Impacto:** ao corrigir P1, o boot falha por definição de bean duplicada. Há duas fontes de verdade para `Tenant`.

### P4 — HIGH — Refatoração do IAM abandonada no meio
- Pacotes duplicados: `iam/repository/*` vs `iam/infrastructure/repository/*`; `iam/security/*` vs `iam/infrastructure/security/*`.
- Serviços usam uma mistura dos dois pacotes.
- `iam/repository/UserRepository` declara `JpaRepository<User, String>` mas `User.id` é `UUID`; referencia campo inexistente `u.emailVerified`.
- As duas `SecurityConfig` divergem nos matchers de `/api/v1/auth/**`, e nenhuma considera o `context-path` corretamente — login pode ficar bloqueado.

### P5 — HIGH — Guardrails ArchUnit não verificam nada
- Testes ficam no shared-kernel, que não depende dos outros módulos — `importPackages("com.nexusops")` só vê o shared-kernel.
- Regras permissivas (`iamModuleShouldOnlyDependOnSharedKernel` permite quase tudo), regra `noCyclesInModuleDependencies` não usa `slices().beFreeOfCycles()`, regras tautológicas (classes terminadas em "X" devem terminar em "X").

### P6 — HIGH — Mecanismo de eventos quebrado e sem consumidores
- `TransactionalEventPublisher.publish()` já dispara os listeners de imediato (antes do commit) e o listener `AFTER_COMMIT` republica o mesmo evento — duplicação de entrega.
- `publishAfterRollback` vazio. Sem `@EnableAsync`. Sem outbox (`spring-modulith-events-jpa`).
- `AuditListener.handleDomainEvent`/`audit(...)` estão vazios.

### P7 — HIGH — Integração ticketing↔sla duplicada em vez de por eventos
- `ticketing/service/SlaCalculationService.java` só faz `log.debug` — nome idêntico ao serviço real do módulo `sla`.
- `WorkflowService.validateTransition` sempre retorna `true`; a máquina de estados real está hardcoded em `TicketService.isValidTransition`.

### P8 — HIGH — Porta sem adaptador e infraestrutura Spring não habilitada
- `sla/service/NotificationService` é interface sem implementação, injetada em `EscalationService` — `NoSuchBeanDefinitionException` no boot.
- Sem `@EnableScheduling` (o `@Scheduled` de `EscalationService` nunca roda) nem `@EnableCaching` (o `@Cacheable` fica inerte).
- `processEscalations` usa tenant `"default"` hardcoded.

### P9 — HIGH — Multi-tenancy sem enforcement (vazamento de dados)
- Sem `TenantContext` fora da própria classe, sem `@Filter`. `TicketService.findById`/`deleteTicket` não verificam tenant. Cliente controla `tenantId` via body/query.
- Ressalva: o próprio exemplo da ADR-003 ativa o filtro em `@PostConstruct` (uma vez no startup, não por request) — a ADR precisa ser corrigida antes de implementar.

### P10 — MEDIUM — Prefixo de URL inconsistente
- Controllers de platform/sla/ticketing usam `@RequestMapping("/api/v1/...")` junto com `context-path=/api/v1` → rota efetiva `/api/v1/api/v1/tickets`.

### P11 — MEDIUM — Autorização inconsistente
- `@PreAuthorize` presente em platform/sla/ticketing, ausente em `UserController`/`RoleController`/`TenantController`/`MfaController` do iam.

### P12 — MEDIUM — Migrations Flyway em conflito
- Todos os scripts usam versão `V1_0_0__*` — conflito garantido quando módulos compartilham classpath. Scripts duplicados entre módulo e bootstrap.

### P13 — MEDIUM — Dependências Maven não refletem uso real do código (grafo de build mais acoplado que o código).

### P14 — MEDIUM — Modelo de domínio anêmico e chaves primárias inconsistentes (UUID vs String, ADR-003 pede UUIDv7).

### P15/P16/P17 — LOW — `spring-boot-maven-plugin` repackage em todos os módulos; shared-kernel carregado com responsabilidades de web/security; configs de profile morto (`application-asset.yml` etc.) e `TRACE` de bind SQL habilitado por padrão.

## Módulos-stub: roadmap ou dívida?

| Módulo | Fase (ADR-012) | Avaliação |
|---|---|---|
| asset | Fase 3 | Roadmap intencional |
| knowledge | Fase 3 | Roadmap, mas colide com `Category` do ticketing (P3) |
| integration | Fase 3 | Roadmap |
| reporting | Fase 3 | Roadmap — ressalva: ADR prevê projeções CQRS, entidades JPA de escrita não são o modelo certo |
| notification | **Fase 2** | **Dívida técnica** — o `sla` (Fase 2) já depende dele via porta órfã |

Nenhum dos stubs está fora do roadmap; nenhum é código abandonado.

## Classificação por módulo

| Módulo | Classificação | Justificativa |
|---|---|---|
| shared-kernel | REFACTOR | Base boa; reescrever `TransactionalEventPublisher`, implementar `AuditListener`/`TenantContext`, mover `JwtTokenProvider`/`OpenApiConfig` para iam/bootstrap |
| iam | REFACTOR | Funcionalidade mais completa; remover duplicações `repository`/`security`, corrigir matchers, trocar HS256→RS256 |
| platform | REFACTOR | Compila e é consistente; deve ser dono único de `Tenant`, corrigir prefixo de URL |
| sla | REFACTOR | Lógica de negócio mais valiosa; corrigir import, implementar porta via evento, habilitar scheduling/cache |
| ticketing | REFACTOR pesado (próximo de REWRITE no `domain`/`TicketService`) | Não compila; modelagem Incident/Problem trocada; controller/dto/mapper/eventos podem ficar |
| bootstrap | REFACTOR | Corrigir scan de pacotes, adicionar flyway-core, criar teste `ApplicationModules.verify()` |
| asset, integration | KEEP | Stubs coerentes com Fase 3 |
| knowledge | KEEP (ajuste) | Renomear `Category` para evitar colisão |
| reporting | KEEP (revisar desenho) | Trocar entidades de escrita por read models quando implementado |
| notification | KEEP e priorizar | Pré-requisito das escalações do sla |

**Nenhum módulo merece REMOVE.** Remoção cabe a arquivos específicos: `iam/repository/*`, `iam/security/*`, `ticketing/service/SlaCalculationService.java`, `iam/domain/Tenant.java`, cópias de migration duplicadas no bootstrap.

## Ordem sugerida de correção arquitetural
1. P1 + P2 + P3 + P12 — ter um boot real com todos os módulos.
2. P5 — testes Modulith/ArchUnit reais no bootstrap.
3. P6 + P8 — eventos e outbox funcionando.
4. P9 — multi-tenancy.
5. Demais itens.
