# 任务 11：Agent 私有视角与记忆系统

## 目标

让 Agent 像玩家一样“只知道自己该知道的信息”，并能记住公开发言、提名、投票、死亡、私有信息、角色声称和怀疑度。没有这层，Agent
会要么全知开挂，要么三秒记忆，两个都不适合社交推理。

## 依赖

- 任务 06：game event 统一写入 `clocktower_game_event`。
- 任务 09：夜晚私有信息事件。
- 任务 10：Agent Runtime。

## 当前代码现状

`ClocktowerGameViewServiceImpl` 已经有基于 viewer 的事件过滤和 ST grimoire 视图；`ClocktowerViewerResolver` 主要根据
`principal.userId` 找真人 player seat。Agent 没有 principal，因此需要专门的 `AgentPrivateView`，不要复用真人 HTTP viewer
resolver。

## 数据库设计

新增迁移：`Vxx__clocktower_agent_memory.sql`。

```sql
create table clocktower_agent_memory (
    id bigserial primary key,
    game_id bigint not null,
    agent_instance_id bigint not null,
    game_seat_id bigint not null,
    source_event_id bigint null,
    memory_type varchar(64) not null,
    visibility varchar(32) not null default 'SELF',
    subject_game_seat_id bigint null,
    content_json jsonb not null default '{}',
    confidence int not null default 50,
    day_no int not null default 0,
    night_no int not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted boolean not null default false
);

create index idx_clocktower_agent_memory_agent
    on clocktower_agent_memory(game_id, agent_instance_id, created_at)
    where deleted = false;

create index idx_clocktower_agent_memory_subject
    on clocktower_agent_memory(game_id, subject_game_seat_id)
    where deleted = false;
```

`memory_type`：

```text
PUBLIC_SPEECH_SUMMARY
ROLE_CLAIM
PRIVATE_INFO
NOMINATION_OBSERVATION
VOTE_OBSERVATION
DEATH_OBSERVATION
TRUST_SCORE
SUSPICION_SCORE
BLUFF_PLAN
EVIL_TEAM_INFO
QUESTION_TO_ASK
```

## AgentPrivateView

```java
public record AgentPrivateView(
        Long gameId,
        Long agentInstanceId,
        Long myGameSeatId,
        int mySeatNo,
        String phase,
        int dayNo,
        int nightNo,
        String myRoleCode,
        String myDisplayedRoleCode,
        String myAlignment,
        String myRoleType,
        String lifeStatus,
        String publicLifeStatus,
        boolean hasDeadVote,
        List<AgentPublicSeatView> publicSeats,
        List<AgentVisibleEventView> visibleEvents,
        List<AgentPrivateInfoView> privateInfos,
        List<AgentMemoryView> memories,
        List<AgentLegalIntentView> legalIntents,
        Map<String, Object> roleSpecificContext
) {}
```

## 视角规则

### 普通 Agent 可以看到

```text
- 自己的角色、阵营、生命状态
- 公开座位信息：seatNo、displayName、publicLifeStatus、是否 traveler、是否 Agent badge
- PUBLIC 事件
- visible_game_seat_ids_json 包含自己的 PRIVATE 事件
- 自己的 memory
- 当前可执行动作
```

### 普通 Agent 不可以看到

```text
- ST grimoire 全量信息
- 其他玩家真实角色
- 其他玩家私有夜晚信息
- hidden role / Drunk 真相
- 中毒/醉酒真实 marker，除非角色视角允许
```

### Spy 特例

Spy 可以看到 grimoire。实现方式：

```text
RoleVisionPolicy#canSeeGrimoire(roleCode) == true
```

不要用 `actorType=AGENT` 放开 grimoire；只能按角色放开。

## Memory 更新流程

Agent Runtime 每次处理任务前：

```text
1. 获取上次处理到的 event_seq。
2. 拉取新 visible events。
3. 调用 MemoryExtractor。
4. 写入 memory。
5. 更新 agent_instance.metadata.lastSeenEventSeq。
```

`MemoryExtractor` 示例：

```text
PUBLIC_SPEECH:
  - 摘要发言
  - 如果包含“我是/我跳/我声称”，生成 ROLE_CLAIM

NOMINATION_OPENED:
  - 记录谁提名谁
  - 根据阵营和对象微调 suspicion/trust

VOTE_CAST:
  - 记录投票立场
  - 如果某人保护高嫌疑对象，提高 suspicion

PRIVATE_INFO_RECEIVED:
  - 写 PRIVATE_INFO
  - 根据信息更新 suspicion/trust
```

第一版不用 NLP 很复杂，可以先做规则抽取：

```text
content 中包含 roleCode 中文名/英文名 -> ROLE_CLAIM
投 yes/投 no -> VOTE_OBSERVATION
```

后续 LLM 任务再做更自然的摘要。

## 信任/怀疑分数

建议在 memory 或 agent_instance metadata 中维护：

```json
{
  "suspicion": {
    "seat-1": 20,
    "seat-2": 65
  },
  "trust": {
    "seat-1": 70,
    "seat-2": 30
  }
}
```

也可以单独表，但 v1 用 JSON 足够。

更新原则：

```text
- 私有信息指向邪恶：suspicion +30
- 发言与已知信息矛盾：suspicion +20
- 投票保护高嫌疑对象：suspicion +10
- 被多个可信玩家支持：trust +10
- 自己是邪恶阵营时，信任/怀疑含义要变成“伪装策略视角”，不要真把队友当敌人
```

## 验收标准

- AgentPrivateView 不包含 grimoire，除非 Agent 角色是 Spy。
- Agent 能看到自己的 PRIVATE_INFO。
- Agent 看不到其他人的 PRIVATE_INFO。
- PUBLIC_SPEECH 会生成基础 memory。
- ROLE_CLAIM 能被记录。
- Agent instance metadata 记录 lastSeenEventSeq，重复处理不会重复写同一批 memory。

## 测试建议

- `normalAgentView_hidesGrimoire`
- `spyAgentView_includesGrimoire`
- `agentView_includesOwnPrivateInfo`
- `agentView_hidesOthersPrivateInfo`
- `memoryExtractor_recordsRoleClaim`
- `memoryUpdate_isIdempotentByEventSeq`

## 不在本任务做

- 复杂自然语言理解。
- LLM 总结。
- 具体提名/投票策略；任务 12 做。
