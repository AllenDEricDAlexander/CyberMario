# CyberMario

CyberMario is a full-stack AI workspace built around chat agents, persistent Agent Memory, RAG knowledge bases, dynamic
MCP tool management, model-call auditing, RBAC-backed administration, and Clocktower game tooling.

The repository contains a Spring Boot WebFlux backend and a React + TypeScript + Vite admin frontend. For a broader
feature inventory and implementation notes, see [FEATURE_CHECKLIST.md](FEATURE_CHECKLIST.md).

## Features

- AI chat and agent debugging with streaming NDJSON responses and shared chat-workspace UI patterns.
- Agent preset management, conversation audit, run audit, model-call dashboard, and SoulMD profile support.
- Agent Memory for Agent Chat, Agent Debug, and RAG Chat, including user-owned sessions, recent-turn context assembly,
  user-level Markdown long-term memory, extraction audit, archive/restore, and logical deletion.
- Dynamic MCP server and tool policy management for Streamable HTTP and SSE transports.
- RAG knowledge-base, document, ingestion-job, retrieval-lab, chat, feedback, and settings pages.
- Hybrid RAG retrieval with pgvector, keyword search, optional rerank, retrieval traces, and source citations.
- arXiv search tooling with protected background import into the RAG document pipeline.
- Family AI nutrition workflow with scoped administration, health profiles, catalog imports, calculated recipes,
  persisted AI menu review, dish-level confirmation, shopping, budgets, nutrition records, and reports.
- Investment workspace for cryptocurrency USDT perpetual-futures research, reproducible Java backtests, paper accounts,
  risk-controlled simulated trading, and audited Agent analysis/paper-trade decisions.
- Clocktower Phase 1 game support with script data, board validation, room lifecycle, grimoire, player actions, event
  stream, and basic replay.
- RBAC user, role, permission, menu, button, API-rule, role-inheritance, and permission-version management.
- JWT access/refresh token flow, public registration, account settings, encrypted password transport, and frontend auth
  retry handling.
- PostgreSQL + Flyway schema management and Redis-backed RBAC/API/token cache support.

## Tech Stack

| Area          | Technology                                                                         |
|---------------|------------------------------------------------------------------------------------|
| Backend       | Java 21, Spring Boot 3.5, WebFlux, Spring Security, Spring AI Alibaba, JPA, Flyway |
| AI and RAG    | DashScope chat/embedding models, pgvector, Apache Tika, Spring AI vector store     |
| MCP           | Model Context Protocol Java SDK, dynamic remote MCP client registry                |
| Data          | PostgreSQL, Redis                                                                  |
| Frontend      | React 19, TypeScript, Vite, Ant Design, Vitest                                     |
| Observability | Actuator health/info/metrics/prometheus, trace IDs, audit records                  |

## Repository Layout

```text
.
|-- be/                  # Spring Boot backend
|-- fe/                  # React + Vite frontend
|-- quality/             # Playwright browser quality gate
|-- docs/                # Design and implementation notes
|-- ops/                 # Local start/stop helper scripts
|-- FEATURE_CHECKLIST.md # Detailed implemented feature inventory
`-- README.md            # Project entry document
```

## Prerequisites

- JDK 21
- Bun compatible with the frontend toolchain
- PostgreSQL with pgvector available for RAG vector storage
- Redis
- DashScope API key for model calls

The backend defaults to:

- Backend port: `28080`
- Database URL: `jdbc:postgresql://localhost:5432/cyber_mario`
- Redis: `localhost:6379`, database `1`
- Frontend dev port: `5173`

Use environment variables to override local defaults. Deployment environments must set their own database,
Redis, and JWT secrets instead of relying on these local development values. The helper scripts under `ops/`
also read a root `.env` file when present.

Common variables:

```bash
AI_DASHSCOPE_API_KEY=your-api-key
DB_URL=jdbc:postgresql://localhost:5432/cyber_mario
DB_USERNAME=postgres
DB_PASSWORD=postgres
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=your-password
JWT_SECRET=replace-with-a-local-secret-at-least-32-bytes
SERVER_PORT=28080
VITE_BACKEND_PORT=28080
BROWSER_AUTH_COOKIE_SECURE=false
AGENT_MEMORY_CHECKPOINTER_ENABLED=false
```

`AGENT_MEMORY_CHECKPOINTER_ENABLED=true` enables the Spring AI Alibaba PostgreSQL checkpointer for recoverable
ReactAgent graph state. Agent Memory's product source of truth still lives in the Flyway-managed memory tables.

## Local Development

Install frontend dependencies:

```bash
cd fe
bun install
```

Build or run the backend:

```bash
cd be
./mvnw spring-boot:run
```

Run the frontend dev server:

```bash
cd fe
bun run dev
```

Frontend package scripts force Bun runtime with `bun run --bun` while keeping Vite as the dev server and production
build tool.

The Vite dev server proxies `/api` and `/demo` to the backend. The proxy target is resolved from
`VITE_BACKEND_TARGET`, then `VITE_API_BASE_URL`, then `http://localhost:${VITE_BACKEND_PORT || BACKEND_PORT || 28080}`.

Browser clients send `X-Client-Type: browser` and use HttpOnly auth cookies plus `X-XSRF-TOKEN` CSRF protection.
Non-browser clients continue to use `Authorization: Bearer`. Local HTTP development should set
`BROWSER_AUTH_COOKIE_SECURE=false`; production should keep secure cookies enabled.

The repository also includes background helper scripts:

```bash
./ops/be/backend.sh start
./ops/be/backend.sh status
./ops/be/backend.sh stop

./ops/fe/frontend.sh start
./ops/fe/frontend.sh status
./ops/fe/frontend.sh stop
```

