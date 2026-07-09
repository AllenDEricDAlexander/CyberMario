# Clocktower Game Flow Service Cutover Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement task 08 by moving new Clocktower game phase advancement onto the game-level model and preserving the legacy room flow.

**Architecture:** Add a focused `top.egon.mario.clocktower.game.flow` package. `ClocktowerGameFlowServiceImpl` orchestrates phase transitions, while victory checks, night-task checks, and future Agent scheduling are isolated behind small services so task 09 and task 10 can extend them without changing the controller contract.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring Data JPA, Flyway, H2/PostgreSQL-compatible SQL, WebFlux controller wrappers, JUnit 5, AssertJ.

---

## File Structure

Create:

- `be/src/main/resources/db/migration/V35__clocktower_game_night_task.sql` - one migration for minimal game night tasks.
- `be/src/main/java/top/egon/mario/clocktower/game/night/po/ClocktowerGameNightTaskPo.java` - minimal task row used for pending-task blocking.
- `be/src/main/java/top/egon/mario/clocktower/game/night/repository/ClocktowerGameNightTaskRepository.java` - task lookup and count methods.
- `be/src/main/java/top/egon/mario/clocktower/game/flow/dto/ClocktowerGameAdvanceRequest.java` - normal and forced advance request.
- `be/src/main/java/top/egon/mario/clocktower/game/flow/dto/ClocktowerGameAdvanceResult.java` - advance result.
- `be/src/main/java/top/egon/mario/clocktower/game/flow/dto/ClocktowerGameFlowView.java` - current flow state.
- `be/src/main/java/top/egon/mario/clocktower/game/flow/dto/ClocktowerGameNightTaskSummary.java` - current night task counters.
- `be/src/main/java/top/egon/mario/clocktower/game/flow/dto/ClocktowerGamePhaseSignal.java` - phase signal extension payload for task 10.
- `be/src/main/java/top/egon/mario/clocktower/game/flow/dto/ClocktowerGameVictoryResult.java` - basic victory result.
- `be/src/main/java/top/egon/mario/clocktower/game/flow/service/ClocktowerGameFlowService.java` - flow API.
- `be/src/main/java/top/egon/mario/clocktower/game/flow/service/ClocktowerGameNightTaskGateway.java` - night-task boundary for task 09.
- `be/src/main/java/top/egon/mario/clocktower/game/flow/service/ClocktowerGamePhaseSignalScheduler.java` - no-op Agent scheduling boundary for task 10.
- `be/src/main/java/top/egon/mario/clocktower/game/flow/service/ClocktowerGameVictoryService.java` - basic victory API.
- `be/src/main/java/top/egon/mario/clocktower/game/flow/service/impl/ClocktowerGameFlowServiceImpl.java` - phase transition implementation.
- `be/src/main/java/top/egon/mario/clocktower/game/flow/service/impl/ClocktowerGameNightTaskGatewayImpl.java` - v1 night-task summary and no-op initialization.
- `be/src/main/java/top/egon/mario/clocktower/game/flow/service/impl/ClocktowerGameVictoryServiceImpl.java` - demon/alive-count victory.
- `be/src/main/java/top/egon/mario/clocktower/game/flow/service/impl/NoopClocktowerGamePhaseSignalScheduler.java` - no-op scheduler.
- `be/src/main/java/top/egon/mario/clocktower/game/flow/web/ClocktowerGameFlowController.java` - game flow endpoints.
- `be/src/test/java/top/egon/mario/clocktower/game/flow/ClocktowerGameFlowServiceTests.java` - integration tests for task 08.

Modify:

- `be/src/test/java/top/egon/mario/clocktower/ClocktowerJpaMappingTests.java` - assert the night-task PO is managed and JSON persists.

Do not modify:

- Existing Flyway migrations before `V35`.
- `be/src/main/java/top/egon/mario/clocktower/flow/**`
- `be/src/main/java/top/egon/mario/clocktower/action/**`
- Frontend files.

---

### Task 1: Night Task Persistence

**Files:**
- Create: `be/src/main/resources/db/migration/V35__clocktower_game_night_task.sql`
- Create: `be/src/main/java/top/egon/mario/clocktower/game/night/po/ClocktowerGameNightTaskPo.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/game/night/repository/ClocktowerGameNightTaskRepository.java`
- Modify: `be/src/test/java/top/egon/mario/clocktower/ClocktowerJpaMappingTests.java`

- [ ] **Step 1: Write the failing JPA mapping test**

Add this import to `ClocktowerJpaMappingTests`:

```java
import top.egon.mario.clocktower.game.night.po.ClocktowerGameNightTaskPo;
```

Extend `clocktowerGamePoClassesAreManagedByJpaContext()`:

```java
assertManaged(ClocktowerGameNightTaskPo.class);
```

Add this test method:

```java
@Test
void clocktowerGameNightTaskJsonColumnsRoundTripMinimalStrings() {
    ClocktowerGameNightTaskPo task = new ClocktowerGameNightTaskPo();
    task.setGameId(801L);
    task.setNightNo(1);
    task.setTaskKey("IMP:FIRST_NIGHT");
    task.setActorGameSeatId(901L);
    task.setRoleCode("IMP");
    task.setStatus("PENDING");
    task.setMandatory(true);
    task.setSortOrder(10);
    task.setMetadataJson("{}");
    entityManager.persist(task);

    entityManager.flush();
    entityManager.clear();

    ClocktowerGameNightTaskPo reloaded = entityManager.find(ClocktowerGameNightTaskPo.class, task.getId());
    assertThat(reloaded.getMetadataJson()).isEqualTo("{}");
    assertThat(reloaded.isMandatory()).isTrue();
    assertThat(reloaded.getStatus()).isEqualTo("PENDING");
}
```

- [ ] **Step 2: Run the mapping test and verify it fails**

Run:

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerJpaMappingTests test
```

Expected: compile fails because `ClocktowerGameNightTaskPo` does not exist.

- [ ] **Step 3: Add the Flyway migration**

Create `be/src/main/resources/db/migration/V35__clocktower_game_night_task.sql`:

```sql
CREATE TABLE clocktower_game_night_task (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    game_id BIGINT NOT NULL,
    night_no INTEGER NOT NULL,
    task_key VARCHAR(128) NOT NULL,
    actor_game_seat_id BIGINT,
    role_code VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    mandatory BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    completed_at TIMESTAMP WITH TIME ZONE,
    skipped_at TIMESTAMP WITH TIME ZONE,
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_clocktower_game_night_task_game_night
    ON clocktower_game_night_task (game_id, night_no);
CREATE INDEX idx_clocktower_game_night_task_game_status
    ON clocktower_game_night_task (game_id, status);
CREATE UNIQUE INDEX uk_clocktower_game_night_task_key
    ON clocktower_game_night_task (game_id, night_no, task_key, deleted);
```

- [ ] **Step 4: Add the night-task PO**

Create `be/src/main/java/top/egon/mario/clocktower/game/night/po/ClocktowerGameNightTaskPo.java`:

```java
package top.egon.mario.clocktower.game.night.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import top.egon.mario.common.entity.BaseAuditablePo;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "clocktower_game_night_task")
public class ClocktowerGameNightTaskPo extends BaseAuditablePo {

