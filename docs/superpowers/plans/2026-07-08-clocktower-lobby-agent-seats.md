# Clocktower Lobby Agent Seats Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `ClocktowerRoomCreateRequest.agentSeatCount` create trailing system Agent seats during lobby room creation.

**Architecture:** Keep `ClocktowerRoomLobbyServiceImpl` as the room-creation orchestrator and delegate Agent Actor / Agent Instance creation to `ClocktowerAgentSeatService`. Extend the backend seat response DTO so newly created Agent seats are visible through the existing room response without changing frontend start-game behavior in this task.

**Tech Stack:** Spring Boot, Spring Data JPA, Hibernate JSON mapping, Lombok constructor injection, JUnit 5, AssertJ, Maven.

---

## Scope Check

This plan implements `docs/superpowers/specs/2026-07-08-clocktower-lobby-agent-seats-design.md`.

It depends on task 02 already being present in this worktree:

- `top.egon.mario.clocktower.agent.constant.ClocktowerActorType`
- `top.egon.mario.clocktower.agent.constant.ClocktowerAgentAutoMode`
- `top.egon.mario.clocktower.agent.service.ClocktowerAgentSeatService`
- Actor fields on `ClocktowerRoomSeatPo`

This plan does not:

- Add a Flyway migration.
- Change game start validation.
- Copy Agent room seats to game seats.
- Change frontend start-room logic.
- Add Agent chat membership.
- Implement Agent runtime behavior.

## File Structure

Modify:

- `be/src/test/java/top/egon/mario/clocktower/room/ClocktowerRoomRefactorServiceTests.java`
  - Add TDD coverage for zero Agent seats, trailing Agent seat creation, invalid Agent count, no-user Agent seats, backend response fields, and public conversation membership.
- `be/src/main/java/top/egon/mario/clocktower/room/service/impl/ClocktowerRoomLobbyServiceImpl.java`
  - Inject `ClocktowerAgentSeatService`.
  - Validate `agentSeatCount`.
  - Convert trailing seats through `createAgentForRoomSeat`.
- `be/src/main/java/top/egon/mario/clocktower/room/dto/response/ClocktowerSeatResponse.java`
  - Add `actorId`, `actorType`, `agentInstanceId`, and `isAgent` response fields.

## Task 1: Failing Room Creation Tests

**Files:**

- Modify: `be/src/test/java/top/egon/mario/clocktower/room/ClocktowerRoomRefactorServiceTests.java`

- [ ] **Step 1: Add imports and repository dependency**

Add these imports:

```java
import top.egon.mario.clocktower.agent.constant.ClocktowerActorType;
import top.egon.mario.im.po.ImConversationMemberPo;
import top.egon.mario.im.repository.ImConversationMemberRepository;
```

Add the repository field after `private ClocktowerRoomSeatRepository seatRepository;`:

```java
    @Autowired
    private ImConversationMemberRepository conversationMemberRepository;
```

- [ ] **Step 2: Add failing tests**

Add these tests after `createRoomCreatesGenericRoomProfileSeatDraftAndRoomPublicConversation`:

