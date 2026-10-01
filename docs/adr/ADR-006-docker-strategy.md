# ADR-006: Docker & Container Strategy

## Status
Accepted

## Context
NexusOps needs container images that are:
- Secure (minimal attack surface)
- Small (fast pulls, deployments)
- Reproducible (deterministic builds)
- Multi-arch (AMD64 + ARM64)
- Signed (supply chain security)

## Decision
Use **Multi-stage Docker builds** with **Distroless base images**, **BuildKit**, and **Cosign signing**.

## Backend Image (Spring Boot)

### Multi-Stage Build
```dockerfile
# Stage 1: Builder (JDK + Maven)
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /workspace

# Install Maven
RUN apk add --no-cache maven

# Copy only pom files first (dependency caching)
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY nexusops-shared-kernel/pom.xml nexusops-shared-kernel/
COPY nexusops-iam/pom.xml nexusops-iam/
COPY nexusops-platform/pom.xml nexusops-platform/
COPY nexusops-sla/pom.xml nexusops-sla/
COPY nexusops-ticketing/pom.xml nexusops-ticketing/
COPY nexusops-asset/pom.xml nexusops-asset/
COPY nexusops-knowledge/pom.xml nexusops-knowledge/
COPY nexusops-notification/pom.xml nexusops-notification/
COPY nexusops-reporting/pom.xml nexusops-reporting/
COPY nexusops-integration/pom.xml nexusops-integration/
COPY nexusops-bootstrap/pom.xml nexusops-bootstrap/

# Download dependencies (cached layer)
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY . .

# Build application
RUN ./mvnw clean package -DskipTests -pl nexusops-bootstrap -am

# Stage 2: Runtime (JRE Distroless)
FROM gcr.io/distroless/java21-debian12:nonroot

WORKDIR /app

# Copy JAR from builder
COPY --from=builder /workspace/nexusops-bootstrap/target/nexusops-bootstrap-*.jar app.jar

# Non-root user (UID 1000 from distroless)
USER nonroot:nonroot

# JVM Options for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

ENTRYPOINT ["java", "${JAVA_OPTS}", "-jar", "app.jar"]
```

### Spring Boot Layered JAR (Optimized)
```xml
<!-- In nexusops-bootstrap/pom.xml -->
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
    <configuration>
        <layers>
            <enabled>true</enabled>
        </layers>
    </configuration>
</plugin>
```

Then use Spring Boot's layer extraction:
```dockerfile
# Extract layers
RUN java -Djarmode=layertools -jar app.jar extract --destination extracted

# Stage 2: Runtime
FROM gcr.io/distroless/java21-debian12:nonroot
WORKDIR /app
COPY --from=builder /workspace/extracted/dependencies/ ./
COPY --from=builder /workspace/extracted/spring-boot-loader/ ./
COPY --from=builder /workspace/extracted/snapshot-dependencies/ ./
COPY --from=builder /workspace/extracted/application/ ./
USER nonroot:nonroot
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
```

### Benefits
- **Layer caching**: Dependencies change less often than application code
- **Smaller images**: Only changed layers rebuilt
- **Faster startup**: Classpath optimized

## Frontend Image (Angular + Nginx)

### Multi-Stage Build
```dockerfile
# Stage 1: Builder (Node + pnpm)
FROM node:20-alpine AS builder

WORKDIR /workspace

# Enable pnpm
RUN corepack enable && corepack prepare pnpm@latest --activate

# Copy package files
COPY package.json pnpm-lock.yaml* ./

# Install dependencies (cached)
RUN pnpm install --frozen-lockfile

# Copy source
COPY . .

# Build production
RUN pnpm run build

# Stage 2: Runtime (Nginx Alpine)
FROM nginx:alpine AS runner

# Copy built assets
COPY --from=builder /workspace/dist/nexusops-frontend/browser /usr/share/nginx/html

# Copy nginx config
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Non-root user
USER nginx

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
```

### Nginx Configuration
```nginx
# nginx.conf
server {
    listen 80;
    server_name localhost;
    root /usr/share/nginx/html;
    index index.html;

    # SPA routing
    location / {
        try_files $uri $uri/ /index.html;
    }

    # API proxy (for local dev)
    location /api/ {
        proxy_pass http://backend:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # WebSocket proxy
    location /ws/notifications {
        proxy_pass http://backend:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_read_timeout 86400;
    }

    # SSE proxy
    location /api/v1/notifications/stream {
        proxy_pass http://backend:8080;
        proxy_set_header Host $host;
        proxy_cache off;
        proxy_buffering off;
        proxy_read_timeout 86400;
    }

    # Static asset caching
    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }

    # Gzip compression
    gzip on;
    gzip_vary on;
    gzip_min_length 1024;
    gzip_types text/plain text/css text/xml text/javascript application/javascript application/xml+rss application/json;
}
```

## Docker Compose

