# 任务 05：公聊限制 —— 轮流麦一轮 + 抢麦 5 分钟

## 目标

实现 Clocktower 公聊秩序：

```text
DAY 阶段开始后：
1. 先按座位顺序轮流麦一轮。
2. 同一时间只能有一个玩家说话。
3. 轮流麦完成后进入 5 分钟抢麦。
4. 抢麦期间仍然同一时间只能一个人说话。
5. 抢麦 5 分钟结束后，公聊阶段关闭，进入提名/其他 ST 可控流程。
```

这条规则同时适用于真人和 Agent。Agent 也必须等到自己拿到麦，才能发公聊。

## 依赖

- 任务 04：已经有 `ClocktowerGameSeatPo.actorType` / `agentInstanceId`。
- 可以先独立于完整 Agent Runtime 做；Agent 到了任务 10 才自动响应麦序。

## 当前代码现状

- 新游戏视图 `ClocktowerGameViewServiceImpl` 会根据 phase 暴露 `PUBLIC_SPEECH`，但没有麦序模型。
- 旧 `ClocktowerActionServiceImpl` 支持 `PUBLIC_SPEECH`，但它走旧 room action，且没有“一次只能一个人说话”的限制。
- 新游戏页现在对玩家动作是 `actionControlsEnabled={false}`，还没有真正的新 game action 提交链路。

## 数据库设计

新增迁移：`Vxx__clocktower_public_mic.sql`。

### 1. `clocktower_game_public_mic_session`

```sql
create table clocktower_game_public_mic_session (
    id bigserial primary key,
    game_id bigint not null,
    day_no int not null,
    status varchar(32) not null default 'ROUND_ROBIN',
    current_holder_game_seat_id bigint null,
    current_turn_id bigint null,
    round_started_at timestamptz null,
    round_finished_at timestamptz null,
    grab_started_at timestamptz null,
    grab_ends_at timestamptz null,
    closed_at timestamptz null,
    metadata_json jsonb not null default '{}',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted boolean not null default false
);

create unique index uk_clocktower_mic_session_game_day
    on clocktower_game_public_mic_session(game_id, day_no)
    where deleted = false;
```

`status`：

```text
ROUND_ROBIN
GRAB_MIC
CLOSED
```

### 2. `clocktower_game_public_mic_turn`

```sql
create table clocktower_game_public_mic_turn (
    id bigserial primary key,
    session_id bigint not null,
    game_id bigint not null,
    day_no int not null,
    game_seat_id bigint not null,
    turn_order int not null,
    stage varchar(32) not null,
    acquisition_type varchar(32) not null,
    status varchar(32) not null default 'PENDING',
    started_at timestamptz null,
    ended_at timestamptz null,
    expires_at timestamptz null,
    speech_event_id bigint null,
    metadata_json jsonb not null default '{}',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted boolean not null default false
);

create index idx_clocktower_mic_turn_session
    on clocktower_game_public_mic_turn(session_id, turn_order)
    where deleted = false;

create index idx_clocktower_mic_turn_game_seat
    on clocktower_game_public_mic_turn(game_id, game_seat_id)
    where deleted = false;
```

`stage`：

```text
ROUND_ROBIN
GRAB_MIC
```

`acquisition_type`：

```text
ROUND_ROBIN
GRAB
ST_GRANT
SYSTEM_TIMEOUT
```

`status`：

```text
PENDING
ACTIVE
DONE
SKIPPED
EXPIRED
CANCELLED
```

## 麦序规则

### 1. 轮流麦

DAY 阶段开始时创建 mic session：

```text
status = ROUND_ROBIN
round_started_at = now()
```

按 `game_seat.seat_no asc` 为所有 eligible player 建 turn：

```text
status = PENDING
stage = ROUND_ROBIN
acquisition_type = ROUND_ROBIN
```

eligible player 建议：

```text
- game_seat.status = ACTIVE
- actor_type in HUMAN / AGENT
- 不要求 alive；BOTC 死人也能发言
- metadata.muted != true
- metadata.leftGame != true
```

轮流麦启动第一位：

```text
session.current_holder_game_seat_id = firstTurn.game_seat_id
session.current_turn_id = firstTurn.id
firstTurn.status = ACTIVE
firstTurn.started_at = now()
firstTurn.expires_at = now() + roundRobinTurnSeconds
```

`roundRobinTurnSeconds` 建议配置：

```yaml
clocktower:
  public-mic:
    round-robin-turn-seconds: 45
    grab-mic-total-seconds: 300
    grab-mic-hold-seconds: 45
```

### 2. 同一时间只能一个人说话

`PUBLIC_SPEECH` 执行前必须校验：

```text
当前 game/day 存在 mic session
session.status in ROUND_ROBIN / GRAB_MIC
session.current_holder_game_seat_id == actorGameSeatId
当前 turn.status == ACTIVE
now < turn.expires_at
```

否则拒绝：

```text
当前不是你的发言时间
```

### 3. 结束当前轮流麦

当前玩家可主动点击“说完了”：

```text
turn.status = DONE
turn.ended_at = now()
session.current_holder_game_seat_id = null
session.current_turn_id = null
```

然后系统激活下一个 PENDING turn。

如果超时：

