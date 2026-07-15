# 任务 09：夜晚任务引擎 —— Trouble Brewing v0

## 目标

新增新 game 模型下的夜晚任务系统，让真人和 Agent 都可以提交夜晚选择，由 ST / Rule Engine 负责结算。第一版只覆盖 Trouble
Brewing，足够让 Agent 局完整跑通。

## 依赖

- 任务 06：新动作执行器。
- 任务 08：新 flow service 可以等待 pending night tasks。

## 当前代码现状

旧 `ClocktowerFlowServiceImpl#ensureNightTasks` 和旧 storyteller task 是 room 模型。新 `ClocktowerGamePo` 开局已有
`phase=FIRST_NIGHT`，但新 game 模型缺少 night task 表和角色结算服务。

## 数据库设计

新增迁移：`Vxx__clocktower_game_night_task.sql`。

```sql
create table clocktower_game_night_task (
    id bigserial primary key,
    game_id bigint not null,
    night_no int not null,
    game_seat_id bigint not null,
    role_code varchar(64) not null,
    task_type varchar(64) not null,
    status varchar(32) not null default 'PENDING',
    sort_order int not null,
    choice_json jsonb not null default '{}',
    result_json jsonb not null default '{}',
    resolved_by_actor_id bigint null,
    metadata_json jsonb not null default '{}',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted boolean not null default false
);

create index idx_clocktower_game_night_task_game_night
    on clocktower_game_night_task(game_id, night_no, sort_order)
    where deleted = false;

create index idx_clocktower_game_night_task_seat
    on clocktower_game_night_task(game_seat_id)
    where deleted = false;
```

`status`：

```text
PENDING
CHOSEN
DONE
SKIPPED
CANCELLED
```

`task_type`：

```text
CHOOSE_TARGET
RECEIVE_INFO
ST_RESOLVE
```

## 服务结构

```text
clocktower/game/night
  ├── po
  ├── repository
  ├── dto
  ├── service
  │   ├── ClocktowerGameNightTaskService.java
  │   ├── ClocktowerNightOrderService.java
  │   ├── ClocktowerRoleSkillService.java
  │   └── ClocktowerNightResolutionService.java
  └── role
      ├── RoleSkill.java
      └── troublebrewing
```

## 角色技能接口

```java
public interface RoleSkill {
    String roleCode();

    boolean actsOnFirstNight();

    boolean actsOnOtherNights();

    List<NightTaskSpec> createTasks(ClocktowerGamePo game, ClocktowerGameSeatPo seat, int nightNo);

    List<AvailableTargetSpec> legalTargets(NightTaskContext context);

    NightChoice autoChoose(NightTaskContext context);

    NightResolution resolve(NightTaskContext context, NightChoice choice);
}
```

重点：Agent 只做 `autoChoose`；结算由 `resolve` 或 ST 做。

## Trouble Brewing v0 覆盖

### 必须先做

| 角色             | v0 行为                           |
|----------------|---------------------------------|
| Imp            | 每个普通夜晚选择 1 个杀人目标；结算时处理死亡        |
| Poisoner       | 每晚选择 1 个中毒目标，写 marker           |
| Monk           | 每晚选择 1 个保护目标，写 marker           |
| Fortune Teller | 每晚选 2 个目标，给 yes/no 私有信息         |
| Empath         | 每晚/每天按邻座邪恶数给信息                  |
| Undertaker     | 看到当天处决玩家角色                      |
| Washerwoman    | 第一夜给两个候选和一个 Townsfolk 信息        |
| Librarian      | 第一夜给两个 Outsider 候选或 0           |
| Investigator   | 第一夜给两个 Minion 候选                |
| Chef           | 第一夜给邪恶相邻对数                      |
| Butler         | 每天/夜里选择 master，投票时受约束           |
| Spy            | 可以获得 grimoire view；这是唯一 v0 天眼角色 |

### 可以先简化

| 角色            | 简化策略                                                 |
|---------------|------------------------------------------------------|
| Ravenkeeper   | 死亡后触发一次私有信息；v0 可由 ST 触发                              |
| Virgin        | 首次被 Townsfolk 提名时处决提名者；v0 可在 nomination service hook |
| Slayer        | 白天 action；v0 可先留 hook                                |
| Soldier       | 被恶魔攻击不死；v0 在 Imp resolve hook                        |
| Mayor         | 胜利/死亡重定向特殊规则；v0 可先手动 ST resolve                      |
| Saint         | 被处决邪恶胜利；v0 在 execution hook                          |
| Recluse       | 检测时可被看作邪恶；v0 可让 ST/规则随机                              |
| Drunk         | Agent 私有视角不知道自己是 Drunk；分配时显示假身份                      |
| Scarlet Woman | 恶魔死亡且人数条件满足时接魔；v0 在 death hook                       |
| Baron         | 开局改 Outsider 数；角色分配阶段处理，v0 不在 night task 里做          |

```

## 夜晚任务生命周期

### 创建任务

Flow 进入 FIRST_NIGHT / NIGHT 时：

```text
for each active game seat:
  roleSkill.createTasks(...)
  save PENDING tasks by sort_order
  append NIGHT_TASKS_CREATED event SYSTEM_ONLY
  schedule Agent NIGHT_TASK_OPENED for Agent seats
```

### 玩家/Agent 选择

新 action：

```text
NIGHT_CHOICE
```

写入：

```text
task.choice_json
task.status = CHOSEN
clocktower_game_event.event_type = NIGHT_CHOICE_SUBMITTED
visibility = PRIVATE 或 SYSTEM_ONLY
```

### ST / Rule Engine 结算

```text
CHOSEN -> DONE
PENDING -> SKIPPED, 如果任务允许 skip
```

写入：

```text
PRIVATE_INFO_RECEIVED
MARKER_APPLIED
PLAYER_DIED
DEMON_KILL_PROTECTED
```

## Agent 注意事项

Agent 可以自动提交 `NIGHT_CHOICE`，但不能直接决定：

```text
谁死亡
谁收到什么最终信息
醉酒/中毒是否生效
保护是否挡刀
```

这些必须由 `ClocktowerNightResolutionService` 或 ST 控制台结算。

## 验收标准

- FIRST_NIGHT 进入后会为相关角色创建 night task。
- Agent seat 的 task 可以被内部 service 自动选择。
- 真人 seat 的 task 可以通过新 game action 提交。
- 所有 mandatory task 完成后，任务 08 的 flow 允许进入 DAY。
- Imp 杀人、Monk 保护、Poisoner 中毒至少有基础 marker/结算。
- 信息类角色会收到 PRIVATE event，普通 Agent 只能在私有视角看到自己的信息。

## 测试建议

- `createFirstNightTasks_troubleBrewing_ordered`
- `agentNightChoice_submitsChoiceOnly`
- `impKill_protectedByMonk_noDeath`
- `fortuneTeller_receivesPrivateInfo`
- `spy_canSeeGrimoireInfo`
- `normalAgent_cannotSeeOtherPrivateInfo`

## 不在本任务做

- LLM。
- 高级角色脚本。
- 私聊。
