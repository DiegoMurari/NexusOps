# Plano de Refatoração — NexusOps

**Data:** 2026-09-28
**Baseado em:** ARCHITECTURE-AUDIT.md, BACKEND-AUDIT.md, DATABASE-AUDIT.md, SECURITY-AUDIT.md, TEST-AUDIT.md, FRONTEND-AUDIT.md, INFRASTRUCTURE-AUDIT.md
**Metodologia:** 7 auditorias independentes (agentes especializados ECC), todas somente-leitura. Nenhum código foi alterado até este ponto.

## Estado atual do projeto em uma frase

O NexusOps tem uma **fundação de design excelente** (ADRs maduras, bounded contexts coerentes, schema de dados correto, frontend Angular moderno) e uma **implementação que nunca foi integrada de ponta a ponta**: o backend sobe sem carregar os módulos de negócio, dois módulos não compilam, a segurança "de papel" está desconectada do runtime, e não há rede de testes para nenhum fluxo crítico.

Isso não é o resultado típico de "código ruim escrito por um modelo fraco" — é o resultado típico de **módulos desenvolvidos em paralelo e nunca integrados**: cada peça isolada é razoável, mas a montagem final nunca foi validada (nenhum teste de boot, nenhum CI verde, nenhum `mvn clean install` na raiz aparentemente).

## Classificação consolidada (KEEP / REFACTOR / REWRITE / REMOVE)

| Área | Classificação | Achados-chave |
|---|---|---|
| **Arquitetura geral / ADRs** | KEEP (documentação) / REFACTOR (implementação) | Design sólido, execução desintegrada |
| **shared-kernel** | REFACTOR | `SecurityUtils` não-bean, `TenantContext` inerte, publisher de eventos duplica entrega |
| **iam** | REFACTOR (beira REWRITE na camada de segurança) | 2x SecurityConfig conflitantes, MFA quebrado (bug real), logout no-op |
| **platform** | REFACTOR | Módulo mais saudável; duplica `Tenant` com iam |
| **sla** | REWRITE parcial (lógica de negócio é boa, mas não compila) | Import faltante; `EscalationService` hardcoded para tenant "default" |
| **ticketing** | REWRITE parcial (domain + TicketService) | Não compila (`@Builder` em hierarquia); filtro de assignee ignorado; paginação forjada |
| **bootstrap** | REFACTOR urgente de escopo | Só inclui iam+platform; component-scan não cobre nem esses dois módulos direito |
| **asset, integration** | KEEP | Stubs coerentes com roadmap (ADR-012 Fase 3) |
| **knowledge** | KEEP (ajuste pontual) | Renomear `Category` para evitar colisão com ticketing |
| **reporting** | KEEP (revisar desenho antes de implementar) | ADR pede CQRS/read models, não entidades de escrita |
| **notification** | KEEP e priorizar | Está atrasado (Fase 2), é dependência do sla |
| **Banco de dados** | REFACTOR | Sem SQL injection, sem joins entre módulos — mas migrations duplicadas, multi-tenancy sem enforcement, 1 query sem filtro de tenant |
| **Segurança (auth/authz)** | REFACTOR urgente | Segurança "de papel" desconectada do runtime + falha crítica de controle de acesso (`UserController`) |
| **Testes** | ADICIONAR DO ZERO | Não é refactor — não existe rede de testes de comportamento para refatorar sobre |
| **Frontend Angular** | REFACTOR | Arquitetura sólida (standalone, signals); token em localStorage e zero testes são os bloqueadores |
| **Infraestrutura (K8s/CI-CD/Docker)** | REFACTOR | Base GitOps bem pensada; RBAC/secrets/deploy de produção ausentes |
| **Terraform** | INCOMPLETO (esqueleto, Fase 3+ do roadmap) | Ambiente de produção nem existe ainda |

**Nenhuma área do projeto foi classificada como REMOVE em bloco.** Remoção se aplica a arquivos específicos (ver lista abaixo), nunca a um módulo inteiro.

