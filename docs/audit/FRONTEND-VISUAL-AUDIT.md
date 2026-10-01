# Auditoria Visual do Frontend — NexusOps

**Data:** 2026-09-28
**Método:** Angular dev server rodado localmente (`npm start`, porta 4200, confirmada via `package.json`) e inspecionado com Chrome DevTools MCP (screenshot, snapshot de acessibilidade, console, rede, JS runtime). **Nenhum arquivo do projeto foi alterado.**
**Backend:** não foi tocado nem "consertado" para esta inspeção, conforme pedido. Como `AuthGuard`/`MfaGuard` fazem a checagem de sessão **inteiramente no cliente** (`AuthService.isAuthenticated()` só olha `localStorage`, sem chamar o backend), foi possível ver as telas protegidas injetando uma sessão sintética no `localStorage` do navegador via `evaluate_script` — isso não altera nenhum arquivo do repositório, é só estado do navegador. Todas as chamadas HTTP reais para `http://localhost:8080/api/v1` falham (backend não está rodando), então qualquer tela que dependa de dados reais é mostrada aqui em seu estado de erro/vazio/mock, o que é registrado explicitamente abaixo.

## Telas observadas

| Tela | Rota | Acessível sem backend? | Estado renderizado |
|---|---|---|---|
| Login | `/login` | Sim (pública) | Formulário completo renderizado |
| Verificação MFA | `/mfa-challenge` | Sim (pública) | Formulário completo renderizado |
| Dashboard | `/dashboard` | Sim, com sessão sintética | **Dados 100% mockados/hardcoded no componente** — nenhuma chamada de rede é feita |
| Tickets (lista) | `/tickets` | Sim, com sessão sintética | Placeholder: "Lista de tickets - Em desenvolvimento" |
| SLA | `/sla` | Sim, com sessão sintética | Placeholder: "SLA Dashboard - Em desenvolvimento" |
| Admin (dashboard) | `/admin` | Sim, com sessão sintética | Placeholder: "Admin Dashboard - Em desenvolvimento" |
| Admin → Usuários | `/admin/users` | Sim, com sessão sintética | Placeholder: "User Management - Em desenvolvimento" |
| Assets, Knowledge, Reports, Integrations e demais sub-rotas de Admin (Tenants, Roles, Feature Flags, Settings, Audit Logs), Ticket Create/Detail | várias | Sim, com sessão sintética (não testadas uma a uma, mas confirmadas via código-fonte) | Mesmo padrão de placeholder — `grep` no código confirma que `ticket-list`, `sla-definition-list`, `admin-dashboard`, `user-management`, `tenant-management`, `role-management`, etc. seguem o mesmo template raso |

**Sem backend, sem sessão sintética:** só Login e MFA são alcançáveis — qualquer outra rota redireciona para `/login` (confirmado no `AuthGuard`). Isso é o que um usuário real veria hoje: duas telas.

## Aparência atual

Visual escuro (dark theme) consistente, tipografia Roboto, paleta de cores (roxo/azul primário, cards com borda lateral colorida por categoria, cards escuros `#3a3a3a`-ish) aplicada de forma uniforme nas poucas telas reais. A estética em si (cores, dark mode, cards com accent lateral) tem potencial de parecer profissional — mas está seriamente comprometida por bugs de execução (abaixo).

## Achados críticos (bugs visuais, não preferência de design)

