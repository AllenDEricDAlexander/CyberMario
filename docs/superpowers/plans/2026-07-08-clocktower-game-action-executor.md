# Clocktower Game Action Executor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the new `gameId + gameSeatId` Clocktower action executor and API for task 06, with shared HUMAN and AGENT action execution for public speech and mic-turn actions.

**Architecture:** Add a focused `top.egon.mario.clocktower.game.action` module. Human and Agent application services authenticate different caller types, then pass a normalized `GameActionCommand` plus `ActorContext` into one executor. The executor owns game/seat/action validation and appends `clocktower_game_event` records through a shared game-event appender.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring MVC/WebFlux controller wrappers, Spring Data JPA, H2/Flyway test database, JUnit 5, AssertJ.

---

## File Structure

Create:

- `be/src/main/java/top/egon/mario/clocktower/game/action/dto/ClocktowerGameActionRequest.java` - external action request.
- `be/src/main/java/top/egon/mario/clocktower/game/action/dto/ClocktowerGameActionResponse.java` - action response with accepted/rejected state.
- `be/src/main/java/top/egon/mario/clocktower/game/action/dto/GameActionCommand.java` - normalized executor command.
- `be/src/main/java/top/egon/mario/clocktower/game/action/dto/ActorContext.java` - authenticated actor identity.
- `be/src/main/java/top/egon/mario/clocktower/game/action/dto/AvailableGameAction.java` - internal action metadata.
- `be/src/main/java/top/egon/mario/clocktower/game/action/service/ClocktowerGameActionExecutor.java` - shared executor API.
- `be/src/main/java/top/egon/mario/clocktower/game/action/service/ClocktowerHumanGameActionService.java` - HTTP/player entry service API.
- `be/src/main/java/top/egon/mario/clocktower/game/action/service/ClocktowerAgentGameActionService.java` - Agent runtime entry service API.
- `be/src/main/java/top/egon/mario/clocktower/game/action/service/impl/ClocktowerHumanGameActionServiceImpl.java` - principal-to-HUMAN-seat gate.
- `be/src/main/java/top/egon/mario/clocktower/game/action/service/impl/ClocktowerAgentGameActionServiceImpl.java` - agent-instance-to-AGENT-seat gate.
- `be/src/main/java/top/egon/mario/clocktower/game/action/service/impl/ClocktowerGameActionExecutorImpl.java` - v1 action execution.
- `be/src/main/java/top/egon/mario/clocktower/game/action/web/ClocktowerGameActionController.java` - new HTTP endpoint.
- `be/src/main/java/top/egon/mario/clocktower/game/service/ClocktowerGameEventAppender.java` - shared `clocktower_game_event` writer.
- `be/src/test/java/top/egon/mario/clocktower/game/action/ClocktowerGameActionServiceTests.java` - integration tests for task 06.

Modify:

- `be/src/main/java/top/egon/mario/clocktower/game/repository/ClocktowerGameSeatRepository.java` - add focused lookup methods.
- `be/src/main/java/top/egon/mario/clocktower/game/mic/service/ClocktowerPublicMicService.java` - add actor-based finish method.
- `be/src/main/java/top/egon/mario/clocktower/game/mic/service/impl/ClocktowerPublicMicServiceImpl.java` - implement actor-based finish and reuse event appender.
- `be/src/test/java/top/egon/mario/clocktower/game/mic/ClocktowerPublicMicServiceTests.java` - add actor-based finish coverage.

Do not modify:

- `be/src/main/resources/db/migration/*.sql`
- `be/src/main/java/top/egon/mario/clocktower/action/**`
- frontend files

---

### Task 1: Action Contracts And Entry Gates

**Files:**
- Create: `be/src/main/java/top/egon/mario/clocktower/game/action/dto/ClocktowerGameActionRequest.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/game/action/dto/ClocktowerGameActionResponse.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/game/action/dto/GameActionCommand.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/game/action/dto/ActorContext.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/game/action/dto/AvailableGameAction.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/game/action/service/ClocktowerGameActionExecutor.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/game/action/service/ClocktowerHumanGameActionService.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/game/action/service/ClocktowerAgentGameActionService.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/game/action/service/impl/ClocktowerHumanGameActionServiceImpl.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/game/action/service/impl/ClocktowerAgentGameActionServiceImpl.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/game/repository/ClocktowerGameSeatRepository.java`
- Test: `be/src/test/java/top/egon/mario/clocktower/game/action/ClocktowerGameActionServiceTests.java`

- [ ] **Step 1: Write failing identity-gate tests**

Create `ClocktowerGameActionServiceTests` with the Spring test skeleton below. Keep the helper methods local to the new test class so the test remains self-contained.

