# Clocktower Storyteller Agent Control Console Design

## Context

Task 14 gives the Storyteller a reliable control console for Agent behavior, public mic control, and night-task fallback. Tasks 05, 09, 10, 11, 12, and 13 already provide most runtime foundations:

- Public mic service exists under `/api/clocktower/games/{gameId}/mic`.
- Night task service exists under `/api/clocktower/games/{gameId}/night-tasks`.
- Agent task queue, runtime, memory, private view, and heuristic policy exist.
- `ClocktowerRoomPlayPage` routes Storyteller game views into `StorytellerGameSurface`.
- `StorytellerGameSurface` currently shows grimoire seats, public events, flow placeholders, rulings placeholders, and chat monitoring.

The missing piece is a Storyteller-facing operational surface that can pause Agents, inspect Agent state, override mic/night situations, and leave auditable traces for replay and debugging.

## Goals

- Let the Storyteller list Agent players in a game and see seat, role, alignment, auto mode, recent task state, recent action summary, and errors.
- Let the Storyteller pause, resume, and manually schedule immediate Agent execution.
- Let the Storyteller inspect Agent memory summaries and recent Agent tasks.
- Wire existing public mic operations into the Storyteller surface: start day mic, skip current turn, close session, extend grab mic by two minutes, and refresh session state.
- Add Storyteller-specific mic override event types required by task 14.
- Let the Storyteller inspect current night tasks, accept choices, override targets, skip tasks, and trigger fallback random/auto choices.
- Keep all Storyteller overrides auditable through game events and/or night task metadata.
- Add focused backend and frontend tests for the accepted behavior.

## Non-Goals

- Do not add LLM decision logic.
- Do not build complex visual replay.
- Do not add private chat control.
- Do not add a new database migration; existing Agent, task, memory, mic, night-task, and event tables are sufficient.
- Do not introduce new frontend libraries or backend architectural layers.
- Do not add compatibility aliases for `/api/clocktower/games/{gameId}/night/tasks`; the implementation will use the existing canonical `/night-tasks` path.

## Chosen Approach

Use方案 A: implement the full Storyteller control loop across backend API, frontend panels, and focused tests in this task.

This is the only approach that satisfies the task-14 acceptance criteria without fake UI controls. A frontend-only approach would fail pause/resume/memory/run-now acceptance because no Agent control API exists. A backend-only approach would leave the requested Storyteller console incomplete. The change is still bounded because it extends existing Agent, mic, night-task, and Storyteller surface seams instead of replacing the game page.

## Design Pattern Consideration

Use a small domain service/facade for Agent control: `ClocktowerAgentControlService`.

This fits because pause/resume/run-now/list/memory/tasks coordinate several existing repositories and the game event appender. Putting that orchestration in a controller would make permission checks and audit behavior harder to test. A heavier Strategy, State, or Command hierarchy is not needed because task 14 has a fixed set of Storyteller actions and existing runtime services already own Agent decision behavior.

For the frontend, use focused React components rather than a new page abstraction:

- `StorytellerAgentPanel`
- `StorytellerMicControlPanel`
- `StorytellerNightTaskPanel`

This matches the current `StorytellerGameSurface` tab layout and keeps each operational panel independently testable.

## Backend Agent Control

Add a Storyteller-only controller under the existing game-scoped API:

```text
GET  /api/clocktower/games/{gameId}/agents
POST /api/clocktower/games/{gameId}/agents/{agentInstanceId}/pause
POST /api/clocktower/games/{gameId}/agents/{agentInstanceId}/resume
POST /api/clocktower/games/{gameId}/agents/{agentInstanceId}/run-now
GET  /api/clocktower/games/{gameId}/agents/{agentInstanceId}/memory
GET  /api/clocktower/games/{gameId}/agents/{agentInstanceId}/tasks
```

All endpoints require the game room owner through the existing `ClocktowerRoomAccessPolicy.requireOwner(...)` pattern.

`GET /agents` returns a console-oriented Agent view, enriched from Agent instance, game seat, recent Agent task, and game seat role fields:

- `agentInstanceId`
- `actorId`
- `gameSeatId`
- `seatNo`
- `displayName`
- `profileName`
- `status`
- `autoMode`
- `roleCode`
- `alignment`
- `recentTaskStatus`
- `recentTaskTriggerType`
- `recentTaskResult`
- `recentError`

`pause` changes `autoMode` to `PAUSED`, keeps Agent `status` unchanged, and appends an `AGENT_PAUSED_BY_ST` game event. The current Agent runtime already skips paused Agents, so no runtime behavior fork is needed.

`resume` changes `autoMode` to `FULL_AUTO` and appends `AGENT_RESUMED_BY_ST`.

`run-now` creates an immediate pending Agent task for the selected instance with trigger type `ST_RUN_NOW`, trigger key unique to the request, and metadata containing the Storyteller actor id and reason. It appends `AGENT_RUN_NOW_REQUESTED_BY_ST`. The existing worker will claim and execute the task.

