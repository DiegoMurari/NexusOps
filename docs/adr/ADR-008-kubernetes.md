# ADR-008: Kubernetes Deployment Strategy

## Status
Accepted

## Context
NexusOps runs on Kubernetes with requirements:
- GitOps deployment model
- Multi-environment (dev, staging, prod)
- Progressive delivery (blue-green, canary)
- Security hardening (network policies, RBAC, pod security)
- Auto-scaling based on custom metrics
- Cost optimization (spot instances, resource rightsizing)
- Disaster recovery capability

## Decision
Use **Kustomize** for configuration management with **ArgoCD** for GitOps, **External Secrets Operator** for secret management, and **Istio** for service mesh.

## Cluster Architecture

### EKS Cluster Design
```
┌─────────────────────────────────────────────────────────────┐
│                    EKS Cluster (1.29)                       │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │  System NG  │  │  General NG │  │    Memory NG        │  │
│  │  (m6i.large)│  │  (m6i.large)│  │    (r6i.large)      │  │
│  │  2-4 nodes  │  │  2-20 nodes │  │    1-10 nodes       │  │
│  │  Taint: sys │  │  No taint   │  │    No taint         │  │
│  └─────────────┘  └─────────────┘  └─────────────────────┘  │
│  ┌─────────────┐  ┌─────────────────────────────────────┐   │
│  │   Spot NG   │  │         Managed Add-ons             │   │
│  │  (mixed)    │  │  VPC CNI, CoreDNS, kube-proxy,      │   │
│  │  0-20 nodes │  │  EBS CSI, CloudWatch, ADOT          │   │
│  │  Taint:spot │  └─────────────────────────────────────┘   │
│  └─────────────┘                                            │
└─────────────────────────────────────────────────────────────┘
```

### Node Group Strategy
| Node Group | Instance Types | Capacity | Purpose | Taints |
|------------|---------------|----------|---------|--------|
| system | m6i.large | ON_DEMAND | System pods (CNI, CSI, monitoring) | `system=true:NoSchedule` |
| general | m6i.large, m6i.xlarge | ON_DEMAND | Application workloads | None |
| memory | r6i.large, r6i.xlarge | ON_DEMAND | Reporting, analytics | None |
| spot | m6i.large, m5.large, m5.xlarge | SPOT | Batch, CI, fault-tolerant | `spot=true:PreferNoSchedule` |

## Configuration Management (Kustomize)

### Base + Overlays Pattern
```
k8s/
├── base/
│   ├── namespace.yaml
│   ├── backend-deployment.yaml
│   ├── frontend-deployment.yaml
│   ├── backend-service.yaml
│   ├── frontend-service.yaml
│   ├── ingress.yaml
│   ├── configmap.yaml
│   ├── secret.yaml
│   ├── hpa.yaml
│   ├── network-policy.yaml
│   ├── pod-disruption-budget.yaml
│   ├── service-monitor.yaml
│   └── kustomization.yaml
├── overlays/
│   ├── dev/
│   │   ├── kustomization.yaml
│   │   └── patches/
│   ├── staging/
│   │   ├── kustomization.yaml
│   │   └── patches/
│   └── prod/
│       ├── kustomization.yaml
│       └── patches/
└── argocd/
    ├── applications.yaml
    └── project.yaml
```

### Environment-Specific Customization
```yaml
# overlays/prod/kustomization.yaml
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization

namespace: nexusops-prod

resources:
  - ../../base

commonLabels:
  environment: prod

images:
  - name: ghcr.io/nexusops/nexusops-backend
    newTag: v1.2.3
  - name: ghcr.io/nexusops/nexusops-frontend
    newTag: v1.2.3

patches:
  - patch: |-
      apiVersion: apps/v1
      kind: Deployment
      metadata:
        name: nexusops-backend
        namespace: nexusops-prod
      spec:
        replicas: 5
        template:
          spec:
            containers:
              - name: backend
                resources:
                  requests:
                    cpu: "1000m"
                    memory: "2Gi"
                  limits:
                    cpu: "4000m"
                    memory: "4Gi"
  - patch: |-
      apiVersion: autoscaling/v2
      kind: HorizontalPodAutoscaler
      metadata:
        name: nexusops-backend-hpa
        namespace: nexusops-prod
      spec:
        minReplicas: 5
        maxReplicas: 50
```

## Deployment Strategies

### Rolling Update (Default)
```yaml
strategy:
  type: RollingUpdate
  rollingUpdate:
    maxSurge: 25%
    maxUnavailable: 0
```

