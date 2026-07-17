# CyberMario 功能清单

> 基于当前仓库源码、前端路由、后端 Controller、服务接口、RBAC 资源提供器和 Flyway 迁移整理。此文件用于快速了解项目已经实现的功能细节。

## 1. 技术与运行基础

- 后端是 Spring Boot WebFlux 应用，主类为 `top.egon.mario.MarioApplication`。
- 前端是 React + TypeScript + Vite 应用，UI 基于 Ant Design。
- 后端统一使用 `ApiResponse<T>` 响应 envelope，前端通过 `requestJson()` / `requestFormData()` 解包。
- 真正的流式接口使用 NDJSON，前端通过 `streamJsonLines()` 逐行解析。
- 前端请求会自动带 `X-Trace-Id`，并在响应中接收权限版本变化。
- 后端全局异常处理覆盖参数校验、RBAC、RAG、Agent 异常，并返回 traceId。
- 后端开启 WebFlux + virtual threads，并通过共享 blocking scheduler 包装阻塞型数据库/文件/外部调用。
- 数据库使用 PostgreSQL，Flyway 位置为 `classpath:db/migration` 和 `classpath:db/postgresql`。
- Redis 用于 RBAC 权限、API 规则和 token 相关缓存，并支持本地缓存广播失效。
- 管理端暴露 Actuator `health`、`info`、`metrics`、`prometheus`。

## 2. 认证与账户

- 支持登录、注册、刷新 token、登出、获取当前登录用户。
- 访问 token 和刷新 token 分离，刷新 token 会持久化 hash，并支持 refresh 轮换与吊销。
- 前端会在 access token 临近过期或遇到 401 时自动刷新并重试请求。
- 登录成功会记录最后登录时间。
- 注册会规范化 username，并校验 username、email、mobile 唯一性。
- 新注册用户默认授予业务角色：
    - `CHAT_BASIC`
    - `RAG_USER`
    - `AGENT_DASHBOARD_USER`
    - `AGENT_MCP_USER`
    - `IM_USER`
- 新注册用户默认不授予 RBAC 管理权限。
- 内置超级管理员 bootstrap 可配置开关、账号、初始密码和是否要求改密。
- 当前用户支持修改个人资料。
- 当前用户支持修改密码，并校验当前密码、确认密码和新旧密码差异。
- 当前用户可获取自己的菜单、按钮权限和 API 权限快照。

## 3. 前端应用与布局

- 路由包括登录、注册、403、后台主布局和受保护业务页面。
- 登录后默认跳转到当前用户第一个有权访问的菜单；无菜单则进入 403。
- 后台菜单根据后端返回的菜单树过滤。
- `SUPER_ADMIN` 专属页面在前端有额外拦截：
    - `/rag/arxiv-logs`
    - `/agent/conversation-audits`
- 账号设置页不依赖菜单权限，只要求已登录。
- 共享布局包含侧边栏、顶部用户区、授权菜单过滤和滚动区域隔离。
- 前端统一处理请求错误、登录失效、无权限、资源不存在和服务异常文案。
- 前端有全局视觉背景、主题 token 和多处页面级状态组件。

## 4. RBAC 管理

- RBAC 使用统一权限模型，权限类型包括：
    - MENU
    - BUTTON
    - API
- 支持用户管理：
    - 用户分页列表
    - 新建用户
    - 编辑资料
    - 启用/禁用
    - 重置密码
    - 删除用户
    - 分配用户角色
    - 查看用户有效权限
- 用户删除有保护：
    - 当前用户不能删除自己
    - 内置 admin 用户不能删除
- 支持角色管理：
    - 角色分页列表
    - 新建角色
    - 编辑角色
    - 删除角色
    - 分配角色权限
    - 查看角色直接权限
    - 查看角色有效权限
    - 配置角色继承
