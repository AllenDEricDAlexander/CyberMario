# 任务 14：说书人控制台 —— Agent、麦序、夜晚任务兜底

## 目标

给 ST 一个可靠控制台，用于管理 Agent 自动行为、公聊麦序、夜晚任务和异常情况。真正 Agent Player 一定要可暂停、可审计、可兜底，否则调试会很痛苦。

## 依赖

- 任务 05：Mic service。
- 任务 09：Night task service。
- 任务 10：Agent task queue。
- 任务 11：Agent memory。

## 当前代码现状

`ClocktowerGameViewServiceImpl` 已经按 `viewerMode=STORYTELLER` 返回 grimoire 和更多信息；`ClocktowerRoomPlayPage.tsx` 对
ST 使用 `StorytellerGameSurface`。本任务主要扩展 ST surface。

## 后端 API

### Agent 控制

```text
GET  /api/clocktower/games/{gameId}/agents
POST /api/clocktower/games/{gameId}/agents/{agentInstanceId}/pause
POST /api/clocktower/games/{gameId}/agents/{agentInstanceId}/resume
POST /api/clocktower/games/{gameId}/agents/{agentInstanceId}/run-now
GET  /api/clocktower/games/{gameId}/agents/{agentInstanceId}/memory
GET  /api/clocktower/games/{gameId}/agents/{agentInstanceId}/tasks
```

权限：ST only。

### 麦序控制

任务 05 已定义：

```text
skip current turn
close session
extend grab mic
start day session
```

这里把它们接到 ST UI。

### 夜晚任务控制

```text
GET  /api/clocktower/games/{gameId}/night/tasks
POST /api/clocktower/games/{gameId}/night/tasks/{taskId}/resolve
POST /api/clocktower/games/{gameId}/night/tasks/{taskId}/skip
POST /api/clocktower/games/{gameId}/night/tasks/{taskId}/random-choice
```

用途：

```text
- Agent 给出选择后，ST 可确认/覆盖
- Agent 卡住时，ST 可跳过/随机
- 真人未操作时，ST 可兜底
```

## 前端组件

在 `StorytellerGameSurface` 下新增：

```text
StorytellerAgentPanel.tsx
StorytellerMicControlPanel.tsx
StorytellerNightTaskPanel.tsx
```

## Agent Panel

展示：

```text
Agent 名称
seatNo
角色 / 阵营，ST 可见
autoMode：FULL_AUTO / ST_APPROVAL / PAUSED
最近任务状态
最近一次发言/投票/夜晚选择
错误信息
按钮：暂停、恢复、立即运行、查看记忆
```

## Mic Control Panel

展示：

```text
当前 mic session 状态
当前麦持有人
轮流麦队列
抢麦剩余时间
按钮：跳过当前、关闭公聊、延长 2 分钟、重新打开抢麦
```

ST override 必须写 event：

```text
MIC_TURN_SKIPPED_BY_ST
MIC_SESSION_EXTENDED_BY_ST
MIC_SESSION_CLOSED_BY_ST
```

## Night Task Panel

展示：

```text
nightNo
sortOrder
seatNo / displayName / roleCode
taskType
status
choice
result
按钮：确认、跳过、随机、手动选目标
```

Agent 的夜晚选择应该显示为：

```text
Agent 建议：杀 3 号
理由：3 号被多个好人信任，疑似信息位
```

ST 可以：

```text
- 接受建议
- 覆盖目标
- 跳过
```

## 验收标准

- ST 可以暂停一个 Agent，暂停后 Agent task 不再执行动作。
- ST 可以恢复 Agent。
- ST 可以手动运行某个 Agent 当前任务。
- ST 可以查看 Agent memory 摘要。
- ST 可以跳过当前麦。
- ST 可以延长抢麦时间。
- ST 可以处理 Agent/真人夜晚任务。
- 所有 ST override 都写 game event，方便复盘。

## 测试建议

- `pauseAgent_setsAutoModePaused`
- `pausedAgentTask_doesNotAct`
- `resumeAgent_allowsTaskExecution`
- `stSkipMicTurn_appendsEvent`
- `stResolveNightTask_marksDone`
- `stOverrideAgentNightChoice_recordsMetadata`

## 不在本任务做

- LLM。
- 更复杂的可视化复盘。
- 私聊控制。
