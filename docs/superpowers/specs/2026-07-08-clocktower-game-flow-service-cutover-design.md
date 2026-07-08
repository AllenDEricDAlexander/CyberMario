# Clocktower Game Flow Service Cutover Design

## Context

Task 08 moves phase advancement for new Clocktower games from the legacy room flow model to the game model introduced by tasks 03 through 07. The new flow must use `ClocktowerGamePo`, `ClocktowerGameSeatPo`, public mic sessions, game nominations, game executions, game events, and a minimal game night-task surface. The old room flow remains available for legacy room pages and is not refactored in this task.

The implementation continues in the existing `codex/clocktower-actor-agent-foundation` worktree because task 08 depends on the task 05 public mic model and task 07 nomination/vote/execution model already committed there.

## Goals

- Add a new `clocktower/game/flow` package with service, DTO, and web entrypoints.
- Expose game-scoped flow APIs:
  - `GET /api/clocktower/games/{gameId}/flow`
  - `POST /api/clocktower/games/{gameId}/flow/advance`
  - `POST /api/clocktower/games/{gameId}/flow/force-advance`
- Advance new game phases using only game-scoped state.
- Preserve the legacy `ClocktowerFlowServiceImpl` and `/api/clocktower/rooms/{roomId}/flow/...` APIs.
- Support real blocking for pending game night tasks with a minimal v1 table.
- Write `PHASE_CHANGED` game events for phase changes.
- Provide a scheduler extension point for future Agent tasks without implementing the full task queue before task 10.

## Non-Goals

- Do not implement complete Trouble Brewing night resolution.
- Do not implement Agent decision runtime or the task queue.
- Do not change frontend flow UI in this task.
- Do not remove legacy room flow code.
- Do not modify existing Flyway migrations.

## Package Shape

```text
top.egon.mario.clocktower.game.flow
  dto
    ClocktowerGameAdvanceRequest
    ClocktowerGameAdvanceResult
    ClocktowerGameFlowView
  service
    ClocktowerGameFlowService
    ClocktowerGameVictoryService
    ClocktowerGameNightTaskGateway
    ClocktowerGamePhaseSignalScheduler
  service.impl
    ClocktowerGameFlowServiceImpl
    ClocktowerGameVictoryServiceImpl
    ClocktowerGameNightTaskGatewayImpl
    NoopClocktowerGamePhaseSignalScheduler
  web
    ClocktowerGameFlowController
```

The flow service is the orchestration boundary. Victory, night-task summary, and future Agent scheduling stay behind small interfaces so task 09 and task 10 can replace the internals without changing the controller contract.

## DTO Contract

`ClocktowerGameFlowView`:

```java
Long gameId;
String status;
String phase;
int dayNo;
int nightNo;
boolean advanceAllowed;
List<String> blockingReasons;
String nextPhase;
Map<String, Object> counters;
```

`ClocktowerGameAdvanceRequest`:

```java
String targetPhase;
String reason;
Map<String, Object> metadata;
```

Normal `advance` ignores `targetPhase` unless it needs an explicit no-execution marker. `force-advance` requires `reason` and accepts `targetPhase`.

`ClocktowerGameAdvanceResult`:

```java
Long gameId;
String previousPhase;
String phase;
boolean advanced;
boolean forced;
ClocktowerGameFlowView flow;
```

## Night Task v1 Schema

Task 09 owns complete night behavior, but task 08 needs real pending-task blocking. Add exactly one migration:

```text
V35__clocktower_game_night_task.sql
```

Minimal table:

```text
clocktower_game_night_task
  id
  game_id
  night_no
  task_key
  actor_game_seat_id
  role_code
  status
  mandatory
  sort_order
  completed_at
  skipped_at
  metadata_json
  audit fields from project convention
```

Statuses for v1:

```text
PENDING
DONE
SKIPPED
```

The gateway counts mandatory tasks where status is not `DONE` or `SKIPPED`. If no v1 tasks exist for a night, the night is considered unblocked. This keeps task 08 usable before task 09 generates full night tasks while still allowing tests to create pending rows and verify the blocker.

## Phase Rules

### FIRST_NIGHT or NIGHT to DAY

Allowed when all mandatory current-night tasks are `DONE` or `SKIPPED`.

Actions:

- Set `game.phase = DAY`.
- For `FIRST_NIGHT`, set `dayNo = 1`.
- For later `NIGHT`, increment `dayNo`.
- Save `lastActiveAt`.
- Append `PHASE_CHANGED`.
- Create or reuse the current day mic session through `ClocktowerPublicMicService.startDayMicSession`.
- Call the phase signal scheduler extension point.

### DAY to NOMINATION

Allowed only when the current day public mic session exists and has status `CLOSED`.

Blocking reasons:

- `MIC_ROUND_ROBIN_NOT_FINISHED`
- `MIC_GRAB_MIC_NOT_FINISHED`
- `MIC_SESSION_NOT_FOUND`

Actions:

- Set `game.phase = NOMINATION`.
- Append `PHASE_CHANGED`.
- Call the phase signal scheduler extension point.

### NOMINATION or EXECUTION to NIGHT

Task 07 already sets `game.phase = EXECUTION` when ST resolves execution, so task 08 must support both `NOMINATION` and `EXECUTION` as day-ending phases.

Allowed when:

- No open nomination exists for the current game.
- The current day execution is `RESOLVED`, or ST explicitly records no execution through the execution service before advancing.
- No victory condition ends the game first.

Actions:

- Set `game.phase = NIGHT`.
- Increment `nightNo`.
- Save `lastActiveAt`.
- Append `PHASE_CHANGED`.
- Ask the night-task gateway to initialize v1 night tasks. In task 08 this may be a no-op unless future rows are already configured.
- Call the phase signal scheduler extension point.

### Any Running Phase to ENDED

Before ordinary advancement, evaluate basic victory:

- all demon seats dead -> `GOOD_WIN`
- alive player count <= 2 and at least one demon alive -> `EVIL_WIN`

If a victory exists, advancement ends the game, updates room/profile state consistently with lifecycle service behavior, writes `GAME_ENDED`, writes `PHASE_CHANGED`, and returns an ended flow view.

Special role victory rules are left for task 09 or later.

## Force Advance

`force-advance` is ST-only and must include `reason`. It bypasses normal blocking reasons but still validates:

- game exists
- caller is room owner/ST
- game is running unless forcing an already ended game is rejected
- `targetPhase` is one of `DAY`, `NOMINATION`, `NIGHT`, `ENDED`

It writes `PHASE_CHANGED` with `forced = true`, `reason`, and request metadata in the payload. When target is `DAY`, it creates/reuses a mic session. When target is `NIGHT`, it increments `nightNo` if the previous phase was not already `NIGHT`.

## Event Payloads

`PHASE_CHANGED` payload:

```json
{
  "previousPhase": "DAY",
  "phase": "NOMINATION",
  "dayNo": 1,
  "nightNo": 1,
  "forced": false,
  "reason": null
}
```

Victory payload also includes `winner`, `victoryReason`, `aliveCount`, and `demonAlive`.

All events use `ClocktowerGameEventAppender` and visibility `PUBLIC`.

## Permissions

Flow view requires an authenticated game viewer. The simplest v1 path is to use existing game viewer resolution for read access where possible.

`advance` and `force-advance` require the room owner/ST through `ClocktowerRoomAccessPolicy.requireOwner`, matching task 05 and task 07 service style.

## Error Handling and Blocking Reasons

`getFlow` returns blocking reasons. `advance` throws a `ClocktowerException` with the first blocking reason when advancement is not allowed.

Canonical v1 reasons:

```text
PENDING_NIGHT_TASKS
MIC_SESSION_NOT_FOUND
MIC_ROUND_ROBIN_NOT_FINISHED
MIC_GRAB_MIC_NOT_FINISHED
OPEN_NOMINATION_EXISTS
EXECUTION_NOT_RESOLVED
GAME_ALREADY_ENDED
GAME_FLOW_PHASE_UNSUPPORTED
```

Project exception codes may keep the `CLOCKTOWER_` prefix when thrown, while the view can expose the shorter business reason strings from the task document.

## Design Pattern Consideration

This task benefits from a small Domain Service split, not a full Strategy or Drools rule system. `ClocktowerGameFlowService` coordinates the phase transition, `ClocktowerGameVictoryService` isolates victory rules, and `ClocktowerGameNightTaskGateway` isolates task-09 expansion. This avoids duplicating conditional logic inside the controller while staying consistent with the existing service/repository style. A broader Strategy or Chain of Responsibility is rejected for v1 because there are only a few fixed transitions and no current need for runtime-pluggable transition handlers.

## Tests

Add focused backend tests for:

- `advanceFirstNight_pendingTasks_rejected`
- `advanceFirstNight_tasksComplete_entersDayAndStartsMic`
- `advanceDay_micOpen_rejected`
- `advanceDay_micClosed_entersNomination`
- `advanceNomination_openNomination_rejected`
- `advanceNomination_executionUnresolved_rejected`
- `advanceExecution_executionResolved_entersNight`
- `victory_goodWinWhenAllDemonsDead`
- `victory_evilWinWhenTwoAliveAndDemonAlive`
- `forceAdvance_requiresReason`
- `forceAdvance_writesForcedPhaseEvent`

Targeted validation:

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerGameFlowServiceTests test
```

Final validation:

```bash
./mvnw -Dmaven.build.cache.enabled=false test
```

## Risks

- Task 07 stores resolved executions with phase `EXECUTION`, while task 08 text lists only `NOMINATION -> NIGHT`. This design explicitly supports `EXECUTION -> NIGHT` to preserve task 07 behavior.
- The v1 night-task table is intentionally minimal. Task 09 must extend behavior through the gateway rather than replacing the flow contract.
- Agent scheduling is only an extension point in this task because no queue/runtime exists yet.