- 角色继承会做环路检测。
- 角色授权时支持把按钮关联的 API 权限一起合并授权。
- 内置角色不能删除，部分关键字段受保护。
- 支持权限管理：
    - 统一权限分页
    - 新建/编辑/删除权限
    - 启用/禁用/草稿状态
    - 菜单树管理
    - 按菜单管理按钮
    - 按钮关联 API 权限
    - API 权限管理
    - 查看已启用 API 规则
- API 权限支持 HTTP method、URL pattern、匹配类型、公开标记和风险等级。
- 公开 API 规则受白名单策略限制，不能任意把接口设为 public。
- 删除权限前会检查子权限、角色授权、按钮/API 关联、菜单/按钮引用，避免破坏引用关系。
- 运行时动态鉴权按数据库中的 API 规则匹配请求 method/path。
- API 规则命中 public 时直接放行，否则要求登录用户拥有对应 API 权限码。
- 权限变更会发布 `RbacPermissionChangedEvent`，并触发缓存失效。
- 角色有 `permission_version`，角色授权、继承、权限状态等变更会 bump 版本。
- 登录态响应中包含 `permissionVersion`，前端可静默刷新菜单/按钮/API 权限。
- RBAC 资源同步器会把模块声明的菜单、按钮、API 和预置角色同步到数据库。
- 资源同步会记录 owner app、source、sync hash、last synced/seen 时间。
- 托管资源有跨 app owner 保护，避免不同模块抢同一个权限码。
- 预置角色采用追加缺失授权的模式，避免覆盖人工调整。
- `SUPER_ADMIN` bootstrap 会创建超级管理员角色、API 权限、用户，并授予全部权限。
- 已实现的 RBAC 管理菜单包括：
    - 用户管理
    - 角色管理
    - 权限管理
    - 菜单管理
    - 按钮管理
    - API 权限

## 5. 首页控制台与模型调用审计

- 首页控制台展示 AI 模型调用审计数据。
- 支持个人范围看板：普通业务用户只能看自己的模型调用数据。
- 支持全局范围看板：需要 `api:agent:model-audit:dashboard:global`。
- 全局看板支持用户筛选，并提供用户选项搜索接口。
- 看板筛选条件包括：
    - 时间范围
    - 用户
    - provider
    - model
    - scenario
    - status
- 查询范围默认 7 天，最大 90 天。
- 看板聚合内容包括：
    - 总调用量
    - 成功/失败数量
    - 成功率
    - prompt/completion/total token
    - prompt/completion 字符数
    - 平均耗时
    - 流式调用数量
    - token 趋势
    - 调用趋势
    - provider/model/scenario/status 维度统计
    - 用户统计
    - 最近调用记录
- 模型调用通过 `AuditedChatModel` 包装，记录同步和流式调用的审计事件。
- 审计记录包含 requestId、traceId、userId、sessionId、threadId、scenario、provider、model、options、token、耗时、状态、错误、IP 和
  User-Agent。

## 6. Agent Chat 与调试

- 基础 Agent Chat 页面支持流式对话。
- Chat 请求包含 message 和 threadId，后端按 threadId 参与会话上下文。
- Agent 调试页面支持通过预设和请求参数发起流式调试对话。
- Agent runtime 支持模型配置、工具配置、预设配置和运行选项。
- 支持 Agent 调试预设管理：
    - 预设分页
    - 预设详情
    - 新建预设
    - 编辑预设
    - 启用/禁用预设
    - 删除预设
    - 默认运行配置
    - 按预设解析运行配置
- `CHAT_BASIC` / `CHAT_USER` 等预置角色包含基础聊天和 Agent 调试所需权限。
- Agent 工具集包含 Wikipedia、DuckDuckGo、Brave Search 和 arXiv 相关工具配置。
- Agent 工具调用有监控拦截器和动态 prompt 拦截器。

## 7. Agent 对话审计

