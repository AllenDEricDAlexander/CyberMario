# Clocktower Night Task Engine Trouble Brewing Design

**Date:** 2026-07-09

**Status:** User-approved approach A. This spec covers task 09 from `docs/clocktower_agent_tasks`: implement a game-scoped night task engine for Trouble Brewing v0 on top of the task 08 flow cutover. Implementation must be planned separately before code changes.

---

## 1. Background

Tasks 01 through 08 established the Agent-capable game path:

- `clocktower_game_seat` carries HUMAN and AGENT actors.
- `ClocktowerGameActionExecutor` is the shared HUMAN / AGENT action entry point.
- `clocktower_game_public_mic_*` and game-level nomination / vote / execution tables handle day play.
- `ClocktowerGameFlowService` now blocks night-to-day advancement on pending game night tasks.
- Task 08 added the minimal `clocktower_game_night_task` table and `ClocktowerGameNightTaskGateway`, but task generation and role resolution are still no-op.

Task 09 fills that night-task seam. It must not modify `V35__clocktower_game_night_task.sql`; all schema extension must use a new Flyway migration.

## 2. Goals

1. Extend the existing game night-task table without changing existing migrations.
2. Generate idempotent FIRST_NIGHT and NIGHT tasks from Trouble Brewing night order data.
3. Add a game-scoped night service package under `top.egon.mario.clocktower.game.night`.
4. Add `NIGHT_CHOICE` support to `ClocktowerGameActionExecutor`.
5. Let HUMAN seats submit night choices through `/api/clocktower/games/{gameId}/actions`.
6. Let Agent seats submit automatic choices through an internal service call only.
7. Keep final role effects controlled by `ClocktowerNightResolutionService`, not by Agent choice.
8. Resolve core Trouble Brewing v0 effects:
   - Imp kill, with Monk and Soldier protection.
   - Poisoner poison marker.
   - Monk protection marker.
   - Fortune Teller, Empath, Undertaker, Washerwoman, Librarian, Investigator, Chef information.
   - Butler master marker plus a vote constraint hook.
   - Spy private grimoire snapshot.
9. Emit game events for choices, private information, markers, deaths, protected kills, and task completion.
10. Allow task 08 flow to advance to DAY once all mandatory tasks are `DONE` or `SKIPPED`.

## 3. Non-Goals

This task does not:

- Implement the task 10 async Agent task queue.
- Add LLM behavior.
- Add frontend UI.
- Remove legacy room night checklist / grimoire code.
- Add full Bad Moon Rising or Sects and Violets support.
- Implement all Trouble Brewing edge cases perfectly.
- Create a separate marker table.
- Change generic game-view permission semantics.
- Modify existing Flyway migration files.

## 4. Selected Approach

Use approach A: implement a backend night engine that extends the existing task-08 table, adds a role-skill Strategy boundary, and keeps night orchestration in domain services.

Rejected alternatives:

- Adding a new marker table now would broaden this task into a full status-marker subsystem and overlap with later Storyteller console work.
- Only creating tasks and deferring all resolution would not satisfy the task 09 acceptance criteria for Imp, Monk, Poisoner, private info, and flow unblocking.
- Running Agent choices asynchronously in this task would duplicate task 10 and risk blocking HTTP flow with Agent behavior.

Design pattern decision:

- Use Strategy for role behavior through `RoleSkill`, because each role has different legal targets, automatic choice, and resolution behavior.
- Use Domain Services for orchestration: task creation, choice submission, and resolution are separate enough to test independently.
- Do not introduce Factory Method, Chain of Responsibility, or State pattern yet. A small registry of `RoleSkill` beans and direct service orchestration is enough and matches the current Clocktower package style.

## 5. Package Boundary

Create or expand:

```text
top.egon.mario.clocktower.game.night
  dto
  po
  repository
  service
    ClocktowerGameNightTaskService
    ClocktowerNightOrderService
    ClocktowerNightResolutionService
    ClocktowerRoleSkillRegistry
  service.impl
  role
    RoleSkill
    NightTaskSpec
    NightTaskContext
    NightChoice
    NightResolution
    AvailableTargetSpec
    troublebrewing
  web
    ClocktowerGameNightTaskController
```

Responsibilities:

- `ClocktowerGameNightTaskService` creates tasks, lists current tasks, submits choices, submits Agent auto choices, skips tasks, and marks task states.
- `ClocktowerNightOrderService` reads `clocktower_night_order` and active game seats to decide which role tasks are due for the current night.
- `ClocktowerNightResolutionService` applies role outcomes and finalizes tasks.
- `RoleSkill` implementations own role-specific target rules and v0 resolution.
- `ClocktowerGameNightTaskGatewayImpl` delegates `initializeNightTasks` and `summarize` to the night-task service so task 08 flow keeps its existing interface.
- `ClocktowerGameActionExecutor` stays the shared entry point and delegates `NIGHT_CHOICE`.

## 6. Database Design

Add exactly one migration:

```text
be/src/main/resources/db/migration/V36__extend_clocktower_game_night_task.sql
```

Extend `clocktower_game_night_task`:

```text
task_type VARCHAR(64) NOT NULL DEFAULT 'ST_RESOLVE'
choice_json JSONB NOT NULL DEFAULT '{}'
result_json JSONB NOT NULL DEFAULT '{}'
resolved_by_actor_id BIGINT
```

Keep existing fields from `V35`:

```text
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
created_at / updated_at / created_by / updated_by / version / deleted
```

Statuses:

```text
PENDING
CHOSEN
DONE
SKIPPED
CANCELLED
```

Task types:

```text
CHOOSE_TARGET
RECEIVE_INFO
ST_RESOLVE
```

The task key remains unique per `(game_id, night_no, task_key, deleted)`. Use a stable key:

```text
<roleCode>:<gameSeatId>:<taskType>
```

This makes initialization idempotent for repeated flow reads or forced phase transitions.

## 7. DTO and API Contract

### 7.1 Game Action

Use the existing endpoint:

```text
POST /api/clocktower/games/{gameId}/actions
```

For `NIGHT_CHOICE`:

```json
{
  "actorGameSeatId": 1001,
  "actionType": "NIGHT_CHOICE",
  "targetGameSeatIds": [1002, 1003],
  "payload": {
    "taskId": 9001
  }
}
```

Rules:

- Game phase must be `FIRST_NIGHT` or `NIGHT`.
- The task must belong to the actor's game seat.
- The task must be current game / current night.
- `PENDING` tasks can become `CHOSEN`; `DONE`, `SKIPPED`, and `CANCELLED` cannot be changed.
- Legal targets are validated by the task's `RoleSkill`.

### 7.2 Storyteller Night API

Add ST-only APIs:

```text
GET  /api/clocktower/games/{gameId}/night/tasks
POST /api/clocktower/games/{gameId}/night/tasks/{taskId}/resolve
POST /api/clocktower/games/{gameId}/night/tasks/{taskId}/skip
POST /api/clocktower/games/{gameId}/night/resolve-ready
```

`resolve-ready` processes current-night tasks whose requirements are satisfied:

- `CHOSEN` for `CHOOSE_TARGET`
- `PENDING` for automatic `RECEIVE_INFO`
- ST-selected input for `ST_RESOLVE` if needed by the request

The first implementation may expose the controller for ST tests and later UI while task 14 builds the richer console.

### 7.3 Internal Agent Service

Add an internal method, not an HTTP endpoint:

```java
ClocktowerGameActionResponse autoChooseTask(Long gameId, Long taskId, Long agentInstanceId);
```

It uses the role skill's deterministic `autoChoose(...)` and submits `NIGHT_CHOICE`. It does not resolve effects.

## 8. Role Skill Contract

Use a record-oriented contract close to the task doc:

```java
public interface RoleSkill {
    String roleCode();

    boolean actsOnFirstNight();

    boolean actsOnOtherNights();

    List<NightTaskSpec> createTasks(NightTaskContext context);

    List<AvailableTargetSpec> legalTargets(NightTaskContext context);

    NightChoice autoChoose(NightTaskContext context);

    NightResolution resolve(NightTaskContext context, NightChoice choice);
}
```

`NightTaskContext` includes:

- game
- current task
- acting seat
- all active seats ordered by `seatNo`
- existing current-night tasks and their choices/results
- current day execution, when needed
- parsed task metadata

`NightResolution` contains:

- task result payload
- target seat life changes
- marker payloads
- private information events
- public events
- next task status

## 9. Trouble Brewing v0 Behavior

### 9.1 Choosing and Marker Roles

`POISONER`

- First night and other nights.
- Chooses one active target.
- Resolution writes `POISONED` marker data to `result_json`.
- Emits `MARKER_APPLIED` as `STORYTELLER`; the Poisoner only receives the choice submission event.

`MONK`