```java
package top.egon.mario.clocktower.game.action;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.clocktower.agent.constant.ClocktowerAgentAutoMode;
import top.egon.mario.clocktower.agent.po.ClocktowerAgentInstancePo;
import top.egon.mario.clocktower.agent.repository.ClocktowerAgentInstanceRepository;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.game.action.dto.ClocktowerGameActionRequest;
import top.egon.mario.clocktower.game.action.dto.ClocktowerGameActionResponse;
import top.egon.mario.clocktower.game.action.service.ClocktowerAgentGameActionService;
import top.egon.mario.clocktower.game.action.service.ClocktowerHumanGameActionService;
import top.egon.mario.clocktower.game.dto.ClocktowerGameResponse;
import top.egon.mario.clocktower.game.po.ClocktowerGamePo;
import top.egon.mario.clocktower.game.po.ClocktowerGameSeatPo;
import top.egon.mario.clocktower.game.po.ClocktowerRoomSeatPo;
import top.egon.mario.clocktower.game.repository.ClocktowerGameEventRepository;
import top.egon.mario.clocktower.game.repository.ClocktowerGameRepository;
import top.egon.mario.clocktower.game.repository.ClocktowerGameSeatRepository;
import top.egon.mario.clocktower.game.service.ClocktowerGameLifecycleService;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomCreateRequest;
import top.egon.mario.clocktower.room.dto.request.ClocktowerSeatClaimRequest;
import top.egon.mario.clocktower.room.dto.response.ClocktowerRoomResponse;
import top.egon.mario.clocktower.room.repository.ClocktowerRoomSeatRepository;
import top.egon.mario.clocktower.room.service.ClocktowerRoomLobbyService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
@Transactional
class ClocktowerGameActionServiceTests {

    private static final List<String> ROLE_CODES = List.of("EMPATH", "CHEF", "MONK", "POISONER", "IMP");

    @Autowired
    private ClocktowerHumanGameActionService humanActionService;

    @Autowired
    private ClocktowerAgentGameActionService agentActionService;

    @Autowired
    private ClocktowerGameLifecycleService gameService;

    @Autowired
    private ClocktowerRoomLobbyService roomService;

    @Autowired
    private ClocktowerRoomSeatRepository roomSeatRepository;

    @Autowired
    private ClocktowerGameRepository gameRepository;

    @Autowired
    private ClocktowerGameSeatRepository gameSeatRepository;

    @Autowired
    private ClocktowerGameEventRepository gameEventRepository;

    @Autowired
    private ClocktowerAgentInstanceRepository agentInstanceRepository;

    @Test
    void humanSubmitAction_requiresOwnGameSeat() {
        StartedGame game = startDayGameWithAgents();
        ClocktowerGameSeatPo humanSeat = game.seats().getFirst();
        ClocktowerGameSeatPo agentSeat = game.seats().get(1);

        ClocktowerGameActionResponse ownSeat = humanActionService.submit(game.gameId(),
                new ClocktowerGameActionRequest(humanSeat.getId(), "NOMINATE", List.of(agentSeat.getId()),
                        null, null, null, Map.of()),
                principal(11L, "player1"));

        assertThat(ownSeat.accepted()).isFalse();
        assertThat(ownSeat.rejectedCode()).isEqualTo("CLOCKTOWER_ACTION_NOT_IMPLEMENTED");
        assertThatThrownBy(() -> humanActionService.submit(game.gameId(),
                new ClocktowerGameActionRequest(agentSeat.getId(), "NOMINATE", List.of(humanSeat.getId()),
                        null, null, null, Map.of()),
                principal(11L, "player1")))
                .isInstanceOf(ClocktowerException.class)
                .hasMessageContaining("CLOCKTOWER_GAME_ACTION_SEAT_FORBIDDEN");
    }

    @Test
    void humanSubmitAction_cannotActAsAgent() {
        StartedGame game = startDayGameWithAgents();
        ClocktowerGameSeatPo agentSeat = game.seats().get(1);

        assertThatThrownBy(() -> humanActionService.submit(game.gameId(),
                new ClocktowerGameActionRequest(agentSeat.getId(), "PUBLIC_SPEECH", List.of(),
                        null, null, "agent words", Map.of()),
                principal(11L, "player1")))
                .isInstanceOf(ClocktowerException.class)
                .hasMessageContaining("CLOCKTOWER_GAME_ACTION_SEAT_FORBIDDEN");
    }

    @Test
    void agentSubmitAction_requiresAgentInstanceGameSeatMatch() {
        StartedGame game = startDayGameWithAgents();
        ClocktowerGameSeatPo humanSeat = game.seats().getFirst();
        ClocktowerGameSeatPo agentSeat = game.seats().get(1);
        ClocktowerAgentInstancePo instance = agentInstanceRepository
                .findByGameSeatIdAndDeletedFalse(agentSeat.getId())
                .orElseThrow();

        ClocktowerGameActionResponse ownSeat = agentActionService.submitAgentAction(game.gameId(), instance.getId(),
                new ClocktowerGameActionRequest(agentSeat.getId(), "NOMINATE", List.of(humanSeat.getId()),
                        null, null, null, Map.of()));

        assertThat(ownSeat.accepted()).isFalse();
        assertThat(ownSeat.rejectedCode()).isEqualTo("CLOCKTOWER_ACTION_NOT_IMPLEMENTED");
        assertThatThrownBy(() -> agentActionService.submitAgentAction(game.gameId(), instance.getId(),
                new ClocktowerGameActionRequest(humanSeat.getId(), "NOMINATE", List.of(agentSeat.getId()),
                        null, null, null, Map.of())))
                .isInstanceOf(ClocktowerException.class)
                .hasMessageContaining("CLOCKTOWER_AGENT_GAME_SEAT_MISMATCH");
    }

    @Test
    void agentSubmitAction_rejectsPausedAutoMode() {
        StartedGame game = startDayGameWithAgents();
        ClocktowerGameSeatPo agentSeat = game.seats().get(1);
        ClocktowerAgentInstancePo instance = agentInstanceRepository
                .findByGameSeatIdAndDeletedFalse(agentSeat.getId())
                .orElseThrow();
        instance.setAutoMode(ClocktowerAgentAutoMode.PAUSED);
        agentInstanceRepository.saveAndFlush(instance);

        assertThatThrownBy(() -> agentActionService.submitAgentAction(game.gameId(), instance.getId(),
                new ClocktowerGameActionRequest(agentSeat.getId(), "NOMINATE", List.of(),
                        null, null, null, Map.of())))
                .isInstanceOf(ClocktowerException.class)
                .hasMessageContaining("CLOCKTOWER_AGENT_AUTO_MODE_PAUSED");
    }

    private StartedGame startDayGameWithAgents() {
        ClocktowerRoomResponse room = roomService.createRoom(createRequest(4), owner());
        roomService.claimSeat(room.roomId(), 1, new ClocktowerSeatClaimRequest("Player 1"),
                principal(11L, "player1"));
        assignReadyRoles(room.roomId());
        ClocktowerGameResponse started = gameService.startGame(room.roomId(), owner());
        ClocktowerGamePo game = gameRepository.findByIdAndDeletedFalse(started.gameId()).orElseThrow();
        game.setPhase("DAY");
        game.setDayNo(1);
        gameRepository.saveAndFlush(game);
        return new StartedGame(room.roomId(), started.gameId(),
                gameSeatRepository.findByGameIdAndDeletedFalseOrderBySeatNoAsc(started.gameId()));
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

    private RbacPrincipal owner() {
        return principal(1L, "mario");
    }

    private RbacPrincipal principal(Long userId, String username) {
        return new RbacPrincipal(userId, username, Set.of(), Set.of(), "test");
    }

    private record StartedGame(Long roomId, Long gameId, List<ClocktowerGameSeatPo> seats) {
    }
}
```

