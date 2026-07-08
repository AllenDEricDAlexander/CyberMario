# Clocktower Public Mic Round Robin Design

**Date:** 2026-07-08

**Status:** User-approved approach A. This spec covers task 05 from `docs/clocktower_agent_tasks`: add a new game-level public mic service for one round of seat-order public speech followed by a five-minute grab-mic window. Implementation must be planned separately before code changes.

---

## 1. Background

Tasks 01 through 04 created real Agent actors, Agent seats, game-seat Actor fields, and a start-game path that supports one human player plus Agent players. The next missing game-level rule is public-speaking order.

Current code has two separate surfaces:

- New game view reads `clocktower_game_*` through `ClocktowerGameViewServiceImpl`, and exposes `PUBLIC_SPEECH` based only on phase.
- Old `ClocktowerActionServiceImpl` handles `PUBLIC_SPEECH` through old room / seat models, with no mic holder check.

Task 05 should not retrofit the old action chain. Task 06 will introduce the new `gameId + gameSeatId` action executor and call this task's mic guard before accepting public speech.

## 2. Goals

1. Persist one public mic session per `gameId + dayNo`.
2. Create one round-robin turn for every eligible HUMAN or AGENT game seat, sorted by `seatNo`.
3. Ensure at most one active mic turn exists for a session.
4. Start the first round-robin turn immediately when a day mic session starts.
5. Let the current holder or storyteller finish / release a turn.
6. Let the storyteller skip turns, extend grab-mic time, and close the session.
7. Move to `GRAB_MIC` automatically after all round-robin turns finish, skip, or expire.
8. Let human players grab the mic during the five-minute grab window when no holder is active.
9. Close the session when the grab window expires.
10. Expose a `canSpeak` / `requireCanSpeak` guard for task 06 and later Agent runtime use.
11. Emit public mic lifecycle game events for future frontend and Agent runtime consumers.

## 3. Non-Goals

This task does not:

- Implement the new game action executor.
- Wire old `/api/clocktower/rooms/{roomId}/actions` into public mic.
- Implement Agent automatic speech or Agent grab-mic decisions.
- Implement frontend mic UI.
- Implement nomination, vote, or execution cutover.
- Replace existing old room / flow services.
- Start the app or run browser verification.

## 4. Selected Approach

Use approach A: build an independent `clocktower/game/mic` module with persistence, service, controller, API DTOs, RBAC resource declarations, and tests. The service owns public mic state transitions and exposes `requireCanSpeak(gameId, actorGameSeatId)` for task 06.

Rejected alternatives:

- Wiring old room actions now would couple new Agent-capable game rules to old `ClocktowerRoomPo` / `ClocktowerSeatPo` flows and fight task 06.
- Only adding tables and PO classes would not satisfy the acceptance criteria around active turns, transitions, grab-mic, and storyteller controls.
- A full State pattern is unnecessary. The state space is small and stable: `ROUND_ROBIN -> GRAB_MIC -> CLOSED`. A focused domain service with explicit transition helpers is clearer and matches the current Clocktower service style.

## 5. Package Boundary

Create a new backend package:

```text
top.egon.mario.clocktower.game.mic
  ├── dto
  ├── po
  ├── repository
  ├── service
  │   └── impl
  └── web
```

Responsibilities:

- PO classes map the two new mic tables.
- Repositories provide locked session lookup, turn lookup, and queue queries.
- DTOs expose a frontend-ready session view and turn view.
- Service implements transitions, permissions, timeout refresh, and `requireCanSpeak`.
- Controller exposes mic APIs under `/api/clocktower/games/{gameId}/mic`.

Do not place this logic in `ClocktowerGameLifecycleServiceImpl`, `ClocktowerActionServiceImpl`, or `ClocktowerFlowServiceImpl`.

## 6. Database Design

Add exactly one new Flyway migration:

```text
be/src/main/resources/db/migration/V33__clocktower_public_mic.sql
```

Do not edit existing migrations.

### 6.1 `clocktower_game_public_mic_session`

Columns:

- `id`
- `game_id`
- `day_no`
- `status`
- `current_holder_game_seat_id`
- `current_turn_id`
- `round_started_at`
- `round_finished_at`
- `grab_started_at`
- `grab_ends_at`
- `closed_at`
- `metadata_json`
- standard audit columns: `created_at`, `updated_at`, `created_by`, `updated_by`, `version`, `deleted`

Indexes:

- unique `(game_id, day_no)` where `deleted = false`
- lookup index on `(game_id, status)` where `deleted = false`

