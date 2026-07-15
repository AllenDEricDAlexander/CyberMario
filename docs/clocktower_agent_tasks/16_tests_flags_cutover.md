# 任务 16：测试、Feature Flag、灰度与旧链路收口

## 目标

把前面 15 个任务变成可安全合并、可回滚、可灰度的工程改造。当前仓库存在新旧模型并行，不能一刀切删除旧代码。

## 依赖

- 任务 01 - 15。

## 当前代码现状

```text
新模型：room_profile / room_seat / game / game_seat / game_event / game_view
旧模型：ClocktowerRoomPo / ClocktowerSeatPo / ClocktowerActionServiceImpl / ClocktowerFlowServiceImpl
前端：新 game view 能展示，但玩家动作控制当前关闭；旧 GameRoomSurface 仍接旧 action endpoint
```

## Feature Flags

建议新增配置：

```yaml
clocktower:
  agent-player:
    enabled: true
  game-actions:
    enabled: true
  public-mic:
    enabled: true
  new-flow:
    enabled: true
  llm-agent:
    enabled: false
```

行为：

```text
agent-player.enabled=false：忽略 agentSeatCount 或创建房间时报错提示未启用。
game-actions.enabled=false：新 /games/{gameId}/actions 拒绝。
public-mic.enabled=false：DAY 不创建 mic session，PUBLIC_SPEECH 可按旧规则临时开放。
new-flow.enabled=false：新 game flow endpoint 不开放。
llm-agent.enabled=false：只用 heuristic。
```

## 测试分层

### 1. Migration tests

覆盖：

```text
- actor / agent 表创建
- seat actor 字段存在
- mic 表创建
- nomination/vote/night task/agent task/memory/decision 表创建
```

### 2. Lobby tests

```text
- agentSeatCount=0 行为不变
- agentSeatCount=N 创建 N 个 Agent
- Agent userId=null
- agentSeatCount >= playerCount rejected
```

### 3. Start game tests

```text
- HUMAN + AGENT 混合开局成功
- fake/manual agent rejected
- Agent 不进 IM members
- actor fields copy 到 game seat
```

### 4. Public mic tests

```text
- DAY 创建 round robin
- seat order 正确
- 同时一个 active turn
- 非持麦者不能发言
- 轮流麦完成进入 5 分钟抢麦
- 抢麦超时关闭
```

### 5. Action executor tests

```text
- 真人只能操作自己的 game seat
- Agent 只能通过 internal service
- PUBLIC_SPEECH requires mic
- NOMINATE/VOTE writes game tables/events
```

### 6. Flow tests

```text
- pending night task blocks DAY
- open mic blocks nomination
- open nomination blocks night
- unresolved execution blocks night
- victory conditions end game
```

### 7. Agent runtime tests

```text
- trigger task idempotent
- paused Agent 不行动
- mic turn -> Agent speech/pass
- nomination -> Agent vote
- night task -> Agent choice
```

### 8. Frontend tests

```text
- Agent seat counts as occupied
- start button logic includes Agent
- mic banner renders current holder/countdown
- public speech input disabled without mic
- new game action endpoint used in game view
```

## 手动验收剧本

### 剧本 1：最小 Agent 开局

```text
1. 创建 5 人 Trouble Brewing 房间，agentSeatCount=4。
2. 真人加入 Seat 1 并 ready。
3. ST 分配所有角色。
4. 开始游戏。
5. 确认 4 个 Agent 出现在 game seat，不在 IM member。
```

### 剧本 2：公聊麦序

```text
1. ST 推进到 DAY。
2. 观察轮流麦从 Seat 1 到 Seat 5。
3. 非当前麦玩家尝试发言，应失败或 UI 禁用。
4. 轮流麦结束后进入 5 分钟抢麦。
5. 两个玩家同时抢麦，只允许一个成功。
```

### 剧本 3：Agent 完整参与

```text
1. Agent 轮到麦时自动发言/pass。
2. 进入 nomination 后，Agent 能提名或投票。
3. 夜晚 Agent 自动提交合法选择。
4. ST 能暂停 Agent，暂停后不再自动行动。
```

## Cutover 计划

### 第一阶段：双轨

```text
旧 room action / flow 保留。
新 game action / flow 只给新 game view 用。
feature flag 默认开发环境开启，生产关闭或按房间开启。
```

### 第二阶段：新局默认新链路

```text
通过 RoomProfile metadata 标记 runtimeModel = GAME_V2。
GAME_V2 房间只走新 action/flow/mic/agent。
旧房间继续旧链路。
```

### 第三阶段：旧链路冻结

```text
ClocktowerActionServiceImpl 标注 legacy。
ClocktowerFlowServiceImpl 标注 legacy。
新增功能只加到 GAME_V2。
```

### 第四阶段：旧链路下线

条件：

```text
- 没有活跃旧房间
- 前端不再调用 /rooms/{roomId}/actions
- 迁移脚本或归档策略完成
```

## 回滚策略

```text
- 关闭 agent-player.enabled，禁止新建 Agent 房。
- 关闭 game-actions.enabled，隐藏新游戏动作提交。
- 关闭 public-mic.enabled，ST 可手动推进。
- 保留旧 action/flow endpoint，旧局不受影响。
```

数据库字段不建议回滚删除，只做代码层禁用。

## 验收标准

- 所有核心单测和集成测试通过。
- `agent-player.enabled=false` 时系统行为接近原始版本。
- 新旧 endpoint 不互相污染数据。
- GAME_V2 房间不会写旧 nomination/vote 表。
- 旧房间不会读取新 game nomination/vote 表。
- 前端新游戏页不再提交旧 `/rooms/{roomId}/actions`。

## 不在本任务做

- 新功能开发。
- 角色策略增强。
- 私聊。
