# MCP Runtime Redis Refresh Design

**日期:** 2026-06-25

**目标:** 将 MCP server/tool 配置变更后的 Agent 运行时刷新改造成 Redis Pub/Sub listener 机制，使 `/chat` 和 Agent 调试运行时在单机和后续分布式部署中都能拿到最新可见 MCP 工具。

**当前背景:** MCP 管理后台已经能保存 server、tool、policy，并且 `McpRuntimeRegistry` 会按 `server.enabled`、`server.status == CONNECTED`、`tool.enabled`、`!tool.requireConfirm` 和运行时 client 的 `listTools()` 结果生成 Agent 可见的 MCP `ToolCallback`。当前缺口在刷新链路: `McpAgentRefreshService` 只是自增 `AtomicLong`，生产路径没有消费者；tool policy 更新、tool enable/disable、discover-tools 只调用这个 refresh marker，不会刷新 `DynamicMcpClientManager` 或跨节点通知。因此 DB 中工具已经启用时，`/chat` 的 `AgentRuntimeSpec.toolConfig.enabledToolNames` 仍可能只包含旧的本地工具，模型请求里的 `available_tools_json` 也不会出现新 MCP 工具。

---

## 1. 需求范围

### 1.1 本次实现

- 用 Redis Pub/Sub 广播 MCP runtime refresh 事件，复用项目已有 RBAC cache broadcast/listener 风格。
- 统一 MCP server/tool/discovery 变更后的刷新入口，避免 controller 直接散落调用 `refreshServer()`、`disableServer()` 或无效 `refresh()`。
- 本节点在变更成功后必须立即刷新本地 runtime；Redis 发布失败只影响其他节点，不影响当前节点生效。
- 其他节点通过 Redis listener 收到事件后刷新本地 `DynamicMcpClientManager`，使后续 Agent 请求能重新构建 MCP callbacks。
- 继续复用 `McpRuntimeRegistry` 的现有可见性判断，不放宽安全策略。
- 保持 `/chat`、Agent debug、MCP admin API 的对外接口兼容。
- 增加聚焦单元测试，覆盖 broadcaster、subscriber、触发点和 Agent runtime 能拿到刷新后的 MCP 工具。

### 1.2 暂不实现

- 不引入 DB outbox、版本表、定时补偿扫描或强一致分布式协议。
- 不新增数据库迁移。
- 不改 MCP SDK、transport 协议或 tool callback 构建模型。
- 不改变 `requireConfirm=true` 时工具不能进入 Agent 的 Stage 1 策略。
- 不重做 MCP 管理前端。当前前端已有 `requireConfirm` 和 `runtimeStatus` 类型及列表/抽屉展示逻辑，本次只把运行时状态刷新作为验收点。
- 不启动项目。

---

## 2. 已确认方案

本次只采用 Redis Pub/Sub + 本地刷新兜底方案。该方案符合后续分布式方向，改动集中，可以复用 RBAC 已有 Redis listener 写法，并且不需要新增数据库迁移。

Redis Pub/Sub 是尽力而为机制，没有离线重放。当前需求接受这个边界: Redis 发布失败时，本节点仍然即时生效，其他节点可能暂时保持旧状态，直到下一次 MCP 变更事件或节点重启后重新加载。后续如果要求强一致或可补偿重放，再单独引入 DB 版本表或 outbox。

新增一个 MCP runtime refresh coordinator 作为 Facade，统一处理 “本地刷新 + Redis 广播”。所有会影响 Agent MCP 工具可见性的写操作都调用 coordinator，而不是直接操作 `DynamicMcpClientManager` 或只打版本标记。

Redis subscriber 只处理非本节点消息，收到后调用本地 runtime refresher。这样发布节点不依赖 Redis 回环也能立即生效，其他节点通过 Pub/Sub 跟进。刷新逻辑保持幂等: 同一个 server 多次 `refreshServer(serverId)` 只会关闭旧 client、按当前 DB 状态重建或标记失败；disable/delete 多次消费也应保持 DISABLED 或无 client。

---

## 3. 后端设计

### 3.1 包和类边界

在 `top.egon.mario.agent.mcp.runtime` 下新增或调整以下类:

- `McpRuntimeRefreshCoordinator`
  - 对外提供 `refreshServer(Long serverId, String reason)`、`disableServer(Long serverId, String reason)`、`refreshAll(String reason)`。
  - 先执行本地 runtime 操作，再发布 Redis broadcast。
  - 捕获 Redis 发布异常并记录 warn，不回滚已经完成的本地刷新。
- `McpRuntimeRefreshBroadcaster`
  - 使用 `StringRedisTemplate.convertAndSend(...)` 发布 JSON 消息。
  - 风格参考 `RbacCacheEvictionBroadcaster`。