### Blue-Green (Production)
```yaml
# Separate deployments for blue/green
apiVersion: apps/v1
kind: Deployment
metadata:
  name: nexusops-backend-blue
  labels:
    app: nexusops-backend
    color: blue
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: nexusops-backend-green
  labels:
    app: nexusops-backend
    color: green
---
# Service selects active color
apiVersion: v1
kind: Service
metadata:
  name: nexusops-backend
spec:
  selector:
    app: nexusops-backend
    color: blue  # Switch to green for rollout
```

### Canary (Future)
```yaml
# Using Flagger + Istio
apiVersion: flagger.app/v1beta1
kind: Canary
metadata:
  name: nexusops-backend
spec:
  targetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: nexusops-backend
  progressDeadlineSeconds: 60
  service:
    port: 8080
    targetPort: 8080
  analysis:
    interval: 1m
    threshold: 5
    metrics:
      - name: request-success-rate
        thresholdRange:
          min: 99
      - name: request-duration-p95
        thresholdRange:
          max: 500
```

## Security Hardening

### Pod Security Standards (Restricted)
```yaml
# namespace.yaml
apiVersion: v1
kind: Namespace
metadata:
  name: nexusops-prod
  labels:
    pod-security.kubernetes.io/enforce: restricted
    pod-security.kubernetes.io/audit: restricted
    pod-security.kubernetes.io/warn: restricted
```

### Network Policies
```yaml
# Default deny all
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: default-deny-all
  namespace: nexusops-prod
spec:
  podSelector: {}
  policyTypes:
    - Ingress
    - Egress
---
# Allow ingress from ingress-nginx
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-from-ingress
  namespace: nexusops-prod
spec:
  podSelector:
    matchLabels:
      app: nexusops-frontend
  policyTypes:
    - Ingress
  ingress:
    - from:
        - namespaceSelector:
            matchLabels:
              name: ingress-nginx
      ports:
        - protocol: TCP
          port: 80
---
# Allow backend egress to PostgreSQL/Redis
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: backend-egress
  namespace: nexusops-prod
spec:
  podSelector:
    matchLabels:
      app: nexusops-backend
  policyTypes:
    - Egress
  egress:
    - to:
        - podSelector:
            matchLabels:
              app: postgres
      ports:
        - protocol: TCP
          port: 5432
    - to:
        - podSelector:
            matchLabels:
              app: redis
      ports:
        - protocol: TCP
          port: 6379
    - to:
        - namespaceSelector: {}
      ports:
        - protocol: TCP
          port: 53
        - protocol: UDP
          port: 53
```

### RBAC
```yaml
# ServiceAccount with minimal permissions
apiVersion: v1
kind: ServiceAccount
metadata:
  name: nexusops-backend
  namespace: nexusops-prod
---
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: nexusops-backend
  namespace: nexusops-prod
rules:
  - apiGroups: [""]
    resources: ["configmaps", "secrets"]
    verbs: ["get", "list", "watch"]
  - apiGroups: [""]
    resources: ["pods"]
    verbs: ["get", "list"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: nexusops-backend
  namespace: nexusops-prod
subjects:
  - kind: ServiceAccount
    name: nexusops-backend
    namespace: nexusops-prod
roleRef:
  kind: Role
  name: nexusops-backend
  apiGroup: rbac.authorization.k8s.io
```

### Pod Security Context
```yaml
# In deployment template
spec:
  template:
    spec:
      securityContext:
        runAsNonRoot: true
        runAsUser: 1000
        runAsGroup: 1000
        fsGroup: 1000
        seccompProfile:
          type: RuntimeDefault
      containers:
        - name: backend
          securityContext:
            allowPrivilegeEscalation: false
            readOnlyRootFilesystem: true
            capabilities:
              drop: ["ALL"]
            runAsNonRoot: true
            runAsUser: 1000
```

## Secret Management

### External Secrets Operator
```yaml
# SecretStore (Cluster-scoped)
apiVersion: external-secrets.io/v1beta1
kind: ClusterSecretStore
metadata:
  name: aws-secrets-manager
spec:
  provider:
    aws:
      service: SecretsManager
      region: us-east-1
      auth:
        jwt:
          serviceAccountRef:
            name: external-secrets
            namespace: external-secrets
---
# ExternalSecret (Namespace-scoped)
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: nexusops-secrets
  namespace: nexusops-prod
spec:
  refreshInterval: 1h
  secretStoreRef:
    name: aws-secrets-manager
    kind: ClusterSecretStore
  target:
    name: nexusops-secrets
    creationPolicy: Owner
  data:
    - secretKey: db-password
      remoteRef:
        key: nexusops-prod/db/credentials
        property: password
    - secretKey: redis-password
      remoteRef:
        key: nexusops-prod/redis/credentials
        property: password
    - secretKey: jwt-private-key
      remoteRef:
        key: nexusops-prod/jwt/keys
        property: private_key
```

