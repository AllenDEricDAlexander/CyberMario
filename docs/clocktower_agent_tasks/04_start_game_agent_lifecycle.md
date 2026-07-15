# 任务 04：开局生命周期 —— 允许合法 Agent，复制 Actor 字段，过滤 IM

## 目标

让任务 03 创建出来的 Agent seat 能通过开局校验，并在 `ClocktowerGameSeatPo` 中保留 Actor 信息。同时避免 Agent 被加入 IM
conversation。

## 依赖

- 任务 01 - 03。

## 当前代码现状

`ClocktowerGameLifecycleServiceImpl#startGame` 当前会：

```text
1. 加锁读取 room profile
2. 读取 room seats
3. validateStartSeats(room, seats, principal)
4. 创建 ClocktowerGamePo
5. 把 room seat 复制成 ClocktowerGameSeatPo
6. append GAME_STARTED event
7. activateGameConversations(savedGame.getId(), principal.userId(), seats.stream().map(userId).toList())
```

当前 `validateStartSeats` 的核心问题：

```text
- seat.userId == null 会失败
- realUser(metadata) 会拒绝 fake=true 或 agent=true
- playerType 不是 HUMAN 会失败
```

这对真人规则是对的，但会挡掉合法系统 Agent。

## 后端改动

### 1. 增加 seat 类型判断

建议新增方法：

```java
private boolean isAgentSeat(ClocktowerRoomSeatPo seat, Map<String, Object> metadata) {
    return "AGENT".equalsIgnoreCase(seat.getActorType())
            && seat.getAgentInstanceId() != null
            && seat.getActorId() != null
            && Boolean.TRUE.equals(metadata.get("systemManaged"))
            && Boolean.TRUE.equals(metadata.get("agentSeat"))
            && "agentSeatCount".equals(String.valueOf(metadata.get("createdBy")));
}
```

注意：不要只看 `metadata.agent=true`。

### 2. 重写开局 seat 校验

建议拆开：

```java
private void validatePlayerSeat(
        ClocktowerRoomProfilePo room,
        ClocktowerRoomSeatPo seat,
        RbacPrincipal principal
) {
    Map<String, Object> metadata = readMap(seat.getMetadataJson());

    if (Boolean.TRUE.equals(metadata.get("fake"))) {
        throw new BadRequestException("不允许使用手工假玩家开局");
    }

    if (isAgentSeat(seat, metadata)) {
        validateAgentStartSeat(seat, metadata);
        return;
    }

    validateHumanStartSeat(room, seat, metadata, principal);
}
```

HUMAN：

```java
private void validateHumanStartSeat(
        ClocktowerRoomProfilePo room,
        ClocktowerRoomSeatPo seat,
        Map<String, Object> metadata,
        RbacPrincipal principal
) {
    if (seat.getUserId() == null) fail(...);
    if (!"OCCUPIED".equals(seat.getStatus())) fail(...);
    if (room.getStorytellerUserId().equals(seat.getUserId())) fail(...);
    if (!Boolean.TRUE.equals(metadata.get("ready"))) fail(...);
    if (!StringUtils.hasText(seat.getRoleCode())) fail(...);

    String playerType = String.valueOf(metadata.getOrDefault("playerType", "HUMAN"));
    if (!"HUMAN".equalsIgnoreCase(playerType)) fail(...);
}
```

AGENT：

```java
private void validateAgentStartSeat(
        ClocktowerRoomSeatPo seat,
        Map<String, Object> metadata
) {
    if (seat.getUserId() != null) fail("Agent seat 不应该绑定真实 userId");
    if (!"OCCUPIED".equals(seat.getStatus())) fail(...);
    if (!Boolean.TRUE.equals(metadata.get("ready"))) fail(...);
    if (!StringUtils.hasText(seat.getRoleCode())) fail(...);
    if (seat.getActorId() == null || seat.getAgentInstanceId() == null) fail(...);
}
```

### 3. 复制 Actor 字段到 `ClocktowerGameSeatPo`

当前复制 seat 时补：

```java
gameSeat.setActorId(roomSeat.getActorId());
gameSeat.setActorType(roomSeat.getActorType());
gameSeat.setAgentInstanceId(roomSeat.getAgentInstanceId());
```

并继续保留：

```java
gameSeat.setUserId(roomSeat.getUserId());
```

Agent 的 `userId` 应为 null。

### 4. 更新 Agent Instance

开局创建 game seat 后，更新 agent instance：

```text
agent_instance.game_id = savedGame.id
agent_instance.game_seat_id = savedGameSeat.id
status = ACTIVE
```

建议在复制 game seat 后批量处理。

### 5. 过滤 IM participants

当前 `activateGameConversations` 不能继续传所有 seat userId。改成只传真人：

```java
List<Long> humanUserIds = seats.stream()
        .filter(seat -> !"AGENT".equalsIgnoreCase(seat.getActorType()))
        .map(ClocktowerRoomSeatPo::getUserId)
        .filter(Objects::nonNull)
        .distinct()
        .toList();

activateGameConversations(savedGame.getId(), principal.userId(), humanUserIds);
```

原因：`im_conversation_member.user_id` 是真实用户 ID，Agent 没有登录身份，不应进入 IM。

### 6. 开局事件 payload

`GAME_STARTED` event payload 建议包含：

```json
{
  "humanSeatCount": 1,
  "agentSeatCount": 4,
  "agentMode": "HEURISTIC_V0"
}
```

后续 Agent Task Scheduler 会监听这个事件。

## 验收标准

- `1 真人玩家 + 4 Agent` 可以开局。
- 手工 `metadata.agent=true` 但没有 `actor_type=AGENT` / `agent_instance_id` 的座位仍然不能开局。
- `fake=true` 继续被拒绝。
- ST 自己占真人玩家座位仍然被拒绝。
- `clocktower_game_seat` 中 Agent seat 有：

```text
actor_type = AGENT
actor_id != null
agent_instance_id != null
user_id = null
```

- IM conversation 只包含真人用户和 ST，不包含 Agent。

## 测试建议

- `startGame_withAgentSeats_success`
- `startGame_manualAgentMetadata_rejected`
- `startGame_agentSeatWithUserId_rejected`
- `startGame_filtersAgentFromImMembers`
- `startGame_copiesActorFieldsToGameSeat`

## 不在本任务做

- Agent 自动行动。
- 新动作执行器。
- 公聊麦序。
