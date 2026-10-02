# NexusOps — Session Checkpoint

**Data do checkpoint:** 2026-09-29 (revisado em 2026-09-30 para refletir o estado real do ambiente antes de encerrar)
**Propósito:** Permitir que outra sessão de trabalho retome o trabalho exatamente de onde parou, sem depender do histórico de conversa desta sessão.

---

## 0000. RODADA "PRODUTO COMPLETO" (a partir de 2026-10-02) — LEIA PRIMEIRO

**Premissa corrigida:** os seis módulos (Assets, Knowledge, Reports, Integrations, Roles, Audit) **já não são stubs**: foram implementados e validados nas rodadas P0–P6 abaixo. Esta rodada fecha as lacunas reais de cada um, sem refazer nada.

**Ordem escolhida (por dependência):** 1 Audit (transversal; o rastro vale para tudo que vem depois) → 2 Roles (só testes de escalada de privilégio) → 3 Assets (histórico + vínculo com tickets) → 4 Knowledge (vínculo com tickets, mesmo padrão dos Assets) → 5 Reports (consome tickets/SLA/ativos/conhecimento, por isso depois deles; agregação em SQL, backlog/tendências, `Dashboard`/`Widget` sem controller) → 6 Integrations (health/status, log de execução, entrega; depende dos eventos de auditoria).

**Status da rodada (2026-10-02): os seis módulos estão CONCLUÍDOS (seções 1–6 abaixo).** Estado verificado: backend inteiro verde (ArchUnit 9 + 21), `ng build` OK, QA no Chrome real de cada módulo. **Nada foi commitado nesta rodada** (só a pedido do usuário, sem autoria do Claude). Próximos passos sugeridos, em ordem de valor: (a) envio automático dos eventos do sistema para os webhooks (fila + reentrega, reusando `SafeHttpSender`), (b) criptografar em repouso o segredo do webhook, (c) `ng lint` (falta `@angular-eslint`) e mais specs de frontend (só 2), (d) executor dos relatórios agendados, (e) agregação de relatórios em SQL.

### 1. Audit — CONCLUÍDO (2026-10-02)
- **Implementado:** logout e logout-em-todos-os-dispositivos viram eventos (`SessionEndedEvent` no iam → `AuditSessionListener` na platform, `auth.logout` / `auth.logout.all`, ação `LOGOUT`); busca por texto `q` (evento, usuário, tipo e id do recurso, com curingas do LIKE escapados) e período `since`/`until` em `GET /audit-logs`; frontend com busca, datas e **painel de detalhe do evento** (quem, quando, tenant, o quê, recurso, resultado, origem, navegador, payload formatado).
- **Arquivos:** `iam/event/SessionEndedEvent`, `iam/service/AuthService`, `platform/audit/AuditSessionListener`, `platform/service/AuditService`, `platform/controller/AuditLogController`, `frontend/.../audit-log-viewer.component.ts`, `core/platform/platform.service.ts`.
- **Testes:** `AuditSessionListenerTest` 2, `AuditServiceTest` 2 (escape do LIKE); iam 32/32 e platform 9/9. `ng build` OK.
- **API (curl):** falha de login `BAD_CREDENTIALS`, logout e logout-all registrados, `q=%` casa literalmente (0), `until` no passado → 0, sem token → 401.
- **Navegador (Chrome real):** lista, busca "logout" (2 linhas), detalhe do evento, filtro de data com estado vazio, console sem erros.
- **Achado fora do módulo e corrigido:** o shell não era responsivo — a barra superior (busca de 220px + ações) estourava a largura em **todas** as telas no celular. Agora o menu começa recolhido abaixo de 900px e a busca global some abaixo de 700px (`app.component.ts`). Overflow de página verificado em auditoria, dashboard, tickets e filas. **`/reports` ainda estoura (690px)** — fica para a reconstrução de Reports.
- **Segurança:** consultas sempre escopadas ao tenant do token; página limitada a 200; payload nunca guarda corpo da requisição; `q` não interpreta curingas.
- **Limites que permanecem:** sem auditoria de leituras (GET) nem de refresh; `previous_state` continua vazio (não há "antes/depois" nas alterações); sem filtro por resultado (SUCCESS/FAILURE vive no JSON do payload).
- **Pendente:** commit (só quando o usuário pedir, sem autoria do assistente).

### 2. Roles — CONCLUÍDO (2026-10-02) — só reforço, nada reescrito
- **Verificado:** o módulo já estava completo (CRUD, catálogo de permissões, papéis de sistema imutáveis, teto de permissões do chamador, tenant vindo do servidor). Os endpoints `POST/DELETE /roles/{id}/permissions` também passam pelas mesmas travas.
- **Testes novos (RoleServiceTest 9 → 12):** conceder permissão acima do teto do chamador, mexer em papel de sistema por esses endpoints, remover permissão de papel de outro tenant. iam 35/35.
- **Escalada testada pela API real, como solicitante (END_USER):** listar/criar/editar papel, conceder permissão ao ADMIN, listar usuários, criar usuário com papel ADMIN, ler auditoria e feature flags → tudo 403. ADMIN (não super) editando o papel de sistema ADMIN → 422.
- **Bug achado e corrigido:** negação de acesso decidida na cadeia de filtros (`/tenants/**`, `/actuator/**`) devolvia **401** a quem estava autenticado mas sem permissão — o frontend trata 401 como sessão expirada. Agora há `accessDeniedHandler` explícito: sem permissão → 403; sem token ou token inválido → 401 (`iam/security/SecurityConfig.java`).
- **Navegador:** a tela de papéis não mudou nesta rodada (já validada em 2026-10-01).

### 3. Assets — CONCLUÍDO (2026-10-02)
- **Bug de isolamento achado e corrigido:** `asset_tag` era `UNIQUE` no banco inteiro (um tenant usando "NB-001" bloqueava todos os outros e a colisão virava 500). Agora `UNIQUE (tenant_id, asset_tag)` — migração `V1_18_0` (asset).
- **Histórico:** tabela `asset.asset_history` (uma linha por campo alterado, criação e vínculo/desvínculo de ticket; ator = e-mail do principal). `GET /assets/{id}/history` paginado (máx. 100). O `AssetService` compara antes/depois e só grava o que mudou; valores legíveis (nome do responsável e da localização, não ids).
- **Vínculo com tickets:** tabela `asset.asset_ticket_links` + porta `shared.directory.TicketDirectory` (implementada no ticketing, mesmo padrão de `UserDirectory`/`LocationDirectory`, sem acoplar asset→ticketing). `PUT/DELETE /assets/{id}/tickets/{ticketId}` (idempotentes, `ASSET:UPDATE`), `GET /assets/{id}/tickets`, `GET /assets/by-ticket/{ticketId}`. Ticket de outro tenant → 404.
- **Responsável e localidade validados** (ativos e do mesmo tenant) na criação e na edição; string vazia limpa o campo. Novo `GET /assets/assignees?q=` (`ASSET:UPDATE`) via `UserDirectory.searchActive` (limite 10, só ativos do tenant, LIKE escapado). `AssetDto` ganhou `assignedToName`/`locationName` no detalhe.
- **Frontend:** detalhe do ativo reescrito em partes — selo de situação com `nx-status-badge` (sem pílula), nomes no lugar de UUIDs (inclui relacionamentos CMDB), **modo Editar** (nome, fabricante, modelo, série, localização, responsável com busca, descrição), card **Tickets vinculados** (busca por número/título, vincular/remover), card **Histórico** em linha do tempo com "Ver mais". Tela do ticket ganhou o painel **Ativos relacionados** e o nó "Ativo" da cadeia usa o ativo vinculado (cai no texto livre `ciReference` se não houver).
- **Testes:** `AssetServiceTest` 5 → 13, `AssetTicketLinkServiceTest` 8 (novo); asset 29/29, iam 35/35, ticketing 55/55, ArchUnit 9 + 21. `ng build` OK sem avisos.
- **API real:** criar/editar gera histórico correto, responsável inexistente → 422, tag duplicada → 422, vínculo repetido → 200 sem duplicar, ticket inexistente → 404, solicitante (END_USER) → 403 em todas as rotas novas.
- **Navegador (Chrome real):** detalhe, desvincular/vincular pela busca, painel no ticket, edição completa (responsável pela busca, localização, fabricante/modelo → histórico), celular sem overflow, console sem erros.
- **Segurança:** tudo escopado por `tenantId` do token; permissões `ASSET:READ/UPDATE`; histórico imutável; ator vem do servidor.
- **Limites que permanecem (Assets):** a listagem de ativos (já nos componentes compartilhados) não tem colunas de responsável/localização nem filtro por elas na tela (a API tem `assignedToId`/`locationId`); sem exclusão lógica (delete apaga histórico em cascata); dados antigos de QA no banco de dev (localidades "Franca"/"Antiga" duplicadas, usuários `*.b.*`).

