# 任务 10：Agent 异步任务队列与 Runtime

## 目标

实现 Agent 自动行为的调度骨架。Agent 不在 HTTP 请求事务里同步思考，不阻塞真人操作。所有 Agent 行为通过任务队列触发，并最终调用任务
06 的 `ClocktowerAgentGameActionService`。

## 依赖

- 任务 04：Agent instance 与 game seat 绑定。
- 任务 05：公聊麦序会产生 `MIC_TURN_STARTED`。
- 任务 06：Agent 内部动作入口。
- 任务 09：夜晚任务。

## 数据库设计

新增迁移：`Vxx__clocktower_agent_task.sql`。

```sql
create table clocktower_agent_task (
    id bigserial primary key,
    game_id bigint not null,
    agent_instance_id bigint not null,
    game_seat_id bigint not null,
    trigger_type varchar(64) not null,
    trigger_key varchar(160) not null,
    status varchar(32) not null default 'PENDING',
    priority int not null default 100,
    available_at timestamptz not null default now(),
    locked_at timestamptz null,
    locked_by varchar(128) null,
    attempts int not null default 0,
    last_error text null,
    metadata_json jsonb not null default '{}',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted boolean not null default false
);

create unique index uk_clocktower_agent_task_trigger
    on clocktower_agent_task(game_id, agent_instance_id, trigger_type, trigger_key)
    where deleted = false;

create index idx_clocktower_agent_task_pending
    on clocktower_agent_task(status, available_at, priority)
    where deleted = false;
```

`status`：

```text
PENDING
RUNNING
DONE
FAILED
CANCELLED
```

## Trigger 类型

```text
GAME_STARTED
PHASE_CHANGED
MIC_TURN_STARTED
MIC_GRAB_OPENED
NOMINATION_OPENED
VOTE_WINDOW_OPENED
NIGHT_TASK_OPENED
PUBLIC_EVENT_APPENDED
PRIVATE_INFO_RECEIVED
PLAYER_DIED
TIMER_TICK
```

幂等 `trigger_key` 示例：

```text
game:123:started
phase:123:DAY:1
micTurn:456
nomination:789:vote
nightTask:111
publicEvent:222:react
```

## 包结构

```text
clocktower/agent/runtime
  ├── ClocktowerAgentTaskScheduler.java
  ├── ClocktowerAgentRuntime.java
  ├── ClocktowerAgentTaskWorker.java
  ├── ClocktowerAgentTriggerListener.java
  └── impl
```

也可以放在 `clocktower/agent/service` 下，保持包简洁。

## 调度原则

### 1. 事件提交后再调度

使用：

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
```

避免这种坑：

```text
事务未提交 -> Agent 读取不到新事件 -> 做出旧视角决策
```

### 2. 不在 HTTP 事务里跑 Agent

禁止：

```text
Controller 请求里创建事件，然后立即调用 LLM / Agent 决策并等待结果
```

正确：

```text
写事件 / 改状态
  -> after commit 创建 agent_task
  -> worker 异步处理
```

### 3. 每个 Agent 每个触发只创建一个任务

用 unique index 保证幂等。

## Worker 逻辑

```java
@Scheduled(fixedDelayString = "${clocktower.agent.worker.fixed-delay-ms:1000}")
public void poll() {
    List<ClocktowerAgentTaskPo> tasks = repository.lockNextBatch(workerId, batchSize);
    for (ClocktowerAgentTaskPo task : tasks) {
        runtime.handle(task);
    }
}
```

`lockNextBatch` 可以先用简单事务：

```text
select pending tasks order by priority, available_at limit N
update status=RUNNING, locked_by=?, locked_at=now()
```

如果数据库支持 `for update skip locked`，优先使用。

## Runtime 处理流程

```text
1. 读取 agent_instance。
2. 如果 auto_mode = PAUSED，任务标记 CANCELLED 或 DONE_SKIPPED。
3. 构造 AgentPrivateView。
4. 更新 memory。
5. 枚举 legal intents。
6. 调用 AgentPolicy 决策。
7. 调用 AgentGameActionService 执行动作。
8. 写 decision log。
9. task DONE。
```

任务 10 可以先 mock 第 3 - 6 步：

```text
MIC_TURN_STARTED -> Agent 说一句固定话或 PASS
NOMINATION_OPENED -> Agent 默认投 false
NIGHT_TASK_OPENED -> Agent 默认合法随机选择
```

真正感知/记忆在任务 11，策略在任务 12。

## 触发点接入

### 开局

任务 04 的 `startGame` append `GAME_STARTED` 后：

```text
为所有 Agent 创建 GAME_STARTED task
```

### 阶段变化

任务 08 的 flow phase changed 后：

```text
为所有 active Agent 创建 PHASE_CHANGED task
```

### 麦序

任务 05 激活一个 turn 时：

```text
如果 current_holder 是 Agent，创建 MIC_TURN_STARTED task
```

### 提名/投票

任务 07 打开 nomination 时：

```text
为所有 eligible Agent 创建 VOTE_WINDOW_OPENED task
```

### 夜晚

任务 09 创建 Agent 的 night task 时：

```text
为该 Agent 创建 NIGHT_TASK_OPENED task
```

## 配置

```yaml
clocktower:
  agent:
    worker:
      enabled: true
      fixed-delay-ms: 1000
      batch-size: 10
      max-attempts: 3
    default-response-delay-ms: 800
```

可以给 Agent 加一点延迟，避免所有 Agent 同时秒回，看起来像机器人合唱团。

## 验收标准

- 开局后会给所有 Agent 创建 `GAME_STARTED` task。
- Agent auto_mode=PAUSED 时不会执行动作。
- 麦序轮到 Agent 时会创建 `MIC_TURN_STARTED` task。
- Worker 能领取任务、处理、标记 DONE。
- 重复事件不会重复创建同一 trigger task。
- Runtime 执行动作失败会记录 `last_error` 和 attempts。

## 测试建议

- `scheduleTask_idempotent`
- `workerLocksPendingTasks`
- `pausedAgent_skipsTask`
- `micTurnStarted_schedulesOnlyCurrentAgent`
- `nightTaskOpened_schedulesOwningAgent`
- `failedTask_retriesUntilMaxAttempts`

## 不在本任务做

- 完整 Agent 记忆。
- 完整 Heuristic 策略。
- LLM。
