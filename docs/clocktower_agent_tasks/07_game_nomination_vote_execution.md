# 任务 07：新游戏提名、投票、处决模型

## 目标

把提名、投票、处决迁移到新 `clocktower_game_*` 模型，避免新游戏视图读一套状态、旧流程写另一套状态。

## 依赖

- 任务 06：`ClocktowerGameActionExecutor`。

## 当前代码现状

旧 `ClocktowerActionServiceImpl` 当前使用：

```text
ClocktowerNominationPo
ClocktowerVotePo
ClocktowerNominationRepository
ClocktowerVoteRepository
```

旧 `ClocktowerFlowServiceImpl` 也使用旧 nomination/vote 计算 open nomination、execution candidate、alive count 等。真正
Agent Player 需要新 game 层数据。

## 数据库设计

新增迁移：`Vxx__clocktower_game_nomination_vote.sql`。

### `clocktower_game_nomination`

```sql
create table clocktower_game_nomination (
    id bigserial primary key,
    game_id bigint not null,
    day_no int not null,
    nominator_game_seat_id bigint not null,
    nominee_game_seat_id bigint not null,
    status varchar(32) not null default 'OPEN',
    vote_count int not null default 0,
    required_votes int not null default 0,
    opened_at timestamptz not null default now(),
    closed_at timestamptz null,
    metadata_json jsonb not null default '{}',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted boolean not null default false
);

create index idx_clocktower_game_nomination_game_day
    on clocktower_game_nomination(game_id, day_no)
    where deleted = false;

create unique index uk_clocktower_game_nomination_open
    on clocktower_game_nomination(game_id)
    where deleted = false and status = 'OPEN';
```

### `clocktower_game_vote`

```sql
create table clocktower_game_vote (
    id bigserial primary key,
    game_id bigint not null,
    nomination_id bigint not null,
    voter_game_seat_id bigint not null,
    vote_value boolean not null,
    used_dead_vote boolean not null default false,
    status varchar(32) not null default 'CAST',
    metadata_json jsonb not null default '{}',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted boolean not null default false
);

create unique index uk_clocktower_game_vote_once
    on clocktower_game_vote(nomination_id, voter_game_seat_id)
    where deleted = false;
```

### `clocktower_game_execution`

```sql
create table clocktower_game_execution (
    id bigserial primary key,
    game_id bigint not null,
    day_no int not null,
    nominee_game_seat_id bigint null,
    nomination_id bigint null,
    status varchar(32) not null default 'PENDING',
    executed boolean not null default false,
    resolved_at timestamptz null,
    metadata_json jsonb not null default '{}',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted boolean not null default false
);

create unique index uk_clocktower_game_execution_day
    on clocktower_game_execution(game_id, day_no)
    where deleted = false;
```

## 服务结构

```text
clocktower/game/nomination
  ├── po
  ├── repository
  ├── service
  │   ├── ClocktowerGameNominationService.java
  │   └── ClocktowerGameVoteService.java
  └── impl
```

## 提名规则 v1

`NOMINATE` 执行规则：

```text
- game.phase = DAY 或 NOMINATION
- 公聊 mic session 已 CLOSED，或者 ST override 允许提前提名
- nominator 是 active player seat
- nominator life_status = ALIVE
- nominator 今天还没提名过
- nominee 是 active player seat
- nominee 今天未被提名过，除非规则/脚本允许
- 当前没有 OPEN nomination
```

写入：

```text
clocktower_game_nomination.status = OPEN
clocktower_game_event.event_type = NOMINATION_OPENED
```

`required_votes`：

```java
int aliveCount = gameSeatRepository.countAlivePlayers(gameId);
int requiredVotes = (aliveCount + 1) / 2;
```

BOTC 通常是“至少半数存活玩家”，奇数时向上取整。

## 投票规则 v1

`VOTE` 执行规则：

```text
- nomination.status = OPEN
- voter 是 active player seat
- 每个 nomination 每个 seat 只能投一次
- alive player 可以投
- dead player 只有 has_dead_vote=true 时可以投 yes；投 yes 后消耗死票
- vote=false 可以记录，也可以不消耗死票
```

写入：

```text
clocktower_game_vote
clocktower_game_event.event_type = VOTE_CAST
```

如果 dead player 投 `true`：

```text
game_seat.has_dead_vote = false
```

## 关闭投票

ST 或系统关闭 nomination：

```text
nomination.status = CLOSED
nomination.vote_count = yes votes
```

然后计算当日 execution candidate：

```text
- vote_count >= required_votes 才有候选
- 当天最高票候选暂定为 execution candidate
- 如果平票，按 BOTC 规则可能无人被处决；v1 可先做“平票清空候选”
```

写事件：

```text
NOMINATION_CLOSED
EXECUTION_CANDIDATE_UPDATED
```

## 处决规则 v1

ST 调用：

```text
POST /api/clocktower/games/{gameId}/executions/resolve
```

body：

```json
{
  "execute": true,
  "targetGameSeatId": 3,
  "nominationId": 12
}
```

执行后：

```text
- target.game_seat.life_status = DEAD
- public_life_status = DEAD
- 写 PLAYER_EXECUTED
- 写 PLAYER_DIED
- execution.status = RESOLVED
```

如果无人处决：

```text
execution.executed = false
execution.status = RESOLVED
event_type = NO_EXECUTION
```

## Agent 影响

Agent 可以：

```text
- 被提名
- 提名别人，如果 alive
- 投票
- 死后保留一次死票
```

但 Agent 的动作来源是内部 `AgentGameActionService`，不是 principal API。

## 验收标准

- 新游戏可以创建 nomination。
- 一个玩家一天只能提名一次。
- 同时只能有一个 OPEN nomination。
- 投票记录写到 `clocktower_game_vote`。
- 死人投 yes 会消耗 dead vote。
- 关闭 nomination 后能产生当日处决候选。
- ST 可以 resolve execution。
- game event 能被 `ClocktowerGameViewServiceImpl` 读取。

## 测试建议

- `nominate_beforeMicClosed_rejected`
- `nominate_alivePlayer_success`
- `nominate_twiceSameDay_rejected`
- `vote_deadPlayerUsesDeadVote`
- `closeNomination_updatesVoteCount`
- `resolveExecution_marksSeatDead`
- `tieVote_noExecutionCandidate`

## 不在本任务做

- 夜晚死亡和角色能力。
- Agent 决策策略。
- 完整胜利判定，只保留 hook 给任务 08。
