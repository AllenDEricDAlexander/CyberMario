# Clocktower Game Action Executor Design

**Date:** 2026-07-08

**Status:** User-approved approach A. This spec covers task 06 from `docs/clocktower_agent_tasks`: add a new game-level action executor and API based on `gameId + gameSeatId`, so human players and Agent players share rule execution while keeping separate authentication entry points. Implementation must be planned separately before code changes.

---

## 1. Background

Tasks 01 through 05 established the new Agent-capable Clocktower game model:

- `clocktower_actor` / `clocktower_agent_instance` exist.
- `clocktower_room_seat` and `clocktower_game_seat` carry `actor_type`, `actor_id`, and `agent_instance_id`.
- Starting a game copies HUMAN and AGENT seats into `clocktower_game_seat`.
- Agent seats are excluded from IM membership because IM still requires a real `user_id`.
- Public mic state exists under `clocktower_game_public_mic_*`, and exposes `requireCanSpeak(gameId, actorGameSeatId)`.

The old action chain still uses old room-level models:

```text
/api/clocktower/rooms/{roomId}/actions
  -> ClocktowerActionServiceImpl
  -> ClocktowerRoomPo / ClocktowerSeatPo
  -> old nomination / vote / event tables
```

That chain cannot represent Agent actors and must remain untouched until the later cutover task. Task 06 adds the new game-level chain:

```text
Human controller -> HumanGameActionService -> ClocktowerGameActionExecutor
Agent runtime    -> AgentGameActionService -> ClocktowerGameActionExecutor
```

## 2. Goals

1. Add `/api/clocktower/games/{gameId}/actions` for human player actions in new game views.
2. Add an internal Agent service entry point that validates `agentInstanceId` before executing an action.
3. Introduce one shared executor that receives an already-authenticated actor context.
4. Support `PUBLIC_SPEECH`, `FINISH_SPEECH`, and `PASS` with `payload.passType = MIC_TURN`.
5. Gate `PUBLIC_SPEECH` through task 05 public mic ownership.
6. Let human and Agent holders finish or pass their own current mic turn.
7. Append `clocktower_game_event` records for accepted v1 actions.
8. Return explicit rejected responses for unsupported or invalid business actions.
9. Preserve old `/api/clocktower/rooms/{roomId}/actions` behavior.
10. Keep `NOMINATE` and `VOTE` visible in the action vocabulary but defer real execution to task 07.

## 3. Non-Goals

This task does not:

- Create new nomination, vote, or execution tables.
- Implement real `NOMINATE` / `VOTE` state changes.
- Implement night choices or night task resolution.
- Implement Agent automatic decision scheduling.
- Wire frontend controls to the new endpoint.
- Remove or modify the old room action endpoint.
- Add a Flyway migration.
- Start the app or run browser verification.

## 4. Selected Approach

Use approach A: implement the smallest correct game-action slice with a shared executor and explicit entry-point services.

Rejected alternatives:

- Event-only `NOMINATE` / `VOTE` placeholders would create misleading events without durable nomination / vote state. Task 07 should own those transitions.
- Folding task 07 into task 06 would mix executor plumbing with nomination / vote domain modeling and make the change harder to verify.
- A full Strategy handler hierarchy is premature for three executable v1 actions. A direct switch inside the executor is clearer now; task 07 or 09 can introduce per-action handlers when the action set becomes meaningfully larger.

Design pattern decision:

- Use Command-style DTOs: `GameActionCommand` carries normalized action input to the executor.
- Use application service / facade boundaries for human and Agent entry points.
- Do not introduce Strategy yet. The executor will isolate the variation point, so adding handlers later will be a local refactor rather than a public API change.

## 5. Package Boundary

Create a new backend package:

```text
top.egon.mario.clocktower.game.action
  ├── dto
  ├── service
  │   └── impl
  └── web
```

Use `web` rather than `controller` to match existing Clocktower packages.

Responsibilities:

- DTOs define external request / response and internal command / actor context records.
- Human service maps `RbacPrincipal` to the caller's HUMAN game seat.
- Agent service maps `agentInstanceId` to an ACTIVE AGENT game seat.
- Executor validates game state, seat state, action phase, mic ownership, and appends game events.
- Web controller exposes only the human HTTP endpoint.

Do not place new action execution in `ClocktowerActionServiceImpl`, `ClocktowerGameLifecycleServiceImpl`, or `ClocktowerFlowServiceImpl`.

## 6. DTO Design

### 6.1 Request

