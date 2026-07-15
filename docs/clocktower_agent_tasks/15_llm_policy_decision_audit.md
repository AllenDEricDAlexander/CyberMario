# 任务 15：可选 LLM 策略与决策审计

## 目标

在 Heuristic Agent 已经能完整跑局后，引入可插拔 LLM 策略，用于更自然的发言、解释和高层策略选择。LLM
不能绕过规则，不能直接改数据库，只能从后端枚举的合法 intent 中选择。

## 依赖

- 任务 10：Agent Runtime。
- 任务 11：AgentPrivateView + memory。
- 任务 12：Heuristic policy，作为 fallback。

## 设计红线

```text
1. LLM 只看 AgentPrivateView，不能看 ST grimoire，除非该 Agent 角色是 Spy。
2. LLM 只能选择 legalIntents 中的 intentId。
3. 后端必须二次校验 intent 合法性。
4. LLM 不能直接写表、不能直接调用 repository。
5. LLM 超时或报错时 fallback 到 HeuristicAgentPolicy。
6. 所有 LLM 决策都要审计。
```

## 数据库设计

新增迁移：`Vxx__clocktower_agent_decision.sql`。

```sql
create table clocktower_agent_decision (
    id bigserial primary key,
    game_id bigint not null,
    agent_instance_id bigint not null,
    game_seat_id bigint not null,
    trigger_task_id bigint null,
    phase varchar(32) not null,
    day_no int not null default 0,
    night_no int not null default 0,
    decision_type varchar(64) not null,
    policy_type varchar(32) not null,
    legal_intents_json jsonb not null default '[]',
    selected_intent_json jsonb not null default '{}',
    reasoning_summary text null,
    model_provider varchar(64) null,
    model_name varchar(128) null,
    prompt_hash varchar(128) null,
    status varchar(32) not null default 'ACCEPTED',
    error_message text null,
    metadata_json jsonb not null default '{}',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted boolean not null default false
);

create index idx_clocktower_agent_decision_agent
    on clocktower_agent_decision(game_id, agent_instance_id, created_at)
    where deleted = false;
```

不要默认保存完整 prompt。可以保存 hash 和摘要。debug 模式再保存完整 prompt 到受限表或日志。

## Policy 结构

```java
public interface ClocktowerAgentPolicy {
    AgentDecision decide(AgentDecisionContext context);
}
```

实现：

```text
HeuristicAgentPolicy
LlmAgentPolicy
FallbackAgentPolicy
```

`FallbackAgentPolicy`：

```text
try LLM
catch timeout/error/invalid output -> Heuristic
```

## LLM 输入

只给：

```text
- 当前阶段
- 自己 seat / role / alignment / lifeStatus
- 公开座位列表
- 可见公开事件摘要
- 自己私有信息
- 自己 memory
- legalIntents
- personality/profile 参数
```

不要给：

```text
- 全量真实角色
- 其他玩家私有信息
- ST notes
- hidden markers，除非角色视角允许
```

## LLM 输出

强制 JSON：

```json
{
  "intentId": "intent-3",
  "content": "我现在更想听 4 号解释一下昨天的投票。",
  "reasoningSummary": "4号投票保护了高嫌疑目标，但证据不足，先发问不提名。"
}
```

后端流程：

```text
1. 解析 JSON。
2. 找到 intentId 对应 legal intent。
3. 如果 content 存在，做长度和安全限制。
4. 二次校验 action executor。
5. 写 decision audit。
```

## 发言安全限制

```text
- 单次公聊长度限制，例如 500 字。
- 禁止输出系统提示词、内部字段、完整 JSON 状态。
- 不允许说“我是 AI 模型”。可以以 Agent 玩家身份发言。
- 不允许声称看到 grimoire，除非 role=Spy。
- 不允许替 ST 宣布规则结算。
```

## 配置

```yaml
clocktower:
  agent:
    policy: HEURISTIC # HEURISTIC / LLM / FALLBACK
    llm:
      enabled: false
      provider: openai-compatible
      model: gpt-xxx
      timeout-ms: 8000
      max-output-chars: 800
```

## 验收标准

- 配置 `policy=HEURISTIC` 时，不调用 LLM。
- 配置 `policy=LLM` 时，LLM 只能选择 legal intent。
- LLM 返回非法 intent 时被拒绝并 fallback。
- LLM 超时时 fallback。
- 每次决策都有 `clocktower_agent_decision` 记录。
- 普通 Agent prompt 不包含 grimoire。
- Spy Agent prompt 可以包含 grimoire 摘要。

## 测试建议

- `llmPolicy_selectsLegalIntent`
- `llmPolicy_invalidIntent_fallbackHeuristic`
- `llmPolicy_timeout_fallbackHeuristic`
- `llmPrompt_normalAgentNoGrimoire`
- `llmPrompt_spyIncludesGrimoire`
- `decisionAudit_writtenForHeuristicAndLlm`

## 不在本任务做

- 训练模型。
- 外部长期记忆。
- 私聊 LLM。