    @Column(name = "game_id", nullable = false)
    private Long gameId;

    @Column(name = "night_no", nullable = false)
    private int nightNo;

    @Column(name = "task_key", nullable = false, length = 128)
    private String taskKey;

    @Column(name = "actor_game_seat_id")
    private Long actorGameSeatId;

    @Column(name = "role_code", length = 64)
    private String roleCode;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "PENDING";

    @Column(name = "mandatory", nullable = false)
    private boolean mandatory = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "skipped_at")
    private Instant skippedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";
}
```

- [ ] **Step 5: Add the night-task repository**

Create `be/src/main/java/top/egon/mario/clocktower/game/night/repository/ClocktowerGameNightTaskRepository.java`:

```java
package top.egon.mario.clocktower.game.night.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import top.egon.mario.clocktower.game.night.po.ClocktowerGameNightTaskPo;

import java.util.Collection;
import java.util.List;

public interface ClocktowerGameNightTaskRepository extends JpaRepository<ClocktowerGameNightTaskPo, Long> {

    List<ClocktowerGameNightTaskPo> findByGameIdAndNightNoAndDeletedFalseOrderBySortOrderAscIdAsc(
            Long gameId, int nightNo);

    long countByGameIdAndNightNoAndMandatoryTrueAndDeletedFalse(Long gameId, int nightNo);

    long countByGameIdAndNightNoAndMandatoryTrueAndStatusInAndDeletedFalse(
            Long gameId, int nightNo, Collection<String> statuses);

    @Query("""
            select count(task)
            from ClocktowerGameNightTaskPo task
            where task.gameId = :gameId
              and task.nightNo = :nightNo
              and task.mandatory = true
              and task.status not in :completedStatuses
              and task.deleted = false
            """)
    long countPendingMandatoryTasks(
            @Param("gameId") Long gameId,
            @Param("nightNo") int nightNo,
            @Param("completedStatuses") Collection<String> completedStatuses);
}
```

- [ ] **Step 6: Run the mapping test and verify it passes**

Run:

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerJpaMappingTests test
```

Expected: pass.

- [ ] **Step 7: Commit**

Run:

```bash
git add be/src/main/resources/db/migration/V35__clocktower_game_night_task.sql \
    be/src/main/java/top/egon/mario/clocktower/game/night \
    be/src/test/java/top/egon/mario/clocktower/ClocktowerJpaMappingTests.java
git commit -m "feat(clocktower): add game night task table"
```

---

### Task 2: Flow DTOs And Service Boundaries

**Files:**
- Create: `be/src/main/java/top/egon/mario/clocktower/game/flow/dto/ClocktowerGameAdvanceRequest.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/game/flow/dto/ClocktowerGameAdvanceResult.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/game/flow/dto/ClocktowerGameFlowView.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/game/flow/dto/ClocktowerGameNightTaskSummary.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/game/flow/dto/ClocktowerGamePhaseSignal.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/game/flow/dto/ClocktowerGameVictoryResult.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/game/flow/service/ClocktowerGameFlowService.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/game/flow/service/ClocktowerGameNightTaskGateway.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/game/flow/service/ClocktowerGamePhaseSignalScheduler.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/game/flow/service/ClocktowerGameVictoryService.java`

- [ ] **Step 1: Add DTO records**

Create `ClocktowerGameFlowView`:

```java
package top.egon.mario.clocktower.game.flow.dto;

import java.util.List;
import java.util.Map;

public record ClocktowerGameFlowView(
        Long gameId,
        String status,
        String phase,
        int dayNo,
        int nightNo,
        boolean advanceAllowed,
        List<String> blockingReasons,
        String nextPhase,
        Map<String, Object> counters
) {
}
```

Create `ClocktowerGameAdvanceRequest`:

```java
package top.egon.mario.clocktower.game.flow.dto;

import java.util.Map;

public record ClocktowerGameAdvanceRequest(
        String targetPhase,
        String reason,
        Map<String, Object> metadata
) {
}
```

Create `ClocktowerGameAdvanceResult`:

```java
package top.egon.mario.clocktower.game.flow.dto;

public record ClocktowerGameAdvanceResult(
        Long gameId,
        String previousPhase,
        String phase,
        boolean advanced,
        boolean forced,
        ClocktowerGameFlowView flow
) {
}
```

Create `ClocktowerGameNightTaskSummary`:

```java
package top.egon.mario.clocktower.game.flow.dto;

public record ClocktowerGameNightTaskSummary(
        int mandatoryCount,
        int pendingMandatoryCount,
        int doneCount,
        int skippedCount
) {

    public boolean complete() {
        return pendingMandatoryCount == 0;
    }
}
```

Create `ClocktowerGamePhaseSignal`:

```java
package top.egon.mario.clocktower.game.flow.dto;

import java.util.Map;

public record ClocktowerGamePhaseSignal(
        Long gameId,
        String previousPhase,
        String phase,
        int dayNo,
        int nightNo,
        boolean forced,
        Map<String, Object> metadata
) {
}
```

Create `ClocktowerGameVictoryResult`:

```java
package top.egon.mario.clocktower.game.flow.dto;

import java.util.Map;

public record ClocktowerGameVictoryResult(
        boolean ended,
        String winner,
        String reason,
        int aliveCount,
        boolean demonAlive,
        Map<String, Object> counters
) {

    public static ClocktowerGameVictoryResult none(int aliveCount, boolean demonAlive,
                                                   Map<String, Object> counters) {
        return new ClocktowerGameVictoryResult(false, null, null, aliveCount, demonAlive, counters);
    }

    public static ClocktowerGameVictoryResult ended(String winner, String reason, int aliveCount,
                                                    boolean demonAlive, Map<String, Object> counters) {
        return new ClocktowerGameVictoryResult(true, winner, reason, aliveCount, demonAlive, counters);
    }
}
```

- [ ] **Step 2: Add service interfaces**

Create `ClocktowerGameFlowService`:

```java
package top.egon.mario.clocktower.game.flow.service;

import top.egon.mario.clocktower.game.flow.dto.ClocktowerGameAdvanceRequest;
import top.egon.mario.clocktower.game.flow.dto.ClocktowerGameAdvanceResult;
import top.egon.mario.clocktower.game.flow.dto.ClocktowerGameFlowView;
import top.egon.mario.rbac.service.security.RbacPrincipal;

public interface ClocktowerGameFlowService {

    ClocktowerGameFlowView getFlow(Long gameId, RbacPrincipal principal);

    ClocktowerGameAdvanceResult advance(Long gameId, ClocktowerGameAdvanceRequest request,
                                        RbacPrincipal principal);

    ClocktowerGameAdvanceResult forceAdvance(Long gameId, ClocktowerGameAdvanceRequest request,
                                             RbacPrincipal principal);
}
```

Create `ClocktowerGameNightTaskGateway`:

```java
package top.egon.mario.clocktower.game.flow.service;

import top.egon.mario.clocktower.game.flow.dto.ClocktowerGameNightTaskSummary;
import top.egon.mario.clocktower.game.po.ClocktowerGamePo;

public interface ClocktowerGameNightTaskGateway {

    ClocktowerGameNightTaskSummary summarize(ClocktowerGamePo game);

    void initializeNightTasks(ClocktowerGamePo game);
}
```

Create `ClocktowerGamePhaseSignalScheduler`:

```java
package top.egon.mario.clocktower.game.flow.service;

import top.egon.mario.clocktower.game.flow.dto.ClocktowerGamePhaseSignal;

public interface ClocktowerGamePhaseSignalScheduler {

    void schedule(ClocktowerGamePhaseSignal signal);
}
```

Create `ClocktowerGameVictoryService`:

```java
package top.egon.mario.clocktower.game.flow.service;

import top.egon.mario.clocktower.game.flow.dto.ClocktowerGameVictoryResult;
import top.egon.mario.clocktower.game.po.ClocktowerGamePo;

public interface ClocktowerGameVictoryService {

    ClocktowerGameVictoryResult evaluate(ClocktowerGamePo game);
}
```

- [ ] **Step 3: Compile**

Run:

```bash
./mvnw -Dmaven.build.cache.enabled=false -DskipTests compile
```

Expected: compile passes because only DTOs and interfaces were added.

- [ ] **Step 4: Commit**

Run:

```bash
git add be/src/main/java/top/egon/mario/clocktower/game/flow/dto \
    be/src/main/java/top/egon/mario/clocktower/game/flow/service
git commit -m "feat(clocktower): define game flow contracts"
```

---

### Task 3: Gateway And Victory Implementations

**Files:**
- Create: `be/src/main/java/top/egon/mario/clocktower/game/flow/service/impl/ClocktowerGameNightTaskGatewayImpl.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/game/flow/service/impl/ClocktowerGameVictoryServiceImpl.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/game/flow/service/impl/NoopClocktowerGamePhaseSignalScheduler.java`
- Test: `be/src/test/java/top/egon/mario/clocktower/game/flow/ClocktowerGameFlowServiceTests.java`

- [ ] **Step 1: Create the initial flow test file with victory tests**

Create `be/src/test/java/top/egon/mario/clocktower/game/flow/ClocktowerGameFlowServiceTests.java` with this test structure. The helper methods should mirror the style of `ClocktowerGameNominationServiceTests`, but keep all helpers inside this file.

```java
package top.egon.mario.clocktower.game.flow;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.game.dto.ClocktowerGameResponse;
import top.egon.mario.clocktower.game.flow.dto.ClocktowerGameAdvanceRequest;
import top.egon.mario.clocktower.game.flow.dto.ClocktowerGameAdvanceResult;
import top.egon.mario.clocktower.game.flow.dto.ClocktowerGameFlowView;
import top.egon.mario.clocktower.game.flow.service.ClocktowerGameFlowService;
import top.egon.mario.clocktower.game.mic.service.ClocktowerPublicMicService;
import top.egon.mario.clocktower.game.night.po.ClocktowerGameNightTaskPo;
import top.egon.mario.clocktower.game.night.repository.ClocktowerGameNightTaskRepository;
import top.egon.mario.clocktower.game.nomination.dto.ClocktowerGameExecutionResolveRequest;
import top.egon.mario.clocktower.game.nomination.service.ClocktowerGameExecutionService;
import top.egon.mario.clocktower.game.po.ClocktowerGameEventPo;
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

@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
@Transactional
class ClocktowerGameFlowServiceTests {

    private static final List<String> ROLE_CODES = List.of("EMPATH", "CHEF", "MONK", "POISONER", "IMP");

    @Autowired
    private ClocktowerGameFlowService flowService;

    @Autowired
    private ClocktowerGameLifecycleService gameService;

    @Autowired
    private ClocktowerPublicMicService micService;

    @Autowired
    private ClocktowerGameExecutionService executionService;

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
    private ClocktowerGameNightTaskRepository nightTaskRepository;

    @Test
    void victoryGoodWinWhenAllDemonsDead() {
        StartedGame game = startGame();
        ClocktowerGameSeatPo demon = game.seats().stream()
                .filter(seat -> "DEMON".equals(seat.getRoleType()))
                .findFirst()
                .orElseThrow();
        demon.setLifeStatus("DEAD");
        demon.setPublicLifeStatus("DEAD");
        gameSeatRepository.saveAndFlush(demon);

        ClocktowerGameAdvanceResult result = flowService.advance(game.gameId(), emptyRequest(), owner());

        assertThat(result.phase()).isEqualTo("ENDED");
        assertThat(result.flow().status()).isEqualTo("ENDED");
        assertThat(gameRepository.findByIdAndDeletedFalse(game.gameId()).orElseThrow().getStatus()).isEqualTo("ENDED");
        assertThat(eventTypes(game.gameId())).contains("GAME_ENDED", "PHASE_CHANGED");
    }

    @Test
    void victoryEvilWinWhenTwoAliveAndDemonAlive() {
        StartedGame game = startGame();
        List<ClocktowerGameSeatPo> seats = game.seats();
        seats.stream()
                .filter(seat -> !"DEMON".equals(seat.getRoleType()))
                .limit(3)
                .forEach(seat -> {
                    seat.setLifeStatus("DEAD");
                    seat.setPublicLifeStatus("DEAD");
                });
        gameSeatRepository.saveAllAndFlush(seats);

        ClocktowerGameAdvanceResult result = flowService.advance(game.gameId(), emptyRequest(), owner());

        assertThat(result.phase()).isEqualTo("ENDED");
        assertThat(result.flow().status()).isEqualTo("ENDED");
        assertThat(eventTypes(game.gameId())).contains("GAME_ENDED", "PHASE_CHANGED");
    }

    private StartedGame startGame() {
        ClocktowerRoomResponse room = roomService.createRoom(createRequest(4), owner());
        roomService.claimSeat(room.roomId(), 1, new ClocktowerSeatClaimRequest("Player 1"),
                principal(11L, "player1"));
        assignReadyRoles(room.roomId());
        ClocktowerGameResponse response = gameService.startGame(room.roomId(), owner());
        return new StartedGame(response.gameId(),
                gameSeatRepository.findByGameIdAndDeletedFalseOrderBySeatNoAsc(response.gameId()));
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

    private List<String> eventTypes(Long gameId) {
        return gameEventRepository.findByGameIdAndStatusAndDeletedFalseOrderByEventSeqAsc(gameId, "VISIBLE")
                .stream()
                .map(ClocktowerGameEventPo::getEventType)
                .toList();
    }

    private ClocktowerGameAdvanceRequest emptyRequest() {
        return new ClocktowerGameAdvanceRequest(null, null, Map.of());
    }

    private RbacPrincipal owner() {
        return principal(1L, "mario");
    }

    private RbacPrincipal principal(Long userId, String username) {
        return new RbacPrincipal(userId, username, Set.of(), Set.of(), "test");
    }

    private record StartedGame(Long gameId, List<ClocktowerGameSeatPo> seats) {
    }
}
```