- 已实现 Agent 对话审计数据模型和管理接口。
- 对话审计记录包含用户、threadId、presetId、运行配置、状态、时间等信息。
- 支持审计列表分页查询。
- 支持按以下条件筛选：
    - startAt
    - endAt
    - userId
    - username
    - threadId
    - presetId
    - status
- 支持查看单次对话的消息明细。
- 对话审计页面和接口为 `SUPER_ADMIN` 专属能力。

## 8. MCP 动态管理

- 支持从后台管理远程 MCP Server。
- 当前阶段支持 Streamable HTTP 和 SSE transport。
- MCP 服务配置能力包括：
    - 服务列表
    - 新建服务
    - 查看详情
    - 编辑服务
    - 启用服务
    - 禁用服务
    - 删除服务
    - 连接测试
    - 工具发现
- MCP 服务配置支持：
    - serverCode
    - serverName
    - transportType
    - baseUrl
    - endpoint
    - headers
    - connectTimeoutMs
    - requestTimeoutMs
- MCP headers 返回前会做脱敏。
- 新 MCP 服务默认 disabled。
- 删除 MCP 服务会软删除其下工具配置。
- 支持 MCP 工具策略管理：
    - 工具列表
    - 工具详情
    - 风险等级
    - 只读标记
    - 是否要求确认
    - 启用/禁用工具
    - 运行时可用状态
- 需要确认的工具在当前阶段不能启用，也不会暴露给 ReactAgent。
- 支持 MCP 调用日志列表和详情。
- MCP 工具调用会通过 logging callback 记录。
- MCP Runtime 包含动态客户端管理、运行时注册表、Agent 工具提供器和刷新服务。
- 已有 MCP 预置角色：
    - `AGENT_MCP_USER`：管理服务和工具策略，不含调用日志菜单。
    - `AGENT_MCP_ADMIN`：管理服务、工具策略和调用日志。

## 9. RAG 知识库与文档

- 支持知识库管理：
    - 分页列表
    - 新建知识库
    - 编辑知识库
    - 删除知识库
    - 查看知识库用户授权
    - 替换知识库用户授权
- 知识库访问通过 `rag_knowledge_base_user` 和 `RagAccessService` 做数据权限控制。
- `SUPER_ADMIN` 可绕过 RAG 知识库数据权限。
- 普通用户只能读取自己可访问的知识库。
- 支持知识库授权等级，服务层会按读取/管理等访问级别校验。
- 支持文档管理：
    - 按知识库分页列表
    - 文件上传
    - 文本导入
    - arXiv PDF 导入
    - 文档详情
    - 删除文档
    - 重建索引
    - 查看文档切片
    - 启用/禁用切片
- 文档上传支持多个文件，并可选择是否立即解析入库。
- 文档入库会生成 ingestion job。
- 本地文件存储默认路径为 `${user.home}/.cyber-mario/rag-files`。
- 文档解析基于 Tika。
- 文档会切片并写入向量库和关键词索引。
- RAG schema 包括知识库、知识库用户、文档、文件对象、文档切片、入库任务、检索 trace、反馈等表。

## 10. RAG 入库任务

- 支持入库任务分页列表。
- 支持按 knowledgeBaseId 过滤任务。
- 支持重试入库任务。
- 支持取消入库任务。
- 入库任务记录状态、步骤和相关文档/知识库信息。

## 11. RAG 检索与问答

- 支持 RAG 流式问答接口。
- 支持 RAG 检索调试接口。
- 检索请求支持：
    - query
    - knowledgeBaseIds
    - topK
    - candidateTopK
    - similarityThreshold
    - searchMode
    - rerankEnabled
    - debug
- 默认检索模式为 HYBRID。
- 支持向量检索、关键词检索、混合权重合并。
- 默认配置包含：
    - defaultTopK
    - defaultSimilarityThreshold
    - candidateTopK
    - contextTopK
    - vectorWeight
    - keywordWeight
    - chunkSize
    - chunkOverlap
    - rerankEnabled
    - rerankModel
