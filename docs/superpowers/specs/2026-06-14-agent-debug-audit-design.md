# Agent Debug Preset And Conversation Audit Design

**日期:** 2026-06-14

**目标:** 扩展 CyberMario Agent 调试能力，支持前端动态配置除模型选择外的 Agent 参数，支持保存命名预设和本次请求临时覆盖；新增可追溯的对话审计，审计日志仅
`SUPER_ADMIN` 可见。

**当前背景:** 现有 `top.egon.mario.agent.config.AgentConfiguration#cyberMarioAgent` 在启动时创建单例 `ReactAgent`，模型、
`ModelOptions`、system prompt、工具、拦截器、hook 和 `MemorySaver` 均写死。
`top.egon.mario.agent.service.impl.ReactAgentChatService#chat` 只接收 `message`、`threadId`、`principal`。已有模型调用审计
`ai_model_call_audit` 记录 token、耗时、模型等调用维度，但不保存完整对话内容。

---

## 1. 需求范围

### 1.1 本次实现

- 新增 Agent 调试页面，支持用户选择/编辑调试预设并发起流式调试对话。
- 支持保存、编辑、启停、删除命名预设。
- 支持一次请求内临时覆盖预设参数，不必须保存。
- 暂不开放模型选择，仍使用当前默认模型；后端 DTO 和表结构预留模型配置扩展点。
- 新增对话审计，完整追踪每轮请求的用户输入、assistant 输出、thinking 输出、最终生效配置、状态、错误、用户、threadId、traceId。
- 对话审计查询接口和审计菜单仅 `SUPER_ADMIN` 可见。
- 新页面和普通调试接口接入现有 RBAC 资源同步、预置角色和前端菜单裁剪体系。
- 保留现有 `/chat` 普通聊天入口和 `/demo/chat/stream` 兼容行为。

### 1.2 暂不实现

- 不开放前端选择模型。
- 不引入新模型供应商。
- 不做跨用户共享/私有预设的复杂权限模型。预设列表对有调试权限的用户可见，只有创建者可以编辑、启停、删除自己的预设。
- 不改造成全新的 Agent 编排框架。
- 不启动项目。

---

## 2. 方案选择

### 2.1 备选方案

**方案 A: 修改单例 `ReactAgent` 的运行时参数**

- 优点: 改动少。
- 缺点: `ReactAgent#setSystemPrompt` 等运行时修改会污染单例状态。并发请求下，用户 A 的 prompt 或工具配置可能影响用户 B。
- 结论: 不采用。

**方案 B: 每次请求重新构建 `ReactAgent`**

- 优点: 隔离彻底，行为直接。
- 缺点: 高频调试时构建成本更高，且相同预设无法复用。
- 结论: 可作为 fallback，不作为首选。

**方案 C: 使用 `AgentRuntimeSpec` 指纹构建并缓存 `ReactAgent`**

- 优点: 避免单例污染；相同预设和相同临时覆盖可以复用；后续模型选择只需要扩展 spec。
- 缺点: 需要新增 factory/cache 边界。
- 结论: 推荐采用。

### 2.2 推荐设计

新增 `AgentRuntimeSpec` 描述一次运行最终生效的 agent 配置。`AgentPresetService` 负责从默认配置、保存预设、本次覆盖合并出
spec；`AgentRuntimeFactory` 根据 spec 构建或获取缓存的 `ReactAgent`；`ReactAgentChatService` 使用该 agent
流式输出，并把输入、输出、状态写入 `AgentConversationAuditService`。

---

## 3. 后端设计

### 3.1 包结构

建议新增或扩展以下包:

- `top.egon.mario.agent.dto.request`
- `top.egon.mario.agent.dto.response`
- `top.egon.mario.agent.po`
- `top.egon.mario.agent.repository`
- `top.egon.mario.agent.service`
- `top.egon.mario.agent.service.impl`
- `top.egon.mario.agent.service.model`
- `top.egon.mario.agent.web`
- `top.egon.mario.agent.service.resource`

保持现有注释风格: 文件/类/方法使用简洁 Javadoc，说明用途和边界，不引入新注释格式。

### 3.2 配置模型

新增运行时配置记录:

- `AgentRuntimeSpec`
    - `modelConfig`: 模型供应商和模型名配置。当前服务端固定为 `DASHSCOPE` / `qwen3.6-max-preview`，前端不可编辑。
    - `modelOptions`: 复用现有 `ModelOptions`。
    - `systemPrompt`: Agent system prompt。
    - `toolConfig`: 工具开关配置。
    - `agentOptions`: ReactAgent 构建和工具执行配置。
    - `fingerprint`: 基于以上字段生成稳定指纹，用于缓存和审计。

建议新增 DTO:

- `AgentDebugChatRequest`
    - `String message`
    - `String threadId`
    - `Long presetId`
    - `AgentPresetConfig overrides`
- `AgentPresetRequest`
    - `String name`
    - `String description`
    - `AgentPresetConfig config`
    - `Boolean enabled`
- `AgentPresetResponse`
    - `Long id`
    - `String name`
    - `String description`
    - `AgentPresetConfig config`
    - `Boolean enabled`
    - 创建/更新时间和创建/更新用户
- `AgentPresetConfig`
    - `AgentModelConfig modelConfig`
    - `ModelOptions modelOptions`
    - `String systemPrompt`
    - `AgentToolConfig toolConfig`
    - `AgentOptions agentOptions`
- `AgentConversationAuditResponse`
- `AgentConversationMessageAuditResponse`

当前实现中，`AgentModelConfig` 只允许后端输出固定默认值。即使请求传入不同模型，也应忽略或校验失败。推荐校验失败，以避免前端误以为模型已生效。

### 3.3 默认配置

从 `AgentConfiguration#cyberMarioAgent` 中抽出默认配置:

- `temperature = 0.7`
- `topP = 0.9`
- `enableThinking = true`
- `multiModel = true`
- `systemPrompt` 使用当前 CyberMario prompt
- 默认工具为全部已注册 `ToolCallback`
- 默认模型仍固定 `DASHSCOPE` / `qwen3.6-max-preview`

`AgentConfiguration` 不再直接写死完整 `ReactAgent` Bean。它保留默认配置 Bean、`ChatAgentService` Bean 和
`AgentRuntimeFactory` 依赖注入。

### 3.4 Agent 构建与缓存

新增 `AgentRuntimeFactory`:

- 输入 `AgentRuntimeSpec` 和 `ModelCallContext`。
- 调用 `MarioModelFactory.resolve(new ModelRequest(...))` 创建带模型审计的 `ChatModel`。
- 根据 `toolConfig` 从 `List<ToolCallback>` 中筛选工具。
- 使用 `ReactAgent.builder()` 构建 agent。
- 保留 `ToolMonitorInterceptor`、`LoggingHook`、`MemorySaver`。
- 根据 `agentOptions` 设置 `parallelToolExecution`、`maxParallelTools`、`toolExecutionTimeout`。

缓存策略:

- key 使用 `AgentRuntimeSpec.fingerprint`。
- value 为构建好的 `ReactAgent`。
- 第一版可用 `ConcurrentHashMap`，并设置最大数量或按近期使用淘汰。若不做淘汰，至少保留可替换接口，后续可换成 Caffeine。
- 对包含临时覆盖的 spec 也可缓存，因为 fingerprint 会包含覆盖后的完整配置。

### 3.5 Chat 服务流

保留现有接口:

- `ChatAgentService#chat(String message, String threadId, RbacPrincipal principal)`

新增调试接口方法:

- `Flux<ChatResponse> debugChat(AgentDebugChatRequest request, RbacPrincipal principal)`

处理流程:

1. 解析 threadId，没有则生成 UUID。
2. 合并默认配置、保存预设、本次 overrides。
3. 创建 `ModelCallContext`，包含 userId、traceId、threadId、scenario、requestId。
4. 通过 `AgentRuntimeFactory` 获取 `ReactAgent`。
5. 创建对话审计主记录，保存用户输入和最终生效配置。
6. 调用 `agent.stream(message, RunnableConfig.threadId(threadId))`。
7. 流式输出时聚合 assistant 内容和 think 内容。
8. 完成时写 assistant/think 消息审计和成功状态。
9. 失败时记录错误状态和错误信息。
10. 取消时记录取消状态。

`ArxivToolUserContext` 继续在流式调用前设置、结束后清理，保证工具侧仍能拿到当前用户上下文。