```java
public record ClocktowerGameActionRequest(
        Long actorGameSeatId,
        String actionType,
        List<Long> targetGameSeatIds,
        Long nominationId,
        Boolean vote,
        String content,
        Map<String, Object> payload
) {
}
```

Notes:

- `actorGameSeatId` is required for both human and Agent submissions.
- `targetGameSeatIds`, `nominationId`, and `vote` exist for task 07 compatibility but are not fully used in task 06.
- `payload.passType` is required for `PASS`; only `MIC_TURN` is supported in task 06.

### 6.2 Actor Context

```java
public record ActorContext(
        String actorType,
        Long actorId,
        Long userId,
        Long agentInstanceId,
        boolean systemInternal
) {
    public static ActorContext human(Long actorId, Long userId) {
        return new ActorContext("HUMAN", actorId, userId, null, false);
    }

    public static ActorContext agent(Long actorId, Long agentInstanceId) {
        return new ActorContext("AGENT", actorId, null, agentInstanceId, false);
    }

    public static ActorContext storyteller(Long userId) {
        return new ActorContext("STORYTELLER", null, userId, null, false);
    }

    public static ActorContext system() {
        return new ActorContext("SYSTEM", null, null, null, true);
    }
}
```

Task 06 uses `human(...)` and `agent(...)`. The storyteller and system factories are included so later tasks can reuse the same executor API without changing the public record shape.

### 6.3 Command

```java
public record GameActionCommand(
        Long gameId,
        Long actorGameSeatId,
        String actionType,
        List<Long> targetGameSeatIds,
        Long nominationId,
        Boolean vote,
        String content,
        Map<String, Object> payload
) {
}
```

Human and Agent services construct this command from the external request after identity checks.

### 6.4 Response

```java
public record ClocktowerGameActionResponse(
        boolean accepted,
        String rejectedCode,
        ClocktowerGameEventResponse event
) {
    public static ClocktowerGameActionResponse accepted(ClocktowerGameEventResponse event) {
        return new ClocktowerGameActionResponse(true, null, event);
    }

    public static ClocktowerGameActionResponse rejected(String rejectedCode) {
        return new ClocktowerGameActionResponse(false, rejectedCode, null);
    }
}
```

`event` may be null for rejected actions. Accepted v1 actions should return the primary action event:

- `PUBLIC_SPEECH` returns the speech event.
- `FINISH_SPEECH` returns the finish-speech action event.
- `PASS(MIC_TURN)` returns the player-pass action event.

Mic service will append additional mic lifecycle events, such as `MIC_TURN_FINISHED`, when `FINISH_SPEECH` or `PASS(MIC_TURN)` advances the mic turn.

### 6.5 Available Action

Add a lightweight internal record for action metadata:

```java
public record AvailableGameAction(
        String actionType,
        String label,
        boolean enabled,
        String disabledReason
) {
}
```

Task 06 does not have to wire this into `ClocktowerGameViewServiceImpl`; the existing view can keep using `AvailableActionResponse` until the frontend task.

## 7. Service API

### 7.1 Executor

```java
public interface ClocktowerGameActionExecutor {
    ClocktowerGameActionResponse execute(GameActionCommand command, ActorContext actor);
}
```

Executor responsibilities:

- Require `gameId`, `actorGameSeatId`, and `actionType`.
- Lock and load the game.
- Require `game.status = RUNNING`.
- Load the actor game seat by `id + gameId + deleted=false`.
- Require `seat.status = ACTIVE`.
- Require the seat actor fields match `ActorContext`.
- Validate action phase.
- Execute the v1 action.
- Append `clocktower_game_event` for accepted actions.

Identity errors and resource errors throw `ClocktowerException`. Business rule failures return `accepted=false` with a rejected code. Mic guard failures are business rule failures once the actor seat has been authenticated.

### 7.2 Human Entry Point

```java
public interface ClocktowerHumanGameActionService {
    ClocktowerGameActionResponse submit(Long gameId,
                                         ClocktowerGameActionRequest request,
                                         RbacPrincipal principal);
}
```

Human validation:

1. Principal must exist and contain `userId`.
2. Find the HUMAN active game seat by `gameId + principal.userId`.
3. `request.actorGameSeatId` must equal the caller's game seat id.
4. The caller cannot act as an Agent seat.
5. Build `ActorContext.human(actorId, userId)` and call the executor.

Failure codes:

- Missing principal: `CLOCKTOWER_AUTH_REQUIRED`
- No active human seat: `CLOCKTOWER_GAME_ACTION_PLAYER_REQUIRED`
- Actor seat mismatch: `CLOCKTOWER_GAME_ACTION_SEAT_FORBIDDEN`