- [ ] **Step 2: Run the failing test**

Run:

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerGameActionServiceTests test
```

Expected: FAIL during test compilation because `top.egon.mario.clocktower.game.action.*` classes and services do not exist.

- [ ] **Step 3: Create DTO records and service interfaces**

Create the DTO files exactly from the spec. Use `top.egon.mario.clocktower.view.dto.ClocktowerGameEventResponse` in `ClocktowerGameActionResponse`.

Add `ClocktowerGameActionExecutor`, `ClocktowerHumanGameActionService`, and `ClocktowerAgentGameActionService` interfaces with these signatures:

```java
ClocktowerGameActionResponse execute(GameActionCommand command, ActorContext actor);
ClocktowerGameActionResponse submit(Long gameId, ClocktowerGameActionRequest request, RbacPrincipal principal);
ClocktowerGameActionResponse submitAgentAction(Long gameId, Long agentInstanceId,
                                               ClocktowerGameActionRequest request);
```

- [ ] **Step 4: Add repository lookups**

Modify `ClocktowerGameSeatRepository`:

```java
Optional<ClocktowerGameSeatPo> findByIdAndGameIdAndDeletedFalse(Long id, Long gameId);

Optional<ClocktowerGameSeatPo> findByIdAndDeletedFalse(Long id);
```

- [ ] **Step 5: Implement entry services and a minimal executor**

Create `ClocktowerHumanGameActionServiceImpl` and `ClocktowerAgentGameActionServiceImpl`.

Human service logic:

```java
ClocktowerGameSeatPo seat = gameSeatRepository.findByGameIdAndUserIdAndDeletedFalse(gameId, principal.userId())
        .filter(candidate -> "ACTIVE".equals(candidate.getStatus()))
        .filter(candidate -> ClocktowerActorType.HUMAN.equals(candidate.getActorType()))
        .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_GAME_ACTION_PLAYER_REQUIRED"));
if (request == null || request.actorGameSeatId() == null || !request.actorGameSeatId().equals(seat.getId())) {
    throw new ClocktowerException("CLOCKTOWER_GAME_ACTION_SEAT_FORBIDDEN");
}
return executor.execute(command(gameId, request), ActorContext.human(seat.getActorId(), seat.getUserId()));
```

Agent service logic:

```java
ClocktowerAgentInstancePo instance = agentInstanceRepository.findByIdAndDeletedFalse(agentInstanceId)
        .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_AGENT_INSTANCE_INVALID"));
if (!ClocktowerAgentStatus.ACTIVE.equals(instance.getStatus())) {
    throw new ClocktowerException("CLOCKTOWER_AGENT_INSTANCE_INVALID");
}
if (ClocktowerAgentAutoMode.PAUSED.equals(instance.getAutoMode())) {
    throw new ClocktowerException("CLOCKTOWER_AGENT_AUTO_MODE_PAUSED");
}
if (!Objects.equals(instance.getGameId(), gameId)
        || request == null
        || !Objects.equals(instance.getGameSeatId(), request.actorGameSeatId())) {
    throw new ClocktowerException("CLOCKTOWER_AGENT_GAME_SEAT_MISMATCH");
}
ClocktowerGameSeatPo seat = gameSeatRepository
        .findByIdAndGameIdAndDeletedFalse(request.actorGameSeatId(), gameId)
        .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_AGENT_GAME_SEAT_MISMATCH"));
if (!ClocktowerActorType.AGENT.equals(seat.getActorType())) {
    throw new ClocktowerException("CLOCKTOWER_AGENT_GAME_SEAT_MISMATCH");
}
return executor.execute(command(gameId, request), ActorContext.agent(instance.getActorId(), instance.getId()));
```

Create a minimal `ClocktowerGameActionExecutorImpl` that validates known action type and returns `CLOCKTOWER_ACTION_NOT_IMPLEMENTED` for `NOMINATE` and `VOTE`. Leave `PUBLIC_SPEECH`, `FINISH_SPEECH`, and `PASS` returning `CLOCKTOWER_ACTION_NOT_IMPLEMENTED` in this task; later tasks replace those branches.

- [ ] **Step 6: Run the identity tests**

Run:

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerGameActionServiceTests test
```

Expected: PASS for the four identity-gate tests.

- [ ] **Step 7: Commit Task 1**