- [ ] **Step 2: Run the failing victory tests**

Run:

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerGameFlowServiceTests test
```

Expected: compile fails because `ClocktowerGameFlowService` has no implementation bean.

- [ ] **Step 3: Implement night-task gateway**

Create `ClocktowerGameNightTaskGatewayImpl`:

```java
package top.egon.mario.clocktower.game.flow.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.egon.mario.clocktower.game.flow.dto.ClocktowerGameNightTaskSummary;
import top.egon.mario.clocktower.game.flow.service.ClocktowerGameNightTaskGateway;
import top.egon.mario.clocktower.game.night.po.ClocktowerGameNightTaskPo;
import top.egon.mario.clocktower.game.night.repository.ClocktowerGameNightTaskRepository;
import top.egon.mario.clocktower.game.po.ClocktowerGamePo;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ClocktowerGameNightTaskGatewayImpl implements ClocktowerGameNightTaskGateway {

    private static final String STATUS_DONE = "DONE";
    private static final String STATUS_SKIPPED = "SKIPPED";
    private static final Set<String> COMPLETED_STATUSES = Set.of(STATUS_DONE, STATUS_SKIPPED);

    private final ClocktowerGameNightTaskRepository nightTaskRepository;

    @Override
    public ClocktowerGameNightTaskSummary summarize(ClocktowerGamePo game) {
        List<ClocktowerGameNightTaskPo> tasks = nightTaskRepository
                .findByGameIdAndNightNoAndDeletedFalseOrderBySortOrderAscIdAsc(game.getId(), game.getNightNo());
        int mandatoryCount = (int) tasks.stream().filter(ClocktowerGameNightTaskPo::isMandatory).count();
        int doneCount = (int) tasks.stream().filter(task -> STATUS_DONE.equals(task.getStatus())).count();
        int skippedCount = (int) tasks.stream().filter(task -> STATUS_SKIPPED.equals(task.getStatus())).count();
        int pendingMandatoryCount = (int) tasks.stream()
                .filter(ClocktowerGameNightTaskPo::isMandatory)
                .filter(task -> !COMPLETED_STATUSES.contains(task.getStatus()))
                .count();
        return new ClocktowerGameNightTaskSummary(mandatoryCount, pendingMandatoryCount, doneCount, skippedCount);
    }

    @Override
    public void initializeNightTasks(ClocktowerGamePo game) {
        // Task 09 owns script-specific night-task generation. Task 08 only needs the extension point.
    }
}
```

- [ ] **Step 4: Implement victory service**

Create `ClocktowerGameVictoryServiceImpl`:

```java
package top.egon.mario.clocktower.game.flow.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.egon.mario.clocktower.game.flow.dto.ClocktowerGameVictoryResult;
import top.egon.mario.clocktower.game.flow.service.ClocktowerGameVictoryService;
import top.egon.mario.clocktower.game.po.ClocktowerGamePo;
import top.egon.mario.clocktower.game.po.ClocktowerGameSeatPo;
import top.egon.mario.clocktower.game.repository.ClocktowerGameSeatRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ClocktowerGameVictoryServiceImpl implements ClocktowerGameVictoryService {

    private static final String SEAT_STATUS_ACTIVE = "ACTIVE";
    private static final String ROLE_TYPE_DEMON = "DEMON";
    private static final String LIFE_ALIVE = "ALIVE";

    private final ClocktowerGameSeatRepository gameSeatRepository;

    @Override
    public ClocktowerGameVictoryResult evaluate(ClocktowerGamePo game) {
        List<ClocktowerGameSeatPo> activeSeats = gameSeatRepository
                .findByGameIdAndDeletedFalseOrderBySeatNoAsc(game.getId())
                .stream()
                .filter(seat -> SEAT_STATUS_ACTIVE.equals(seat.getStatus()))
                .toList();
        int aliveCount = (int) activeSeats.stream()
                .filter(seat -> LIFE_ALIVE.equals(seat.getLifeStatus()))
                .count();
        long demonCount = activeSeats.stream()
                .filter(seat -> ROLE_TYPE_DEMON.equals(seat.getRoleType()))
                .count();
        long aliveDemonCount = activeSeats.stream()
                .filter(seat -> ROLE_TYPE_DEMON.equals(seat.getRoleType()))
                .filter(seat -> LIFE_ALIVE.equals(seat.getLifeStatus()))
                .count();
        boolean demonAlive = aliveDemonCount > 0;
        Map<String, Object> counters = new LinkedHashMap<>();
        counters.put("aliveCount", aliveCount);
        counters.put("demonCount", demonCount);
        counters.put("aliveDemonCount", aliveDemonCount);
        counters.put("demonAlive", demonAlive);
        if (demonCount > 0 && aliveDemonCount == 0) {
            return ClocktowerGameVictoryResult.ended("GOOD", "ALL_DEMONS_DEAD", aliveCount, false, counters);
        }
        if (aliveCount <= 2 && demonAlive) {
            return ClocktowerGameVictoryResult.ended("EVIL", "TWO_ALIVE_AND_DEMON_ALIVE", aliveCount, true, counters);
        }
        return ClocktowerGameVictoryResult.none(aliveCount, demonAlive, counters);
    }
}
```

- [ ] **Step 5: Add no-op phase signal scheduler**

Create `NoopClocktowerGamePhaseSignalScheduler`:

```java
package top.egon.mario.clocktower.game.flow.service.impl;

import org.springframework.stereotype.Service;
import top.egon.mario.clocktower.game.flow.dto.ClocktowerGamePhaseSignal;
import top.egon.mario.clocktower.game.flow.service.ClocktowerGamePhaseSignalScheduler;

