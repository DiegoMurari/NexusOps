# Auditoria de Banco de Dados — NexusOps

**Data:** 2026-09-28
**Agente:** `ecc:database-reviewer` (somente leitura, nenhum arquivo alterado)
**Escopo:** `backend/` — PostgreSQL 16 (ADR-003), schemas por módulo, Flyway, JPA/Hibernate.

## Resumo

A arquitetura de dados de base é sólida: separação por schema por módulo, tipos corretos (`UUID`, `TIMESTAMP WITH TIME ZONE`, `JSONB`), zero SQL injection, zero join direto entre módulos (encapsulamento do Modulith respeitado nos dados). Os problemas encontrados são bugs concretos e corrigíveis, não falhas de desenho.

## Achados CRITICAL

### 1. Vazamento cross-tenant em busca de tickets
- `TicketRepository.searchTickets(tenantId, search)` — o parâmetro `tenantId` é declarado mas **nunca usado no WHERE**. Qualquer chamada retorna tickets de todos os tenants.
- Sem Hibernate Filter nem RLS ativos (achado #5), não há nenhuma camada de defesa que impeça o vazamento.

### 2. Credenciais de admin padrão inseridas via migration, sem guarda de ambiente
- `V1_0_1__insert_admin_user.sql` (duplicado em iam e bootstrap) insere `admin@nexusops.com` com senha documentada em comentário no próprio SQL (`admin123456`) e role `SUPER_ADMIN`.
- `spring.flyway.enabled: true` sem exclusão para prod — essa credencial é criada em produção também.

### 3. Migrations duplicadas entre módulo e bootstrap → conflito garantido no Flyway
- `nexusops-bootstrap/src/main/resources/db/migration/` contém cópias byte-idênticas de `V1_0_0__iam_initial_schema.sql`, `V1_0_0__platform_initial_schema.sql` e dos módulos stub.
- Como bootstrap depende de iam/platform, dois arquivos com a mesma versão+descrição existirão no classpath final → `FlywayException: Found more than one migration with version X`.

### 4. `platform.audit_logs` particionada sem partição para o presente/futuro
- `PARTITION BY RANGE (created_at)` com partições só de `2024_01` a `2024_12`, sem `DEFAULT PARTITION`. Qualquer INSERT com `created_at` a partir de 2025-01-01 (hoje é 2026-09-28) falha com "no partition of relation found for row". Sem `pg_partman`/job de manutenção.

### 5. Isolamento multi-tenant descrito na ADR não está implementado no código
- ADR-003 descreve Hibernate Filter + RLS como defesa em profundidade. Busca em todo o backend não encontrou nenhuma ocorrência real de `@Filter`, `enableFilter` ou `CREATE POLICY` — só o pseudo-código da própria ADR.
- Toda a segurança multi-tenant depende de cada repositório lembrar de filtrar por `tenantId` manualmente — o que já falhou no achado #1.

### 6. Job de escalonamento de SLA hardcoded para tenant "default"
- `EscalationService.processEscalations()` (roda a cada minuto) busca `slaBreachRepository.findPendingEscalation("default", ...)`, mas `tenant_id` é `UUID NOT NULL` e nenhum tenant tem id `"default"`. **Escalonamento de SLA nunca funciona para nenhum tenant real.**

## Achados HIGH

**7. Repositórios JPA duplicados e conflitantes no IAM** — `iam.repository.*` (código morto de refactor anterior) coexiste com `iam.infrastructure.repository.*` (usado pelos services atuais). O legado declara `JpaRepository<User, String>` mas `User.id` é `UUID` — risco de ambiguidade/falha de contexto Spring.

**8. N+1 garantido em `@ElementCollection(EAGER)` sem batch fetch** — `User.roles`/`permissions`, `Role.permissions`. Sem `hibernate.default_batch_fetch_size` configurado em nenhum profile.

**9. Múltiplas queries sem paginação em tabelas potencialmente grandes** — `TicketRepository.findByTenantId`, `findOverdueTickets`; `AuditLogRepository.findByTenantIdOrderByCreatedAtDesc` e afins (na própria tabela que a ADR identifica como a que mais cresce).

**10. Duplicação de entrega de eventos de domínio** — `TransactionalEventPublisher.publish()` já dispara os listeners de imediato (antes do commit); `publishAfterCommit` (também listener) republica o mesmo evento após commit → execução duplicada de qualquer handler.

**11. Conflito Flyway × `ddl-auto: create-drop` no profile `test`** — Flyway roda as migrations reais E Hibernate tenta recriar o schema a partir das entidades. O schema de teste diverge do schema real de produção (falta GIN/full-text, `CHECK`, partições, seed).

**12. Constraint única incorreta em `Role.name`** — migration real define `UNIQUE (name, tenant_id)` (composta), mas a anotação JPA declara `unique = true` só na coluna `name`. No profile `test` (`create-drop`), isso impede dois tenants de terem role com mesmo nome — pode mascarar bugs de isolamento multi-tenant nos testes.

## Achados MEDIUM

**13.** `tickets.category_id` sem FK para `categories` apesar de ser referência intra-módulo (não quebraria o encapsulamento do Modulith).
**14.** `AuditListener` é stub vazio — a tabela `audit_logs` existe mas nada a popula; requisito de compliance da ADR não implementado.
**15.** Fallback de senha de banco fraco em texto plano (`DB_PASSWORD:nexusops`).

## Achados LOW
- Logging `TRACE` de bind SQL fora do profile `dev` (mitigado parcialmente pelo profile `prod`, mas risco existe se `prod` não estiver ativo corretamente).
- `role_permissions`/`user_roles`/`user_permissions` usam strings livres em vez de FK para `permissions.permission_key` — perde integridade referencial.

## Pontos positivos confirmados
- Nenhuma concatenação de SQL dinâmico — todas as `@Query` usam `@Param`. **Zero risco de SQL injection.**
- Nenhum relacionamento `@ManyToOne/@OneToMany/@ManyToMany` entre módulos — encapsulamento do Modulith respeitado nos dados (referências sempre por ID simples).
- Tipos de coluna majoritariamente corretos: `UUID`, `TIMESTAMP WITH TIME ZONE`, `JSONB`, `@Version` (optimistic locking) presente na maioria das entidades.
- Índices em FKs e colunas de filtro comuns presentes na maioria das tabelas centrais.

## Avaliação geral da estratégia de banco de dados
- **Migrations:** existem, versionadas com Flyway, seguem convenção — mas duplicadas entre módulos e bootstrap (CRITICAL #3), sem evolução real de schema ainda, particionamento sem plano de manutenção (CRITICAL #4).
- **`ddl-auto`:** correto em prod/dev (`validate`) — risco isolado no profile `test` (HIGH #11).
- **Multi-tenancy:** estratégia documentada (Filter + RLS) não implementada; depende de disciplina manual por query, e já há uma query que falha nisso (CRITICAL #1).

## Classificação: REFACTOR
Justificativa: o desenho geral (separação por schema, tipos de dados, ausência de joins entre módulos, ausência de SQL injection, Flyway) é sólido. Nenhum achado exige reescrever o modelo de dados ou a estratégia de módulos — são correções pontuais e testáveis.