```text
turn.status = EXPIRED
metadata.timeout = true
激活下一个 PENDING turn
```

轮流麦所有 turn 都结束后：

```text
session.status = GRAB_MIC
session.round_finished_at = now()
session.grab_started_at = now()
session.grab_ends_at = now() + 5 minutes
```

### 4. 抢麦 5 分钟

抢麦期间：

```text
session.status = GRAB_MIC
now < session.grab_ends_at
session.current_holder_game_seat_id is null
```

玩家点击抢麦：

```text
创建 GRAB_MIC turn
turn.status = ACTIVE
turn.started_at = now()
turn.expires_at = min(now() + grabMicHoldSeconds, session.grab_ends_at)
session.current_holder_game_seat_id = actorGameSeatId
```

如果有人正在发言，其他人不能抢麦。

抢麦总时长到 5 分钟：

```text
session.status = CLOSED
session.closed_at = now()
session.current_holder_game_seat_id = null
session.current_turn_id = null
```

ST 可以扩展：

```text
POST /api/clocktower/games/{gameId}/mic/sessions/{sessionId}/extend
body: {"seconds": 120}
```

## 后端服务

新增包：

```text
clocktower/game/mic
  ├── po
  │   ├── ClocktowerGamePublicMicSessionPo.java
  │   └── ClocktowerGamePublicMicTurnPo.java
  ├── repository
  ├── dto
  ├── service
  │   ├── ClocktowerPublicMicService.java
  │   └── impl/ClocktowerPublicMicServiceImpl.java
  └── controller
      └── ClocktowerPublicMicController.java
```

服务接口：

```java
public interface ClocktowerPublicMicService {
    ClocktowerMicSessionView startDayMicSession(Long gameId, int dayNo, RbacPrincipal principal);
    ClocktowerMicSessionView getMicSession(Long gameId);
    ClocktowerMicTurnView finishCurrentTurn(Long gameId, Long turnId, RbacPrincipal principal);
    ClocktowerMicTurnView skipTurn(Long gameId, Long turnId, RbacPrincipal principal);
    ClocktowerMicTurnView grabMic(Long gameId, RbacPrincipal principal);
    ClocktowerMicSessionView releaseMic(Long gameId, RbacPrincipal principal);
    ClocktowerMicSessionView closeExpiredSessions();
    boolean canSpeak(Long gameId, Long actorGameSeatId);
}
```

## API 建议

```text
GET    /api/clocktower/games/{gameId}/mic
POST   /api/clocktower/games/{gameId}/mic/start-day
POST   /api/clocktower/games/{gameId}/mic/turns/{turnId}/finish
POST   /api/clocktower/games/{gameId}/mic/turns/{turnId}/skip
POST   /api/clocktower/games/{gameId}/mic/grab
POST   /api/clocktower/games/{gameId}/mic/release
POST   /api/clocktower/games/{gameId}/mic/extend
```

权限：

```text
start-day / skip / extend：ST only
finish / release：当前麦持有人或 ST
抓麦：当前 game 的 HUMAN player；Agent 走内部 Agent service，不走 principal API
```

## 与 Action Executor 的关系

本任务可以先建服务和表。等任务 06 做 `ClocktowerGameActionExecutor` 时，`PUBLIC_SPEECH` 必须调用：

```java
publicMicService.requireCanSpeak(gameId, actorGameSeatId);
```

Agent 发言也必须经过同一规则。

## 与 Agent 的关系

Agent Runtime 后续监听：

```text
MIC_TURN_STARTED
MIC_GRAB_OPENED
MIC_SESSION_CLOSED
```

在轮到 Agent 的轮流麦时，Agent 可以：

```text
- 发一句公开发言
- 或 PASS / SKIP
```

抢麦阶段 Agent 可以根据策略选择是否抢麦，但必须遵守 5 分钟和单人麦规则。

## 前端展示要求

放到任务 13 具体实现，本任务只定义数据：

```text
当前阶段：轮流麦 / 抢麦 / 已关闭
当前麦持有人
自己的麦状态
轮流麦队列
抢麦剩余时间 5:00 -> 0:00
当前发言剩余时间
按钮：说完了 / 抢麦 / 释放麦 / ST 跳过 / ST 延长
```

## 验收标准

- DAY 开始可以创建一个 mic session。
- 轮流麦按 seat_no 依次激活。
- 同一时间只有一个 ACTIVE turn。
- 非麦持有人提交 `PUBLIC_SPEECH` 会失败。
- 轮流麦全部完成后自动进入 5 分钟抢麦。
- 抢麦期间同时只能一个人持麦。
- 5 分钟结束后 session 关闭。
- ST 可以跳过、关闭、延长。

## 测试建议

- `startDayMicSession_createsRoundRobinTurnsInSeatOrder`
- `publicSpeech_withoutMic_rejected`
- `finishTurn_activatesNextTurn`
- `allRoundRobinTurnsDone_entersGrabMic`
- `grabMic_whenFree_success`
- `grabMic_whenOccupied_rejected`
- `grabMic_afterFiveMinutes_rejected`
- `stCanSkipCurrentTurn`

## 不在本任务做

- 不做新 action executor 的全部动作。
- 不做 Agent 自动发言。
- 不做前端 UI。
