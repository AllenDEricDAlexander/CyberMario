# CyberMario Frontend UI Design Brief

本文档面向 UI 设计交接，说明当前 `fe` 前端已经存在的页面、功能点、用户入口和主要界面状态。设计时请以“后台工作台 + AI/知识库/游戏工具”为核心，而不是营销站点。

当前前端基于 React 19、TypeScript、Vite、Ant Design 6、Ant Design X、Ant Design Charts 和 Bun。应用通过 `/api` 和 `/demo` 调用后端，普通 JSON 接口使用统一 `ApiResponse<T>` envelope，流式聊天接口使用 NDJSON 或 SSE。

## Design Scope

- 需要设计的主要外壳：登录/注册页、后台管理布局、通用页面工具栏、表格型管理页、抽屉/弹窗表单、聊天工作台、数据看板、Clocktower 游戏工具页。
- 当前没有平台级 IM 独立模块，也没有 `/api/im/**` 前端入口。Clocktower 内出现的聊天、会话和聊天审计只属于钟楼游戏域；房间大厅里的“房间公聊”仍是后续任务，不应作为通用 IM 设计范围。
- 绝大多数页面按 RBAC 菜单权限和按钮权限展示，UI 需要支持按钮缺失、无权限、空数据、加载、错误和禁用状态。
- 后台页面是重复工作型产品界面，优先考虑密度、扫描效率、筛选效率、状态可读性和长表格横向滚动。

## Global App Shell

### Auth Layout

- 路由：`/login`、`/register`。
- 当前视觉：深色全屏背景、CyberMario 品牌文案、右侧玻璃质感表单卡片。
- 登录表单字段：账号或邮箱、密码；成功后跳转到来源页或 `/chat`。
- 注册表单字段：账号、用户名、昵称、密码、确认密码、邮箱、手机；注册后直接进入工作台。
- 表单错误使用页面内 `Alert` 展示。

### Admin Layout

- 受保护路由根路径：`/`。
- 左侧深色侧边栏：CyberMario logo、可折叠菜单、菜单根据后端权限过滤。
- 顶部栏：折叠按钮、当前用户头像和名称、用户下拉菜单。
- 用户下拉菜单：个人设置、退出登录。
- 内容区：浅色动态背景、页面主体滚动隔离。
- 无菜单权限时展示 403 Result；权限变化可能触发当前页面重新挂载。
- 全局错误：顶部居中的可关闭错误 Alert。

### Shared Page Patterns

- `PageToolbar`：每个后台页顶部的标题、说明、右侧操作按钮。
- 管理页常见结构：工具栏 + 筛选卡片 + 表格 + 抽屉或弹窗。
- 状态表达：`Tag`、`StatusTag`、`ApiRiskTag`、`PermissionTypeTag`。
- 日期展示：`DateTimeText`。
- 重要删除、取消、切换状态操作使用 `Popconfirm`。
- 新建/编辑复杂对象通常使用右侧 `Drawer`；短流程使用 `Modal`。

## Navigation Map

