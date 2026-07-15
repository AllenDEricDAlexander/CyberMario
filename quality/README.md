# CyberMario Browser Quality

This project runs real browser regression tests against the CyberMario
frontend and a backend using the Spring `auto` profile.

## Prerequisites

- JDK 21
- Bun 1.3.14
- a dedicated PostgreSQL Auto database with `vector`, `hstore`, `uuid-ossp`,
  and `pg_trgm` extension support
- a dedicated local Redis database index greater than 1
- credentials allowed to create and drop only approved `auto_*` schemas

Playwright starts `be` and `fe`. It does not start PostgreSQL, Redis, MQ,
or Docker.

## Local Setup

```bash
cd quality
bun install
cp .env.example .env
bunx playwright install chromium
```

Set only Auto environment values in `.env`. The runner refuses a database
without an `auto` boundary, the `public` schema, Redis database 0 or 1,
and missing cleanup authorization.

## Commands

```bash
bun run test:auth
bun run test:auth:headed
bun run test:auth:ui
bun run test:auth:debug
bun run report
```

Every command uses the same Auth specs. Display and debug modes change only
browser presentation.

## Data Lifecycle

The runner recreates the configured PostgreSQL schema and flushes the
configured Redis database before the suite. It runs the same cleanup after
Playwright stops, including after failures. Cleanup failures fail the command.

Local and CI must use different schema and Redis lanes. Do not point this
project at development or production infrastructure.

## Diagnostics

Failure diagnostics are written to:

- `artifacts/process-logs/`
- `playwright-report/`
- `test-results/`

Reports must never contain passwords, JWTs, Cookie values, database dumps, or
Redis dumps.

## CI And Release Gate

The `Quality Gate` workflow is reusable through `workflow_call`. A future
production deployment workflow must call it for the same commit and make its
deployment job depend on the completed gate before deploying that exact
`github.sha`.

Configure the repository secrets and variables listed below before running
the CI gate. The database and Redis resources are pre-provisioned; CI does
not create containers or external services.

Secrets:

```text
AUTO_DB_URL
AUTO_DB_USERNAME
AUTO_DB_PASSWORD
AUTO_REDIS_PASSWORD
AUTO_JWT_SECRET
AUTO_ADMIN_PASSWORD
```

Variables:

```text
AUTO_DB_SCHEMA=auto_ci
AUTO_REDIS_HOST=<auto Redis host>
AUTO_REDIS_PORT=6379
AUTO_REDIS_DATABASE=<dedicated CI Redis database greater than 1>
```
