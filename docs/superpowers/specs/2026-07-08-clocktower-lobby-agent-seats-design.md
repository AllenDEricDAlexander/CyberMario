# Clocktower Lobby Agent Seats Design

**Date:** 2026-07-08

**Status:** User-approved approach A. This spec covers only task 03 from `docs/clocktower_agent_tasks`: consume `agentSeatCount` during room creation, create trailing Agent room seats through the task 02 Agent seat service, and expose Agent identity fields in the backend room-seat response. Implementation must be planned separately before code changes.

---

## 1. Background

Task 01 added first-class Actor / Agent database fields to Clocktower seats. Task 02 added Java mappings, repositories, and `ClocktowerAgentSeatService`, which can create an Agent Actor, Agent Instance, and bind them to an existing room seat.

The current lobby room creation flow still ignores `ClocktowerRoomCreateRequest.agentSeatCount`. `ClocktowerRoomLobbyServiceImpl#createRoom` validates the script and player count, creates a room profile, creates every seat as an open human seat, and creates the public conversation for the owner. This means the frontend field for Agent seat count is accepted by the request model but has no backend effect.

## 2. Goals

1. Make `agentSeatCount` effective during room creation.
2. Create the last N room seats as system-managed Agent seats.
3. Keep at least one human player seat in every created room.
4. Reuse `ClocktowerAgentSeatService` as the single owner of Agent Actor / Agent Instance creation and room-seat binding.
5. Keep Agent seats out of IM conversation membership.
6. Add backend response fields that allow clients to distinguish open human seats from occupied Agent seats.
7. Preserve existing behavior when `agentSeatCount` is zero.
8. Add focused backend tests for zero Agent seats, trailing Agent seat creation, invalid counts, no-user Agent seats, response fields, and IM membership.

## 3. Non-Goals

This task does not:

- Start games with Agent seats.
- Copy Agent fields from room seats to game seats.
- Change game start validation.
- Implement Agent runtime, decisions, task queues, memory, LLM policy, public mic, nominations, votes, or night actions.
- Add Agent seats to public chat or private chat membership.
- Change the frontend start-room rule. A later frontend task can treat `actorType == AGENT` or `isAgent == true` as a valid occupied player.
- Add a new Flyway migration.

## 4. Selected Approach

Use approach A: keep room creation as the lifecycle orchestrator and delegate Agent row creation to `ClocktowerAgentSeatService`.

The lobby service will validate `agentSeatCount`, create room seats in seat-number order, and route only the trailing seats through the Agent seat service. This keeps the task narrow and avoids duplicating the Agent metadata, Actor, and Agent Instance creation rules that task 02 already centralized.

Alternative approaches were rejected:

- Creating Agent Actor and Agent Instance rows directly in `ClocktowerRoomLobbyServiceImpl` would duplicate task 02 service logic and create two sources of truth for Agent seat metadata.
- Deferring all response fields to a frontend task would leave the backend API unable to describe the newly created Agent seats.
- Updating frontend start validation in this task would cross into the later UI / game-start readiness boundary.
- Introducing a Strategy, Factory, or Builder would add abstraction without a current variation point. The existing domain service is already the appropriate seam.

## 5. Room Creation Rules

Add a small validation helper in `ClocktowerRoomLobbyServiceImpl`:

```java
private int requireAgentSeatCount(int requested, int playerCount) {
    if (requested < 0) {
        throw new ClocktowerException("CLOCKTOWER_AGENT_SEAT_COUNT_INVALID", "Agent 座位数不能小于 0");
    }
    if (requested >= playerCount) {
        throw new ClocktowerException("CLOCKTOWER_AGENT_SEAT_COUNT_INVALID", "至少需要保留 1 个真人玩家座位");
    }
    return requested;
}
```

`ClocktowerRoomCreateRequest.agentSeatCount` is currently a primitive `int`, so null handling is not needed.

For `playerCount = 5` and `agentSeatCount = 4`:

- Seat 1 remains an open human seat.
- Seats 2, 3, 4, and 5 become occupied Agent seats.

The first Agent seat number is:

```java
int firstAgentSeatNo = playerCount - agentSeatCount + 1;
```

## 6. Agent Seat Creation

For each seat:

1. Create and save the normal open room seat first, so it has a database id.
2. If the seat number is in the trailing Agent range, call:

```java
agentSeatService.createAgentForRoomSeat(
        room.getId(),
        seat.getId(),
        seatNo,
        "Agent " + agentIndex,
        roleCode,
        "balanced",
        ClocktowerAgentAutoMode.FULL_AUTO
);
```

The Agent seat service remains responsible for:

- Creating `clocktower_actor`.
- Creating `clocktower_agent_instance`.
- Setting `actor_type = AGENT`.
- Setting `actor_id`.
- Setting `agent_instance_id`.
- Setting `user_id = null`.
- Setting `status = OCCUPIED`.
- Writing the Agent seat metadata, including `ready = true`, `agent = true`, `agentSeat = true`, `systemManaged = true`, `agentPolicy = HEURISTIC_V0`, and `autoMode = FULL_AUTO`.

The lobby service should not pre-build or duplicate the Agent metadata JSON.

## 7. Backend Response Contract

Extend `ClocktowerSeatResponse` for room seats with:

- `actorId`
- `actorType`
- `agentInstanceId`
- `isAgent`

`ready` already exists and should continue to be derived from room-seat metadata.

For room seats:

- `actorId` comes from `ClocktowerRoomSeatPo.actorId`.
- `actorType` comes from `ClocktowerRoomSeatPo.actorType`.
- `agentInstanceId` comes from `ClocktowerRoomSeatPo.agentInstanceId`.
- `isAgent` is true when `actorType` is `AGENT` or `agentInstanceId` is not null.

For legacy game-seat response conversion paths that do not have actor fields, keep the response human-compatible:

- `actorId = null`
- `actorType = HUMAN`
- `agentInstanceId = null`
- `isAgent = false`

Do not change the meaning of `connected`; it should continue to represent a real connected user, so Agent seats with `userId = null` remain not connected until a later task adds richer UI semantics.

## 8. IM Membership Boundary

Room creation should continue creating the public conversation for the room owner. Agent seats do not have a `userId` and must not be added to `im_conversation_member`.

The task does not require any chat service changes if the existing creation flow only adds the owner. Tests should assert the conversation membership count stays unchanged by Agent seat creation.

## 9. Tests

Add or extend room lobby tests around `ClocktowerRoomLobbyServiceImpl#createRoom`:

1. `createRoom_agentSeatCountZero_keepsAllSeatsOpen`
   - Create a five-player room with `agentSeatCount = 0`.
   - Assert all five seats remain `OPEN`.
   - Assert all seats are human-compatible and have no Agent instance.

2. `createRoom_agentSeatCountFour_createsFourAgentSeats`
   - Create a five-player room with `agentSeatCount = 4`.
   - Assert seat 1 is `OPEN`, `actorType = HUMAN`, `agentInstanceId = null`.
   - Assert seats 2-5 are `OCCUPIED`, `actorType = AGENT`, `actorId != null`, `agentInstanceId != null`, `userId = null`, and metadata has `ready = true`.
   - Assert response seats expose the same Agent fields and `isAgent = true`.

3. `createRoom_agentSeatCountTooLarge_rejected`
   - Create a five-player room with `agentSeatCount = 5`.
   - Assert `ClocktowerException` with code `CLOCKTOWER_AGENT_SEAT_COUNT_INVALID`.

4. `createRoom_agentSeatsHaveNoUserId`
   - Create a five-player room with `agentSeatCount = 4`.
   - Assert every Agent seat has `userId = null`.

5. `createRoom_agentSeatsDoNotJoinPublicConversation`
   - Create a five-player room with `agentSeatCount = 4`.
   - Assert the public conversation has only the owner membership created by the existing flow.

Run at minimum:

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerRoomRefactorServiceTests,ClocktowerAgentSeatServiceTests test
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerGameLifecycleServiceTests test
git diff --check
```

If IM membership assertions touch chat repositories or service helpers, also run the relevant chat test class.