| Menu | Route | Page |
|------|-------|------|
| 首页控制台 | `/dashboard` | 模型调用数据看板 |
| Agent Chat | `/chat` | 基础 Agent 流式聊天 |
| Agent 管理 / Agent 调试 | `/agent/debug` | Agent 调试聊天和预设管理 |
| Agent 管理 / 对话审计 | `/agent/conversation-audits` | Agent 对话审计 |
| Agent 管理 / 运行审计 | `/agent/run-audits` | Agent ReAct 运行审计 |
| Agent 管理 / 记忆管理 | `/agent/memory` | Agent Memory 当前会话和长期记忆 |
| Agent 管理 / 归档会话 | `/agent/memory/archive` | Agent Memory 归档会话 |
| Agent 管理 / MCP 服务配置 | `/agent/mcp/servers` | MCP Server 和工具策略 |
| Agent 管理 / MCP 调用日志 | `/agent/mcp/logs` | MCP 工具调用日志 |
| RAG 管理 / RAG 问答 | `/rag/chat` | RAG 流式问答 |
| RAG 管理 / 知识库管理 | `/rag/knowledge-bases` | 知识库和用户授权 |
| RAG 管理 / 文档管理 | `/rag/documents` | 文档上传、文本导入、重建索引 |
| RAG 管理 / 文档详情 | `/rag/documents/:documentId` | 文档切片详情 |
| RAG 管理 / 入库任务 | `/rag/ingestion-jobs` | 文档入库任务 |
| RAG 管理 / 检索调试 | `/rag/retrieval-lab` | 召回调试和 Trace |
| RAG 管理 / arXiv 日志 | `/rag/arxiv-logs` | arXiv 工具导入日志 |
| RAG 管理 / RAG 设置 | `/rag/settings` | RAG 全局只读设置 |
| 钟楼 / 钟楼配板 | `/clocktower/boards` | 配板生成、编辑、校验和保存 |
| 钟楼 / 钟楼房间 | `/clocktower/rooms` | 房间列表和创建房间 |
| 钟楼 / 房间大厅 | `/clocktower/rooms/:roomId/lobby` | 房间大厅、座位、邀请、成员 |
| 钟楼 / 游戏入口 | `/clocktower/rooms/:roomId/play` | 根据身份进入玩家/说书人/旁观视角 |
| 钟楼 / 说书人魔典 | `/clocktower/rooms/:roomId/grimoire` | 说书人管理视图 |
| 钟楼 / 钟楼规则 | `/clocktower/rules` | 剧本、角色、夜晚顺序、术语、相克规则 |
| 钟楼 / 钟楼回放 | `/clocktower/replays` | 游戏回放列表 |
| 钟楼 / 游戏回放 | `/clocktower/games/:gameId/replay` | 单局事件回放 |
| 钟楼 / 钟楼审计 | `/clocktower/admin/audit` | 房间、游戏、会话审计查询 |
| RBAC 管理 / 用户管理 | `/rbac/users` | 用户、角色授权、有效权限 |
| RBAC 管理 / 角色管理 | `/rbac/roles` | 角色、权限、继承 |
| RBAC 管理 / 权限管理 | `/rbac/permissions` | 通用权限 |
| RBAC 管理 / 菜单管理 | `/rbac/menus` | 菜单权限树 |
| RBAC 管理 / 按钮管理 | `/rbac/buttons` | 菜单按钮和关联 API |
| RBAC 管理 / API 权限 | `/rbac/apis` | 动态 API 规则 |
| 个人设置 | `/account/settings` | 资料、密码、SoulMD |

## Dashboard

### `/dashboard` 首页控制台

用途：查看 AI 模型调用、Token 消耗、成功率、耗时和用户维度排行。

核心功能：

- Scope 切换：全局用量 / 我的用量；全局模式需要高权限。
- 筛选项：时间范围、用户、Provider、模型、场景、状态。
- 指标卡片：调用次数、总 Token、输入 Token、输出 Token、成功率、平均耗时。
- 图表：Token 趋势折线图、调用量柱状图、模型 Token 排行、场景分布、状态分布、用户排行。
- 最近调用表格：时间、用户、模型、场景、状态、输入/输出/总 Token、耗时、Trace。

设计注意点：

- 这是运营监控型页面，图表和指标需要高可读性。
- 当用户没有全局权限时，只显示个人范围，不显示用户选择器。

## Chat Workspace

### Shared Chat Workspace

`/chat`、`/agent/debug`、`/rag/chat` 使用同一套聊天工作台理念：

- 左侧会话栏：品牌标题、会话列表、新建会话、刷新、归档当前会话。
- 主区域：页面头部、消息列表、输入器、流式状态、错误状态。
- 消息动作：复制、重试；RAG 消息额外支持反馈。
- 支持加载历史会话、切换会话、新会话、归档会话。
- 使用 Ant Design X 的 Conversations、Bubble/List、Sender 等聊天组件。

### `/chat` Agent Chat

用途：基础 Agent 流式对话。

核心功能：

- 新建 Agent Chat 会话。
- 左侧列出 `AGENT_CHAT` 类型记忆会话。
- 发送消息后流式接收 assistant 输出。
- 支持会话历史加载和当前会话归档。
- 支持“记忆上下文”开关。

主要状态：

- 默认欢迎消息。
- 流式生成中、请求取消、接口错误、空会话列表。

### `/agent/debug` Agent 调试

用途：使用预设和运行参数调试 Agent。

