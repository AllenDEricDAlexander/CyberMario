# Clocktower Start Game Agent Lifecycle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let legal system Agent seats participate in Clocktower game start while preserving human-only IM membership and binding Agent instances to the created game seats.

**Architecture:** Keep `ClocktowerGameLifecycleServiceImpl` as the start-game orchestrator and add a strict Agent-seat branch beside the existing human-seat validation. Copy actor identity into immutable game seats, then bind matching `ClocktowerAgentInstancePo` rows after game seats are flushed so the instance can reference the generated `game_seat_id`.

**Tech Stack:** Spring Boot, Spring Data JPA, Hibernate JSON mapping, Lombok constructor injection, JUnit 5, AssertJ, Jackson `ObjectMapper`, Maven.

---

## Scope Check

This plan implements `docs/superpowers/specs/2026-07-08-clocktower-start-game-agent-lifecycle-design.md`.

It depends on task 03 being present in this worktree:

- Room creation can create trailing system Agent seats through `agentSeatCount`.
- `ClocktowerRoomSeatPo` already has `actorId`, `actorType`, and `agentInstanceId`.
- `ClocktowerGameSeatPo` already has `actorId`, `actorType`, and `agentInstanceId`.
- `ClocktowerAgentInstanceRepository` already exposes `findByIdAndDeletedFalse`.

This plan does not:

- Add or edit Flyway migrations.
- Add frontend behavior.
- Add Agent runtime decisions, action execution, chat participation, or model calls.
- Add a new Strategy, Factory, or Domain Service abstraction. The variation is one start-game validation branch, and the existing lifecycle service already owns this orchestration.

## File Structure

Modify:

- `be/src/test/java/top/egon/mario/clocktower/game/ClocktowerGameLifecycleServiceTests.java`
  - Add focused TDD coverage for Agent start success, actor-field snapshots, Agent instance binding, manual metadata rejection, Agent-with-user rejection, human-only IM members, and `GAME_STARTED` payload counts.
- `be/src/main/java/top/egon/mario/clocktower/game/service/ClocktowerGameLifecycleServiceImpl.java`
  - Import Agent constants and repository.
  - Validate humans and strict system Agent seats separately.
  - Copy actor fields from room seats to game seats.
  - Flush game seats, bind Agent instances to `game_id` and `game_seat_id`, and mark them `ACTIVE`.
  - Pass only human user ids to IM activation.
  - Enrich `GAME_STARTED` event payload.

## Task 1: Failing Game Lifecycle Agent Tests

**Files:**

- Modify: `be/src/test/java/top/egon/mario/clocktower/game/ClocktowerGameLifecycleServiceTests.java`

- [ ] **Step 1: Add imports**

Add these imports near the top of the file:

```java
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import top.egon.mario.clocktower.agent.constant.ClocktowerActorType;
import top.egon.mario.clocktower.agent.constant.ClocktowerAgentStatus;
import top.egon.mario.clocktower.agent.po.ClocktowerAgentInstancePo;
import top.egon.mario.clocktower.agent.repository.ClocktowerAgentInstanceRepository;
import top.egon.mario.clocktower.game.po.ClocktowerGameEventPo;
import top.egon.mario.clocktower.game.repository.ClocktowerGameEventRepository;
import top.egon.mario.im.po.ImConversationMemberPo;
import top.egon.mario.im.repository.ImConversationMemberRepository;
import java.util.Map;
```

- [ ] **Step 2: Add JSON type reference and dependencies**

Add this field below `ROLE_CODES`:

```java
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
```

Add these dependencies after the existing repository fields:

```java
    @Autowired
    private ClocktowerGameEventRepository gameEventRepository;

    @Autowired
    private ClocktowerAgentInstanceRepository agentInstanceRepository;

    @Autowired
    private ImConversationMemberRepository conversationMemberRepository;

    @Autowired
    private ObjectMapper objectMapper;
```

- [ ] **Step 3: Add failing Agent lifecycle tests**

Add these tests after `startGameActivatesGameConversations`:

```java
    @Test
    void startGameWithAgentSeatsSuccess() {
        ClocktowerRoomResponse room = agentRoom();

        ClocktowerGameResponse response = gameService.startGame(room.roomId(), owner());

        assertThat(response.status()).isEqualTo("RUNNING");
        ClocktowerRoomProfilePo profile = profileRepository.findByRoomId(room.roomId()).orElseThrow();
        assertThat(profile.getStatus()).isEqualTo("IN_GAME");
        assertThat(profile.getCurrentGameId()).isEqualTo(response.gameId());
        assertThat(gameSeatRepository.findByGameIdOrderBySeatNoAsc(response.gameId())).hasSize(5);
    }

    @Test
    void startGameCopiesActorFieldsToGameSeat() {
        ClocktowerRoomResponse room = agentRoom();

        ClocktowerGameResponse response = gameService.startGame(room.roomId(), owner());

        List<ClocktowerGameSeatPo> gameSeats = gameSeatRepository.findByGameIdOrderBySeatNoAsc(response.gameId());
        assertThat(gameSeats.get(0))
                .extracting(ClocktowerGameSeatPo::getActorType, ClocktowerGameSeatPo::getActorId,
                        ClocktowerGameSeatPo::getAgentInstanceId, ClocktowerGameSeatPo::getUserId)
                .containsExactly(ClocktowerActorType.HUMAN, null, null, 11L);
        assertThat(gameSeats.subList(1, 5)).allSatisfy(seat -> {
            assertThat(seat.getActorType()).isEqualTo(ClocktowerActorType.AGENT);
            assertThat(seat.getActorId()).isNotNull();
            assertThat(seat.getAgentInstanceId()).isNotNull();
            assertThat(seat.getUserId()).isNull();
        });
    }

    @Test
    void startGameUpdatesAgentInstances() {
        ClocktowerRoomResponse room = agentRoom();

        ClocktowerGameResponse response = gameService.startGame(room.roomId(), owner());

        List<ClocktowerGameSeatPo> agentSeats = gameSeatRepository.findByGameIdOrderBySeatNoAsc(response.gameId())
                .stream()
                .filter(seat -> ClocktowerActorType.AGENT.equals(seat.getActorType()))
                .toList();
        assertThat(agentSeats).hasSize(4);
        for (ClocktowerGameSeatPo gameSeat : agentSeats) {
            ClocktowerAgentInstancePo instance = agentInstanceRepository
                    .findByIdAndDeletedFalse(gameSeat.getAgentInstanceId())
                    .orElseThrow();
            assertThat(instance.getGameId()).isEqualTo(response.gameId());
            assertThat(instance.getGameSeatId()).isEqualTo(gameSeat.getId());
            assertThat(instance.getRoomSeatId()).isEqualTo(gameSeat.getRoomSeatId());
            assertThat(instance.getActorId()).isEqualTo(gameSeat.getActorId());
            assertThat(instance.getStatus()).isEqualTo(ClocktowerAgentStatus.ACTIVE);
        }
    }

    @Test
    void startGameManualAgentMetadataRejected() {
        Long roomId = readyRoom();
        ClocktowerRoomSeatPo seat = roomSeatRepository.findByRoomIdAndSeatNo(roomId, 1).orElseThrow();
        seat.setMetadataJson("{\"ready\":true,\"agent\":true,\"agentSeat\":true,"
                + "\"systemManaged\":true,\"createdBy\":\"agentSeatCount\"}");
        roomSeatRepository.saveAndFlush(seat);

        assertStartRejected(roomId, "CLOCKTOWER_GAME_SEAT_INVALID");

        assertThat(gameRepository.findByRoomIdAndDeletedFalseOrderByGameNoAsc(roomId)).isEmpty();
    }

    @Test
    void startGameAgentSeatWithUserIdRejected() {
        ClocktowerRoomResponse room = agentRoom();
        ClocktowerRoomSeatPo agentSeat = roomSeatRepository.findByRoomIdAndSeatNo(room.roomId(), 2).orElseThrow();
        agentSeat.setUserId(22L);
        roomSeatRepository.saveAndFlush(agentSeat);

        assertStartRejected(room.roomId(), "CLOCKTOWER_GAME_SEAT_INVALID");

        assertThat(gameRepository.findByRoomIdAndDeletedFalseOrderByGameNoAsc(room.roomId())).isEmpty();
    }

    @Test
    void startGameFiltersAgentFromImMembers() {
        ClocktowerRoomResponse room = agentRoom();

        ClocktowerGameResponse response = gameService.startGame(room.roomId(), owner());

        ClocktowerGameConversationResponse publicConversation = conversation(response,
                ClocktowerChatConstants.GROUP_PUBLIC, ClocktowerChatConstants.CONVERSATION_PUBLIC);
        assertThat(conversationMemberRepository
                .findByConversationIdAndDeletedFalse(publicConversation.conversationId()))
                .extracting(ImConversationMemberPo::getUserId)
                .containsExactlyInAnyOrder(1L, 11L);
    }

    @Test
    void startGameEventPayloadIncludesAgentCounts() throws Exception {
        ClocktowerRoomResponse room = agentRoom();

        ClocktowerGameResponse response = gameService.startGame(room.roomId(), owner());

        List<ClocktowerGameEventPo> events = gameEventRepository
                .findByGameIdAndStatusAndDeletedFalseOrderByEventSeqAsc(response.gameId(), "VISIBLE");
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getEventType()).isEqualTo("GAME_STARTED");
        Map<String, Object> payload = objectMapper.readValue(events.get(0).getPayloadJson(), MAP_TYPE);
        assertThat(payload)
                .containsEntry("roomId", room.roomId().intValue())
                .containsEntry("gameNo", 1)
                .containsEntry("humanSeatCount", 1)
                .containsEntry("agentSeatCount", 4)
                .containsEntry("agentMode", "HEURISTIC_V0");
    }
```