- 支持可选 rerank。
- 支持检索 trace 记录和 trace 详情查询。
- RAG 问答返回来源引用。
- 支持用户对 RAG 答案和来源提交反馈。
- RAG 设置页目前提供只读系统配置查看。
- 已有 RAG 预置角色：
    - `RAG_ADMIN`：完整 RAG 管理能力。
    - `RAG_OPERATOR`：文档、任务和检索操作能力。
    - `RAG_USER`：默认知识库使用、问答、检索和基础文档导入能力。

## 12. arXiv 工具与自动收录

- Agent arXiv 工具支持论文搜索。
- arXiv 搜索请求支持 query、maxResults、includeFullText。
- 支持论文摘要检索。
- 可选读取全文预览，默认预览长度有配置。
- 每次 arXiv 工具检索会生成 requestId 并写入工具日志。
- 搜索结果会异步提交后台导入任务。
- arXiv 论文 PDF 会下载到临时文件，并导入 RAG 文档。
- 所有用户通过 arXiv 工具检索到的论文会导入受保护的 `super-admin-arxiv` 知识库。
- `super-admin-arxiv` 知识库会在启动时 bootstrap。
- `SUPER_ADMIN` 用户会自动获得该知识库 MANAGE 授权。
- 已成功导入过的论文会跳过重复收录。
- arXiv 工具日志状态包含搜索、导入 pending/running/success/failed/skipped 等过程信息。
- arXiv 日志页面和接口只允许 `SUPER_ADMIN` 访问。

## 13. 营养管理（Nutrition Management）

- 已实现家庭 AI 营养管理的后端领域、RBAC 资源、真实 API 前端页面和纵向验收测试。
- 支持 clan、family、clan-family 关系和 family-scoped member profile 模型。
- family 是菜单、确认、购物清单、预算、食谱和营养记录的业务隔离边界。
- 营养权限分为平台 RBAC 菜单/API 入口和 nutrition-scoped role binding / data grant 显式授权。
- 家庭管理员可维护家庭设置、成员、角色绑定、clan 关系和显式 data grant；cook 权限不会跨 family 泄漏。
- 支持 member health profile、健康标签、饮食目标、过敏/忌口/限制配置，以及按授权范围读取健康档案。
- 支持平台 standard food、家庭 standard food 和 family recipe；食谱包含食材映射、步骤、营养计算、校验和停用流程。
- 支持标准食物和家庭食谱 CSV import job，包含上传、预检、错误记录、确认导入和导入结果。
- AI 推荐任务持久化成员健康、可用食谱、预算规则、近期价格和近期用餐上下文；模型输出会物化为 AI recipe 和待 cook review 的
  meal plan，不会直接发布。
- cook 可按版本编辑、增删和排序 meal plan 菜品；编辑会重新计算营养快照、执行风险校验并写入 before/after 操作审计。
- 高风险过敏会阻止 meal plan 发布，中风险偏好冲突需要 cook 记录确认说明后才能发布。
- 成员或代理可按菜品确认是否在家用餐和精确份数；meal summary 汇总成员状态及每道菜的确认份数。
- 确认关单后按食谱基准份数和成员确认份数生成 shopping list，并保存 meal plan/confirmation 版本快照；支持采购项状态、实际采购信息和
  food price record。
- 支持家庭预算规则、weekly/monthly budget statistics 和预算快照；预算使用率与购物完成率是两个独立指标。
- meal completion 会幂等生成成员 nutrition record；手工调整以追加 correction 的方式保留原始记录，并支持加餐、daily
  overview、weekly/monthly report、目标对比和报告快照。
- 家庭营养首页从 meal plan、confirmation、risk、shopping、budget 和 nutrition record 的同一套持久化数据生成工作流概览。
- 前端营养路由包括家庭营养、成员健康、食谱库、AI 菜单、用餐确认、用餐汇总、购物清单、预算、营养记录和平台配置页面；页面通过
  nutrition service 调用真实后端接口，并处理加载、空数据和失败状态，不依赖静态业务 fixture。