核心功能：

- 聊天式调试入口，支持流式输出。
- 左侧会话列表使用 `AGENT_DEBUG` 类型记忆会话。
- 设置弹窗管理调试预设。
- 预设字段：预设、名称、启用、描述、系统提示词、Temperature、Top P、Max Tokens、Top K、Thinking Budget、Tool Timeout、Max Parallel Tools、启用工具。
- 开关：Thinking、Search、多模型、并行工具。
- 预设操作：新建、保存、另存、删除；只能编辑自己可编辑的预设。

设计注意点：

- 这是高阶调参页面，设置弹窗需要清楚区分“模型参数”“工具参数”“预设元信息”。

### `/rag/chat` RAG 问答

用途：对选定知识库进行流式问答。

核心功能：

- 新建和归档 `RAG_CHAT` 会话。
- 必须选择知识库后才能提问。
- 设置弹窗配置知识库、多选检索参数和记忆开关。
- 检索参数：TopK、候选 TopK、相似度阈值、检索模式、Rerank。
- 开关：记忆上下文、长期记忆提取。
- 消息展示来源引用，支持打开来源抽屉。
- 反馈动作：有帮助、无帮助、来源错误、未回答。

主要状态：

- 未选知识库提醒、流式回答、来源引用为空、反馈权限缺失、请求错误。

## Agent Management

### `/agent/conversation-audits` 对话审计

用途：查询 Agent 对话原文、运行配置和执行状态，仅 `SUPER_ADMIN` 可访问。

核心功能：

- 筛选：用户 ID、用户名、线程 ID、预设 ID、状态。
- 表格字段：ID、用户、线程、预设、状态、耗时、请求 ID、Trace、错误、开始/完成时间。
- 详情抽屉：运行配置 JSON、消息明细表格。

### `/agent/run-audits` 运行审计

用途：查看 Agent ReAct 链路、模型轮次、工具调用和明文载荷，仅 `SUPER_ADMIN` 可访问。

核心功能：

- 筛选：用户 ID、时间范围、用户名、线程 ID、请求 ID、Trace ID、预设 ID、工具名、MCP 服务、状态。
- 表格字段：运行状态、模型轮次、工具数、MCP 调用数、耗时、错误、时间。
- 详情抽屉：基础信息、运行配置、用户输入、最终结果、事件表。
- 事件表：事件类型、状态、轮次、工具、MCP 服务、模型、耗时、错误、开始时间。
- JSON/长文本使用折叠块展示。

### `/agent/memory` 记忆管理

用途：查看当前用户长期记忆和未归档会话。

核心功能：

- 长期记忆卡片：状态、字符数、更新时间、Markdown 内容摘要。
- 会话筛选：入口类型、长期记忆状态。
- 会话表格：标题、入口、状态、长期记忆、长期提取、最后活跃。
- 操作：归档会话、进入归档会话页、刷新。

### `/agent/memory/archive` 归档会话

用途：查看和处理当前用户已归档会话。

核心功能：

- 表格字段：标题、入口、归档时间。
- 操作：恢复、删除。

### `/agent/mcp/servers` MCP 服务配置

用途：维护 ReactAgent 可连接的远程 MCP 服务和工具策略。

核心功能：

- 服务表格：编码、名称、传输、Base URL、Endpoint、状态、启用开关、最近连接。
- 服务操作：新建、编辑、连接测试、工具发现、工具策略、删除、启用/禁用。
- 服务抽屉字段：服务编码、服务名称、传输协议、Base URL、Endpoint、请求头、连接超时、请求超时。
- 工具策略抽屉：按服务展示工具列表，可编辑工具风险等级、只读、需要确认。
- 需要确认的工具第一阶段不会暴露给 ReactAgent，UI 需要明显提示。

### `/agent/mcp/logs` MCP 调用日志

用途：查看 ReactAgent 调用 MCP 工具的状态、耗时和请求响应摘要。

核心功能：

- 表格字段：创建时间、状态、服务、Tool Key、用户 ID、线程、耗时、错误。
- 操作：刷新。

## RAG Management

### `/rag/knowledge-bases` 知识库管理

用途：维护 RAG 知识库、默认检索参数和用户级数据权限。

核心功能：