```java
    @Test
    void createRoomAgentSeatCountZeroKeepsAllSeatsOpen() {
        ClocktowerRoomResponse room = roomService.createRoom(createRequest("OPEN_SEATING", 0),
                principal(1L, "mario"));

        assertThat(seatRepository.findByRoomIdOrderBySeatNoAsc(room.roomId()))
                .extracting(ClocktowerRoomSeatPo::getStatus, ClocktowerRoomSeatPo::getActorType,
                        ClocktowerRoomSeatPo::getActorId, ClocktowerRoomSeatPo::getAgentInstanceId)
                .containsExactly(
                        tuple("OPEN", ClocktowerActorType.HUMAN, null, null),
                        tuple("OPEN", ClocktowerActorType.HUMAN, null, null),
                        tuple("OPEN", ClocktowerActorType.HUMAN, null, null),
                        tuple("OPEN", ClocktowerActorType.HUMAN, null, null),
                        tuple("OPEN", ClocktowerActorType.HUMAN, null, null)
                );
        assertThat(room.seats())
                .extracting(ClocktowerSeatResponse::actorType, ClocktowerSeatResponse::actorId,
                        ClocktowerSeatResponse::agentInstanceId, ClocktowerSeatResponse::isAgent,
                        ClocktowerSeatResponse::ready)
                .containsOnly(tuple(ClocktowerActorType.HUMAN, null, null, false, false));
    }

    @Test
    void createRoomAgentSeatCountFourCreatesTrailingAgentSeats() {
        ClocktowerRoomResponse room = roomService.createRoom(createRequest("OPEN_SEATING", 4),
                principal(1L, "mario"));

        List<ClocktowerRoomSeatPo> seats = seatRepository.findByRoomIdOrderBySeatNoAsc(room.roomId());
        assertThat(seats).hasSize(5);
        assertThat(seats.get(0))
                .extracting(ClocktowerRoomSeatPo::getSeatNo, ClocktowerRoomSeatPo::getStatus,
                        ClocktowerRoomSeatPo::getActorType, ClocktowerRoomSeatPo::getActorId,
                        ClocktowerRoomSeatPo::getAgentInstanceId)
                .containsExactly(1, "OPEN", ClocktowerActorType.HUMAN, null, null);
        assertThat(seats.subList(1, 5)).allSatisfy(seat -> {
            assertThat(seat.getStatus()).isEqualTo("OCCUPIED");
            assertThat(seat.getActorType()).isEqualTo(ClocktowerActorType.AGENT);
            assertThat(seat.getActorId()).isNotNull();
            assertThat(seat.getAgentInstanceId()).isNotNull();
            assertThat(seat.getUserId()).isNull();
            assertThat(seat.getMetadataJson()).contains("\"ready\":true");
            assertThat(seat.getMetadataJson()).contains("\"agentSeat\":true");
            assertThat(seat.getMetadataJson()).contains("\"systemManaged\":true");
            assertThat(seat.getMetadataJson()).contains("\"agentPolicy\":\"HEURISTIC_V0\"");
            assertThat(seat.getMetadataJson()).contains("\"autoMode\":\"FULL_AUTO\"");
            assertThat(seat.getMetadataJson()).contains("\"createdBy\":\"agentSeatCount\"");
        });
        assertThat(seats.subList(1, 5))
                .extracting(ClocktowerRoomSeatPo::getDisplayName)
                .containsExactly("Agent 1", "Agent 2", "Agent 3", "Agent 4");

        assertThat(room.seats().get(0).isAgent()).isFalse();
        assertThat(room.seats().subList(1, 5)).allSatisfy(seat -> {
            assertThat(seat.actorType()).isEqualTo(ClocktowerActorType.AGENT);
            assertThat(seat.actorId()).isNotNull();
            assertThat(seat.agentInstanceId()).isNotNull();
            assertThat(seat.isAgent()).isTrue();
            assertThat(seat.ready()).isTrue();
            assertThat(seat.status()).isEqualTo("OCCUPIED");
        });
    }

    @Test
    void createRoomAgentSeatCountTooLargeRejected() {
        long roomCount = roomSpaceRepository.count();

        assertThatThrownBy(() -> roomService.createRoom(createRequest("OPEN_SEATING", 5),
                principal(1L, "mario")))
                .isInstanceOf(ClocktowerException.class)
                .hasMessageContaining("CLOCKTOWER_AGENT_SEAT_COUNT_INVALID");

        assertThat(roomSpaceRepository.count()).isEqualTo(roomCount);
    }

    @Test
    void createRoomAgentSeatsHaveNoUserId() {
        ClocktowerRoomResponse room = roomService.createRoom(createRequest("OPEN_SEATING", 4),
                principal(1L, "mario"));

        assertThat(seatRepository.findByRoomIdOrderBySeatNoAsc(room.roomId()).subList(1, 5))
                .extracting(ClocktowerRoomSeatPo::getUserId)
                .containsOnlyNulls();
    }

    @Test
    void createRoomAgentSeatsDoNotJoinPublicConversation() {
        ClocktowerRoomResponse room = roomService.createRoom(createRequest("OPEN_SEATING", 4),
                principal(1L, "mario"));

        assertThat(conversationMemberRepository.findByConversationIdAndDeletedFalse(room.publicConversationId()))
                .extracting(ImConversationMemberPo::getUserId)
                .containsExactly(1L);
    }
```

- [ ] **Step 3: Add a request helper overload**

Replace the existing helper:

```java
    private static ClocktowerRoomCreateRequest createRequest(String seatingPolicy) {
        return new ClocktowerRoomCreateRequest(
                "Friday Clocktower",
                ClocktowerScriptCode.TROUBLE_BREWING,
                5,
                null,
                null,
                roleCodes(),
                "HUMAN",
                true,
                true,
                0,
                "PUBLIC",
                seatingPolicy
        );
    }
```

with:

```java
    private static ClocktowerRoomCreateRequest createRequest(String seatingPolicy) {
        return createRequest(seatingPolicy, 0);
    }

    private static ClocktowerRoomCreateRequest createRequest(String seatingPolicy, int agentSeatCount) {
        return new ClocktowerRoomCreateRequest(
                "Friday Clocktower",
                ClocktowerScriptCode.TROUBLE_BREWING,
                5,
                null,
                null,
                roleCodes(),
                "HUMAN",
                true,
                true,
                agentSeatCount,
                "PUBLIC",
                seatingPolicy
        );
    }
```