@Service
public class NoopClocktowerGamePhaseSignalScheduler implements ClocktowerGamePhaseSignalScheduler {

    @Override
    public void schedule(ClocktowerGamePhaseSignal signal) {
    }
}
```

- [ ] **Step 6: Compile**

Run:

```bash
./mvnw -Dmaven.build.cache.enabled=false -DskipTests compile
```

Expected: compile passes after the flow service implementation is added in Task 4. If this step fails because `ClocktowerGameFlowService` still has no implementation, continue with Task 4 before re-running the command.

- [ ] **Step 7: Commit after Task 4 passes**

This task's implementation is committed together with Task 4 because the victory tests need `ClocktowerGameFlowServiceImpl` to run.

---

### Task 4: Flow View And Normal Advance

**Files:**
- Create: `be/src/main/java/top/egon/mario/clocktower/game/flow/service/impl/ClocktowerGameFlowServiceImpl.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/game/flow/web/ClocktowerGameFlowController.java`
- Modify: `be/src/test/java/top/egon/mario/clocktower/game/flow/ClocktowerGameFlowServiceTests.java`

- [ ] **Step 1: Add normal flow tests**

Add these test methods to `ClocktowerGameFlowServiceTests`:

```java
@Test
void advanceFirstNightPendingTasksRejected() {
    StartedGame game = startGame();
    ClocktowerGameNightTaskPo task = nightTask(game.gameId(), 1, "IMP:FIRST_NIGHT", "PENDING", true);
    nightTaskRepository.saveAndFlush(task);

    ClocktowerGameFlowView flow = flowService.getFlow(game.gameId(), owner());

    assertThat(flow.advanceAllowed()).isFalse();
    assertThat(flow.blockingReasons()).containsExactly("PENDING_NIGHT_TASKS");
}

@Test
void advanceFirstNightTasksCompleteEntersDayAndStartsMic() {
    StartedGame game = startGame();
    ClocktowerGameNightTaskPo task = nightTask(game.gameId(), 1, "IMP:FIRST_NIGHT", "DONE", true);
    nightTaskRepository.saveAndFlush(task);

    ClocktowerGameAdvanceResult result = flowService.advance(game.gameId(), emptyRequest(), owner());

    assertThat(result.previousPhase()).isEqualTo("FIRST_NIGHT");
    assertThat(result.phase()).isEqualTo("DAY");
    ClocktowerGamePo reloaded = gameRepository.findByIdAndDeletedFalse(game.gameId()).orElseThrow();
    assertThat(reloaded.getPhase()).isEqualTo("DAY");
    assertThat(reloaded.getDayNo()).isEqualTo(1);
    assertThat(micService.getMicSession(game.gameId(), owner()).status()).isEqualTo("ROUND_ROBIN");
    assertThat(eventTypes(game.gameId())).contains("PHASE_CHANGED", "MIC_SESSION_STARTED");
}

@Test
void advanceDayMicOpenRejected() {
    StartedGame game = startDayGame();
    micService.startDayMicSession(game.gameId(), owner());

    ClocktowerGameFlowView flow = flowService.getFlow(game.gameId(), owner());

    assertThat(flow.advanceAllowed()).isFalse();
    assertThat(flow.blockingReasons()).contains("MIC_ROUND_ROBIN_NOT_FINISHED");
}

@Test
void advanceDayMicClosedEntersNomination() {
    StartedGame game = startDayGame();
    micService.startDayMicSession(game.gameId(), owner());
    micService.closeSession(game.gameId(), owner());

    ClocktowerGameAdvanceResult result = flowService.advance(game.gameId(), emptyRequest(), owner());

    assertThat(result.previousPhase()).isEqualTo("DAY");
    assertThat(result.phase()).isEqualTo("NOMINATION");
    assertThat(gameRepository.findByIdAndDeletedFalse(game.gameId()).orElseThrow().getPhase())
            .isEqualTo("NOMINATION");
    assertThat(eventTypes(game.gameId())).contains("PHASE_CHANGED");
}
```

Add helper methods:

```java
private StartedGame startDayGame() {
    StartedGame game = startGame();
    ClocktowerGamePo entity = gameRepository.findByIdAndDeletedFalse(game.gameId()).orElseThrow();
    entity.setPhase("DAY");
    entity.setDayNo(1);
    gameRepository.saveAndFlush(entity);
    return game;
}

private ClocktowerGameNightTaskPo nightTask(Long gameId, int nightNo, String taskKey, String status,
                                            boolean mandatory) {
    ClocktowerGameNightTaskPo task = new ClocktowerGameNightTaskPo();
    task.setGameId(gameId);
    task.setNightNo(nightNo);
    task.setTaskKey(taskKey);
    task.setStatus(status);
    task.setMandatory(mandatory);
    task.setSortOrder(10);
    task.setMetadataJson("{}");
    return task;
}
```

- [ ] **Step 2: Run the failing normal flow tests**

Run:

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerGameFlowServiceTests test
```

Expected: compile fails because `ClocktowerGameFlowServiceImpl` and controller are not implemented.

- [ ] **Step 3: Implement `ClocktowerGameFlowServiceImpl`**

Create `ClocktowerGameFlowServiceImpl` with these injected dependencies:

```java
private final ClocktowerGameRepository gameRepository;
private final ClocktowerGameEventAppender eventAppender;
private final ClocktowerGamePublicMicSessionRepository micSessionRepository;
private final ClocktowerGameNominationRepository nominationRepository;
private final ClocktowerGameExecutionRepository executionRepository;
private final ClocktowerPublicMicService micService;
private final ClocktowerGameLifecycleService lifecycleService;
private final RoomSpaceRepository roomSpaceRepository;
private final ClocktowerRoomAccessPolicy accessPolicy;
private final ClocktowerViewerResolver viewerResolver;
private final ClocktowerGameNightTaskGateway nightTaskGateway;
private final ClocktowerGameVictoryService victoryService;
private final ClocktowerGamePhaseSignalScheduler phaseSignalScheduler;
```

Use these constants:

