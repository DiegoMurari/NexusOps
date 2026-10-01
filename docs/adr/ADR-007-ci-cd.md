# ADR-007: CI/CD Strategy

## Status
Accepted

## Context
NexusOps requires a CI/CD pipeline that:
- Validates code quality and security on every PR
- Builds and tests on every commit
- Deploys to dev/staging automatically
- Supports blue-green production deployment
- Provides fast feedback (< 10 min PR validation)
- Implements GitOps for Kubernetes

## Decision
Use **GitHub Actions** for CI/CD with **ArgoCD** for GitOps Kubernetes deployments.

## Pipeline Architecture

```
┌─────────────┐    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│   Commit    │───▶│    CI       │───▶│   Build     │───▶│   Deploy    │
│   / PR      │    │  (Validate) │    │  (Images)   │    │  (GitOps)   │
└─────────────┘    └─────────────┘    └─────────────┘    └─────────────┘
                        │                   │                   │
                        ▼                   ▼                   ▼
                 ┌─────────────┐    ┌─────────────┐    ┌─────────────┐
                 │ Lint, Test, │    │ Multi-arch  │    │ Dev → Stage │
                 │ Security,   │    │ Docker,     │    │ → Prod      │
                 │ ArchUnit    │    │ Sign, SBOM  │    │ (Blue-Green)│
                 └─────────────┘    └─────────────┘    └─────────────┘
```

## CI Pipeline (GitHub Actions)

### Trigger Rules
```yaml
on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main, develop]
  schedule:
    - cron: '0 2 * * 0'  # Weekly full scan
```

### Job Matrix (Parallel Execution)
```yaml
jobs:
  backend-lint:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '21', distribution: 'temurin', cache: 'maven' }
      - run: ./mvnw spotless:check checkstyle:check spotbugs:check pmd:check

  backend-test:
    runs-on: ubuntu-latest
    services:
      postgres: { image: postgres:16, env: { POSTGRES_DB: test } }
      redis: { image: redis:7 }
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '21', distribution: 'temurin', cache: 'maven' }
      - run: ./mvnw test -B
      - run: ./mvnw verify -DskipUnitTests=true -B  # Integration tests
      - uses: codecov/codecov-action@v4

  backend-archunit:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '21', distribution: 'temurin', cache: 'maven' }
      - run: ./mvnw test -Dtest=*ArchitectureTest -B

  frontend-lint:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: pnpm/action-setup@v3
      - uses: actions/setup-node@v4
        with: { node-version: '20', cache: 'pnpm' }
      - run: pnpm install --frozen-lockfile
      - run: pnpm run format:check && pnpm run lint

  frontend-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: pnpm/action-setup@v3
      - uses: actions/setup-node@v4
        with: { node-version: '20', cache: 'pnpm' }
      - run: pnpm install --frozen-lockfile
      - run: pnpm run test:unit
      - uses: codecov/codecov-action@v4

  security-scan:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: github/codeql-action/init@v3
        with: { languages: 'java,typescript' }
      - run: ./mvnw compile -B
      - run: cd frontend && pnpm install --frozen-lockfile && pnpm run build
      - uses: github/codeql-action/analyze@v3
      - uses: aquasecurity/trivy-action@master
        with: { scan-type: 'fs', format: 'sarif' }
      - uses: dependency-check/Dependency-Check_Action@main
        with: { project: 'NexusOps', path: '.', args: '--failOnCVSS 7' }
      - uses: trufflesecurity/trufflehog@main

  docker-build:
    needs: [backend-lint, backend-test, frontend-lint, frontend-test]
    if: github.event_name == 'push'
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: docker/setup-buildx-action@v3
      - uses: docker/login-action@v3
        with: { registry: ghcr.io, username: ${{ github.actor }}, password: ${{ secrets.GHCR_TOKEN }} }
      - uses: docker/metadata-action@v5
        id: meta-backend
        with:
          images: ghcr.io/${{ github.repository }}/nexusops-backend
          tags: type=ref,event=branch, type=sha
      - uses: docker/build-push-action@v5
        with:
          context: ./backend
          push: true
          tags: ${{ steps.meta-backend.outputs.tags }}
          cache-from: type=gha
          cache-to: type=gha,mode=max
      # Repeat for frontend
```