### 1. Ícones quebrados em 100% da aplicação — texto cru sobreposto ao conteúdo
**Causa raiz confirmada:** os componentes `<mat-icon>` usam a classe CSS `material-icons` (fonte ligature clássica "Material Icons"), mas a aplicação só carrega a fonte **"Material Symbols Outlined"** via Google Fonts (`index.html`/config de fontes). Como a fonte "Material Icons" nunca é carregada, o navegador renderiza o nome literal do ícone (`login`, `search`, `visibility`, `notifications`, `menu`, `add`, `list`, `article`...) na fonte padrão (Roboto), e como os elementos de ícone têm largura fixa menor que o texto, o texto **vaza e sobrepõe** o label ao lado.
**Efeito visual:** em toda tela, cada botão/ícone mostra fragmentos de texto cortados e sobrepostos: "log" sobre "Entrar", "se" sobre o título do card, "vis" sobre o campo de senha, "notific" sobre "notifications", "ad"/"lis"/"ar" sobre os botões de ação rápida do dashboard, "as"/"wa"/"dn"/"mi" sobre os ícones dos cards de estatística, "pin"/"ver" na tela de MFA.
**Severidade:** CRITICAL — não é estético, é um bug de renderização visível em toda a superfície do produto.

### 2. Layout desperdiça 280px de largura em TODA tela, em TODA resolução
**Causa raiz confirmada:** o `mat-sidenav` (`mode="side"`, `width: 280px`) está com `display: none` (nunca renderiza visualmente — nem colapsado, nem expandido), mas o `.mat-drawer-content` que envolve o header e o conteúdo principal continua deslocado `left: 280px`, como se a sidebar estivesse visível e ocupando espaço. Resultado: **280px de espaço em branco à esquerda em toda tela**, e o conteúdo real fica espremido no restante.
**Efeito:** em desktop, gera barra de rolagem horizontal desnecessária (visível em todas as capturas). Em mobile (375-502px de largura testada), os 280px "fantasmas" consomem mais da metade da tela — sobra menos de 45% da largura para o conteúdo real, tornando a interface praticamente inutilizável em celular.
**Severidade:** CRITICAL — afeta 100% das telas, 100% das resoluções.

### 3. Navegação principal (sidebar) é completamente inacessível
O botão "Toggle menu" no header existe e é clicável, mas não abre a sidebar (ela está fixa em `display:none`, não em um estado colapsado que o toggle poderia reverter). **Não há nenhuma forma visual de navegar entre Dashboard, Tickets, Assets, Knowledge Base, SLA, Reports, Integrations e Administration** — os itens de menu existem no DOM (confirmado via snapshot de acessibilidade) mas nunca ficam visíveis na tela. Hoje, a única navegação possível é digitar a URL manualmente ou usar os 4 botões de "Ações Rápidas" do dashboard (que só cobrem Ticket/Asset/Artigo).
**Severidade:** CRITICAL — sem navegação visível, o produto não é usável por um usuário real.

### 4. Login renderiza dentro do shell autenticado, não em layout público
O formulário de login aparece **dentro** do mesmo `mat-sidenav-container`/header/nav que as telas autenticadas usam — o snapshot de acessibilidade mostra a navegação completa (Dashboard, Tickets, Assets...) presente no DOM da tela de login, mesmo sem sessão. Um usuário não autenticado nunca deveria ver estrutura de navegação para áreas protegidas.
**Severidade:** HIGH — mistura de contexto de layout público/privado.

### 5. Dashboard exibe dados 100% mockados, sem nenhuma chamada de rede
Nenhuma requisição HTTP é feita ao carregar `/dashboard` (confirmado via lista de requisições de rede: zero XHR/fetch). Os números ("24 Tickets Abertos", "1234 Assets Cadastrados", nomes como "João Silva", "Maria Santos", "INC-2024-001") estão hardcoded no `dashboard.component.ts`. Isso não é um bug do frontend em si (é esperado que sem backend não haja dados reais), mas confirma que **esta tela nunca foi conectada a uma API**, nem mesmo com tratamento de loading/erro — é puramente decorativa hoje.

### 6. Links mortos na tela de login
"Esqueci a senha" e "Registre-se" apontam para `/auth/forgot-password` e `/auth/register`, rotas que **não existem** em `app.routes.ts` (cairiam no wildcard, que redireciona para `/dashboard`, que por sua vez redireciona de volta para `/login` sem sessão — um ciclo sem tela de destino real).

