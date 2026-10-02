# ADR-013: Fluxo completo de atendimento (Portal, catálogo, filas, ciclos)

Status: aceito (revisão final, após aprovação do dono do produto)
Substitui: nada. Complementa ADR-001 (estilo) e ADR-003 (banco).

## Regra de trabalho

Domínio, estados, permissões, histórico e configuração primeiro; telas depois, representando o domínio.
O Portal do Solicitante é uma experiência própria para outro público, não uma versão reduzida do console.

## Achados da auditoria do código existente (Fase 0)

| Achado | Consequência |
|---|---|
| `WorkflowService.validateTransition` é um stub que sempre retorna `true` e ninguém o usa | Será substituído por uma máquina de estados real e pura (`TicketLifecycle`). |
| As transições vivem em `TicketService.isValidTransition` e qualquer usuário com permissão pode fechar | `FECHADO` passa a exigir o solicitante (reporter). |
| Ao resolver, o texto é concatenado em `tickets.description` e `resolved_at` é sobrescrito | Viola o histórico. A solução vira registro próprio e imutável por ciclo. |
| `CLOSED -> REOPENED` existe na máquina atual | Removido na v1: só se contesta enquanto `RESOLVED` (aguardando validação). |
| `TicketService.deleteTicket` apaga fisicamente | Incompatível com histórico append-only: ticket com histórico não pode ser apagado. |
| `tickets.group_id` existe, mas não há conceito de fila | Nova coluna `queue_id`; `group_id` fica legado. |
| Papéis existentes: SUPER_ADMIN, ADMIN, MANAGER, TEAM_LEAD, AGENT, END_USER | Mapeamento: END_USER = solicitante, AGENT = analista, TEAM_LEAD = líder, sem criar papéis paralelos. |
| Status existentes: OPEN, IN_PROGRESS, WAITING, ON_HOLD, RESOLVED, CLOSED, REOPENED | Mantidos. `WAITING` = aguardando o solicitante (gera "precisa da sua atenção"); `ON_HOLD` = em espera interna. |
| Migrações Flyway com `out-of-order: true` | Novas migrações usam versões `V1_10_x` em diante. Nenhuma migração aplicada é editada. |

## Decisões de negócio fechadas

1. Fluxo: `ABERTO -> EM ANDAMENTO -> RESOLVIDO -> FECHADO` e `RESOLVIDO -> REABERTO -> EM ANDAMENTO`.
   - RESOLVIDO: técnico informou a solução e as evidências exigidas.
   - FECHADO: solicitante aceitou. Somente ele.
2. Aceite automático: não existe na v1. O modelo reserva `acceptance_policy.auto_accept` (desligado, sem prazo) e o `TicketLifecycle` recusa aceite por ator SISTEMA. Nenhum job de auto-fechamento.
3. Reabertura: apenas enquanto RESOLVIDO. Comentário obrigatório, evidência opcional, motivo registrado, evento na timeline, chamado volta para a fila operacional (responsável limpo). A solução anterior permanece.
4. SLA por ciclos (`ticket_cycles`). Cada ciclo guarda suas próprias marcas; nada é sobrescrito. Métricas futuras derivadas: tempo até a primeira resolução, tempo de validação do solicitante, quantidade de reaberturas, tempo do segundo atendimento, tempo total.
5. Portal: o solicitante não escolhe fila. Escolhe o que precisa (`Rede e Telecom -> Falha de conexão`); o sistema define fila, prioridade inicial, SLA e responsável (quando houver regra).
   - Reconciliação com a decisão anterior: o super admin cria filas e tópicos, e cada tópico tem uma fila padrão interna. O que o portal agrupa é a **área do catálogo** (`catalog_areas`, rótulo de exibição), não a fila.
6. "Precisa da sua atenção": conceito próprio (`ticket_pending_actions`: VALIDAR_SOLUCAO, RESPONDER_PERGUNTA, INFORMACAO_ADICIONAL, SOLICITAR_EVIDENCIA), distinto de "Meus chamados".
7. Evidências em três momentos (abertura, atendimento, resolução), sempre associadas ao evento, comentário ou etapa de origem.
8. Timeline append-only, imposta no banco (trigger que bloqueia UPDATE e DELETE).
9. Catálogo configurável e versionado: campos, obrigatoriedade, padrões, pré-preenchimento, evidência, fila padrão, SLA, prioridade, roteamento, necessidade de aceite, regras específicas.
10. Localidade é cidadã de primeira classe: `locations`, localidade padrão no perfil do usuário, `location_id` no ticket, condição de primeira classe no roteamento.

