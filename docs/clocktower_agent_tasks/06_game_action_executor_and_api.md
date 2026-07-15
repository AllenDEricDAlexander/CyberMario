# 任务 06：新游戏动作执行器 —— `gameId + gameSeatId` 替代旧 room action

## 目标

新增基于新 game 模型的动作执行器，让真人和 Agent 共用规则层，但走不同入口：

```text
真人：Controller -> HumanGameActionService -> principal 权限校验 -> GameActionExecutor
Agent：AgentRuntime -> AgentGameActionService -> agent instance 校验 -> GameActionExecutor
```

核心是把动作从旧 `/api/clocktower/rooms/{roomId}/actions` 迁到新：

```text
/api/clocktower/games/{gameId}/actions
```

## 依赖

- 任务 04：`ClocktowerGameSeatPo` 有 actor 字段。
- 任务 05：公聊麦序服务，`PUBLIC_SPEECH` 需要校验持麦。

## 当前代码现状

旧 `ClocktowerActionServiceImpl`：

```text
- 入参是 roomId + roomSeatId
- 读取旧 ClocktowerRoomPo / ClocktowerSeatPo
- 校验 actor.userId == principal.userId
- 支持 PUBLIC_SPEECH / PRIVATE_MESSAGE / NOMINATE / VOTE / PASS
- 写旧 nomination / vote / event
```

这套链路不能给 Agent 用，也不应该继续用于新 `clocktower_game_*` 局。

## 新包结构

```text
clocktower/game/action
  ├── dto
  │   ├── ClocktowerGameActionRequest.java
  │   ├── ClocktowerGameActionResponse.java
  │   ├── GameActionCommand.java
  │   ├── ActorContext.java
  │   └── AvailableGameAction.java
  ├── service
  │   ├── ClocktowerGameActionExecutor.java
  │   ├── ClocktowerHumanGameActionService.java
  │   ├── ClocktowerAgentGameActionService.java
  │   └── impl
  └── controller
      └── ClocktowerGameActionController.java
```

## DTO

### Request

```java
public record ClocktowerGameActionRequest(
        Long actorGameSeatId,
        String actionType,
        List<Long> targetGameSeatIds,
        Long nominationId,
        Boolean vote,
        String content,
        Map<String, Object> payload
) {}
```

### ActorContext

```java
public record ActorContext(
        String actorType,
        Long actorId,
        Long userId,
        Long agentInstanceId,
        boolean systemInternal
) {
    public static ActorContext human(Long actorId, Long userId) { ... }
    public static ActorContext agent(Long actorId, Long agentInstanceId) { ... }
    public static ActorContext storyteller(Long userId) { ... }
    public static ActorContext system() { ... }
}
```

### Command

```java
public record GameActionCommand(
        Long gameId,
        Long actorGameSeatId,
        String actionType,
        List<Long> targetGameSeatIds,
        Long nominationId,
        Boolean vote,
        String content,
        Map<String, Object> payload
) {}
```

## 支持的动作 v1

```text
PUBLIC_SPEECH
NOMINATE
VOTE
PASS
FINISH_SPEECH
```

私聊先不放进 v1。公聊已经受任务 05 的麦序限制。

## 真人入口

```java
@PostMapping("/api/clocktower/games/{gameId}/actions")
public ClocktowerGameActionResponse submit(
        @PathVariable Long gameId,
        @RequestBody ClocktowerGameActionRequest request,
        RbacPrincipal principal
) {
    return humanGameActionService.submit(gameId, request, principal);
}
```

真人权限：

```text
1. principal 必须存在。
2. 用 gameId + principal.userId 找 ClocktowerGameSeatPo。
3. request.actorGameSeatId 必须等于自己的 gameSeatId。
4. 不能使用 Agent seat 提交。
```

## Agent 内部入口

```java
public interface ClocktowerAgentGameActionService {
    ClocktowerGameActionResponse submitAgentAction(
            Long gameId,
            Long agentInstanceId,
            ClocktowerGameActionRequest request
    );
}
```

Agent 权限：

```text
1. agent_instance 存在且 status=ACTIVE。
2. agent_instance.game_id == gameId。
3. agent_instance.game_seat_id == request.actorGameSeatId。
4. game_seat.actor_type = AGENT。
5. auto_mode != PAUSED。
```

## Executor 规则

所有入口最终调用：

```java
ClocktowerGameActionExecutor#execute(GameActionCommand command, ActorContext actor)
```

Executor 统一做：

```text
- game 存在且 RUNNING
- actor game seat 属于该 game
- seat.status = ACTIVE
- action type 合法
- phase 合法
- 具体动作合法
- append clocktower_game_event
- 调用 nomination/vote/mic/night task 对应服务做状态变更
- after commit schedule Agent tasks
```

## `PUBLIC_SPEECH`

规则：

```text
- game.phase = DAY 或 NOMINATION
- 必须是当前麦持有人
- content 非空
- content 长度限制，例如 500 或 1000 字
- 写 clocktower_game_event:
  event_type = PUBLIC_SPEECH
  visibility = PUBLIC
  actor_game_seat_id = actor
  payload.content = content
```

必须调用任务 05：

```java
publicMicService.requireCanSpeak(gameId, actorGameSeatId);
```

## `PASS`

用于：

```text
- 轮流麦时选择不发言
- 夜晚任务选择跳过，如果角色允许
- 抢麦阶段不做动作
```

v1 可以只支持“麦序 PASS”：

```text
actionType = PASS
payload.passType = MIC_TURN
```

## 事件可见性

```text
PUBLIC_SPEECH：PUBLIC
NOMINATE：PUBLIC
VOTE：PUBLIC
PASS：根据 passType，MIC_TURN 可 PUBLIC 或 SYSTEM_ONLY
AGENT_INTERNAL：SYSTEM_ONLY
```

## 与旧链路兼容

不要删除旧 `ClocktowerActionServiceImpl`。先保留：

```text
旧房间 / 旧页面：继续使用 /rooms/{roomId}/actions
新 game view：改用 /games/{gameId}/actions
```

后续任务 16 再做 cutover。

## 验收标准

- 真人玩家可以通过新 endpoint 提交 `PUBLIC_SPEECH`。
- 没有麦权时提交 `PUBLIC_SPEECH` 被拒绝。
- Agent 不能调用真人 endpoint。
- Agent 内部 service 可以用 agent instance 提交动作。
- 动作写入 `clocktower_game_event`，新 game view 能看到。
- 旧 `/rooms/{roomId}/actions` 不受影响。

## 测试建议

- `humanSubmitAction_requiresOwnGameSeat`
- `humanSubmitAction_cannotActAsAgent`
- `agentSubmitAction_requiresAgentInstanceGameSeatMatch`
- `publicSpeech_requiresMic`
- `publicSpeech_appendsGameEvent`

## 不在本任务做

- 提名和投票的完整新表逻辑；任务 07 做。
- 夜晚动作；任务 09 做。
- Agent 自动决策；任务 10 - 12 做。