---

## 4. 数据库设计

新增 Flyway migration，版本号按当前迁移序列追加，例如 `V13__create_agent_debug_preset_and_conversation_audit.sql`。

### 4.1 `agent_chat_preset`

字段建议:

- `id BIGINT PRIMARY KEY`
- `name VARCHAR(128) NOT NULL`
- `description VARCHAR(512)`
- `model_config_json TEXT`
- `model_options_json TEXT`
- `system_prompt TEXT NOT NULL`
- `tool_config_json TEXT`
- `agent_options_json TEXT`
- `enabled BOOLEAN NOT NULL DEFAULT TRUE`
- `deleted BOOLEAN NOT NULL DEFAULT FALSE`
- `created_by BIGINT`
- `updated_by BIGINT`
- `created_at TIMESTAMP WITH TIME ZONE NOT NULL`
- `updated_at TIMESTAMP WITH TIME ZONE NOT NULL`

索引:

- `idx_agent_chat_preset_enabled_deleted(enabled, deleted)`
- `idx_agent_chat_preset_name(name)`

### 4.2 `agent_conversation_audit`

字段建议:

- `id BIGINT PRIMARY KEY`
- `request_id VARCHAR(64)`
- `trace_id VARCHAR(64)`
- `user_id BIGINT`
- `username VARCHAR(128)`
- `thread_id VARCHAR(128) NOT NULL`
- `preset_id BIGINT`
- `runtime_fingerprint VARCHAR(128)`
- `effective_config_json TEXT`
- `status VARCHAR(32) NOT NULL`
- `started_at TIMESTAMP WITH TIME ZONE NOT NULL`
- `finished_at TIMESTAMP WITH TIME ZONE`
- `duration_ms BIGINT`
- `error_code VARCHAR(256)`
- `error_message VARCHAR(1024)`
- `ip VARCHAR(64)`
- `user_agent VARCHAR(512)`
- `created_at TIMESTAMP WITH TIME ZONE NOT NULL`

索引:

- `idx_agent_conversation_audit_user_created(user_id, created_at)`
- `idx_agent_conversation_audit_thread(thread_id)`
- `idx_agent_conversation_audit_trace(trace_id)`
- `idx_agent_conversation_audit_status(status)`

### 4.3 `agent_conversation_message_audit`

字段建议:

- `id BIGINT PRIMARY KEY`
- `conversation_audit_id BIGINT NOT NULL`
- `seq_no INTEGER NOT NULL`
- `role VARCHAR(32) NOT NULL`
- `message_type VARCHAR(32) NOT NULL`
- `content TEXT`
- `content_chars INTEGER`
- `created_at TIMESTAMP WITH TIME ZONE NOT NULL`

`role` 建议值:

- `USER`
- `ASSISTANT`
- `SYSTEM`

`message_type` 建议值:

- `MESSAGE`
- `THINK`
- `ERROR`

索引:

- `idx_agent_conversation_message_audit_conversation(conversation_audit_id, seq_no)`

---

## 5. API 设计

### 5.1 调试对话

`POST /api/agent/debug/chat/stream`

- 消费 `application/json`
- 生产 `application/x-ndjson`
- 权限码 `api:agent:debug:chat:stream`
- 风险等级 `MEDIUM`

请求:

```json
{
  "message": "解释一下当前工具能力",
  "threadId": "optional-thread-id",
  "presetId": 1,
  "overrides": {
    "systemPrompt": "You are CyberMario...",
    "modelOptions": {
      "temperature": 0.7,
      "topP": 0.9,
      "enableThinking": true,
      "multiModel": true
    },
    "toolConfig": {
      "enabledToolNames": ["searchDuckDuckGoNews", "searchWikipedia"]
    },
    "agentOptions": {
      "parallelToolExecution": false,
      "maxParallelTools": 5,
      "toolExecutionTimeoutSeconds": 300
    }
  }
}
```

### 5.2 预设管理

- `GET /api/agent/presets`
- `GET /api/agent/presets/{id}`
- `POST /api/agent/presets`
- `PUT /api/agent/presets/{id}`
- `PATCH /api/agent/presets/{id}/status`
- `DELETE /api/agent/presets/{id}`

权限码:

- `api:agent:preset:collection`
- `api:agent:preset:*`

