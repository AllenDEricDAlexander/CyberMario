# Clocktower Tests Flags Cutover Design

## Goal

Task 16 makes the actor-agent Clocktower runtime safe to merge, roll back, and gradually enable while the legacy room runtime still exists.

The task is a consolidation task, not a new gameplay task. It must:

- Add runtime feature flags for Agent seats, new game actions, public mic, new game flow, and LLM Agent decisions.
- Keep old room action and room flow endpoints available.
- Make rollback a code/config decision instead of a database rollback.
- Harden tests around the new-vs-old boundaries created by tasks 01 through 15.
- Mark the intended cutover path without deleting or broadly rewriting legacy code.

This task does not add new roles, private chat behavior, stronger Agent strategy, new LLM prompts, or database schema.

## Existing Context

The current branch already has the major task 01 through 15 runtime pieces:

- Agent room seats are created from `agentSeatCount`.
- Agent room seats use `actorType=AGENT`, have no human `userId`, and do not join IM members.
- Game start snapshots human and Agent room seats into `game_seat`.
- Public mic creates day round-robin and grab-mic sessions.
- New game action executor writes `game_event`, `clocktower_game_nomination`, and `clocktower_game_vote`.
- New game flow advances phases and can start day mic sessions.
- Agent runtime, memory, heuristic policy, LLM policy, and decision audit are present.
- Frontend `/clocktower/rooms/:roomId/play` loads current `gameView`; player mode uses the new game action endpoint when `useGameActionApi` is true.

The remaining gap is the rollback and cutover boundary. There is no single typed feature-flag model yet, and `clocktower.public-mic` only controls durations, not enablement.

## Approach

Use the smallest safe cutover layer:

1. Add typed feature properties with default values matching the task document.
2. Inject the properties only at existing boundary services/controllers.
3. Keep old room endpoints and old services behavior unchanged.
4. Add tests for flag behavior and data separation instead of duplicating every task 01 through 15 test.
5. Add only light legacy markers where useful.

This is intentionally not a new routing framework. The project already has clear service boundaries, and the flags are operational gates rather than polymorphic business rules.

## Configuration

Add typed properties under `clocktower`:

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
  agent:
    llm:
      enabled: false