### 4. Knowledge — CONCLUÍDO (2026-10-02)
- **Falha de controle de acesso achada e corrigida:** qualquer pessoa com `KNOWLEDGE:READ` (ex.: papel personalizado só de leitura) via rascunhos e arquivados de outros, na lista e no detalhe, e cada abertura contava visualização. Agora quem não tem `KNOWLEDGE:UPDATE` só enxerga artigos **publicados** (lista, detalhe e vínculos); rascunho/arquivado = 404; visualização só conta em artigo publicado. Testado de verdade: papel `QA_READER` + usuário → rascunho 404, lista só `PUBLISHED`, editor vê tudo.
- **Vínculo com tickets:** `knowledge.article_ticket_links` (migração `V1_19_0`), mesma porta `TicketDirectory`. `GET/PUT/DELETE /articles/{id}/tickets[/{ticketId}]` (PUT/DELETE exigem `KNOWLEDGE:UPDATE`, idempotente), `GET /articles/by-ticket/{ticketId}`. Ticket de outro tenant → 404.
- **Listagem reescrita com `Specification`:** filtros agora combinam (antes só valia o primeiro: busca OU status OU categoria); busca por texto com curingas do LIKE escapados; removida a query `search` que ficou sem uso.
- **Frontend:** detalhe do artigo com `nx-status-badge` (sem pílula), ações/editar/publicar/excluir só para quem pode editar, card **Tickets relacionados** (busca, vincular, remover); detalhe do ticket com painel **Artigos relacionados** (busca de artigos publicados; vincular/remover para quem edita a base).
- **Testes:** `ArticleServiceTest` 8 → 11 (visibilidade, visualização, escape do LIKE), `ArticleTicketLinkServiceTest` 8 (novo); knowledge 26/26; ArchUnit 9 + 21; backend inteiro verde; `ng build` OK.
- **Navegador (Chrome real):** vínculos existentes, desvincular, vincular pela busca, painel no ticket, celular sem overflow, console sem erros. Dado de QA removido (artigo e vínculos).
- **Limites que permanecem (Knowledge):** `ArticleFeedbackService` ainda aceita feedback em artigo não publicado para quem sabe o id (risco baixo); a contagem `/articles/stats/count` inclui rascunhos; sem aprovação/revisão (`REVIEW` existe no enum, sem fluxo); sem página pública (`KNOWLEDGE:READ:PUBLIC` só semeada).