- 表格字段：名称、编码、TopK、模式、Rerank、阈值、状态、描述。
- 操作：新建、编辑、删除、用户授权。
- 知识库表单：名称、编码、描述、默认 TopK、默认阈值、状态。
- 检索配置：检索模式、候选 TopK、上下文 TopK、Rerank、向量权重、关键词权重、切片长度、重叠长度。
- 用户授权弹窗：多行用户 ID + 授权等级，支持添加/移除。

### `/rag/documents` 文档管理

用途：上传文件、导入纯文本、重建索引和删除文档。

核心功能：

- 表格字段：文档名、知识库、类型、来源、状态、切片、已入库、解析器、Embedding、错误。
- 文档名可进入切片详情页。
- 上传文档弹窗：知识库、上传后立即解析、拖拽上传。
- 导入纯文本弹窗：知识库、标题、内容、导入后立即解析。
- 操作：重建索引、删除。

### `/rag/documents/:documentId` 文档切片详情

用途：查看文档切片、元数据和检索启用状态。

核心功能：

- 表格字段：序号、Token、标题路径、Hash、状态、内容。
- 操作：启用/禁用切片。

### `/rag/ingestion-jobs` 入库任务

用途：查看文档解析、切片、向量化和入库进度。

核心功能：

- 表格字段：任务 ID、文档 ID、知识库、步骤、状态、进度、切片、错误。
- 操作：重试、取消。

### `/rag/retrieval-lab` 检索调试

用途：输入问题查看召回片段、分数和来源，不调用生成模型。

核心功能：

- 表单字段：问题、知识库、TopK、候选 TopK、阈值、模式、Rerank。
- 操作：开始检索、打开 Trace 详情。
- 结果区：Tabs 展示召回结果和调试信息。
- Trace 抽屉：展示检索链路、请求参数和召回明细。

### `/rag/arxiv-logs` arXiv 日志

用途：查看 arXiv 检索、PDF 下载和导入受保护知识库的后台任务记录。

核心功能：

- 表格字段：日志 ID、请求用户、查询、结果数、全文、状态、论文、文档 ID、入库任务、错误、创建/完成时间。
- 操作：刷新。

### `/rag/settings` RAG 设置

用途：只读展示当前后端环境驱动的 RAG 全局配置。

核心功能：

- 字段：Chat 模型、Embedding 模型、Embedding 维度、默认检索模式、默认 TopK、候选 TopK、上下文 TopK、默认阈值、Rerank、Rerank 模型、文件存储。

## Clocktower

Clocktower 是“血染钟楼”游戏工具域，当前前端覆盖规则数据、配板、房间、大厅、说书人、玩家入口、回放和审计。这里的聊天/会话只服务于游戏上下文，不代表平台 IM。

### `/clocktower/boards` 钟楼配板

用途：为说书人生成、编辑并校验一局可用配板。

核心功能：

- 生成表单：剧本、人数、难度、混乱度、邪恶压力、新手友好、候选数、随机种子。
- 配板编辑器：角色树选择、角色数量、手动校验、保存配板。
- 校验结果：通过/未通过 Alert，展示规则问题。
- 候选配板表格：生成候选、选用候选。
- 我的配板库：按剧本、人数、校验状态查询；支持编辑、删除。

### `/clocktower/rooms` 钟楼房间

用途：创建房间、进入大厅并跳转到玩家或说书人视图。

核心功能：

- 房间表格：房间、剧本、可见性、状态、玩家数、预留数、说书人、操作。
- 房间操作：进入大厅、游戏入口、说书人魔典。
- 创建房间弹窗字段：房间名称、剧本、人数、Agent 座位数、说书人模式、入座策略、通过配板、允许旁观、允许私聊。

### `/clocktower/rooms/:roomId/lobby` 房间大厅

用途：进入房间后旁观、认领座位、邀请成员、查看成员并开始游戏。

核心功能：

- 顶部操作：刷新、邀请、成员、开始游戏。
- Tabs：座位、大厅信息、邀请/预留、成员等。
- 座位区：展示座位、玩家、状态、认领按钮。
- 邀请抽屉：被邀请用户 ID、邀请类型、目标座位、现有预留表。
- 成员抽屉：成员表、移出、移出并封禁。
- 当前房间公聊仍提示“后续聊天任务接入”。