- [ ] **Step 4: Add Agent room and conversation helpers**

Replace the existing `readyRoom()` helper with this version:

```java
    private Long readyRoom() {
        ClocktowerRoomResponse room = roomService.createRoom(createRequest(), owner());
        claimHumanSeats(room.roomId(), 1, ROLE_CODES.size());
        assignReadyRoles(room.roomId());
        return room.roomId();
    }
```

Add these helpers below `readyRoom()`:

```java
    private ClocktowerRoomResponse agentRoom() {
        ClocktowerRoomResponse room = roomService.createRoom(createRequest(4), owner());
        roomService.claimSeat(room.roomId(), 1, new ClocktowerSeatClaimRequest("Player 1"),
                principal(11L, "player1"));
        assignReadyRoles(room.roomId());
        return room;
    }

    private void claimHumanSeats(Long roomId, int firstSeatNo, int lastSeatNo) {
        for (int seatNo = firstSeatNo; seatNo <= lastSeatNo; seatNo++) {
            roomService.claimSeat(roomId, seatNo, new ClocktowerSeatClaimRequest("Player " + seatNo),
                    principal(10L + seatNo, "player" + seatNo));
        }
    }

    private void assignReadyRoles(Long roomId) {
        List<ClocktowerRoomSeatPo> seats = roomSeatRepository.findByRoomIdOrderBySeatNoAsc(roomId);
        for (int index = 0; index < seats.size(); index++) {
            ClocktowerRoomSeatPo seat = seats.get(index);
            seat.setRoleCode(ROLE_CODES.get(index));
            seat.setMetadataJson(readyMetadata(seat.getMetadataJson()));
        }
        roomSeatRepository.saveAllAndFlush(seats);
    }

    private String readyMetadata(String metadataJson) {
        if (metadataJson != null && metadataJson.contains("\"agentSeat\":true")) {
            return metadataJson;
        }
        return "{\"ready\":true}";
    }

    private ClocktowerGameConversationResponse conversation(ClocktowerGameResponse response, String groupKey,
                                                           String conversationType) {
        return response.conversations().stream()
                .filter(conversation -> groupKey.equals(conversation.groupKey())
                        && conversationType.equals(conversation.conversationType()))
                .findFirst()
                .orElseThrow();
    }
```

Replace the existing `createRequest()` helper with this overload pair:

```java
    private ClocktowerRoomCreateRequest createRequest() {
        return createRequest(0);
    }

    private ClocktowerRoomCreateRequest createRequest(int agentSeatCount) {
        return new ClocktowerRoomCreateRequest(
                "Friday Clocktower",
                ClocktowerScriptCode.TROUBLE_BREWING,
                ROLE_CODES.size(),
                null,
                null,
                ROLE_CODES,
                "HUMAN",
                true,
                true,
                agentSeatCount,
                "PUBLIC",
                "OPEN_SEATING"
        );
    }
```