- Other nights only.
- Chooses one active non-self target.
- Resolution writes `PROTECTED` marker data to `result_json`.
- Emits `MARKER_APPLIED` as `STORYTELLER`.

`IMP`

- Other nights only.
- Chooses one active target.
- Resolution checks current-night Monk protection and Soldier role protection.
- If protected, no life status change and emits `DEMON_KILL_PROTECTED` as `STORYTELLER`.
- If not protected, sets target `lifeStatus = DEAD` and `publicLifeStatus = DEAD`, emits `PLAYER_DIED` as `PUBLIC`.

### 9.2 Information Roles

`FORTUNETELLER`

- First night and other nights.
- Chooses two active targets.
- v0 answer is `true` if either selected target is a living demon. Red herring is deferred.
- Emits `PRIVATE_INFO_RECEIVED` visible only to the Fortune Teller.

`EMPATH`

- First night and other nights.
- No target choice.
- Counts evil among nearest living neighbors on both sides.
- Emits `PRIVATE_INFO_RECEIVED`.

`UNDERTAKER`

- Other nights only.
- If the current day had an executed seat, emits that seat's role code privately.
- If no executed seat exists, marks the task `SKIPPED` or `DONE` with a no-execution result.

`WASHERWOMAN`, `LIBRARIAN`, `INVESTIGATOR`

- First night only.
- Choose deterministic candidates from current game seats:
  - Washerwoman: one Townsfolk role plus one other candidate.
  - Librarian: one Outsider role plus one other candidate, or a `zero` result if no Outsider exists.
  - Investigator: one Minion role plus one other candidate.
- Emit private candidate information to the acting seat.

`CHEF`

- First night only.
- Counts adjacent evil pairs in circular seat order.
- Emits `PRIVATE_INFO_RECEIVED`.

`SPY`

- First night and other nights.
- Emits a private grimoire snapshot to the Spy only.
- Does not change the generic `ClocktowerGameViewService` permission model.

### 9.3 Butler and Simplified Hooks

`BUTLER`

- First night and other nights.
- Chooses a non-self active master.
- Resolution writes `BUTLER_MASTER` to `result_json`.
- `ClocktowerGameVoteService` gets a focused hook: a Butler true vote is accepted only if the current master has already voted true on the same nomination. False votes remain allowed.

Simplified role hooks:

- Ravenkeeper: leave a resolution hook for future death-triggered private info.
- Virgin: leave nomination hook placement for a later nomination-special task.
- Slayer: no night task; action hook later.
- Mayor: no automatic redirect in v0.
- Saint: execution special victory remains outside task 09 unless naturally covered by later victory hooks.
- Recluse / Drunk: no deception override in v0 night calculations.
- Scarlet Woman: no demon transfer in v0.
- Baron: handled by board setup, not night task.

## 10. Task Lifecycle

### 10.1 Creation

Night tasks are initialized in two places:

1. `ClocktowerGameLifecycleServiceImpl` after a game starts in `FIRST_NIGHT`.
2. `ClocktowerGameFlowServiceImpl` through `ClocktowerGameNightTaskGateway.initializeNightTasks(...)` when advancing into later `NIGHT`.

Creation algorithm:

```text
load active seats for game
resolve script code and night type
load clocktower_night_order rows for present role codes
for each order:
  find the game seat with that role
  find RoleSkill
  create task specs
  upsert missing PENDING tasks by stable task_key
append NIGHT_TASKS_CREATED event as STORYTELLER
```

If a role has no `RoleSkill`, skip creation and record it in the event payload. This keeps advanced scripts from blocking Trouble Brewing v0.

### 10.2 Choice

For `NIGHT_CHOICE`:

```text
validate actor seat
validate current task
validate legal target count and target ids
write choice_json
status = CHOSEN
append NIGHT_CHOICE_SUBMITTED PRIVATE to actor seat
```

Agent auto choice uses the same service path and therefore gets the same validation.

### 10.3 Resolution

Resolution is ST / rule-engine controlled:

```text
load current-night tasks ordered by sort_order
resolve ready tasks in order
write result_json
status = DONE or SKIPPED
completed_at / skipped_at
append private/storyteller/public events
```

Pending mandatory `CHOOSE_TARGET` tasks continue to block task 08 flow until chosen and resolved or skipped by ST.

## 11. Event Visibility

Use the existing game event visibility vocabulary:

- `PUBLIC`: deaths and other public game-state changes.
- `PRIVATE`: submitted night choices and private info, visible to the acting seat.
- `STORYTELLER`: marker details and protected-kill details.
- `AUDIT`: system-only creation summaries if they are not useful in ST console.

Do not introduce `SYSTEM_ONLY` unless the projection mapper is expanded in a later task.

## 12. Integration With Existing Services

`ClocktowerGameActionExecutor`

- Adds `NIGHT_CHOICE` switch branch.
- Delegates to `ClocktowerGameNightTaskService.submitChoice(...)`.
- Keeps unsupported action rejection behavior consistent with current code.

`ClocktowerGameNightTaskGatewayImpl`

- Keeps the task 08 summary behavior.
- Changes `initializeNightTasks` from no-op to delegate to the new night-task service.

`ClocktowerGameLifecycleServiceImpl`

- Initializes first-night tasks immediately after game seats are copied and the game enters `FIRST_NIGHT`.

`ClocktowerGameVoteService`

- Adds Butler vote constraint only when a current-night `BUTLER_MASTER` result exists for that Butler.
- Does not generalize all role voting rules.

`ClocktowerGameViewService`

- Keeps its current available action behavior. It already exposes `NIGHT_CHOICE` during `FIRST_NIGHT` / `NIGHT`.

## 13. Error Handling

Hard validation still throws `ClocktowerException`:

- missing game id
- missing actor seat id
- game not found
- seat not found
- task not found
- unauthorized storyteller action

Business action rejection returns `ClocktowerGameActionResponse.rejected(...)` and appends `ACTION_REJECTED` privately:

- `CLOCKTOWER_NIGHT_CHOICE_PHASE_INVALID`
- `CLOCKTOWER_NIGHT_TASK_NOT_CURRENT`
- `CLOCKTOWER_NIGHT_TASK_ALREADY_RESOLVED`
- `CLOCKTOWER_NIGHT_TARGET_INVALID`
- `CLOCKTOWER_NIGHT_TARGET_COUNT_INVALID`
- `CLOCKTOWER_BUTLER_MASTER_NOT_VOTING`

## 14. Testing

Focused backend tests:

- `ClocktowerJpaMappingTests` covers extended night-task fields.
- `ClocktowerSchemaMigrationTests` covers `V36`.
- `ClocktowerGameNightTaskServiceTests`
  - `createFirstNightTasks_troubleBrewing_ordered`
  - `createOtherNightTasks_troubleBrewing_ordered`
  - `initializeNightTasks_isIdempotent`
  - `humanNightChoice_submitsChoice`
  - `agentNightChoice_submitsChoiceOnly`
  - `receiveInfoTask_resolvesWithoutChoice`
- `ClocktowerNightResolutionServiceTests`
  - `impKill_protectedByMonk_noDeath`
  - `impKill_unprotected_marksDead`
  - `poisoner_appliesMarker`
  - `fortuneTeller_receivesPrivateInfo`
  - `spy_receivesPrivateGrimoireInfo`
- `ClocktowerGameFlowServiceTests`
  - pending night task blocks DAY
  - resolved mandatory tasks allow DAY
- `ClocktowerGameActionServiceTests`
  - `NIGHT_CHOICE` uses actor ownership validation.
- `ClocktowerGameNominationServiceTests` or vote-focused tests cover Butler master voting.

Validation commands:

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerJpaMappingTests,ClocktowerGameNightTaskServiceTests,ClocktowerNightResolutionServiceTests test
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerGameActionServiceTests,ClocktowerGameFlowServiceTests,ClocktowerGameNominationServiceTests test
./mvnw -Dmaven.build.cache.enabled=false test
```

## 15. Acceptance Checklist

- FIRST_NIGHT creates current-night Trouble Brewing tasks.
- Later NIGHT creates other-night Trouble Brewing tasks.
- Generated tasks are ordered by `clocktower_night_order.sort_order`.
- Repeated initialization does not duplicate tasks.
- HUMAN seats can submit `NIGHT_CHOICE` through the game action endpoint.
- Agent seats can auto-submit choices through the internal service.
- Agent choices do not directly apply deaths, markers, or private info.
- ST / rule resolution finalizes effects and marks mandatory tasks complete.
- Imp / Monk / Poisoner basic interactions work.
- Information roles emit private events only to the intended seat.
- Spy receives a private grimoire snapshot without changing general view permissions.
- Normal players and Agents cannot see other seats' private info.
- Flow remains blocked while mandatory tasks are pending and advances after completion.