```bash
git add be/src/main/java/top/egon/mario/clocktower/game/action \
        be/src/main/java/top/egon/mario/clocktower/game/repository/ClocktowerGameSeatRepository.java \
        be/src/test/java/top/egon/mario/clocktower/game/action/ClocktowerGameActionServiceTests.java
git commit -m "feat(clocktower): add game action entry gates"
```

---

### Task 2: Shared Game Event Appender And Executor Base Validation

**Files:**
- Create: `be/src/main/java/top/egon/mario/clocktower/game/service/ClocktowerGameEventAppender.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/game/action/service/impl/ClocktowerGameActionExecutorImpl.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/game/mic/service/impl/ClocktowerPublicMicServiceImpl.java`
- Test: `be/src/test/java/top/egon/mario/clocktower/game/action/ClocktowerGameActionServiceTests.java`
- Test: `be/src/test/java/top/egon/mario/clocktower/game/mic/ClocktowerPublicMicServiceTests.java`

- [ ] **Step 1: Add failing executor validation tests**

Add these tests to `ClocktowerGameActionServiceTests`:

```java
@Test
void unknownActionTypeIsRejected() {
    StartedGame game = startDayGameWithAgents();
    ClocktowerGameSeatPo humanSeat = game.seats().getFirst();

    ClocktowerGameActionResponse response = humanActionService.submit(game.gameId(),
            new ClocktowerGameActionRequest(humanSeat.getId(), "SING_LOUDLY", List.of(),
                    null, null, null, Map.of()),
            principal(11L, "player1"));

    assertThat(response.accepted()).isFalse();
    assertThat(response.rejectedCode()).isEqualTo("UNKNOWN_ACTION_TYPE");
}

@Test
void actionRequiresRunningGameAndActiveSeat() {
    StartedGame game = startDayGameWithAgents();
    ClocktowerGameSeatPo humanSeat = game.seats().getFirst();
    ClocktowerGamePo gamePo = gameRepository.findByIdAndDeletedFalse(game.gameId()).orElseThrow();
    gamePo.setStatus("ENDED");
    gameRepository.saveAndFlush(gamePo);

    assertThatThrownBy(() -> humanActionService.submit(game.gameId(),
            new ClocktowerGameActionRequest(humanSeat.getId(), "PUBLIC_SPEECH", List.of(),
                    null, null, "hello", Map.of()),
            principal(11L, "player1")))
            .isInstanceOf(ClocktowerException.class)
            .hasMessageContaining("CLOCKTOWER_GAME_NOT_RUNNING");
}
```

- [ ] **Step 2: Run the failing tests**

Run:

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerGameActionServiceTests test
```

Expected: FAIL because the executor does not yet lock and validate the game and seat for all action types.

- [ ] **Step 3: Implement `ClocktowerGameEventAppender`**

Create `ClocktowerGameEventAppender` with this API:

```java
public ClocktowerGameEventResponse append(ClocktowerGamePo game,
                                          String eventType,
                                          Long actorGameSeatId,
                                          Long targetGameSeatId,
                                          String visibility,
                                          List<Long> visibleGameSeatIds,
                                          Map<String, Object> payload,
                                          Instant occurredAt)
```

Implementation details:

- Use `ClocktowerGameEventRepository.findTopByGameIdAndDeletedFalseOrderByEventSeqDesc(game.getId())`.
- Save a `ClocktowerGameEventPo` with game phase, day, night, actor, target, visibility, visible seats JSON, payload JSON, status `VISIBLE`, and `occurredAt`.
- Use `ObjectMapper.writeValueAsString`.
- Return `ClocktowerGameEventResponse.from(saved, visibleGameSeatIds, payload)`.
- Throw `ClocktowerException("CLOCKTOWER_GAME_EVENT_JSON_INVALID")` on JSON serialization errors.

- [ ] **Step 4: Refactor public mic event writes through the appender**

Modify `ClocktowerPublicMicServiceImpl`:

- Inject `ClocktowerGameEventAppender`.
- Change the private `appendGameEvent` helper to return `ClocktowerGameEventResponse`.
- Replace direct `ClocktowerGameEventRepository.saveAndFlush` usage inside that helper with `eventAppender.append`.
- Replace `turn.setSpeechEventId(event.getId())` with `turn.setSpeechEventId(event.eventId())`.
- Keep event type, phase, visibility, payload, and sequencing behavior identical.

Run:

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerPublicMicServiceTests test
```

Expected: PASS; this proves task 05 behavior did not change while its event writer moved behind the shared helper.

- [ ] **Step 5: Implement executor base validation**

In `ClocktowerGameActionExecutorImpl`, inject:

```java
ClocktowerGameRepository gameRepository
ClocktowerGameSeatRepository gameSeatRepository
ClocktowerGameEventAppender eventAppender
ClocktowerPublicMicService publicMicService
```

Implement base validation:

```java
ClocktowerGamePo game = gameRepository.findLockedByIdAndDeletedFalse(command.gameId())
        .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_GAME_NOT_FOUND"));
if (!"RUNNING".equals(game.getStatus())) {
    throw new ClocktowerException("CLOCKTOWER_GAME_NOT_RUNNING");
}
ClocktowerGameSeatPo seat = gameSeatRepository
        .findByIdAndGameIdAndDeletedFalse(command.actorGameSeatId(), game.getId())
        .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_GAME_SEAT_NOT_FOUND"));
if (!"ACTIVE".equals(seat.getStatus())) {
    throw new ClocktowerException("CLOCKTOWER_GAME_SEAT_INACTIVE");
}
if (!actorMatchesSeat(actor, seat)) {
    throw new ClocktowerException("CLOCKTOWER_GAME_ACTION_SEAT_FORBIDDEN");
}
```