- [ ] **Step 5: Run focused lifecycle tests and verify RED**

Run:

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerGameLifecycleServiceTests test
```

Expected: tests compile, and the new Agent start tests fail because current start-game validation still requires every start seat to have a human `userId` and rejects `metadata.agent=true`.

- [ ] **Step 6: Commit the failing tests**

```bash
git add be/src/test/java/top/egon/mario/clocktower/game/ClocktowerGameLifecycleServiceTests.java
git commit -m "test: cover clocktower start game agent lifecycle"
```

## Task 2: Implement Agent-Aware Start Game Lifecycle

**Files:**

- Modify: `be/src/main/java/top/egon/mario/clocktower/game/service/ClocktowerGameLifecycleServiceImpl.java`

- [ ] **Step 1: Add Agent imports**

Add these imports:

```java
import top.egon.mario.clocktower.agent.constant.ClocktowerActorType;
import top.egon.mario.clocktower.agent.constant.ClocktowerAgentStatus;
import top.egon.mario.clocktower.agent.po.ClocktowerAgentInstancePo;
import top.egon.mario.clocktower.agent.repository.ClocktowerAgentInstanceRepository;
```

- [ ] **Step 2: Add Agent constants and repository dependency**

Add these constants after `ACTION_ARCHIVE`:

```java
    private static final String AGENT_MODE_NONE = "NONE";
    private static final String AGENT_POLICY_HEURISTIC_V0 = "HEURISTIC_V0";
    private static final String CREATED_BY_AGENT_SEAT_COUNT = "agentSeatCount";
```

Add this constructor-injected field after `private final ClocktowerGameSeatRepository gameSeatRepository;`:

```java
    private final ClocktowerAgentInstanceRepository agentInstanceRepository;
```

- [ ] **Step 3: Update `startGame` orchestration**

Replace this block:

```java
        List<ClocktowerGameSeatPo> gameSeats = seats.stream()
                .map(seat -> gameSeat(savedGame.getId(), seat, rolesByCode.get(seat.getRoleCode())))
                .toList();
        gameSeatRepository.saveAll(gameSeats);
```

with:

```java
        List<ClocktowerGameSeatPo> gameSeats = seats.stream()
                .map(seat -> gameSeat(savedGame.getId(), seat, rolesByCode.get(seat.getRoleCode())))
                .toList();
        List<ClocktowerGameSeatPo> savedGameSeats = gameSeatRepository.saveAllAndFlush(gameSeats);
        bindAgentInstances(savedGame, savedGameSeats);
```

Replace the event and IM activation block:

```java
        appendGameEvent(savedGame, "GAME_STARTED", now, Map.of("roomId", roomId, "gameNo", savedGame.getGameNo()));
        List<ClocktowerGameConversationResponse> conversations = activateGameConversations(savedGame.getId(),
                principal.userId(), seats.stream().map(ClocktowerRoomSeatPo::getUserId).toList());
```

with:

```java
        appendGameEvent(savedGame, "GAME_STARTED", now, gameStartedPayload(roomId, savedGame.getGameNo(), seats));
        List<ClocktowerGameConversationResponse> conversations = activateGameConversations(savedGame.getId(),
                principal.userId(), humanUserIds(seats));
```

- [ ] **Step 4: Split seat validation into human and Agent branches**

Replace the existing `validateStartSeats` method with:

```java
    private void validateStartSeats(ClocktowerRoomProfilePo profile, List<ClocktowerRoomSeatPo> seats,
                                    Map<String, ClocktowerRolePo> rolesByCode) {
        for (ClocktowerRoomSeatPo seat : seats) {
            Map<String, Object> metadata = metadata(seat.getMetadataJson());
            if (isSystemAgentSeat(seat, metadata)) {
                validateAgentStartSeat(seat, metadata);
            } else {
                validateHumanStartSeat(profile, seat, metadata);
            }
            validateRoleAssignment(profile, seat, rolesByCode);
        }
    }