Do not run these scripts unless you explicitly want to start local processes.

## Investment Module

Investment is a web-only, paper-trading module for cryptocurrency USDT perpetual futures. It covers shared market-data
storage and revision history, K-line and indicator analysis, six research-report types, immutable dataset snapshots,
fixed Java strategies and reproducible backtests, private workspaces/watchlists/accounts/positions, mandatory risk
checks, margin-ledger accounting, and audited Agent analysis or automatic simulated trading. It has no spot support,
exchange private client, credential storage, live-order endpoint, or real-trading path.

The production market-data provider, subscription, and strategy registries are intentionally empty. Until code supplies
all three, Investment pages expose empty/unavailable states and no ingestion jobs are scheduled. Bitget is not connected
by this delivery. Add integrations through these code-only seams:

- implement the required `MarketDataProvider` capability interfaces and register the adapter as a Spring bean;
- provide selected symbols, capabilities, intervals, price types, schedules, and retention through an
  `InvestmentMarketSubscriptionProvider` bean;
- implement `InvestmentStrategy` as a Spring bean; its declared capabilities and intervals must already be covered by
  the subscription registry;
- change the fixed Investment Agent preset and tool allowlist in code, never through a browser-supplied strategy or
  prompt definition.

The same paper-order facade and risk rules are used for manual, strategy, and Agent-originated intents. Agent trade
decisions do not require per-order user confirmation, but cannot bypass ownership, freshness, position, leverage,
exposure, loss, drawdown, frequency, cooldown, or slippage checks.

## Browser Quality Gate

The root `quality/` project runs real Playwright registration and login
regression tests against the Spring `auto` profile and pre-provisioned
PostgreSQL/Redis Auto resources.

See [quality/README.md](quality/README.md) for environment safety requirements,
local headed/debug commands, CI usage, diagnostics, and cleanup behavior.

## Validation

Backend:

```bash
cd be
./mvnw test
```

The recovered nutrition workflow has a focused persisted-flow acceptance suite:

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false -Dtest=NutritionVerticalFlowTests test
```

IM PostgreSQL contract checks can be run against a disposable PostgreSQL
database:

```bash
cd be
IM_POSTGRES_TEST_URL=jdbc:postgresql://localhost:5432/cyber_mario_im_test \
IM_POSTGRES_TEST_USERNAME=postgres \
IM_POSTGRES_TEST_PASSWORD=postgres \
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ImPostgresContractIT,ImPostgresBehaviorIT test
```

Investment backend and disposable PostgreSQL gates:

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false test

INVESTMENT_POSTGRES_TEST_URL=jdbc:postgresql://localhost:5432/cyber_mario_investment_test \
INVESTMENT_POSTGRES_TEST_USERNAME=postgres \
INVESTMENT_POSTGRES_TEST_PASSWORD=postgres \
./mvnw -Dmaven.build.cache.enabled=false \
  -Dtest=InvestmentPostgresContractIT,InvestmentPostgresPersistenceIT test
```

The PostgreSQL database must be disposable: the tests create and drop an isolated schema and run the full Flyway
chain. H2 validates the fast suite but does not replace PostgreSQL evidence for JSONB, locking, lease fencing, MVCC,
idempotency, and concurrent financial updates. Frontend Investment checks use the standard commands below; bundle
analysis should keep `lightweight-charts` in the lazy Instrument-page chunk.

Frontend:

```bash
cd fe
bun run lint
bun run typecheck
bun run test
bun run build
```

For documentation-only edits, a lightweight whitespace check is usually enough:

```bash
git diff --check
```

## Data and Permissions

- Flyway migrations live under `be/src/main/resources/db/migration` and `be/src/main/resources/db/postgresql`.
- Existing migration files must not be modified after creation.
- RBAC resource synchronization declares menus, buttons, APIs, and preset roles from code and syncs them to the
  database.
- Preset roles append missing grants instead of removing manually adjusted permissions.
- RAG knowledge-base access is enforced separately from RBAC API permissions.
- MCP tools requiring confirmation are not exposed to the runtime agent until a confirmation workflow exists.
- Agent Memory is user-scoped at the service layer: RBAC grants menu/API/button access, while memory services still
  enforce current-user ownership for sessions, messages, long-term memory, versions, and extraction audits.
- Agent Memory persistence is introduced by `V17__create_agent_memory_schema.sql`, covering memory sessions, normalized
  messages, user Markdown long-term memory, version history, and extraction audit records.

## Further Reading

- [FEATURE_CHECKLIST.md](FEATURE_CHECKLIST.md): current feature inventory and implementation notes.
- [fe/README.md](fe/README.md): frontend scripts, proxy behavior, response contracts, and validation commands.
- [docs/im-core-contract.md](docs/im-core-contract.md): IM core facade, realtime, Clocktower adapter, and validation contract.
- [docs/superpowers/specs/2026-07-16-investment-module-design.md](docs/superpowers/specs/2026-07-16-investment-module-design.md):
  Investment architecture, schema, security, paper-trading, quant, and Agent contracts.
- [docs/superpowers/plans/2026-07-16-investment-module.md](docs/superpowers/plans/2026-07-16-investment-module.md): Investment
  implementation and verification plan.
- [docs/rbac-resource-sync-design.md](docs/rbac-resource-sync-design.md): RBAC resource synchronization design.
- [docs/superpowers/specs/2026-06-16-agent-memory-design.md](docs/superpowers/specs/2026-06-16-agent-memory-design.md):
  Agent Memory design.
- [docs/superpowers/plans/2026-06-16-agent-memory.md](docs/superpowers/plans/2026-06-16-agent-memory.md): Agent Memory
  implementation plan.