```

`clocktower.llm-agent.enabled` is a global kill switch for task 16. The task 15 LLM policy setting under `clocktower.agent.llm.enabled` remains the detailed policy setting. An LLM decision can only run when both are true and the configured Agent policy is LLM-capable.

Existing `clocktower.public-mic.round-robin-turn-seconds`, `grab-mic-total-seconds`, and `grab-mic-hold-seconds` remain on the same properties object.

Main and test `application.yaml` should declare these defaults explicitly. Test resources keep the Agent worker runner disabled.

## Backend Gates

### Agent Player

`agent-player.enabled=false` rejects room creation when `agentSeatCount > 0`.

`agentSeatCount=0` keeps the current original behavior. Rejecting non-zero Agent seats is preferred over silently ignoring them because the caller immediately sees that the feature is unavailable instead of getting a room that differs from the request.

Expected error code:

```text
CLOCKTOWER_AGENT_PLAYER_DISABLED
```

### Game Actions

`game-actions.enabled=false` rejects new game action submission:

- Human HTTP path through `/api/clocktower/games/{gameId}/actions`.
- Agent internal action path through `ClocktowerAgentGameActionService`.

Legacy `/api/clocktower/rooms/{roomId}/actions` remains unchanged. This prevents GAME_V2 action writes during rollback while preserving old rooms.

Expected error code:

```text
CLOCKTOWER_GAME_ACTIONS_DISABLED
```

### Public Mic

`public-mic.enabled=false` disables new public mic orchestration:

- `startDayMicSession` rejects direct start attempts.
- Day phase entry through new game flow does not create a mic session.
- Day-to-nomination flow is not blocked by a missing or open mic session.
- `getMicSession`, finish, skip, grab, release, extend, and close reject while disabled.
- `PUBLIC_SPEECH` in `DAY` is temporarily allowed without mic, matching the task document's old-rule fallback.

`PUBLIC_SPEECH` still requires valid game phase, non-empty content, and actor-seat ownership. `FINISH_SPEECH` and mic-turn `PASS` remain mic-specific and should reject when the mic feature is disabled or no holder exists.

Expected error code:

```text
CLOCKTOWER_PUBLIC_MIC_DISABLED
```

### New Flow

`new-flow.enabled=false` rejects new game flow endpoints:

- `GET /api/clocktower/games/{gameId}/flow`
- `POST /api/clocktower/games/{gameId}/flow/advance`
- `POST /api/clocktower/games/{gameId}/flow/force-advance`

Legacy `/api/clocktower/rooms/{roomId}/flow` remains unchanged.

Expected error code:

```text
CLOCKTOWER_NEW_FLOW_DISABLED
```

### LLM Agent

`llm-agent.enabled=false` forces heuristic decisions, even when task 15 detailed LLM settings are enabled.

Effective behavior:

- `clocktower.llm-agent.enabled=false`: no LLM call.
- `clocktower.llm-agent.enabled=true` and `clocktower.agent.llm.enabled=false`: no LLM call.
- Both enabled and policy is LLM-capable: existing task 15 LLM/fallback behavior applies.

Decision audit should still record heuristic or fallback metadata through the existing decision path.

## Runtime Model Metadata

Newly created generic room profiles should include runtime metadata:

```json
{"runtimeModel":"GAME_V2","seatingPolicy":"OPEN"}
```

This is a cutover marker for new rooms. It does not require a new column or migration. Existing rooms without the marker remain readable and continue to work with current behavior.

The marker is not used to delete old paths in task 16. It exists so later phases can distinguish GAME_V2 rooms from legacy rooms safely.

## Frontend Boundary

The current player play route already loads current `gameView` and passes `useGameActionApi` into `GameRoomSurface`, so new player actions use:

```text
/api/clocktower/games/{gameId}/actions
```

Task 16 should harden this with focused tests:

- The play surface uses the game endpoint for player submit when `useGameActionApi` is true.
- Public speech uses the game endpoint.
- Legacy `submitClocktowerPlayerAction` remains available only for the legacy surface path.

No new visual design or browser verification is required. The user will run runtime UI testing separately.

## Legacy Markers

Add light legacy markers to the old room action/flow implementation where they help future maintainers:

- `ClocktowerActionServiceImpl`
- `ClocktowerFlowServiceImpl`

Do not change their behavior. Do not remove old controllers, DTOs, tables, or tests.

## Test Plan

Use existing task-specific tests and add only missing cutover assertions.

### Backend

Add or extend focused tests for:

- Feature property defaults and binding.
- `agent-player.enabled=false` rejects non-zero `agentSeatCount` and preserves zero-agent room creation.
- `game-actions.enabled=false` rejects human new game actions and Agent internal new game actions.
- Legacy room action service remains unaffected by `game-actions.enabled=false`.
- `public-mic.enabled=false` prevents day mic creation and lets flow advance from DAY without mic blockers.
- `PUBLIC_SPEECH` can succeed in DAY without mic only while public mic is disabled.
- `new-flow.enabled=false` rejects new game flow service methods without affecting legacy flow service tests.
- `llm-agent.enabled=false` prevents LLM calls even when `clocktower.agent.llm.enabled=true`.
- GAME_V2 action tests continue to write new game nomination/vote tables, not legacy nomination/vote tables.
- Legacy room action tests continue to write old nomination/vote tables, not new game nomination/vote tables.
- Schema migration tests continue covering actor/agent, public mic, nomination/vote, night task, agent task, memory, and decision tables.

### Frontend

Add or extend focused Vitest tests for:

- Agent seats count as occupied in lobby remains covered.
- Game room submit uses `/api/clocktower/games/{gameId}/actions` when the refactor play surface is active.
- Public mic banner and speech disabled/allowed rendering remains covered by existing `PublicMicPanel` tests.

Avoid broad end-to-end tests in this task.

## Validation

Targeted validation after implementation:

```bash
cd be
./mvnw -Dtest=ClocktowerRoomRefactorServiceTests,ClocktowerGameLifecycleServiceTests,ClocktowerPublicMicServiceTests,ClocktowerGameActionServiceTests,ClocktowerGameFlowServiceTests,ClocktowerAgentLlmPolicyTests,ClocktowerSchemaMigrationTests test

cd ../fe
bun run --bun vitest run src/modules/clocktower/clocktowerService.test.ts src/modules/clocktower/GameRoomPage.test.tsx src/modules/clocktower/RoomLobbyPage.test.tsx src/modules/clocktower/components/PublicMicPanel.test.tsx
```

If these slices pass but unrelated suites fail, report the failing command and reason instead of hiding it.

Do not start the application server as part of this task.

## Design Pattern Decision

No new major design pattern is needed for the flag layer. A Strategy or State machine would be overbuilt because the flags are simple operational gates, not interchangeable algorithms.

Use typed configuration plus small guard checks at existing boundaries. This keeps the change consistent with the current Spring service style and avoids introducing a new abstraction layer.

Existing Strategy in task 15 remains the right boundary for Agent policy selection. Task 16 only adds the `llm-agent` kill switch to that existing strategy path.

## Risks And Mitigations

- Risk: disabling public mic could deadlock DAY flow because current flow expects a session.
  Mitigation: when mic is disabled, DAY flow ignores mic blockers and can move to NOMINATION.

- Risk: disabling game actions only at HTTP controller would still allow Agent runtime to write new action data.
  Mitigation: gate both human HTTP service and Agent internal action service.

- Risk: adding runtime metadata could overwrite existing profile metadata.
  Mitigation: preserve existing metadata keys and add `runtimeModel` alongside `seatingPolicy`.

- Risk: LLM settings become ambiguous.
  Mitigation: document and test that the new `llm-agent` flag is a global kill switch while `clocktower.agent.llm.enabled` remains the detailed task 15 LLM switch.

## Acceptance

Task 16 is complete when:

- Feature flags bind with documented defaults.
- Disabled flags produce predictable rejections or fallback behavior.
- Old room endpoints remain functional.
- New game action/flow/mic paths are independently disableable.
- GAME_V2 paths do not write legacy nomination/vote data.
- Legacy room paths do not read or write new game nomination/vote data.
- Frontend tests prove the refactor play surface submits to the game action endpoint.
- Targeted backend and frontend tests pass, or any external/unrelated failure is reported clearly.