### Arquivos/artefatos específicos candidatos a remoção
- `backend/nexusops-iam/src/main/java/com/nexusops/iam/repository/*` (pacote legado, duplicado de `infrastructure/repository`)
- `backend/nexusops-iam/src/main/java/com/nexusops/iam/security/*` (pacote legado, duplicado de `infrastructure/security`) — **decisão de qual dos dois pacotes manter precisa ser tomada por você**, não presumida
- `backend/nexusops-ticketing/src/main/java/com/nexusops/ticketing/service/SlaCalculationService.java` (no-op, nome duplicado do serviço real em `sla`)
- `backend/nexusops-iam/src/main/java/com/nexusops/iam/domain/Tenant.java` e fluxo de tenant do iam (manter só em `platform`) — **decisão de qual módulo é o dono precisa ser sua**
- Cópias de migration duplicadas em `backend/nexusops-bootstrap/src/main/resources/db/migration/`

## Matriz de priorização (CRITICAL → LOW)

### CRITICAL — bloqueadores absolutos (nada funciona em produção sem isso)
1. **[Arquitetura]** Component-scan do `NexusOpsApplication` não cobre os módulos — nenhum módulo de negócio carrega.
2. **[Backend]** `sla` e `ticketing` não compilam (imports faltantes, uso incorreto de `@Builder`).
3. **[Backend]** `SecurityUtils` injetado como bean em 13 controllers, mas não é um bean — quebraria o boot desses 3 módulos.
4. **[Segurança]** Sem o scan corrigido, a app roda com Spring Security padrão (Basic Auth com senha aleatória) — toda a autenticação/autorização documentada está inerte.
5. **[Segurança]** `UserController` sem `@PreAuthorize` + mass assignment de `roles` — qualquer usuário autenticado pode se autopromover a SUPER_ADMIN.
6. **[Dados]** Query de busca de tickets sem filtro de tenant — vazamento cross-tenant confirmado no código.
7. **[Dados]** Migrations duplicadas entre módulos e bootstrap — Flyway falha assim que os módulos forem integrados.
8. **[Dados]** Admin padrão com senha conhecida (documentada em comentário SQL) inserido também em produção.
9. **[Infra]** Secrets do K8s vazios, RBAC ausente, placeholders não resolvidos no Ingress, credenciais AWS não-OIDC.

### HIGH — corrigir logo após os CRITICAL
10. Duplicação de `SecurityConfig`/`JwtAuthenticationFilter`/`Tenant`/`SlaCalculationService` no IAM/ticketing/sla (causa raiz de vários CRITICAL).
11. Logout/refresh token são no-ops — sem revogação real de sessão.
12. MFA tem bug real (`setupMfa` sempre lança exception) e usa Base64 em vez de Base32 — feature indisponível/incompatível na prática.
13. JWT usa HS256 com fallback para chave aleatória por instância (deveria ser RS256 fixo).
14. Filtro `assigneeId` ignorado e paginação forjada no `TicketController`.
15. `TenantContext` nunca populado — multi-tenancy depende 100% de disciplina manual.
16. Ausência total de testes de comportamento (0 testes de negócio no backend, 0 specs no frontend).
17. JWT em `localStorage` no frontend + token vazando em URL de WebSocket.
18. Testes ArchUnit não verificam nada de fato (classpath errado).

### MEDIUM
19. Prefixo de URL duplicado (`/api/v1/api/v1/...`).
20. Autorização inconsistente entre módulos (`@PreAuthorize` presente em alguns controllers, ausente em outros do iam).
21. N+1 em `User`/`Role` (EAGER sem batch fetch); queries sem paginação em `AuditLogRepository`/`PermissionService`/etc.
22. Particionamento de `audit_logs` sem partição futura (bomba-relógio, já estourando desde 2025-01).
23. Job de escalonamento de SLA hardcoded para tenant "default" — nunca escalona nada de verdade.
24. Logging `TRACE` de bind SQL habilitado por padrão (vaza dados sensíveis em log).
25. Dockerfile do backend sem non-root/distroless/JVM tuning; SecurityContext do frontend incompleto no K8s.
26. Pipeline de CI sem gate de segurança (scans não bloqueiam merge) e sem deploy de produção.

### LOW
27. Código morto (`iam/repository`, `iam/security` legados), configs de profile nunca ativados, ESLint ausente no frontend, CSS duplicado no `login.component`, `.gitignore` ausente na raiz do repo, `dependabot.yml` ausente.

## Fase 12 — Perguntas de decisão (não decidi por você)

**1. O NexusOps atual possui uma base aproveitável?**
Sim. Todas as 7 auditorias, independentemente, chegaram à mesma conclusão: o design (ADRs, bounded contexts, schema de dados, arquitetura frontend) é sólido. O problema é execução/integração, não desenho.