## 14. 平台 Web 即时通讯（IM）

- 已在现有 Web 管理端提供 `/im` 工作区，不再依赖原桌面客户端，也没有拆分新的 IM 项目或服务。
- 工作区包含消息、联系人、频道、独立群组和邀请入口，并复用现有登录态、`AdminLayout`、菜单树与 RBAC。
- 支持安全用户搜索、好友申请、接受、拒绝、取消、双向删除和调用方私有好友备注。
- 平台私聊只允许 ACTIVE 好友；REST 私聊打开/发送和 WebSocket 发送使用同一好友门禁。
- 删除好友后不会删除历史私聊：双方仍可读取并标记已读，但会显示为只读且不能继续发送。
- 不再初始化默认 `PLATFORM/general`；频道由用户自行创建。已有 `general` 数据不会自动删除，只按普通频道和现有成员关系展示。
- 频道和独立群组均不提供搜索/发现加入，只允许所有者或管理员直接邀请，受邀用户接受或拒绝。
- 频道成员可查看频道下的群组；频道所有者或管理员可创建子群组，子群组对频道成员支持 `OPEN` 或 `APPROVAL`。
- 退出或被移出频道会级联退出其子群组并取消待处理的子群组申请/邀请；离开后不可读取平台频道/群组历史，重新加入后恢复。
- 所有者离开前必须先转让频道及其仍持有的子群组；前任所有者转为管理员。
- 消息统一使用 conversation + sequence 模型，支持精确未读、单调已读游标、稳定 `clientMsgId` 幂等、乐观发送状态和失败重试。
- 单节点实时链路使用现有 WebSocket、事务 Outbox dispatcher 和本地路由；断线或 `RESYNC` 后按 REST 历史与消息序号恢复。
- V1 消息类型为 `TEXT` / `SYSTEM`，支持普通 Unicode emoji 输入；不包含上传、图片、搜索、reaction、thread、typing、presence 或回执。
- RBAC 新增 `menu:im`、`IM_USER`、`IM_ADMIN`。新注册用户获得 `IM_USER`；可配置回填仅为现有启用且未删除用户幂等授予 `IM_USER`
  ，不会自动授予 `IM_ADMIN`。
- 好友关系通过 `im_friendship` 的规范化用户对和 `im_contact` 的双向联系人记录持久化；本次数据库变更仅新增
  `V46__create_im_platform_friendship_schema.sql`。
- 频道/群组邀请通过 `V48__create_im_surface_invitation_schema.sql` 持久化；未修改或重算任何已有 Flyway 迁移。
- Clocktower 仍只依赖通用 `ImFacade` / `RoomFacade` 和自身策略适配器，不依赖平台好友门禁或平台展示模型。

## Clocktower Phase 1

- [x] Script, role, night-order, term, and jinx query APIs.
- [x] Board generation and validation.
- [x] Room, seat, start-game, and role assignment flow.
- [x] Storyteller grimoire, markers, and night checklist.
- [x] Player view, actions, nomination, vote, execution basics.
- [x] SSE room event stream and basic replay.
- [x] RBAC resource provider and frontend route shells.

## Clocktower room / IM / game refactor

- [x] Room is modeled as a long-lived generic `room_space` with room members, invitations, bans, and Clocktower-specific
  room profile/seats.
- [x] Game is modeled as a per-round `clocktower_game` snapshot with game seats and game-scoped events.
- [x] Generic IM core is reusable internally through `ImFacade`, while current HTTP APIs remain Clocktower-scoped.
- [x] Clocktower chat endpoints stay under `/api/clocktower/**`; management audit chat endpoints stay under
  `/api/admin/clocktower/**`.
- [x] 本次 Room / IM / game 重构当时未暴露平台 `/api/im/**`；后续平台 Web IM 已以独立 composition facade 和 `api:im:*`
  权限补齐，不改变 Clocktower 端点与策略边界。