```java
private static final String STATUS_RUNNING = "RUNNING";
private static final String STATUS_ENDED = "ENDED";
private static final String PHASE_FIRST_NIGHT = "FIRST_NIGHT";
private static final String PHASE_NIGHT = "NIGHT";
private static final String PHASE_DAY = "DAY";
private static final String PHASE_NOMINATION = "NOMINATION";
private static final String PHASE_EXECUTION = "EXECUTION";
private static final String PHASE_ENDED = "ENDED";
private static final String MIC_ROUND_ROBIN = "ROUND_ROBIN";
private static final String MIC_GRAB_MIC = "GRAB_MIC";
private static final String MIC_CLOSED = "CLOSED";
private static final String NOMINATION_OPEN = "OPEN";
private static final String EXECUTION_RESOLVED = "RESOLVED";
private static final String REASON_PENDING_NIGHT_TASKS = "PENDING_NIGHT_TASKS";
private static final String REASON_MIC_SESSION_NOT_FOUND = "MIC_SESSION_NOT_FOUND";
private static final String REASON_MIC_ROUND_ROBIN_NOT_FINISHED = "MIC_ROUND_ROBIN_NOT_FINISHED";
private static final String REASON_MIC_GRAB_MIC_NOT_FINISHED = "MIC_GRAB_MIC_NOT_FINISHED";
private static final String REASON_OPEN_NOMINATION_EXISTS = "OPEN_NOMINATION_EXISTS";
private static final String REASON_EXECUTION_NOT_RESOLVED = "EXECUTION_NOT_RESOLVED";
private static final String REASON_GAME_ALREADY_ENDED = "GAME_ALREADY_ENDED";
private static final String REASON_PHASE_UNSUPPORTED = "GAME_FLOW_PHASE_UNSUPPORTED";
```

Implement the public methods:

```java
@Override
@Transactional(readOnly = true)
public ClocktowerGameFlowView getFlow(Long gameId, RbacPrincipal principal) {
    viewerResolver.resolveGameViewer(gameId, principal);
    ClocktowerGamePo game = gameRepository.findByIdAndDeletedFalse(gameId)
            .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_GAME_NOT_FOUND"));
    return buildFlow(game);
}

@Override
@Transactional
public ClocktowerGameAdvanceResult advance(Long gameId, ClocktowerGameAdvanceRequest request,
                                           RbacPrincipal principal) {
    ClocktowerGamePo game = lockedGame(gameId);
    requireStoryteller(game, principal);
    String previousPhase = game.getPhase();
    ClocktowerGameVictoryResult victory = victoryService.evaluate(game);
    if (victory.ended()) {
        return endForVictory(game, previousPhase, victory, principal);
    }
    ClocktowerGameFlowView flow = buildFlow(game);
    if (!flow.advanceAllowed()) {
        throw new ClocktowerException("CLOCKTOWER_" + flow.blockingReasons().getFirst());
    }
    applyNormalAdvance(game, flow.nextPhase(), principal, false, null, metadata(request));
    ClocktowerGameFlowView nextFlow = buildFlow(game);
    return new ClocktowerGameAdvanceResult(game.getId(), previousPhase, game.getPhase(), true, false, nextFlow);
}

@Override
@Transactional
public ClocktowerGameAdvanceResult forceAdvance(Long gameId, ClocktowerGameAdvanceRequest request,
                                                RbacPrincipal principal) {
    ClocktowerGamePo game = lockedGame(gameId);
    requireStoryteller(game, principal);
    if (request == null || !StringUtils.hasText(request.reason())) {
        throw new ClocktowerException("CLOCKTOWER_FORCE_ADVANCE_REASON_REQUIRED");
    }
    String targetPhase = request.targetPhase();
    if (!Set.of(PHASE_DAY, PHASE_NOMINATION, PHASE_NIGHT, PHASE_ENDED).contains(targetPhase)) {
        throw new ClocktowerException("CLOCKTOWER_FORCE_ADVANCE_TARGET_INVALID");
    }
    String previousPhase = game.getPhase();
    applyNormalAdvance(game, targetPhase, principal, true, request.reason(), metadata(request));
    ClocktowerGameFlowView nextFlow = buildFlow(game);
    return new ClocktowerGameAdvanceResult(game.getId(), previousPhase, game.getPhase(), true, true, nextFlow);
}
```

Build flow with deterministic counters:

```java
private ClocktowerGameFlowView buildFlow(ClocktowerGamePo game) {
    Map<String, Object> counters = new LinkedHashMap<>();
    List<String> blockingReasons = new ArrayList<>();
    String nextPhase = nextPhase(game, counters, blockingReasons);
    ClocktowerGameVictoryResult victory = victoryService.evaluate(game);
    counters.putAll(victory.counters());
    if (victory.ended()) {
        nextPhase = PHASE_ENDED;
        blockingReasons.clear();
    }
    if (STATUS_ENDED.equals(game.getStatus()) || PHASE_ENDED.equals(game.getPhase())) {
        blockingReasons.add(REASON_GAME_ALREADY_ENDED);
        nextPhase = null;
    }
    return new ClocktowerGameFlowView(game.getId(), game.getStatus(), game.getPhase(),
            game.getDayNo(), game.getNightNo(), blockingReasons.isEmpty() && nextPhase != null,
            List.copyOf(blockingReasons), nextPhase, counters);
}
```

Use helpers:

```java
private String nextPhase(ClocktowerGamePo game, Map<String, Object> counters, List<String> blockingReasons) {
    return switch (game.getPhase()) {
        case PHASE_FIRST_NIGHT, PHASE_NIGHT -> nextAfterNight(game, counters, blockingReasons);
        case PHASE_DAY -> nextAfterDay(game, counters, blockingReasons);
        case PHASE_NOMINATION, PHASE_EXECUTION -> nextAfterNomination(game, counters, blockingReasons);
        default -> {
            blockingReasons.add(REASON_PHASE_UNSUPPORTED);
            yield null;
        }
    };
}

private String nextAfterNight(ClocktowerGamePo game, Map<String, Object> counters,
                              List<String> blockingReasons) {
    ClocktowerGameNightTaskSummary summary = nightTaskGateway.summarize(game);
    counters.put("nightMandatoryCount", summary.mandatoryCount());
    counters.put("nightPendingMandatoryCount", summary.pendingMandatoryCount());
    counters.put("nightDoneCount", summary.doneCount());
    counters.put("nightSkippedCount", summary.skippedCount());
    if (!summary.complete()) {
        blockingReasons.add(REASON_PENDING_NIGHT_TASKS);
    }
    return PHASE_DAY;
}

private String nextAfterDay(ClocktowerGamePo game, Map<String, Object> counters,
                            List<String> blockingReasons) {
    ClocktowerGamePublicMicSessionPo session = micSessionRepository
            .findByGameIdAndDayNoAndDeletedFalse(game.getId(), game.getDayNo())
            .orElse(null);
    counters.put("micStatus", session == null ? null : session.getStatus());
    if (session == null) {
        blockingReasons.add(REASON_MIC_SESSION_NOT_FOUND);
    } else if (MIC_ROUND_ROBIN.equals(session.getStatus())) {
        blockingReasons.add(REASON_MIC_ROUND_ROBIN_NOT_FINISHED);
    } else if (MIC_GRAB_MIC.equals(session.getStatus())) {
        blockingReasons.add(REASON_MIC_GRAB_MIC_NOT_FINISHED);
    } else if (!MIC_CLOSED.equals(session.getStatus())) {
        blockingReasons.add(REASON_MIC_ROUND_ROBIN_NOT_FINISHED);
    }
    return PHASE_NOMINATION;
}

private String nextAfterNomination(ClocktowerGamePo game, Map<String, Object> counters,
                                   List<String> blockingReasons) {
    boolean openNomination = nominationRepository
            .findTopByGameIdAndStatusAndDeletedFalseOrderByIdDesc(game.getId(), NOMINATION_OPEN)
            .isPresent();
    counters.put("openNominationExists", openNomination);
    if (openNomination) {
        blockingReasons.add(REASON_OPEN_NOMINATION_EXISTS);
    }
    ClocktowerGameExecutionPo execution = executionRepository
            .findByGameIdAndDayNoAndDeletedFalse(game.getId(), game.getDayNo())
            .orElse(null);
    counters.put("executionStatus", execution == null ? null : execution.getStatus());
    if (execution == null || !EXECUTION_RESOLVED.equals(execution.getStatus())) {
        blockingReasons.add(REASON_EXECUTION_NOT_RESOLVED);
    }
    return PHASE_NIGHT;
}
```