- `McpRuntimeRefreshSubscriber`
  - 实现 `MessageListener`。
  - 解析消息、跳过本节点 source、按 event type 调用本地 refresher。
  - 风格参考 `RbacCacheEvictionSubscriber`。
- `McpRuntimeRefreshMessage`
  - Redis 消息模型。
- `McpRuntimeRefreshProperties`
  - 配置 prefix 建议为 `agent.mcp.runtime.refresh`。
  - 字段包含 `broadcastEnabled` 和 `broadcastTopic`。
  - 默认 topic: `agent:mcp:runtime:refresh`。
- `McpRuntimeInstanceIdentity`
  - 本节点标识，用于跳过自己发布的 Redis 消息。
  - 可复用 RBAC identity 的实现风格，但不要耦合 RBAC 包。
- `McpRuntimeRefreshConfiguration`
  - 注册 `RedisMessageListenerContainer` 或复用项目一致的 listener container 写法。
  - 仅在 `agent.mcp.runtime.enabled=true` 且 `broadcastEnabled=true` 时监听 topic。

保留 `DynamicMcpClientManager` 作为本地 runtime client 管理者。`McpAgentRefreshService` 不再作为无消费者的版本标记使用，可以改造成 coordinator 的兼容入口，或删除并替换所有注入点。实现时优先选择清理无效语义，避免继续留下误导性的 refresh marker。

### 3.2 事件模型

`McpRuntimeRefreshMessage` 字段建议:

- `String sourceInstanceId`
- `EventType eventType`
- `Long serverId`
- `String reason`
- `Instant createdAt`

`EventType`:

- `SERVER_REFRESH`: 某个 server 配置、启用状态、tool policy 或 tool discovery 变化，需要按当前 DB 状态重建该 server runtime client。
- `SERVER_DISABLE`: 某个 server 被禁用或删除，需要移除本地 runtime client，并确保 server 状态为 `DISABLED`。
- `ALL_REFRESH`: 启动后或管理类全量操作需要重新加载所有 enabled server。第一版主要作为扩展和手工调用入口，普通写操作优先使用 server 级事件。

`reason` 用固定短字符串，便于日志和测试断言:

- `server_update`
- `server_enable`
- `server_disable`
- `server_delete`
- `tool_policy_update`
- `tool_enable`
- `tool_disable`
- `tool_discover`
- `startup_or_manual`

### 3.3 触发点

统一把以下路径改为调用 `McpRuntimeRefreshCoordinator`:

- `AdminMcpServerController#update`
  - 现状: service update 后直接 `clientManager.refreshServer(id)`。
  - 目标: `coordinator.refreshServer(id, "server_update")`。
- `AdminMcpServerController#enable`
  - 目标: `coordinator.refreshServer(id, "server_enable")`。
- `AdminMcpServerController#disable`
  - 目标: `coordinator.disableServer(id, "server_disable")`。
- `AdminMcpServerController#delete`
  - 目标: `coordinator.disableServer(id, "server_delete")`。
- `AdminMcpServerController#discoverTools`
  - 现状: `McpToolDiscoveryService#discover` 内部只调用无效 refresh marker。
  - 目标: discover 成功后触发 `coordinator.refreshServer(id, "tool_discover")`。
- `AdminMcpToolController#updatePolicy`
  - 目标: `coordinator.refreshServer(response.serverId(), "tool_policy_update")`。
- `AdminMcpToolController#enable`
  - 目标: 根据 tool 所属 server 触发 `SERVER_REFRESH`。
- `AdminMcpToolController#disable`
  - 目标: 根据 tool 所属 server 触发 `SERVER_REFRESH`，因为 server client 可以保留，但 Agent 可见 tool snapshot 需要后续请求重新读取 DB/tool callback。

为了让 tool enable/disable 不再额外查询 DB，`McpToolConfigService#enable/disable` 可以返回 `McpToolResponse` 或至少返回 `serverId`。这是小范围 API 内部调整，不改变 HTTP 响应结构也可以通过 controller 内部查询实现。优先选择最少重复查询且测试清晰的内部返回值。

### 3.4 事务边界

刷新必须在 DB 写入成功之后读取到新状态。

当前 controller 的 `blocking(() -> { service.transactionalMethod(); nextCall(); })` 中，service 是独立 Spring bean，`@Transactional` 方法返回后事务已经提交，因此 controller 后续调用 coordinator 能读到提交后的 DB 状态。第一版可以沿用这个边界，不引入额外应用事件。

如果实现时把 refresh 触发下沉到 service 内部，必须使用 `@TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = true)`，避免事务未提交就刷新 runtime。除非需要统一 service 内部触发，否则本次优先保持 controller 后置调用，改动更小。

