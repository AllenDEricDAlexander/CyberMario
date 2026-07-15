# 任务 02：后端领域层 —— Actor / Agent PO、Repository、DTO、基础服务

## 目标

把任务 01 的数据库结构接到 Java 代码中，提供创建 Agent Actor、Agent Instance、查询 Agent seat
的基础服务。这个任务完成后，后续大厅创建和开局生命周期可以直接调用服务，不在业务类里拼 JSON 和散落创建逻辑。

## 依赖

- 任务 01：`clocktower_actor`、`clocktower_agent_profile`、`clocktower_agent_instance` 表和 seat 字段。

## 当前代码现状

## 当前仓库断点

- `fe/src/modules/clocktower/RoomListPage.tsx` 已经提交 `agentSeatCount`，但后端创建房间还没有消费这个字段。
- `ClocktowerRoomLobbyServiceImpl#createRoom` 当前只按 `playerCount` 循环创建 `openSeat(...)`，`openSeat` 的 metadata 只有
  `ready=false`。
- `ClocktowerGameLifecycleServiceImpl#validateStartSeats` 当前要求座位有 `userId`、`OCCUPIED`、`ready=true`，并且
  `realUser(metadata)` 会拒绝 `fake=true` 或 `agent=true`。
- 新游戏视图已经在读 `clocktower_game_*`：`ClocktowerGameViewServiceImpl` / `ClocktowerViewerResolver`。
- 旧玩家动作和旧流程仍然读写旧模型：`ClocktowerActionServiceImpl`、`ClocktowerFlowServiceImpl` 使用旧 `ClocktowerRoomPo` /
  `ClocktowerSeatPo` / `ClocktowerNominationPo` / `ClocktowerVotePo`。
- `ClocktowerRoomPlayPage.tsx` 对新 `gameView` 的玩家动作目前是 `actionControlsEnabled={false}`；旧 `GameRoomSurface` 仍会向
  `/api/clocktower/rooms/{roomId}/actions` 提交动作。
- IM 表 `im_conversation_member.user_id` 是非空用户 ID；Agent 不应该被塞进 IM 成员表。

当前 `top.egon.mario.clocktower` 下面已有 `room`、`game`、`view`、`action`、`flow` 等包。建议新增 `agent` 包，不要把 Agent 类塞进
`room` 或 `game` 里。

## 新增包结构

```text
be/src/main/java/top/egon/mario/clocktower/agent
  ├── constant
  │   ├── ClocktowerActorType.java
  │   ├── ClocktowerAgentStatus.java
  │   └── ClocktowerAgentAutoMode.java
  ├── po
  │   ├── ClocktowerActorPo.java
  │   ├── ClocktowerAgentProfilePo.java
  │   └── ClocktowerAgentInstancePo.java
  ├── repository
  │   ├── ClocktowerActorRepository.java
  │   ├── ClocktowerAgentProfileRepository.java
  │   └── ClocktowerAgentInstanceRepository.java
  ├── dto
  │   ├── ClocktowerAgentSeatDescriptor.java
  │   └── ClocktowerAgentInstanceView.java
  └── service
      ├── ClocktowerAgentSeatService.java
      └── impl
          └── ClocktowerAgentSeatServiceImpl.java
```

## 枚举建议

### `ClocktowerActorType`

```java
public final class ClocktowerActorType {
    public static final String HUMAN = "HUMAN";
    public static final String AGENT = "AGENT";
    public static final String STORYTELLER = "STORYTELLER";
    public static final String SYSTEM = "SYSTEM";

    private ClocktowerActorType() {}
}
```

项目如果已经习惯用 `String` 常量，就先保持一致，避免全模块枚举迁移过大。

### `ClocktowerAgentAutoMode`

```java
public final class ClocktowerAgentAutoMode {
    public static final String FULL_AUTO = "FULL_AUTO";
    public static final String ST_APPROVAL = "ST_APPROVAL";
    public static final String PAUSED = "PAUSED";

    private ClocktowerAgentAutoMode() {}
}
```

## PO 字段

PO 命名和现有模块风格保持一致，字段至少包括：

```text
id
actorType / profileId / actorId 等核心字段
status
autoMode
metadataJson
createdAt
updatedAt
deleted
```

`ClocktowerRoomSeatPo` 和 `ClocktowerGameSeatPo` 要补：

```java
private Long actorId;
private String actorType;
private Long agentInstanceId;
```

## Repository 方法

### `ClocktowerAgentProfileRepository`

```java
Optional<ClocktowerAgentProfilePo> findFirstByNameAndDeletedFalse(String name);
List<ClocktowerAgentProfilePo> findByDeletedFalseOrderByIdAsc();
```

### `ClocktowerAgentInstanceRepository`

```java
List<ClocktowerAgentInstancePo> findByRoomIdAndDeletedFalseOrderByIdAsc(Long roomId);
List<ClocktowerAgentInstancePo> findByGameIdAndDeletedFalseOrderByIdAsc(Long gameId);
Optional<ClocktowerAgentInstancePo> findByIdAndDeletedFalse(Long id);
Optional<ClocktowerAgentInstancePo> findByGameSeatIdAndDeletedFalse(Long gameSeatId);
Optional<ClocktowerAgentInstancePo> findByActorIdAndDeletedFalse(Long actorId);
```

### `ClocktowerActorRepository`

```java
Optional<ClocktowerActorPo> findByIdAndDeletedFalse(Long id);
```

## `ClocktowerAgentSeatService`

职责：只负责 Agent seat 的创建和查询，不做开局、不做动作。

```java
public interface ClocktowerAgentSeatService {
    ClocktowerAgentSeatDescriptor createAgentForRoomSeat(
            Long roomId,
            Long roomSeatId,
            int seatNo,
            String displayName,
            String roleCode,
            String profileName,
            String autoMode
    );

    boolean isSystemAgentSeat(String actorType, Long agentInstanceId, Map<String, Object> metadata);

    List<ClocktowerAgentInstancePo> agentsOfRoom(Long roomId);

    List<ClocktowerAgentInstancePo> agentsOfGame(Long gameId);
}
```

## Metadata 约定

Agent room seat metadata：

```json
{
  "ready": true,
  "actorType": "AGENT",
  "agent": true,
  "agentSeat": true,
  "systemManaged": true,
  "agentPolicy": "HEURISTIC_V0",
  "autoMode": "FULL_AUTO",
  "createdBy": "agentSeatCount"
}
```

HUMAN seat metadata 继续保持：

```json
{
  "ready": false
}
```

注意：后续校验不要只看 `metadata.agent=true`，必须同时要求：

```text
actor_type = AGENT
agent_instance_id != null
systemManaged = true
createdBy = agentSeatCount
```

## 验收标准

- 可以通过 service 创建一个 Agent Actor + Agent Instance，并回填 room seat 的 actor 字段。
- 旧 HUMAN seat 不受影响。
- Agent 创建逻辑不需要真实 `userId`。
- `ClocktowerRoomSeatPo` / `ClocktowerGameSeatPo` 能读写新字段。

## 测试建议

- Repository 层 smoke test。
- `ClocktowerAgentSeatServiceImpl#createAgentForRoomSeat` 单元测试。
- metadata JSON 读写测试。

## 不在本任务做

- 不改 `ClocktowerRoomLobbyServiceImpl#createRoom`。
- 不改 `ClocktowerGameLifecycleServiceImpl#startGame`。
- 不实现 Agent 自动行为。
