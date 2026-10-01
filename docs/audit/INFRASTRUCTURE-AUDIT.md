# Auditoria de Infraestrutura — NexusOps

**Data:** 2026-09-28
**Agente:** auditoria especializada de infraestrutura (somente leitura, nenhum arquivo alterado)
**Escopo:** Docker, docker-compose, Kubernetes (base+overlays), ArgoCD, CI/CD (.github/workflows), Terraform (infrastructure/), Observabilidade — comparado às ADR-006 a ADR-010.

## Resumo executivo
Boa fundação arquitetural (multi-stage Docker, Kustomize base+overlays, ArgoCD GitOps, ADRs detalhadas), mas gaps significativos entre estratégia documentada e implementação real, especialmente em segurança e observabilidade. Pipeline de CI/CD incompleto (sem deploy de produção).

**Maturidade: INCOMPLETA.** Estimativa: 4-5 semanas para production-ready.

## Docker

| Achado | Severidade |
|---|---|
| Backend usa `eclipse-temurin:21-jre-alpine` em vez de distroless (ADR-006 pede `gcr.io/distroless/java21-debian12:nonroot`) | CRITICAL |
| Sem diretiva `USER` no Dockerfile do backend — container roda como root | CRITICAL |
| Sem tuning de JVM em container (`-XX:MaxRAMPercentage`) — risco de OOM kill | HIGH |
| Sem Spring Boot layered JAR — build cache não otimizado | MEDIUM |
| `docker-compose.yml`: `POSTGRES_PASSWORD`/Redis password hardcoded em texto plano | CRITICAL |
| SecurityContext do frontend incompleto (falta `seccompProfile`, `readOnlyRootFilesystem`) | HIGH |
| Cache headers `expires 1y` para todos os paths, incluindo dinâmicos | MEDIUM |

## Kubernetes

| Achado | Severidade |
|---|---|
| **RBAC ausente** — `backend-deployment.yaml` referencia `serviceAccountName` que não é definido em nenhum lugar | CRITICAL |
| **`secret.yaml` com todos os valores vazios** — sem External Secrets Operator/Sealed Secrets implementado | CRITICAL |
| **Placeholders não resolvidos no Ingress** (`${ACM_CERT_ARN}`, `${WAF_ACL_ARN}`) — deploy falharia | CRITICAL |
| **Network policy do frontend sem egress de DNS** — frontend não conseguiria resolver hostname do backend | CRITICAL |
| Image tag `latest`/`dev`/`staging` em vez de commit SHA — deploys não reproduzíveis | HIGH |
| Sem startup probe (só liveness/readiness) — risco em apps de start lento | MEDIUM |
| `SPRING_PROFILES_ACTIVE=prod` hardcoded no deployment base (deveria vir só do overlay) | HIGH |
| HPA referencia métrica customizada (`http_requests_per_second`) sem confirmação de que o Micrometer a exporta | MEDIUM |

## ArgoCD
- Applications definidas para todos os ambientes, finalizers configurados, retry com backoff — **bem implementado.**
- `selfHeal: true` em todos os ambientes — reverte qualquer alteração manual no cluster em segundos; deve ser documentado como comportamento esperado.
- Whitelist de recursos de cluster no `project.yaml` permite todos os `Namespace` — recomenda-se restringir a `nexusops-*`.

## CI/CD (.github/workflows)

| Achado | Severidade |
|---|---|
| Job de assinatura de imagem referencia outputs (`backend-digest`) que o job `docker-build` não gera — assinatura sempre falha | CRITICAL |
| **Credenciais AWS de longa duração** no workflow de deploy dev, em vez de OIDC | CRITICAL |
| **Nenhum workflow de deploy de produção** — só dev e staging; produção é manual | CRITICAL |
| Scans de segurança (CodeQL/Trivy/Dependency-Check) não bloqueiam o pipeline em caso de vulnerabilidade | HIGH |
| Scripts de build inconsistentes entre CI (`build:dev`) e Dockerfile (`build`) | MEDIUM |

## Terraform (infrastructure/)
- Estrutura de módulos existe (vpc, eks, rds, elasticache, iam, kms, monitoring, s3, secrets, waf) — estágio esqueleto.
- **Ambiente de produção ausente** (`infrastructure/environments/` só tem dev/staging) — CRITICAL.
- Versão do cluster EKS (1.28) divergente da ADR-008 (1.29) — LOW.
- RDS de dev em `db.t3.medium` (burstable) — não representa comportamento de produção.

## Observabilidade

| Componente | Status |
|---|---|
| Métricas (Prometheus) | Parcial — sem regras de alerta, sem Alertmanager configurado |
| Logs (Loki) | Parcial — presente no compose, config de coleta não verificada |
| Traces (Tempo) | Parcial — retenção de apenas 1h (ADR pede 7 dias), sem tail-sampling |
| Profiles (Pyroscope) | **Ausente** — previsto na ADR-010, não implementado |
| Dashboards (Grafana) | Parcial — sem dashboards versionados visíveis |
| SLO/alertas de erro | **Ausente** |

## Classificação geral por camada

| Camada | Real | Esqueleto | Status |
|---|---|---|---|
| Docker | 50% | 50% | Precisa hardening (distroless, non-root) |
| Kubernetes | 70% | 30% | Precisa RBAC, secrets, image tags |
| ArgoCD | 85% | 15% | Bem implementado |
| CI/CD | 60% | 40% | Falta deploy de prod, assinatura de imagem quebrada, credenciais não-OIDC |
| Terraform | 30% | 70% | Estágio esqueleto; ambiente prod ausente |
| Observabilidade | 40% | 60% | Falta Pyroscope, alertas SLO, sampling |

## Classificação final: REFACTOR (não fazer deploy de produção sem resolver os CRITICAL)

### Bloqueadores de produção (must-fix)
1. RBAC ausente no K8s
2. Secrets vazios / sem External Secrets Operator
3. Dockerfile backend sem distroless/non-root/JVM tuning
4. Assinatura de imagem quebrada no CI
5. Placeholders não resolvidos no Ingress
6. Credenciais AWS não-OIDC
7. Sem workflow de deploy de produção
8. Network policy do frontend sem DNS egress

### Ordem recomendada
1. **Semanas 1-2:** RBAC, External Secrets Operator, hardening de Docker, correção do Ingress, SecurityContext do frontend.
2. **Semanas 2-3:** Migração para OIDC no CI, correção da assinatura de imagem, workflow de produção com aprovação manual.
3. **Semanas 3-4:** Pyroscope, tail-sampling, regras de alerta Prometheus/Alertmanager.
4. **Semanas 4-5:** Terraform de produção, revisão profunda dos módulos, ResourceQuota por namespace.

**Go/No-Go: NO-GO até os itens CRITICAL das fases 1-2 serem resolvidos.**