## Ordem de implementação (domínio antes de interface)

| Fase | Entrega |
|---|---|
| A | Ciclos, timeline imutável, resoluções imutáveis, máquina de estados real, aceite apenas pelo solicitante |
| B | IAM: perfil do usuário e localidades; super admin cria admins e super admins |
| C | Catálogo: filas, membros, áreas, tópicos; responsável independente da fila |
| D | Formulário dinâmico versionado e validação no servidor |
| E | Roteamento configurável com simulador |
| F | Evidências associadas a evento, comentário ou etapa |
| G | Portal do Solicitante |
| H | Console do analista (Operations Console, faixa P1) e Administração |

## Fase B — IAM, perfil e localidades (concluída)

Decisões:

- **Localidade reaproveita `asset.locations`** (já hierárquica e por tenant) em vez de criar outra tabela: ganhou `code` (estável, único por tenant sem diferenciar caixa, usado por regras de roteamento) e `active` (aposentar sem apagar). Quem só referencia a localidade pelo ID (perfil, chamado, roteamento) valida pela porta `shared.directory.LocationDirectory`, implementada em `asset`; o IAM não depende do módulo asset.
- **Perfil do usuário** (`iam.users`): `phone`, `job_title`, `department`, `default_location_id`. `GET/PATCH /users/me` exigem apenas autenticação (o solicitante não tem permissão `USER`) e nunca alteram papéis ou status. Localidade padrão: nulo mantém, vazio remove, senão precisa existir e estar ativa no tenant.
- **Contas administrativas**: só SUPER_ADMIN cria, altera ou remove ADMIN e SUPER_ADMIN (403 para os demais, checado antes do teto de permissões). Ninguém altera os próprios papéis, se desativa ou se apaga. O tenant mantém ao menos um SUPER_ADMIN ativo.
- **ADMIN completo**: a semente dava ao ADMIN só escopos GLOBAL/TENANT, então ele não podia conceder AGENT (que carrega permissões OWN/TEAM). A migration V1_11_2 dá ao ADMIN tudo exceto `TENANT:*`.
- `PATCH /users/{id}` passou a ignorar campos nulos (antes apagava nome e e-mail). `GET /locations/{id}/children` passou a filtrar por tenant (antes listava filhos de qualquer tenant).

Lacuna conhecida: apagar uma localidade não verifica quem a referencia (usuários, chamados, regras). Até existir essa checagem, a interface deve oferecer "aposentar" (`active=false`) em vez de excluir.

Migrations: V1_11_0 (perfil), V1_11_1 (código e ativo da localidade), V1_11_2 (papel ADMIN).

Identidade visual preservada: monograma A, Operations Pulse como faixa de leituras (P1), Light Theme padrão, Signal Blue, Nexus Violet, IBM Plex Sans e Mono.

## SLA aplicado de verdade (correção pós-Fase B)

- O SLA é resolvido na abertura do chamado (`TicketSlaService`): definição explícita do tópico, senão da categoria, senão a mais específica entre as ativas do tenant (mais critérios preenchidos vence; empate = revisão mais nova). Os prazos respeitam o calendário comercial.
- É por ciclo: reabrir (contestar) recomeça com prazos novos; o ciclo anterior mantém os dele.
- O relógio pausa em espera e aguardando o solicitante (quando a definição pede) e, ao retomar, estende os prazos pelo tempo pausado (`tickets.sla_paused_at`, V1_12_0).
- `SlaWatchService` roda a cada minuto (exige `@EnableScheduling`): aplica SLA a chamados sem ele, avisa uma vez por ciclo aos 80% e registra a violação uma única vez (idempotente) com evento interno `SLA_WARNED`/`SLA_BREACHED`.
- Definições são do tenant do chamador e editáveis (PATCH: nulo mantém, texto vazio limpa o critério). `version` é a revisão e sobe só em mudança material. Editar não reescreve prazos de ciclos em andamento.

## Fase C: catálogo, filas e membros

- Tabelas `queues`, `queue_members` (MEMBER | LEAD), `catalog_areas`, `catalog_topics` (V1_13_0). Fila = equipe; responsável individual continua independente (mover de fila não troca o responsável; ser membro não é pré-requisito para assumir).
- O tópico carrega os padrões internos: fila, prioridade inicial, SLA e categoria. Ao abrir um chamado com `topicId`, o sistema deriva fila, prioridade e SLA (a prioridade do pedido é ignorada) e registra `ROUTED` (interno). `POST /tickets/{id}/queue` move de fila e registra `QUEUE_CHANGED` (interno).
- `GET /catalog/portal` devolve só áreas e tópicos ativos: sem fila, prioridade ou SLA. A timeline só devolve eventos internos para a equipe (SUPER_ADMIN, ADMIN, MANAGER, TEAM_LEAD, AGENT).
- Fila em uso (chamados ou tópicos) não é excluída nem desativada com tópico ativo; usar desativar. Membros resolvidos por `UserDirectory` (porta em shared-kernel, implementada pelo iam).
- Administração: `/admin/queues` e `/admin/catalog`.