- [ ] **Step 4: Run the focused room test and verify RED**

Run:

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerRoomRefactorServiceTests test
```

Expected result:

```text
Compilation failure
invalid method reference
cannot find symbol: method actorType()
cannot find symbol: method actorId()
cannot find symbol: method agentInstanceId()
cannot find symbol: method isAgent()
```

## Task 2: Backend Seat Response Fields

**Files:**

- Modify: `be/src/main/java/top/egon/mario/clocktower/room/dto/response/ClocktowerSeatResponse.java`

- [ ] **Step 1: Add Actor type import**

Add:

```java
import top.egon.mario.clocktower.agent.constant.ClocktowerActorType;
```

- [ ] **Step 2: Extend the record and factory methods**

Replace the current record with:

```java
public record ClocktowerSeatResponse(
        Long seatId,
        int seatNo,
        Long userId,
        Long actorId,
        String actorType,
        Long agentInstanceId,
        boolean isAgent,
        String displayName,
        String roleCode,
        ClocktowerRoleType roleType,
        String lifeStatus,
        String publicLifeStatus,
        boolean connected,
        boolean hasDeadVote,
        String status,
        boolean ready
) {

    public static ClocktowerSeatResponse from(ClocktowerSeatPo seat) {
        return new ClocktowerSeatResponse(seat.getId(), seat.getSeatNo(), seat.getUserId(), null,
                ClocktowerActorType.HUMAN, null, false, seat.getDisplayName(), seat.getRoleCode(),
                seat.getRoleType(), seat.getLifeStatus(), seat.getPublicLifeStatus(), seat.isConnected(),
                seat.isHasDeadVote(), seat.getUserId() == null ? "OPEN" : "OCCUPIED", false);
    }

    public static ClocktowerSeatResponse publicView(ClocktowerSeatPo seat) {
        return new ClocktowerSeatResponse(seat.getId(), seat.getSeatNo(), seat.getUserId(), null,
                ClocktowerActorType.HUMAN, null, false, seat.getDisplayName(), null, null,
                seat.getPublicLifeStatus(), seat.getPublicLifeStatus(), seat.isConnected(),
                seat.isHasDeadVote(), seat.getUserId() == null ? "OPEN" : "OCCUPIED", false);
    }

    public static ClocktowerSeatResponse from(ClocktowerRoomSeatPo seat, boolean ready) {
        String actorType = actorType(seat.getActorType());
        boolean agent = ClocktowerActorType.AGENT.equals(actorType) || seat.getAgentInstanceId() != null;
        return new ClocktowerSeatResponse(seat.getId(), seat.getSeatNo(), seat.getUserId(), seat.getActorId(),
                actorType, seat.getAgentInstanceId(), agent, seat.getDisplayName(), seat.getRoleCode(), null,
                null, null, seat.getUserId() != null, false, seat.getStatus(), ready);
    }

    private static String actorType(String actorType) {
        return actorType == null ? ClocktowerActorType.HUMAN : actorType;
    }
}
```

- [ ] **Step 3: Run the focused room test and verify RED moves to service behavior**

Run:

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerRoomRefactorServiceTests test
```

Expected result:

```text
Failures:
createRoomAgentSeatCountFourCreatesTrailingAgentSeats
expected: "OCCUPIED"
 but was: "OPEN"
```

The exact first failure line can vary, but the test must compile and fail because room creation still ignores `agentSeatCount`.

## Task 3: Create Trailing Agent Seats in Lobby Room Creation

**Files:**

- Modify: `be/src/main/java/top/egon/mario/clocktower/room/service/impl/ClocktowerRoomLobbyServiceImpl.java`

- [ ] **Step 1: Add imports**

Add:

```java
import top.egon.mario.clocktower.agent.constant.ClocktowerAgentAutoMode;
import top.egon.mario.clocktower.agent.service.ClocktowerAgentSeatService;
```

- [ ] **Step 2: Add default Agent profile constant and service dependency**

Add the constant after `INVITATION_TYPE_SEAT_REQUEST`:

```java
    private static final String DEFAULT_AGENT_PROFILE_NAME = "balanced";
```

Add the dependency after `private final ClocktowerRoomSeatRepository seatRepository;`:

```java
    private final ClocktowerAgentSeatService agentSeatService;
```

- [ ] **Step 3: Consume `agentSeatCount` in `createRoom`**

Replace:

```java
        String resolvedSeatingPolicy = seatingPolicy(request.seatingPolicy());
```

with:

```java
        String resolvedSeatingPolicy = seatingPolicy(request.seatingPolicy());
        int agentSeatCount = requireAgentSeatCount(request.agentSeatCount(), playerCount);
```