- [x] `CLOCKTOWER_PLAYER` covers both room spectators and seated players; no separate `CLOCKTOWER_SPECTATOR` RBAC role
  is added.
- [x] Room lobby supports spectator entry, seat claim/request, invitation/member governance, and start-game gating.
- [x] `/clocktower/rooms/{roomId}/play` resolves lobby/player/storyteller/spectator surfaces instead of assuming a seat.
- [x] Game replay is queried by `gameId`; `/clocktower/games/{gameId}/replay` is authorized through the Clocktower
  replay menu.
- [x] Management audit supports room, game, chat, invitation, member, and ban projections through Clocktower admin APIs.

## 15. 数据权限与边界

- RBAC API 权限控制“能不能调用接口”。
- RAG 知识库用户授权控制“能看到哪些知识库和文档”。
- 模型审计看板区分 self 和 global 数据范围。
- 普通用户默认只有自己的 dashboard 数据。
- `dashboard:global` 和 user-options 属于更高权限范围。
- 对话审计和 arXiv 工具日志是 `SUPER_ADMIN` 专属。
- RBAC 管理权限不会授予普通注册用户。
- 预置角色与资源同步采用追加缺失授权，不主动删除人工授权。
- 已有迁移清理普通用户不应拥有的 dashboard global 授权。
- 本次钟楼重构接受一个 RBAC 迁移偏差：`V27__retire_old_clocktower_rbac_resources.sql` 一次性退休旧钟楼权限；后续类似能力应优先进入
  RBAC 资源同步器的 stale-resource retirement 机制。

## 16. 数据库迁移现状

- `V1__create_rbac_schema.sql`：RBAC 基础 schema。
- `V2__convert_rbac_enums_to_integer.sql`：RBAC 枚举持久化调整。
- `V3__expand_password_hash_length.sql`：密码 hash 长度扩展。
- `V4__create_rag_schema.sql`：RAG 基础 schema。
- `V5__create_rag_pgvector_store.sql`：pgvector 向量存储。
- `V6__add_role_permission_version.sql`：角色权限版本。
- `V7__add_rbac_sync_metadata.sql`：RBAC 资源同步元数据。
- `V8__create_ai_model_call_audit.sql`：AI 模型调用审计。
- `V9__remove_normal_dashboard_global_grants.sql`：移除普通用户 dashboard global 授权。
- `V10__create_arxiv_tool_log.sql`：arXiv 工具日志。
- `V11__enhance_rag_hybrid_retrieval.sql`：RAG 混合检索增强。
- `V12__optimize_rag_keyword_search.sql`：RAG 关键词检索优化。
- `V13__create_agent_debug_preset_and_conversation_audit.sql`：Agent 调试预设和对话审计。
- `V14__create_agent_mcp_schema.sql`：Agent MCP schema。
- `V15__add_agent_mcp_partial_unique_indexes.sql`：Agent MCP 部分唯一索引。
- `V16__create_agent_run_audit.sql`：Agent run audit。
- `V17__create_agent_memory_schema.sql`：Agent memory schema。
- `V18__create_clocktower_core_schema.sql`：钟楼核心 schema。
- `V19__disable_old_clocktower_room_wildcard_permission.sql`：停用旧钟楼房间 wildcard 权限。
- `V20__complete_clocktower_rule_data.sql`：补全钟楼规则数据。
- `V21__create_clocktower_ruling_system.sql`：钟楼裁定系统。
- `V22__add_clocktower_board_valid.sql`：钟楼配板有效状态。
- `V24__extend_agent_memory_message_final_snapshot.sql`：Agent memory 消息最终快照扩展。
- `V25__add_agent_soulmd.sql`：Agent SoulMD。
- `V26__create_room_im_clocktower_refactor_schema.sql`：Room / IM / Clocktower game refactor schema。
- `V27__retire_old_clocktower_rbac_resources.sql`：一次性退休旧钟楼 RBAC 权限；这是本次重构接受的迁移偏差。
- `V30__create_im_core_schema.sql`：统一 Channel、Group、DM、Conversation、Message、Inbox、Outbox 与治理基础 schema。
- `V31__create_nutrition_mvp_schema.sql`：营养管理持久化基线；恢复实现未修改该历史迁移。
- `V46__create_im_platform_friendship_schema.sql`：平台好友关系和双向联系人 schema；未修改任何历史迁移。
- `V48__create_im_surface_invitation_schema.sql`：平台频道/群组直接邀请 schema；未修改任何历史迁移。

