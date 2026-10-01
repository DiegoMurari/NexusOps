# Auditoria de Backend Java — NexusOps

**Data:** 2026-09-28
**Agente:** `ecc:java-reviewer` (somente leitura, nenhum arquivo alterado)
**Escopo:** `backend/` — Spring Boot 3.2.3, Java 17/21, módulos shared-kernel, iam, ticketing, platform, sla, bootstrap.

## Resumo

Confirma, com evidência de build (`target/`), que **o backend não compila/inicia como um todo**. `nexusops-ticketing` e `nexusops-sla` nunca geraram `.class` files. `nexusops-bootstrap/pom.xml` comenta explicitamente as dependências de 7 dos 10 módulos. Mesmo `iam`+`platform` (os dois que "rodam") têm bugs de inicialização do contexto Spring. Só existem 2 arquivos de teste em todo o backend — qualquer correção precisa de testes novos, não há rede de segurança.

## Achados CRITICAL

### 1. Duplicação de `SecurityConfig`/`JwtAuthenticationFilter` no IAM quebra o boot do Spring
- `iam/security/SecurityConfig.java` e `iam/infrastructure/security/SecurityConfig.java` — duas classes `@Configuration @Bean filterChain` com nomes de bean colidindo (`securityConfig`, `jwtAuthenticationFilter`, `filterChain`) e implementações divergentes.
- **Impacto:** `ConflictingBeanDefinitionException` ou comportamento de segurança indeterminístico.

### 2. Mismatch entre `context-path: /api/v1` e os `requestMatchers` de segurança
- Os dois `SecurityConfig` usam matchers como `/api/v1/auth/login`, mas com `context-path` configurado o Spring Security casa contra o path **sem** o prefixo do contexto. `permitAll()` de login/refresh/MFA/health nunca bate com o path real.
- **Impacto:** ninguém consegue autenticar via API.

### 3. `SecurityUtils` não é bean Spring, mas é injetado em 13 controllers de 3 módulos
- `shared/security/SecurityUtils.java` é `final`, construtor privado, só métodos `static`. Injetado via `@RequiredArgsConstructor` em `TicketController`, `CommentController`, `CategoryController`, `TimeTrackingController` (ticketing); `BusinessCalendarController`, `EscalationRuleController`, `SlaBreachController`, `SlaDefinitionController` (sla); `TenantController`, `SystemSettingController`, `AuditLogController`, `FeatureFlagController` (platform).
- **Impacto:** `UnsatisfiedDependencyException` na criação desses 13 controllers — os módulos ficam inutilizáveis mesmo quando compilam.

### 4. `ticketing`/`sla` — erro real de compilação: `Optional<String>` atribuído a `String`
- `SecurityUtils.getCurrentUserId()`/`getCurrentTenantId()` retornam `Optional<String>`, mas o código faz `String createdBy = securityUtils.getCurrentUserId();` sem `.orElseThrow()`.
- Ocorre em `TicketController` (9 pontos), `CommentController`, `CategoryController` (4), `TimeTrackingController`, `BusinessCalendarController` (4), `EscalationRuleController` (3), `SlaDefinitionController` (3).
- Contraste: `nexusops-platform/TenantController.java:33` faz isso corretamente com `.orElseThrow(...)`.

### 5. `Comment.java` usa `UUID` sem import
- `ticketing/domain/Comment.java`, método `onCreate()`: `UUID.randomUUID().toString()` sem `import java.util.UUID`.

### 6. `nexusops-bootstrap` não inclui os módulos de negócio
- `pom.xml` (linhas 34-68): `sla`, `ticketing`, `asset`, `knowledge`, `notification`, `reporting`, `integration` comentados.
- **Impacto:** mesmo corrigindo os bugs de compilação, o artefato publicado hoje não expõe nenhuma funcionalidade de service desk — o produto core do NexusOps está ausente do build.