## Problemas de UX (além dos bugs acima)
- Estado de loading: não observável nas 3 telas reais — os cards do dashboard aparecem "prontos" instantaneamente (mock), sem skeleton/spinner. Não há indicação de como a app trata carregamento real de dados.
- Estados vazios: não observáveis — nenhuma tela real tem lista de dados (as que teriam são todas placeholder "Em desenvolvimento").
- Mensagens de erro: não testáveis com o backend fora do ar de forma realista sem forçar um submit de login (fora do escopo desta inspeção somente-visual).
- Hierarquia visual do dashboard é razoável (cards de estatística → ações rápidas → atividade recente), mas cada elemento está comprometido pelo bug de ícone.
- Tipografia e espaçamento nos cards parecem consistentes onde dá para avaliar (antes do texto de ícone sobrepor).

## Componentes que podem ser preservados (KEEP)
- Paleta de cores e dark theme (roxo primário `#5c6bc0`-ish, cards com accent lateral colorido).
- Estrutura de layout com `mat-sidenav-container` + toolbar + drawer — o *conceito* está certo, só a execução (280px fantasma, display:none) está quebrada.
- Formulário de login: campos, validação reativa, checkbox "Lembrar-me", estrutura geral do card.
- Tela de MFA: fluxo de "código de 6 dígitos" + opção de "usar código de recuperação" é um bom padrão de UX.
- Cards de estatística do dashboard (layout de card com número grande + label + badge de tendência) — bom padrão para reutilizar quando os dados forem reais.

## Componentes que precisam melhorar (IMPROVE)
- Sistema de ícones: trocar a classe `material-icons` para `material-symbols-outlined` (ou carregar a fonte "Material Icons" clássica) — correção pontual, não redesign.
- `mat-drawer-content`: corrigir o offset de 280px quando a sidebar não está visível (ou simplesmente corrigir a sidebar para aparecer).
- Toggle de menu: implementar de fato a abertura/fechamento da sidebar.
- Layout do login: mover para fora do shell autenticado (rota pública com layout próprio).
- Corrigir/criar as rotas `/auth/forgot-password` e `/auth/register`, ou remover os links até existirem.

## Telas que precisam de redesign (REDESIGN)
Nenhuma das 3 telas reais precisa de redesign conceitual — precisam de **correção de bugs de execução**. O conceito visual (dark theme, cards, formulários) é razoável como ponto de partida. O termo "redesign" cabe melhor às **telas que ainda não existem de fato** (ver MISSING) — quando forem implementadas, é a oportunidade de já nascerem consistentes, sem repetir os bugs de ícone/layout.

## Funcionalidades visuais ausentes (MISSING)
- Lista de tickets real (tabela, filtros, paginação, badges de status/prioridade) — hoje é um placeholder de uma linha.
- Detalhe de ticket, criação de ticket — não implementados visualmente (só a rota existe).
- Dashboard de SLA, gestão de assets, base de conhecimento, relatórios, integrações — todos placeholders.
- Toda a área de Administração (usuários, tenants, roles, feature flags, settings, audit logs) — placeholders.
- Estados de loading (skeleton/spinner) para quando dados reais chegarem.
- Estados vazios ("nenhum ticket encontrado", etc.).
- Sidebar de navegação funcional (existe no código, nunca renderiza).
- Layout responsivo/mobile — não há nenhum breakpoint observável; o bug do offset de 280px torna qualquer visualização em tela pequena praticamente inutilizável.

## Classificação resumida