## Auto-Scaling

### Horizontal Pod Autoscaler (HPA)
```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: nexusops-backend-hpa
  namespace: nexusops-prod
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: nexusops-backend
  minReplicas: 3
  maxReplicas: 50
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
    - type: Resource
      resource:
        name: memory
        target:
          type: Utilization
          averageUtilization: 80
    - type: Pods
      pods:
        metric:
          name: http_requests_per_second
        target:
          type: AverageValue
          averageValue: "1000"
  behavior:
    scaleDown:
      stabilizationWindowSeconds: 300
      policies:
        - type: Percent
          value: 10
          periodSeconds: 60
    scaleUp:
      stabilizationWindowSeconds: 0
      policies:
        - type: Percent
          value: 100
          periodSeconds: 15
        - type: Pods
          value: 4
          periodSeconds: 15
      selectPolicy: Max
```

### Vertical Pod Autoscaler (VPA)
```yaml
apiVersion: autoscaling.k8s.io/v1
kind: VerticalPodAutoscaler
metadata:
  name: nexusops-backend-vpa
  namespace: nexusops-prod
spec:
  targetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: nexusops-backend
  updatePolicy:
    updateMode: "Auto"
  resourcePolicy:
    containerPolicies:
      - containerName: backend
        minAllowed:
          cpu: "250m"
          memory: "512Mi"
        maxAllowed:
          cpu: "4000m"
          memory: "4Gi"
        controlledResources: ["cpu", "memory"]
```

### Cluster Autoscaler
```yaml
# Deployed as deployment in kube-system
# Config via ConfigMap
apiVersion: v1
kind: ConfigMap
metadata:
  name: cluster-autoscaler-config
  namespace: kube-system
data:
  scale-down-delay-after-add: "10m"
  scale-down-unneeded-time: "10m"
  scale-down-utilization-threshold: "0.5"
  max-node-provision-time: "15m"
  ignore-daemonsets-utilization: "true"
  skip-nodes-with-local-storage: "true"
  skip-nodes-with-system-pods: "true"
```

## Observability Integration

### ServiceMonitor (Prometheus)
```yaml
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: nexusops-backend
  namespace: nexusops-prod
  labels:
    release: prometheus
spec:
  selector:
    matchLabels:
      app: nexusops-backend
  endpoints:
    - port: http
      path: /api/v1/actuator/prometheus
      interval: 30s
  namespaceSelector:
    matchNames:
      - nexusops-prod
```

### PodMonitor (For Non-Service Scraping)
```yaml
apiVersion: monitoring.coreos.com/v1
kind: PodMonitor
metadata:
  name: nexusops-backend-jvm
  namespace: nexusops-prod
spec:
  selector:
    matchLabels:
      app: nexusops-backend
  podMetricsEndpoints:
    - port: http
      path: /api/v1/actuator/prometheus
      interval: 30s
```

## Disaster Recovery

### Backup Strategy
```yaml
# Velero backup schedule
apiVersion: velero.io/v1
kind: Schedule
metadata:
  name: nexusops-daily-backup
  namespace: velero
spec:
  schedule: "0 2 * * *"
  template:
    includedNamespaces:
      - nexusops-prod
    includedResources:
      - "*"
    excludedResources:
      - events
      - pods
    ttl: 720h
    storageLocation: default
```

### Cross-Region DR
- EKS cluster in us-west-2 (warm standby)
- RDS cross-region replica
- S3 cross-region replication
- ArgoCD ApplicationSet for multi-cluster
- RTO: < 1 hour, RPO: < 5 minutes

## Cost Optimization

### Right-Sizing
- VPA recommendations applied weekly
- HPA based on custom metrics (not just CPU)
- Spot instances for fault-tolerant workloads
- Savings Plans for baseline capacity

### Resource Quotas
```yaml
apiVersion: v1
kind: ResourceQuota
metadata:
  name: nexusops-prod-quota
  namespace: nexusops-prod
spec:
  hard:
    requests.cpu: "32"
    requests.memory: "64Gi"
    limits.cpu: "64"
    limits.memory: "128Gi"
    persistentvolumeclaims: "20"
    services.loadbalancers: "2"
```