### 3.5 本地刷新语义

`McpRuntimeRefreshCoordinator` 本地执行规则:

- `refreshServer(serverId, reason)`
  - 调用 `DynamicMcpClientManager.refreshServer(serverId)`。
  - 该方法按当前 DB 状态决定 CONNECTED、FAILED、DISABLED 或无操作。
  - 成功或失败状态均应落库，由现有 manager 负责。
- `disableServer(serverId, reason)`
  - 调用 `DynamicMcpClientManager.disableServer(serverId)`。
  - 移除本地 client 并写入 DISABLED。
- `refreshAll(reason)`
  - 调用 `DynamicMcpClientManager.reloadEnabledServers()`。

需要保留现有 `DynamicMcpClientManager` 的 best-effort close 行为，避免关闭旧 client 失败阻塞新状态生效。

### 3.6 Redis 失败策略

- Redis 发布失败: 记录 warn，HTTP 请求仍按本地 DB 写入和本地刷新结果返回。
- Redis listener 消费失败: 记录 warn，不向外抛异常中断 listener container。
- JSON 序列化失败: 记录 warn；消息不发布。
- 收到未知 event type 或缺少必要 `serverId`: 记录 warn 并跳过。
- 收到本节点 source message: debug 日志跳过，避免重复刷新。

这意味着跨节点刷新是最终一致、尽力而为。若未来需要保证所有节点都能补偿拉齐，再单独设计版本表或 outbox 机制。

---

## 4. `/chat` Agent 工具可见性影响

本次不改变 `DefaultAgentRuntimeFactory` 和 `AgentPresetServiceImpl` 的核心策略，只修复它们依赖的运行时 MCP snapshot。

变更后默认 `/chat` 流程应为:

1. 管理员启用 MCP server，并发现 tools。
2. 管理员将目标 tool 设置为 `enabled=true` 且 `requireConfirm=false`。
3. coordinator 在本节点刷新对应 server runtime client，并广播 Redis。
4. 其他节点 listener 收到事件后刷新本地 runtime client。
5. 下一次 `/demo/chat/stream` 请求进入 `AgentPresetServiceImpl#defaultRuntimeSpec()`。
6. `registeredToolNames()` 通过 `McpAgentToolProvider.currentToolCallbacks()` 读取当前可见 MCP callbacks。
7. `DefaultAgentRuntimeFactory.enabledTools(spec)` 构建本次 Agent 可用工具。
8. `agent_run_audit.effective_config_json.toolConfig.enabledToolNames` 和 `MODEL_REQUEST.available_tools_json` 应包含新 MCP tool name。

如果 tool 被禁用、server 被禁用、server 状态失败或 `requireConfirm=true`，`McpRuntimeRegistry` 仍应过滤该工具。

---

## 5. 配置设计

在 `application.yaml` 增加 MCP runtime refresh 配置，保持 env override:

```yaml
agent:
  mcp:
    runtime:
      enabled: ${AGENT_MCP_RUNTIME_ENABLED:true}
      refresh:
        broadcast-enabled: ${AGENT_MCP_RUNTIME_REFRESH_BROADCAST_ENABLED:true}
        broadcast-topic: ${AGENT_MCP_RUNTIME_REFRESH_BROADCAST_TOPIC:agent:mcp:runtime:refresh}
```

如果当前项目已有 `agent.mcp.runtime.enabled` 但未显式配置在 `application.yaml`，本次可以补充到同一层级。注意不要改动 unrelated 配置或已有敏感值。

测试资源中已有 `agent.mcp.runtime.enabled=false` 的场景，新增 listener configuration 和 properties 测试时要保持该默认关闭语义，避免普通 Spring 上下文测试因 Redis listener 自动启动而变慢或失败。

---

## 6. 前端和管理页边界

当前前端 MCP 管理模块已经包含:

- `McpToolResponse.requireConfirm`
- `McpToolResponse.runtimeStatus`
- `McpToolListPage` 的 “需要确认” 和 “运行状态” 列
- `McpToolPolicyDrawer` 的 `requireConfirm` 开关

因此本次无需新增 UI 字段。验收时需要确认:

- 工具策略保存后列表重新加载，状态字段能反映后端结果。
- `requireConfirm=true` 时 enable 操作仍被后端拒绝或策略保存后自动禁用，前端能看到该状态。
- `runtimeStatus=AVAILABLE` 且 tool enabled、server connected 后，下一次 `/chat` 审计里能看到该 MCP tool。

如果实际浏览器仍看不到字段，应作为单独 UI 回归处理，避免扩大本次 Redis listener 改造范围。

---

## 7. 测试计划

### 7.1 单元测试

新增或调整测试:

- `McpRuntimeRefreshBroadcasterTests`
  - broadcast enabled 时发布正确 topic 和 JSON。
  - broadcast disabled 时不发布。
  - Redis 异常不向外抛。
- `McpRuntimeRefreshSubscriberTests`
  - 非本节点 `SERVER_REFRESH` 调用 local refresh。
  - 非本节点 `SERVER_DISABLE` 调用 local disable。
  - 非本节点 `ALL_REFRESH` 调用 local reload。
  - 本节点消息跳过。
  - 非法 JSON 或缺字段不抛出。
- `AdminMcpToolControllerTests`
  - update policy / enable / disable 触发对应 server refresh。
- `AdminMcpServerControllerTests`
  - update / enable 触发 refresh。
  - disable / delete 触发 disable。
  - discover-tools 成功后触发 refresh。
- `McpToolDiscoveryServiceTests`
  - 移除对无效 `McpAgentRefreshService.version()` 的断言，改为由 controller/coordinator 测试覆盖刷新触发。

### 7.2 Runtime 回归测试

在现有 `McpRuntimeRegistryTests` 或新增 focused test 中覆盖:

- server connected、tool enabled、`requireConfirm=false`、runtime client listTools 包含 tool 时，`currentToolCallbacks()` 返回 prefixed MCP tool。
- policy 从 blocked 调整为 visible 后，coordinator refresh 后 `AgentPresetServiceImpl.defaultRuntimeSpec()` 可包含 MCP tool name。
- disabled 或 `requireConfirm=true` 的 tool 不进入 callback 列表。

### 7.3 建议验证命令

实现阶段优先运行:

```bash
cd be
mvn -Djava.version=21 -Dmaven.build.cache.enabled=false \
  -Dtest=McpRuntimeRefreshBroadcasterTests,McpRuntimeRefreshSubscriberTests,AdminMcpToolControllerTests,AdminMcpServerControllerTests,McpRuntimeRegistryTests \
  test
```

如果 Maven test selection 因仓库既有测试漂移失败，应至少运行 targeted compile:

```bash
cd be
mvn -Djava.version=21 -Dmaven.build.cache.enabled=false -DskipTests compile
```

文档/spec 阶段只做文本 diff 检查，不启动项目。

---

## 8. 设计模式考虑

本次使用两个轻量模式:

- Observer/Pub-Sub: Redis topic 作为跨节点通知通道，各节点 subscriber 观察 MCP runtime refresh 事件并更新本地 runtime。
- Facade/Coordinator: `McpRuntimeRefreshCoordinator` 封装本地刷新和广播，避免 controller/service 了解 Redis 细节，也避免继续散落直接调用 `DynamicMcpClientManager`。

不引入 Strategy、Factory 或复杂事件总线。当前事件类型很少，直接 enum switch 更清晰，也符合已有 RBAC cache subscriber 风格。

---

## 9. 风险和边界

- Redis Pub/Sub 不保证离线重放。节点离线期间错过事件时，可能直到下一次 MCP 变更或重启后才拉齐 runtime。
- MCP server 本身连接失败仍会导致 server status 变为 `FAILED`，工具不会进入 Agent；本次不改变连接失败处理。
- 如果 `DefaultAgentRuntimeFactory` 或 Agent runtime 后续引入缓存，需要确保缓存 key 或失效机制不会把旧 MCP tool list 长期固定住。当前工厂每次请求构建 runtime，风险较低。
- 多节点同时修改同一 server/tool 时，以数据库最终状态为准。listener 事件顺序不是强一致，但每次 refresh 都读取 DB 当前值，最终会收敛。
- `DynamicMcpClientManager.disableServer()` 当前会写 DB `enabled=false` 和 `DISABLED`。对于 listener 消费 delete/disable 消息这是可接受的幂等结果；实现时不要让远端 listener 误改除目标 server 以外的数据。

---

## 10. 验收标准

- 更新 tool policy、enable/disable tool、discover tools 后，本节点下一次 `/chat` 能按 DB 策略看到最新 MCP tool。
- 在两个应用节点连接同一个 Redis topic 的情况下，节点 A 修改 MCP 配置后，节点 B 下一次 `/chat` 也能看到最新 MCP tool。
- `agent_run_audit.effective_config_json.toolConfig.enabledToolNames` 包含符合策略的 MCP tool name。
- `agent_run_event_audit` 的 `MODEL_REQUEST.available_tools_json` 包含符合策略的 MCP tool schema。
- `requireConfirm=true`、tool disabled、server disabled、server failed 的工具不会进入 Agent。
- Redis 不可用时，本节点本地刷新仍生效，请求不因 broadcast 失败而失败。
- 新增/调整测试通过，且没有启动项目。
