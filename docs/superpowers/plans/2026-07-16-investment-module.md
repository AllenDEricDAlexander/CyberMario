# Investment 合约分析、量化与 Agent 模拟交易 Implementation Plan

> **Execution contract:** Only the user's preset subagents may be used. Every implementation
> task below owns one non-overlapping file scope, must be reviewed by the main agent,
> verified with its focused commands, and committed once; read-only gates create evidence only.

**Goal:** 在 CyberMario 中交付独立的 `investment` Web 模块，覆盖加密货币 USDT 永续
合约的传统分析与报告、Java 代码策略与可复现回测、以及受确定性风控约束的 Agent 自动
模拟交易；V1 不接 Bitget、不接实盘、不保存 Tick，也不允许前端定义策略或数据订阅。

**Architecture:** 使用同一 Spring Boot 应用内的模块化单体。平台行情和任务是共享数据，
workspace/report/backtest/paper account/Agent run 是私人数据。Provider、代码订阅、策略、
回测、撮合、费用、滑点、保证金和 Agent preset 都通过窄 SPI 隔离变化点；`PaperTradingFacade`
是所有人工、策略和 Agent 模拟交易的唯一写入口。PostgreSQL 保存事实、revision、任务、
账本和审计，Redis 仅缓存 latest quote。长任务采用持久队列和短事务租约，不在数据库事务中
执行 HTTP、LLM 或回测。

**Tech Stack:** Java 21, Spring Boot 3.5.16, WebFlux, Spring Data JPA, JDBC,
Flyway, PostgreSQL, Redis, Spring AI Alibaba Agent, Ta4j 0.22.6, React 19,
React Router 7, Ant Design 6, Ant Design Charts 2.6.7, TradingView Lightweight
Charts 5.2.0, Bun 1.3.14, Vite 8, Vitest 4

**Approved design:**
[`2026-07-16-investment-module-design.md`](../specs/2026-07-16-investment-module-design.md)

---

## Scope Check

### In scope

- `investment` 后端包、`/api/investment/**` API、React 路由和 RBAC 资源。
- 代码控制的数据 Provider SPI、订阅注册表、持久任务、数据质量和 read-only 平台页。
- 日 K、1 分钟及聚合周期 K 线、last/mark/index 价型、资金费率、合约规格和仓位档位。
- K 线、指标、传统分析、异步报告、自选和 Overview。
- Java 代码策略注册、策略发布快照、逻辑数据快照和确定性合约回测。
- `USDT-FUTURES + PERPETUAL + ISOLATED + ONE_WAY` 模拟账户、订单、成交、资金费、
  保证金、强平、仓位、账本和净值。
- Investment Agent 分析和自动模拟交易；无逐单确认，但每个意图必须同步经过风控。
- H2 通用 schema 验证、真实 PostgreSQL 并发/锁/类型契约测试、前后端完整质量门。

### Out of scope

- Bitget HTTP adapter、Bitget API 实际对接、外部 symbol seed 和任何私有 API key。
- 现货、交割合约、USDC/币本位、全仓、双向持仓、实盘和真实订单状态。
- 前端策略编辑器、策略参数表单、数据订阅配置页、Prompt/工具权限编辑器。
- Tick/订单簿历史、分钟内撮合路径、部分成交、正式 Event Sourcing、微服务和 Python Worker。
- PDF/Excel、移动端、WebSocket 实时推送、无证据的分区/BRIN/fillfactor/autovacuum 调优。

### Production-empty boundaries

- 生产 `InvestmentMarketSubscriptionProvider` 初始不注册任何 symbol；测试源码提供 fixture。
- 生产 `InvestmentStrategyRegistry` 初始不注册私人策略；测试源码提供固定测试策略。
- 市场数据 planner 默认关闭；Investment worker 仍可执行回测、模拟撮合和 Agent 等内部任务。
- 用户后续提供 symbol 或策略时，各自新增 Java-only task/commit，不修改前端契约或历史 migration。

## Locked Implementation Decisions

1. 所有 API 金额、价格、数量、名义价值、比率和指标都以十进制字符串序列化。Java 内部
   使用 `BigDecimal`，React 表单使用 `InputNumber stringMode`，只在图表边界转 `number`。
2. 生产订阅和生产策略为空不是缺陷，而是避免擅自接入数据或发明私人策略的安全边界。
3. 模拟账户创建请求必须同时提交完整风险配置；无隐藏额度默认值，两个交易开关初始关闭。
4. 风控字段单位固定：`*_notional`/`*_amount` 为 USDT 金额，`*_ratio` 为 `[0,1]` 比例，
   `*_bps` 为万分之一；`max_leverage` 为正整数。
5. Intent 通过风控后只创建 `PENDING` order 和 `PAPER_MATCH` job。N+1 已关闭 1m bar 到达后
   才能成交；API/Agent 在此前返回 `PENDING_MATCH`。
6. 日 K、分钟 K、资金费使用同表 SCD2 revision-slot；旧 revision 永不被覆盖，回测按
   `data_as_of` 从 current + history 重放。
7. 数据快照未物化为 Parquet 前，仍被可重跑 snapshot 引用的 1m 范围不允许物理清理。
8. Job claim、heartbeat、complete/retry/fail 都使用短事务和 fencing token；任务主体无事务。
9. 所有资金变更统一锁序：`account -> positions(instrument_id) -> orders(id) -> append facts`。
10. Investment 只读 tools 仅作为 per-run scoped callbacks 创建，服务端绑定 actor/workspace/account/
    `dataAsOf`；绝不进入普通聊天的全局工具表。LLM 运行期没有写工具；decision 校验并审计后，
    Runner 才进入唯一的交易执行路径。崩溃重试可以用同一幂等键再次进入
    `PaperTradingFacade`，但最终只能存在一个 intent、order 和资金效果。
11. 报告创建为异步：创建 `PENDING` report + `REPORT_BUILD` job，详情直接带 evidence。
12. 回测和 Agent run 由前端有限轮询至终态；Market/Portfolio 使用显式刷新，V1 不加 WebSocket。

## Plan-time Technical Corrections

规划评审发现并已回写 approved spec 的强制修正：

- 原“覆盖 K 线并增加 revision”无法重放旧快照，改为 SCD2 revision-slot。
- 原 job 只有超时锁，补 `claim_token`、`lease_expires_at`、`heartbeat_at` 和 fencing。
- 原 Agent 工具边界未阻止普通聊天默认加载领域工具，也允许校验前产生副作用；改为 scoped
  read-only runtime + validated two-phase execution。
- 原风险字段单位不清，改为显式后缀和创建账户时必填。
- 全局 Jackson 忽略未知字段，回测 DTO 必须单独捕获并拒绝未知策略字段。
- 原同步图示可能让 Agent 伪报成交，改为 `PENDING_MATCH` 异步撮合。
- nullable 复合唯一键使用 `NONE` sentinel；初始 H2/PG 共用普通唯一约束，不建部分索引。
- 追加式账本增加全局 idempotency key，账户行维护单调 `ledger_sequence`。

## Dependency Pins