Replace the seat creation loop:

```java
        for (int seatNo = 1; seatNo <= playerCount; seatNo++) {
            String roleCode = seatNo <= roleCodes.size() ? roleCodes.get(seatNo - 1) : null;
            seatRepository.save(openSeat(room.getId(), seatNo, roleCode));
        }
```

with:

```java
        int firstAgentSeatNo = playerCount - agentSeatCount + 1;
        int agentIndex = 0;
        for (int seatNo = 1; seatNo <= playerCount; seatNo++) {
            String roleCode = seatNo <= roleCodes.size() ? roleCodes.get(seatNo - 1) : null;
            ClocktowerRoomSeatPo seat = seatRepository.save(openSeat(room.getId(), seatNo, roleCode));
            if (seatNo >= firstAgentSeatNo) {
                agentIndex++;
                agentSeatService.createAgentForRoomSeat(room.getId(), seat.getId(), seatNo,
                        "Agent " + agentIndex, roleCode, DEFAULT_AGENT_PROFILE_NAME,
                        ClocktowerAgentAutoMode.FULL_AUTO);
            }
        }
```

When `agentSeatCount` is zero, `firstAgentSeatNo` is `playerCount + 1`, so no seat enters the Agent branch.

- [ ] **Step 4: Add validation helper**

Add after `requirePlayerCount`:

```java
    private int requireAgentSeatCount(int requested, int playerCount) {
        if (requested < 0) {
            throw new ClocktowerException("CLOCKTOWER_AGENT_SEAT_COUNT_INVALID",
                    "Agent 座位数不能小于 0");
        }
        if (requested >= playerCount) {
            throw new ClocktowerException("CLOCKTOWER_AGENT_SEAT_COUNT_INVALID",
                    "至少需要保留 1 个真人玩家座位");
        }
        return requested;
    }
```

- [ ] **Step 5: Run the focused room test and verify GREEN**

Run:

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerRoomRefactorServiceTests test
```

Expected result:

```text
BUILD SUCCESS
```

## Task 4: Regression Validation and Commit

**Files:**

- No additional files unless validation finds a task-scoped defect.

- [ ] **Step 1: Run Agent seat service regression**

Run:

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerAgentSeatServiceTests test
```

Expected result:

```text
BUILD SUCCESS
```

- [ ] **Step 2: Run game lifecycle regression**

Run:

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerGameLifecycleServiceTests test
```

Expected result:

```text
BUILD SUCCESS
```

- [ ] **Step 3: Run combined targeted suite**

Run:

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerRoomRefactorServiceTests,ClocktowerAgentSeatServiceTests,ClocktowerGameLifecycleServiceTests test
```

Expected result:

```text
BUILD SUCCESS
```

- [ ] **Step 4: Check whitespace**

Run:

```bash
git diff --check
```

Expected result: no output and exit code 0.

- [ ] **Step 5: Review changed files**

Run:

```bash
git diff --stat
git diff -- be/src/test/java/top/egon/mario/clocktower/room/ClocktowerRoomRefactorServiceTests.java
git diff -- be/src/main/java/top/egon/mario/clocktower/room/service/impl/ClocktowerRoomLobbyServiceImpl.java
git diff -- be/src/main/java/top/egon/mario/clocktower/room/dto/response/ClocktowerSeatResponse.java
```

Expected result: only task 03 files changed, no Flyway migration changed, no frontend files changed.

- [ ] **Step 6: Commit implementation**

Run:

```bash
git add be/src/test/java/top/egon/mario/clocktower/room/ClocktowerRoomRefactorServiceTests.java \
  be/src/main/java/top/egon/mario/clocktower/room/service/impl/ClocktowerRoomLobbyServiceImpl.java \
  be/src/main/java/top/egon/mario/clocktower/room/dto/response/ClocktowerSeatResponse.java
git commit -m "feat: create clocktower lobby agent seats"
```

Expected result: implementation commit created on `codex/clocktower-actor-agent-foundation`.

## Self-Review

- Spec coverage: agent count validation, trailing Agent seat creation, no-user Agent seats, backend response fields, no IM membership, zero-count compatibility, and no frontend/game-start changes are all covered.
- Placeholder scan: no task uses TBD/TODO/fill-in wording.
- Type consistency: test method references match the planned `ClocktowerSeatResponse` record components: `actorId`, `actorType`, `agentInstanceId`, and `isAgent`.
- Scope: no existing Flyway migration is edited, and no new migration is needed because task 01 already added schema fields.

## Execution Mode

The user confirmed approach A and asked for inline execution in this task stream. Execute this plan in the current worktree using `superpowers:executing-plans`.
