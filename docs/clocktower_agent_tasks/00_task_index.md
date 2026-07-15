# Clocktower 真正 Agent Player 拆分任务索引

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

## 总体原则

这次不要把 Agent 做成“假 userId 占座”。目标是把 Agent 做成游戏内一等 Actor：

```text
HUMAN 玩家：actor_type = HUMAN, user_id != null
AGENT 玩家：actor_type = AGENT, user_id = null, agent_instance_id != null
ST：仍然由真实 user 承担，但在游戏权限里是 STORYTELLER viewer
SYSTEM：系统事件、超时、自动跳过等内部 Actor
```

核心迁移方向：

```text
房间创建 room_profile / room_seat
  -> 创建 Agent Actor / Agent Instance / Agent Seat
  -> 开局复制到 game / game_seat
  -> 新动作接口写 clocktower_game_event + 新 game nomination/vote/night task
  -> Agent Runtime 通过 task queue 触发
  -> 前端游戏页接新 game action 和公聊麦序
```

## 推荐合并顺序

| 顺序 | 文件                                        | 目标                                               |
|---:|-------------------------------------------|--------------------------------------------------|
|  1 | `01_schema_actor_agent_foundation.md`     | 加 Actor / Agent 基础表和 seat 字段                     |
|  2 | `02_backend_actor_agent_domain.md`        | PO、Repository、DTO、枚举、基础服务                        |
|  3 | `03_lobby_create_agent_seats.md`          | 创建房间真正消费 `agentSeatCount`                        |
|  4 | `04_start_game_agent_lifecycle.md`        | 开局允许合法 Agent，复制到 game seat，过滤 IM                 |
|  5 | `05_public_mic_round_robin_grab.md`       | 公聊麦序：轮流麦一轮 + 抢麦 5 分钟                             |
|  6 | `06_game_action_executor_and_api.md`      | 新游戏动作执行器，替代旧 room action 链路                      |
|  7 | `07_game_nomination_vote_execution.md`    | 新 nomination / vote / execution 表与服务             |
|  8 | `08_game_flow_service_cutover.md`         | 新 game flow，减少对旧 `ClocktowerFlowServiceImpl` 的依赖 |
|  9 | `09_night_task_engine_trouble_brewing.md` | 新夜晚任务引擎和 Trouble Brewing v0                      |
| 10 | `10_agent_task_queue_runtime.md`          | Agent 异步任务队列和 Runtime                            |
| 11 | `11_agent_private_view_memory.md`         | Agent 私有视角与记忆系统                                  |
| 12 | `12_heuristic_agent_player.md`            | 不依赖 LLM 的真正 Agent 决策策略                           |
| 13 | `13_frontend_lobby_game_mic_agent_ui.md`  | 前端大厅、游戏页、公聊麦序、Agent 标识                           |
| 14 | `14_storyteller_agent_control_console.md` | ST 控制台：Agent、麦序、夜晚任务兜底                           |
| 15 | `15_llm_policy_decision_audit.md`         | 可选 LLM 策略和决策审计                                   |
| 16 | `16_tests_flags_cutover.md`               | 测试、feature flag、灰度和旧链路收口                         |

## 里程碑拆法

### 里程碑 A：Agent 能合法开局，但还不自动玩

完成任务 01 - 04。验收目标：

```text
1 真人 ST + 1 真人玩家 + N Agent 可以创建房间并开局。
Agent 不进 IM conversation。
Agent seat 在 game seat 中保留 actor_type / agent_instance_id。
```

### 里程碑 B：公聊和真人动作先跑通

完成任务 05 - 08。验收目标：

```text
DAY 阶段先按座位顺序轮流麦一轮；之后进入 5 分钟抢麦。
新游戏动作接口可以处理公聊、提名、投票、处决。
前端不再依赖旧 /rooms/{roomId}/actions 进行新局动作提交。
```

### 里程碑 C：Agent 开始自动参与

完成任务 09 - 12。验收目标：

```text
Trouble Brewing 基础局中，Agent 能夜晚选目标、白天发言/提名/投票。
没有 LLM 时，HeuristicAgentPolicy 也能完整跑一局。
```

### 里程碑 D：体验、调试和长期扩展

完成任务 13 - 16。验收目标：

```text
前端可以看见 Agent 状态、麦序、倒计时和 ST 控制台。
LLM 策略可插拔，所有 Agent 决策可审计。
旧 action / flow 链路有明确保留范围或下线计划。
```