## Fase D: formulários dinâmicos versionados

- `topic_form_versions` (V1_14_0): cada tópico tem versões do formulário (DRAFT → PUBLISHED → ARCHIVED). Só a publicada vale para chamados novos; publicar arquiva a anterior. O chamado guarda `form_version_id` e `form_answers`, então chamados antigos continuam legíveis mesmo após uma nova versão.
- Tipos de campo: TEXT, TEXTAREA, NUMBER, DATE, BOOLEAN, SELECT, MULTISELECT, LOCATION. Campos condicionais (`visibleWhen`), pré-preenchimento do perfil e política de evidência (NONE | OPTIONAL | REQUIRED) fazem parte da definição.
- O servidor é a autoridade: `FormValidationService` valida respostas contra a versão publicada (campo oculto não é exigido nem aceito) e devolve erros por campo (422, `extensions`). Respostas sem formulário publicado, ou sem tópico, são recusadas.
- Administração: construtor de formulário em `/admin/catalog`; o Portal renderiza o mesmo componente (`nx-dynamic-form`).

## Fase E: roteamento configurável e simulador

- `routing_rules` (V1_15_0): regras do tenant com ordem (`position`), ativas/inativas, `conditions` e `actions` em jsonb.
- Condições: TOPIC, AREA, LOCATION, PRIORITY e `ANSWER:chave` (resposta do formulário). Todas as condições precisam casar; dentro de uma, basta um dos valores. Regra sem condições casa sempre (regra padrão ao fim da lista).
- Ações: fila, prioridade e responsável (NONE, USER, LEAST_LOADED = membro ativo da fila com menos chamados abertos). Pelo menos uma ação é obrigatória; LEAST_LOADED exige a fila da regra. Fila e responsável continuam independentes.
- Ordem de decisão: padrões do tópico, depois a primeira regra ativa que casar sobrepõe o que define. Responsável informado manualmente na criação não é sobrescrito. Atribuição por regra tem ator `system` na timeline; `ROUTED` registra `ruleId` e `ruleName` (interno).
- `RoutingEngineService` é puro (testável sem banco); `RoutingService` valida, grava e decide. `POST /routing/simulate` usa o mesmo caminho do chamado real, devolve o rastro de cada regra e não grava nada.
- Só ADMIN e SUPER_ADMIN configuram (`/routing/*`). Administração: `/admin/routing`.

## Fase F: evidência ligada ao histórico

- Evidência não fica solta no chamado: pertence ao chamado inteiro (`TICKET`), a um comentário (`COMMENT`) ou a um evento da timeline (`EVENT`, que cobre as etapas: resolução, pedido de informação, retomada). `attachments.subject_type/subject_id/internal` (V1_16_0). Anexar e remover geram `EVIDENCE_ADDED`/`EVIDENCE_REMOVED` na timeline append-only; remover apaga o arquivo mas o histórico continua dizendo que existiu.
- Visibilidade: evidência de nota interna ou de evento interno nasce interna; o solicitante só vê e baixa evidência pública, e só a equipe pode marcar como interna. Recurso que o solicitante não pode ver responde 404.
- Política do formulário aplicada no servidor ao abrir o chamado: `NONE` recusa, `REQUIRED` exige ao menos uma, `OPTIONAL` aceita até 10. O arquivo é enviado antes (`POST /evidence/staged`, "preparado", `ticket_id` nulo, só do próprio autor) e confirmado pelo `evidenceIds` do pedido de criação. Preparado e não confirmado por 24 h é apagado (job horário).
- Segurança do upload: máximo 10 MB; lista de extensões (png, jpg, jpeg, gif, webp, pdf, txt, log, csv); tipo de mídia decidido pelo servidor (o do cliente é ignorado); conferência dos primeiros bytes contra a extensão; texto sem bytes nulos; nome saneado e nome em disco gerado (sem caminho do usuário); download sempre como `attachment` com `nosniff`. Armazenamento em disco (`nexusops.evidence.dir`, padrão `./data/evidence`) atrás de `EvidenceStorageService`, trocável por object storage. Não há antivírus integrado: `virus_scan_status` fica `PENDING` e é exposto como `scanStatus`.
- `TicketAccessService` centraliza "quem vê este chamado": a equipe vê o tenant; o solicitante só os que abriu (chamado de outro tenant ou de outra pessoa responde 404).
- Correção junto: colunas que guardam o e-mail do usuário eram `VARCHAR(36)` (assignee, actor, author, created_by...) e quebravam com e-mail longo; alargadas para 320.
- Achado para a Fase G: END_USER só tem `TICKET:READ:OWN` e o avaliador de permissão aceita qualquer escopo no portão de método, então `GET /tickets` e `GET /tickets/{id}` ainda não são escopados ao solicitante. O Portal precisa de API própria e leitura escopada.