`memory` returns recent memory records for that Agent and game, ordered newest first or adapted from the repository order in the service layer. It does not expose unrelated private-view data.

`tasks` returns recent task records for that Agent and game, including status, trigger fields, attempt count, result JSON, error code, and timestamps.

## Backend Mic Control

Reuse the existing mic endpoints:

```text
GET  /api/clocktower/games/{gameId}/mic
POST /api/clocktower/games/{gameId}/mic/start-day
POST /api/clocktower/games/{gameId}/mic/turns/{turnId}/skip
POST /api/clocktower/games/{gameId}/mic/extend
POST /api/clocktower/games/{gameId}/mic/close
```

Task 14 requires Storyteller override event names. The explicit Storyteller operations should append:

- `MIC_TURN_SKIPPED_BY_ST`
- `MIC_SESSION_EXTENDED_BY_ST`
- `MIC_SESSION_CLOSED_BY_ST`

Existing lifecycle events such as normal turn finish, automatic expiry, round-robin turn start, grab open, and session start remain unchanged. If helper methods currently use a shared close path, the implementation should pass the event type into the helper rather than changing non-Storyteller close/expiry behavior.

`start-day` remains the entry point for opening or reopening a day mic session. It can keep the existing `MIC_SESSION_STARTED` event because starting the session is not listed as an override event in task 14.

## Backend Night Task Control

Use the existing canonical path:

```text
GET  /api/clocktower/games/{gameId}/night-tasks
POST /api/clocktower/games/{gameId}/night-tasks/{taskId}/resolve
POST /api/clocktower/games/{gameId}/night-tasks/{taskId}/skip
POST /api/clocktower/games/{gameId}/night-tasks/{taskId}/random-choice
```

`GET` and existing `skip`/`resolve` remain Storyteller-only.

Extend `ClocktowerNightResolveRequest` in a backward-compatible way:

- existing `result`
- existing `note`
- new optional `targetGameSeatIds`
- new optional `payload`

When `targetGameSeatIds` is present, the resolution service writes a Storyteller override choice before resolving:

```json
{
  "targetGameSeatIds": [123],
  "payload": {...},
  "source": "ST_OVERRIDE"
}
```

It validates targets through the existing role skill target validation path before applying the normal role resolution. This keeps effects such as kill, private info, and public events consistent with regular Agent/human choices.

`random-choice` is a Storyteller fallback for stuck Agent or human tasks. It chooses from legal targets when a task requires targets, records metadata such as `source=ST_RANDOM_CHOICE` and `requestedByStorytellerUserId`, and marks the task `CHOSEN`. Information-only tasks can use the role skill fallback choice when no target is required. The endpoint returns the updated `ClocktowerNightTaskView`; the Storyteller can then resolve it through the regular `resolve` endpoint.

Night task override and fallback should append auditable game events, for example:

- `NIGHT_CHOICE_OVERRIDDEN_BY_ST`
- `NIGHT_CHOICE_RANDOMIZED_BY_ST`
- existing `NIGHT_TASK_SKIPPED`
- existing `NIGHT_TASK_RESOLVED`

## Frontend Service Contract

Extend `fe/src/modules/clocktower/clocktowerTypes.ts` with:

- `ClocktowerAgentConsoleView`
- `ClocktowerAgentTaskView`
- `ClocktowerAgentMemoryView`
- `ClocktowerNightTaskView`
- `ClocktowerNightResolveRequest`
- `ClocktowerNightSkipRequest`

Extend `clocktowerService.ts` with typed functions:

- `getClocktowerGameAgents(gameId)`
- `pauseClocktowerAgent(gameId, agentInstanceId)`
- `resumeClocktowerAgent(gameId, agentInstanceId)`
- `runClocktowerAgentNow(gameId, agentInstanceId)`
- `getClocktowerAgentMemory(gameId, agentInstanceId)`
- `getClocktowerAgentTasks(gameId, agentInstanceId)`
- `getClocktowerNightTasks(gameId)`
- `resolveClocktowerNightTask(gameId, taskId, request)`
- `skipClocktowerNightTask(gameId, taskId, request)`
- `randomChoiceClocktowerNightTask(gameId, taskId)`

Reuse existing mic service functions from task 13.

## Frontend Storyteller Surface

Update `StorytellerGameSurface` to add operational tabs in the right-side panel while preserving current flow, rulings, and chat tabs.

Recommended tab order:

```text
Agent
麦序
夜晚任务
流程
裁定
聊天监控
```

The left side remains the grimoire and public event timeline.

Panel actions refresh their own data after success. The public event timeline can remain based on the loaded game view for this task; backend tests cover event persistence, and a future live-sync task can refresh the full game view after every Storyteller action.

## StorytellerAgentPanel

`StorytellerAgentPanel.tsx` loads Agent console rows when mounted and after pause/resume/run-now.

It displays:

- Agent name/profile
- seat number
- role and alignment
- status and auto mode tags
- recent task status and trigger
- recent action/result summary
- recent error code/message when present

