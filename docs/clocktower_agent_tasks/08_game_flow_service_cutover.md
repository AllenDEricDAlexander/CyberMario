# 任务 08：新 Game Flow Service —— 阶段推进从旧 room 模型切到新 game 模型

## 目标

新增 `ClocktowerGameFlowService`，基于 `ClocktowerGamePo`、`ClocktowerGameSeatPo`、新 nomination/vote/execution/night task
表推进阶段。新局不再依赖旧 `ClocktowerFlowServiceImpl`。

## 依赖

- 任务 05：公聊 mic session。
- 任务 07：新提名/投票/处决模型。
- 任务 09 之后夜晚任务更完整；本任务可以先留接口。

## 当前代码现状

旧 `ClocktowerFlowServiceImpl` 当前负责：

```text
- ensureNightTasks
- buildFlow
- 根据 old room phase/day/night 推进
- 检查 old storyteller task、old nomination、old vote
- 判断 victory candidate
```

但新游戏视图读的是 `ClocktowerGamePo.phase`、`ClocktowerGameSeatPo`、`ClocktowerGameEventPo`。因此需要新 flow。

## 新包结构

```text
clocktower/game/flow
  ├── dto
  │   ├── ClocktowerGameFlowView.java
  │   ├── ClocktowerGameAdvanceRequest.java
  │   └── ClocktowerGameAdvanceResult.java
  ├── service
  │   ├── ClocktowerGameFlowService.java
  │   └── ClocktowerGameVictoryService.java
  └── impl
```

## 阶段定义

建议先保持当前 `ClocktowerGamePo.phase` 兼容：

```text
FIRST_NIGHT
NIGHT
DAY
NOMINATION
ENDED
```

公聊 mic 是 DAY 内部状态，不一定新加 phase。原因：现有前端和 game view 已经认 DAY。

DAY 内部顺序：

```text
DAY started
  -> public mic ROUND_ROBIN
  -> public mic GRAB_MIC 5 minutes
  -> mic CLOSED
  -> NOMINATION phase 或 DAY_NOMINATION_WINDOW
```

如果想更明确，也可以后续扩展：

```text
DAY_PUBLIC_DISCUSSION
DAY_NOMINATION
```

但 v1 先少改 phase。

## Flow View

```java
public record ClocktowerGameFlowView(
        Long gameId,
        String status,
        String phase,
        int dayNo,
        int nightNo,
        boolean advanceAllowed,
        List<String> blockingReasons,
        String nextPhase,
        Map<String, Object> counters
) {}
```

blocking reasons 示例：

```text
MIC_ROUND_ROBIN_NOT_FINISHED
MIC_GRAB_MIC_NOT_FINISHED
OPEN_NOMINATION_EXISTS
EXECUTION_NOT_RESOLVED
PENDING_NIGHT_TASKS
GAME_ALREADY_ENDED
```

## 推进规则 v1

### FIRST_NIGHT -> DAY

允许条件：

```text
- 所有 mandatory night task status in DONE / SKIPPED
```

动作：

```text
- game.phase = DAY
- game.day_no = 1
- append PHASE_CHANGED
- 创建 day 1 mic session，进入 ROUND_ROBIN
- schedule Agent PHASE_CHANGED / MIC_TURN_STARTED task
```

### NIGHT -> DAY

允许条件同上。

动作：

```text
- day_no += 1
- phase = DAY
- 创建新 day mic session
```

### DAY -> NOMINATION

允许条件：

```text
- mic session status = CLOSED
```

动作：

```text
- phase = NOMINATION
- append PHASE_CHANGED
```

也可以由 ST 手动提前关闭 mic 后推进。

### NOMINATION -> NIGHT

允许条件：

```text
- 没有 OPEN nomination
- 当日 execution 已 RESOLVED 或 ST 显式选择 no execution
- 胜利判定未结束游戏
```

动作：

```text
- phase = NIGHT
- night_no += 1
- 创建 night tasks
- append PHASE_CHANGED
```

### 任意阶段 -> ENDED

胜利条件满足：

```text
- demon all dead -> GOOD_WIN
- alive player count <= 2 and demon alive -> EVIL_WIN
- Saint 被处决等特殊规则 -> EVIL_WIN
```

v1 可以先实现基础 demon/aliveCount，再把角色特殊胜利交给任务 09。

## Controller

```text
GET  /api/clocktower/games/{gameId}/flow
POST /api/clocktower/games/{gameId}/flow/advance
POST /api/clocktower/games/{gameId}/flow/force-advance
```

权限：

```text
advance：ST only
force-advance：ST only，payload 必须写 metadata.reason
```

## 与旧 Flow 的关系

保留旧：

```text
ClocktowerFlowServiceImpl
/api/clocktower/rooms/{roomId}/flow/...
```

新增新：

```text
ClocktowerGameFlowServiceImpl
/api/clocktower/games/{gameId}/flow/...
```

前端新 game view 只调用新 flow。旧 GameRoomPage 如果还存在，继续旧链路。

## 验收标准

- FIRST_NIGHT pending task 未完成时不能进 DAY。
- DAY mic 未关闭时不能进 NOMINATION。
- NOMINATION 有 open nomination 时不能进 NIGHT。
- execution 未 resolved 时不能进 NIGHT。
- phase change 写入 `clocktower_game_event`。
- phase change 后可以 schedule Agent task。

## 测试建议

- `advanceFirstNight_pendingTasks_rejected`
- `advanceDay_micOpen_rejected`
- `advanceDay_micClosed_entersNomination`
- `advanceNomination_openNomination_rejected`
- `advanceNomination_executionResolved_entersNight`
- `victory_goodWinWhenAllDemonsDead`
- `victory_evilWinWhenTwoAliveAndDemonAlive`

## 不在本任务做

- Trouble Brewing 完整夜晚结算。
- Agent 决策。
- 前端 flow UI。