### Local Development
```yaml
# docker-compose.yml
version: '3.8'

services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: nexusops
      POSTGRES_USER: nexusops
      POSTGRES_PASSWORD: nexusops
    ports: ["5432:5432"]
    volumes: [postgres_data:/var/lib/postgresql/data]
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U nexusops -d nexusops"]
      interval: 10s

  redis:
    image: redis:7-alpine
    command: redis-server --appendonly yes --requirepass nexusops
    ports: ["6379:6379"]
    volumes: [redis_data:/data]
    healthcheck:
      test: ["CMD", "redis-cli", "-a", "nexusops", "ping"]
      interval: 10s

  mailhog:
    image: mailhog/mailhog
    ports: ["1025:1025", "8025:8025"]

  backend:
    build:
      context: ../backend
      dockerfile: Dockerfile.dev
    environment:
      SPRING_PROFILES_ACTIVE: dev
      DB_HOST: postgres
      REDIS_HOST: redis
      MAIL_HOST: mailhog
      JWT_PRIVATE_KEY: dev-key
      JWT_PUBLIC_KEY: dev-key
    ports: ["8080:8080", "5005:5005"]  # Debug port
    volumes:
      - ../backend:/workspace
      - ~/.m2:/root/.m2
    depends_on:
      postgres: { condition: service_healthy }
      redis: { condition: service_healthy }

  frontend:
    build:
      context: ../frontend
      dockerfile: Dockerfile.dev
    ports: ["4200:4200"]
    volumes:
      - ../frontend:/workspace
      - /workspace/node_modules
    depends_on: [backend]

volumes:
  postgres_data:
  redis_data:
```

### Dev Override
```yaml
# docker-compose.dev.yml
version: '3.8'

services:
  backend:
    command: ["./mvnw", "spring-boot:run", "-Dspring-boot.run.jvmArguments=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"]
    environment:
      SPRING_PROFILES_ACTIVE: dev

  frontend:
    command: ["pnpm", "run", "start"]
```

## Security Hardening

### Distroless Benefits
- No shell (`/bin/sh`)
- No package manager (`apk`, `apt`)
- No unnecessary binaries
- Minimal CVE surface
- Non-root user by default

### Image Scanning
```yaml
# CI Pipeline
- name: Scan image
  uses: aquasecurity/trivy-action@master
  with:
    image-ref: ghcr.io/nexusops/nexusops-backend:${{ github.sha }}
    format: sarif
    output: trivy-results.sarif
    severity: CRITICAL,HIGH
    ignore-unfixed: true
```

### Base Image Updates
```yaml
# Dependabot for base images
version: 2
updates:
  - package-ecosystem: "docker"
    directory: "/backend"
    schedule:
      interval: "weekly"
    labels: ["docker", "backend"]
  - package-ecosystem: "docker"
    directory: "/frontend"
    schedule:
      interval: "weekly"
    labels: ["docker", "frontend"]
```

### Image Signing (Cosign)
```bash
# Sign image
cosign sign --yes ghcr.io/nexusops/nexusops-backend@v1.0.0

# Verify signature
cosign verify ghcr.io/nexusops/nexusops-backend@v1.0.0 \
  --certificate-identity-regexp "https://github.com/nexusops/nexusops" \
  --certificate-oidc-issuer "https://token.actions.githubusercontent.com"
```

### SBOM Generation
```bash
# Generate SBOM
syft ghcr.io/nexusops/nexusops-backend:v1.0.0 -o spdx-json=sbom.spdx.json

# Upload to GitHub
gh api repos/nexusops/nexusops/dependency-graph/sboms \
  --method POST \
  --field sbom=@sbom.spdx.json
```

## Multi-Architecture Builds

### BuildKit Configuration
```dockerfile
# syntax = docker/dockerfile:1.7
FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk-alpine AS builder
# ... build steps
```

### GitHub Actions
```yaml
- name: Set up Docker Buildx
  uses: docker/setup-buildx-action@v3

- name: Build and push multi-arch
  uses: docker/build-push-action@v5
  with:
    context: ./backend
    platforms: linux/amd64,linux/arm64
    push: true
    tags: ghcr.io/nexusops/nexusops-backend:${{ github.sha }}
```

## Image Promotion Strategy

### Tagging Convention
| Tag | Purpose | Example |
|-----|---------|---------|
| `sha-<commit>` | Unique build identifier | `sha-a1b2c3d` |
| `branch-<name>` | Branch builds | `branch-feature-ticketing` |
| `pr-<number>` | Pull request builds | `pr-42` |
| `latest` | Latest main branch | `latest` |
| `v<semver>` | Release versions | `v1.2.3` |
| `v<major>.<minor>` | Minor version pointer | `v1.2` |

### Promotion Flow
```
PR Build → Dev Deploy → Integration Tests → Main Merge → Staging Deploy → 
Manual Approval → Production Deploy (Blue-Green)
```

## Resource Constraints

### Kubernetes Resource Requests/Limits
```yaml
# Backend
resources:
  requests:
    cpu: "500m"
    memory: "1Gi"
  limits:
    cpu: "2000m"
    memory: "2Gi"

# Frontend
resources:
  requests:
    cpu: "100m"
    memory: "128Mi"
  limits:
    cpu: "500m"
    memory: "512Mi"
```

### JVM Tuning for Containers
```dockerfile
ENV JAVA_OPTS="-XX:+UseContainerSupport \
  -XX:MaxRAMPercentage=75.0 \
  -XX:InitialRAMPercentage=50.0 \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=100 \
  -XX:+ParallelRefProcEnabled \
  -Djava.security.egd=file:/dev/./urandom"
```

## Build Optimization

### .dockerignore
```dockerignore
# Backend
**/target/
**/.mvn/wrapper/maven-wrapper.jar
**/*.log
**/*.tmp
.idea/
.git/
.github/
docs/
*.md

# Frontend
**/node_modules/
**/dist/
**/.angular/
**/coverage/
.git/
.idea/
*.md
```

### BuildKit Cache Mounts
```dockerfile
# Maven cache
RUN --mount=type=cache,target=/root/.m2 ./mvnw dependency:go-offline -B

# pnpm cache
RUN --mount=type=cache,target=/root/.local/share/pnpm/store pnpm install --frozen-lockfile
```