| Item | Classificação |
|---|---|
| Paleta de cores / dark theme | KEEP |
| Estrutura conceitual de layout (sidenav+toolbar) | KEEP (conceito), IMPROVE (execução) |
| Sistema de ícones | IMPROVE (bug de classe CSS, correção simples) |
| Offset de 280px / sidebar invisível | IMPROVE (bug concreto, não redesign) |
| Formulário de login | KEEP (conteúdo), IMPROVE (mover para layout público) |
| Formulário de MFA | KEEP |
| Cards de estatística do dashboard | KEEP (layout), precisa conectar a dados reais |
| Responsividade mobile | IMPROVE urgente — hoje inexistente na prática |
| Tickets, SLA, Assets, Knowledge, Reports, Integrations, Admin (todas as sub-telas) | MISSING — não há UI real ainda, é trabalho novo, não redesign |

## Respostas às 10 perguntas

**1. Como o NexusOps está visualmente hoje?**
Um esqueleto de design com boa intenção (dark theme consistente, paleta definida, estrutura de cards) mas com bugs de execução que quebram a experiência em toda tela: ícones sobrepostos como texto cru, 280px de layout desperdiçado, navegação principal invisível.

**2. Ele parece um produto profissional ou ainda um protótipo?**
Protótipo — e nem um protótipo polido. Das ~15+ telas mapeadas nas rotas, apenas 3 têm alguma UI real (Login, MFA, Dashboard), e mesmo essas 3 têm bugs visuais graves visíveis a olho nu. As demais são literalmente texto "Em desenvolvimento".

**3. Quais telas estão boas?**
Nenhuma está "boa" no estado atual por causa dos bugs de ícone/layout que afetam todas. As que têm o **melhor ponto de partida conceitual** são Login e MFA (formulários bem estruturados, boa lógica de UX no fluxo de MFA).

**4. Quais estão ruins?**
Todas as telas de feature (Tickets, SLA, Assets, Knowledge, Reports, Integrations, toda a Administração) — são placeholders sem nenhuma UI.

**5. O dashboard precisa de redesign?**
Não de redesign conceitual — precisa de correção de bugs (ícones, layout) e de ser conectado a dados reais (hoje é 100% mock, sem loading/erro/vazio). O layout de cards em si é reaproveitável.

**6. O login precisa de redesign?**
Não. Precisa sair do shell autenticado (usar layout público), corrigir ícones, corrigir/remover os 2 links mortos. O conteúdo e a estrutura do formulário estão bons.

**7. A navegação precisa de redesign?**
O conceito (sidebar lateral com ícones) é adequado para este tipo de produto (service desk). O que precisa é **fazer a sidebar existir de fato** — hoje ela não aparece em nenhuma circunstância.

**8. O design system existente é aproveitável?**
Parcialmente. Angular Material está integrado, a paleta de cores é consistente, o padrão de card com accent lateral é bom. Mas não há evidência de um design system formal (tokens de espaçamento, tipografia documentada, biblioteca de componentes reutilizáveis) — parece decisões pontuais tela a tela. Aproveitável como ponto de partida, mas precisa ser formalizado.

**9. Quanto do frontend pode ser preservado?**
A arquitetura de código (confirmada na auditoria técnica anterior: standalone components, signals, guards, lazy loading) e a decisão visual de base (dark theme, paleta, Angular Material) podem ser preservadas quase integralmente. O que precisa ser feito do zero é a **implementação visual de ~90% das telas** (hoje inexistente) e a correção dos bugs estruturais (ícones, layout de 280px, sidebar).

**10. Qual seria a proposta visual para transformar o NexusOps em um produto profissional?**
Não incluída aqui, por instrução explícita — este documento é só o diagnóstico. A proposta de novo design deve ser decidida em conjunto na próxima etapa, agora que há evidência concreta do que existe, o que funciona e o que está quebrado.

## Nota operacional
O servidor de desenvolvimento Angular (`npm start`, processo em background, porta 4200) foi deixado rodando para permitir inspeção contínua, se desejado. Nenhum arquivo do projeto foi criado, editado ou removido durante esta auditoria — apenas estado de navegador (localStorage) foi manipulado para contornar o guard de autenticação client-side, sem tocar no backend.