**2. Quais partes devem ser preservadas?**
- As 12 ADRs (ajustando só onde a implementação revelou inconsistência, ex.: exemplo de Hibernate Filter na ADR-003).
- Estrutura de módulos Maven e separação por schema no banco.
- `shared-kernel` (conceitos: `DomainEvent`, exceções, `GlobalExceptionHandler`) — refatorado, não descartado.
- Domínio de `sla` (`SlaCalculationService` real, 236 linhas) — a lógica de negócio mais madura do projeto.
- Arquitetura do frontend Angular (standalone, signals, guards, interceptors).
- ArgoCD/Kustomize (camada mais madura da infraestrutura).

**3. Quais partes precisam ser refatoradas?**
`shared-kernel`, `iam`, `platform`, `sla`, `bootstrap`, banco de dados (isolamento multi-tenant, migrations), segurança (auth/authz completo), frontend (token storage, error handling, performance), infraestrutura (RBAC, secrets, hardening de Docker).

**4. Existe alguma parte que realmente deveria ser reescrita?**
Parcialmente, e só dentro de módulos específicos — nenhum agente recomendou reescrever um módulo inteiro:
- `ticketing`: a camada `domain` (`Ticket`/`Incident`/`Problem`, trocar `@Builder` por `@SuperBuilder`, corrigir a modelagem Incident vs Problem) e `TicketService` (a máquina de estados deveria estar no agregado, não no service).
- `sla`: a integração com `ticketing` (a "porta" `NotificationService` deveria ser um evento, não uma interface órfã).
- Camada de autenticação do `iam`: escolher uma das duas implementações de `SecurityConfig` e reescrever a partir dela.

**5. É justificável começar do zero?**
Não, segundo as 7 auditorias. O padrão dominante é "peças bem desenhadas isoladamente, nunca integradas" — isso se resolve integrando e testando, não reescrevendo. Uma reescrita completa descartaria trabalho de design real (ADRs, schema, domínio de SLA) que está correto.

**6. Qual seria o custo/risco de reescrever do zero?**
- Descartaria 12 ADRs maduras e o trabalho de modelagem de domínio já validado (SLA, ticketing, IAM).
- Não eliminaria o risco: um novo desenvolvimento teria os mesmos riscos de integração se não houver disciplina de testes/CI desde o início — o problema raiz não foi "código ruim", foi "nunca rodou de ponta a ponta".
- Custo de tempo alto sem redução proporcional de risco.

**7. Qual seria o custo/risco de evoluir a implementação existente?**
- Risco baixo-médio: os bugs são concretos, localizados e corrigíveis (imports faltantes, scan de pacotes, um método sem filtro de tenant) — não exigem redesenho.
- Custo estimado por camada, segundo as auditorias: backend ~2-3 semanas para ter um boot real + segurança funcional; testes ~contínuo, começando pelos fluxos críticos (auth, transição de ticket, cálculo de SLA); infraestrutura ~4-5 semanas para production-ready.
- Principal risco do caminho de evolução: fazer correções sem testes que as protejam — por isso a auditoria de testes recomenda testar **antes ou durante** a correção dos bugs críticos, não depois.

## Recomendação de ordem de correção (se você decidir evoluir, não reescrever)

1. **Fazer o backend compilar e subir com todos os módulos** (arquitetura P1-P3, backend #1-#6) — sem isso, nada mais pode ser validado de verdade.
2. **Corrigir os bloqueadores de segurança** (scan de pacotes, `@PreAuthorize` no `UserController`, JWT com chave fixa) — antes de qualquer exposição externa.
3. **Escrever testes para os fluxos críticos identificados** (auth, transição de ticket, cálculo de SLA) — em paralelo ou imediatamente após as correções acima, para não corrigir "no escuro" de novo.
4. **Resolver a duplicação de dados** (vazamento cross-tenant, migrations duplicadas, admin padrão).
5. **Frontend:** mover token para armazenamento seguro + testes básicos de guards/auth.
6. **Infraestrutura:** RBAC + secrets antes de qualquer deploy real; deploy de produção só depois disso.

## O que este plano NÃO decide
Conforme solicitado, esta auditoria não determina se o time deve reescrever, refatorar ou remover — apenas organiza a evidência para essa decisão ser sua. Nenhum código foi alterado, criado ou deletado durante toda a execução das 7 auditorias.
