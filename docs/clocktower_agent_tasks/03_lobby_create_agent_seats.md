# 任务 03：大厅创建房间 —— 真正消费 `agentSeatCount`

## 目标

让前端已有的 `agentSeatCount` 字段真正生效。创建房间时，如果用户选择 N 个 Agent 座位，后端自动把末尾 N 个座位创建为系统
Agent 玩家，并标记 ready。

## 依赖

- 任务 01：seat 表有 `actor_type` / `actor_id` / `agent_instance_id`。
- 任务 02：有 `ClocktowerAgentSeatService`。

## 当前代码现状

`ClocktowerRoomLobbyServiceImpl#createRoom` 当前大致流程：

```text
1. 校验 script / playerCount / roleCodes
2. 创建 ClocktowerRoomProfilePo
3. for seatNo in 1..playerCount:
      seatRepository.save(openSeat(room.getId(), seatNo, roleCode))
4. 创建 public conversation
```

`openSeat(...)` 当前会设置：

```text
displayName = "Seat " + seatNo
status = OPEN
metadataJson = {"ready": false}
```

问题：`ClocktowerRoomCreateRequest.agentSeatCount()` 当前没有参与创建 seat。

## 后端改动

### 1. 校验 `agentSeatCount`

新增工具方法：

```java
private int requireAgentSeatCount(Integer requested, int playerCount) {
    int count = requested == null ? 0 : requested;
    if (count < 0) {
        throw new IllegalArgumentException("Agent 座位数不能小于 0");
    }
    if (count >= playerCount) {
        throw new IllegalArgumentException("至少需要保留 1 个真人玩家座位");
    }
    return count;
}
```

是否允许 `agentSeatCount = playerCount - 1`：允许。这样可以 `1 真人玩家 + N Agent` 测试局。

### 2. 创建末尾 N 个 Agent seat

推荐 seat 规则：

```text
总座位：1..playerCount
Agent 座位：playerCount - agentSeatCount + 1 到 playerCount
真人开放座位：前面的座位
```

示例：`playerCount=5, agentSeatCount=4`

```text
Seat 1: OPEN, HUMAN
Seat 2: OCCUPIED, AGENT
Seat 3: OCCUPIED, AGENT
Seat 4: OCCUPIED, AGENT
Seat 5: OCCUPIED, AGENT
```

伪代码：

```java
int agentSeatCount = requireAgentSeatCount(request.agentSeatCount(), playerCount);
int firstAgentSeatNo = playerCount - agentSeatCount + 1;

for (int seatNo = 1; seatNo <= playerCount; seatNo++) {
    String roleCode = seatNo <= roleCodes.size() ? roleCodes.get(seatNo - 1) : null;

    ClocktowerRoomSeatPo seat = openSeat(room.getId(), seatNo, roleCode);
    if (seatNo >= firstAgentSeatNo) {
        seat.setDisplayName("Agent " + (seatNo - firstAgentSeatNo + 1));
        seat.setStatus("OCCUPIED");
        seat.setActorType("AGENT");
        seat.setUserId(null);
        seat.setMetadataJson(writeJson(agentSeatMetadata()));
        seat = seatRepository.save(seat);

        ClocktowerAgentSeatDescriptor agent = agentSeatService.createAgentForRoomSeat(
                room.getId(),
                seat.getId(),
                seatNo,
                seat.getDisplayName(),
                roleCode,
                defaultProfileName(seatNo),
                "FULL_AUTO"
        );

        seat.setActorId(agent.actorId());
        seat.setAgentInstanceId(agent.agentInstanceId());
        seatRepository.save(seat);
    } else {
        seatRepository.save(seat);
    }
}
```

### 3. Metadata

```java
private Map<String, Object> agentSeatMetadata() {
    return Map.of(
        "ready", true,
        "actorType", "AGENT",
        "agent", true,
        "agentSeat", true,
        "systemManaged", true,
        "agentPolicy", "HEURISTIC_V0",
        "autoMode", "FULL_AUTO",
        "createdBy", "agentSeatCount"
    );
}
```

### 4. Response DTO

确认 `ClocktowerRoomSeatView` 或对应前端响应中包含：

```text
actorType
actorId
agentInstanceId
isAgent
ready
```

如果现在 DTO 只返回 `userId`、`displayName`、`status`、`roleCode`，需要补字段，否则前端无法区分“无人空座”和“Agent 占用座”。

## 前端最小改动

`RoomLobbyPage.tsx` 当前 `canStartClocktowerRoom` 大概率只检查：

```text
Boolean(seat.userId)
roleCode
ready
```

改成：

```ts
const isAgentSeat = seat.actorType === 'AGENT' || seat.isAgent;
const hasPlayer = Boolean(seat.userId) || isAgentSeat;
```

但这个前端改动可以放到任务 13；本任务只保证后端数据正确。

## 验收标准

- 创建 5 人房，`agentSeatCount=4` 后，数据库中有 1 个 OPEN HUMAN seat + 4 个 OCCUPIED AGENT seat。
- Agent seat：

```text
user_id = null
actor_type = AGENT
actor_id != null
agent_instance_id != null
metadata.ready = true
status = OCCUPIED
```

- 不创建 IM conversation member 给 Agent。
- `agentSeatCount=0` 行为和现在一致。
- `agentSeatCount >= playerCount` 被拒绝。

## 测试建议

- `createRoom_agentSeatCountZero_keepsAllSeatsOpen`
- `createRoom_agentSeatCountFour_createsFourAgentSeats`
- `createRoom_agentSeatCountTooLarge_rejected`
- `createRoom_agentSeatsHaveNoUserId`

## 不在本任务做

- Agent 开局校验。
- Agent 自动行动。
- 公聊麦序。