Use this actor matching rule:

```java
private boolean actorMatchesSeat(ActorContext actor, ClocktowerGameSeatPo seat) {
    if (ClocktowerActorType.HUMAN.equals(actor.actorType())) {
        return ClocktowerActorType.HUMAN.equals(seat.getActorType())
                && Objects.equals(actor.userId(), seat.getUserId());
    }
    if (ClocktowerActorType.AGENT.equals(actor.actorType())) {
        return ClocktowerActorType.AGENT.equals(seat.getActorType())
                && Objects.equals(actor.agentInstanceId(), seat.getAgentInstanceId())
                && Objects.equals(actor.actorId(), seat.getActorId());
    }
    return false;
}
```

Known action switch in this task:

```java
return switch (actionType) {
    case "PUBLIC_SPEECH", "FINISH_SPEECH", "PASS", "NOMINATE", "VOTE" ->
            ClocktowerGameActionResponse.rejected("CLOCKTOWER_ACTION_NOT_IMPLEMENTED");
    default -> reject(game, seat, actionType, "UNKNOWN_ACTION_TYPE");
};
```

The private helper named `reject` should append `ACTION_REJECTED` with visibility `PRIVATE`, `visibleGameSeatIds = List.of(seat.getId())`, payload containing `actionType` and `rejectedCode`, then return `ClocktowerGameActionResponse.rejected(rejectedCode)`.

- [ ] **Step 6: Run tests**

Run:

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerGameActionServiceTests test
```

Expected: PASS for identity and base validation tests, and PASS for public mic tests after the appender refactor.

- [ ] **Step 7: Commit Task 2**

```bash
git add be/src/main/java/top/egon/mario/clocktower/game/service/ClocktowerGameEventAppender.java \
        be/src/main/java/top/egon/mario/clocktower/game/action/service/impl/ClocktowerGameActionExecutorImpl.java \
        be/src/main/java/top/egon/mario/clocktower/game/mic/service/impl/ClocktowerPublicMicServiceImpl.java \
        be/src/test/java/top/egon/mario/clocktower/game/action/ClocktowerGameActionServiceTests.java \
        be/src/test/java/top/egon/mario/clocktower/game/mic/ClocktowerPublicMicServiceTests.java
git commit -m "feat(clocktower): add game action executor validation"
```

---

### Task 3: Public Speech Execution

**Files:**
- Modify: `be/src/main/java/top/egon/mario/clocktower/game/action/service/impl/ClocktowerGameActionExecutorImpl.java`
- Test: `be/src/test/java/top/egon/mario/clocktower/game/action/ClocktowerGameActionServiceTests.java`

- [ ] **Step 1: Add failing public speech tests**

Add these tests:

```java
@Autowired
private top.egon.mario.clocktower.game.mic.service.ClocktowerPublicMicService micService;

@Test
void publicSpeech_requiresMic() {
    StartedGame game = startDayGameWithAgents();
    ClocktowerGameSeatPo humanSeat = game.seats().getFirst();

    ClocktowerGameActionResponse response = humanActionService.submit(game.gameId(),
            new ClocktowerGameActionRequest(humanSeat.getId(), "PUBLIC_SPEECH", List.of(),
                    null, null, "I am first", Map.of()),
            principal(11L, "player1"));

    assertThat(response.accepted()).isFalse();
    assertThat(response.rejectedCode()).isEqualTo("CLOCKTOWER_MIC_SESSION_NOT_FOUND");
}

@Test
void publicSpeech_appendsGameEvent() {
    StartedGame game = startDayGameWithAgents();
    ClocktowerGameSeatPo humanSeat = game.seats().getFirst();
    micService.startDayMicSession(game.gameId(), owner());

    ClocktowerGameActionResponse response = humanActionService.submit(game.gameId(),
            new ClocktowerGameActionRequest(humanSeat.getId(), "PUBLIC_SPEECH", List.of(),
                    null, null, "I am the empath.", Map.of("tone", "calm")),
            principal(11L, "player1"));

    assertThat(response.accepted()).isTrue();
    assertThat(response.event()).isNotNull();
    assertThat(response.event().eventType()).isEqualTo("PUBLIC_SPEECH");
    assertThat(response.event().actorGameSeatId()).isEqualTo(humanSeat.getId());
    assertThat(response.event().visibility()).isEqualTo("PUBLIC");
    assertThat(response.event().payload())
            .containsEntry("content", "I am the empath.")
            .containsEntry("actorType", "HUMAN");
    assertThat(gameEventTypes(game.gameId())).contains("PUBLIC_SPEECH");
}

@Test
void publicSpeechRejectsBlankOrLongContent() {
    StartedGame game = startDayGameWithAgents();
    ClocktowerGameSeatPo humanSeat = game.seats().getFirst();
    micService.startDayMicSession(game.gameId(), owner());

    ClocktowerGameActionResponse blank = humanActionService.submit(game.gameId(),
            new ClocktowerGameActionRequest(humanSeat.getId(), "PUBLIC_SPEECH", List.of(),
                    null, null, " ", Map.of()),
            principal(11L, "player1"));
    ClocktowerGameActionResponse tooLong = humanActionService.submit(game.gameId(),
            new ClocktowerGameActionRequest(humanSeat.getId(), "PUBLIC_SPEECH", List.of(),
                    null, null, "x".repeat(1001), Map.of()),
            principal(11L, "player1"));

    assertThat(blank.accepted()).isFalse();
    assertThat(blank.rejectedCode()).isEqualTo("CLOCKTOWER_PUBLIC_SPEECH_CONTENT_REQUIRED");
    assertThat(tooLong.accepted()).isFalse();
    assertThat(tooLong.rejectedCode()).isEqualTo("CLOCKTOWER_PUBLIC_SPEECH_CONTENT_TOO_LONG");
}
```

Add helper:

```java
private List<String> gameEventTypes(Long gameId) {
    return gameEventRepository.findByGameIdAndStatusAndDeletedFalseOrderByEventSeqAsc(gameId, "VISIBLE")
            .stream()
            .map(top.egon.mario.clocktower.game.po.ClocktowerGameEventPo::getEventType)
            .toList();
}
```

- [ ] **Step 2: Run the failing public speech tests**

Run:

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false \
  -Dtest=ClocktowerGameActionServiceTests#publicSpeech_requiresMic,ClocktowerGameActionServiceTests#publicSpeech_appendsGameEvent,ClocktowerGameActionServiceTests#publicSpeechRejectsBlankOrLongContent test
```