## Fase G: Portal do Solicitante

- Experiência própria em `/portal` (layout próprio, sem menu de operação), não o console com menos itens. Quem não é equipe (AGENT ou acima) cai no Portal no login; `StaffGuard` manda `/tickets`, `/admin` etc. de volta ao Portal e `HomeGuard` decide a raiz.
- API `/portal/*` (`PortalController`/`PortalService`), sempre escopada a "chamados que eu abri", qualquer que seja o papel: lista (com "precisa da sua atenção" primeiro), detalhe, abrir pedido, responder, aceitar, contestar. Fila, prioridade e SLA são derivados no servidor e nunca saem pelo Portal; a etapa é traduzida (RECEIVED, IN_PROGRESS, WAITING_YOU, AWAITING_VALIDATION, CLOSED). O histórico é uma lista branca de eventos públicos com só o texto; nota interna, ROUTED, QUEUE_CHANGED, SLA e payloads com fila ou responsável anterior nunca aparecem.
- Abrir pedido: tópico + resumo + detalhes + localidade + respostas do formulário (`nx-dynamic-form`) + evidências preparadas. O corpo não define `reporterId`, tenant, prioridade nem responsável: vêm da identidade do chamador e do roteamento. Localidades para escolher vêm de `GET /locations/options` (mínimo id/nome/código, sem permissão de ativos).
- Responder: se a equipe pediu informação (WAITING), a resposta é a ação `REQUESTER_REPLY` (devolve ao atendimento e retoma o SLA); senão vira comentário público. Com a solução proposta (RESOLVED) só aceitar ou contestar; contestar exige motivo e abre novo ciclo. Anexos de uma resposta ficam ligados ao evento dela (Fase F).
- Correções encontradas no caminho: (1) o solicitante nunca era reconhecido como solicitante, porque `actorFor` comparava o e-mail do principal com o `reporterId` (UUID); agora usa `TicketAccessService.isRequesterOf`. (2) `RESUME` vencia `REQUESTER_REPLY` em `actionFor`, então responder pela máquina de estados era impossível; `TicketService.performAction` executa a ação pelo nome. (3) Vazamento entre solicitantes: END_USER tem `TICKET:READ:OWN` mas o portão de método aceita qualquer escopo e as leituras do console não eram escopadas; agora `GET /tickets*`, comentários, `/tickets/{id}/form`, timeline e ciclos exigem papel de equipe (`StaffAccess.READ`), e o solicitante lê só pelo Portal. A criação pelo console (`POST /tickets`) continua sem ser aberta ao END_USER, pois aceita `reporterId`/`assigneeId`/`tenantId` do corpo.

## Fase H — Console do analista

A operação passa a ser vista por **fila** e por **responsável**, que são recortes independentes (fila é equipe; responsável é pessoa).

- `GET /tickets/queue-view?scope=MINE|MY_QUEUES|UNASSIGNED|ALL&queueId&status&q`: sem `status` explícito mostra só o que está em aberto; o fechado se pede por `status=CLOSED`. Busca por título e número. Recorte inválido → 422.
- `GET /tickets/counts`: contagens abertas por recorte (abas da lista).
- `GET /tickets/{id}/context`: fila, tópico, área, solicitante e responsável com nomes resolvidos, mais as respostas do formulário com rótulo. O solicitante não acessa (403); ele usa o Portal.
- Todos exigem papel de equipe (`StaffAccess.READ`), porque `hasPermission` aceita qualquer escopo e deixaria o END_USER passar.
- Frontend: lista com abas de recorte + contagem, filtro e colunas de fila/responsável; detalhe com painel de origem, mover de fila, atribuição por membros da fila e composer de comentário (público ou nota interna).
- Verificação: `qaH.js` (20/20) e `qa-console-ui.js` (10/10).
