# Clocktower Start Game Agent Lifecycle Design

**Date:** 2026-07-08

**Status:** User-approved approach A. This spec covers only task 04 from `docs/clocktower_agent_tasks`: allow legal Agent room seats to pass start-game validation, copy Actor fields into game seats, bind Agent Instances to the new game seats, and keep Agent seats out of IM conversation membership. Implementation must be planned separately before code changes.

---

## 1. Background

Task 03 made `ClocktowerRoomCreateRequest.agentSeatCount` create trailing system-managed Agent room seats. Those seats are persisted with:

- `actor_type = AGENT`
- non-null `actor_id`
- non-null `agent_instance_id`
- `user_id = null`
- `status = OCCUPIED`
- metadata containing `ready = true`, `agentSeat = true`, `systemManaged = true`, and `createdBy = agentSeatCount`

The current game start flow in `ClocktowerGameLifecycleServiceImpl#startGame` still assumes every player seat is a real logged-in user. Its validation rejects `userId == null`, rejects metadata with `agent = true`, and only accepts human player metadata. That remains correct for human seats, but it blocks the legal Agent seats created by task 03.

## 2. Goals

1. Allow room creation results from task 03, such as one human player plus four Agent seats, to start a game.
2. Keep existing human-player validation unchanged for real players.
3. Reject hand-crafted fake or Agent-like metadata that is not backed by the task 01 / 02 Actor and Agent Instance fields.
4. Copy `actorId`, `actorType`, and `agentInstanceId` from room seats to game seats.
5. Bind each Agent Instance to the newly created game and game seat.
6. Activate game IM conversations only for real users: storyteller / owner and human player users.
7. Add `GAME_STARTED` event payload fields needed by later Agent runtime tasks.
8. Add focused tests for Agent start lifecycle behavior and negative cases.

## 3. Non-Goals

This task does not:

- Implement Agent automatic actions.
- Add the action executor or Agent task queue.
- Add public mic / round-robin speaking.
- Change night task execution.
- Change frontend lobby or game UI.
- Add a Flyway migration.
- Add Agent users to IM membership.

## 4. Selected Approach

Use approach A: keep the start-game lifecycle in `ClocktowerGameLifecycleServiceImpl`, add explicit human / Agent validation branches, and update the existing game-seat creation path.

This keeps the change close to the lifecycle invariant that is being modified: a room seat becomes an immutable game seat snapshot. It also avoids stretching `ClocktowerAgentSeatService` into a game-start orchestration service before later runtime tasks define broader Agent behavior.

Alternative approaches were rejected:

- Moving all start-game Agent logic into `ClocktowerAgentSeatService` would blur responsibility. That service currently owns room-seat Agent creation, not game lifecycle orchestration.
- Simply allowing `metadata.agent = true` or `userId = null` would let manually edited room seats masquerade as legal Agents.
- Adding a new Strategy or Factory layer would be heavier than the current variation point requires. Small validation helpers and one Agent Instance update helper are enough.

## 5. Start Seat Validation

Refactor `validateStartSeats` so every room seat is validated as either a legal system Agent seat or a legal human seat.

The first gate is shared:

- If metadata has `fake = true`, reject with `CLOCKTOWER_GAME_SEAT_INVALID`.

Then:

- If the seat satisfies the legal Agent predicate, validate it with Agent rules.
- Otherwise validate it with the existing human rules.

### 5.1 Legal Agent Predicate

A legal Agent room seat must satisfy all of these:

- `actorType = AGENT`
- `actorId != null`
- `agentInstanceId != null`
- metadata has `systemManaged = true`
- metadata has `agentSeat = true`
- metadata has `createdBy = agentSeatCount`

Do not use only `metadata.agent = true`; that field is not sufficient proof that the Agent was created by the supported backend path.

### 5.2 Human Seat Rules

Human seats must keep current behavior:

- `userId != null`
- `status = OCCUPIED`
- `userId` is not the storyteller user id
- metadata has `ready = true`
- `roleCode` is present
- metadata `playerType` is absent or equals `HUMAN`
- metadata must not contain `agent = true`

Failures keep the current error codes:

- invalid seat shape: `CLOCKTOWER_GAME_SEAT_INVALID`
- storyteller-as-player: `CLOCKTOWER_STORYTELLER_CANNOT_PLAY`
- unready seat: `CLOCKTOWER_GAME_SEAT_NOT_READY`
- missing role: `CLOCKTOWER_GAME_ROLE_REQUIRED`
- invalid assignment: `CLOCKTOWER_ASSIGNMENT_INVALID`

### 5.3 Agent Seat Rules

Agent seats must satisfy:

- `userId == null`
- `status = OCCUPIED`
- metadata has `ready = true`
- `roleCode` is present
- `actorId != null`
- `actorType = AGENT`
- `agentInstanceId != null`

Failures should use the same public error codes as comparable human failures:

- malformed Agent seat: `CLOCKTOWER_GAME_SEAT_INVALID`
- unready Agent seat: `CLOCKTOWER_GAME_SEAT_NOT_READY`
- missing Agent role: `CLOCKTOWER_GAME_ROLE_REQUIRED`
- invalid role assignment: `CLOCKTOWER_ASSIGNMENT_INVALID`

## 6. Game Seat Snapshot

When converting a `ClocktowerRoomSeatPo` to `ClocktowerGameSeatPo`, copy the Actor fields:

```java
gameSeat.setActorId(roomSeat.getActorId());
gameSeat.setActorType(roomSeat.getActorType());
gameSeat.setAgentInstanceId(roomSeat.getAgentInstanceId());
```

Continue copying `userId` as-is. Human game seats keep a real `userId`; Agent game seats keep `userId = null`.

The snapshot remains immutable with respect to later room-seat edits, matching the existing game-seat behavior.

## 7. Agent Instance Binding

After saving the game seats, bind Agent Instances to the new game:

- Find game seats whose `actorType = AGENT` and `agentInstanceId != null`.
- Load matching `ClocktowerAgentInstancePo` rows by id.
- Verify the instance belongs to the same room, same room seat, and same actor.
- Set:
  - `gameId = savedGame.id`
  - `gameSeatId = savedGameSeat.id`
  - `status = ACTIVE`

If an Agent game seat references a missing or mismatched Agent Instance, fail the start with `CLOCKTOWER_GAME_SEAT_INVALID`. The transaction should roll back, leaving the room in lobby state.

The repository already has `findByIdAndDeletedFalse`, so no new repository method is required.

## 8. IM Membership

`activateGameConversations` should receive only real human player user ids:

- Include room seats where `actorType` is not `AGENT`.
- Include only non-null `userId`.
- Deduplicate ids.

The storyteller / owner still joins as the owner principal used by `ClocktowerImAdapter`.

Agent seats must not be added to `im_conversation_member` because IM membership represents real users and Agent seats have no login user.

## 9. Game Started Event Payload

Change the `GAME_STARTED` event payload from the current minimal payload to include:

- `roomId`
- `gameNo`
- `humanSeatCount`
- `agentSeatCount`
- `agentMode`

`agentMode` should be:

- `HEURISTIC_V0` when `agentSeatCount > 0`
- `NONE` when no Agent seats exist

This is an event contract for later Agent scheduler tasks. It does not start any runtime work in this task.

## 10. Tests

Extend `ClocktowerGameLifecycleServiceTests`:

1. `startGameWithAgentSeatsSuccess`
   - Create a five-seat room with `agentSeatCount = 4`.
   - Claim the remaining human seat.
   - Start the game.
   - Assert the game starts and has five game seats.

2. `startGameCopiesActorFieldsToGameSeat`
   - Assert Agent game seats have `actorType = AGENT`, non-null `actorId`, non-null `agentInstanceId`, and `userId = null`.
   - Assert the human game seat remains `actorType = HUMAN`, no Agent Instance, and real `userId`.

3. `startGameUpdatesAgentInstances`
   - Assert each Agent Instance has `gameId` and `gameSeatId` after start.

4. `startGameManualAgentMetadataRejected`
   - Mutate a human room seat to metadata with `ready = true` and `agent = true` but without Actor / Agent Instance fields.
   - Assert start is rejected with `CLOCKTOWER_GAME_SEAT_INVALID`.

5. `startGameAgentSeatWithUserIdRejected`
   - Set a legal Agent seat `userId` to a real user id.
   - Assert start is rejected with `CLOCKTOWER_GAME_SEAT_INVALID`.

6. `startGameFiltersAgentFromImMembers`
   - Start a room with one human and four Agents.
   - Locate the game public conversation from the response.
   - Assert `im_conversation_member` contains the storyteller / owner and the human player only.

7. `startGameEventPayloadIncludesAgentCounts`
   - Read visible game events.
   - Assert the `GAME_STARTED` payload contains `humanSeatCount = 1`, `agentSeatCount = 4`, and `agentMode = HEURISTIC_V0`.

Regression coverage should keep the existing tests:

- fake seats rejected
- unready seats rejected
- storyteller-as-player rejected
- all-human room start unchanged
- game conversations still created

## 11. Validation

Run at minimum:

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerGameLifecycleServiceTests test
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerRoomRefactorServiceTests,ClocktowerAgentSeatServiceTests,ClocktowerGameLifecycleServiceTests test
git diff --check
```

If IM membership assertions require additional coverage, include the relevant Clocktower chat tests in the final targeted suite.

## 12. Spec Self-Review

- Placeholder scan: no TBD or TODO entries.
- Internal consistency: validation, snapshot copy, Agent Instance binding, IM filtering, and event payload all describe the same lifecycle boundary.
- Scope check: this is one backend lifecycle task and does not require decomposition.
- Ambiguity check: legal Agent seats are explicitly defined by both Actor fields and system-managed metadata; hand-crafted `metadata.agent = true` is not accepted.
