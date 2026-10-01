# Auditoria de Testes — NexusOps

**Data:** 2026-09-28
**Agente:** `ecc:tdd-guide` (somente leitura, nenhum arquivo criado/editado)
**Escopo:** backend/ (213 arquivos .java de produção), frontend/ (48 arquivos .ts), tests/performance/k6.

## O que os 2 testes existentes realmente verificam

Local: `backend/nexusops-shared-kernel/src/test/java/com/nexusops/shared/architecture/` — `LayerRulesTest.java` (8 métodos) e `ModuleBoundaryRulesTest.java` (18 métodos).

Ambos usam **ArchUnit** para validar exclusivamente convenções estruturais/de pacotes (ex.: "controllers não acessam repositories diretamente", "sem ciclos entre módulos"). **Zero teste de comportamento, cálculo, transição de estado, autenticação ou autorização.** Nenhum outro módulo tem qualquer arquivo em `src/test`.

## Confirmação: ausência de testes de integração/API
Busca por `@SpringBootTest`, `MockMvc`, `RestAssured`, `Testcontainers` em todo `backend/**/*.java`: **zero ocorrências.**

## `tests/performance/k6`
Diretório **vazio** — sem nenhum script `.js`/`.ts`. Estrutura de pastas existe, mas nunca foi implementado.

## Frontend
- Infraestrutura de teste (Karma/Jasmine) configurada em `angular.json`/`package.json`, mas **0 arquivos `*.spec.ts`** para 48 arquivos de produção.
- Scripts `e2e`/`e2e:headed`/`e2e:ui` (Playwright) existem no `package.json`, mas **não existe `playwright.config.ts`** nem diretório `e2e/` — `npm run e2e` falharia hoje.
- Módulos críticos sem cobertura: `core/auth/auth.guard.ts`, `auth.service.ts`, `mfa.guard.ts`, `role.guard.ts`, toda a feature `ticketing/` e `sla/`.

## Fluxos críticos SEM nenhuma proteção de teste

**Autenticação/Sessão** — `AuthService.java` (132 linhas, zero testes):
- `login()`: senha, status da conta, MFA obrigatório/opcional — nenhum ramo testado.
- `logout()`/`logoutAll()` são **no-ops** — descoberto só por leitura manual, não por teste que capturasse a regressão.
- Existem **dois** `JwtAuthenticationFilter` — sem teste que evidencie qual está de fato ativo.

**Autorização** — 13 arquivos usam `@PreAuthorize`/`@Secured` — nenhum teste verifica que um usuário sem permissão é de fato bloqueado.

**Criação e transição de estado de ticket** — `TicketService.isValidTransition()`: máquina de estados com 7 status — zero teste cobrindo transições válidas/inválidas ou casos de borda (CLOSED→REOPENED).

**Cálculo de SLA** — duas implementações coexistindo:
- `sla/service/SlaCalculationService.java` (236 linhas, algoritmo de calendário de negócio/timezone/pausa) — zero teste de casos de borda (meia-noite, fim de semana, feriado, SLA pausado).
- `ticketing/service/SlaCalculationService.java` (29 linhas, **mesmo nome**, todos os métodos são no-ops) — `TicketService.createTicket()` chama `startSlaTimer` esperando que o timer comece, mas não faz nada. **Lacuna funcional grave sem qualquer teste que a revele.**

## Risco concreto para evolução do projeto
- Refactor em `isValidTransition()` pode permitir transições inválidas sem que ninguém note.
- Refactor em `calculateDueTime`/`calculateRemainingMinutes` pode inverter o sinal de "tempo restante" ou ignorar timezone — impacto direto em relatórios de breach de SLA e escalonamento, sem rede de segurança.
- Refactor em `AuthService.login`/`refreshToken` pode reintroduzir bypass de MFA sem detecção.
- A duplicação de `JwtAuthenticationFilter` e `SlaCalculationService` (nomes iguais, comportamento diferente) é uma armadilha para qualquer novo desenvolvedor — autocomplete pode importar a classe errada silenciosamente.
- Guards de rota no frontend sem teste — refactor de roteamento pode remover proteção sem aviso.

## Classificação: CRITICAL
Não é "baixa cobertura" — é ausência total de rede de segurança para os fluxos que mais importam ao negócio, combinada com lógica incompleta/no-op (logout, timer de SLA) só identificada por leitura manual.

## Ordem de prioridade recomendada para investir em testes
1. **AuthService (IAM)** — unit tests para `login`/`refreshToken` (credenciais inválidas, conta inativa, MFA); depois teste de integração (`@SpringBootTest`+`MockMvc`) no `AuthController`. Resolver o TODO de invalidação de refresh token antes de qualquer outra coisa.
2. **Autorização** — teste do `PermissionEvaluator`/`SecurityConfig` garantindo 403 para quem não tem permissão.
3. **`TicketService.isValidTransition`/`transitionTicket`** — testes parametrizados de todas as transições, mais efeitos colaterais (timestamps, eventos).
4. **`SlaCalculationService` (módulo sla)** — testes de unidade para `calculateDueTime`/`calculateRemainingMinutes` com casos de borda.
5. Resolver a duplicação `SlaCalculationService`/`JwtAuthenticationFilter` com teste que comprove qual está ativo.
6. Teste de integração ponta a ponta (`@SpringBootTest`+Testcontainers) cobrindo "criar ticket → transicionar → SLA calculado/breach detectado".
7. Frontend: começar pelos guards (Karma/Jasmine já configurado, só faltam specs), depois `auth.service.ts`; Playwright só depois de smoke tests unitários.