Expected: FAIL because `PUBLIC_SPEECH` still returns `CLOCKTOWER_ACTION_NOT_IMPLEMENTED`.

- [ ] **Step 3: Implement `PUBLIC_SPEECH` branch**

In `ClocktowerGameActionExecutorImpl`:

- Accept only phases `DAY` and `NOMINATION`; rejected code `CLOCKTOWER_PUBLIC_SPEECH_PHASE_INVALID`.
- Require `StringUtils.hasText(command.content())`; rejected code `CLOCKTOWER_PUBLIC_SPEECH_CONTENT_REQUIRED`.
- Require `command.content().length() <= 1000`; rejected code `CLOCKTOWER_PUBLIC_SPEECH_CONTENT_TOO_LONG`.
- Call `publicMicService.requireCanSpeak(game.getId(), seat.getId())`.
- Catch `ClocktowerException` from the mic guard and return `reject(game, seat, "PUBLIC_SPEECH", ex.getMessage())`.
- Append event through `ClocktowerGameEventAppender`:

```java
Map<String, Object> payload = new LinkedHashMap<>();
payload.put("content", command.content().trim());
payload.put("actorType", actor.actorType());
if (command.payload() != null && !command.payload().isEmpty()) {
    payload.put("clientPayload", command.payload());
}
ClocktowerGameEventResponse event = eventAppender.append(game, "PUBLIC_SPEECH", seat.getId(), null,
        "PUBLIC", List.of(), payload, Instant.now());
return ClocktowerGameActionResponse.accepted(event);
```

- [ ] **Step 4: Run the public speech tests**

Run:

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerGameActionServiceTests test
```

Expected: PASS for all current `ClocktowerGameActionServiceTests`.

- [ ] **Step 5: Commit Task 3**

```bash
git add be/src/main/java/top/egon/mario/clocktower/game/action/service/impl/ClocktowerGameActionExecutorImpl.java \
        be/src/test/java/top/egon/mario/clocktower/game/action/ClocktowerGameActionServiceTests.java
git commit -m "feat(clocktower): execute public speech actions"
```

---

### Task 4: Actor-Based Mic Finish, Finish Speech, And Mic Pass

**Files:**
- Modify: `be/src/main/java/top/egon/mario/clocktower/game/mic/service/ClocktowerPublicMicService.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/game/mic/service/impl/ClocktowerPublicMicServiceImpl.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/game/action/service/impl/ClocktowerGameActionExecutorImpl.java`
- Test: `be/src/test/java/top/egon/mario/clocktower/game/mic/ClocktowerPublicMicServiceTests.java`
- Test: `be/src/test/java/top/egon/mario/clocktower/game/action/ClocktowerGameActionServiceTests.java`

- [ ] **Step 1: Add failing mic actor-finish service test**

Add to `ClocktowerPublicMicServiceTests`:

```java
@Test
void finishCurrentTurnAsActorFinishesOnlyCurrentHolder() {
    StartedGame game = startDayGameWithAgents();
    ClocktowerMicSessionView started = micService.startDayMicSession(game.gameId(), owner());
    Long holderSeatId = started.currentHolderGameSeatId();
    Long otherSeatId = started.turns().stream()
            .map(ClocktowerMicTurnView::gameSeatId)
            .filter(seatId -> !seatId.equals(holderSeatId))
            .findFirst()
            .orElseThrow();

    assertThatThrownBy(() -> micService.finishCurrentTurnAsActor(game.gameId(), otherSeatId))
            .isInstanceOf(ClocktowerException.class)
            .hasMessageContaining("CLOCKTOWER_MIC_NOT_HOLDER");

    ClocktowerMicSessionView finished = micService.finishCurrentTurnAsActor(game.gameId(), holderSeatId);

    assertThat(turnBySeatNo(finished, 1).status()).isEqualTo("DONE");
    assertThat(activeTurn(finished).seatNo()).isEqualTo(2);
    assertThat(gameEventTypes(game.gameId())).contains("MIC_TURN_FINISHED");
}
```

- [ ] **Step 2: Run the failing mic service test**

Run:

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerPublicMicServiceTests#finishCurrentTurnAsActorFinishesOnlyCurrentHolder test
```

Expected: FAIL during test compilation because `finishCurrentTurnAsActor` is not defined.

- [ ] **Step 3: Implement actor-based mic finish**

Add to `ClocktowerPublicMicService`:

```java
ClocktowerMicSessionView finishCurrentTurnAsActor(Long gameId, Long actorGameSeatId);
```

Implement in `ClocktowerPublicMicServiceImpl` by reusing the existing locked-session and finish path:

```java
@Override
@Transactional
public ClocktowerMicSessionView finishCurrentTurnAsActor(Long gameId, Long actorGameSeatId) {
    ClocktowerGamePo game = lockedGame(gameId);
    ClocktowerGamePublicMicSessionPo session = lockedCurrentSession(game);
    Instant now = Instant.now();
    refreshExpiredState(game, session, now);
    requireSessionOpen(session);
    ClocktowerGamePublicMicTurnPo turn = currentTurn(session);
    if (!TURN_ACTIVE.equals(turn.getStatus())
            || !Objects.equals(session.getCurrentHolderGameSeatId(), actorGameSeatId)
            || !Objects.equals(turn.getGameSeatId(), actorGameSeatId)) {
        throw new ClocktowerException("CLOCKTOWER_MIC_NOT_HOLDER");
    }
    finishActiveTurn(game, session, turn, now, TURN_DONE, EVENT_MIC_TURN_FINISHED);
    return toView(session);
}
```

- [ ] **Step 4: Run the mic service test**

Run:

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerPublicMicServiceTests#finishCurrentTurnAsActorFinishesOnlyCurrentHolder test
```

Expected: PASS.

- [ ] **Step 5: Add failing action tests for `FINISH_SPEECH` and `PASS(MIC_TURN)`**

Add to `ClocktowerGameActionServiceTests`:

```java
@Test
void finishSpeech_finishesCurrentMicTurn() {
    StartedGame game = startDayGameWithAgents();
    ClocktowerGameSeatPo humanSeat = game.seats().getFirst();
    micService.startDayMicSession(game.gameId(), owner());

    ClocktowerGameActionResponse response = humanActionService.submit(game.gameId(),
            new ClocktowerGameActionRequest(humanSeat.getId(), "FINISH_SPEECH", List.of(),
                    null, null, null, Map.of()),
            principal(11L, "player1"));

    assertThat(response.accepted()).isTrue();
    assertThat(response.event().eventType()).isEqualTo("FINISH_SPEECH");
    assertThat(gameEventTypes(game.gameId())).contains("FINISH_SPEECH", "MIC_TURN_FINISHED");
    assertThat(micService.canSpeak(game.gameId(), humanSeat.getId())).isFalse();
}

@Test
void passMicTurn_finishesCurrentMicTurnAndAppendsPassEvent() {
    StartedGame game = startDayGameWithAgents();
    ClocktowerGameSeatPo humanSeat = game.seats().getFirst();
    micService.startDayMicSession(game.gameId(), owner());

    ClocktowerGameActionResponse response = humanActionService.submit(game.gameId(),
            new ClocktowerGameActionRequest(humanSeat.getId(), "PASS", List.of(),
                    null, null, null, Map.of("passType", "MIC_TURN")),
            principal(11L, "player1"));

    assertThat(response.accepted()).isTrue();
    assertThat(response.event().eventType()).isEqualTo("PLAYER_PASSED");
    assertThat(response.event().payload()).containsEntry("passType", "MIC_TURN");
    assertThat(gameEventTypes(game.gameId())).contains("PLAYER_PASSED", "MIC_TURN_FINISHED");
}

@Test
void passRejectsUnsupportedPassType() {
    StartedGame game = startDayGameWithAgents();
    ClocktowerGameSeatPo humanSeat = game.seats().getFirst();

    ClocktowerGameActionResponse response = humanActionService.submit(game.gameId(),
            new ClocktowerGameActionRequest(humanSeat.getId(), "PASS", List.of(),
                    null, null, null, Map.of("passType", "NIGHT_TASK")),
            principal(11L, "player1"));

    assertThat(response.accepted()).isFalse();
    assertThat(response.rejectedCode()).isEqualTo("CLOCKTOWER_PASS_TYPE_UNSUPPORTED");
}
```

- [ ] **Step 6: Run the failing action tests**

Run:

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false \
  -Dtest=ClocktowerGameActionServiceTests#finishSpeech_finishesCurrentMicTurn,ClocktowerGameActionServiceTests#passMicTurn_finishesCurrentMicTurnAndAppendsPassEvent,ClocktowerGameActionServiceTests#passRejectsUnsupportedPassType test
```

Expected: FAIL because executor still returns `CLOCKTOWER_ACTION_NOT_IMPLEMENTED` for `FINISH_SPEECH` and `PASS`.

- [ ] **Step 7: Implement `FINISH_SPEECH` and `PASS` branches**

In `ClocktowerGameActionExecutorImpl`:

- Add shared phase guard for `DAY` and `NOMINATION` with rejected code `CLOCKTOWER_PUBLIC_SPEECH_PHASE_INVALID` for `FINISH_SPEECH`.
- For `PASS`, read `String passType = stringPayload(command.payload(), "passType")`.
- If pass type is not `MIC_TURN`, return `reject(game, seat, "PASS", "CLOCKTOWER_PASS_TYPE_UNSUPPORTED")`.
- For both branches:
  1. Call `publicMicService.requireCanSpeak(game.getId(), seat.getId())` and translate `ClocktowerException` into rejected response.
  2. Append primary action event through `ClocktowerGameEventAppender`.
  3. Call `publicMicService.finishCurrentTurnAsActor(game.getId(), seat.getId())`.
  4. Return accepted response with the primary action event.

Primary event payloads:

```java
Map.of("actorType", actor.actorType())
Map.of("passType", "MIC_TURN", "actorType", actor.actorType())
```

- [ ] **Step 8: Run focused task tests**

Run:

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false \
  -Dtest=ClocktowerGameActionServiceTests,ClocktowerPublicMicServiceTests test