```

Add these helper methods below `validateStartSeats`:

```java
    private void validateHumanStartSeat(ClocktowerRoomProfilePo profile, ClocktowerRoomSeatPo seat,
                                        Map<String, Object> metadata) {
        if (seat.getUserId() == null || !SEAT_STATUS_OCCUPIED.equals(seat.getStatus())
                || !realUser(metadata)) {
            throw new ClocktowerException("CLOCKTOWER_GAME_SEAT_INVALID");
        }
        if (Objects.equals(profile.getStorytellerUserId(), seat.getUserId())) {
            throw new ClocktowerException("CLOCKTOWER_STORYTELLER_CANNOT_PLAY");
        }
        if (!ready(metadata)) {
            throw new ClocktowerException("CLOCKTOWER_GAME_SEAT_NOT_READY");
        }
    }

    private void validateAgentStartSeat(ClocktowerRoomSeatPo seat, Map<String, Object> metadata) {
        if (seat.getUserId() != null || !SEAT_STATUS_OCCUPIED.equals(seat.getStatus())
                || !ClocktowerActorType.AGENT.equals(seat.getActorType())
                || seat.getActorId() == null || seat.getAgentInstanceId() == null) {
            throw new ClocktowerException("CLOCKTOWER_GAME_SEAT_INVALID");
        }
        if (!ready(metadata)) {
            throw new ClocktowerException("CLOCKTOWER_GAME_SEAT_NOT_READY");
        }
    }

    private void validateRoleAssignment(ClocktowerRoomProfilePo profile, ClocktowerRoomSeatPo seat,
                                        Map<String, ClocktowerRolePo> rolesByCode) {
        if (!StringUtils.hasText(seat.getRoleCode())) {
            throw new ClocktowerException("CLOCKTOWER_GAME_ROLE_REQUIRED");
        }
        ClocktowerRolePo role = rolesByCode.get(seat.getRoleCode());
        if (role == null || !profile.getScriptCode().equals(role.getScriptCode().name())) {
            throw new ClocktowerException("CLOCKTOWER_ASSIGNMENT_INVALID");
        }
    }
```

- [ ] **Step 5: Add strict Agent-seat predicate**

Add this method below `ready`:

```java
    private boolean isSystemAgentSeat(ClocktowerRoomSeatPo seat, Map<String, Object> metadata) {
        return ClocktowerActorType.AGENT.equals(seat.getActorType())
                && seat.getActorId() != null
                && seat.getAgentInstanceId() != null
                && Boolean.TRUE.equals(metadata.get("agentSeat"))
                && Boolean.TRUE.equals(metadata.get("systemManaged"))
                && CREATED_BY_AGENT_SEAT_COUNT.equals(String.valueOf(metadata.get("createdBy")));
    }
```

This deliberately does not treat `metadata.agent=true` alone as legal Agent evidence. A manually edited human seat with only Agent-looking metadata must still go through `validateHumanStartSeat` and fail `realUser`.

- [ ] **Step 6: Copy actor fields into immutable game seats**

Add these lines in `gameSeat(...)` after `seat.setUserId(roomSeat.getUserId());`:

```java
        seat.setActorId(roomSeat.getActorId());
        seat.setActorType(StringUtils.hasText(roomSeat.getActorType())
                ? roomSeat.getActorType() : ClocktowerActorType.HUMAN);
        seat.setAgentInstanceId(roomSeat.getAgentInstanceId());
```

- [ ] **Step 7: Bind Agent instances after game seats are flushed**

Add these methods below `gameSeat(...)`:

```java
    private void bindAgentInstances(ClocktowerGamePo game, List<ClocktowerGameSeatPo> gameSeats) {
        for (ClocktowerGameSeatPo gameSeat : gameSeats) {
            if (!ClocktowerActorType.AGENT.equals(gameSeat.getActorType())) {
                continue;
            }
            ClocktowerAgentInstancePo instance = agentInstanceRepository
                    .findByIdAndDeletedFalse(gameSeat.getAgentInstanceId())
                    .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_GAME_SEAT_INVALID"));
            if (!Objects.equals(instance.getRoomId(), game.getRoomId())
                    || !Objects.equals(instance.getRoomSeatId(), gameSeat.getRoomSeatId())
                    || !Objects.equals(instance.getActorId(), gameSeat.getActorId())) {
                throw new ClocktowerException("CLOCKTOWER_GAME_SEAT_INVALID");
            }
            instance.setGameId(game.getId());
            instance.setGameSeatId(gameSeat.getId());
            instance.setStatus(ClocktowerAgentStatus.ACTIVE);
            agentInstanceRepository.save(instance);
        }
    }