### `/clocktower/rooms/:roomId/play` 游戏入口

用途：解析当前账号在房间和游戏中的可用视角。

核心功能：

- 游戏未开始：提示返回大厅或房间列表。
- 无可用视角：提示返回大厅。
- 有视角时跳转或渲染对应玩家、说书人、旁观界面。

### `/clocktower/rooms/:roomId/grimoire` 说书人魔典

用途：说书人管理身份、标记、夜晚顺序、流程推进和裁定。

核心功能：

- 魔典座位列表：角色、阵营、生命状态、公开生命状态、死票、旅行者状态。
- 座位操作：死亡、复活等公开裁定。
- Tabs：夜晚任务、流程事件、裁定历史、动作面板、聊天监控等。
- 流程操作：推进流程、跳过夜晚任务、关闭提名、确认处决。
- 裁定表单：裁定类型、目标座位、提名 ID、公开生死、获胜阵营、裁定原因、可见范围、内部记录、公开记录、强制裁定。
- 说书人动作表单：动作、目标座位、备注。

### `/clocktower/rules` 钟楼规则

用途：查看剧本角色、夜晚顺序、术语和相克规则。

核心功能：

- 剧本选择。
- 角色表：角色代码、名称、类型、阵营、能力、首夜、其他夜、状态。
- 夜晚顺序：首夜和其他夜表格。
- 术语检索：关键词、分类。
- 相克规则检索：角色代码、严重级别。

### `/clocktower/replays` 钟楼回放复盘

用途：按游戏记录进入事件回放。

核心功能：

- 表格字段：游戏编号、房间/游戏、剧本、状态、阶段、开始时间、结束时间、身份/权限。
- 操作：查看回放。
- 当前历史接口不包含完整房间名称与身份信息，UI 需要有降级展示。

### `/clocktower/games/:gameId/replay` 钟楼回放

用途：按游戏 ID 查看公开、私密与审计可见事件。

核心功能：

- 游戏信息卡片：房间、游戏编号、剧本、状态、阶段、时间等。
- Tabs：公开事件、私密事件、全部事件。
- 事件时间线：事件序号、类型、阶段、可见性、发生时间、载荷摘要。

### `/clocktower/admin/audit` 钟楼审计

用途：按房间、游戏或会话 ID 查询钟楼审计数据，当前不提供全量列表。

核心功能：

- 查询条件：房间 ID、游戏 ID、会话 ID。
- Tabs：房间、游戏、聊天、邀请、成员、封禁。
- 房间审计：房间信息、房间座位。
- 游戏审计：游戏信息、游戏座位、游戏事件。
- 聊天审计：会话和消息，仅限钟楼游戏域。
- 详情抽屉：展示对象字段、事件时间线或消息列表。

## RBAC Management

### `/rbac/users` 用户管理

用途：维护用户资料、状态、角色授权和有效权限。

核心功能：

- 表格字段：账号、用户名、昵称、邮箱、手机、状态、锁定、密码过期。
- 操作：新建、编辑、分配角色、查看有效权限、重置密码、启用/禁用、删除。
- 用户抽屉字段：账号、用户名、初始密码、昵称、邮箱、手机、头像地址、状态、锁定、密码过期、备注。
- 角色抽屉：Transfer 分配角色。
- 有效权限抽屉：角色、菜单权限、按钮权限、API 权限。

### `/rbac/roles` 角色管理

用途：维护角色、直接权限和 RBAC1 角色继承。

核心功能：

- 表格字段：角色编码、角色名称、状态、排序、内置、描述。
- 操作：新建、编辑、分配权限、角色继承、删除。
- 角色抽屉字段：角色编码、角色名称、状态、排序、内置角色、描述。
- 权限抽屉：权限树，多选，支持“同步按钮关联 API”开关。
- 继承抽屉：Transfer 选择继承角色。

### `/rbac/permissions` 权限管理

用途：统一维护菜单、按钮和 API 权限。

核心功能：

- 表格字段：权限编码、权限名称、类型、状态、父权限、排序、描述。
- 操作：新建、编辑、切换状态、删除。
- 权限抽屉根据类型展示不同字段。

### `/rbac/menus` 菜单管理

