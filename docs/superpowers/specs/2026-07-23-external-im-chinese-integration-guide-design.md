# 2026-07-23 External IM 中文接入手册设计

## 目标

将 `docs/external-im-chat-agent.md` 从英文说明改写为可以直接执行的中文接入手册。重点说明 CyberMario 如何通过 NapCat HTTP Client 接收 QQ 事件、通过 NapCat HTTP Server 发送 QQ 消息，以及如何建立 Memory Space 与群聊、私聊绑定。

## 变更范围

- 直接将现有 `docs/external-im-chat-agent.md` 改为中文，不保留英文副本。
- 保留 Telegram、上下文与隐私边界、发布限制等现有信息。
- 补充当前文档缺失的 NapCat 接入前提、数据库绑定 SQL、联调检查和常见故障处理。
- 不修改 Java、YAML、数据库迁移或运行时行为。

## 文档结构

1. **能力与数据流**：说明 NapCat HTTP Client → CyberMario Webhook → Chat Guard → Chat Agent → NapCat HTTP Server 的链路，并明确当前仅支持文本。
2. **接入前提**：列出已登录的 NapCat QQ、可互通的网络、CyberMario 用户 ID、PostgreSQL 和模型配置等前置条件。
3. **CyberMario 配置**：逐项解释 `AGENT_EXTERNAL_IM_QQ_*` 环境变量、默认端口和 Token 一致性要求。
4. **NapCat HTTP Server**：给出 WebUI 配置字段，用于 CyberMario 调用 `/send_group_msg` 与 `/send_private_msg`。
5. **NapCat HTTP Client**：给出事件上报地址、`messagePostFormat=array`、`reportSelfMessage=false` 和 Token 配置。
6. **Memory Space 与会话绑定**：提供 PostgreSQL SQL 模板，分别覆盖群聊 `group:<group_id>` 和私聊 `private:<user_id>`，并解释 `audience_key`。
7. **启动与验收**：给出连通性检查、NapCat API 检查、群聊与私聊消息验证、事件表与 Guard 审计查询。
8. **行为与安全边界**：说明群聊 Guard、引用回复、重试语义、共享记忆方向、私聊信息泄露风险和单实例限制。
9. **常见问题**：覆盖 401、400、收不到事件、能收不能发、事件落库但不回复、Docker 中 `127.0.0.1` 指向错误等问题。
10. **Telegram 与扩展边界**：保留简明的 Telegram 配置和 WeCom 后续 Adapter 说明。

## 关键约定

- `connector_id` 使用当前配置中的 `main`。
- 群聊会话 ID 必须为 `group:<群号>`，私聊会话 ID 必须为 `private:<QQ号>`。
- `audience_key` 分别为 `qq:main:group:<群号>` 和 `qq:main:private:<QQ号>`。
- NapCat HTTP Client 与 HTTP Server Token 使用同一强随机值，并与 `AGENT_EXTERNAL_IM_QQ_MAIN_ACCESS_TOKEN` 一致。
- NapCat HTTP Client 必须使用数组消息格式；字符串 CQ 码格式会被 CyberMario 拒绝。
- 当前没有面向运营人员的 Memory Space/绑定 REST 接口，因此手册使用显式 PostgreSQL SQL 模板，并提醒先确认 `sys_user.id`。
- 文档中的 SQL 使用占位符，要求操作者替换后在事务中执行，避免误绑用户或会话。

## 验证方式

- 使用仓库搜索确认所有环境变量、Webhook 路径、表名和枚举值与当前实现一致。
- 检查 Markdown 结构、代码块和链接。
- 执行 `git diff --check`，确保没有空白错误。
- 本任务只修改文档，不启动应用，不声称完成真实 NapCat 或 PostgreSQL 联调。

## 验收标准

- 中文读者无需查看 Java 源码即可完成 NapCat HTTP Server、HTTP Client 与 CyberMario 的配置。
- 群聊和私聊均有完整绑定示例和验收步骤。
- 文档明确区分“NapCat 能访问 CyberMario”和“CyberMario 能访问 NapCat”两个网络方向。
- 文档不会把当前未实现的多模态、后置 Reply Guard、分布式 Worker 或自有 IM 接入描述为已完成。