```

- [ ] **Step 8: Add human-user filtering and game-start payload helpers**

Add these methods below `bindAgentInstances(...)`:

```java
    private List<Long> humanUserIds(List<ClocktowerRoomSeatPo> seats) {
        return seats.stream()
                .filter(seat -> !ClocktowerActorType.AGENT.equals(seat.getActorType()))
                .map(ClocktowerRoomSeatPo::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private Map<String, Object> gameStartedPayload(Long roomId, int gameNo, List<ClocktowerRoomSeatPo> seats) {
        long agentSeatCount = seats.stream()
                .filter(seat -> isSystemAgentSeat(seat, metadata(seat.getMetadataJson())))
                .count();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("roomId", roomId);
        payload.put("gameNo", gameNo);
        payload.put("humanSeatCount", seats.size() - agentSeatCount);
        payload.put("agentSeatCount", agentSeatCount);
        payload.put("agentMode", agentSeatCount > 0 ? AGENT_POLICY_HEURISTIC_V0 : AGENT_MODE_NONE);
        return payload;
    }
```

- [ ] **Step 9: Run focused lifecycle tests and verify GREEN**

Run:

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerGameLifecycleServiceTests test
```

Expected: all tests in `ClocktowerGameLifecycleServiceTests` pass.

- [ ] **Step 10: Commit implementation**

```bash
git add be/src/main/java/top/egon/mario/clocktower/game/service/ClocktowerGameLifecycleServiceImpl.java
git commit -m "feat: start clocktower games with agent seats"
```

## Task 3: Regression Validation

**Files:**

- Validate: `be/src/test/java/top/egon/mario/clocktower/room/ClocktowerRoomRefactorServiceTests.java`
- Validate: `be/src/test/java/top/egon/mario/clocktower/agent/ClocktowerAgentSeatServiceTests.java`
- Validate: `be/src/test/java/top/egon/mario/clocktower/game/ClocktowerGameLifecycleServiceTests.java`

- [ ] **Step 1: Run combined room, Agent seat, and game lifecycle tests**

Run:

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerRoomRefactorServiceTests,ClocktowerAgentSeatServiceTests,ClocktowerGameLifecycleServiceTests test
```

Expected: all selected tests pass.

- [ ] **Step 2: Run whitespace validation**

Run:

```bash
git diff --check
```

Expected: no output and exit code `0`.

- [ ] **Step 3: Inspect changed files**

Run:

```bash
git status --short
git diff --stat HEAD
```

Expected: only the lifecycle service and lifecycle test have uncommitted changes after Task 2 validation, or the worktree is clean if Task 2 was already committed.

- [ ] **Step 4: Commit validation fixes only if needed**

If validation required a small correction after the implementation commit, commit only that correction:

```bash
git add be/src/test/java/top/egon/mario/clocktower/game/ClocktowerGameLifecycleServiceTests.java \
  be/src/main/java/top/egon/mario/clocktower/game/service/ClocktowerGameLifecycleServiceImpl.java
git commit -m "test: stabilize clocktower agent start lifecycle"
```

If no correction was needed, do not create an empty commit.

## Self-Review

Spec coverage:

- Legal Agent seats can pass start-game validation through `isSystemAgentSeat` and `validateAgentStartSeat`.
- Manual metadata-only Agent spoofing fails through the human validation branch.
- Agent seats with `userId` fail through `validateAgentStartSeat`.
- Actor fields are copied to `ClocktowerGameSeatPo`.
- Agent instances are bound to `game_id` and `game_seat_id` after game seats are flushed.
- IM activation receives only human user ids.
- `GAME_STARTED` payload includes `humanSeatCount`, `agentSeatCount`, and `agentMode`.
- Existing fake-user and storyteller guards stay in the human validation branch.

Placeholder scan:

- The plan contains exact files, method names, code blocks, commands, expected outcomes, and commit messages.

Type consistency:

- Actor constants use `ClocktowerActorType.HUMAN` and `ClocktowerActorType.AGENT`.
- Agent status uses `ClocktowerAgentStatus.ACTIVE`.
- Repositories and DTO names match the current worktree files.