Session statuses:

- `ROUND_ROBIN`
- `GRAB_MIC`
- `CLOSED`

### 6.2 `clocktower_game_public_mic_turn`

Columns:

- `id`
- `session_id`
- `game_id`
- `day_no`
- `game_seat_id`
- `turn_order`
- `stage`
- `acquisition_type`
- `status`
- `started_at`
- `ended_at`
- `expires_at`
- `speech_event_id`
- `metadata_json`
- standard audit columns: `created_at`, `updated_at`, `created_by`, `updated_by`, `version`, `deleted`

Indexes:

- `(session_id, turn_order)` where `deleted = false`
- `(game_id, game_seat_id)` where `deleted = false`
- unique `(session_id)` where `deleted = false and status = 'ACTIVE'`

The partial unique active-turn index is intentional. It makes the "only one speaker at a time" rule a database invariant in addition to service locking.

Turn stages:

- `ROUND_ROBIN`
- `GRAB_MIC`

Acquisition types:

- `ROUND_ROBIN`
- `GRAB`
- `ST_GRANT`
- `SYSTEM_TIMEOUT`

Turn statuses:

- `PENDING`
- `ACTIVE`
- `DONE`
- `SKIPPED`
- `EXPIRED`
- `CANCELLED`

## 7. Configuration

Add `ClocktowerPublicMicProperties` with:

```yaml
clocktower:
  public-mic:
    round-robin-turn-seconds: 45
    grab-mic-total-seconds: 300
    grab-mic-hold-seconds: 45
```

Defaults live in Java so tests and local runs do not require YAML changes.

Validation:

- all durations must be positive
- grab hold seconds may be larger than remaining grab time; the active turn expiry is capped at `grabEndsAt`

## 8. Service API

Use a refined service interface:

```java
public interface ClocktowerPublicMicService {
    ClocktowerMicSessionView startDayMicSession(Long gameId, RbacPrincipal principal);
    ClocktowerMicSessionView getMicSession(Long gameId, RbacPrincipal principal);
    ClocktowerMicSessionView finishCurrentTurn(Long gameId, Long turnId, RbacPrincipal principal);
    ClocktowerMicSessionView skipTurn(Long gameId, Long turnId, RbacPrincipal principal);
    ClocktowerMicSessionView grabMic(Long gameId, RbacPrincipal principal);
    ClocktowerMicSessionView releaseMic(Long gameId, RbacPrincipal principal);
    ClocktowerMicSessionView extendGrabMic(Long gameId, long seconds, RbacPrincipal principal);
    ClocktowerMicSessionView closeSession(Long gameId, RbacPrincipal principal);
    boolean canSpeak(Long gameId, Long actorGameSeatId);
    void requireCanSpeak(Long gameId, Long actorGameSeatId);
}
```

Differences from the rough task note:

- Controller methods return full `ClocktowerMicSessionView` so the frontend can refresh state after every operation.
- `closeSession` is explicit because the acceptance criteria includes ST closing.
- Expiry is handled by lazy refresh on every query / mutation. A scheduler can be added later if needed, but task 05 does not need one.

## 9. DTO Design

`ClocktowerMicSessionView` should include:

- `sessionId`
- `gameId`
- `dayNo`
- `status`
- `currentHolderGameSeatId`
- `currentTurnId`
- `roundStartedAt`
- `roundFinishedAt`
- `grabStartedAt`
- `grabEndsAt`
- `closedAt`
- `turns`

`ClocktowerMicTurnView` should include:

- `turnId`
- `gameSeatId`
- `seatNo`
- `displayName`
- `actorType`
- `agentInstanceId`
- `turnOrder`
- `stage`
- `acquisitionType`
- `status`
- `startedAt`
- `endedAt`
- `expiresAt`

This is enough for task 13 to render current holder, queue, Agent labels, grab remaining time, and current turn remaining time. Per-viewer button enablement can be computed by the frontend from `mySeat`, current holder, and storyteller mode.

## 10. Eligibility Rules

A game seat is eligible for round-robin public mic if:

- `gameSeat.status = ACTIVE`
- `actorType` is `HUMAN` or `AGENT`
- metadata does not have `muted = true`
- metadata does not have `leftGame = true`

Do not require alive status. Blood on the Clocktower dead players can still speak.

HUMAN and AGENT seats both get round-robin turns. Agent seats do not use the human principal API, but they must be represented in the queue so future Agent runtime can respond when `MIC_TURN_STARTED` targets them.

## 11. State Transitions

### 11.1 Start Day Mic Session

