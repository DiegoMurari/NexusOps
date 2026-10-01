# Auditoria de Frontend Angular — NexusOps

**Data:** 2026-09-28
**Agente:** auditoria especializada Angular (somente leitura, nenhum arquivo alterado)
**Escopo:** `frontend/src/app` — Angular 17.3, 48 arquivos TS (core, features, shared), 0 arquivos `.spec.ts`.

## Resumo executivo
Fundação arquitetural sólida (standalone components, lazy loading, signals do Angular 17), mas **3 vulnerabilidades CRITICAL** em gestão de token/autenticação e ausência total de testes. Adequado para REFATORAÇÃO, com prioridade em hardening de segurança.

## CRITICAL

### 1. Tokens JWT armazenados em `localStorage`
- `core/auth/auth.service.ts` (linhas ~103-105, 147-150): access e refresh token gravados em `localStorage`.
- **Impacto:** vulnerável a XSS — qualquer script injetado pode roubar os tokens.
- **Recomendação:** mover refresh token para cookie httpOnly/Secure/SameSite=Strict (setado pelo backend); manter access token em memória/signal, nunca em localStorage.

### 2. Token JWT passado via query parameter de URL
- `core/notification/notification.service.ts` (linhas 46, 74): token de acesso exposto em URL de WebSocket/SSE (`ws://...?token=<JWT>`).
- **Impacto:** token fica em histórico do navegador, logs de acesso do servidor, e pode vazar via header `Referer`.
- **Recomendação:** autenticar via mensagem inicial no protocolo WS (`ws.send({type:'auth', token})`) em vez de query string.

### 3. Cobertura de teste zero
- 0 arquivos `.spec.ts` para 48 arquivos de produção.
- **Impacto:** vulnerabilidades de segurança só detectadas por auditoria manual; regressões passam sem alarme; CI sem gate de qualidade.
- **Recomendação:** priorizar `auth.service`, guards, interceptor de erro, fluxos de login/MFA.

## HIGH

**4.** Sem `ErrorHandler` global — tratamento de erro espalhado pelos componentes; erros 401/403 tratados no interceptor, mas outros erros passam sem log/tratamento uniforme.

**5.** CSS duplicado em `login.component.ts` — o bloco inteiro de estilos aparece repetido (linhas ~152-258 e ~260-325), ~50% de tamanho desnecessário do arquivo.

**6.** Nenhum componente usa `ChangeDetectionStrategy.OnPush` — checagem completa da árvore a cada evento; impacto em listas/tabelas (dashboard, tickets).

## MEDIUM

**7.** `mfa-challenge.component.ts` usa `alert()` nativo do browser para o fluxo de recovery code — inconsistente com Material Design, bloqueia interação.

**8.** Subscrições HTTP sem `takeUntilDestroyed` em `login.component.ts`/`mfa-challenge.component.ts` — `NotificationService` usa o padrão correto, mas não é aplicado de forma consistente.

## LOW

**9.** Sem configuração de ESLint (`.eslintrc.json`/`eslint.config.js`) apesar do script `ng lint` existir no `package.json`.

**10.** Padrões de tratamento de erro inconsistentes entre services (pipe `tap`/`catchError`) e componentes (callback de `subscribe`).

## Pontos positivos confirmados
- Standalone components em 100% dos componentes (padrão Angular 17).
- Lazy loading de rotas de feature via `loadChildren`/`loadComponent`.
- TypeScript estrito habilitado, **nenhum uso de `any` encontrado**.
- Nenhum uso de `innerHTML`/`bypassSecurityTrust*` — sem vetor de XSS via output no código atual.
- Guards de rota (`auth`, `mfa`, `role`) implementados corretamente.
- Interceptors funcionais (auth + error) configurados.
- Angular Signals usados para estado (`AuthService.user`, `NotificationService`).
- Nova syntax de controle de fluxo (`@for`/`@if`/`@empty`).
- Injeção de dependência consistente (`inject()`, `providedIn: 'root'`).

## Checklist de segurança

| Item | Status |
|---|---|
| Proteção CSRF | Depende do backend (não verificável no frontend isoladamente) |
| Prevenção XSS | ✓ Seguro (sem innerHTML/bypass) |
| Armazenamento de token | ✗ Quebrado (localStorage) |
| Autorização (guards) | ✓ Implementado |
| HTTPS/TLS | ✓ Configurado em `environment.prod.ts` |
| Headers seguros (CSP, X-Frame-Options) | Depende do backend |
| Validação de entrada | ✓ Reactive Forms com Validators |
| Gestão de sessão | ✗ Quebrado (tokens expostos, sem revogação real — ver auditoria de segurança) |

## Classificação final: REFACTOR
- **KEEP** a fundação arquitetural: standalone, lazy loading, signals, guards.
- **REFACTOR** gestão de token (mover para cookie httpOnly), performance (OnPush), tratamento de erro (handler global).
- **ADD** suite de testes completa.
- Não é REWRITE — a arquitetura é sólida, só a segurança/qualidade precisa de correção. Com 2-3 semanas de trabalho focado em segurança e testes, fica pronto para produção. Os 48 componentes são majoritariamente stubs — não há dívida técnica legada complexa.