- Backend：在指标任务中只新增 `org.ta4j:ta4j-core:0.22.6`。版本依据为
  [Maven Central](https://central.sonatype.com/artifact/org.ta4j/ta4j-core/0.22.6)。
- Frontend：在 K 线任务中从 `fe/` 执行 `bun add lightweight-charts@5.2.0`，只修改
  `fe/package.json` 和 `fe/bun.lock`。使用官方 React 集成方式，不增加 wrapper；版本和
  attribution 约束见 [npm](https://www.npmjs.com/package/lightweight-charts) 与
  [official React guide](https://tradingview.github.io/lightweight-charts/tutorials/react/advanced)。
- 不新增 Redux、React Query、数据库扩展、消息队列、工作流引擎或 Python 依赖。

## Ownership And Preset Subagents

实施期间只允许下列现有 preset subagent；主 agent 负责派发、审查、验证、逐任务提交和清理：

| Preset | Exclusive ownership |
|---|---|
| `postgres-pro` | 当次 Investment Flyway 文件、schema migration tests、最终 PostgreSQL contract/persistence IT |
| `spring-boot-engineer` | `be/src/main/java/top/egon/mario/investment/**`、对应单元/API 测试、必要配置 |
| `react-specialist` | `fe/src/modules/investment/**`、Investment 路由集成、前端测试和依赖锁文件 |
| `llm-architect` | BE-16/BE-17 的 scoped Agent runtime、结构化输出、tool/eval/audit 边界 |
| `qa-expert` | 阶段验收矩阵、回归和 release gate；默认只读 |
| `code-reviewer` | 每个 Phase 的证据驱动最终 review；默认只读 |

同一共享工作树内并行时，subagent 不自行 commit。主 agent 等待本波全部返回，审查无冲突后
按任务边界逐个 stage、运行验证并 commit。不同任务不得同时修改同一文件；Flyway 永远串行。

Before every commit, the main agent must run:

```bash
git diff --cached --name-only
git diff --cached --check
```

The staged list must be a subset of that task's `Files` section. If another parallel task appears, do not commit until
the scopes are separated. Phase review happens after task commits; any review-driven correction is a new explicit
`FIX-P{phase}-{n}` task/commit owned by the original preset, never an amend or a second commit pretending to be the
original task.

## API Contract Freeze

任何前端任务开始前，相关后端 controller DTO/tests 必须冻结以下契约：

- 列表：统一 `page`、`size`、领域 filter/sort 和项目现有 `PageResult<T>` envelope。
- Instrument detail：必须返回 `availableCapabilities`、`availablePriceTypes`、
  `availableIntervals`、`dataAsOf`、freshness 和 contract spec。
- Candles：按时间升序；包含 OHLCV、`isClosed`、`revision`、`dataAsOf`；服务端限制点数和跨度。
- Report POST：返回 `PENDING/QUEUED` 摘要；detail 直接带 evidence。
- Overview：`GET /api/investment/workspaces/{workspaceId}/overview` 返回摘要，不让前端拼 6 个请求；
  workspace 必填，无 workspace 时前端展示创建/选择空态并不发该请求。
- Backtest：detail + `/trades` + `/events` + `/equity`；equity 已由后端确定性降采样。
- Backtest POST：只接受 `strategyCode/instrumentIds/startTime/endTime`；workspace 来自 path；未知字段报错。
- Trade intent POST：返回 intent、每条 risk result、order/fill summary 和 `PENDING_MATCH` 状态。
- Portfolio fill markers：`GET /paper-accounts/{accountId}/fills` 支持 `instrumentId/from/to/page/size`；
  marker 调用必须提交 instrument/time window，服务端按 `eventTime,id` 稳定排序并限制跨度/大小；
  DTO 包含 `marketBarOpenTime`、side、actionType、orderOrigin、eventType、price、quantity、liquidation，
  且全程 owner-scoped。
- Agent run POST：只接受固定 `presetCode`、`runType`、`instrumentIds`、可选 `accountId`；
  `actorId/workspaceId/dataAsOf/tools` 均由服务端绑定。
- Agent detail/decision：包含 decision -> intent -> risk -> order -> fill 执行摘要及终态状态。
- RBAC：menu/button/API code 由 `InvestmentPermissionCatalog` 和前端常量共同测试锁定。

## Flyway Rules

- 实施每个数据库任务前运行 `rg --files be/src/main/resources/db | sort` 并重新解析 next version。
- 下文 V40-V43 仅代表当前 V39 基线下的预期编号；出现新 migration 时整体顺延未创建文件。
- 四个阶段是四次独立数据库变更，每次恰好一个新 versioned migration 和一个提交。
- 任何已创建/提交 migration 不得修改、重命名、重排、格式化或 checksum repair。
- 初始文件全部放 `be/src/main/resources/db/migration`，同时兼容 H2 PostgreSQL mode 与 PG。
- 初始不创建 repeatable、`db/postgresql` 部分索引、BRIN 或分区；有规模证据后另开新 DB change。

## Preflight Baseline

开始实现前在 `codex/investment-module` 隔离分支/worktree 记录基线；不要启动服务：

```bash
git status --short --branch

cd /Users/mario/SelfProject/CyberMario/be
./mvnw -Dmaven.build.cache.enabled=false test

cd /Users/mario/SelfProject/CyberMario/fe
bun install --frozen-lockfile
bun run lint
bun run typecheck
bun run test
bun run build
```

Expected: worktree clean and all current gates pass. 基线失败只记录准确命令与错误，不把无关修复
混入 Investment task。

## Execution Graph

```text
Preflight + contract freeze
        |
        +-- DB-01 Foundation schema ---- BE-02/04/05 ---- BE-06/07/08
        |          |                         |                  |
        |          +-- BE-01/03/10 ----------+                  +-- FE-01/02/03/04/08
        |
        +-- DB-02 Quant schema -------- BE-09/11 ---- BE-12 ---- FE-05
        |
        +-- DB-03 Paper schema -------- BE-13 ---- BE-14 ---- BE-15 ---- FE-06
        |
        +-- BE-16 scoped Agent runtime -- DB-04 Agent schema -- BE-17
                                                               +-- BE-19 overview -- FE-09
                                                               +-- FE-07 -- FE-11
                                                                            |
                                                                       FE-10 integration
                                                                            |
                         DB-05 + BE-18 -- INT-VERIFY-01 -- QA-01 -- REVIEW-01 -- INT-DOCS-01
```

Safe parallel work is named in each Phase. A downstream task starts only after all listed dependencies are
committed and its API/schema contract is stable.

---
## Phase 1: Market Data, Traditional Analysis, And Reports

### Task DB-01: Create The Foundation Schema

**Owner:** preset `postgres-pro`

**Dependencies:** Preflight only. This task may run in parallel with BE-01 and BE-03, but no other task may
create or edit a migration.

**Files:**

- Create: `be/src/main/resources/db/migration/V40__create_investment_market_research_schema.sql`
  (or the then-current next version)
- Create: `be/src/test/java/top/egon/mario/investment/InvestmentFoundationSchemaMigrationTests.java`

- [ ] **Step 1: Re-resolve the migration version**

```bash
cd /Users/mario/SelfProject/CyberMario
rg --files be/src/main/resources/db | sort
```

Expected: V39 is latest today. If not, replace V40 with exactly the next version before creating the file.

- [ ] **Step 2: Write the failing schema test**

Assert the 18 Phase-1 tables, JSONB/timestamp/numeric mappings, PK/FK/CHECK/unique constraints and index
column order. The test must verify schema from Flyway, not duplicate the DDL in test code.

- [ ] **Step 3: Run the focused test and confirm RED**

```bash
cd /Users/mario/SelfProject/CyberMario/be
./mvnw -Dmaven.build.cache.enabled=false \
  -Dtest=InvestmentFoundationSchemaMigrationTests test
```

Expected: FAIL because the Investment tables do not exist.

- [ ] **Step 4: Add exactly one migration**

Create:

```text
investment_venue
investment_data_source
investment_instrument
investment_instrument_source
investment_contract_spec
investment_position_tier
investment_market_bar_daily
investment_market_bar_intraday
investment_contract_quote_latest
investment_funding_rate
investment_workspace
investment_ingest_cursor
investment_job
investment_data_quality_issue
investment_watchlist
investment_watchlist_item
investment_research_report
investment_report_evidence
```

Required details:

- Bars/funding use `revision`, `revision_slot`, `valid_from`, `valid_to`, `checksum`; PK includes revision;
  natural key + `revision_slot` is unique; CHECK enforces current/history slot consistency.
- `price_type`/`interval_code` participating in cursor uniqueness are NOT NULL with `NONE` sentinel.
- Job has unique `idempotency_key`, `claim_token`, lease/heartbeat, attempts and ordinary
  `(status, available_at, priority, id)` index.
- Time is `TIMESTAMP WITH TIME ZONE`; amount/price `NUMERIC(38,18)`; ratios `NUMERIC(24,12)`.
- Use named OHLC, positive-value, revision and state CHECK constraints.
- Use ordinary unique constraints for business keys. Do not add partial indexes or `(key, deleted)`.
- Internal FK delete behavior is `NO ACTION/RESTRICT`; append-only facts have no soft delete.

- [ ] **Step 5: Verify GREEN and Flyway validate**

```bash
./mvnw -Dmaven.build.cache.enabled=false \
  -Dtest=InvestmentFoundationSchemaMigrationTests test
./mvnw -Dmaven.build.cache.enabled=false -DskipTests compile
```

- [ ] **Step 6: Commit once**

```bash
git add be/src/main/resources/db/migration/V*_create_investment_market_research_schema.sql \
  be/src/test/java/top/egon/mario/investment/InvestmentFoundationSchemaMigrationTests.java
git commit -m "feat(investment): add foundation schema"
```

### Task BE-01: Add Module Contracts And API Primitives

**Owner:** preset `spring-boot-engineer`

**Dependencies:** Preflight only. Safe in parallel with DB-01 and BE-03.

**Files:**

- Create: `be/src/main/java/top/egon/mario/investment/common/InvestmentException.java`
- Create: `be/src/main/java/top/egon/mario/investment/common/InvestmentErrorCode.java`
- Create: `be/src/main/java/top/egon/mario/investment/common/model/*.java`
- Create: `be/src/main/java/top/egon/mario/investment/common/web/ReactiveInvestmentSupport.java`
- Create: `be/src/main/java/top/egon/mario/investment/common/web/InvestmentDecimalCodec.java`
- Create: `be/src/main/java/top/egon/mario/investment/config/InvestmentProperties.java`
- Modify: `be/src/main/java/top/egon/mario/config/GlobalExceptionHandler.java`
- Create: `be/src/test/java/top/egon/mario/investment/common/InvestmentCommonContractTests.java`
- Create: `be/src/test/java/top/egon/mario/investment/common/web/ReactiveInvestmentSupportTests.java`
- Create: `be/src/test/java/top/egon/mario/config/GlobalExceptionHandlerInvestmentTests.java`
- Create: `be/src/test/java/top/egon/mario/investment/InvestmentModuleBoundaryTests.java`

- [ ] **Step 1: Write RED tests for common contracts**

Cover exact enum wire values for product/contract/price type/interval/capability/job/margin/position/order/
intent/run status, strict decimal parsing/serialization, WebFlux blocking dispatch, error mapping and package
boundaries. `InvestmentProperties.marketDataPlannerEnabled` defaults to false.

- [ ] **Step 2: Run focused RED tests**

```bash
cd /Users/mario/SelfProject/CyberMario/be
./mvnw -Dmaven.build.cache.enabled=false \
  -Dtest=InvestmentCommonContractTests,ReactiveInvestmentSupportTests,GlobalExceptionHandlerInvestmentTests,InvestmentModuleBoundaryTests \
  test
```

- [ ] **Step 3: Implement the smallest contracts**

Follow `ReactiveNutritionSupport` and the current exception envelope. Do not expose JPA entities. Do not add
generic abstractions outside `investment`; only `GlobalExceptionHandler` receives the new exception mapping.

- [ ] **Step 4: Verify and commit**

```bash
./mvnw -Dmaven.build.cache.enabled=false \
  -Dtest=InvestmentCommonContractTests,ReactiveInvestmentSupportTests,GlobalExceptionHandlerInvestmentTests,InvestmentModuleBoundaryTests \
  test
./mvnw -Dmaven.build.cache.enabled=false -DskipTests compile
git add be/src/main/java/top/egon/mario/investment/common \
  be/src/main/java/top/egon/mario/investment/config \
  be/src/main/java/top/egon/mario/config/GlobalExceptionHandler.java \
  be/src/test/java/top/egon/mario/investment/common \
  be/src/test/java/top/egon/mario/investment/InvestmentModuleBoundaryTests.java \
  be/src/test/java/top/egon/mario/config/GlobalExceptionHandlerInvestmentTests.java
git commit -m "feat(investment): add backend module contracts"
```

### Task BE-02: Add RBAC, Workspace, Watchlists, And Ownership

**Owner:** preset `spring-boot-engineer`

**Dependencies:** DB-01 and BE-01.

**Files:**

- Create: `be/src/main/java/top/egon/mario/investment/bootstrap/InvestmentPermissionCatalog.java`
- Create: `be/src/main/java/top/egon/mario/investment/bootstrap/InvestmentRbacResourceProvider.java`
- Create: `be/src/main/java/top/egon/mario/investment/common/access/InvestmentAccessService.java`
- Create: `be/src/main/java/top/egon/mario/investment/research/po/InvestmentWorkspacePo.java`
- Create: `be/src/main/java/top/egon/mario/investment/research/po/InvestmentWatchlistPo.java`
- Create: `be/src/main/java/top/egon/mario/investment/research/po/InvestmentWatchlistItemPo.java`
- Create: `be/src/main/java/top/egon/mario/investment/research/repository/*.java`
- Create: `be/src/main/java/top/egon/mario/investment/research/service/InvestmentWorkspaceService.java`
- Create: `be/src/main/java/top/egon/mario/investment/research/service/InvestmentWatchlistService.java`
- Create: `be/src/main/java/top/egon/mario/investment/research/web/InvestmentWorkspaceController.java`
- Create: `be/src/main/java/top/egon/mario/investment/research/web/dto/*.java`
- Create: `be/src/test/java/top/egon/mario/investment/bootstrap/InvestmentRbacResourceProviderTests.java`
- Create: `be/src/test/java/top/egon/mario/investment/common/access/InvestmentAccessServiceTests.java`
- Create: `be/src/test/java/top/egon/mario/investment/research/InvestmentWorkspaceServiceTests.java`
- Create: `be/src/test/java/top/egon/mario/investment/research/InvestmentWorkspaceControllerTests.java`

- [ ] **Step 1: Lock permission and owner contracts in failing tests**

Cover menu/API/button codes, one-owner workspace creation, same-owner name uniqueness, watchlist ownership,
cross-user 403/not-found behavior, and platform-admin not automatically reading private content.

- [ ] **Step 2: Implement using existing module patterns**

Mirror `NutritionRbacResourceProvider` and `NutritionAccessService`. Every private repository query includes
owner/workspace scope; Controller validation alone is insufficient. Soft-delete recovery reactivates the same
business key rather than inserting a duplicate.

- [ ] **Step 3: Verify and commit**

```bash
cd /Users/mario/SelfProject/CyberMario/be
./mvnw -Dmaven.build.cache.enabled=false \
  -Dtest=InvestmentRbacResourceProviderTests,InvestmentAccessServiceTests,InvestmentWorkspaceServiceTests,InvestmentWorkspaceControllerTests \
  test
git add be/src/main/java/top/egon/mario/investment/bootstrap \
  be/src/main/java/top/egon/mario/investment/common/access \
  be/src/main/java/top/egon/mario/investment/research \
  be/src/test/java/top/egon/mario/investment/bootstrap/InvestmentRbacResourceProviderTests.java \
  be/src/test/java/top/egon/mario/investment/common/access/InvestmentAccessServiceTests.java \
  be/src/test/java/top/egon/mario/investment/research/InvestmentWorkspaceServiceTests.java \
  be/src/test/java/top/egon/mario/investment/research/InvestmentWorkspaceControllerTests.java
git commit -m "feat(investment): add workspace access and rbac"
```

### Task BE-03: Add Provider And Code Subscription SPIs

**Owner:** preset `spring-boot-engineer`

**Dependencies:** BE-01 only. Safe in parallel with DB-01/BE-02.

**Files:**

- Create: `be/src/main/java/top/egon/mario/investment/marketdata/provider/ContractMetadataProvider.java`
- Create: `be/src/main/java/top/egon/mario/investment/marketdata/provider/ContractTickerProvider.java`
- Create: `be/src/main/java/top/egon/mario/investment/marketdata/provider/ContractCandleProvider.java`
- Create: `be/src/main/java/top/egon/mario/investment/marketdata/provider/FundingRateProvider.java`
- Create: `be/src/main/java/top/egon/mario/investment/marketdata/provider/PositionTierProvider.java`
- Create: `be/src/main/java/top/egon/mario/investment/marketdata/provider/ProviderRegistry.java`
- Create: `be/src/main/java/top/egon/mario/investment/marketdata/provider/model/*.java`
- Create: `be/src/main/java/top/egon/mario/investment/marketdata/subscription/*.java`
- Create: `be/src/test/java/top/egon/mario/investment/marketdata/provider/InvestmentProviderRegistryTests.java`
- Create: `be/src/test/java/top/egon/mario/investment/marketdata/provider/InvestmentProviderModelTests.java`
- Create: `be/src/test/java/top/egon/mario/investment/marketdata/subscription/InvestmentMarketSubscriptionRegistryTests.java`

- [ ] **Step 1: Test registration, normalization and rejection boundaries**

Reject duplicate provider code, duplicate subscription key, unsupported capability/price type/interval and any
runtime request outside the code registry. Verify the production registry is empty and test fixtures live only in
`src/test`.

- [ ] **Step 2: Implement narrow Adapter/Strategy contracts**

Provider DTOs contain normalized identifiers, UTC timestamps and `BigDecimal`; they never leak exchange DTOs.
Do not implement Bitget clients, schedules, symbols or database/frontend subscription CRUD.

- [ ] **Step 3: Verify and commit**

```bash
cd /Users/mario/SelfProject/CyberMario/be
./mvnw -Dmaven.build.cache.enabled=false \
  -Dtest=InvestmentProviderRegistryTests,InvestmentProviderModelTests,InvestmentMarketSubscriptionRegistryTests \
  test
git add be/src/main/java/top/egon/mario/investment/marketdata \
  be/src/test/java/top/egon/mario/investment/marketdata
git commit -m "feat(investment): add market provider and subscription spis"
```

### Task BE-04: Add Market Persistence Adapters And As-Of Reads

**Owner:** preset `spring-boot-engineer`, reviewed by preset `postgres-pro`

**Dependencies:** DB-01, BE-01 and BE-03.

**Files:**

- Create: `be/src/main/java/top/egon/mario/investment/marketdata/po/*.java`
- Create: `be/src/main/java/top/egon/mario/investment/marketdata/repository/*.java`
- Create: `be/src/main/java/top/egon/mario/investment/marketdata/repository/jdbc/MarketBarJdbcRepository.java`
- Create: `be/src/main/java/top/egon/mario/investment/marketdata/repository/jdbc/FundingRateJdbcRepository.java`
- Create: `be/src/main/java/top/egon/mario/investment/marketdata/repository/jdbc/ContractQuoteJdbcRepository.java`
- Create: `be/src/main/java/top/egon/mario/investment/marketdata/repository/jdbc/model/*.java`
- Create: `be/src/test/java/top/egon/mario/investment/marketdata/InvestmentPersistenceMappingTests.java`
- Create: `be/src/test/java/top/egon/mario/investment/marketdata/InvestmentMarketDataJdbcRepositoryTests.java`
- Create: `be/src/test/java/top/egon/mario/investment/marketdata/InvestmentAsOfMarketDataRepositoryTests.java`

- [ ] **Step 1: Write mapping and revision tests**

Cover ordinary PO audit fields, high-volume JDBC mappings, same-checksum no-op, revision transition, current read,
historical `data_as_of` read and deterministic ordering/paging.

- [ ] **Step 2: Implement persistence split**

Use JPA only for venue/source/instrument/spec/tier/cursor/quality. Bars, funding and latest quote use JDBC and
batch operations. Revision write transaction locks the cursor, moves old current to historical slot, inserts new
current, then updates cursor/quality. Do not create composite-ID JPA entities for high-volume tables.

- [ ] **Step 3: Verify and commit**

```bash
cd /Users/mario/SelfProject/CyberMario/be
./mvnw -Dmaven.build.cache.enabled=false \
  -Dtest=InvestmentPersistenceMappingTests,InvestmentMarketDataJdbcRepositoryTests,InvestmentAsOfMarketDataRepositoryTests \
  test
git add be/src/main/java/top/egon/mario/investment/marketdata \
  be/src/test/java/top/egon/mario/investment/marketdata
git commit -m "feat(investment): add market persistence adapters"
```

### Task BE-05: Add The Durable Fenced Job Runtime

**Owner:** preset `spring-boot-engineer`, reviewed by preset `postgres-pro`

**Dependencies:** DB-01 and BE-01.

**Files:**

- Create: `be/src/main/java/top/egon/mario/investment/common/job/po/InvestmentJobPo.java`
- Create: `be/src/main/java/top/egon/mario/investment/common/job/repository/InvestmentJobRepository.java`
- Create: `be/src/main/java/top/egon/mario/investment/common/job/*.java`
- Modify: `be/src/main/resources/application.yaml`
- Modify: `be/src/test/resources/application.yaml`
- Create: `be/src/test/java/top/egon/mario/investment/common/job/InvestmentJobRuntimeTests.java`
- Create: `be/src/test/java/top/egon/mario/investment/common/job/InvestmentJobTransactionBoundaryTests.java`
- Create: `be/src/test/java/top/egon/mario/investment/common/job/InvestmentJobRetryTests.java`
- Create: `be/src/test/java/top/egon/mario/investment/common/job/InvestmentJobDeferTests.java`

- [ ] **Step 1: Write RED lifecycle and transaction tests**

Cover idempotent enqueue, claim token issuance, no overlapping claims, heartbeat, lease recovery, stale-token
completion rejection, retry/backoff, terminal failure and handler execution outside an active transaction.
`defer(nextAvailableAt)` must match the fencing token, return the job to pending without increasing attempts and be
distinguishable from dependency retry/failure.

- [ ] **Step 2: Implement three explicit transaction seams**

`InvestmentJobClaimService`, `InvestmentJobExecutionService` and `InvestmentJobCompletionService` must be
separate Spring beans. Claim/heartbeat/complete are short transactions; HTTP/LLM/backtest handler body is not.
Use JDBC `INSERT ... ON CONFLICT` for enqueue instead of catching a unique violation inside a doomed transaction.
Completion service exposes fenced complete/retry/fail/defer transitions. Disable automatic runner in test profile;
leave market planner disabled by default.

- [ ] **Step 3: Verify and commit**

```bash
cd /Users/mario/SelfProject/CyberMario/be
./mvnw -Dmaven.build.cache.enabled=false \
  -Dtest=InvestmentJobRuntimeTests,InvestmentJobTransactionBoundaryTests,InvestmentJobRetryTests,InvestmentJobDeferTests \
  test
git add be/src/main/java/top/egon/mario/investment/common/job \
  be/src/main/resources/application.yaml be/src/test/resources/application.yaml \
  be/src/test/java/top/egon/mario/investment/common/job
git commit -m "feat(investment): add durable job runtime"
```

### Task BE-06: Add Market Ingestion, Quality, Quote Cache, And Retention

**Owner:** preset `spring-boot-engineer`

**Dependencies:** BE-03, BE-04 and BE-05.

**Files:**

- Create: `be/src/main/java/top/egon/mario/investment/marketdata/ingest/*.java`
- Create: `be/src/main/java/top/egon/mario/investment/marketdata/ingest/handler/*.java`
- Create: `be/src/main/java/top/egon/mario/investment/marketdata/quality/*.java`
- Create: `be/src/main/java/top/egon/mario/investment/marketdata/service/QuoteCacheService.java`
- Create: `be/src/main/java/top/egon/mario/investment/marketdata/service/MarketDataRetentionCandidateService.java`
- Create: `be/src/main/java/top/egon/mario/investment/marketdata/job/InvestmentMarketJobPlanner.java`
- Create: `be/src/main/java/top/egon/mario/investment/marketdata/event/InvestmentMarketDataCommittedEvent.java`
- Create: `be/src/test/java/top/egon/mario/investment/marketdata/InvestmentMarketDataIngestTests.java`
- Create: `be/src/test/java/top/egon/mario/investment/marketdata/InvestmentMarketDataQualityTests.java`
- Create: `be/src/test/java/top/egon/mario/investment/marketdata/InvestmentMarketDataRetentionCandidateTests.java`
- Create: `be/src/test/java/top/egon/mario/investment/marketdata/InvestmentMarketJobPlannerTests.java`
- Create: `be/src/test/java/top/egon/mario/investment/marketdata/InvestmentQuoteCacheTests.java`

- [ ] **Step 1: Write Template Method and failure-path tests**

Cover subscription revalidation, provider timeout, invalid OHLC, gap/duplicate/revision/stale/missing capability
issues, page retries, cursor atomicity and Redis/event-after-commit behavior. Planner tests cover schedule expansion,
restart/concurrent ticks deduplicating to one job, and strict no-op when disabled or the production registry is empty.
Retention tests only calculate two-year candidate ranges and prove physical deletion remains disabled in Phase 1.

- [ ] **Step 2: Implement handlers**

Implement contract metadata, tier, candle, quote, funding and quality-scan handlers plus the code-schedule planner.
Fetch/normalize
outside transactions; one normalized page writes revision/cursor/quality in a short transaction; update Redis only
after commit and then publish a sanitized market-data-committed event. Candidate retention does not query Quant tables
or delete rows; snapshot-aware protection and physical deletion are added only in BE-11. No handler may turn missing
mark/funding/tier data into zero.

- [ ] **Step 3: Verify and commit**

```bash
cd /Users/mario/SelfProject/CyberMario/be
./mvnw -Dmaven.build.cache.enabled=false \
  -Dtest=InvestmentMarketDataIngestTests,InvestmentMarketDataQualityTests,InvestmentMarketDataRetentionCandidateTests,InvestmentMarketJobPlannerTests,InvestmentQuoteCacheTests \
  test
git add be/src/main/java/top/egon/mario/investment/marketdata \
  be/src/test/java/top/egon/mario/investment/marketdata
git commit -m "feat(investment): add market ingestion and quality pipeline"
```

### Task BE-07: Expose Market, Platform, And Overview APIs

**Owner:** preset `spring-boot-engineer`

**Dependencies:** BE-02, BE-04, BE-05 and BE-06.

**Files:**

- Create: `be/src/main/java/top/egon/mario/investment/marketdata/query/InvestmentMarketQueryService.java`
- Create: `be/src/main/java/top/egon/mario/investment/marketdata/query/InvestmentPlatformQueryService.java`
- Create: `be/src/main/java/top/egon/mario/investment/overview/InvestmentOverviewQueryService.java`
- Create: `be/src/main/java/top/egon/mario/investment/overview/InvestmentOverviewSectionContributor.java`
- Create: `be/src/main/java/top/egon/mario/investment/overview/MarketOverviewSectionContributor.java`
- Create: `be/src/main/java/top/egon/mario/investment/marketdata/web/InvestmentMarketController.java`
- Create: `be/src/main/java/top/egon/mario/investment/marketdata/web/InvestmentPlatformController.java`
- Create: `be/src/main/java/top/egon/mario/investment/overview/InvestmentOverviewController.java`
- Create: `be/src/main/java/top/egon/mario/investment/marketdata/web/dto/*.java`
- Create: `be/src/main/java/top/egon/mario/investment/overview/dto/*.java`
- Create: `be/src/test/java/top/egon/mario/investment/marketdata/InvestmentMarketQueryServiceTests.java`
- Create: `be/src/test/java/top/egon/mario/investment/marketdata/InvestmentMarketControllerTests.java`
- Create: `be/src/test/java/top/egon/mario/investment/marketdata/InvestmentPlatformControllerTests.java`
- Create: `be/src/test/java/top/egon/mario/investment/overview/InvestmentOverviewControllerTests.java`

- [ ] **Step 1: Freeze API DTOs in failing tests**

Test decimal strings, fixed sort/pagination, capabilities/freshness, candle max points and ascending order, platform
subscription read-only behavior, admin retry/resolve permissions and private overview ownership.

- [ ] **Step 2: Implement query projections**

Use dedicated query DTOs/SQL to avoid N+1. Query never triggers data ingestion and never accepts external symbol.
Overview selects one server cutoff and passes it with actor/workspace to an ordered contributor list. BE-07 supplies
the market/data-warning contributor and a stable response shape; missing later contributors return explicit unavailable
sections until their phases are installed.

- [ ] **Step 3: Verify and commit**

```bash
cd /Users/mario/SelfProject/CyberMario/be
./mvnw -Dmaven.build.cache.enabled=false \
  -Dtest=InvestmentMarketQueryServiceTests,InvestmentMarketControllerTests,InvestmentPlatformControllerTests,InvestmentOverviewControllerTests \
  test
git add be/src/main/java/top/egon/mario/investment/marketdata \
  be/src/main/java/top/egon/mario/investment/overview \
  be/src/test/java/top/egon/mario/investment/marketdata/InvestmentMarketQueryServiceTests.java \
  be/src/test/java/top/egon/mario/investment/marketdata/InvestmentMarketControllerTests.java \
  be/src/test/java/top/egon/mario/investment/marketdata/InvestmentPlatformControllerTests.java \
  be/src/test/java/top/egon/mario/investment/overview/InvestmentOverviewControllerTests.java
git commit -m "feat(investment): expose market and platform apis"
```

### Task BE-08: Add Indicators And Asynchronous Research Reports

**Owner:** preset `spring-boot-engineer`

**Dependencies:** BE-02, BE-04, BE-05 and BE-07.

**Files:**

- Modify: `be/pom.xml`
- Create: `be/src/main/java/top/egon/mario/investment/research/indicator/*.java`
- Create: `be/src/main/java/top/egon/mario/investment/research/report/*.java`
- Create: `be/src/main/java/top/egon/mario/investment/research/report/generator/MarketOverviewReportGenerator.java`
- Create: `be/src/main/java/top/egon/mario/investment/research/report/generator/InstrumentAnalysisReportGenerator.java`
- Create: `be/src/main/java/top/egon/mario/investment/research/po/InvestmentResearchReportPo.java`
- Create: `be/src/main/java/top/egon/mario/investment/research/po/InvestmentReportEvidencePo.java`
- Create: `be/src/main/java/top/egon/mario/investment/research/repository/InvestmentResearchReportRepository.java`
- Create: `be/src/main/java/top/egon/mario/investment/research/repository/InvestmentReportEvidenceRepository.java`
- Create: `be/src/main/java/top/egon/mario/investment/research/web/InvestmentResearchController.java`
- Create: `be/src/main/java/top/egon/mario/investment/research/web/dto/*.java`
- Create: `be/src/test/java/top/egon/mario/investment/research/InvestmentIndicatorServiceTests.java`
- Create: `be/src/test/java/top/egon/mario/investment/research/InvestmentReportServiceTests.java`
- Create: `be/src/test/java/top/egon/mario/investment/research/InvestmentResearchControllerTests.java`
- Create: `be/src/test/java/top/egon/mario/investment/research/MarketResearchReportGeneratorTests.java`

- [ ] **Step 1: Add RED indicator/report tests**

Cover closed/as-of-only bars, missing capability rejection, deterministic indicator values, queued report creation,
job retry, immutable report version, evidence ownership and Markdown without raw HTML.
BE-08 owns only `MARKET_OVERVIEW` and `INSTRUMENT_ANALYSIS`; tests prove all deterministic numbers come from market/
indicator services and that evidence, cutoff and owner scope are immutable.

- [ ] **Step 2: Pin Ta4j and implement adapters**

Add only `ta4j-core:0.22.6`. `Ta4jIndicatorAdapter` converts internal bars; Ta4j types do not leak into APIs.
Report generator registry is Strategy pattern for fixed report types. Every report persists `data_as_of`, input hash,
indicator snapshot and evidence; later data cannot mutate it.
The registry auto-discovers later domain generators without central edits; missing generator returns an explicit
unsupported-capability result until its owning Phase is installed.

- [ ] **Step 3: Verify and commit**

```bash
cd /Users/mario/SelfProject/CyberMario/be
./mvnw -Dmaven.build.cache.enabled=false \
  -Dtest=InvestmentIndicatorServiceTests,InvestmentReportServiceTests,InvestmentResearchControllerTests,MarketResearchReportGeneratorTests \
  test
./mvnw -Dmaven.build.cache.enabled=false -DskipTests compile
git add be/pom.xml be/src/main/java/top/egon/mario/investment/research \
  be/src/test/java/top/egon/mario/investment/research
git commit -m "feat(investment): add indicators and research reports"
```

### Task FE-01: Add Investment Workspace Foundation

**Owner:** preset `react-specialist`

**Dependencies:** BE-02 permission/workspace DTOs and BE-07 common market DTOs are committed.

**Files:**

- Create: `fe/src/modules/investment/InvestmentWorkspaceLayout.tsx`
- Create: `fe/src/modules/investment/InvestmentWorkspaceLayout.test.tsx`
- Create: `fe/src/modules/investment/investmentPermissionCodes.ts`
- Create: `fe/src/modules/investment/investment.css`
- Create: `fe/src/modules/investment/types/investmentCommonTypes.ts`
- Create: `fe/src/modules/investment/types/investmentWorkspaceTypes.ts`
- Create: `fe/src/modules/investment/services/investmentWorkspaceService.ts`
- Create: `fe/src/modules/investment/services/investmentWorkspaceService.test.ts`
- Create: `fe/src/modules/investment/hooks/useInvestmentWorkspace.tsx`
- Create: `fe/src/modules/investment/hooks/useInvestmentWorkspace.test.tsx`
- Create: `fe/src/modules/investment/components/InvestmentAsyncState.tsx`
- Create: `fe/src/modules/investment/components/InvestmentAsyncState.test.tsx`
- Create: `fe/src/modules/investment/components/InvestmentDecimalText.tsx`
- Create: `fe/src/modules/investment/components/InvestmentDecimalText.test.tsx`
- Create: `fe/src/modules/investment/components/InvestmentWorkspaceSelect.tsx`
- Create: `fe/src/modules/investment/components/InvestmentWorkspaceSelect.test.tsx`
- Create: `fe/src/modules/investment/components/WorkspaceCreateDrawer.tsx`
- Create: `fe/src/modules/investment/components/WorkspaceCreateDrawer.test.tsx`
- Create: `fe/src/modules/investment/test/renderInvestmentPage.tsx`

- [ ] **Step 1: Write RED service/context/layout tests**

Cover API envelope, decimal text preservation, create/select workspace, empty/loading/error states, permission codes,
workspace change clearing the current paper account and stale-request responses being ignored.

- [ ] **Step 2: Implement local state ownership**

Provider owns only workspace list/current workspace/current paper account and create/refresh status. Do not add a
global state library. Market/Instrument can render without workspace; Overview and other private tabs show a clear
create/select state and do not call workspace-scoped APIs until selection exists.
Use generation id or mounted guard because `requestJson` has no `AbortSignal`.

- [ ] **Step 3: Verify and commit**

```bash
cd /Users/mario/SelfProject/CyberMario/fe
bun run test -- investmentWorkspaceService.test.ts InvestmentWorkspaceLayout.test.tsx \
  useInvestmentWorkspace.test.tsx InvestmentAsyncState.test.tsx InvestmentDecimalText.test.tsx \
  InvestmentWorkspaceSelect.test.tsx WorkspaceCreateDrawer.test.tsx
bun run typecheck
git add fe/src/modules/investment/InvestmentWorkspaceLayout.tsx \
  fe/src/modules/investment/InvestmentWorkspaceLayout.test.tsx \
  fe/src/modules/investment/investmentPermissionCodes.ts fe/src/modules/investment/investment.css \
  fe/src/modules/investment/types/investmentCommonTypes.ts \
  fe/src/modules/investment/types/investmentWorkspaceTypes.ts \
  fe/src/modules/investment/services/investmentWorkspaceService.ts \
  fe/src/modules/investment/services/investmentWorkspaceService.test.ts \
  fe/src/modules/investment/hooks fe/src/modules/investment/components/InvestmentAsyncState* \
  fe/src/modules/investment/components/InvestmentDecimalText* \
  fe/src/modules/investment/components/InvestmentWorkspaceSelect* \
  fe/src/modules/investment/components/WorkspaceCreateDrawer* \
  fe/src/modules/investment/test/renderInvestmentPage.tsx
git commit -m "feat(fe): add investment workspace foundation"
```

### Task FE-02: Add The Contract Market Page

**Owner:** preset `react-specialist`

**Dependencies:** FE-01 and BE-07.

**Files:**

- Create: `fe/src/modules/investment/types/investmentMarketTypes.ts`
- Create: `fe/src/modules/investment/services/investmentMarketService.ts`
- Create: `fe/src/modules/investment/services/investmentMarketService.test.ts`
- Create: `fe/src/modules/investment/market/InvestmentMarketPage.tsx`
- Create: `fe/src/modules/investment/market/InvestmentMarketPage.test.tsx`
- Create: `fe/src/modules/investment/market/investmentMarketColumns.tsx`
- Create: `fe/src/modules/investment/market/investmentMarketColumns.test.tsx`

- [ ] **Step 1: Write RED list/filter/link/watchlist tests**

Cover server pagination/sort/filter, no subscribed instruments, freshness/capability display, decimal strings,
keyboard-accessible instrument links and add-to-watchlist disabled with an explanation when no workspace exists.

- [ ] **Step 2: Implement without subscription controls**

The API result is the only market universe. Do not render symbol/interval/price-type subscription inputs. Use a
real `Link` or named button for detail navigation; row click alone is not sufficient.

- [ ] **Step 3: Verify and commit**

```bash
cd /Users/mario/SelfProject/CyberMario/fe
bun run test -- investmentMarketService.test.ts InvestmentMarketPage.test.tsx investmentMarketColumns.test.tsx
bun run typecheck
git add fe/src/modules/investment/types/investmentMarketTypes.ts \
  fe/src/modules/investment/services/investmentMarketService.ts \
  fe/src/modules/investment/services/investmentMarketService.test.ts \
  fe/src/modules/investment/market
git commit -m "feat(fe): add investment contract market"
```

### Task FE-03: Add Instrument Detail And Lightweight Charts

**Owner:** preset `react-specialist`

**Dependencies:** FE-02, BE-07 and BE-08. Safe in parallel with FE-04 and FE-08 after shared market types and all
Instrument/indicator API contracts are committed.

**Files:**

- Modify: `fe/package.json`
- Modify: `fe/bun.lock`
- Modify: `fe/src/modules/investment/types/investmentMarketTypes.ts`
- Modify: `fe/src/modules/investment/services/investmentMarketService.ts`
- Modify: `fe/src/modules/investment/services/investmentMarketService.test.ts`
- Create: `fe/src/modules/investment/instrument/InvestmentInstrumentPage.tsx`
- Create: `fe/src/modules/investment/instrument/InvestmentInstrumentPage.test.tsx`
- Create: `fe/src/modules/investment/instrument/InvestmentKlinePanel.tsx`
- Create: `fe/src/modules/investment/instrument/InvestmentKlinePanel.test.tsx`
- Create: `fe/src/modules/investment/instrument/investmentChartMappers.ts`
- Create: `fe/src/modules/investment/instrument/investmentChartMappers.test.ts`
- Create: `fe/src/modules/investment/components/InvestmentCandlestickChart.tsx`
- Create: `fe/src/modules/investment/components/InvestmentCandlestickChart.test.tsx`

- [ ] **Step 1: Add the exact dependency**

```bash
cd /Users/mario/SelfProject/CyberMario/fe
bun add lightweight-charts@5.2.0
```

Expected diff: only `package.json` and `bun.lock` before component files are added.

- [ ] **Step 2: Write RED mapper/lifecycle/race tests**

First freeze service contracts for Instrument detail, quote, candles, funding, tiers and indicators. Cover decimal-to-
chart conversion at the boundary, UTC sort, closed bars, route-param changes, price/interval/range changes, stale
response suppression, optional-panel failure, chart created once, `setData` on update, resize and `remove()` on unmount.

- [ ] **Step 3: Implement the official direct React integration**

Keep chart/series refs in `useRef`; do not recreate on every render. Quote/candle is primary state; funding/tier/
indicator panels fail independently. Provide an accessible text summary and data-table fallback. Keep required
TradingView attribution/logo visible; do not add an unofficial wrapper.

- [ ] **Step 4: Verify and commit**

```bash
bun run test -- investmentMarketService.test.ts InvestmentCandlestickChart.test.tsx investmentChartMappers.test.ts \
  InvestmentKlinePanel.test.tsx InvestmentInstrumentPage.test.tsx
bun run typecheck
bun run build
git add fe/package.json fe/bun.lock fe/src/modules/investment/instrument \
  fe/src/modules/investment/components/InvestmentCandlestickChart* \
  fe/src/modules/investment/types/investmentMarketTypes.ts \
  fe/src/modules/investment/services/investmentMarketService.ts \
  fe/src/modules/investment/services/investmentMarketService.test.ts
git commit -m "feat(fe): add investment contract charts"
```

### Task FE-04: Add Traditional Research And Reports

**Owner:** preset `react-specialist`

**Dependencies:** FE-01 and BE-08. Safe in parallel with FE-03 and FE-08.

**Files:**

- Create: `fe/src/modules/investment/types/investmentResearchTypes.ts`
- Create: `fe/src/modules/investment/services/investmentResearchService.ts`
- Create: `fe/src/modules/investment/services/investmentResearchService.test.ts`
- Create: `fe/src/modules/investment/research/InvestmentResearchPage.tsx`
- Create: `fe/src/modules/investment/research/InvestmentResearchPage.test.tsx`
- Create: `fe/src/modules/investment/research/InvestmentReportDrawer.tsx`
- Create: `fe/src/modules/investment/research/InvestmentReportDrawer.test.tsx`
- Create: `fe/src/modules/investment/research/InvestmentReportFilters.tsx`
- Create: `fe/src/modules/investment/research/InvestmentReportFilters.test.tsx`

- [ ] **Step 1: Write RED queue/version/evidence tests**

Cover queued report creation, terminal/failure status display, page-owned list, drawer-owned detail, report version,
`dataAsOf`, evidence, missing capability vs API error and workspace switch race.
Filter coverage includes all six fixed types: market, instrument, strategy, backtest, portfolio and Agent; a type with
no generator/data is an explicit empty state rather than a hidden option or error.

- [ ] **Step 2: Implement safe report rendering**

Reuse existing `react-markdown` + `remark-gfm`; do not enable raw HTML. Load detail by report id in the drawer.
Never hide the evidence/data cutoff behind a tooltip only.

- [ ] **Step 3: Verify and commit**

```bash
cd /Users/mario/SelfProject/CyberMario/fe
bun run test -- investmentResearchService.test.ts InvestmentResearchPage.test.tsx InvestmentReportDrawer.test.tsx \
  InvestmentReportFilters.test.tsx
bun run typecheck
git add fe/src/modules/investment/types/investmentResearchTypes.ts \
  fe/src/modules/investment/services/investmentResearchService* \
  fe/src/modules/investment/research
git commit -m "feat(fe): add investment research reports"
```

### Task FE-08: Add Read-only Platform Data Monitoring

**Owner:** preset `react-specialist`

**Dependencies:** FE-01 and BE-07. Safe in parallel with FE-03 and FE-04.

**Files:**

- Create: `fe/src/modules/investment/types/investmentPlatformTypes.ts`
- Create: `fe/src/modules/investment/services/investmentPlatformService.ts`
- Create: `fe/src/modules/investment/services/investmentPlatformService.test.ts`
- Create: `fe/src/modules/investment/platform/InvestmentPlatformPage.tsx`
- Create: `fe/src/modules/investment/platform/InvestmentPlatformPage.test.tsx`

- [ ] **Step 1: Write RED permission/read-only/operation tests**

Cover empty code registry, subscription read-only rendering, job/error pagination, stale lease, retry and quality
resolution permissions, duplicate click prevention, `Popconfirm`, and absence of private workspace/account calls.

- [ ] **Step 2: Implement bounded operations**

No forms may create or mutate symbol/interval/price type/capability. Only admin retry-failed-job and resolve-quality-
issue buttons are writes; use loading and button permission codes.

- [ ] **Step 3: Verify and commit**

```bash
cd /Users/mario/SelfProject/CyberMario/fe
bun run test -- investmentPlatformService.test.ts InvestmentPlatformPage.test.tsx
bun run typecheck
git add fe/src/modules/investment/types/investmentPlatformTypes.ts \
  fe/src/modules/investment/services/investmentPlatformService* \
  fe/src/modules/investment/platform
git commit -m "feat(fe): add investment platform monitoring"
```

### Phase 1 Gate

- [ ] Backend focused module tests pass.
- [ ] Frontend Phase-1 tests and typecheck pass.
- [ ] Empty production registry renders explicit “尚未在代码中接入” rather than an error.
- [ ] `code-reviewer` checks ownership/RBAC, revision writes, task transaction seams and API decimal contracts.
- [ ] Main agent creates the smallest explicit `FIX-P1-*` task/commit for blocker findings before Phase 2; no amend
  and no drive-by refactor.

```bash
cd /Users/mario/SelfProject/CyberMario/be
./mvnw -Dmaven.build.cache.enabled=false -Dtest='top.egon.mario.investment.**.*Tests' test

cd /Users/mario/SelfProject/CyberMario/fe
bun run test -- src/modules/investment
bun run typecheck
```

---



## Phase 2: Code Strategies And Reproducible Futures Backtesting

### Task DB-02: Create The Quant Schema

**Owner:** preset `postgres-pro`

**Dependencies:** DB-01 committed and current Flyway sequence rechecked. No other migration may run concurrently.

**Files:**

- Create: `be/src/main/resources/db/migration/V41__create_investment_quant_schema.sql`
  (or the then-current next version)
- Create: `be/src/test/java/top/egon/mario/investment/InvestmentQuantSchemaMigrationTests.java`

- [ ] **Step 1: Resolve next version and write RED schema tests**

Assert seven tables, FK/unique/check/index contracts, JSONB snapshots, immutable facts and sentinel columns.

```text
investment_strategy_release
investment_dataset_snapshot
investment_dataset_snapshot_item
investment_backtest_run
investment_backtest_trade
investment_backtest_event
investment_backtest_equity_point
```

- [ ] **Step 2: Run RED, add exactly one migration, rerun GREEN**

Required constraints:

- strategy `(strategy_code, strategy_version)` unique.
- snapshot `(workspace_id, dataset_hash)` unique; snapshot/item immutable after creation.
- snapshot item price/interval NOT NULL with `NONE` sentinel.
- backtest `job_id` unique; event `(run_id, sequence_no)` unique; equity PK `(run_id, point_time)`.
- JSON snapshot hashes are SHA-256 strings; DDL does not attempt to hash non-canonical JSON.

```bash
cd /Users/mario/SelfProject/CyberMario/be
./mvnw -Dmaven.build.cache.enabled=false -Dtest=InvestmentQuantSchemaMigrationTests test
./mvnw -Dmaven.build.cache.enabled=false -DskipTests compile
```

- [ ] **Step 3: Commit once**

```bash
git add be/src/main/resources/db/migration/V*_create_investment_quant_schema.sql \
  be/src/test/java/top/egon/mario/investment/InvestmentQuantSchemaMigrationTests.java
git commit -m "feat(investment): add quant schema"
```

### Task BE-09: Add The Code Strategy Registry And Release Snapshots

**Owner:** preset `spring-boot-engineer`

**Dependencies:** BE-01, BE-03, BE-08 and DB-02.

**Files:**

- Create: `be/src/main/java/top/egon/mario/investment/quant/strategy/InvestmentStrategy.java`
- Create: `be/src/main/java/top/egon/mario/investment/quant/strategy/StrategyDescriptor.java`
- Create: `be/src/main/java/top/egon/mario/investment/quant/strategy/StrategyContext.java`
- Create: `be/src/main/java/top/egon/mario/investment/quant/strategy/StrategyDecision.java`
- Create: `be/src/main/java/top/egon/mario/investment/quant/strategy/InvestmentStrategyRegistry.java`
- Create: `be/src/main/java/top/egon/mario/investment/quant/strategy/InvestmentStrategyReleaseSyncService.java`
- Create: `be/src/main/java/top/egon/mario/investment/quant/po/InvestmentStrategyReleasePo.java`
- Create: `be/src/main/java/top/egon/mario/investment/quant/repository/InvestmentStrategyReleaseRepository.java`
- Create: `be/src/main/java/top/egon/mario/investment/quant/web/InvestmentStrategyController.java`
- Create: `be/src/test/java/top/egon/mario/investment/quant/strategy/fixture/TestEmaCrossStrategy.java`
- Create: `be/src/test/java/top/egon/mario/investment/quant/strategy/InvestmentStrategyRegistryTests.java`
- Create: `be/src/test/java/top/egon/mario/investment/quant/strategy/InvestmentStrategyReleaseSyncTests.java`
- Create: `be/src/test/java/top/egon/mario/investment/quant/InvestmentStrategyControllerTests.java`

- [ ] **Step 1: Write RED registry/release/API tests**

Cover duplicate code/version, required capabilities, fixed descriptor, source/build hash, idempotent startup sync,
same code/version with changed source hash blocking quant scheduling, empty production registry and read-only API.

- [ ] **Step 2: Implement Strategy + registry without production rules**

No strategy CRUD, request parameters, script engine or database-defined logic. The test EMA strategy lives in
`src/test` only. A real private strategy later implements this SPI and receives its own commit/version.

- [ ] **Step 3: Verify and commit**

```bash
cd /Users/mario/SelfProject/CyberMario/be
./mvnw -Dmaven.build.cache.enabled=false \
  -Dtest=InvestmentStrategyRegistryTests,InvestmentStrategyReleaseSyncTests,InvestmentStrategyControllerTests \
  test
git add be/src/main/java/top/egon/mario/investment/quant \
  be/src/test/java/top/egon/mario/investment/quant
git commit -m "feat(investment): add code strategy registry"
```

### Task BE-10: Add The Pure Java Futures Simulation Kernel

**Owner:** preset `spring-boot-engineer`

**Dependencies:** BE-01. Safe in parallel with DB-02 and BE-09; this task must not touch Spring, JPA or Agent code.

**Files:**

- Create: `be/src/main/java/top/egon/mario/investment/trading/matching/MatchingModel.java`
- Create: `be/src/main/java/top/egon/mario/investment/trading/matching/BarMatchingModel.java`
- Create: `be/src/main/java/top/egon/mario/investment/trading/matching/SlippageModel.java`
- Create: `be/src/main/java/top/egon/mario/investment/trading/matching/FixedBpsSlippageModel.java`
- Create: `be/src/main/java/top/egon/mario/investment/trading/matching/FeeModel.java`
- Create: `be/src/main/java/top/egon/mario/investment/trading/matching/model/*.java`
- Create: `be/src/main/java/top/egon/mario/investment/portfolio/margin/IsolatedMarginModel.java`
- Create: `be/src/main/java/top/egon/mario/investment/portfolio/margin/PositionTierResolver.java`
- Create: `be/src/main/java/top/egon/mario/investment/portfolio/margin/LiquidationModel.java`
- Create: `be/src/main/java/top/egon/mario/investment/portfolio/margin/FundingModel.java`
- Create: `be/src/test/java/top/egon/mario/investment/trading/matching/BarMatchingModelTests.java`
- Create: `be/src/test/java/top/egon/mario/investment/portfolio/margin/IsolatedMarginModelTests.java`
- Create: `be/src/test/java/top/egon/mario/investment/portfolio/margin/LiquidationModelTests.java`
- Create: `be/src/test/java/top/egon/mario/investment/portfolio/margin/FundingModelTests.java`

- [ ] **Step 1: Lock formulas and event ordering in RED tests**

Cover long/short, open/reduce/close, quantity/step rounding, next-bar market fill, limit crossing, fixed bps slippage,
maker/taker fee, tier selection, isolated margin, funding sign, conservative liquidation-before-limit ordering and
missing mark/tier/funding rejection. Use exact `BigDecimal` assertions.

- [ ] **Step 2: Implement a dependency-free domain kernel**

No Spring annotations, repository calls, clock reads or random values. Inputs include explicit clock/data cutoff and
model snapshots. The same kernel must be callable by backtest and paper trading.

- [ ] **Step 3: Verify and commit**

```bash
cd /Users/mario/SelfProject/CyberMario/be
./mvnw -Dmaven.build.cache.enabled=false \
  -Dtest=BarMatchingModelTests,IsolatedMarginModelTests,LiquidationModelTests,FundingModelTests \
  test
git add be/src/main/java/top/egon/mario/investment/trading/matching \
  be/src/main/java/top/egon/mario/investment/portfolio/margin \
  be/src/test/java/top/egon/mario/investment/trading \
  be/src/test/java/top/egon/mario/investment/portfolio/margin
git commit -m "feat(investment): add deterministic futures simulation kernel"
```

### Task BE-11: Add Reproducible Dataset Snapshots

**Owner:** preset `spring-boot-engineer`, reviewed by preset `postgres-pro`

**Dependencies:** BE-04, BE-09 and DB-02.

**Files:**

- Create: `be/src/main/java/top/egon/mario/investment/quant/dataset/InvestmentDatasetSnapshotService.java`
- Create: `be/src/main/java/top/egon/mario/investment/quant/dataset/InvestmentDatasetHasher.java`
- Create: `be/src/main/java/top/egon/mario/investment/quant/dataset/MarketDataAsOfReader.java`
- Create: `be/src/main/java/top/egon/mario/investment/quant/dataset/DatasetCapabilityValidator.java`
- Create: `be/src/main/java/top/egon/mario/investment/quant/dataset/SnapshotRetentionProtectionService.java`
- Create: `be/src/main/java/top/egon/mario/investment/quant/dataset/SnapshotProtectedRetentionExecutionService.java`
- Modify: `be/src/main/java/top/egon/mario/investment/marketdata/service/MarketDataRetentionCandidateService.java`
- Create: `be/src/main/java/top/egon/mario/investment/quant/po/InvestmentDatasetSnapshotPo.java`
- Create: `be/src/main/java/top/egon/mario/investment/quant/po/InvestmentDatasetSnapshotItemPo.java`
- Create: `be/src/main/java/top/egon/mario/investment/quant/repository/*.java`
- Create: `be/src/test/java/top/egon/mario/investment/quant/dataset/InvestmentDatasetSnapshotServiceTests.java`
- Create: `be/src/test/java/top/egon/mario/investment/quant/dataset/InvestmentDatasetHasherTests.java`
- Create: `be/src/test/java/top/egon/mario/investment/quant/dataset/InvestmentAsOfMarketDataTests.java`
- Create: `be/src/test/java/top/egon/mario/investment/quant/dataset/InvestmentSnapshotProtectedRetentionTests.java`

- [ ] **Step 1: Write RED reproducibility tests**

Create revision 1 snapshot, ingest revision 2, and prove snapshot 1 still reads/hash-matches revision 1. Cover closed
data only, capability/gap/staleness failure, canonical JSON key ordering, source/spec/tier/funding/fee/slippage copies,
workspace ownership and idempotent same-hash creation.
Retention coverage proves a referenced range is skipped, an unreferenced older-than-two-years range can be deleted,
and an artifact-verified or explicitly expired snapshot changes protection deterministically.

- [ ] **Step 2: Implement bounded as-of paging**

Read current/history by `valid_from <= dataAsOf < valid_to` semantics without holding one long MVCC transaction.
Canonicalize JSON fields before SHA-256. Snapshot and items become immutable after creation.
Wire the Phase-1 candidate service to snapshot protection only now that DB-02 exists; physical deletion remains a
short bounded batch and cannot delete a range protected by a reproducible snapshot.

- [ ] **Step 3: Verify and commit**

```bash
cd /Users/mario/SelfProject/CyberMario/be
./mvnw -Dmaven.build.cache.enabled=false \
  -Dtest=InvestmentDatasetSnapshotServiceTests,InvestmentDatasetHasherTests,InvestmentAsOfMarketDataTests,InvestmentSnapshotProtectedRetentionTests \
  test
git add be/src/main/java/top/egon/mario/investment/quant \
  be/src/main/java/top/egon/mario/investment/marketdata/service/MarketDataRetentionCandidateService.java \
  be/src/test/java/top/egon/mario/investment/quant
git commit -m "feat(investment): add reproducible dataset snapshots"
```

### Task BE-12: Add The Java Backtest Runtime And APIs

**Owner:** preset `spring-boot-engineer`

**Dependencies:** BE-05, BE-09, BE-10, BE-11 and DB-02.

**Files:**

- Create: `be/src/main/java/top/egon/mario/investment/quant/engine/BacktestEngine.java`
- Create: `be/src/main/java/top/egon/mario/investment/quant/engine/JavaBacktestEngine.java`
- Create: `be/src/main/java/top/egon/mario/investment/quant/backtest/*.java`
- Create: `be/src/main/java/top/egon/mario/investment/quant/backtest/model/*.java`
- Create: `be/src/main/java/top/egon/mario/investment/quant/report/StrategyAnalysisReportGenerator.java`
- Create: `be/src/main/java/top/egon/mario/investment/quant/report/BacktestReportGenerator.java`
- Create: `be/src/main/java/top/egon/mario/investment/quant/overview/QuantOverviewSectionContributor.java`
- Create: `be/src/main/java/top/egon/mario/investment/quant/po/InvestmentBacktestRunPo.java`
- Create: `be/src/main/java/top/egon/mario/investment/quant/po/InvestmentBacktestTradePo.java`
- Create: `be/src/main/java/top/egon/mario/investment/quant/po/InvestmentBacktestEventPo.java`
- Create: `be/src/main/java/top/egon/mario/investment/quant/repository/*.java`
- Create: `be/src/main/java/top/egon/mario/investment/quant/repository/jdbc/BacktestEquityJdbcRepository.java`
- Create: `be/src/main/java/top/egon/mario/investment/quant/web/InvestmentBacktestController.java`
- Create: `be/src/main/java/top/egon/mario/investment/quant/web/dto/*.java`
- Create: `be/src/test/java/top/egon/mario/investment/quant/JavaBacktestEngineTests.java`
- Create: `be/src/test/java/top/egon/mario/investment/quant/InvestmentBacktestServiceTests.java`
- Create: `be/src/test/java/top/egon/mario/investment/quant/InvestmentBacktestControllerTests.java`
- Create: `be/src/test/java/top/egon/mario/investment/quant/InvestmentBacktestRequestStrictBindingTests.java`
- Create: `be/src/test/java/top/egon/mario/investment/quant/QuantReportGeneratorTests.java`
- Create: `be/src/test/java/top/egon/mario/investment/quant/QuantOverviewSectionContributorTests.java`

- [ ] **Step 1: Write RED engine/job/API tests**

Cover no-lookahead N/N+1, deterministic repeat, funding/fee/liquidation events, multi-instrument event order, job
retry/idempotency, owner isolation, unknown strategy, empty registry, invalid range and unknown request field rejection.
`STRATEGY_ANALYSIS` and `BACKTEST_REPORT` tests verify deterministic metrics come from the strategy descriptor and
backtest result, with evidence/cutoff/owner scope and immutable versions.
Quant overview contributor returns only the owner's recent runs and metrics no later than the shared cutoff.

- [ ] **Step 2: Implement short transaction phases**

Submit creates run/snapshot/job; compute runs outside a transaction; result rows batch-write in one bounded transaction;
terminal status writes separately. Strict DTO uses `@JsonAnySetter` or an Investment-only deserializer to reject extra
fields without changing global Jackson behavior.

- [ ] **Step 3: Implement deterministic equity persistence**

Compute every bar in memory. Persist first/last, every trade/funding/liquidation/new-max-drawdown point, plus at most
5,000 evenly spaced non-event bucket endpoints. API returns this series; events remain separately pageable.

- [ ] **Step 4: Verify and commit**

```bash
cd /Users/mario/SelfProject/CyberMario/be
./mvnw -Dmaven.build.cache.enabled=false \
  -Dtest=JavaBacktestEngineTests,InvestmentBacktestServiceTests,InvestmentBacktestControllerTests,InvestmentBacktestRequestStrictBindingTests,QuantReportGeneratorTests,QuantOverviewSectionContributorTests \
  test
git add be/src/main/java/top/egon/mario/investment/quant \
  be/src/test/java/top/egon/mario/investment/quant
git commit -m "feat(investment): add java futures backtesting"
```

### Task FE-05: Add The Quant Workbench

**Owner:** preset `react-specialist`

**Dependencies:** FE-01 and BE-09/BE-12 APIs committed.

**Files:**

- Create: `fe/src/modules/investment/types/investmentQuantTypes.ts`
- Create: `fe/src/modules/investment/services/investmentQuantService.ts`
- Create: `fe/src/modules/investment/services/investmentQuantService.test.ts`
- Create: `fe/src/modules/investment/quant/InvestmentQuantPage.tsx`
- Create: `fe/src/modules/investment/quant/InvestmentQuantPage.test.tsx`
- Create: `fe/src/modules/investment/quant/InvestmentBacktestDrawer.tsx`
- Create: `fe/src/modules/investment/quant/InvestmentBacktestDrawer.test.tsx`
- Create: `fe/src/modules/investment/quant/useBacktestRunPolling.ts`
- Create: `fe/src/modules/investment/quant/useBacktestRunPolling.test.ts`

- [ ] **Step 1: Write RED boundary/polling/chart tests**

Cover empty production strategy registry, read-only descriptor, form containing only strategy/instruments/time range,
absence of parameter editor, queued-to-terminal polling, timer cleanup, stale response suppression, trades/events and
Ant Design Charts equity/drawdown rendering.

- [ ] **Step 2: Implement bounded UI**

Workspace comes from Context. Poll 2s for the first minute, then 5s, stop on terminal/unmount/run change and expose
manual refresh. Do not place leverage/interval/fee/slippage/strategy parameter controls in the DOM.

- [ ] **Step 3: Verify and commit**

```bash
cd /Users/mario/SelfProject/CyberMario/fe
bun run test -- investmentQuantService.test.ts InvestmentQuantPage.test.tsx \
  InvestmentBacktestDrawer.test.tsx useBacktestRunPolling.test.ts
bun run typecheck
git add fe/src/modules/investment/types/investmentQuantTypes.ts \
  fe/src/modules/investment/services/investmentQuantService* \
  fe/src/modules/investment/quant
git commit -m "feat(fe): add investment quant workbench"
```

### Phase 2 Gate

- [ ] A snapshot created before an upstream correction remains byte-for-byte hash reproducible afterward.
- [ ] Backtest N signal cannot fill before N+1 open; mark price/funding/tier gaps fail closed.
- [ ] Empty strategy registry is a supported UI/API state; no strategy inputs can be smuggled through JSON.
- [ ] Backend focused tests, frontend quant tests and `code-reviewer` review pass before Phase 3.

---


## Phase 3: Paper Futures Accounts, Risk, Matching, And Ledger

### Task DB-03: Create The Paper Trading Schema

**Owner:** preset `postgres-pro`

**Dependencies:** DB-02 committed and Flyway sequence rechecked. No concurrent migration edits.

**Files:**

- Create: `be/src/main/resources/db/migration/V42__create_investment_paper_trading_schema.sql`
  (or the then-current next version)
- Create: `be/src/test/java/top/egon/mario/investment/InvestmentPaperSchemaMigrationTests.java`

- [ ] **Step 1: Write RED schema tests for nine tables**

```text
investment_paper_account
investment_risk_profile
investment_trade_intent
investment_risk_check
investment_paper_order
investment_paper_fill
investment_margin_ledger
investment_position
investment_account_snapshot
```

- [ ] **Step 2: Add exactly one migration**

Required constraints:

- account `(workspace_id, name)` unique and `ledger_sequence NOT NULL DEFAULT 0`.
- risk profile one-to-one; fields use explicit notional/amount/ratio/bps names and positive/range CHECKs.
- intent idempotency unique; risk `(intent_id, rule_code)` unique.
- V1 order has unique `client_order_id` and unique `intent_id`; fill `(order_id, fill_no)` unique.
- ledger `(account_id, sequence_no)` and global `idempotency_key` unique; ledger/fill immutable.
- position `(account_id, instrument_id)` unique; account snapshot PK `(account_id, snapshot_time)`.
- Redundant workspace/account/instrument columns use composite FK where necessary to prevent cross-owner mismatch.

- [ ] **Step 3: Verify and commit**

```bash
cd /Users/mario/SelfProject/CyberMario/be
./mvnw -Dmaven.build.cache.enabled=false -Dtest=InvestmentPaperSchemaMigrationTests test
./mvnw -Dmaven.build.cache.enabled=false -DskipTests compile
git add be/src/main/resources/db/migration/V*_create_investment_paper_trading_schema.sql \
  be/src/test/java/top/egon/mario/investment/InvestmentPaperSchemaMigrationTests.java
git commit -m "feat(investment): add paper trading schema"
```

### Task BE-13: Add Paper Accounts And The Deterministic Risk Gate

**Owner:** preset `spring-boot-engineer`

**Dependencies:** BE-02, BE-07, BE-10 and DB-03.

**Files:**

- Create: `be/src/main/java/top/egon/mario/investment/portfolio/po/InvestmentPaperAccountPo.java`
- Create: `be/src/main/java/top/egon/mario/investment/portfolio/po/InvestmentRiskProfilePo.java`
- Create: `be/src/main/java/top/egon/mario/investment/portfolio/repository/*.java`
- Create: `be/src/main/java/top/egon/mario/investment/portfolio/service/InvestmentPaperAccountService.java`
- Create: `be/src/main/java/top/egon/mario/investment/portfolio/risk/InvestmentRiskService.java`
- Create: `be/src/main/java/top/egon/mario/investment/portfolio/risk/InvestmentRiskRule.java`
- Create: `be/src/main/java/top/egon/mario/investment/portfolio/risk/rule/*.java`
- Create: `be/src/main/java/top/egon/mario/investment/portfolio/web/InvestmentPaperAccountController.java`
- Create: `be/src/main/java/top/egon/mario/investment/portfolio/web/dto/*.java`
- Create: `be/src/test/java/top/egon/mario/investment/portfolio/InvestmentPaperAccountServiceTests.java`
- Create: `be/src/test/java/top/egon/mario/investment/portfolio/InvestmentRiskServiceTests.java`
- Create: `be/src/test/java/top/egon/mario/investment/portfolio/InvestmentRiskControllerTests.java`

- [ ] **Step 1: Write RED account/risk tests**

Cover required risk profile on create, disabled initial switches, owner isolation, update validation, optimistic conflict,
and every mandatory rule code: switches, subscription/tradability, freshness/mark/tier/funding, order/position/gross
notional, leverage/open positions, daily loss amount, drawdown ratio, rate/cooldown, available margin, slippage and
reduce-only validity.

- [ ] **Step 2: Implement grouped Strategy rules**

Use a small ordered rule registry grouped by switch/data/exposure/loss/order semantics; do not create 18 trivial
classes. BE-13 is pure evaluation: it receives an immutable risk context and returns ordered results, but owns no
intent/risk-check persistence. Strategy/Agent may tighten but never widen account limits.

- [ ] **Step 3: Implement account APIs**

Create account + risk profile atomically; switches remain false regardless of client fields on create. PUT risk profile
is permission protected. Return only server-calculated equity/margin/risk values as decimal strings.

- [ ] **Step 4: Verify and commit**

```bash
cd /Users/mario/SelfProject/CyberMario/be
./mvnw -Dmaven.build.cache.enabled=false \
  -Dtest=InvestmentPaperAccountServiceTests,InvestmentRiskServiceTests,InvestmentRiskControllerTests \
  test
git add be/src/main/java/top/egon/mario/investment/portfolio \
  be/src/test/java/top/egon/mario/investment/portfolio
git commit -m "feat(investment): add paper accounts and risk gate"
```

### Task BE-14: Add Trade Intents, Pending Orders, Matching, Ledger, And Positions

**Owner:** preset `spring-boot-engineer`, reviewed by preset `postgres-pro`

**Dependencies:** BE-05, BE-10, BE-13 and DB-03.

**Files:**

- Create: `be/src/main/java/top/egon/mario/investment/trading/po/*.java`
- Create: `be/src/main/java/top/egon/mario/investment/trading/po/InvestmentRiskCheckPo.java`
- Create: `be/src/main/java/top/egon/mario/investment/trading/repository/*.java`
- Create: `be/src/main/java/top/egon/mario/investment/portfolio/po/InvestmentPositionPo.java`
- Create: `be/src/main/java/top/egon/mario/investment/portfolio/po/InvestmentAccountSnapshotPo.java`
- Create: `be/src/main/java/top/egon/mario/investment/portfolio/repository/InvestmentPositionRepository.java`
- Create: `be/src/main/java/top/egon/mario/investment/trading/service/PaperTradingFacade.java`
- Create: `be/src/main/java/top/egon/mario/investment/trading/service/PaperOrderService.java`
- Create: `be/src/main/java/top/egon/mario/investment/trading/service/PaperIntentAcceptanceTransactionService.java`
- Create: `be/src/main/java/top/egon/mario/investment/trading/service/PaperExecutionTransactionService.java`
- Create: `be/src/main/java/top/egon/mario/investment/trading/matching/PaperMatchJobHandler.java`
- Create: `be/src/main/java/top/egon/mario/investment/trading/web/InvestmentPaperTradingController.java`
- Create: `be/src/main/java/top/egon/mario/investment/trading/web/dto/*.java`
- Create: `be/src/test/java/top/egon/mario/investment/trading/PaperTradingFacadeTests.java`
- Create: `be/src/test/java/top/egon/mario/investment/trading/PaperIntentAcceptanceTransactionServiceTests.java`
- Create: `be/src/test/java/top/egon/mario/investment/trading/PaperExecutionTransactionServiceTests.java`
- Create: `be/src/test/java/top/egon/mario/investment/trading/PaperMatchingJobHandlerTests.java`
- Create: `be/src/test/java/top/egon/mario/investment/trading/InvestmentPaperTradingControllerTests.java`

- [ ] **Step 1: Write RED facade/idempotency/transaction tests**

Cover USER/STRATEGY/AGENT sources sharing one facade, duplicate idempotency, risk rejection without order, accepted
intent creating exactly one pending order/job, concurrent reserved-margin/rate/cooldown checks, market/limit match on
the correct closed bar, N+1-not-ready defer, GTC-not-crossed defer, cancel/expiry races, rollback at each acceptance and
fill/ledger/wallet/position/order seam, and response execution summary. Defer must not consume failure attempts.

- [ ] **Step 2: Implement the single write facade**

Controllers, strategies and Agent never access repositories directly. `PaperTradingFacade` first reads the immutable
market/spec/tier snapshot outside a transaction, then calls the separate transactional acceptance bean. That bean
locks account, related positions and pending opening orders; recomputes balance/reserved margin/rate/cooldown; validates
snapshot revision/freshness; then atomically writes intent, one final risk row per rule and, only on pass, one pending
order + match job. It returns `PENDING_MATCH` until the handler fills.

Two concurrent intents that together exceed available margin must yield exactly one `PENDING_MATCH` and one
`RISK_REJECTED`, with one order/job total. `PaperIntentAcceptanceTransactionService` and
`PaperExecutionTransactionService` are separate Spring beans so `@Transactional` applies through proxies.

- [ ] **Step 3: Enforce lock and ledger ordering**

In one transaction: lock account, positions sorted by instrument, orders sorted by id; recheck order/margin; allocate
account ledger sequence; append fill/fee/ledger; update wallet/position/order; optionally append account snapshot.
No market query or calculation that can block runs while locks are held.
When N+1 is not closed, or a GTC limit has not crossed, the handler calls fenced job `defer(nextAvailableAt)` and keeps
the order pending. Only fill, cancel, explicit expiry or invalidated risk state terminates it.

- [ ] **Step 4: Verify and commit**

```bash
cd /Users/mario/SelfProject/CyberMario/be
./mvnw -Dmaven.build.cache.enabled=false \
  -Dtest=PaperTradingFacadeTests,PaperIntentAcceptanceTransactionServiceTests,PaperExecutionTransactionServiceTests,PaperMatchingJobHandlerTests,InvestmentPaperTradingControllerTests \
  test
git add be/src/main/java/top/egon/mario/investment/trading \
  be/src/main/java/top/egon/mario/investment/portfolio \
  be/src/test/java/top/egon/mario/investment/trading
git commit -m "feat(investment): add paper order execution and ledger"
```

### Task BE-15: Add Funding, Margin Checks, Liquidation, And Equity Snapshots

**Owner:** preset `spring-boot-engineer`

**Dependencies:** BE-14.

**Files:**

- Create: `be/src/main/java/top/egon/mario/investment/trading/service/PaperFundingSettlementService.java`
- Create: `be/src/main/java/top/egon/mario/investment/trading/service/PaperMarginCheckService.java`
- Create: `be/src/main/java/top/egon/mario/investment/trading/service/PaperLiquidationService.java`
- Create: `be/src/main/java/top/egon/mario/investment/trading/service/InvestmentAccountSnapshotService.java`
- Create: `be/src/main/java/top/egon/mario/investment/trading/handler/PaperFundingSettleJobHandler.java`
- Create: `be/src/main/java/top/egon/mario/investment/trading/handler/PaperMarginCheckJobHandler.java`
- Create: `be/src/main/java/top/egon/mario/investment/trading/job/PaperMaintenanceJobPlanner.java`
- Create: `be/src/main/java/top/egon/mario/investment/trading/job/PaperMarketDataCommittedListener.java`
- Create: `be/src/main/java/top/egon/mario/investment/portfolio/query/InvestmentPortfolioQueryService.java`
- Create: `be/src/main/java/top/egon/mario/investment/portfolio/web/InvestmentPortfolioController.java`
- Create: `be/src/main/java/top/egon/mario/investment/portfolio/web/dto/*.java`
- Create: `be/src/main/java/top/egon/mario/investment/portfolio/report/PortfolioReportGenerator.java`
- Create: `be/src/main/java/top/egon/mario/investment/portfolio/overview/PortfolioOverviewSectionContributor.java`
- Create: `be/src/test/java/top/egon/mario/investment/trading/PaperFundingSettlementTests.java`
- Create: `be/src/test/java/top/egon/mario/investment/trading/PaperMarginCheckTests.java`
- Create: `be/src/test/java/top/egon/mario/investment/trading/PaperLiquidationTests.java`
- Create: `be/src/test/java/top/egon/mario/investment/trading/InvestmentAccountSnapshotTests.java`
- Create: `be/src/test/java/top/egon/mario/investment/trading/PaperMaintenanceJobPlannerTests.java`
- Create: `be/src/test/java/top/egon/mario/investment/portfolio/InvestmentPortfolioControllerTests.java`
- Create: `be/src/test/java/top/egon/mario/investment/portfolio/InvestmentPortfolioMarkerContractTests.java`
- Create: `be/src/test/java/top/egon/mario/investment/portfolio/PortfolioReportGeneratorTests.java`
- Create: `be/src/test/java/top/egon/mario/investment/portfolio/PortfolioOverviewSectionContributorTests.java`

- [ ] **Step 1: Write RED settlement/liquidation tests**

Cover long/short funding sign, deterministic ledger idempotency key, repeated settlement no-op, unavailable funding/
mark/tier failure, maintenance threshold boundary, conservative liquidation ordering, cancellation of open orders,
system close and exact equity/drawdown snapshot values.
Planner tests cover market-commit fan-out plus scheduled restart reconciliation, repeated/concurrent ticks producing
one idempotent task, first funding/margin task creation and no task for accounts without open positions. Query/API
tests cover every Spec 23.4 GET endpoint with owner scope, pagination and decimal-string projections.
Marker contract tests require `instrumentId/from/to` for marker requests, enforce maximum window/page size and stable
`eventTime,id` ordering, and assert `marketBarOpenTime/side/actionType/orderOrigin/eventType/price/quantity/liquidation` plus
cross-owner rejection and explicit liquidation classification.

- [ ] **Step 2: Implement through the same account transaction seam**

Use the BE-14 lock order and append-only ledger. Funding idempotency key includes account/position/instrument/time.
Liquidation cannot bypass order/fill/ledger facts. Jobs retry dependency failures without duplicate financial facts.
After market commit, the listener enqueues or wakes PAPER_MATCH for pending orders and idempotent funding/margin jobs;
the scheduled planner reconciles missed work after restart. `InvestmentPortfolioQueryService` provides account,
orders, fills, positions, ledger and equity without N+1. `PortfolioReportGenerator` creates immutable
`PORTFOLIO_REPORT` from those domain projections and evidence at one server-selected cutoff.
Portfolio overview contributor returns owner-scoped account equity, risk warnings and positions at that same cutoff.

- [ ] **Step 3: Verify and commit**

```bash
cd /Users/mario/SelfProject/CyberMario/be
./mvnw -Dmaven.build.cache.enabled=false \
  -Dtest=PaperFundingSettlementTests,PaperMarginCheckTests,PaperLiquidationTests,InvestmentAccountSnapshotTests,PaperMaintenanceJobPlannerTests,InvestmentPortfolioControllerTests,InvestmentPortfolioMarkerContractTests,PortfolioReportGeneratorTests,PortfolioOverviewSectionContributorTests \
  test
git add be/src/main/java/top/egon/mario/investment/trading \
  be/src/main/java/top/egon/mario/investment/portfolio \
  be/src/test/java/top/egon/mario/investment/trading \
  be/src/test/java/top/egon/mario/investment/portfolio
git commit -m "feat(investment): add funding margin and liquidation jobs"
```

### Task FE-06: Add Paper Portfolio And Manual Simulation

**Owner:** preset `react-specialist`

**Dependencies:** FE-01 and BE-13/BE-14/BE-15 APIs committed.

**Files:**

- Create: `fe/src/modules/investment/types/investmentPortfolioTypes.ts`
- Create: `fe/src/modules/investment/services/investmentPortfolioService.ts`
- Create: `fe/src/modules/investment/services/investmentPortfolioService.test.ts`
- Create: `fe/src/modules/investment/portfolio/InvestmentPortfolioPage.tsx`
- Create: `fe/src/modules/investment/portfolio/InvestmentPortfolioPage.test.tsx`
- Create: `fe/src/modules/investment/portfolio/PaperAccountCreateDrawer.tsx`
- Create: `fe/src/modules/investment/portfolio/PaperAccountCreateDrawer.test.tsx`
- Create: `fe/src/modules/investment/portfolio/RiskProfileDrawer.tsx`
- Create: `fe/src/modules/investment/portfolio/RiskProfileDrawer.test.tsx`
- Create: `fe/src/modules/investment/portfolio/TradeIntentDrawer.tsx`
- Create: `fe/src/modules/investment/portfolio/TradeIntentDrawer.test.tsx`
- Create: `fe/src/modules/investment/portfolio/PortfolioEquityChart.tsx`
- Create: `fe/src/modules/investment/portfolio/PortfolioEquityChart.test.tsx`
- Create: `fe/src/modules/investment/portfolio/PortfolioPositionsTable.tsx`
- Create: `fe/src/modules/investment/portfolio/PortfolioOrdersTable.tsx`
- Create: `fe/src/modules/investment/portfolio/PortfolioFillsTable.tsx`
- Create: `fe/src/modules/investment/portfolio/PortfolioLedgerTable.tsx`
- Create: `fe/src/modules/investment/portfolio/InvestmentPortfolioTables.test.tsx`

- [ ] **Step 1: Write RED private-state/form/result tests**

Cover workspace/account switch clearing old facts, required risk fields and units, initial switches off, switch rollback
on API error, stringMode inputs, double-submit protection, risk rejection details, pending match vs fill, cancel and
server-calculated margin/PNL/liquidation display.
Cover equity/drawdown chart empty/large/downsampled data, accessible text fallback, and server-paginated positions,
orders, fills and ledger tables across account/workspace changes.

- [ ] **Step 2: Implement page and drawers**

Page owns selected-account facts; drawers own their forms. Never calculate account facts in React. A risk rejection
is an explainable domain result, not a generic toast-only failure. Do not include real-trading wording or controls.
Ant Design Charts renders server-provided equity/drawdown only; each fact table has independent loading/error/page
state and clears immediately when the account changes.

- [ ] **Step 3: Verify and commit**

```bash
cd /Users/mario/SelfProject/CyberMario/fe
bun run test -- investmentPortfolioService.test.ts InvestmentPortfolioPage.test.tsx \
  PaperAccountCreateDrawer.test.tsx RiskProfileDrawer.test.tsx TradeIntentDrawer.test.tsx \
  PortfolioEquityChart.test.tsx InvestmentPortfolioTables.test.tsx
bun run typecheck
git add fe/src/modules/investment/types/investmentPortfolioTypes.ts \
  fe/src/modules/investment/services/investmentPortfolioService* \
  fe/src/modules/investment/portfolio
git commit -m "feat(fe): add investment paper portfolio"
```

### Phase 3 Gate

- [ ] Duplicate intent/fill/funding/liquidation retries create one financial fact and one ledger effect.
- [ ] Concurrent account operations cannot overspend margin or deadlock under the fixed lock order.
- [ ] Every accepted order is visibly `PENDING_MATCH` until the correct bar exists; no fake synchronous fill.
- [ ] Portfolio owner isolation, risk units and disabled initial switches are covered in API and UI tests.
- [ ] `postgres-pro` and `code-reviewer` approve transaction/idempotency evidence before Phase 4.

---


## Phase 4: Scoped Investment Agent And Automatic Paper Trading

### Task BE-16: Add Per-run Scoped Read-only Agent Tools

**Owner:** preset `llm-architect`, reviewed by preset `spring-boot-engineer`

**Dependencies:** Existing generic Agent tests pass; BE-01 is committed. This task does not depend on DB-04 and may
run in parallel with it. It exclusively owns the listed shared Agent files.

**Files:**

- Modify: `be/src/main/java/top/egon/mario/agent/service/AgentRuntimeFactory.java`
- Modify: `be/src/main/java/top/egon/mario/agent/service/impl/DefaultAgentRuntimeFactory.java`
- Modify: `be/src/main/java/top/egon/mario/agent/model/dto/enums/ModelScenario.java`
- Modify: `be/src/main/java/top/egon/mario/agent/observability/interceptor/AgentObservabilityToolInterceptor.java`
- Modify: `be/src/main/java/top/egon/mario/agent/observability/interceptor/AgentObservabilityModelInterceptor.java`
- Modify: `be/src/main/java/top/egon/mario/agent/observability/service/impl/AgentRunAuditServiceImpl.java`
- Create: `be/src/main/java/top/egon/mario/agent/service/model/ScopedAgentToolSet.java`
- Create: `be/src/test/java/top/egon/mario/agent/service/ScopedAgentRuntimeFactoryTests.java`
- Modify: `be/src/test/java/top/egon/mario/agent/service/impl/AgentRuntimeFactoryTests.java`
- Create: `be/src/test/java/top/egon/mario/agent/model/dto/enums/ModelScenarioTests.java`
- Modify: `be/src/test/java/top/egon/mario/agent/observability/interceptor/AgentObservabilityToolInterceptorTests.java`
- Modify: `be/src/test/java/top/egon/mario/agent/observability/interceptor/AgentObservabilityModelInterceptorTests.java`
- Modify: `be/src/test/java/top/egon/mario/agent/observability/service/impl/AgentRunAuditServiceImplTests.java`

- [ ] **Step 1: Write RED isolation and compatibility tests**

Prove a scoped read callback is available only to the created runtime, never appears in `AgentPresetServiceImpl`
default tools, cannot be selected by ordinary chat/debug, and retains all existing runtime overload behavior. Test
name collision rejection, one-run lifecycle, exception propagation and an explicit `parallelToolExecution` option.

- [ ] **Step 2: Add the smallest runtime overload**

Accept an immutable `ScopedAgentToolSet` per runtime creation. Do not register scoped callbacks as Spring beans.
The type rejects callbacks marked as side-effecting; Investment runtime receives read-only callbacks only. Add
`INVESTMENT_AGENT` model scenario and allow AUTO_TRADE callers to force `parallelToolExecution=false` without
changing existing scenario routing. Keep original call sites binary/source compatible.

- [ ] **Step 3: Redact observability safely**

Runtime logs may record callback/model name, status, latency and argument/prompt/response/result size or hash. Update
tool interceptor, model interceptor and run-audit service so no application log prints full portfolio/report/prompt/
messages/model response/tool payload. Existing database audit can retain approved structured events under current
access controls; no credentials or future exchange keys are logged.

- [ ] **Step 4: Verify and commit**

```bash
cd /Users/mario/SelfProject/CyberMario/be
./mvnw -Dmaven.build.cache.enabled=false \
  -Dtest=AgentRuntimeFactoryTests,ScopedAgentRuntimeFactoryTests,AgentObservabilityToolInterceptorTests,AgentObservabilityModelInterceptorTests,AgentRunAuditServiceImplTests,ModelScenarioTests \
  test
git add be/src/main/java/top/egon/mario/agent/service/AgentRuntimeFactory.java \
  be/src/main/java/top/egon/mario/agent/service/impl/DefaultAgentRuntimeFactory.java \
  be/src/main/java/top/egon/mario/agent/model/dto/enums/ModelScenario.java \
  be/src/main/java/top/egon/mario/agent/observability/interceptor/AgentObservabilityToolInterceptor.java \
  be/src/main/java/top/egon/mario/agent/observability/interceptor/AgentObservabilityModelInterceptor.java \
  be/src/main/java/top/egon/mario/agent/observability/service/impl/AgentRunAuditServiceImpl.java \
  be/src/main/java/top/egon/mario/agent/service/model/ScopedAgentToolSet.java \
  be/src/test/java/top/egon/mario/agent/service/ScopedAgentRuntimeFactoryTests.java \
  be/src/test/java/top/egon/mario/agent/service/impl/AgentRuntimeFactoryTests.java \
  be/src/test/java/top/egon/mario/agent/model/dto/enums/ModelScenarioTests.java \
  be/src/test/java/top/egon/mario/agent/observability/interceptor/AgentObservabilityToolInterceptorTests.java \
  be/src/test/java/top/egon/mario/agent/observability/interceptor/AgentObservabilityModelInterceptorTests.java \
  be/src/test/java/top/egon/mario/agent/observability/service/impl/AgentRunAuditServiceImplTests.java
git commit -m "feat(agent): support scoped domain tools"
```

### Task DB-04: Create The Investment Agent Schema

**Owner:** preset `postgres-pro`

**Dependencies:** DB-03 committed and Flyway sequence rechecked. Safe in parallel with BE-16 only.

**Files:**

- Create: `be/src/main/resources/db/migration/V43__create_investment_agent_schema.sql`
  (or the then-current next version)
- Create: `be/src/test/java/top/egon/mario/investment/InvestmentAgentSchemaMigrationTests.java`

- [ ] **Step 1: Write RED schema tests**

Assert `investment_agent_run` and `investment_agent_decision`, FK to workspace/account/report/generic
`agent_run_audit`/instrument/intent, unique run idempotency key, unique nullable decision intent/execution key,
`NOT_APPLICABLE/PENDING/SUBMITTED/FAILED` execution state checks and required indexes.

- [ ] **Step 2: Add exactly one migration**

Use `NO ACTION/RESTRICT`; no cascade delete. `idempotency_key` is NOT NULL unique, avoiding a nullable-account
composite unique. Decision is immutable after terminal validation except linking its one intent in the same controlled
flow. A validated non-HOLD decision stores `PENDING + execution_idempotency_key` before Facade entry and becomes
`SUBMITTED` only after its intent is linked; this is the durable crash-recovery seam.

- [ ] **Step 3: Verify and commit**

```bash
cd /Users/mario/SelfProject/CyberMario/be
./mvnw -Dmaven.build.cache.enabled=false -Dtest=InvestmentAgentSchemaMigrationTests test
./mvnw -Dmaven.build.cache.enabled=false -DskipTests compile
git add be/src/main/resources/db/migration/V*_create_investment_agent_schema.sql \
  be/src/test/java/top/egon/mario/investment/InvestmentAgentSchemaMigrationTests.java
git commit -m "feat(investment): add agent schema"
```

### Task BE-17: Add The Audited Investment Agent

**Owner:** preset `llm-architect`, reviewed by preset `spring-boot-engineer`

**Dependencies:** BE-05, BE-08, BE-12, BE-14, BE-15, BE-16 and DB-04.

**Files:**

- Create: `be/src/main/java/top/egon/mario/investment/agent/po/*.java`
- Create: `be/src/main/java/top/egon/mario/investment/agent/repository/*.java`
- Create: `be/src/main/java/top/egon/mario/investment/agent/service/InvestmentAgentPresetRegistry.java`
- Create: `be/src/main/java/top/egon/mario/investment/agent/service/InvestmentAgentRunner.java`
- Create: `be/src/main/java/top/egon/mario/investment/agent/service/InvestmentAgentRunService.java`
- Create: `be/src/main/java/top/egon/mario/investment/agent/service/InvestmentAgentDecisionValidator.java`
- Create: `be/src/main/java/top/egon/mario/investment/agent/service/InvestmentAgentToolCallbackFactory.java`
- Create: `be/src/main/java/top/egon/mario/investment/agent/service/InvestmentAgentDecisionExecutionService.java`
- Create: `be/src/main/java/top/egon/mario/investment/agent/service/InvestmentAgentJobHandler.java`
- Create: `be/src/main/java/top/egon/mario/investment/agent/report/AgentAnalysisReportGenerator.java`
- Create: `be/src/main/java/top/egon/mario/investment/agent/overview/AgentOverviewSectionContributor.java`
- Create: `be/src/main/java/top/egon/mario/investment/agent/tool/*.java`
- Create: `be/src/main/java/top/egon/mario/investment/agent/web/InvestmentAgentController.java`
- Create: `be/src/main/java/top/egon/mario/investment/agent/web/dto/*.java`
- Create: `be/src/test/java/top/egon/mario/investment/agent/InvestmentAgentToolTests.java`
- Create: `be/src/test/java/top/egon/mario/investment/agent/InvestmentAgentRunnerTests.java`
- Create: `be/src/test/java/top/egon/mario/investment/agent/InvestmentAgentAccessTests.java`
- Create: `be/src/test/java/top/egon/mario/investment/agent/InvestmentAgentControllerTests.java`
- Create: `be/src/test/java/top/egon/mario/investment/agent/InvestmentAgentAuditIntegrationTests.java`
- Create: `be/src/test/java/top/egon/mario/investment/agent/InvestmentAgentDecisionExecutionRecoveryTests.java`
- Create: `be/src/test/java/top/egon/mario/investment/agent/AgentOverviewSectionContributorTests.java`

- [ ] **Step 1: Freeze the built-in preset and structured schema**

Create one fixed code preset `INVESTMENT_ANALYST_V1`; no frontend prompt editing. Supported run types are
`MARKET_ANALYSIS`, `INSTRUMENT_ANALYSIS`, `STRATEGY_REVIEW`, `PORTFOLIO_REVIEW`, `AUTO_TRADE`.
Structured decision permits only `HOLD/OPEN_LONG/OPEN_SHORT/CLOSE/REDUCE` and spec fields. `dataAsOf` is server-
selected from validated closed data, not client/LLM controlled.

- [ ] **Step 2: Write RED tool/security/output tests**

Cover all read tools, absence of any LLM write tool, server-bound actor/workspace/account/dataAsOf, attempted
cross-account ids, unsubscribed instruments, invalid JSON/enum/range/missing field, stale data, tool failure, model
timeout, duplicate/parallel proposal, HOLD, duplicate run, analysis with auto-trade disabled and auto-trade accepted/
risk-rejected/pending-match paths. Timeout or invalid final output after read-tool use must create zero intent/order/
ledger facts. Simulate a crash after the Facade commits but before the decision links its intent; retry must re-enter
the Facade with the same key, recover the existing intent and still leave one order and one financial effect.

- [ ] **Step 3: Implement dynamic scoped tools and strict validation**

Create read-only callbacks per run as closures. Both analysis and AUTO_TRADE expose only read callbacks; AUTO_TRADE
forces `parallelToolExecution=false`. Parse and validate the complete final decision before any side effect. Persist
the validated decision first, then `InvestmentAgentDecisionExecutionService` is the only component allowed to enter
`PaperTradingFacade` for non-HOLD actions. It always uses the same `run + decision + instrument` idempotency key.
Retries may physically call the Facade again after an ambiguous crash, but the Facade returns the same intent and may
not create a second order, fill, ledger or position effect. Runner has no independent submission path.

- [ ] **Step 4: Implement short transaction audit flow**

Create Investment run + generic run audit, execute LLM outside transaction, validate/store decisions, execute the
post-validation intent step, link the returned intent idempotently, then complete/fail in separate short transactions.
An unlinked validated non-HOLD decision is recoverable work rather than a terminal success. Persist tool/model errors without
leaking private payloads to logs. Run idempotency includes workspace/account/preset/run type, sorted/deduplicated
instrument IDs (canonical input hash) and data cutoff; same set in another order deduplicates, different sets do not.
`AgentAnalysisReportGenerator` creates immutable `AGENT_ANALYSIS` reports from validated decisions and evidence.
Agent overview contributor returns only the owner's recent runs/decisions no later than the shared cutoff.

- [ ] **Step 5: Verify and commit**

```bash
cd /Users/mario/SelfProject/CyberMario/be
./mvnw -Dmaven.build.cache.enabled=false \
  -Dtest=InvestmentAgentToolTests,InvestmentAgentRunnerTests,InvestmentAgentAccessTests,InvestmentAgentControllerTests,InvestmentAgentAuditIntegrationTests,InvestmentAgentDecisionExecutionRecoveryTests,AgentOverviewSectionContributorTests \
  test
git add be/src/main/java/top/egon/mario/investment/agent \
  be/src/test/java/top/egon/mario/investment/agent
git commit -m "feat(investment): add audited auto paper trading agent"
```

### Task FE-07: Add The Investment Agent Console

**Owner:** preset `react-specialist`

**Dependencies:** FE-06 and BE-17. Do not run concurrently with FE-06 if shared account selection types still change.

**Files:**

- Create: `fe/src/modules/investment/types/investmentAgentTypes.ts`
- Create: `fe/src/modules/investment/services/investmentAgentService.ts`
- Create: `fe/src/modules/investment/services/investmentAgentService.test.ts`
- Create: `fe/src/modules/investment/agent/InvestmentAgentPage.tsx`
- Create: `fe/src/modules/investment/agent/InvestmentAgentPage.test.tsx`
- Create: `fe/src/modules/investment/agent/InvestmentAgentRunDrawer.tsx`
- Create: `fe/src/modules/investment/agent/InvestmentAgentRunDrawer.test.tsx`
- Create: `fe/src/modules/investment/agent/useAgentRunPolling.ts`
- Create: `fe/src/modules/investment/agent/useAgentRunPolling.test.ts`

- [ ] **Step 1: Write RED scope/UX/polling tests**

Cover fixed preset/run type/instruments/account fields, absence of Prompt/tool/strategy editors, no confirmation modal,
paper-only warning, analysis while auto-trade off, HOLD/open/close/reduce decisions, risk rejection, pending match, fill,
failure/liquidation, run switch race and terminal polling cleanup.

- [ ] **Step 2: Implement explainable automatic execution UI**

Show `dataAsOf`, thesis, risks, invalidation, confidence and decision -> intent -> risk -> order -> fill summary.
Never render “approve order” or “confirm execution”. Poll with the same bounded policy as backtests; provide manual
refresh and explicit “仅模拟盘，风控通过后自动执行” wording.

- [ ] **Step 3: Verify and commit**

```bash
cd /Users/mario/SelfProject/CyberMario/fe
bun run test -- investmentAgentService.test.ts InvestmentAgentPage.test.tsx \
  InvestmentAgentRunDrawer.test.tsx useAgentRunPolling.test.ts
bun run typecheck
git add fe/src/modules/investment/types/investmentAgentTypes.ts \
  fe/src/modules/investment/services/investmentAgentService* \
  fe/src/modules/investment/agent
git commit -m "feat(fe): add investment agent console"
```

### Task FE-11: Enrich Instrument Detail With Reports And Trade Markers

**Owner:** preset `react-specialist`

**Dependencies:** FE-03, FE-04, FE-06, FE-07 and BE-15/BE-17 APIs.

**Files:**

- Modify: `fe/src/modules/investment/instrument/InvestmentInstrumentPage.tsx`
- Modify: `fe/src/modules/investment/instrument/InvestmentInstrumentPage.test.tsx`
- Modify: `fe/src/modules/investment/instrument/InvestmentKlinePanel.tsx`
- Modify: `fe/src/modules/investment/components/InvestmentCandlestickChart.tsx`
- Modify: `fe/src/modules/investment/components/InvestmentCandlestickChart.test.tsx`
- Create: `fe/src/modules/investment/instrument/investmentTradeMarkerMappers.ts`
- Create: `fe/src/modules/investment/instrument/investmentTradeMarkerMappers.test.ts`
- Create: `fe/src/modules/investment/instrument/InvestmentInstrumentReportsPanel.tsx`
- Create: `fe/src/modules/investment/instrument/InvestmentInstrumentReportsPanel.test.tsx`

- [ ] **Step 1: Write RED marker/report/race tests**

Map owner-account fills and liquidation events to accessible buy/sell/liquidation markers at exact bar times. Cover
no workspace/account, empty markers, account/instrument switch clearing stale markers, closed-bar alignment, report
type/status/evidence links and traditional/Agent report empty/error states.

- [ ] **Step 2: Extend the existing chart lifecycle**

Use the Lightweight Charts v5 marker primitive without recreating chart/series. Private markers are optional overlays
and never block public candles. Provide a textual activity summary/table fallback; color/shape alone is insufficient.
The reports panel links to fixed report versions and shows `dataAsOf` rather than asking the chart to calculate facts.

- [ ] **Step 3: Verify and commit**

```bash
cd /Users/mario/SelfProject/CyberMario/fe
bun run test -- InvestmentInstrumentPage.test.tsx InvestmentCandlestickChart.test.tsx \
  investmentTradeMarkerMappers.test.ts InvestmentInstrumentReportsPanel.test.tsx
bun run typecheck
git add fe/src/modules/investment/instrument \
  fe/src/modules/investment/components/InvestmentCandlestickChart*
git commit -m "feat(fe): add investment trade markers and reports"
```

### Phase 4 Gate

- [ ] Ordinary chat/debug cannot discover or call Investment tools.
- [ ] No Investment run receives an LLM write callback; validated AUTO_TRADE execution cannot target another
  account/workspace. Ambiguous retries may re-enter the Facade only with the same key and produce one logical effect.
- [ ] Invalid/ungrounded model output produces an audited failure and zero intent/order/ledger changes.
- [ ] Risk rejection and pending match are explicit successful domain outcomes, not fake fills.
- [ ] Instrument detail shows owner-scoped trade/liquidation markers and fixed traditional/Agent report versions.
- [ ] `llm-architect`, `spring-boot-engineer` and `code-reviewer` findings are resolved before integration.

---


## Phase 5: Integration, PostgreSQL Contracts, And Release Gates

### Task BE-19: Complete The Cross-domain Overview Projection

**Owner:** preset `spring-boot-engineer`

**Dependencies:** BE-12, BE-15 and BE-17 contributors are committed.

**Files:**

- Modify: `be/src/main/java/top/egon/mario/investment/overview/InvestmentOverviewQueryService.java`
- Modify: `be/src/main/java/top/egon/mario/investment/overview/InvestmentOverviewController.java`
- Modify: `be/src/main/java/top/egon/mario/investment/overview/dto/*.java`
- Modify: `be/src/test/java/top/egon/mario/investment/overview/InvestmentOverviewControllerTests.java`
- Create: `be/src/test/java/top/egon/mario/investment/overview/InvestmentOverviewIntegrationTests.java`
- Create: `be/src/test/java/top/egon/mario/investment/overview/InvestmentOverviewCutoffTests.java`

- [ ] **Step 1: Write RED cross-domain aggregation tests**

Cover one workspace-scoped response containing platform market summary plus owner-scoped account/equity/position/
risk warnings, recent backtest and Agent run, partial unavailable section, no N+1 query fan-out, missing workspace
rejection and cross-owner rejection.

- [ ] **Step 2: Enforce one server-selected cutoff**

The query service selects one cutoff and sends it to market/quant/portfolio/Agent contributors. Every section echoes
that cutoff and may not return a later fact. Aggregate contributors in fixed order, reject duplicate section codes,
and keep one section failure from erasing other safe summaries while surfacing its error state.

- [ ] **Step 3: Verify and commit**

```bash
cd /Users/mario/SelfProject/CyberMario/be
./mvnw -Dmaven.build.cache.enabled=false \
  -Dtest=InvestmentOverviewControllerTests,InvestmentOverviewIntegrationTests,InvestmentOverviewCutoffTests \
  test
git add be/src/main/java/top/egon/mario/investment/overview \
  be/src/test/java/top/egon/mario/investment/overview
git commit -m "feat(investment): complete overview projection"
```

### Task FE-09: Add The Investment Overview

**Owner:** preset `react-specialist`

**Dependencies:** FE-02, FE-05, FE-06, FE-07 and BE-19.

**Files:**

- Create: `fe/src/modules/investment/types/investmentOverviewTypes.ts`
- Create: `fe/src/modules/investment/services/investmentOverviewService.ts`
- Create: `fe/src/modules/investment/services/investmentOverviewService.test.ts`
- Create: `fe/src/modules/investment/overview/InvestmentOverviewPage.tsx`
- Create: `fe/src/modules/investment/overview/InvestmentOverviewPage.test.tsx`

- [ ] **Step 1: Write RED aggregate-state tests**

Cover no-workspace create/select empty state with zero Overview request, plus selected-workspace market/account/
position/risk/recent backtest/recent Agent sections, partial section failure, stale-data warnings, explicit navigation
and workspace switch race.

- [ ] **Step 2: Implement only summaries and navigation**

Do not duplicate full Market/Quant/Portfolio/Agent tables or business calculations. After workspace selection, one
Overview API request owns the snapshot cutoff. Before selection, render only the workspace empty state; public
market data remains available on the Market page.

- [ ] **Step 3: Verify and commit**

```bash
cd /Users/mario/SelfProject/CyberMario/fe
bun run test -- investmentOverviewService.test.ts InvestmentOverviewPage.test.tsx
bun run typecheck
git add fe/src/modules/investment/types/investmentOverviewTypes.ts \
  fe/src/modules/investment/services/investmentOverviewService* \
  fe/src/modules/investment/overview
git commit -m "feat(fe): add investment overview"
```

### Task FE-10: Wire Routes, Menus, Permission Impact, And Frontend Docs

**Owner:** preset `react-specialist`

**Dependencies:** FE-01 through FE-09 plus FE-11. This task exclusively owns shared route/menu files.

**Files:**

- Modify: `fe/src/app/routes.tsx`
- Modify: `fe/src/app/routes.test.ts`
- Modify: `fe/src/layouts/AdminLayout/menu.tsx`
- Modify: `fe/src/layouts/AdminLayout/menu.test.tsx`
- Modify: `fe/src/layouts/AdminLayout/permissionImpact.ts`
- Modify: `fe/src/layouts/AdminLayout/permissionImpact.test.ts`
- Modify: `fe/README.md`

- [ ] **Step 1: Write RED lazy-route/canonical-menu tests**

Required routes:

```text
InvestmentWorkspaceLayout
  /investment/overview
  /investment/market
  /investment/instruments/:instrumentId
  /investment/research
  /investment/quant
  /investment/portfolio
  /investment/agent

Independent admin route
  /investment/platform
```

Test two sidebar entries only: “投资工作台” at `/investment/overview`, and admin-only “投资平台” at
`/investment/platform`. Canonicalize all workspace child paths to overview; instrument detail keeps Market tab active.

- [ ] **Step 2: Implement lazy routes and RBAC integration**

Follow existing lazy `Component` exports. The backend menu tree controls visibility; frontend route guards/impact
mapping provide defense and understandable permission changes. Platform is not a workspace tab.

- [ ] **Step 3: Verify and commit**

```bash
cd /Users/mario/SelfProject/CyberMario/fe
bun run test -- routes.test.ts menu.test.tsx permissionImpact.test.ts InvestmentWorkspaceLayout.test.tsx
bun run typecheck
bun run build
git add fe/src/app/routes.tsx fe/src/app/routes.test.ts \
  fe/src/layouts/AdminLayout fe/README.md
git commit -m "feat(fe): wire investment routes and permissions"
```

### Task DB-05: Add Real PostgreSQL Contract And Concurrency Tests

**Owner:** preset `postgres-pro`

**Dependencies:** DB-01 through DB-04 and BE-04/BE-05/BE-14/BE-15/BE-17 committed. This is test-only and must not
alter any migration.

**Files:**

- Create: `be/src/test/java/top/egon/mario/investment/InvestmentPostgresContractIT.java`
- Create: `be/src/test/java/top/egon/mario/investment/InvestmentPostgresPersistenceIT.java`

- [ ] **Step 1: Write opt-in disposable PostgreSQL tests**

Follow `ImPostgresContractIT` explicit environment contract. Test actual JSONB/timestamptz/numeric types, named
constraints/index order, full Flyway V39-to-Investment upgrade and repeat `validate`.

- [ ] **Step 2: Cover PostgreSQL-only behavior**

Test:

- same checksum no revision; corrected bar/funding retains both revisions and resolves by `data_as_of`;
- concurrent `ON CONFLICT` enqueue/write idempotency;
- two `SKIP LOCKED` claims do not overlap; lease recovery fences the stale token;
- two over-limit concurrent account intents yield exactly one `PENDING_MATCH`, one `RISK_REJECTED`, one order/job,
  and fixed lock order does not deadlock;
- fill/funding/intent retry creates one fact/ledger effect;
- Agent crash after Facade commit but before decision-intent link recovers through the same idempotency key and leaves
  one intent/order/financial effect;
- FK/unique constraints reject workspace/account/instrument mismatch.

- [ ] **Step 3: Run when a disposable DB is explicitly available**

```bash
cd /Users/mario/SelfProject/CyberMario/be
INVESTMENT_POSTGRES_TEST_URL=jdbc:postgresql://localhost:5432/cyber_mario_investment_test \
INVESTMENT_POSTGRES_TEST_USERNAME=postgres \
INVESTMENT_POSTGRES_TEST_PASSWORD=postgres \
./mvnw -Dmaven.build.cache.enabled=false \
  -Dtest=InvestmentPostgresContractIT,InvestmentPostgresPersistenceIT test
```

The database must be disposable and support extensions required by the existing full migration chain. If unavailable,
do not point the test at a shared DB; report this gate as unverified, not passed.

- [ ] **Step 4: Commit tests once**

```bash
git add be/src/test/java/top/egon/mario/investment/InvestmentPostgresContractIT.java \
  be/src/test/java/top/egon/mario/investment/InvestmentPostgresPersistenceIT.java
git commit -m "test(investment): add postgres contract gates"
```

### Task BE-18: Add Vertical Flow, Security, And Logging Gates

**Owner:** preset `spring-boot-engineer`

**Dependencies:** All backend implementation tasks and DB-01 through DB-04. Safe in parallel with DB-05 because
the test files are disjoint.

**Files:**

- Create: `be/src/test/java/top/egon/mario/investment/InvestmentVerticalFlowTests.java`
- Create: `be/src/test/java/top/egon/mario/investment/InvestmentSecurityBoundaryTests.java`
- Create: `be/src/test/java/top/egon/mario/investment/InvestmentLoggingConventionsTests.java`
- Create: `be/src/test/java/top/egon/mario/investment/InvestmentProductionEmptyContextTests.java`

- [ ] **Step 1: Add the complete fixture-provider vertical flow**

Using test-only subscriptions/provider/strategy and a fixed model client, prove:

```text
ingest revisioned market data
  -> market/indicator/report
  -> snapshot + backtest
  -> create account/risk
  -> manual intent -> risk -> pending -> match -> ledger/position
  -> Agent decision -> intent -> risk -> pending -> match -> ledger/position
```

No live provider/model/server is started.

- [ ] **Step 2: Add security and redaction regressions**

Cover unauthenticated/permission denied, cross-workspace/account/report/backtest/run access, admin-private boundary,
strict strategy request, no real-trading endpoint/credential property, no global Investment callback and no private
portfolio/report/tool payload in application logs.
`InvestmentProductionEmptyContextTests` starts the test application context without fixture beans and proves provider,
subscription and strategy registries are empty; planners create no jobs; market/strategy APIs return supported empty
results; and no Provider, model or Redis external call occurs.

- [ ] **Step 3: Verify and commit**

```bash
cd /Users/mario/SelfProject/CyberMario/be
./mvnw -Dmaven.build.cache.enabled=false \
  -Dtest=InvestmentVerticalFlowTests,InvestmentSecurityBoundaryTests,InvestmentLoggingConventionsTests,InvestmentProductionEmptyContextTests \
  test
git add be/src/test/java/top/egon/mario/investment/InvestmentVerticalFlowTests.java \
  be/src/test/java/top/egon/mario/investment/InvestmentSecurityBoundaryTests.java \
  be/src/test/java/top/egon/mario/investment/InvestmentLoggingConventionsTests.java \
  be/src/test/java/top/egon/mario/investment/InvestmentProductionEmptyContextTests.java
git commit -m "test(investment): add backend integration gates"
```

### Task INT-VERIFY-01: Run Full Static And PostgreSQL Quality Gates

**Owner:** main agent; validation-only, no commit.

**Dependencies:** DB-05, BE-18, FE-10 and FE-11 are committed and all focused task tests passed.

- [ ] **Step 1: Run backend gates**

```bash
cd /Users/mario/SelfProject/CyberMario/be
./mvnw -Dmaven.build.cache.enabled=false test
```

- [ ] **Step 2: Run frontend gates**

```bash
cd /Users/mario/SelfProject/CyberMario/fe
bun install --frozen-lockfile
bun run lint
bun run typecheck
bun run test
bun run build
bun run analyze
```

`analyze` must show `lightweight-charts` in the lazy Instrument route chunk rather than the initial shell chunk.

- [ ] **Step 3: Run PostgreSQL gates if the disposable DB is available**

Use the exact DB-05 command. H2 success does not substitute for PostgreSQL lock/JSONB/MVCC evidence.

- [ ] **Step 4: Record evidence before review**

Record exact command, exit code, date, environment and relevant counts for every gate. If disposable PostgreSQL is
unavailable, mark only that gate `UNVERIFIED`; do not let QA return GO or call the release `RELEASE_GO`.

### Task QA-01: Run The Risk-based Release Review

**Owner:** preset `qa-expert` (read-only)

**Dependencies:** INT-VERIFY-01 has produced complete evidence, including an explicit PG result or `UNVERIFIED`.

- [ ] Map every acceptance item below to an automated test, exact command and observed result.
- [ ] Treat schema/migration, private-data isolation, financial idempotency, Agent tool scope and no-lookahead as
  release blockers.
- [ ] Inspect empty registries, stale/missing data, duplicate submit, lease recovery, polling cleanup, large candle/
  equity result size, accessibility labels and lazy chunking.
- [ ] Record environment-only checks separately. Do not claim real Bitget/model/browser behavior was tested.
- [ ] Do not return `RELEASE_GO` unless DB-05 has actually passed on disposable PostgreSQL; H2 is insufficient.
- [ ] Return GO only when every blocker has evidence; otherwise return NO-GO with the smallest `FIX-*` scope.

This is a review gate and creates no commit unless it identifies missing coverage; any correction becomes a new,
scoped `FIX-QA-*` task/commit owned by the appropriate preset. After any correction, rerun its focused tests and
INT-VERIFY-01 before QA-01 is repeated.

### Task REVIEW-01: Final Evidence-driven Code Review

**Owner:** preset `code-reviewer` (read-only)

**Dependencies:** QA-01 completed and all QA Critical/High findings are resolved. A PG-only `UNVERIFIED` status may
remain visible for implementation handoff, but prevents `RELEASE_GO`.

- [ ] Review the complete Investment diff only; do not request unrelated refactors.
- [ ] Check comments/logging/validation/empty states/exceptions/modularity/concurrency/tests/API compatibility/security/
  transactions/idempotency against repository conventions.
- [ ] Trace one normal analysis path, one risk-rejected Agent path and one concurrent financial retry path.
- [ ] Create only the smallest `FIX-REVIEW-*` task for Critical/High findings. Medium/Low items are recorded with impact.
- [ ] Confirm no historical Flyway file changed and no Bitget/private trading code or credential field exists.

Any review correction returns to its focused tests, INT-VERIFY-01 and QA-01 before REVIEW-01 is repeated. A stale
pre-fix validation result is never release evidence.

### Task INT-DOCS-01: Update Root Documentation And Record Final Status

**Owner:** main agent; no subagent writes shared root docs concurrently.

**Dependencies:** latest INT-VERIFY-01 ran after all fixes, all available gates pass, PostgreSQL is either PASS or
explicitly `UNVERIFIED`, QA-01 is complete and REVIEW-01 has no unresolved Critical/High finding.

**Files:**

- Modify: `README.md`
- Modify: `docs/superpowers/specs/2026-07-16-investment-module-design.md` only if implementation evidence requires
  a factual synchronization; never silently change confirmed product scope.

- [ ] **Step 1: Update root README without runtime claims**

Document module scope, paper-only warning, production-empty provider/strategy registries, code extension seams and
validation commands. Do not say Bitget, live Agent model or browser flow was verified.

- [ ] **Step 2: Check diff and commit once**

```bash
cd /Users/mario/SelfProject/CyberMario
git status --short
git diff --check
git diff --name-only $(git merge-base HEAD main)..HEAD
git add README.md docs/superpowers/specs/2026-07-16-investment-module-design.md
git commit -m "docs(investment): document module delivery"
```

If the design spec did not need a factual update, stage only `README.md`.

---

## Acceptance Traceability

| Confirmed requirement | Owning tasks | Required evidence |
|---|---|---|
| Crypto futures only; no spot/live trading | BE-01, BE-10, BE-18, FE-06/07 | enum/boundary tests; no private client/credential/real order path |
| Code-controlled selective data ingestion | BE-03, BE-06, FE-08 | empty production registry; out-of-subscription rejection; no write UI/API |
| Daily permanent, selected 1m two years | DB-01, BE-06, BE-11 | retention tests; referenced snapshots prevent unsafe deletion |
| Traditional analysis, K line and reports | BE-07/08/12/15/17, FE-02/03/04/11 | candle/indicator/six report types/marker tests; cutoff/evidence visible |
| Java code-only strategies | BE-09, BE-12, FE-05 | no CRUD/editor/parameter fields; unknown JSON rejected; release hash captured |
| Reproducible futures backtest | DB-01/02, BE-10/11/12, DB-05 | revision-as-of replay, no-lookahead, deterministic repeat and PG contract tests |
| Paper contract account and position | DB-03, BE-13/14/15, FE-06 | exact margin/funding/fee/liquidation tests and portfolio ownership tests |
| Agent automatic paper trade, no per-order confirmation | BE-16/17, FE-07/11 | read-only LLM phase, one validated execution path, same-key crash recovery and one logical effect |
| Mandatory risk gate | BE-13/14/17 | every rule persisted; one failure creates no order; Agent cannot bypass facade |
| Private workspace/account data + platform data | BE-02/07/18/19, FE-01/08/09 | owner-scoped repositories, one overview cutoff, admin-private boundary |
| PostgreSQL optimization without premature tuning | DB-01..05 | ordinary indexes first; real PG lock/type tests; no partition/BRIN without evidence |
| Web only | FE-01..11 | routes/build/tests; no PDF/Excel/mobile artifacts |

## Parallel Dispatch Matrix

The main agent may fill at most the available preset-agent slots with ready tasks. These combinations are safe because
their write scopes do not overlap:

| Wave | Preset tasks that may run together | Join condition |
|---|---|---|
| 1A | `postgres-pro` DB-01 + `spring-boot-engineer` BE-01 | Both commits reviewed; no migration edits by backend owner |
| 1B | After BE-07: `spring-boot-engineer` BE-08 + `react-specialist` FE-01 | Both now have their exact API/RBAC contracts |
| 1C | `spring-boot-engineer` ready backend work + `react-specialist` FE-02 then disjoint FE-03/04/08 | Each FE task waits for its exact API; parallel FE instances require disjoint files and exact staging |
| 2A | `postgres-pro` DB-02 + `spring-boot-engineer` BE-10 + `react-specialist` remaining Phase-1 UI | DB, pure Java kernel and React do not overlap |
| 2B | `spring-boot-engineer` BE-11/12 sequential + `react-specialist` remaining FE-03/04/08 | FE-05 starts only after BE-12 commits |
| 3A | `postgres-pro` DB-03 + `react-specialist` FE-05/Phase-2 fixes | No shared files |
| 3B | After BE-15: `llm-architect` BE-16 + `postgres-pro` DB-04 + `react-specialist` FE-06 | Generic Agent, migration and Portfolio UI are disjoint |
| 4A | `llm-architect` BE-17 + `react-specialist` remaining FE-06 | FE-07 waits until BE-17 commits |
| 4B | After BE-17: `spring-boot-engineer` BE-19 + `react-specialist` FE-07 | Overview backend and Agent UI are disjoint |
| 5A | `postgres-pro` DB-05 + `spring-boot-engineer` BE-18 + `react-specialist` FE-11 | PG IT, backend gates and Instrument UI are disjoint |
| 5B | `react-specialist` FE-09 then FE-10 while DB/backend gates finish | FE-09 waits for BE-19; FE-10 waits for FE-11 |
| 5C | Main agent INT-VERIFY-01, then `qa-expert` QA-01, then `code-reviewer` REVIEW-01 | Sequential; reviewers consume observed full-gate evidence |
| 5D | Main agent INT-DOCS-01 | Runs only after the latest fix/reverify/review loop is closed |

Additional rules:

- One active writer per package/file. A task that says “reviewed by” receives review only after its writer finishes.
- Do not dispatch two agents to modify `InvestmentAccessService`, `application.yaml`, `be/pom.xml`, routes/menu,
  shared portfolio types, generic Agent runtime or PostgreSQL IT at the same time.
- The main agent reviews every result and runs the focused command before commit. A subagent completion message is not
  acceptance evidence by itself.
- Clean up each finished subagent before creating the next task instance. Do not reuse a completed subagent for a new
  task.
- Never interrupt a normally running subagent; wait for its final result or final blocker report.

## Test Data And Environment

Automated tests must be deterministic and self-contained:

- Test venue/source/instruments use synthetic names and never imply Bitget was integrated.
- Fixture provider supplies contract spec, tier sets, trade/mark/index bars, funding and quote at explicit UTC times.
- Fixture strategy is test-only and fixed in Java; it cannot be discovered in a production Spring context.
- Fixture model client returns valid HOLD/open/close decisions plus malformed/cross-account cases; no live model call.
- Users include owner A, owner B, platform admin and ordinary non-Investment user for data-scope tests.
- Paper fixtures include exact wallet, leverage, tier, fee, funding and slippage numbers for `BigDecimal` assertions.
- Job fixtures use injected `Clock` and deterministic token source; tests do not sleep for leases or polling.
- PostgreSQL tests use an explicitly disposable database. H2 remains the default fast suite.

## Release Blockers

Any one of the following is NO-GO:

- A historical bar/funding correction makes an old dataset hash unreplayable.
- Unknown strategy fields are accepted, or frontend/API can create/modify a strategy/subscription.
- A normal chat runtime can see an Investment scoped tool, or any LLM runtime receives a trading write callback.
- Any Agent/strategy/manual intent bypasses `PaperTradingFacade` or deterministic risk checks.
- Duplicate/retried work produces a second fill, funding charge, ledger effect or order.
- A transaction holds database locks while running HTTP, LLM or backtest computation.
- Cross-workspace/account/report/backtest/run access succeeds for another user or platform admin by default.
- Missing/stale mark price, funding or tier is replaced with zero/current/future data.
- An API reports executed before a fill transaction exists.
- An existing Flyway migration changes or more than one migration is created for one planned DB task.
- Backend full tests, frontend lint/typecheck/test/build, or required disposable PostgreSQL tests fail.

## Known Non-blocking Limits

- 1m OHLC cannot reproduce order-book queue or intraminute price path; UI labels results “Bar 模拟结果”.
- Canvas K line is not a semantic data table; accessible text summary and expandable data view are required.
- A live Agent run depends on existing configured model availability. Automated delivery proof uses a fixed model stub.
- Physical 1m retention may temporarily exceed two years while an unarchived reproducible snapshot references it.
- Production pages are intentionally empty until Java subscription and private strategy commits are supplied.
- Performance tuning beyond the initial indexes awaits real row counts, query plans, table size and dead-tuple evidence.

## Implementation Completion Checklist

- [ ] All DB-01..DB-05, BE-01..BE-19, FE-01..FE-11 and any explicit FIX task commits exist with no mixed scopes;
  INT-VERIFY-01, QA-01, REVIEW-01 and INT-DOCS-01 evidence is complete.
- [ ] Approved spec and implementation agree on confirmed contract boundary and all plan-time corrections.
- [ ] All focused test commands were observed passing after each task.
- [ ] The latest post-fix INT-VERIFY-01 backend and frontend gates pass; no server or browser was started by the
  implementation agent.
- [ ] If PostgreSQL-only gates cannot run, status is exactly `IMPLEMENTATION_COMPLETE / RELEASE_UNVERIFIED`; QA-01
  must not return GO.
- [ ] Status becomes `RELEASE_GO` only after DB-05 and the latest INT-VERIFY-01 pass on disposable PostgreSQL,
  QA-01 returns GO and REVIEW-01 has no unresolved Critical/High finding.
- [ ] `git diff --check` passes; historical migrations and unrelated user changes are untouched.
- [ ] README states paper-only, empty registries, extension points and honest validation status.

## Execution Handoff

Start with Preflight, then execute one implementation task/commit at a time following dependencies; explicitly
read-only review and validation tasks create evidence but no commit. Use only the preset subagents listed above. The
main agent owns merges and commits, does not start the project, and stops only for a real product scope change or
external environment blocker.

No product clarification is required to implement this plan with empty production registries. User input becomes
necessary only when adding the first real subscription list, the first private production strategy, Bitget adapter,
new contract modes, or live-trading scope.