### 7.3 Agent Entry Point

```java
public interface ClocktowerAgentGameActionService {
    ClocktowerGameActionResponse submitAgentAction(Long gameId,
                                                   Long agentInstanceId,
                                                   ClocktowerGameActionRequest request);
}
```

Agent validation:

1. `agentInstanceId` must identify a non-deleted Agent instance.
2. `instance.status = ACTIVE`.
3. `instance.autoMode != PAUSED`.
4. `instance.gameId == gameId`.
5. `instance.gameSeatId == request.actorGameSeatId`.
6. The game seat exists, belongs to the same game, and has `actorType = AGENT`.
7. Build `ActorContext.agent(actorId, agentInstanceId)` and call the executor.

Failure codes:

- Missing / inactive instance: `CLOCKTOWER_AGENT_INSTANCE_INVALID`
- Paused auto mode: `CLOCKTOWER_AGENT_AUTO_MODE_PAUSED`
- Game / seat mismatch: `CLOCKTOWER_AGENT_GAME_SEAT_MISMATCH`

## 8. Public Mic Integration

`PUBLIC_SPEECH` must call:

```java
publicMicService.requireCanSpeak(gameId, actorGameSeatId);
```

Task 05 currently has principal-based finish / release methods. Task 06 should add one internal actor-based public mic method:

```java
ClocktowerMicSessionView finishCurrentTurnAsActor(Long gameId, Long actorGameSeatId);
```

This method should:

- lock the current game and mic session
- refresh expired state
- require an active current turn
- require `currentHolderGameSeatId == actorGameSeatId`
- finish the current turn using the same transition path as the existing finish method

Both `FINISH_SPEECH` and `PASS(MIC_TURN)` call this actor-based method after appending their primary action event.

Do not add Agent-specific public mic controller endpoints. Agent runtime uses the internal service path.

## 9. Event Writing

Task 06 should append new game events to `clocktower_game_event`, not the old room event table.

Add a small shared helper to avoid duplicating task 05's event-sequence code:

```text
clocktower/game/service/ClocktowerGameEventAppender
```

Responsibilities:

- compute next `eventSeq` for a locked game
- serialize payload and visible seat ids
- save and flush `ClocktowerGameEventPo`
- map the saved event to `ClocktowerGameEventResponse`

The appender should assume callers already lock the game when ordering matters. Refactor task 05 public mic service to reuse it without changing external behavior.

Accepted action events:

| Action | Event type | Visibility | Actor | Target | Payload |
|---|---|---|---|---|---|
| `PUBLIC_SPEECH` | `PUBLIC_SPEECH` | `PUBLIC` | actor seat | null | `content`, `actorType`, optional `clientPayload` |
| `FINISH_SPEECH` | `FINISH_SPEECH` | `PUBLIC` | actor seat | null | `actorType` |
| `PASS(MIC_TURN)` | `PLAYER_PASSED` | `PUBLIC` | actor seat | null | `passType`, `actorType` |

Rejected business actions should append `ACTION_REJECTED` with `PRIVATE` visibility to the actor seat when the actor seat is known. This mirrors the old action service, but the response rejected code is the primary contract for task 06. Authentication, authorization, and missing-resource failures do not append action events.

## 10. Action Rules

### 10.1 Common Rules

All task 06 actions require:

- game exists
- game is `RUNNING`
- actor seat belongs to the game
- actor seat status is `ACTIVE`
- actor context matches the seat actor fields
- action type is known

Unknown action type returns:

```text
UNKNOWN_ACTION_TYPE
```

### 10.2 `PUBLIC_SPEECH`

Rules:

- phase must be `DAY` or `NOMINATION`
- content must be non-blank
- content must be at most 1000 characters
- actor must currently hold the public mic

Rejected codes:

- `CLOCKTOWER_PUBLIC_SPEECH_PHASE_INVALID`
- `CLOCKTOWER_PUBLIC_SPEECH_CONTENT_REQUIRED`
- `CLOCKTOWER_PUBLIC_SPEECH_CONTENT_TOO_LONG`
- public mic service codes such as `CLOCKTOWER_MIC_NOT_HOLDER`

The executor writes one `PUBLIC_SPEECH` game event. The existing game view already reads visible game events, so the new event will appear in the new game view without frontend changes.

When `publicMicService.requireCanSpeak(...)` throws a `ClocktowerException` after actor authentication succeeds, the executor catches it and returns a rejected response with the exception message as `rejectedCode`.