用途：以树形结构维护前端菜单权限。

核心功能：

- 菜单树：节点显示菜单名称和路由/权限码。
- 操作：新建、编辑、删除。
- 菜单字段：父菜单、路由名称、路由路径、组件标识、重定向、图标、隐藏菜单、缓存页面、外链。

### `/rbac/buttons` 按钮管理

用途：按菜单维护按钮权限，并绑定按钮会调用的 API 权限。

核心功能：

- 顶部菜单选择器。
- 表格字段：按钮编码、按钮名称、按钮 Key、前端动作、状态。
- 操作：新建、编辑、绑定 API、删除。
- 按钮字段：所属菜单、按钮 Key、前端动作、样式提示、按钮说明、关联 API 权限。

### `/rbac/apis` API 权限

用途：维护动态 API 授权规则。

核心功能：

- 表格字段：权限编码、权限名称、方法、URL 模式、匹配、公开、风险、状态。
- 操作：新建、编辑、删除。
- API 字段：HTTP 方法、URL 模式、匹配类型、公开接口、服务标识、操作名称、风险等级。
- “扫描 API”按钮当前禁用，页面内有说明 Alert。

## Account Settings

### `/account/settings` 个人设置

用途：维护当前账号资料、登录密码和 Agent SoulMD。

核心功能：

- 基础资料：用户名只读、昵称、邮箱、手机、头像 URL。
- Agent SoulMD：启用注入开关、Markdown 文本、字符计数、保存。
- SoulMD 版本：版本列表、变更摘要、创建时间。
- 安全设置：当前密码、新密码、确认新密码。

## Permission and State Notes

- 菜单权限决定左侧导航是否展示以及路由是否可访问。
- 按钮权限决定新建、编辑、删除、导入、重试、取消、绑定等动作是否出现。
- `SUPER_ADMIN` 专属页面包括：`/agent/conversation-audits`、`/agent/run-audits`、`/rag/arxiv-logs`。
- 普通注册用户通常拥有 Chat、RAG 用户能力和部分 Agent/MCP 菜单，不默认拥有 RBAC 管理能力。
- 所有表格页都需要考虑空状态、加载状态、权限不足导致的操作缺失、横向滚动和长文本截断。
- 聊天页需要考虑流式生成、取消、历史加载竞争、会话切换、错误重试和来源引用。
- Clocktower 页面需要考虑房间未开始、无视角、旁观、玩家、说书人、审计视角等多种身份状态。

## Frontend Engineering Notes

### Scripts

Install dependencies:

```bash
bun install
```

Common commands:

```bash
bun run dev
bun run lint
bun run typecheck
bun run test
bun run test:coverage
bun run build
bun run analyze
```

- `dev` starts Vite locally.
- `lint` runs ESLint.
- `typecheck` runs TypeScript project checks without emitting files.
- `test` runs Vitest unit tests.
- `test:coverage` runs Vitest with coverage output.
- `build` runs typecheck and Vite production build.
- `analyze` builds with bundle analysis output at `dist/stats.html`.

### Backend Target

Vite proxies `/api` and `/demo` to the backend. The target is resolved in this order:

1. `VITE_BACKEND_TARGET`
2. `VITE_API_BASE_URL`
3. `http://localhost:${VITE_BACKEND_PORT || BACKEND_PORT || 28080}`

When `VITE_API_BASE_URL` is set in browser builds, frontend requests use it as the absolute API base URL.

### Response Contracts

- Spring MVC JSON responses and WebFlux `Mono<ApiResponse<T>>` responses use `requestJson<T>()`.
- File uploads use `requestFormData<T>()`, and their response body is still parsed as normal JSON.
- True NDJSON HTTP streams use `streamJsonLines<T>()`.
- Clocktower room events use SSE helpers.

Browser requests automatically send `X-Client-Type: browser`, include browser credentials, and attach `X-XSRF-TOKEN` for unsafe methods after initializing `/api/auth/csrf` when needed. Access and refresh tokens are not stored in browser localStorage.

### Validation

For frontend code changes, run:

```bash
bun run lint
bun run typecheck
bun run test
bun run build
```

For documentation-only edits, a lightweight check is enough:

```bash
git diff --check fe/README.md
```