## 17. 已有测试覆盖线索

- 前端有 request、tokenStorage、urlSearch、async、enum、pageDataState 等基础工具测试。
- 前端有 AdminLayout 菜单、权限影响和样式相关测试。
- 前端有 RBAC service、dashboard service、chat stream merge 测试。
- 前端有 Agent 预设权限、默认预设和 agent service 测试。
- 前端有 MCP service 和 MCP Server 编辑抽屉测试。
- 前端有 RAG service 测试。
- 后端已有 nutrition slice 测试，覆盖 schema migration、RBAC resource provider、access service、CSV import、rule check、AI
  service、meal plan/confirmation、shopping、budget、nutrition record 和 controller smoke 路径。
- `NutritionVerticalFlowTests` 包含 10 条持久化纵向验收，覆盖家庭与 clan 授权、AI
  草案、风险复核、菜单编辑、精确份数确认、关单采购、预算指标、完成幂等、记录修正和首页投影。
- controller smoke 测试会实际调用设置/授权、食谱校验、AI 入队、菜单编辑/发布、菜品确认、购物清单生成、预算规则和营养记录
  Controller 方法；路径反射仅作为补充。
- 前端 nutrition 测试覆盖 service 请求契约、家庭切换和核心页面的加载/失败/提交刷新行为；当前仓库没有声明浏览器或人工验收结果。
- 后端 IM 测试覆盖迁移/映射、好友状态机、成员范围频道/群组、直接邀请、父子成员约束与退出级联、好友门禁私聊、精确未读、
  Outbox/WebSocket 恢复、RBAC 资源和 Clocktower 边界。
- 前端 IM 测试覆盖 service 请求契约、workspace reducer、乐观发送与同 `clientMsgId` 重试、未读合并、实时恢复、路由/菜单和消息/
  联系人/频道/群组/邀请 UI 状态。
- PostgreSQL 锁、并发序号和 filtered-index 语义必须使用显式 `IM_POSTGRES_TEST_*` 一次性测试库验证；H2 测试不能替代该门禁。

## 18. 目前需要注意的实现细节

- `fe/node_modules` 和 `fe/dist` 当前存在于工作区，但它们是生成/依赖产物，不应作为功能来源。
- `README.md` 作为高层项目入口维护紧凑 feature list；完整功能细节仍以本文件和对应模块源码为准。
- arXiv 日志菜单由 RAG 菜单展示，但接口权限资源在 Agent 侧声明。
- Agent MCP 资源 provider 中的顶层 Agent 菜单当前命名为“首页控制台”，实际用于承载 `/dashboard`。
- 基础 Agent Chat 使用 `/demo/chat/stream`，Agent 调试使用 `/api/agent/debug/chat/stream`，RAG 问答使用
  `/api/rag/chat/stream`。
- `RAG_USER` 拥有文档上传/文本导入/arXiv 导入按钮和基础集合接口，但敏感管理接口仍由 RAG 数据权限和 API 权限进一步约束。
- MCP 工具若 `requireConfirm=true` 会被自动禁止启用，这是当前阶段的明确安全限制。
- 资源同步不会修改非托管手工资源，也不会主动删除预置角色中已经存在但后来不再声明的旧授权。