### 10.3 `FINISH_SPEECH`

Rules:

- phase must be `DAY` or `NOMINATION`
- actor must currently hold the mic

Behavior:

1. Append `FINISH_SPEECH`.
2. Call `publicMicService.finishCurrentTurnAsActor(gameId, actorGameSeatId)`.

The mic service then appends its own `MIC_TURN_FINISHED` event and advances the queue.

### 10.4 `PASS`

Task 06 supports only:

```text
payload.passType = MIC_TURN
```

Rules:

- phase must be `DAY` or `NOMINATION`
- actor must currently hold the mic

Behavior:

1. Append `PLAYER_PASSED` with `passType = MIC_TURN`.
2. Call `publicMicService.finishCurrentTurnAsActor(gameId, actorGameSeatId)`.

Other pass types return:

```text
CLOCKTOWER_PASS_TYPE_UNSUPPORTED
```

### 10.5 `NOMINATE` and `VOTE`

Task 06 accepts these action types as known but does not execute them.

Return:

```text
CLOCKTOWER_ACTION_NOT_IMPLEMENTED
```

Task 07 will replace this branch with real nomination / vote state changes.

## 11. HTTP API

Add:

```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/clocktower/games/{gameId}")
public class ClocktowerGameActionController extends ClocktowerReactiveSupport {

    @PostMapping("/actions")
    public Mono<ApiResponse<ClocktowerGameActionResponse>> submit(
            @PathVariable Long gameId,
            @RequestBody ClocktowerGameActionRequest request,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> humanGameActionService.submit(gameId, request, principal));
    }
}
```

RBAC:

- No new permission code is required.
- Existing `api:clocktower:game:action` already matches `POST /api/clocktower/games/{gameId}/actions`.
- Keep the old room action path covered by the same provider rule.

## 12. Repository Additions

Add focused repository methods rather than ad hoc filtering:

```java
Optional<ClocktowerGameSeatPo> findByIdAndGameIdAndDeletedFalse(Long id, Long gameId);
Optional<ClocktowerGameSeatPo> findByIdAndDeletedFalse(Long id);
```

The existing `findByGameIdAndUserIdAndDeletedFalse` supports human lookup. The existing Agent instance repository supports instance lookup.

## 13. Error Handling

Use the current Clocktower convention:

- Throw `ClocktowerException` for authentication, authorization, missing resources, and impossible state.
- Return `ClocktowerGameActionResponse.rejected(code)` for validly submitted actions that fail game rules.
- Catch public mic rule exceptions inside the executor and translate them to rejected responses after the actor has been authenticated.

This matches the old action service while keeping API errors distinct from game-rule rejections.

## 14. Compatibility

Preserve:

- `ClocktowerActionController`
- `ClocktowerActionService`
- `ClocktowerActionServiceImpl`
- old room nomination / vote / event behavior
- existing mic controller behavior

The new action endpoint is additive. Later tasks can move frontend and flow code onto it without breaking old pages.

## 15. Testing

Add `ClocktowerGameActionServiceTests` with Spring Boot integration coverage:

1. `humanSubmitAction_requiresOwnGameSeat`
2. `humanSubmitAction_cannotActAsAgent`
3. `agentSubmitAction_requiresAgentInstanceGameSeatMatch`
4. `agentSubmitAction_rejectsPausedAutoMode`
5. `publicSpeech_requiresMic`
6. `publicSpeech_appendsGameEvent`
7. `finishSpeech_finishesCurrentMicTurn`
8. `passMicTurn_finishesCurrentMicTurnAndAppendsPassEvent`
9. `nominateVote_notImplementedUntilTask07`

Add or extend public mic tests for `finishCurrentTurnAsActor`.

Validation commands:

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false \
  -Dtest=ClocktowerGameActionServiceTests,ClocktowerPublicMicServiceTests,ClocktowerRbacResourceProviderTests test

./mvnw -Dmaven.build.cache.enabled=false test
```

Run full backend tests before declaring the task complete.

## 16. Implementation Notes

- Keep actions transactional.
- Lock the game before calculating `eventSeq`.
- Keep JSON serialization through `ObjectMapper`, matching existing game event projection code.
- Do not add new enum classes unless the implementation becomes clearer; current Clocktower code mostly stores action / phase / visibility strings in the new game tables.
- Keep comments minimal and follow nearby service style.
- Do not add frontend code in task 06.
- Do not edit existing Flyway migrations.