## CD Pipeline

### Dev Deployment (Auto on Feature Branches)
```yaml
# .github/workflows/cd-dev.yml
on:
  push:
    branches: ['feature/**', 'fix/**', 'develop']

jobs:
  deploy-dev:
    runs-on: ubuntu-latest
    environment: development
    steps:
      - uses: actions/checkout@v4
      - uses: aws-actions/configure-aws-credentials@v4
      - uses: aws-actions/amazon-ecr-login@v2
      - run: |
          # Pull from GHCR, tag for ECR, push
          docker pull ghcr.io/${{ github.repository }}/nexusops-backend:${{ github.sha }}
          docker tag ... ${{ registry }}/nexusops-backend:${{ github.sha }}
          docker push ${{ registry }}/nexusops-backend:${{ github.sha }}
      - uses: aws-actions/eks-update-kubeconfig@v1
        with: { cluster-name: nexusops-dev }
      - run: |
          cd k8s/overlays/dev
          kustomize edit set image backend=${{ registry }}/nexusops-backend:${{ github.sha }}
          kubectl apply -k .
          kubectl rollout status deployment/nexusops-backend -n nexusops-dev --timeout=300s
      - run: |
          # Smoke test
          kubectl run smoke-test --image=curlimages/curl --rm -i --restart=Never -- \
            curl -f http://nexusops-backend/actuator/health
```

### Staging Deployment (Auto on Main)
```yaml
# .github/workflows/cd-staging.yml
on:
  push:
    branches: [main]

jobs:
  deploy-staging:
    runs-on: ubuntu-latest
    environment: staging
    steps:
      # ... similar to dev but with staging cluster
      - run: |
          # Run integration tests against staging
          kubectl run integration-test --image=... --rm -i --restart=Never -- \
            ./mvnw test -Dtest=*IntegrationTest -Dspring.profiles.active=staging
```

### Production Deployment (Manual Approval + Blue-Green)
```yaml
# .github/workflows/cd-production.yml
on:
  workflow_dispatch:
    inputs:
      version:
        description: 'Version to deploy (tag or sha)'
        required: true
      strategy:
        description: 'Deployment strategy'
        type: choice
        options: [blue-green, rolling]
        default: blue-green

jobs:
  deploy-production:
    runs-on: ubuntu-latest
    environment: production
    steps:
      - uses: actions/checkout@v4
      - uses: aws-actions/configure-aws-credentials@v4
      - uses: aws-actions/amazon-ecr-login@v2
      - uses: aws-actions/eks-update-kubeconfig@v1
        with: { cluster-name: nexusops-prod }
      
      - name: Blue-Green Deploy
        if: inputs.strategy == 'blue-green'
        run: |
          # Deploy to inactive color
          ACTIVE=$(kubectl get svc nexusops-backend -n nexusops-prod -o jsonpath='{.spec.selector.color}')
          INACTIVE=$([ "$ACTIVE" = "blue" ] && echo "green" || echo "blue")
          
          kubectl apply -f - <<EOF
          apiVersion: apps/v1
          kind: Deployment
          metadata:
            name: nexusops-backend-${INACTIVE}
            namespace: nexusops-prod
          spec:
            replicas: 3
            selector:
              matchLabels:
                app: nexusops-backend
                color: ${INACTIVE}
            template:
              metadata:
                labels:
                  app: nexusops-backend
                  color: ${INACTIVE}
              spec:
                containers:
                - name: backend
                  image: ${{ registry }}/nexusops-backend:${{ inputs.version }}
          EOF
          
          # Wait for readiness
          kubectl rollout status deployment/nexusops-backend-${INACTIVE} -n nexusops-prod --timeout=300s
          
          # Smoke test inactive
          kubectl run smoke-${INACTIVE} --image=curlimages/curl --rm -i --restart=Never -- \
            curl -f http://nexusops-backend-${INACTIVE}/actuator/health
          
          # Switch traffic
          kubectl patch svc nexusops-backend -n nexusops-prod -p "{\"spec\":{\"selector\":{\"color\":\"${INACTIVE}\"}}}"
          
          # Verify
          kubectl run smoke-prod --image=curlimages/curl --rm -i --restart=Never -- \
            curl -f https://api.nexusops.com/actuator/health
```

