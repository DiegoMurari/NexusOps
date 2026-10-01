# Contributing to NexusOps

## Development Workflow

### Branching Strategy (GitHub Flow)
- `main` - Production-ready code (protected, requires PR + CI pass)
- `feature/*` - Feature branches from `main`
- `fix/*` - Bug fix branches from `main`
- `release/*` - Release preparation branches (optional)

### Commit Convention (Conventional Commits)
```
<type>(<scope>): <subject>

<body>

<footer>
```

**Types**: `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `chore`, `ci`, `build`, `revert`

**Examples**:
```
feat(iam): add MFA TOTP enrollment endpoint
fix(ticketing): prevent invalid state transition on reopen
docs(adr): add ADR-003 for database strategy
```

### Pull Request Process
1. Create PR from feature/fix branch to `main`
2. Ensure all CI checks pass (lint, test, build, security)
3. Require 1 approval from code owner
4. Squash merge (maintains clean history)
5. Delete branch after merge

## Coding Standards

### Backend (Java 21 / Spring Boot 3.2+)
- **Formatter**: google-java-format (via Spotless Maven plugin)
- **Static Analysis**: Checkstyle, SpotBugs, PMD, ArchUnit
- **Architecture**: Spring Modulith module boundaries enforced in CI
- **Dependencies**: Dependabot weekly updates; no SNAPSHOT in releases
- **Testing**: 80%+ coverage (unit + integration); Testcontainers for IT

### Frontend (Angular 17+ / TypeScript 5.3+)
- **Formatter**: Prettier (single quotes, 2 spaces, trailing commas)
- **Linting**: ESLint + Angular ESLint + TypeScript ESLint
- **Architecture**: Standalone components, Signals, lazy-loaded feature modules
- **Testing**: 80%+ unit coverage (Jest); E2E with Playwright
- **Standards**: Angular Style Guide, RxJS best practices

### General
- **Line length**: 100 chars (Java), 120 chars (TypeScript)
- **No console.log / System.out.println** in committed code
- **Secrets**: Never commit secrets; use `.env.local` (gitignored) for local
- **ADRs**: Document significant architectural decisions in `docs/adr/`

## Quality Gates (CI Pipeline)

| Stage | Tools | Threshold |
|-------|-------|-----------|
| Format | google-java-format, Prettier | Zero violations |
| Lint | Checkstyle, SpotBugs, ESLint | Zero errors |
| Unit Tests | JUnit 5, Jest | ≥80% coverage |
| Integration Tests | Testcontainers, Playwright | All pass |
| Contract Tests | Spring Cloud Contract, Pact | All pass |
| Security Scan | CodeQL, Trivy, Dependency Check | No CRITICAL/HIGH |
| Build | Maven, Angular CLI | Success |
| Docker | Multi-stage, Distroless, Cosign | Signed images |

## Local Development Setup

```bash
# 1. Install tools
# Java 21: sdk install java 21.0.2-tem
# Node 20: fnm install 20
# pnpm: corepack enable && corepack prepare pnpm@latest --activate

# 2. Backend
cd backend
./mvnw spotless:apply  # format code

# 3. Frontend
cd frontend
pnpm install
pnpm run lint:fix
pnpm run format
```

## Pre-commit Hooks (Husky)

```bash
# Installed automatically on `pnpm install` (frontend) or `./mvnw install` (backend)
# Runs: format check, lint, unit tests (staged files only)
```

## Code Review Checklist

- [ ] Solves the stated problem
- [ ] Follows coding standards & architecture
- [ ] Tests added/updated (unit + integration)
- [ ] Documentation updated (ADRs, README, OpenAPI)
- [ ] No breaking changes without migration path
- [ ] Security considerations addressed
- [ ] Performance impact assessed
- [ ] Observability (logs, metrics, traces) added

## Release Process

1. Create `release/x.y.z` branch from `main`
2. Update version in `pom.xml` (backend) and `package.json` (frontend)
3. Generate changelog (`git cliff` or manual)
4. PR to `main` with version bump
5. Tag release: `git tag vx.y.z && git push origin vx.y.z`
6. GitHub Actions builds, signs, and publishes Docker images
7. ArgoCD deploys to staging → manual approval → production

## Issue Reporting

Use GitHub Issues with templates:
- **Bug Report**: Steps to reproduce, expected vs actual, logs
- **Feature Request**: Problem statement, proposed solution, alternatives
- **Security**: Report privately via GitHub Security Advisories

## Code of Conduct

Follow [Contributor Covenant](https://www.contributor-covenant.org/version/2/1/code_of_conduct/). Be respectful, inclusive, and constructive.