```

Expected: PASS.

- [ ] **Step 9: Commit Task 4**

```bash
git add be/src/main/java/top/egon/mario/clocktower/game/mic/service/ClocktowerPublicMicService.java \
        be/src/main/java/top/egon/mario/clocktower/game/mic/service/impl/ClocktowerPublicMicServiceImpl.java \
        be/src/main/java/top/egon/mario/clocktower/game/action/service/impl/ClocktowerGameActionExecutorImpl.java \
        be/src/test/java/top/egon/mario/clocktower/game/mic/ClocktowerPublicMicServiceTests.java \
        be/src/test/java/top/egon/mario/clocktower/game/action/ClocktowerGameActionServiceTests.java
git commit -m "feat(clocktower): finish mic turns from game actions"
```

---

### Task 5: HTTP Endpoint, Compatibility Check, And Full Verification

**Files:**
- Create: `be/src/main/java/top/egon/mario/clocktower/game/action/web/ClocktowerGameActionController.java`
- Test: `be/src/test/java/top/egon/mario/clocktower/resource/ClocktowerRbacResourceProviderTests.java`
- Test: `be/src/test/java/top/egon/mario/clocktower/game/action/ClocktowerGameActionServiceTests.java`

- [ ] **Step 1: Add endpoint/RBAC regression checks**

Confirm `ClocktowerRbacResourceProviderTests` contains this assertion:

```java
assertMatches(rules, "POST", "/api/clocktower/games/9/actions", "api:clocktower:game:action");
```

If it is already present, do not edit `ClocktowerRbacResourceProviderTests` in this task.

In `ClocktowerGameActionServiceTests`, add the deferred action vocabulary test:

```java
@Test
void nominateVote_notImplementedUntilTask07() {
    StartedGame game = startDayGameWithAgents();
    ClocktowerGameSeatPo humanSeat = game.seats().getFirst();
    ClocktowerGameSeatPo agentSeat = game.seats().get(1);

    ClocktowerGameActionResponse nominate = humanActionService.submit(game.gameId(),
            new ClocktowerGameActionRequest(humanSeat.getId(), "NOMINATE", List.of(agentSeat.getId()),
                    null, null, "nominate", Map.of()),
            principal(11L, "player1"));
    ClocktowerGameActionResponse vote = humanActionService.submit(game.gameId(),
            new ClocktowerGameActionRequest(humanSeat.getId(), "VOTE", List.of(),
                    123L, true, null, Map.of("vote", true)),
            principal(11L, "player1"));

    assertThat(nominate.accepted()).isFalse();
    assertThat(nominate.rejectedCode()).isEqualTo("CLOCKTOWER_ACTION_NOT_IMPLEMENTED");
    assertThat(vote.accepted()).isFalse();
    assertThat(vote.rejectedCode()).isEqualTo("CLOCKTOWER_ACTION_NOT_IMPLEMENTED");
}
```

- [ ] **Step 2: Run the endpoint/RBAC regression check**

Run:

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false \
  -Dtest=ClocktowerGameActionServiceTests,ClocktowerRbacResourceProviderTests test
```

Expected: PASS for action service and RBAC resource tests.

- [ ] **Step 3: Create controller**

Create `ClocktowerGameActionController`:

```java
package top.egon.mario.clocktower.game.action.web;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.egon.mario.clocktower.common.web.ClocktowerReactiveSupport;
import top.egon.mario.clocktower.game.action.dto.ClocktowerGameActionRequest;
import top.egon.mario.clocktower.game.action.dto.ClocktowerGameActionResponse;
import top.egon.mario.clocktower.game.action.service.ClocktowerHumanGameActionService;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/clocktower/games/{gameId}")
public class ClocktowerGameActionController extends ClocktowerReactiveSupport {

    private final ClocktowerHumanGameActionService humanGameActionService;

    @PostMapping("/actions")
    public Mono<ApiResponse<ClocktowerGameActionResponse>> submit(
            @PathVariable Long gameId,
            @RequestBody ClocktowerGameActionRequest request,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> humanGameActionService.submit(gameId, request, principal));
    }
}
```

- [ ] **Step 4: Run targeted suite**

Run:

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false \
  -Dtest=ClocktowerGameActionServiceTests,ClocktowerPublicMicServiceTests,ClocktowerRbacResourceProviderTests test
```

Expected: PASS.

- [ ] **Step 5: Confirm old room action code was not changed**

Run:

```bash
git diff --name-only HEAD~4..HEAD | rg '^be/src/main/java/top/egon/mario/clocktower/action/' || true
```

Expected: no output. If output appears, inspect it and remove any unrelated old-action changes before committing.

- [ ] **Step 6: Run full backend verification**

Run:

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false test
```

Expected: BUILD SUCCESS with all backend tests passing.

- [ ] **Step 7: Commit Task 5**

```bash
git add be/src/main/java/top/egon/mario/clocktower/game/action/web/ClocktowerGameActionController.java \
        be/src/test/java/top/egon/mario/clocktower/game/action/ClocktowerGameActionServiceTests.java
git commit -m "feat(clocktower): expose game action api"
```

---

## Final Verification Checklist

- [ ] `git status --short` shows a clean worktree after commits.
- [ ] `git log --oneline -6` shows one plan/spec commit plus one commit per implementation task.
- [ ] Targeted command passes:

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false \
  -Dtest=ClocktowerGameActionServiceTests,ClocktowerPublicMicServiceTests,ClocktowerRbacResourceProviderTests test
```

- [ ] Full backend command passes:

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false test
```

- [ ] No files under `be/src/main/resources/db/migration` changed.
- [ ] No files under `be/src/main/java/top/egon/mario/clocktower/action` changed.
- [ ] No frontend files changed.