## GitOps with ArgoCD

### Application Definitions
```yaml
# k8s/argocd/applications.yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: nexusops-dev
  namespace: argocd
spec:
  project: nexusops
  source:
    repoURL: https://github.com/nexusops/nexusops.git
    targetRevision: develop
    path: k8s/overlays/dev
  destination:
    server: https://kubernetes.default.svc
    namespace: nexusops-dev
  syncPolicy:
    automated:
      prune: true
      selfHeal: true
    syncOptions:
      - CreateNamespace=true
```

### Project RBAC
```yaml
# k8s/argocd/project.yaml
apiVersion: argoproj.io/v1alpha1
kind: AppProject
metadata:
  name: nexusops
  namespace: argocd
spec:
  sourceRepos:
    - https://github.com/nexusops/nexusops.git
  destinations:
    - namespace: nexusops-dev
      server: https://kubernetes.default.svc
    - namespace: nexusops-staging
      server: https://kubernetes.default.svc
    - namespace: nexusops-prod
      server: https://kubernetes.default.svc
  clusterResourceWhitelist:
    - group: ''
      kind: Namespace
  roles:
    - name: developer
      policies:
        - p, proj:nexusops:developer, applications, get, nexusops/nexusops-dev, allow
        - p, proj:nexusops:developer, applications, sync, nexusops/nexusops-dev, allow
```

## Quality Gates Summary

| Stage | Checks | Timeout |
|-------|--------|---------|
| PR Validation | Format, Lint, Unit Tests, ArchUnit, Security Scan | 10 min |
| Post-Merge | Integration Tests, Contract Tests, Docker Build | 15 min |
| Dev Deploy | Image Pull, Kustomize, Rollout, Smoke Test | 5 min |
| Staging Deploy | Dev steps + Integration Tests | 10 min |
| Prod Deploy | Manual Approval, Blue-Green, Smoke Test, Verify | 15 min |

## Secrets Management

### GitHub Environments
```yaml
# Repository Settings → Environments
development:
  protection_rules: []
  secrets:
    AWS_ACCESS_KEY_ID
    AWS_SECRET_ACCESS_KEY
    GHCR_TOKEN

staging:
  protection_rules: [required_reviewers: 1]
  secrets:
    AWS_ACCESS_KEY_ID
    AWS_SECRET_ACCESS_KEY
    GHCR_TOKEN

production:
  protection_rules: [required_reviewers: 2, wait_timer: 10min]
  secrets:
    AWS_ACCESS_KEY_ID
    AWS_SECRET_ACCESS_KEY
    GHCR_TOKEN
```

### OIDC for AWS (No Long-lived Keys)
```yaml
- name: Configure AWS Credentials
  uses: aws-actions/configure-aws-credentials@v4
  with:
    role-to-assume: arn:aws:iam::123456789:role/GitHubActions-NexusOps
    role-session-name: GitHubActions
    aws-region: us-east-1
```

## Rollback Strategy

### Automatic Rollback
```yaml
# In deployment spec
strategy:
  type: RollingUpdate
  rollingUpdate:
    maxSurge: 25%
    maxUnavailable: 0
```

### Manual Rollback (ArgoCD)
```bash
# Via CLI
argocd app rollback nexusops-prod <revision>

# Via UI
# Applications → nexusops-prod → History → Rollback
```

### Database Migration Rollback
- Flyway: `flyway undo` (requires commercial)
- Manual: Create compensating migration `V1.0.1__rollback_feature.sql`
- Blue-Green: Keep old version running until new version verified

## Monitoring & Alerts

### Pipeline Notifications
```yaml
- name: Notify Slack
  if: always()
  uses: slackapi/slack-github-action@v1.23.0
  with:
    payload: |
      {
        "text": "Pipeline ${{ job.status }}: ${{ github.workflow }}",
        "blocks": [...]
      }
  env:
    SLACK_WEBHOOK_URL: ${{ secrets.SLACK_WEBHOOK_URL }}
```

### Deployment Metrics
- Deployment frequency
- Lead time for changes
- Mean time to recovery
- Change failure rate

Tracked via GitHub Actions workflow runs and ArgoCD application health.