### 5.3 对话审计

- `GET /api/admin/agent/conversation-audits`
- `GET /api/admin/agent/conversation-audits/{id}`
- `GET /api/admin/agent/conversation-audits/{id}/messages`

权限码:

- `api:agent:conversation-audit:collection`
- `api:agent:conversation-audit:*`

这些接口不加入普通预置角色，只允许 `SUPER_ADMIN` 通过全量授权访问。

---

## 6. RBAC 设计

在 `agent` app 下通过 `RbacResourceProvider` 声明资源。现有 `menu:agent` 是首页控制台资源，不作为本功能的父菜单，避免改变
dashboard 菜单语义。

### 6.1 菜单资源

- `menu:agent:debug`
    - name: `Agent 调试`
    - path: `/agent/debug`
    - routeName: `agentDebug`
    - icon: `ExperimentOutlined`
    - parentCode: 空，作为独立菜单项

- `menu:agent:conversation-audit`
    - name: `对话审计`
    - path: `/agent/conversation-audits`
    - routeName: `agentConversationAudits`
    - icon: `AuditOutlined`
    - parentCode: 空，作为独立菜单项

### 6.2 API 资源

- `api:agent:debug:chat:stream`
- `api:agent:preset:collection`
- `api:agent:preset:*`
- `api:agent:conversation-audit:collection`
- `api:agent:conversation-audit:*`

### 6.3 预置角色

普通预置角色:

- 扩展现有 `CHAT_BASIC`，不新增独立调试角色。
- `CHAT_BASIC` 权限包含:
    - `menu:agent:debug`
    - `api:agent:debug:chat:stream`
    - `api:agent:preset:collection`
    - `api:agent:preset:*`
    - `api:rbac:auth:self`
    - `api:rbac:me:self`

`SUPER_ADMIN`:

- 继续通过现有 bootstrap 的 `grantAllPermissions` 获得所有权限。
- 审计菜单和审计接口只依赖 `SUPER_ADMIN` 可见，不进普通预置角色。

前端需把 `/agent/conversation-audits` 加入 super-admin-only 路径集合，非 `SUPER_ADMIN` 即使有菜单绕过也不显示。

---

## 7. 前端设计

新增模块:

- `fe/src/modules/agent/agentTypes.ts`
- `fe/src/modules/agent/agentService.ts`
- `fe/src/modules/agent/AgentDebugPage.tsx`
- `fe/src/modules/agent/AgentConversationAuditPage.tsx`

### 7.1 Agent 调试页

路径: `/agent/debug`

页面结构:

- 顶部 `PageToolbar`
    - 标题 `Agent 调试`
    - 操作: 新建预设、保存预设、另存为、刷新预设
- 参数区
    - 预设选择
    - system prompt 编辑
    - 模型选项表单
    - 工具开关
    - agent options
    - 当前固定模型只展示不可编辑摘要
- 对话区
    - 复用现有 `ChatPage` 的消息渲染和 NDJSON 流处理模式
    - 支持新会话、停止生成、显示 threadId

设计上保持现有 Ant Design 风格，不做营销式页面，不启动项目。

### 7.2 对话审计页

路径: `/agent/conversation-audits`

功能:

- 表格查询审计记录。
- 支持按时间、用户、threadId、status、presetId 过滤。
- 点击记录查看消息明细。
- think 内容折叠展示。
- 错误信息只在详情中展开，列表保持可扫描。

### 7.3 菜单和路由

修改:

- `fe/src/layouts/AdminLayout/menu.tsx`
- `fe/src/app/routes.tsx`

新增 `/agent/debug` 和 `/agent/conversation-audits` 路由。保留 `/dashboard` 和 `/chat` 现有路径。

---

## 8. 审计策略

### 8.1 记录粒度

每一次流式请求创建一条 `agent_conversation_audit`。

消息明细:

- 用户输入保存为 `role=USER`、`messageType=MESSAGE`。
- assistant 最终输出聚合保存为 `role=ASSISTANT`、`messageType=MESSAGE`。
- thinking 内容聚合保存为 `role=ASSISTANT`、`messageType=THINK`。
- 失败时保存错误摘要为主记录字段，必要时追加 `messageType=ERROR`。

### 8.2 状态

新增枚举:

- `SUCCESS`
- `FAILED`
- `CANCELLED`

取消由 Reactor `SignalType.CANCEL` 识别。失败记录异常类名和截断后的错误消息。

### 8.3 与模型审计关系

模型审计 `ai_model_call_audit` 继续记录 token、模型、耗时等调用指标。对话审计记录业务层完整对话和最终生效配置。两者通过
`traceId`、`requestId`、`threadId` 可关联，不把对话正文写入模型 dashboard。

---

## 9. 校验与错误处理

后端校验:

- `message` 非空。
- `name` 非空且长度受限。
- `systemPrompt` 非空且长度受限。
- `temperature`、`topP` 使用合理区间。
- `maxTokens`、`topK`、`thinkingBudget`、`maxParallelTools`、`toolExecutionTimeoutSeconds` 为正数或空。
- `enabledToolNames` 必须来自当前注册工具名集合。
- `modelConfig` 当前必须为空或等于服务端默认值；如果传入非默认模型，返回校验错误。

错误处理:

- 预设不存在、禁用、已删除时返回业务错误。
- 工具名无效返回校验错误。
- Agent 调用失败时返回流式错误响应前，审计记录 `FAILED`。
- 用户取消时审计记录 `CANCELLED`。

---

## 10. 测试计划

后端测试:

- `AgentPresetServiceTests`
    - 创建、更新、启停、删除预设。
    - 合并默认配置、预设、overrides。
    - 拒绝非法模型配置和非法工具名。

- `AgentRuntimeFactoryTests`
    - 默认 spec 构建 agent。
    - 不同 fingerprint 返回不同 agent。
    - 工具开关生效。
    - 模型仍通过 `MarioModelFactory` 创建。

- `ReactAgentChatServiceTests`
    - 默认聊天兼容现有行为。
    - debug chat 使用 preset/overrides。
    - 设置并清理 `ArxivToolUserContext`。
    - 成功、失败、取消均调用审计服务。

- `AgentConversationAuditServiceTests`
    - 主记录和消息明细落库。
    - 错误消息截断。
    - 查询只返回预期分页和明细。

- `AgentRbacResourceProviderTests`
    - 菜单、API、预置角色资源同步声明正确。
    - 审计权限不授予普通预置角色。

- `AgentDebugControllerTests`
    - 请求校验。
    - NDJSON 返回。

前端测试:

- `agentService.test.ts`
    - 预设 CRUD 请求 URL 和 body。
    - debug stream body 包含 `presetId` 和 `overrides`。

- `menu.test.tsx`
    - 有普通菜单权限时显示 `/agent/debug`。
    - 非 `SUPER_ADMIN` 不显示 `/agent/conversation-audits`。

验证命令:

-

`mvn -q -f be/pom.xml -Dtest=AgentPresetServiceTests,AgentRuntimeFactoryTests,ReactAgentChatServiceTests,AgentConversationAuditServiceTests,AgentRbacResourceProviderTests,AgentDebugControllerTests test`

- `mvn -q -f be/pom.xml -DskipTests compile`
- `npm test -- --run agentService menu`
- `npm run build`

实际实现时根据项目现有 test runner 参数微调命令。

---

## 11. 风险与缓解

- **单例状态污染:** 不修改现有单例 agent，改用 spec 构建和缓存。
- **审计内容敏感:** 对话审计独立于模型 dashboard，只开放给 `SUPER_ADMIN`。
- **缓存无限增长:** 第一版至少保留缓存封装边界，建议实现最大缓存数量或后续替换 Caffeine。
- **模型选择扩展:** `AgentModelConfig`、`model_config_json`、`AgentRuntimeSpec` 已预留，不在本次暴露 UI。
- **现有聊天兼容:** `/demo/chat/stream` 和 `/chat` 保持默认行为。
- **工具名漂移:** 前端工具选项来自后端注册工具列表或预设详情，后端仍做最终校验。

---

## 12. 需要确认的决策

1. 普通调试能力扩展现有 `CHAT_BASIC`。
2. 预设列表对有调试权限的用户可见，只有创建者可以编辑、启停、删除自己的预设。
3. 审计页路径使用 `/agent/conversation-audits`。
4. 对话正文原文保存，仅 `SUPER_ADMIN` 可查，不做脱敏。