Apply transitions:

```java
private void applyNormalAdvance(ClocktowerGamePo game, String targetPhase, RbacPrincipal principal,
                                boolean forced, String reason, Map<String, Object> metadata) {
    String previousPhase = game.getPhase();
    Instant now = Instant.now();
    if (PHASE_DAY.equals(targetPhase)) {
        game.setPhase(PHASE_DAY);
        if (PHASE_FIRST_NIGHT.equals(previousPhase) && game.getDayNo() <= 0) {
            game.setDayNo(1);
        } else if (PHASE_NIGHT.equals(previousPhase)) {
            game.setDayNo(game.getDayNo() + 1);
        } else if (game.getDayNo() <= 0) {
            game.setDayNo(1);
        }
    } else if (PHASE_NOMINATION.equals(targetPhase)) {
        game.setPhase(PHASE_NOMINATION);
    } else if (PHASE_NIGHT.equals(targetPhase)) {
        game.setPhase(PHASE_NIGHT);
        if (!PHASE_NIGHT.equals(previousPhase)) {
            game.setNightNo(game.getNightNo() + 1);
        }
        nightTaskGateway.initializeNightTasks(game);
    } else if (PHASE_ENDED.equals(targetPhase)) {
        lifecycleService.endGame(game.getId(), principal);
        return;
    } else {
        throw new ClocktowerException("CLOCKTOWER_GAME_FLOW_PHASE_UNSUPPORTED");
    }
    game.setLastActiveAt(now);
    gameRepository.saveAndFlush(game);
    appendPhaseChanged(game, previousPhase, forced, reason, metadata, now);
    if (PHASE_DAY.equals(game.getPhase())) {
        micService.startDayMicSession(game.getId(), principal);
    }
    phaseSignalScheduler.schedule(new ClocktowerGamePhaseSignal(
            game.getId(), previousPhase, game.getPhase(), game.getDayNo(), game.getNightNo(), forced, metadata));
}
```

Implement `endForVictory`, `appendPhaseChanged`, `lockedGame`, `requireStoryteller`, and `metadata` with the same exception style used in task 05 and task 07. `appendPhaseChanged` must call:

```java
eventAppender.append(game, "PHASE_CHANGED", null, null, "PUBLIC", List.of(), payload, occurredAt);
```

For `endForVictory`, call `lifecycleService.endGame(game.getId(), principal)`, reload the game, then append `PHASE_CHANGED` with the victory payload.

- [ ] **Step 4: Add the controller**

Create `ClocktowerGameFlowController`:

```java
package top.egon.mario.clocktower.game.flow.web;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.egon.mario.clocktower.common.web.ClocktowerReactiveSupport;
import top.egon.mario.clocktower.game.flow.dto.ClocktowerGameAdvanceRequest;
import top.egon.mario.clocktower.game.flow.dto.ClocktowerGameAdvanceResult;
import top.egon.mario.clocktower.game.flow.dto.ClocktowerGameFlowView;
import top.egon.mario.clocktower.game.flow.service.ClocktowerGameFlowService;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/clocktower/games/{gameId}/flow")
public class ClocktowerGameFlowController extends ClocktowerReactiveSupport {

    private final ClocktowerGameFlowService flowService;

    @GetMapping
    public Mono<ApiResponse<ClocktowerGameFlowView>> getFlow(
            @PathVariable Long gameId,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> flowService.getFlow(gameId, principal));
    }

    @PostMapping("/advance")
    public Mono<ApiResponse<ClocktowerGameAdvanceResult>> advance(
            @PathVariable Long gameId,
            @RequestBody(required = false) ClocktowerGameAdvanceRequest request,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> flowService.advance(gameId, request, principal));
    }

    @PostMapping("/force-advance")
    public Mono<ApiResponse<ClocktowerGameAdvanceResult>> forceAdvance(
            @PathVariable Long gameId,
            @RequestBody ClocktowerGameAdvanceRequest request,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> flowService.forceAdvance(gameId, request, principal));
    }
}
```

- [ ] **Step 5: Run the flow tests**

Run:

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerGameFlowServiceTests test
```

Expected: victory and normal flow tests pass.

- [ ] **Step 6: Commit**

Run:

```bash
git add be/src/main/java/top/egon/mario/clocktower/game/flow \
    be/src/test/java/top/egon/mario/clocktower/game/flow/ClocktowerGameFlowServiceTests.java
git commit -m "feat(clocktower): advance game flow phases"
```

---

### Task 5: Nomination-to-Night And Force Advance Coverage

**Files:**
- Modify: `be/src/test/java/top/egon/mario/clocktower/game/flow/ClocktowerGameFlowServiceTests.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/game/flow/service/impl/ClocktowerGameFlowServiceImpl.java`

- [ ] **Step 1: Add nomination and force-advance tests**

Add these tests to `ClocktowerGameFlowServiceTests`:

```java
@Test
void advanceNominationOpenNominationRejected() {
    StartedGame game = startNominationGameWithOpenNomination();

    ClocktowerGameFlowView flow = flowService.getFlow(game.gameId(), owner());

    assertThat(flow.advanceAllowed()).isFalse();
    assertThat(flow.blockingReasons()).contains("OPEN_NOMINATION_EXISTS");
}

@Test
void advanceNominationExecutionUnresolvedRejected() {
    StartedGame game = startNominationGameWithClosedNomination();

    ClocktowerGameFlowView flow = flowService.getFlow(game.gameId(), owner());

    assertThat(flow.advanceAllowed()).isFalse();
    assertThat(flow.blockingReasons()).contains("EXECUTION_NOT_RESOLVED");
}