`startDayMicSession(gameId, principal)`:

1. Require authenticated storyteller / room owner.
2. Lock the running game.
3. Require `game.status = RUNNING`.
4. Require `game.phase = DAY`.
5. Require `game.dayNo > 0`.
6. If a non-deleted session already exists for `gameId + dayNo`, refresh and return it.
7. Create a session with:
   - `status = ROUND_ROBIN`
   - `roundStartedAt = now`
8. Create PENDING round-robin turns for eligible seats in `seatNo asc`.
9. Activate the first pending turn.
10. Emit `MIC_SESSION_STARTED` and `MIC_TURN_STARTED`.

Starting the mic session does not change game phase. The new game-flow cutover remains a later task.

### 11.2 Activate Next Round-Robin Turn

When a round-robin turn needs to advance:

1. Find the next `PENDING` `ROUND_ROBIN` turn by `turnOrder`.
2. If it exists, set:
   - turn `status = ACTIVE`
   - `startedAt = now`
   - `expiresAt = now + roundRobinTurnSeconds`
   - session `currentHolderGameSeatId = turn.gameSeatId`
   - session `currentTurnId = turn.id`
3. Emit `MIC_TURN_STARTED`.
4. If no pending round-robin turn exists, enter grab-mic.

### 11.3 Finish Current Turn

`finishCurrentTurn(gameId, turnId, principal)`:

- allowed for current holder HUMAN player or storyteller
- requires the turn to be the current ACTIVE turn
- marks it `DONE`
- sets `endedAt = now`
- clears session current holder / turn
- emits `MIC_TURN_FINISHED`
- advances next round-robin turn or leaves grab-mic free, depending on session status

For a `GRAB_MIC` turn, finishing behaves like release.

### 11.4 Skip Turn

`skipTurn(gameId, turnId, principal)`:

- storyteller only
- may skip an ACTIVE or PENDING turn
- marks the turn `SKIPPED`
- sets `endedAt = now`
- if it was current, clears session current holder / turn and advances
- emits `MIC_TURN_SKIPPED`

This gives ST a manual escape hatch for Agent turns before Agent runtime exists.

### 11.5 Timeout Refresh

Every public service method first refreshes the current session:

- if the current active turn has `expiresAt <= now`, mark it `EXPIRED`
- clear current holder / turn
- emit `MIC_TURN_EXPIRED`
- if session is `ROUND_ROBIN`, activate next pending turn or enter grab-mic
- if session is `GRAB_MIC`, close the session if `grabEndsAt <= now`; otherwise leave mic free

This lazy refresh satisfies timeout behavior without a scheduler.

### 11.6 Enter Grab-Mic

When all round-robin turns are terminal:

- session `status = GRAB_MIC`
- `roundFinishedAt = now`
- `grabStartedAt = now`
- `grabEndsAt = now + grabMicTotalSeconds`
- no current holder
- emit `MIC_GRAB_OPENED`

Terminal round-robin statuses are `DONE`, `SKIPPED`, `EXPIRED`, and `CANCELLED`.

### 11.7 Grab Mic

`grabMic(gameId, principal)`:

1. Require authenticated HUMAN game player for this game.
2. Refresh session.
3. Require session `status = GRAB_MIC`.
4. Require `now < grabEndsAt`.
5. Require no current holder.
6. Create a new `GRAB_MIC` turn:
   - `acquisitionType = GRAB`
   - `status = ACTIVE`
   - `startedAt = now`
   - `expiresAt = min(now + grabMicHoldSeconds, grabEndsAt)`
   - `turnOrder = max(session.turnOrder) + 1`
7. Set current holder and current turn.
8. Emit `MIC_TURN_STARTED`.

If another holder is active, reject with `CLOCKTOWER_MIC_OCCUPIED`.

### 11.8 Release Mic

`releaseMic(gameId, principal)`:

- allowed for current holder HUMAN player or storyteller
- requires current active turn
- marks the active turn `DONE`
- clears current holder / turn
- if `grabEndsAt <= now`, closes session; otherwise stays in `GRAB_MIC`
- emits `MIC_TURN_FINISHED`

### 11.9 Extend Grab-Mic

`extendGrabMic(gameId, seconds, principal)`:

- storyteller only
- requires session status `GRAB_MIC`
- requires `seconds > 0`
- adds seconds to `grabEndsAt`
- if an active grab turn was capped by the previous `grabEndsAt`, do not automatically extend that active turn; the extension affects the total grab window only
- emits `MIC_SESSION_EXTENDED`

