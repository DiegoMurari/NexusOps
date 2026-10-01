# Auditoria de Segurança — NexusOps

**Data:** 2026-09-28
**Agente:** `ecc:security-reviewer` (somente leitura, nenhum arquivo alterado)
**Escopo:** backend/, frontend/, k8s/, ci-cd/ — comparado a ADR-004 (auth) e ADR-011 (security).

## Resumo executivo

Existe um desalinhamento grave entre o que as ADRs descrevem e o que está implementado. Vários controles "existem" no código mas estão desconectados do runtime; onde estão conectados, contêm falhas críticas de controle de acesso.

## CRITICAL

### 1. A aplicação real não carrega o módulo de segurança do IAM — todo o modelo de auth/authz está inerte em produção
- `NexusOpsApplication` sem `scanBasePackages` cobrindo `com.nexusops.iam`/`com.nexusops.shared`.
- **Prova:** log real mostra "Found 0 JPA repository interfaces" e `DefaultSecurityFilterChain` do Spring Boot padrão (login form + Basic Auth com senha gerada aleatoriamente a cada boot).
- Login, MFA, RBAC/ABAC — nada disso existe no contexto Spring em execução hoje.

### 2. Actuator expõe endpoints sensíveis sem controle de privilégio adequado
- `application.yml`: `management.endpoints.web.exposure.include: health,info,metrics,prometheus,loggers,env,threaddump,heapdump`.
- Mesmo no `SecurityConfig` pretendido, a regra é `anyRequest().authenticated()` — qualquer usuário autenticado (até END_USER) acessaria `/actuator/env` (todas variáveis de ambiente, incluindo senhas/chaves) e `/actuator/heapdump` (dump de memória com tokens/PII).
- O profile `prod` reduz a exposição, mas o profile default/dev (ativo por padrão) não.

### 3. Escalonamento de privilégios via mass assignment em `POST /users`, sem `@PreAuthorize`
- `UserController` não tem `@PreAuthorize` em nenhum endpoint.
- `CreateUserRequest`/`UpdateUserRequest` aceitam `roles` diretamente do body sem allow-list — qualquer chamada autenticada com `roles: ["ROLE_SUPER_ADMIN"]` cria um SUPER_ADMIN.
- `getUser`/`deleteUser` não validam tenant/ownership — IDOR completo entre tenants.
- `changePassword` recebe senha como `@RequestParam` (query string, vaza em logs de proxy).

### 4. `NexusPermissionEvaluator` incompatível com o formato real das authorities
- Avaliador só concede acesso a authorities com prefixo `PERM_`; nenhum código do projeto cria authorities com esse prefixo (usa strings crus como `TICKET:READ:TENANT`).
- Resultado: `hasPermission(...)` retorna `false` sempre — fail-closed hoje, mas evidencia que a camada ABAC nunca foi validada e é risco alto de bypass se "corrigida" às pressas sem revisão.

### 5. JWT "RS256" na ADR é, na prática, HS256 com fallback para chave aleatória por instância
- `JwtTokenProvider.init()` usa `Keys.hmacShaKeyFor(...)` (HMAC simétrico), não RSA.
- Se a env var estiver vazia (default), gera `Keys.secretKeyFor(HS256)` — chave nova a cada boot/instância.
- Em k8s multi-réplica: tokens de um pod são inválidos em outro; reinícios invalidam tokens sem aviso.

### 6. Revogação/rotação de refresh token descrita na ADR não está implementada
- `refreshToken()`: `// TODO: Check refresh token in database (rotation)`.
- `logout()`/`logoutAll()`: corpo vazio, `// TODO: Invalidate refresh token in database`.
- Refresh token roubado continua válido por até 7 dias mesmo após logout — sem blacklist, sem detecção de reuso.

## HIGH

**7.** Refresh token (7 dias) em `localStorage` no frontend (`auth.service.ts`), contrariando a ADR-004 que pede cookie httpOnly/Secure/SameSite=Strict. Amplia o raio de um XSS de "sequestro temporário" para "takeover de conta por até 7 dias".

**8.** Interceptor Angular envia `Authorization: Bearer` para qualquer requisição, sem checar domínio de destino — risco de vazamento a domínios externos se o app evoluir.

**9.** TOTP secret em texto puro no banco (`MfaSecret.secret` sem `AttributeConverter`), contrariando a ADR-004 ("secret encrypted at rest").

**10.** Bug de implementação faz `setupMfa()` sempre lançar `StringIndexOutOfBoundsException` (`Base64` de 20 bytes = 28 chars, `.substring(0,32)` estoura) — **MFA está indisponível na prática**.

**11.** Códigos de recuperação MFA hasheados com SHA-256 simples (sem salt) em vez de bcrypt (pedido pela ADR); fluxo de verificação de recovery code no login nem existe.

**12.** Ausência de bloqueio de conta por tentativas falhas apesar de configurado (`max-failed-attempts: 5`) — `AuthService.login()` não incrementa nenhum contador. Sem proteção de força-bruta/credential-stuffing.

**13.** Duas implementações divergentes de `SecurityConfig`/`JwtAuthenticationFilter` coexistem no IAM — risco de conflito de bean ou política de autorização imprevisível quando o scan (item 1) for corrigido.

## MEDIUM

**14.** Mensagens de erro verbosas (`include-message: always`) não sobrescritas no profile `prod` — permite enumeração de usuários/tenants/roles.

**15.** Logging `DEBUG`/`TRACE` por padrão, incluindo bind de parâmetros SQL (senhas, tokens) — combinado com ausência de `.gitignore`, risco de commit acidental do log.

**16.** Ausência de `.gitignore` no repositório — `target/`, `logs/`, `node_modules/`, `.angular/cache/` desprotegidos contra commit acidental.

**17.** `.github/dependabot.yml` ausente apesar de exigido pela ADR-011 (CodeQL/Trivy/OWASP Dependency-Check/TruffleHog já estão OK).

**18.** `k8s/base/secret.yaml` usa `Secret` Opaque puro (hoje vazio, sem vazamento real) em vez de Sealed/External Secrets — padrão arriscado se alguém preencher valores reais.

## LOW / Falsos-positivos notados
- Credenciais fracas em `docker-compose.dev.yml` — escopo de dev local, risco baixo.
- Queries JPA usam parâmetros nomeados corretamente — sem SQL injection.
- Nenhum uso de `innerHTML`/`bypassSecurityTrustHtml` no frontend — sem XSS via output não sanitizado identificado.
- CORS: propriedades `nexusops.cors.*` existem mas **nenhum bean `CorsConfigurationSource`** as aplica — config "morta" (não é CORS permissivo, mas o frontend provavelmente vai falhar por CORS assim que a stack subir corretamente).

## Dependências
`backend/pom.xml`: Spring Boot 3.2.3, Spring Cloud 2023.0.1, Spring Modulith 1.2.1, jjwt 0.12.5, PostgreSQL driver 42.7.3, Jackson 2.16.1 — nenhum CVE crítico conhecido identificado por inspeção estática (recomenda-se rodar `mvn dependency-check:check` e `npm audit` de fato).

## Observação final
O achado mais importante é a combinação dos itens 1, 3 e 4: o mecanismo de segurança "de papel" está bem desenhado na documentação, mas (a) não está conectado ao runtime, e (b) mesmo se estivesse, contém pelo menos uma falha crítica de controle de acesso que permitiria escalonamento de privilégio total. **Itens 1, 3, 4, 5 e 6 são bloqueadores de produção, nessa ordem.**