Actions:

- `暂停`: enabled when `autoMode !== 'PAUSED'`
- `恢复`: enabled when `autoMode === 'PAUSED'`
- `立即运行`: enabled when the Agent is active and has a game seat
- `查看记忆`: opens a drawer/modal that loads memory and recent tasks for the selected Agent

Failures go through the existing global error handling pattern. Successful actions show concise Ant Design messages and refresh the panel.

## StorytellerMicControlPanel

`StorytellerMicControlPanel.tsx` reuses task-13 mic types and service functions.

It displays:

- session status
- current holder seat/name/Agent badge
- round-robin or grab-mic queue
- current turn countdown
- grab window countdown

Actions:

- `开启白天麦序`: calls `startClocktowerDayMic`
- `跳过当前`: calls `skipClocktowerMicTurn` with `currentTurnId`
- `关闭公聊`: calls `closeClocktowerMicSession`
- `延长 2 分钟`: calls `extendClocktowerMicSession(gameId, 120)`

Buttons are disabled when their required session state is absent. Each operation refreshes the session after success.

## StorytellerNightTaskPanel

`StorytellerNightTaskPanel.tsx` loads current night tasks and enriches them with the grimoire seats already present in the Storyteller game view.

It displays:

- night number
- sort order
- seat number and display name
- role code
- task type
- status
- choice JSON summary
- result JSON summary
- metadata summary

Actions:

- `确认`: calls `resolveClocktowerNightTask` without manual result/target when the task is ready.
- `跳过`: calls `skipClocktowerNightTask` with a short reason.
- `随机`: calls `randomChoiceClocktowerNightTask`.
- `手动选目标`: opens a target selector using grimoire seats and calls `resolveClocktowerNightTask` with `targetGameSeatIds`.

Agent suggestions are shown from existing task choice/result/metadata when present. If no explicit reason exists, the UI should show the choice without inventing one. This keeps the task honest until later LLM decision audit data exists.

## Data Flow

Agent control:

```text
StorytellerAgentPanel
  -> /games/{gameId}/agents
  -> pause/resume/run-now/memory/tasks
  -> Agent instance/task/memory repositories
  -> game events for Storyteller overrides
```

Mic control:

```text
StorytellerMicControlPanel
  -> existing /games/{gameId}/mic endpoints
  -> public mic service
  -> Storyteller override events for skip/extend/close
```

Night task control:

```text
StorytellerNightTaskPanel
  -> /games/{gameId}/night-tasks
  -> resolve/skip/random-choice
  -> role skill target validation and resolution
  -> game events + task metadata
```

## Error Handling

- Backend endpoints validate game id, Agent id, task id, and ownership before mutation.
- All new Storyteller APIs use the existing room-owner permission policy.
- Agent actions fail if the Agent is not in the target game or has no game seat.
- `run-now` fails for inactive/deleted Agents instead of creating orphan tasks.
- Night target overrides fail when the task is not current, already done/skipped, or targets violate role skill rules.
- Frontend errors use the existing `reportGlobalError` pattern and keep panels usable after failures.
- UI action buttons use local loading state to avoid duplicate submissions.

## Testing

Backend tests:

- `pauseAgent_setsAutoModePaused`
- `pausedAgentTask_doesNotAct`
- `resumeAgent_allowsTaskExecution`
- `runNowAgent_createsImmediateTaskAndEvent`
- `agentMemory_returnsOnlySelectedAgentGameMemory`
- `stSkipMicTurn_appendsStorytellerEvent`
- `stExtendMicSession_appendsStorytellerEvent`
- `stCloseMicSession_appendsStorytellerEvent`
- `stResolveNightTask_marksDone`
- `stOverrideAgentNightChoice_recordsMetadata`
- `stRandomChoiceNightTask_recordsMetadata`

Frontend tests:

- Agent panel renders Agent rows and calls pause/resume/run-now/memory endpoints.
- Mic control panel renders current holder/queue and calls skip/extend/close/start endpoints.
- Night task panel enriches tasks with grimoire seat data and calls resolve/skip/random/manual target endpoints.
- `StorytellerGameSurface` renders the new tabs without removing the existing grimoire, events, and chat monitoring.
- `clocktowerService.test.ts` covers all new endpoint paths.

Validation commands should be targeted first:

```text
bun run test -- StorytellerGrimoirePage.test.tsx StorytellerAgentPanel.test.tsx StorytellerMicControlPanel.test.tsx StorytellerNightTaskPanel.test.tsx clocktowerService.test.ts
bun run typecheck
```

Backend validation should run focused tests for the new Agent control, mic event, and night task override behavior. If the repository's Maven module layout requires broader coverage, run the smallest module-level test command that includes the changed backend package.

## Rollout Notes

This task completes the operational console needed before optional LLM policy work. The UI should not pretend LLM reasoning exists; it should display Agent choices and known metadata only. The design intentionally leaves full live event refresh and richer replay to later tasks because task 14 only requires that override events are written and usable for replay.