### 11.10 Close Session

`closeSession(gameId, principal)`:

- storyteller only
- if a turn is active, mark it `CANCELLED`
- set session `status = CLOSED`
- set `closedAt = now`
- clear current holder and current turn
- emit `MIC_SESSION_CLOSED`

Automatic closure after `grabEndsAt` uses the same final session state, but active turns expire rather than being storyteller-cancelled.

## 12. Speak Guard

`requireCanSpeak(gameId, actorGameSeatId)` enforces:

- session exists for current game day
- session status is `ROUND_ROBIN` or `GRAB_MIC`
- `currentHolderGameSeatId` equals `actorGameSeatId`
- `currentTurnId` points to an `ACTIVE` turn
- active turn `expiresAt > now`

If not allowed, throw `ClocktowerException` with `CLOCKTOWER_MIC_NOT_HOLDER` or a more specific code:

- `CLOCKTOWER_MIC_SESSION_NOT_FOUND`
- `CLOCKTOWER_MIC_CLOSED`
- `CLOCKTOWER_MIC_NOT_HOLDER`
- `CLOCKTOWER_MIC_TURN_EXPIRED`

Task 06 should call this guard before appending a `PUBLIC_SPEECH` game event. Agent actions use the same guard through the internal action executor path.

## 13. Controller API

Create `ClocktowerPublicMicController` under:

```text
be/src/main/java/top/egon/mario/clocktower/game/mic/web
```

Endpoints:

```text
GET  /api/clocktower/games/{gameId}/mic
POST /api/clocktower/games/{gameId}/mic/start-day
POST /api/clocktower/games/{gameId}/mic/turns/{turnId}/finish
POST /api/clocktower/games/{gameId}/mic/turns/{turnId}/skip
POST /api/clocktower/games/{gameId}/mic/grab
POST /api/clocktower/games/{gameId}/mic/release
POST /api/clocktower/games/{gameId}/mic/extend
POST /api/clocktower/games/{gameId}/mic/close
```

Use `ClocktowerReactiveSupport#blocking` like existing Clocktower controllers.

Request DTO:

- `ClocktowerMicExtendRequest(Long seconds)`

No browser or frontend work is part of this task.

## 14. Permissions

Service-level permissions:

- `start-day`, `skip`, `extend`, `close`: storyteller only
- `finish`, `release`: current holder HUMAN player or storyteller
- `grab`: current HUMAN game player
- `get`: any valid game viewer

Agent seats do not call principal APIs. Later Agent runtime should call internal game action services.

RBAC resources should be explicit:

- `api:clocktower:game:mic:read`
- `api:clocktower:game:mic:player`
- `api:clocktower:game:mic:storyteller`

Preset role grants:

- `CLOCKTOWER_PLAYER`: mic read + player actions
- `CLOCKTOWER_STORYTELLER`: mic read + player actions + storyteller actions

Update `ClocktowerRbacResourceProviderTests` to map the new endpoints.

## 15. Game Events

Public mic service appends `clocktower_game_event` rows for state changes:

- `MIC_SESSION_STARTED`
- `MIC_TURN_STARTED`
- `MIC_TURN_FINISHED`
- `MIC_TURN_SKIPPED`
- `MIC_TURN_EXPIRED`
- `MIC_GRAB_OPENED`
- `MIC_SESSION_EXTENDED`
- `MIC_SESSION_CLOSED`

Use:

- `visibility = PUBLIC`
- `actorGameSeatId = current holder` when the event is holder-specific
- `payload` with `sessionId`, `turnId`, `gameSeatId`, `stage`, `status`, and timing fields relevant to that event

These events are not a substitute for the mic session API. They exist so existing game event streams can notify clients and future Agent runtime can react to `MIC_TURN_STARTED`, `MIC_GRAB_OPENED`, and `MIC_SESSION_CLOSED`.

## 16. Error Codes

Use project-style `ClocktowerException` codes:

- `CLOCKTOWER_GAME_NOT_FOUND`
- `CLOCKTOWER_GAME_NOT_RUNNING`
- `CLOCKTOWER_MIC_GAME_PHASE_INVALID`
- `CLOCKTOWER_MIC_NO_ELIGIBLE_SEATS`
- `CLOCKTOWER_MIC_SESSION_NOT_FOUND`
- `CLOCKTOWER_MIC_SESSION_CLOSED`
- `CLOCKTOWER_MIC_TURN_NOT_FOUND`
- `CLOCKTOWER_MIC_TURN_INVALID`
- `CLOCKTOWER_MIC_NOT_HOLDER`
- `CLOCKTOWER_MIC_OCCUPIED`
- `CLOCKTOWER_MIC_GRAB_NOT_OPEN`
- `CLOCKTOWER_MIC_GRAB_EXPIRED`
- `CLOCKTOWER_MIC_EXTEND_SECONDS_INVALID`
- `CLOCKTOWER_MIC_STORYTELLER_FORBIDDEN`
- `CLOCKTOWER_MIC_PLAYER_REQUIRED`
- `CLOCKTOWER_MIC_AGENT_PRINCIPAL_FORBIDDEN`