@Test
void advanceExecutionExecutionResolvedEntersNight() {
    StartedGame game = startNominationGameWithClosedNomination();
    executionService.resolveExecution(game.gameId(),
            new ClocktowerGameExecutionResolveRequest(false, null, null, "tie"),
            owner());

    ClocktowerGameAdvanceResult result = flowService.advance(game.gameId(), emptyRequest(), owner());

    assertThat(result.previousPhase()).isEqualTo("EXECUTION");
    assertThat(result.phase()).isEqualTo("NIGHT");
    ClocktowerGamePo reloaded = gameRepository.findByIdAndDeletedFalse(game.gameId()).orElseThrow();
    assertThat(reloaded.getPhase()).isEqualTo("NIGHT");
    assertThat(reloaded.getNightNo()).isEqualTo(2);
    assertThat(eventTypes(game.gameId())).contains("PHASE_CHANGED");
}

@Test
void forceAdvanceRequiresReason() {
    StartedGame game = startGame();

    org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            flowService.forceAdvance(game.gameId(), new ClocktowerGameAdvanceRequest("DAY", null, Map.of()), owner()))
            .hasMessageContaining("CLOCKTOWER_FORCE_ADVANCE_REASON_REQUIRED");
}

@Test
void forceAdvanceWritesForcedPhaseEvent() {
    StartedGame game = startGame();

    ClocktowerGameAdvanceResult result = flowService.forceAdvance(game.gameId(),
            new ClocktowerGameAdvanceRequest("DAY", "manual test recovery", Map.of("source", "test")),
            owner());

    assertThat(result.forced()).isTrue();
    assertThat(result.phase()).isEqualTo("DAY");
    assertThat(eventTypes(game.gameId())).contains("PHASE_CHANGED");
}
```

Add helper methods that open and close nominations through existing game action APIs. If the test file does not yet inject action services, add:

```java
@Autowired
private top.egon.mario.clocktower.game.action.service.ClocktowerHumanGameActionService humanActionService;
```

Helpers:

```java
private StartedGame startNominationGameWithOpenNomination() {
    StartedGame game = startDayGame();
    micService.startDayMicSession(game.gameId(), owner());
    micService.closeSession(game.gameId(), owner());
    humanActionService.submit(game.gameId(),
            new top.egon.mario.clocktower.game.action.dto.ClocktowerGameActionRequest(
                    game.seats().getFirst().getId(), "NOMINATE", List.of(game.seats().get(1).getId()),
                    null, null, null, Map.of()),
            principal(11L, "player1"));
    return game;
}

private StartedGame startNominationGameWithClosedNomination() {
    StartedGame game = startNominationGameWithOpenNomination();
    Long nominationId = gameEventRepository
            .findByGameIdAndStatusAndDeletedFalseOrderByEventSeqAsc(game.gameId(), "VISIBLE")
            .stream()
            .filter(event -> "NOMINATION_OPENED".equals(event.getEventType()))
            .map(event -> extractNominationId(event.getPayloadJson()))
            .findFirst()
            .orElseThrow();
    executionService.closeNomination(game.gameId(), nominationId, owner());
    return game;
}

private Long extractNominationId(String payloadJson) {
    try {
        return new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(payloadJson)
                .get("nominationId")
                .asLong();
    } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
        throw new IllegalStateException(ex);
    }
}
```

- [ ] **Step 2: Run the failing tests**

Run:

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerGameFlowServiceTests test
```

Expected: at least one test may fail if `ClocktowerGameFlowServiceImpl` does not yet support force reason validation, `EXECUTION -> NIGHT`, or the no-execution resolved path.

- [ ] **Step 3: Complete the implementation**

Ensure `ClocktowerGameFlowServiceImpl` supports:

```java
case PHASE_NOMINATION, PHASE_EXECUTION -> nextAfterNomination(game, counters, blockingReasons);
```

Ensure `forceAdvance` has:

```java
if (request == null || !StringUtils.hasText(request.reason())) {
    throw new ClocktowerException("CLOCKTOWER_FORCE_ADVANCE_REASON_REQUIRED");
}
```

Ensure forced `PHASE_DAY` creates a mic session:

```java
if (PHASE_DAY.equals(game.getPhase())) {
    micService.startDayMicSession(game.getId(), principal);
}
```

Ensure forced events include payload values:

```java
payload.put("forced", forced);
payload.put("reason", reason);
payload.put("metadata", metadata == null ? Map.of() : metadata);
```

- [ ] **Step 4: Run the targeted flow tests**

Run:

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerGameFlowServiceTests test
```

Expected: pass.

- [ ] **Step 5: Commit**

Run:

```bash
git add be/src/main/java/top/egon/mario/clocktower/game/flow/service/impl/ClocktowerGameFlowServiceImpl.java \
    be/src/test/java/top/egon/mario/clocktower/game/flow/ClocktowerGameFlowServiceTests.java
git commit -m "test(clocktower): cover game flow blockers"
```

---

### Task 6: Final Validation

**Files:**
- Review: all files changed in tasks 1 through 5.

- [ ] **Step 1: Run focused tests**

Run:

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerJpaMappingTests,ClocktowerGameFlowServiceTests test
```

Expected: pass.

- [ ] **Step 2: Run related regression tests from tasks 05 and 07**

Run:

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerGameActionServiceTests,ClocktowerGameNominationServiceTests test
```

Expected: pass.

- [ ] **Step 3: Run full backend tests**

Run:

```bash
./mvnw -Dmaven.build.cache.enabled=false test
```

Expected: pass.

- [ ] **Step 4: Inspect git status**

Run:

```bash
git status --short --branch
```

Expected: only intentional changes are committed; worktree is clean.

- [ ] **Step 5: Report result**

Include:

- commits created
- files changed
- focused and full validation results
- any blockers or skipped validation

---

## Self-Review

Spec coverage:

- New game flow package and APIs: Task 2 and Task 4.
- Minimal game night-task table with audit fields: Task 1.
- FIRST_NIGHT/NIGHT to DAY with mic session creation: Task 4.
- DAY to NOMINATION mic blockers: Task 4.
- NOMINATION/EXECUTION to NIGHT blockers: Task 5.
- Victory checks: Task 3 and Task 4.
- `PHASE_CHANGED` game events: Task 4 and Task 5.
- Agent scheduling extension point: Task 2 and Task 3.
- Legacy room flow untouched: File Structure and task scope.

Placeholder scan:

- The plan contains no unfinished marker words or intentionally blank implementation steps.

Type consistency:

- DTO, interface, service, repository, and test names are consistent across tasks.
- The plan uses `ClocktowerGameNightTaskPo` under `game/night`, while flow integration stays under `game/flow`.