### 5. Reports — CONCLUÍDO (2026-10-02)
- **Backend (`reporting`):** `ReportDataService` agora agrega dados reais por tenant (tickets, SLA, backlog, tendência, categorias, agentes). Corrigido: "em aberto" passou a incluir `ON_HOLD`/`REOPENED` (o `Ticket.isOpen()` do domínio os deixa de fora, então o relatório subcontava); tendência preenche dias sem atividade com zero; agente aparece pelo **nome** (via `UserDirectory`, com o e-mail como reserva). Novo tipo `BACKLOG` (faixas de idade, vencidos, sem responsável, mais antigo). Novo `GET /reports/builtin?type&days` roda uma análise direto, sem relatório salvo (`REPORT:READ`); `CUSTOM` e período fora de 1–365 → 422.
- **Frontend:** novos componentes compartilhados `nx-bar-list` e `nx-trend-chart` (SVG próprio, sem biblioteca, cor nunca é o único sinal, resumo em texto para leitor de tela). `/reports` ganhou o painel analítico: KPIs, tendência criados × resolvidos, situação, prioridade, conformidade de SLA, backlog por idade, categoria e desempenho por agente; cada cartão falha isoladamente (carregando/vazio/erro tratados). Construtor de relatório lista `Backlog por idade`.
- **Bug de layout achado e corrigido:** `/reports` (e qualquer tela com `nx-data-table` + coluna com cabeçalho oculto) estourava para 690px no celular — os `.sr-only` são `position:absolute` e escapavam do recorte do wrapper; `.dt-wrap { position: relative }` resolve em todas as telas. Conferido em /tickets, /assets, /knowledge, /admin/*, /integrations, /sla, /reports/*: largura = viewport.
- **Testes:** `ReportDataServiceTest` reescrito (9); reporting 19/19; backend inteiro verde (`mvnw test -fae`, ArchUnit 9 + 21); `ng build` OK.
- **API real:** as 6 análises → 200 (admin); END_USER → 403; sem token → 401; `days=0` → 422; `CUSTOM` → 422; tipo inexistente → 400.
- **Navegador (Chrome real):** claro e escuro (1440) e celular (390), 7 painéis, console sem erros.
- **Limites que permanecem (Reports):** agregação em memória (ok para o volume atual; precisaria de `GROUP BY` no banco para tenants grandes); só exporta CSV (PDF/Excel existem no enum, sem gerador); agendamentos são cadastrados mas não há executor que os dispare; sem controller de Dashboard/Widget; o `nx-trend-chart` usa fuso UTC para fechar o dia.

### 6. Integrations — CONCLUÍDO (2026-10-02)
- **Decisão (KEEP + IMPROVE, sem acoplar a fornecedor):** a base de configuração (webhooks/conectores genéricos, sem segredo no conector, `WebhookUrlValidator`) estava correta e foi mantida. Entrou o que faltava para o módulo ser demonstrável: saúde, liga/desliga e histórico. Nada de Jira/Slack específico no código: o conector é genérico (`baseUrl` + campos livres sem credencial).
- **Saída HTTP única e segura (`SafeHttpSender`):** https, sem credenciais na URL, DNS resolvido e **todos** os IPs conferidos contra rede interna logo antes de conectar (fecha o item "o código de entrega DEVE revalidar o IP"), sem seguir redirecionamentos, tempo limite ≤ 15 s, corpo da resposta descartado, erros como categorias fixas (`TIMEOUT`, `DNS_FAILURE`, `BLOCKED_ADDRESS`…), nunca texto da exceção. Testado ao vivo: `localtest.me` (nome público → 127.0.0.1) → `BLOCKED_ADDRESS`.
- **Migração `V1_20_0`:** estado de saúde em `webhooks` (última entrega/HTTP/erro) e `connectors` (`enabled`, última verificação), tabela `integration.integration_logs` (índices por tenant/data e por integração).
- **API nova:** `POST /integrations/webhooks/{id}/test` (evento `ping` assinado HMAC-SHA256 sobre `timestamp.corpo`, cabeçalhos `X-NexusOps-*`, cooldown de 5 s por webhook), `POST /integrations/connectors/{id}/check` (só alcance do `baseUrl`, sem credenciais; recusa conector desativado ou sem URL), `GET /integrations/logs` (filtros `kind`/`integrationId`/`outcome`, página ≤ 100), `PATCH` com `enabled` (conector) e `status` ACTIVE/INACTIVE (webhook; `FAILED`/`DISABLED` são do sistema → 422). Overview ganhou falhas/desativados. Permissões: leitura `INTEGRATION:READ`; teste/verificação `INTEGRATION:UPDATE`. Tudo por `tenantId` do token; END_USER → 403; sem token → 401.
- **Histórico:** `IntegrationLog` guarda quem, quando, o quê, resultado, HTTP e duração; **nunca** segredo, corpo ou URL. Sobrevive à exclusão da integração (copia o nome). Testes vivos confirmaram que segredo e URL não aparecem no log.
- **Gravação de saúde sem conflito de versão:** `recordDelivery`/`recordCheck` são `UPDATE` direcionados (a chamada de rede roda fora de transação).
- **Frontend:** dashboard com falhas por cartão + painel **Atividade recente** (filtros, paginação); webhooks com "Último teste", **Enviar teste**, ligar/desligar; conexões com Estado, "Verificação", **Verificar** (só se ativa e com URL base) e ligar/desligar; avisos reescritos (já não dizem que nada funciona, e dizem o que ainda não existe).
- **Testes:** integration 36 → 65 (`SafeHttpSenderTest` 10, `IntegrationLogServiceTest` 3, `WebhookServiceTest` 15, `ConnectorServiceTest` 12); backend inteiro verde (ArchUnit 9 + 21); `ng build` OK.
- **Navegador (Chrome real):** dashboard, webhooks e Jira — enviar teste (falha HTTP 405 mostrada em vermelho com texto), verificar conexão (HTTP 200), ligar/desligar, claro/escuro/celular sem overflow, console sem erros. Dados de QA (integrações e linhas de log) removidos.
- **Limites que permanecem (Integrations):** **não há envio automático dos eventos do sistema** para os webhooks (só o `ping` de teste) nem sincronização Jira/Slack (`SyncJob` sem controller); o segredo do webhook segue em texto puro no banco (necessário para assinar; criptografar em repouso é o próximo passo); sobra uma janela estreita de DNS rebinding entre a conferência do IP e a conexão (o JDK resolve de novo; documentado em `SafeHttpSender`); sem retenção/limpeza do log; `retry_policy`/`headers` existem na entidade sem uso. **(Atualização: envio automático, criptografia do segredo e retenção foram feitos na seção 7 abaixo.)**

### 7. Integrations, rodada 2 — envio automático de eventos (2026-10-02)
- **Migração `V1_21_0`:** `webhooks.secret` → VARCHAR(512) e tabela `integration.webhook_deliveries` (outbox; `UNIQUE(webhook_id, event_id)`; índices por status/próxima tentativa e tenant/data).
- **Segredo cifrado em repouso:** `WebhookSecretConverter` (AES-256-GCM via `EncryptedStringConverter`, prefixo `enc:v1:`); segredo legado em texto puro continua legível e é cifrado na próxima gravação. Conferido no banco (`enc:v1:`), nunca em API/log.
- **Captura dos eventos:** `WebhookEventListener` (`@TransactionalEventListener` AFTER_COMMIT) para `ticket.created`, `status_changed`, `assigned`, `resolved`, `closed`, `comment_added`. O corpo leva só ids e metadados; texto de solução/comentário e notas internas **nunca** saem (comentário não público é ignorado). Falha ao enfileirar não derruba a operação do chamado. Idempotente por webhook+evento (o publisher entrega o mesmo evento duas vezes).
- **Entrega:** `WebhookDeliveryWorker` (a cada 5 s) reserva com `FOR UPDATE SKIP LOCKED` + lease de 2 min; assina (HMAC-SHA256, `X-NexusOps-*`) e envia por `SafeHttpSender`. 2xx → entregue; 4xx (exceto 408/429), URL inválida/endereço bloqueado ou 6 tentativas → desistiu; demais → nova tentativa com espera de 30 s/2 min/10 min/1 h/6 h. Webhook removido/desativado cancela.
- **Retenção:** limpeza diária às 03:17 (entregas concluídas > 30 dias; log de atividade > 90 dias). Pendentes nunca são apagadas.
- **API:** `GET /integrations/events` (catálogo), `GET /integrations/deliveries` (filtros `webhookId`/`status`, sem payload), `POST /integrations/deliveries/{id}/retry` (só `FAILED`; senão 422). Leitura `INTEGRATION:READ`, reenvio `INTEGRATION:UPDATE`; END_USER → 403.
- **Frontend:** tabela **Entregas de eventos** em Webhooks (filtro, tentativas, detalhe traduzido, **Reenviar**, paginação), seletor de eventos no formulário, cartão do dashboard com desistidas/na fila, coluna "Último envio", avisos atualizados.
- **Testes:** integration 65 → 90 (`WebhookDeliveryServiceTest` 13, `WebhookEventListenerTest` 7, `WebhookSecretConverterTest` 5); backend inteiro verde (315, ArchUnit incluso); `ng build` OK.
- **Ao vivo:** um chamado real + uma transição gerou exatamente 6 entregas (3 webhooks × 2 eventos, sem duplicar); 200 → entregue, 405 → desistiu de imediato, 503 → fila com espera; reenviar funciona; no navegador claro/escuro/390px sem overflow e console limpo. Dados de QA (webhooks, entregas, logs) removidos; ficou o chamado "QA entrega automática" no banco de dev.
- **Limites que permanecem:** sem sincronização Jira/Slack (`SyncJob` sem controller); janela estreita de DNS rebinding (documentada em `SafeHttpSender`); o corpo inclui título e e-mails de solicitante/responsável do chamado (sem texto de comentário/solução); `retry_policy`/`headers` da entidade seguem sem uso; várias instâncias dependem de `SKIP LOCKED` + lease.

---

## 000. QA AO VIVO P4+P5+P6 — CONCLUÍDO (2026-10-01)

Backend subido (V1_8_0 + V1_9_0 aplicadas pelo Flyway, Hibernate `validate` OK). **API (curl):** integrações (criar/listar/excluir webhook e conector, SSRF 422, chave de credencial 422), papéis (editar ADMIN → 422, criar custom, permissão inexistente → 422, criar usuário com e-mail >36 chars e papel custom OK, excluir papel em uso → 422), auditoria (mutações e logins com SUCCESS/FAILURE, login falho `BAD_CREDENTIALS`, filtros). **Navegador:** dashboard/webhooks/Jira (criar, cron inválido com mensagem, excluir), papéis (ver papel de sistema só leitura, criar, duplicado com mensagem do backend, excluir), log de auditoria com filtro LOGIN e coluna Resultado. Console: só os 2 avisos pré-existentes (NG0505, matBadge) + o 422 esperado. Dados de QA removidos (linhas de auditoria do próprio QA permanecem — são registros legítimos). **Bug achado no QA:** as telas mostravam mensagem genérica porque o backend devolve o erro em `detail` (ProblemDetail), não `message` → corrigido em 6 componentes (`err?.error?.detail ?? err?.error?.message`). Regressão de testes unitários de todos os módulos verde (iam 14, platform 5, knowledge 15, notification 6, reporting 14, integration 36, asset 14...). O backend continua rodando na 8080 (parar com Stop-Process se a memória apertar).

---

## 00. P5 Roles + P6 Audit (implementados em 2026-10-01; QA ao vivo feito, ver seção 000)

**P5 — Roles (iam):** `RoleController` NÃO tinha `@PreAuthorize`, isolamento por tenant nem proteção de papéis de sistema (qualquer autenticado editava ADMIN/SUPER_ADMIN = escalada de privilégio). Agora: `GET /roles` (sistema + papéis do tenant), CRUD com `ROLE:READ/CREATE/UPDATE/DELETE`; `tenantId` vem do servidor; papéis de sistema imutáveis; nome `^[A-Z][A-Z0-9_]{1,49}$` globalmente único (login resolve papel por nome); permissões devem existir no catálogo E o chamador só concede o que ele mesmo possui (`CallerPermissions.current()`); delete bloqueado se há usuários com o papel (`UserRepository.countByRole`).
**Hardening de usuários (mesmo risco):** `UserService`/`UserController` — tenant sempre do chamador (ignora `tenantId` do request; `GET /users?tenantId=` ignorado), get/update/delete/change-password escopados por tenant (404 fora), roles atribuídas validadas (existem, visíveis ao tenant, ⊆ permissões do chamador); papel padrão corrigido de `ROLE_END_USER` (inexistente → usuário sem permissões) para `END_USER`; `createdBy` agora é o usuário real (era "system"). `CreateUserRequest.tenantId` deixou de ser obrigatório.
**Frontend:** `core/iam/role.service.ts` + `role-management` real (lista, ver papel de sistema, criar/editar com catálogo de permissões por categoria, excluir).
**Testes iam:** RoleServiceTest 9 + UserServiceTest 5 = 14/14.

**P6 — Audit:** a leitura já existia mas NADA gravava. Agora: `AuditInterceptor` (pacote `platform.audit`) grava toda mutação (POST/PUT/PATCH/DELETE) de usuário autenticado — quem, tenant, recurso/id do padrão da rota, status, SUCCESS/FAILURE, IP (peer, sem confiar em X-Forwarded-For), user-agent — NUNCA o corpo. Login: `AuthService` publica `LoginAttemptedEvent` (iam) e `AuditLoginListener` (platform) grava sucesso/falha (conta bloqueada, senha errada, inativa, MFA) só para contas existentes. `AuditRecorder` usa `REQUIRES_NEW` e engole falhas (auditoria não derruba a operação; depende da V1_9_0 para e-mails >36 chars em `user_id`). Leitura corrigida: `/audit-logs/{id}`, `/user/{id}`, `/resource` vazavam entre tenants → escopados; listagem com filtros `action`/`resourceType`/`userId`/`since`, ordenada por data desc, página máx. 200. Frontend: viewer com filtros e coluna Resultado.
**Testes platform:** AuditInterceptorTest 3 + AuditRecorderTest 2 = 5/5. ArchUnit 21/21 (as classes de auditoria ficam em `platform.audit` porque a regra exige sufixo Config em `..config..`). `ng build` OK.
**Limites:** não há auditoria de leituras (GET), nem de estado anterior (`previous_state` vazio), nem partições mensais (tudo cai na partição default); `AuthService` não audita refresh/logout.
**PENDENTE (tudo do P4+P5+P6):** subir o backend (aplica V1_8_0 + V1_9_0; V1_8_0 validada por dry-run com ROLLBACK), testar no navegador integrações, papéis (criar papel custom, tentar editar ADMIN → erro), usuários e auditoria (fazer uma mutação e ver a linha), checar console, remover dados de QA. Não subi o backend por causa do aviso de memória — só a pedido do usuário.

---

## 0. P4 — Integrations (implementado em 2026-10-01, FALTA QA ao vivo)

**Decisão:** adapters/stubs honestos, sem inventar integração externa. O módulo `nexusops-integration` agora só guarda **configuração**; não há chamadas HTTP de saída, nem entrega de webhooks, nem sincronização.
- **pom:** removidas as libs inexistentes (jira-rest-java-client, bolt, botbuilder) e o spring-integration; módulo ativado em `nexusops-bootstrap/pom.xml`.
- **Domínio corrigido:** `SyncJob` tinha `0L.` (erro de sintaxe) e faltava `import UUID`; `@Builder.Default` em todos os campos com default; tokens OAuth removidos de `Connector` (credenciais NUNCA são armazenadas). `Webhook.secret` é write-only (DTO só expõe `hasSecret`).
- **Migração V1_8_0 reescrita** (nunca tinha sido aplicada: schema `integration` vazio, sem entrada no flyway): ids/tenant `VARCHAR(36)`, colunas de usuário `VARCHAR(255)`, sem colunas de token.
- **API (`/integrations`):** `GET /overview`, CRUD de `/webhooks` e `/connectors` (`?type=`). Permissões `INTEGRATION:READ/CREATE/UPDATE/DELETE` (já seedadas). Tudo escopado por `tenantId` do servidor; 404 fora do tenant.
- **Segurança:** `WebhookUrlValidator` (só https, sem userinfo, bloqueia hosts de rede interna: single-label, `.local/.internal/...`, IPs loopback/privados/link-local/ULA, formas decimal/hex). Não protege contra DNS rebinding — o futuro código de entrega DEVE revalidar o IP resolvido. Chaves de configuração de conector com cara de credencial (token/secret/password/api key/authorization) são rejeitadas. Cron validado.
- **Frontend:** `core/integrations/integration.service.ts`; `integration-dashboard` (contadores), `webhook-config` (CRUD), `connector-panel` reutilizável usado por `jira-config` e `slack-config`. Todas as telas avisam que envio/sincronização ainda não existe.
- **Testes:** integration 32/32 (WebhookUrlValidatorTest 21, WebhookServiceTest 5, ConnectorServiceTest 6); ArchUnit `ModuleBoundaryRulesTest` 21/21 (regra de integration sem `allowEmptyShould`); `ng build` OK (único aviso: budget de CSS do article-detail, pré-existente).
- **PENDENTE:** subir o backend (aplica V1_8_0 + V1_9_0), testar as 4 telas no navegador, checar console e remover dados de QA. Não subi o backend porque o ambiente matou o processo por pouca memória — só reiniciar a pedido do usuário. Comandos: `mvnw install -pl nexusops-integration,nexusops-reporting,nexusops-bootstrap -DskipTests` e `spring-boot:run -pl nexusops-bootstrap` (JAVA_HOME=jdk-21, em `backend/`).
- **Limites conhecidos:** sem entrega de webhooks, sem sync/`SyncJob` endpoints, sem Teams/ServiceNow/Zendesk na UI (tipos existem no enum), segredo do webhook em texto puro no banco (necessário para assinar HMAC; criptografar quando a entrega existir).

---

## 0b. P3 — Reports (concluído em 2026-09-30)

**Implementado (backend `nexusops-reporting`, ativado no bootstrap):**
- `ReportController` (`/reports`): create/list/get/update/delete, `GET /overview`, `GET /types`, `GET /{id}/run?days=`, `GET /{id}/export` (CSV com neutralização de formula-injection). `ScheduledReportController` (`/scheduled-reports`): create/list/pause-resume/delete.
- **Sem execução de SQL do usuário**: `ReportDataService` executa 5 analíticas fixas sobre tickets (TICKET_SUMMARY, SLA_COMPLIANCE, AGENT_PERFORMANCE, CATEGORY_DISTRIBUTION, TREND_ANALYSIS); `CUSTOM` retorna 422. A coluna `query` da entidade `Report` existe mas NÃO é exposta nos DTOs (decisão de segurança deliberada). `days` limitado a 1..365. Agregação é em memória Java sobre `findByTenantIdAndCreatedAtBetween` (ok para volume atual; migrar para GROUP BY se crescer).
- Visibilidade: relatório privado só o dono vê; update/delete só o dono (404 para os demais). Agendamentos só aparecem/operam se o relatório-pai for visível ao usuário.
- Permissões reutilizadas (nenhuma nova seedada): `REPORT:READ` (leitura/run), `REPORT:EXPORT` (CSV), `REPORT:CREATE` (create/update/delete/agendar).
- `ScheduledReport`: só definição (cron 6 campos validado via `CronExpression`, timezone validado, `nextRunAt` calculado). **NÃO há executor/envio automático** — UI avisa isso explicitamente. PDF/EXCEL existem só como enum; só CSV é exportado.
- `Dashboard`/`Widget`: entidades corrigidas e migradas, mas **sem controller/UI** (nenhuma rota frontend as usa) — gap conhecido.
- Frontend: `core/reporting/reporting.service.ts` + 3 telas reais (`report-dashboard` com KPIs/lista/executar/exportar/excluir, `report-builder`, `scheduled-reports`).

**Bugs corrigidos:** `@Builder.Default` ausente (4 entidades); colunas `UUID` → `VARCHAR(36)` na V1_7_0; `report_type VARCHAR(20)` estourava com `CATEGORY_DISTRIBUTION` (21 chars) → 30; **`getCurrentUserId()` retorna o e-mail** (não UUID) → `owner_id/created_by/updated_by` alargados para `VARCHAR(255)` via nova migração `V1_7_1` (V1_7_0 já estava aplicada no DB dev — nunca editar migração aplicada).

**Testes:** reporting 14/14 (ReportDataServiceTest 4, ReportServiceTest 5, ScheduledReportServiceTest 5). Regressão asset 14, knowledge 15, notification 6 OK. ArchUnit `ModuleBoundaryRulesTest` 21/21 (regra do reporting sem `allowEmptyShould`). `LayerRulesTest`: as 2 falhas pré-existentes continuam (dtoClasses.. agora com mais violações porque `ReportDto`/`ScheduledReportDto` usam enums de domain e `from()` estático — mesmo padrão já tolerado em ticketing; não corrigido).
**Navegador:** login → criar relatório no builder → listar/KPIs reais → executar → criar agendamento (nextRunAt correto) — tudo OK; console só com os 2 avisos pré-existentes (NG0505, matBadge). Dados de QA removidos.

**Problema sistêmico do e-mail vs `VARCHAR(36)` — CORRIGIDO (2026-09-30), aguardando aplicação:** `getCurrentUserId()` retorna o e-mail e ~48 colunas (`author_id/created_by/updated_by/owner_id/user_id/reporter_id/assignee_id/...` em ticketing, sla, platform, asset, knowledge, notification, reporting) eram `VARCHAR(36)`. Nova migração `nexusops-bootstrap/.../db/migration/V1_9_0__widen_user_reference_columns.sql` alarga todas para 255 via bloco `DO $$` dinâmico (ignora partições/views — `platform.audit_logs_default` herda do pai; exclui `iam`, cujas refs são UUID FK reais). Validada em transação com ROLLBACK no Postgres de dev (48 colunas alargadas, 0 restantes em 36); **será aplicada pelo Flyway na próxima subida do backend**. Decisão: NÃO mudar a identidade do principal (JWT subject = e-mail) porque dados existentes já guardam e-mails e `isCurrentUser(username)` depende disso. As anotações `length = 36` nas entidades ficaram (Hibernate `validate` não checa tamanho).

**Estado dos processos:** backend rodando na 8080 mas com jar ANTERIOR à correção de visibilidade dos agendamentos — reiniciar (`mvnw install -pl nexusops-reporting,nexusops-bootstrap -DskipTests`, depois `spring-boot:run -pl nexusops-bootstrap`). Nada commitado (repo sem commits).

---

## 1. Mandato em vigor (não expira entre sessões)

O usuário autorizou execução autônoma completa do plano P0→P6 de rollout de módulos, sem necessidade de confirmação a cada passo ("não precisa ficar me questionando"). O plano confirmado é:

- **P0 = Notification** — ✅ CONCLUÍDO (sessão anterior)
- **P1 = Assets (CMDB)** — ✅ CONCLUÍDO (sessão anterior)
- **P2 = Knowledge Base** — ✅ CONCLUÍDO (esta sessão)
- **P3 = Reports** — ✅ CONCLUÍDO (2026-09-30, ver seção 0 acima)
- **P4 = Integrations** — 🟡 IMPLEMENTADO como configuração-apenas (adapters stub), faltando QA ao vivo no navegador (ver seção 0)
- **P5 = Roles** — 🟡 IMPLEMENTADO (+ hardening de usuários), faltando QA ao vivo (seção 00)
- **P6 = Audit Events** — 🟡 IMPLEMENTADO (escrita + leitura segura), faltando QA ao vivo (seção 00)

**Regra de execução obrigatória por módulo** (deve ser seguida à risca para P3–P6):
1. Compilar backend
2. Executar testes
3. Executar frontend
4. Testar no navegador
5. Verificar console
6. Revisão de segurança
7. Revisão de integração com outros módulos

Só avançar ao próximo módulo depois de completar os 7 passos do módulo atual e apresentar o relatório de encerramento (implementado / arquivos / testes / problemas encontrados / problemas restantes).

**Prioridades gerais** (ordem confirmada pelo usuário): Correção > Segurança > Arquitetura > Manutenibilidade > Testabilidade > UX > Performance > Simplicidade > Consistência visual — exceto que a identidade visual já aprovada é um requisito de produto e não pode ser degradada por simplicidade de implementação.

Correções arquiteturais no código legado são bem-vindas quando tecnicamente justificadas — não é necessário preservar decisões antigas só porque já existem.

---

## 2. O que foi concluído nesta sessão (P2 — Knowledge Base)

Implementação completa e real (não-stub) do módulo Knowledge Base, seguindo os 7 passos de execução:

- Backend completo: domain → repository → mapper (MapStruct) → service → controller para 4 entidades: `Article`, `KnowledgeCategory`, `Tag`, `ArticleFeedback`.
- Fluxo de ciclo de vida real de artigo: `DRAFT → PUBLISHED → ARCHIVED` via endpoints `/articles/{id}/publish` e `/articles/{id}/archive`.
- Geração automática de slug único por tenant com resolução de colisão (`titulo`, `titulo-2`, ...).
- Sincronização real de `articleCount` em `KnowledgeCategory` ao criar/mover/excluir artigos.
- Contagem de visualizações incrementada a cada leitura do artigo (`GET /articles/{id}`).
- Feedback de utilidade (`helpfulCount`/`notHelpfulCount`/`helpfulPercentage`) via `POST /articles/{id}/feedback`.
- RBAC via `hasPermission('KNOWLEDGE', ação)`, usando permissões já seedadas no IAM (`KNOWLEDGE:CREATE/READ/UPDATE/DELETE:TENANT`).
- `tenantId` sempre derivado de `SecurityUtils.getCurrentTenantId()` no servidor — nunca aceito do payload do cliente (mesmo padrão de segurança do P1).
- Frontend: as 3 telas já roteadas em `knowledge.routes.ts` (`article-list`, `article-create`, `article-detail`) foram reescritas de stubs "Em desenvolvimento" para telas reais, seguindo fielmente o padrão visual já aprovado (mesmas classes/tokens CSS dos módulos Tickets/Assets).

---

## 3. Arquivos principais alterados nesta sessão

**Backend — domain (corrigidos)**
- `backend/nexusops-knowledge/src/main/java/com/nexusops/knowledge/domain/Article.java`
- `backend/nexusops-knowledge/src/main/java/com/nexusops/knowledge/domain/KnowledgeCategory.java` *(renomeado de `Category.java` — ver seção 4)*
- `backend/nexusops-knowledge/src/main/java/com/nexusops/knowledge/domain/Tag.java`
- `backend/nexusops-knowledge/src/main/java/com/nexusops/knowledge/domain/ArticleFeedback.java` *(lida, sem alteração necessária)*

**Backend — migração (corrigida)**
- `backend/nexusops-knowledge/src/main/resources/db/migration/V1_5_0__knowledge_initial_schema.sql`

**Backend — novos (repository/dto/mapper/service/controller)** — 23 arquivos novos em:
- `backend/nexusops-knowledge/src/main/java/com/nexusops/knowledge/repository/` (`ArticleRepository`, `KnowledgeCategoryRepository`, `TagRepository`, `ArticleFeedbackRepository`)
- `backend/nexusops-knowledge/src/main/java/com/nexusops/knowledge/dto/` (10 DTOs)
- `backend/nexusops-knowledge/src/main/java/com/nexusops/knowledge/mapper/` (`ArticleMapper`, `KnowledgeCategoryMapper`, `TagMapper`, `ArticleFeedbackMapper`)
- `backend/nexusops-knowledge/src/main/java/com/nexusops/knowledge/service/` (`ArticleService`, `KnowledgeCategoryService`, `TagService`, `ArticleFeedbackService`)
- `backend/nexusops-knowledge/src/main/java/com/nexusops/knowledge/controller/` (`ArticleController`, `KnowledgeCategoryController`, `TagController`, `ArticleFeedbackController`)

**Backend — wiring**
- `backend/nexusops-bootstrap/pom.xml` — dependência `nexusops-knowledge` ativada (movida do bloco comentado)
- `backend/nexusops-bootstrap/src/test/java/com/nexusops/architecture/ModuleBoundaryRulesTest.java` — regra `knowledgeModuleShouldOnlyDependOnAllowedModules` não usa mais `allowEmptyShould(true)`

**Backend — testes novos**
- `backend/nexusops-knowledge/src/test/java/com/nexusops/knowledge/service/ArticleServiceTest.java` (8 testes)
- `backend/nexusops-knowledge/src/test/java/com/nexusops/knowledge/service/KnowledgeCategoryServiceTest.java` (5 testes)
- `backend/nexusops-knowledge/src/test/java/com/nexusops/knowledge/service/TagServiceTest.java` (2 testes)

**Frontend — novo**
- `frontend/src/app/core/knowledge/knowledge.service.ts`

**Frontend — reescritos (de stub para real)**
- `frontend/src/app/features/knowledge/article-list/article-list.component.ts`
- `frontend/src/app/features/knowledge/article-create/article-create.component.ts`
- `frontend/src/app/features/knowledge/article-detail/article-detail.component.ts`

---

## 4. Problemas encontrados e corrigidos nesta sessão

1. **Bug sistêmico `UUID` vs `VARCHAR(36)`** (mesmo padrão já visto em P0/P1): a migração `V1_5_0` usava colunas `UUID` nativas para `id`/FKs, mas a convenção do projeto é `@Id private String id` mapeado para `VARCHAR(36)`. Corrigido em todas as 6 tabelas do schema `knowledge`.

2. **`@Builder.Default` ausente** (mesmo padrão sistêmico): `Article.status/version/viewCount/helpfulCount/notHelpfulCount/featured/allowComments`, `Category.sortOrder/active/articleCount/version`, `Tag.usageCount` tinham inicializadores de campo descartados silenciosamente pelo builder do Lombok. Corrigido com `@Builder.Default` em todos.

3. **Bug real de conflito de bean/entidade JPA (descoberto ao subir a aplicação)**: `com.nexusops.knowledge.domain.Category` tinha o **mesmo nome de classe** que `com.nexusops.ticketing.domain.Category`, causando `ConflictingBeanDefinitionException` no Spring (bean `categoryController` duplicado) e conflito de nome de entidade JPA. A aplicação **não subia**. Corrigido renomeando toda a stack do knowledge (`Category` → `KnowledgeCategory`): entidade, repository, mapper, service, controller. `ArticleService` foi atualizado para referenciar `KnowledgeCategory`/`KnowledgeCategoryRepository`.

4. **Bug real: `Tag` sem geração de UUID** (descoberto ao testar via curl): diferente de `Article`/`Category`/`ArticleFeedback`, a entidade `Tag` não tinha `@PrePersist` gerando o `id`, causando `IdentifierGenerationException` (HTTP 500) em toda criação de tag. Corrigido adicionando `@PrePersist onCreate()`.

5. **Correção arquitetural deliberada (com justificativa, autorizada pelo usuário)**: a restrição de unicidade de `slug` (em `articles` e `categories`) e `name` (em `tags`) era **global no banco** (`UNIQUE` de coluna única), não escopada por tenant — um bug real de isolamento multi-tenant herdado do design original das entidades (impedia dois tenants diferentes de usarem o mesmo slug/nome). Corrigido para `UNIQUE (tenant_id, slug)` / `UNIQUE (tenant_id, name)`, tanto na migração SQL quanto nas anotações `@UniqueConstraint` das entidades.

6. **Necessário: Flyway `out-of-order: true`** — não é um bug novo desta sessão, mas essa config (adicionada em P1) foi o que permitiu a migração `V1_5_0` (knowledge) aplicar corretamente mesmo tendo sido habilitada depois de módulos com número de versão maior. Confirmado funcionando.

---

## 5. Testes executados e resultados

**Unitários (Mockito + AssertJ), módulo knowledge:**
- `ArticleServiceTest`: 8/8 ✅ (geração/colisão de slug, sincronização de contador de categoria, publish, view tracking, delete)
- `KnowledgeCategoryServiceTest`: 5/5 ✅ (validação de parent, auto-parent, delete bloqueado com artigos/filhos)
- `TagServiceTest`: 2/2 ✅ (dedupe de nome por tenant)
- **Total knowledge: 15/15 passando**

**Regressão (confirmado sem quebra após mudanças do P2):**
- `nexusops-asset`: 14/14 ✅ (inalterado)
- `nexusops-notification`: 6/6 ✅ (inalterado)

**ArchUnit (arquitetura), rodado via `nexusops-bootstrap` (contexto completo do reactor):**
- `ModuleBoundaryRulesTest`: **21/21 passando**, incluindo a nova regra estrita `knowledgeModuleShouldOnlyDependOnAllowedModules` (antes usava `allowEmptyShould`, agora valida de verdade)
- `LayerRulesTest`: 28/30 — **2 falhas pré-existentes, não relacionadas ao knowledge** (já documentadas em memória de sessões anteriores): `dtoClassesShouldNotContainBusinessLogic` (DTOs de ticketing/platform acessando enums de domain) e `springAnnotationsOnlyInAppropriateLayers` (`UserDetailsServiceImpl` fora do pacote `service`). **Não foram introduzidas nesta sessão** — não tentar corrigir sem instrução explícita do usuário, pois estão fora do escopo do módulo knowledge.

**Nota sobre `mvn test` isolado por módulo:** rodar `mvn -pl nexusops-shared-kernel test` sozinho (fora do reactor completo) produz falsas falhas de ArchUnit ("failed to check any classes") porque o classpath do módulo isolado não inclui as classes de outros módulos que as regras de arquitetura esperam escanear. **Sempre rodar as regras ArchUnit via `nexusops-bootstrap` (que agrega todo o reactor) para um resultado confiável.**

**Verificação via curl (backend):** create/list/get/update/delete testados para Article, KnowledgeCategory, Tag; publish/archive; feedback; validação (422), 401 sem token, 404 cross-tenant, bloqueio de self-parent, bloqueio de delete-com-filhos/artigos.

---

## 6. O que foi validado no navegador (Chrome DevTools MCP)

Fluxo completo testado ao vivo em `http://localhost:4200`:
1. Grid `/knowledge` — filtros de status, busca, paginação, dados reais renderizados corretamente (nome da categoria resolvido via join client-side)
2. Detalhe `/knowledge/:id` — visualização de conteúdo, contador de views incrementando a cada carga, meta (categoria/views/útil%/datas)
3. Edição inline — toggle para formulário de edição, salvar título/resumo/conteúdo/categoria, timestamp "Atualizado em" mudou corretamente
4. Feedback de utilidade — clique em "Sim", troca para estado "Obrigado pelo seu feedback!"
5. Criação `/knowledge/new` — formulário completo (título, resumo, conteúdo, categoria carregada da API, tags), submissão e navegação para o detalhe do novo artigo criado (status DRAFT)
6. Ação "Publicar" — status mudou de "Rascunho" para "Publicado" ao vivo, botão trocou para "Arquivar"

**Console verificado:** apenas os 2 avisos pré-existentes e não relacionados já vistos em P0/P1 (`NG0505` hydration warning; `matBadge`/`aria-hidden` no ícone de notificação). **Nenhum erro novo introduzido pelo trabalho do P2.**

**Dados de teste criados durante a QA foram removidos via curl DELETE ao final** (2 artigos, 2 categorias, 1 tag) — o banco de dev está limpo, sem lixo de teste do knowledge.

---

## 7. O que ainda está pendente

- **P3 (Reports), P4 (Integrations), P5 (Roles), P6 (Audit Events)** — não iniciados. Próximo passo é P3.
- Nenhuma tarefa do P2 ficou incompleta — o módulo foi fechado com relatório de encerramento apresentado ao usuário.

---

## 8. Próximo passo EXATO a executar

Iniciar **P3 — Reports**, seguindo a mesma metodologia usada em P0/P1/P2:

1. Explorar `backend/nexusops-reporting/` (entidades, migração, se já existem stubs) e `frontend/src/app/features/reports/` (rotas e componentes existentes) — **não assumir nada, ler o código primeiro**.
2. Auditar os mesmos bugs sistêmicos conhecidos antes de escrever qualquer linha nova:
   - Prefixo duplicado de rota (`@RequestMapping` não deve repetir `/api/v1`)
   - Colunas `jsonb` sem `@JdbcTypeCode(SqlTypes.JSON)`
   - Campos com inicializador (`= valor`) em entidades `@Builder`/`@SuperBuilder` sem `@Builder.Default`
   - Colunas `UUID` nativas em vez de `VARCHAR(36)`
   - **Adicionar a esta lista, aprendido nesta sessão**: checar se algum nome de classe de domínio do reporting colide com nome já usado em outro módulo (ex.: `Category`, `Report` genérico demais) — renomear preventivamente se houver colisão, ANTES de tentar subir a aplicação.
   - **E**: checar se toda entidade `@Entity` tem `@PrePersist` gerando UUID quando `id` é `String` — não assumir que existe só porque outras entidades do mesmo módulo têm.
   - **E**: checar se qualquer constraint `UNIQUE` de negócio (slug, nome, código) está escopada por tenant, não global.
3. `nexusops-bootstrap/pom.xml` tem a dependência do reporting **comentada** (bloco junto com integration) — precisa ser ativada isoladamente (não ativar integration junto, ver seção 11).
4. Seguir os 7 passos de execução por módulo (compilar → testar → frontend → navegador → console → segurança → integração) antes de apresentar o relatório e avançar ao P4.
5. **Estado dos processos ao final desta sessão (verificado via `netstat`/`docker ps` antes de encerrar):** o **backend NÃO está rodando** (porta 8080 sem listener) — o processo `mvnw.cmd spring-boot:run` que estava servindo as requisições durante o desenvolvimento e QA do P2 foi finalizado pelo próprio ambiente de execução por baixa memória do sistema enquanto a sessão ficava ociosa entre turnos (não foi um crash da aplicação nem um bug introduzido). O **frontend continua rodando** (`ng serve` na porta 4200, com hot-reload). Os containers Docker (`nexusops-postgres`, `nexusops-redis`, `nexusops-mailhog`) continuam rodando normalmente. **Antes de iniciar P3, subir o backend novamente**: `cd backend`, setar `JAVA_HOME`/`PATH` para o JDK 21 (ver seção 12), depois `.\mvnw.cmd -pl nexusops-bootstrap spring-boot:run` (rodar em background, sem `-am`). O Flyway vai reaplicar o estado do schema normalmente (idempotente).

---

## 9. Decisões arquiteturais tomadas (cumulativas, P0→P2)

- **`tenantId` derivado server-side, não aceito do payload do cliente** em todos os `Create*Request` novos (P1 Asset, P2 Knowledge) — desvio deliberado do padrão mais antigo usado por `CreateTicketRequest`/`CreateCategoryRequest` (ticketing), que ainda confiam em `tenantId` vindo do cliente. Esse padrão mais antigo **não foi retrofitado** — só aplicado a código novo, por instrução do usuário.
- **Unicidade de slug/nome deve ser sempre por tenant, nunca global** (corrigido no knowledge nesta sessão; **verificar se este mesmo padrão de bug existe em outros módulos ainda não auditados**, especialmente reporting).
- **`flyway.out-of-order: true`** é necessário porque módulos são habilitados em ordem de prioridade/dependência, não em ordem numérica de versão pré-reservada. Já configurado em `application.yml`, vale para todos os módulos futuros.
- **Descoberta automatizada (Asset/DiscoveryJob) ficou como CRUD apenas**, sem motor de execução real — não existe infraestrutura de scanning de rede no projeto, e inventar uma seria "inventar requisito" fora do escopo pedido.
- **`KNOWLEDGE:READ:PUBLIC` está seedado no IAM mas sem rota pública/anônima implementada** — decisão de escopo deliberada; expor artigos sem autenticação é uma decisão de produto maior, não solicitada.
- **Reutilização de permissões RBAC existentes em vez de inventar novas**: tanto Asset (`ASSET:*`) quanto Knowledge (`KNOWLEDGE:*`) usam apenas os `resource` já seedados no IAM — nenhum recurso novo de permissão foi criado.

---

## 10. Decisões de design/UI aprovadas (cumulativas)

- Identidade visual aprovada (referenciada em memória do projeto como "nexusops_visual_direction_approved") deve ser seguida com fidelidade em toda tela nova — mesmos tokens CSS (`--accent`, `--surface-2`, `--critical-soft` etc.), mesmas classes (`.page-header`, `.filter-chip`, `.table-card`, `.card-title`, `.meta-grid`, `.btn-primary`/`.btn-secondary`, `.status-tag`), mesmo padrão de busca com debounce de 350ms, mesma paginação, mesmos estados de loading/error/empty.
- Componentes standalone Angular com `signal()` para estado, sintaxe de controle de fluxo `@if`/`@for` (não `*ngIf`/`*ngFor`).
- Telas de detalhe usam grid de 2 colunas (`2fr 1fr`) com conteúdo principal à esquerda e ações/metadados à direita, responsivo (`1fr` abaixo de 900px).

---

## 11. Problemas conhecidos que NÃO devem ser considerados resolvidos

- **`nexusops-integration` (P4) não compila** — depende de bibliotecas externas fictícias/inexistentes no Maven Central: `com.atlassian.jira:jira-rest-java-client-api:5.2.0`, `com.atlassian.jira:jira-rest-java-client-core:5.2.0`, `com.microsoft.teams:botbuilder:4.20.0`. Isso **bloqueia `mvn test` na raiz do reactor completo** (sem `-pl`) — sempre rodar testes com `-pl` explícito nos módulos ativos, ou aceitar que a raiz falha por causa do integration. Este problema é anterior a esta sessão e não foi introduzido nem corrigido aqui. Precisará de decisão de produto (trocar por dependências reais ou stub) quando P4 começar.
- **2 falhas pré-existentes do `LayerRulesTest`** (ver seção 5) — não corrigidas, fora do escopo do knowledge, presentes desde antes desta sessão.
- **`nexusops-reporting` (P3) e `nexusops-integration` (P4) estão comentados juntos no mesmo bloco** de `nexusops-bootstrap/pom.xml` — ao iniciar P3, ativar **somente** a dependência do reporting, mantendo integration comentado.
- **Role Management (P5) tem gap de funcionalidade conhecido**: `RoleController` não tem endpoint de listagem de todas as roles — não é um bug, é funcionalidade ausente que P5 precisará implementar.
- **Auditoria de logs (P6)**: o backend não grava entradas de auditoria em ações como login/toggle de feature flag — gap real, mas é trabalho net-new do P6, não um bug a corrigir agora.
- **`AuthService.isAuthenticated()` no frontend**: documentado em sessão anterior como possivelmente checando apenas presença no localStorage — **verificar se essa correção (checar `exp` do JWT) ainda está em vigor** antes de assumir que está resolvido; não foi tocado nesta sessão.

---

## 12. Contexto importante para a próxima sessão

**Ambiente de execução (Windows):**
- Backend: Java 21 obrigatório para build (Maven enforcer bloqueia JDK 8). `JAVA_HOME` do sistema aponta para `C:\Program Files\Zulu\zulu-8\jre` (Java 8) por padrão — **sempre setar `$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"` e prepend ao `$env:PATH`** antes de rodar `mvnw.cmd`.
- Maven wrapper disponível em `backend/mvnw.cmd` (Windows) — não há `mvn` no PATH global.
- PowerShell é necessário para comandos com `-D` (Bash/Git Bash tem problemas de parsing com flags `-Dtest=X -Dsurefire.foo=bar`; sempre citar cada `-D` entre aspas duplas separadamente).
- Backend sobe com `mvnw.cmd -pl nexusops-bootstrap spring-boot:run` — **rodar `mvnw.cmd install -pl nexusops-bootstrap -am -DskipTests` primeiro** sempre que qualquer módulo dependency tiver mudado, para atualizar o repositório local Maven, senão o `spring-boot:run` usa jars desatualizados de módulos-dependência.
- Rodar `spring-boot:run` com `-am` (a partir da raiz do reactor) falha com "Unable to find a suitable main class" porque tenta rodar o `nexusops-parent` também — **sempre usar `-pl nexusops-bootstrap` sozinho** (sem `-am`) para o `spring-boot:run`.
- Docker (Postgres/Redis/Mailhog) já está rodando persistentemente neste ambiente (`nexusops-postgres`, `nexusops-redis`, `nexusops-mailhog`) — não precisa subir.
- Frontend: `ng serve` já roda persistentemente na porta 4200 com hot-reload — mudanças em componentes Angular refletem automaticamente, não precisa restart.
- **Backend em 8080 NÃO está rodando neste momento** (confirmado via `netstat` ao final desta sessão) — precisa ser iniciado manualmente antes de começar P3. Precisa também de restart manual toda vez que houver mudança em entidade/migração/controller (não tem hot-reload).
- **Lição operacional desta sessão**: rodar `spring-boot:run` via `run_in_background` mantém o processo vivo e servindo normalmente enquanto a sessão está ativa, mas o ambiente de execução pode encerrá-lo automaticamente se a sessão ficar ociosa e o sistema estiver com pouca memória (isso aconteceu nesta sessão, sem relação com bug de código). Se isso acontecer de novo, apenas reiniciar o processo — não é preciso investigar como se fosse uma falha da aplicação.
- Login de dev: `username: admin@nexusops.com`, `password: admin123456` (nota: o campo do JSON de login é **`username`**, não `email`, apesar do valor ser um email) — retorna `accessToken` com expiração de 900s (15 min).
- Tenant de dev: `00000000-0000-0000-0000-000000000000` (super-admin, todas as permissões).
- Repositório git **não tem nenhum commit ainda** — todo o conteúdo aparece como untracked (`??`) no `git status`. Nenhuma alteração desta sessão foi commitada.

**Convenções de código confirmadas (aplicar em P3+):**
- Toda entidade nova: `@Id private String id` (não UUID nativo), `@PrePersist` gerando `UUID.randomUUID().toString()` se `id == null`.
- Todo campo com inicializador em entidade `@Builder`: adicionar `@Builder.Default`, incluindo campos `@Version`.
- Todo `Create*Request` novo: **sem** campo `tenantId` — sempre derivado via `SecurityUtils.getCurrentTenantId()` no controller.
- Toda constraint `UNIQUE` de negócio (slug, nome, código): sempre `UNIQUE (tenant_id, campo)`, nunca `UNIQUE (campo)` sozinho, a menos que seja genuinamente global por design (ex.: e-mail de usuário).
- Nome de classe de domínio novo: **grep primeiro** por colisão de nome em outros módulos antes de criar (`grep -rn "class NomeDaClasse" backend/`), especialmente nomes genéricos como `Category`, `Item`, `Status`, `Config`.
- Repositórios: sempre `findByIdAndTenantId`, nunca `findById` sozinho, para qualquer lookup que retorne dado potencialmente de outro tenant.
- Rotas de controller: **sem** prefixo `/api/v1` (já vem do `context-path` global) — bare paths como `/articles`, `/assets`.

**Onde encontrar mais contexto:**
- Memória persistente da ferramenta de desenvolvimento (fora do repo) tem 4 registros relevantes: `nexusops_visual_direction_approved`, `nexusops_implementation_priorities`, `nexusops_systemic_backend_bugs`, `nexusops_session_completion_state` — consultar antes de assumir qualquer estado do projeto.
- `docs/adr/ADR-012-phased-implementation.md` — roadmap original de fases (P0-P6 mapeia a esse ADR).
- `docs/audit/*.md` — auditorias de arquitetura/backend/banco/frontend feitas antes desta leva de sessões P0-P6; a base do porquê certas decisões legadas foram sinalizadas como problemáticas.