### 7. Chave JWT ausente ⇒ segredo HMAC aleatório por instância
- `JwtTokenProvider.init()`: se `JWT_PRIVATE_KEY`/`JWT_PUBLIC_KEY` não estiverem definidas (default vazio), gera `Keys.secretKeyFor(HS256)` — chave nova a cada boot, por instância.
- **Impacto:** em deploy multi-réplica, tokens de uma instância não validam em outra; reinício invalida todos os tokens sem aviso. Sem fail-fast.

## Achados HIGH

**8. Logout/refresh não implementados** — `AuthService.logout()`/`logoutAll()` são no-ops; `refreshToken()` não checa revogação. Refresh token roubado continua válido por até 7 dias mesmo após "logout".

**9. Troca de senha via `@RequestParam`** — `UserController.changePassword` recebe `currentPassword`/`newPassword` na query string (log de proxy/APM), embora exista um `ChangePasswordRequest` DTO correto e não usado.

**10. MFA usa Base64 em vez de Base32** — `MfaService.setupMfa()` gera secret em Base64, mas o padrão TOTP (RFC 6238/otpauth) exige Base32. **Provavelmente incompatível com Google Authenticator/Authy/1Password.**

**11. Filtro `assigneeId` ignorado em `GET /tickets`** — `listTickets()` ignora o parâmetro e chama `findByTenantId` sem filtro de assignee. Usuário pede "meus tickets" e recebe todos os tickets do tenant.

**12. Paginação forjada ao filtrar por status** — `findByTenantIdAndStatus` carrega a lista inteira sem `Pageable` e finge paginação envolvendo em `PageImpl` com `totalElements = tickets.size()`.

**13. `TenantContext` nunca é populado** — nenhum filtro/interceptor chama `TenantContext.setTenantId(...)`. Isolamento de tenant depende 100% de cada query filtrar manualmente.

**14. `Tenant` duplicado em iam e platform** — schemas e tipos de ID diferentes (UUID vs String), CRUD completo duplicado e divergente.

## Achados MEDIUM

**15.** Verificação TOTP sem tolerância de clock-skew e sem proteção a replay (`window: 1` configurado mas nunca usado).
**16.** TOTP secret em texto plano no banco (`MfaSecret.secret` sem `@Convert`).
**17.** `GET /tickets/stats` faz 7 queries `COUNT` em loop em vez de um único `GROUP BY`.
**18.** `User`/`Role` com `@ElementCollection(EAGER)` em duas coleções — N+1 garantido em qualquer listagem.
**19.** Endpoints sem paginação (`PermissionService.findAll()`, `FeatureFlagService`, `SystemSettingService`, `TenantService`).
**20.** Logging `TRACE` de bind SQL habilitado por padrão fora do profile `prod`.
**21.** Fallback de senha de banco fraco (`DB_PASSWORD:nexusops`) sem fail-fast em prod.

## Achado LOW

**22.** `GlobalExceptionHandler` devolve mensagens de negócio (ex. `MFA_REQUIRED`) como `ProblemDetail.detail` genérico em vez de modelar como resposta estruturada.

## Resumo por módulo

| Módulo | Classificação | Justificativa |
|---|---|---|
| shared-kernel | REFACTOR | Conceitos corretos, mas `SecurityUtils` não-bean e `TenantContext` inerte se propagam para 3 módulos consumidores |
| iam | REFACTOR (beira REWRITE na camada de segurança) | Domínio razoável; duas implementações paralelas de SecurityConfig impedem boot correto; MFA/logout quebrados |
| ticketing | REWRITE parcial | Não compila hoje; filtro de assignee ignorado; paginação forjada — mesmo corrigindo compilação, precisa revisão funcional |
| platform | REFACTOR | Módulo mais saudável, mas repete erro de `SecurityUtils`, duplica `Tenant`, `findAll()` sem paginação |
| sla | REWRITE parcial | Não compila hoje (mesmo padrão Optional→String); query dinâmica bem escrita, mas módulo inoperante |
| bootstrap | REFACTOR urgente de escopo | Só sobe iam+platform, e mesmo essa fração tem bugs de boot |