When the difference is user-visible, prefer specific codes. When the issue is malformed persisted state, use `CLOCKTOWER_MIC_TURN_INVALID`.

## 17. Tests

Add `ClocktowerPublicMicServiceTests`:

1. `startDayMicSessionCreatesRoundRobinTurnsInSeatOrder`
   - Create or reuse a started game.
   - Set it to `DAY` and `dayNo = 1`.
   - Start mic session.
   - Assert turns are in `seatNo asc`, first turn is ACTIVE, others PENDING.

2. `requireCanSpeakWithoutMicRejected`
   - Call `requireCanSpeak` before a session exists.
   - Expect `CLOCKTOWER_MIC_SESSION_NOT_FOUND`.

3. `requireCanSpeakRejectsNonHolder`
   - Start session.
   - Assert first holder can speak.
   - Assert second seat is rejected with `CLOCKTOWER_MIC_NOT_HOLDER`.

4. `finishTurnActivatesNextTurn`
   - Finish active first turn.
   - Assert first is DONE and second is ACTIVE.

5. `allRoundRobinTurnsDoneEntersGrabMic`
   - Finish or skip all round-robin turns.
   - Assert session status `GRAB_MIC`, `grabStartedAt` and `grabEndsAt` are set.

6. `grabMicWhenFreeSuccess`
   - Enter grab phase.
   - Human player grabs.
   - Assert active grab turn with expiry capped by `grabEndsAt`.

7. `grabMicWhenOccupiedRejected`
   - Have one player grab.
   - Another player attempts to grab.
   - Expect `CLOCKTOWER_MIC_OCCUPIED`.

8. `grabMicAfterFiveMinutesRejected`
   - Move session past `grabEndsAt`.
   - Call grab or get.
   - Assert session closes and grab is rejected.

9. `storytellerCanSkipCurrentTurn`
   - ST skips active turn.
   - Assert current advances.

10. `storytellerCanExtendAndCloseGrabMic`
    - Enter grab phase.
    - Extend by 120 seconds.
    - Close session.
    - Assert final status CLOSED.

11. `agentSeatsJoinRoundRobinButCannotGrabViaPrincipal`
    - Start a game with one human and Agent seats.
    - Assert Agent seats have round-robin turns.
    - Assert no principal can grab as an Agent seat.

Extend existing tests:

- `ClocktowerSchemaMigrationTests` should cover `clocktower_game_public_mic_session` and `clocktower_game_public_mic_turn`.
- `ClocktowerRbacResourceProviderTests` should include the mic endpoint mappings and role grants.

Validation commands after implementation:

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerPublicMicServiceTests,ClocktowerSchemaMigrationTests,ClocktowerRbacResourceProviderTests test
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerRoomRefactorServiceTests,ClocktowerAgentSeatServiceTests,ClocktowerGameLifecycleServiceTests,ClocktowerPublicMicServiceTests test
git diff --check
```

## 18. Rollout Boundary

After task 05:

- Backend can create and manage public mic sessions.
- New game action executor from task 06 can call `requireCanSpeak`.
- Agent runtime from later tasks can observe mic events and respond when its Agent game seat holds the mic.
- Frontend task 13 can render mic state from the session view and game events.

The old room action endpoint remains unchanged until the later cutover task.

## 19. Self-Review

Placeholder scan:

- No placeholder sections or unspecified implementation choices remain.

Internal consistency:

- The design consistently keeps task 05 independent from old room actions and from task 06's executor.
- The service state machine matches the database statuses.

Scope check:

- This is one backend subsystem: schema, service, controller, RBAC, and tests for public mic. It is appropriate for a single implementation plan.

Ambiguity check:

- DAY phase handling is explicit: `startDayMicSession` requires `game.phase = DAY` and does not transition the game.
- Expiry handling is explicit: lazy refresh on service calls, no scheduler in this task.
- Agent behavior is explicit: Agent seats are queued and guarded, but Agent runtime actions are deferred.
