# Clocktower Agent Private View Memory Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build task 11 so Agent runtime has a player-like private view, event-derived memory, and idempotent `lastSeenEventSeq` refresh before placeholder actions run.

**Architecture:** Add one Flyway migration and a focused Agent memory package, then add an Agent private-view service that does not reuse human principal resolution. Runtime integration stays narrow: validate the Agent instance, refresh memory, and keep task-10 placeholder actions unchanged until task 12.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring Data JPA, Flyway, H2 PostgreSQL mode tests, JUnit 5, AssertJ, Jackson, existing Clocktower game/action/night/runtime services.

---

## File Structure

Create:

- `be/src/main/resources/db/migration/V38__clocktower_agent_memory.sql`: `clocktower_agent_memory` table with audit fields, JSON payloads, indexes, and event-derived idempotency index.
- `be/src/main/java/top/egon/mario/clocktower/agent/memory/constant/ClocktowerAgentMemoryType.java`: memory type constants from the task doc.
- `be/src/main/java/top/egon/mario/clocktower/agent/memory/po/ClocktowerAgentMemoryPo.java`: JPA entity extending `BaseAuditablePo`.
- `be/src/main/java/top/egon/mario/clocktower/agent/memory/repository/ClocktowerAgentMemoryRepository.java`: memory queries for agent view and idempotent refresh.
- `be/src/main/java/top/egon/mario/clocktower/agent/memory/service/ClocktowerAgentMemoryExtractor.java`: rule-based extraction interface.
- `be/src/main/java/top/egon/mario/clocktower/agent/memory/service/ClocktowerAgentMemoryService.java`: runtime refresh boundary.
- `be/src/main/java/top/egon/mario/clocktower/agent/memory/service/impl/RuleBasedClocktowerAgentMemoryExtractor.java`: deterministic event-to-memory extraction.
- `be/src/main/java/top/egon/mario/clocktower/agent/memory/service/impl/ClocktowerAgentMemoryServiceImpl.java`: transactional refresh, idempotent save, metadata update.
- `be/src/main/java/top/egon/mario/clocktower/agent/view/dto/AgentPublicSeatView.java`
- `be/src/main/java/top/egon/mario/clocktower/agent/view/dto/AgentVisibleEventView.java`
- `be/src/main/java/top/egon/mario/clocktower/agent/view/dto/AgentPrivateInfoView.java`
- `be/src/main/java/top/egon/mario/clocktower/agent/view/dto/AgentMemoryView.java`
- `be/src/main/java/top/egon/mario/clocktower/agent/view/dto/AgentLegalIntentView.java`
- `be/src/main/java/top/egon/mario/clocktower/agent/view/dto/AgentPrivateView.java`
- `be/src/main/java/top/egon/mario/clocktower/agent/view/service/ClocktowerAgentPrivateViewService.java`
- `be/src/main/java/top/egon/mario/clocktower/agent/view/service/ClocktowerRoleVisionPolicy.java`
- `be/src/main/java/top/egon/mario/clocktower/agent/view/service/impl/ClocktowerAgentPrivateViewServiceImpl.java`
- `be/src/test/java/top/egon/mario/clocktower/agent/memory/ClocktowerAgentMemoryServiceTests.java`: memory extraction, idempotency, metadata refresh, runtime refresh coverage.
- `be/src/test/java/top/egon/mario/clocktower/agent/view/ClocktowerAgentPrivateViewServiceTests.java`: normal/Spy/private-info visibility coverage.

Modify:

- `be/src/main/java/top/egon/mario/clocktower/agent/repository/ClocktowerAgentInstanceRepository.java`: add locked Agent instance lookup for refresh.
- `be/src/main/java/top/egon/mario/clocktower/agent/runtime/ClocktowerAgentRuntime.java`: call memory refresh before placeholder action switch.
- `be/src/main/java/top/egon/mario/clocktower/game/repository/ClocktowerGameEventRepository.java`: add event-seq window query.
- `be/src/main/java/top/egon/mario/clocktower/game/night/repository/ClocktowerGameNightTaskRepository.java`: reuse current actor-seat task lookup for legal intents.
- `be/src/main/java/top/egon/mario/clocktower/game/nomination/repository/ClocktowerGameNominationRepository.java`: reuse current open nomination lookup for legal intents.
- `be/src/test/java/top/egon/mario/clocktower/ClocktowerSchemaMigrationTests.java`: migration text and H2 migration coverage.
- `be/src/test/java/top/egon/mario/clocktower/ClocktowerJpaMappingTests.java`: JPA mapping coverage for `ClocktowerAgentMemoryPo`.
- `be/src/test/java/top/egon/mario/clocktower/agent/runtime/ClocktowerAgentTaskRuntimeTests.java`: assert runtime refresh happens before task action.

## Shared Constants

Use string constants, not enums, to match the nearby Clocktower task style:

```java
public final class ClocktowerAgentMemoryType {

    public static final String PUBLIC_SPEECH_SUMMARY = "PUBLIC_SPEECH_SUMMARY";
    public static final String ROLE_CLAIM = "ROLE_CLAIM";
    public static final String PRIVATE_INFO = "PRIVATE_INFO";
    public static final String NOMINATION_OBSERVATION = "NOMINATION_OBSERVATION";
    public static final String VOTE_OBSERVATION = "VOTE_OBSERVATION";
    public static final String DEATH_OBSERVATION = "DEATH_OBSERVATION";
    public static final String TRUST_SCORE = "TRUST_SCORE";
    public static final String SUSPICION_SCORE = "SUSPICION_SCORE";
    public static final String BLUFF_PLAN = "BLUFF_PLAN";
    public static final String EVIL_TEAM_INFO = "EVIL_TEAM_INFO";
    public static final String QUESTION_TO_ASK = "QUESTION_TO_ASK";

    private ClocktowerAgentMemoryType() {
    }
}
```

---

### Task 1: Persistence Red

**Files:**
- Modify: `be/src/test/java/top/egon/mario/clocktower/ClocktowerSchemaMigrationTests.java`
- Modify: `be/src/test/java/top/egon/mario/clocktower/ClocktowerJpaMappingTests.java`

- [ ] **Step 1: Write failing schema tests**

Add the migration path constant beside `AGENT_TASK_MIGRATION`:

```java
private static final Path AGENT_MEMORY_MIGRATION = Path.of(
        "src/main/resources/db/migration/V38__clocktower_agent_memory.sql");
```

Add these tests:

```java
@Test
void agentMemoryMigrationCreatesMemoryTableWithAuditAndIdempotency() throws IOException {
    assertThat(Files.exists(AGENT_MEMORY_MIGRATION)).isTrue();

    String sql = Files.readString(AGENT_MEMORY_MIGRATION);

    assertThat(sql).contains("CREATE TABLE clocktower_agent_memory");
    assertThat(sql).contains("game_id BIGINT NOT NULL");
    assertThat(sql).contains("agent_instance_id BIGINT NOT NULL");
    assertThat(sql).contains("game_seat_id BIGINT NOT NULL");
    assertThat(sql).contains("source_event_id BIGINT");
    assertThat(sql).contains("source_event_seq BIGINT");
    assertThat(sql).contains("memory_type VARCHAR(64) NOT NULL");
    assertThat(sql).contains("visibility VARCHAR(32) NOT NULL DEFAULT 'SELF'");
    assertThat(sql).contains("content_json JSONB NOT NULL DEFAULT '{}'");
    assertThat(sql).contains("metadata_json JSONB NOT NULL DEFAULT '{}'");
    assertThat(sql).contains("created_by BIGINT");
    assertThat(sql).contains("updated_by BIGINT");
    assertThat(sql).contains("version BIGINT NOT NULL DEFAULT 0");
    assertThat(sql).contains("deleted BOOLEAN NOT NULL DEFAULT FALSE");
    assertThat(sql).contains("idx_clocktower_agent_memory_agent");
    assertThat(sql).contains("idx_clocktower_agent_memory_subject");
    assertThat(sql).contains("uk_clocktower_agent_memory_event");
    assertThat(sql).contains("COALESCE(subject_game_seat_id, -1)");
    assertThat(sql).doesNotContain("DROP TABLE");
}

@Test
void agentMemoryMigrationAppliesAndPreventsDuplicateEventMemory() {
    JdbcTemplate jdbcTemplate = migratedJdbcTemplate("clocktower_agent_memory_%s"
            .formatted(UUID.randomUUID()));

    jdbcTemplate.update("""
            insert into clocktower_agent_memory
                (id, game_id, agent_instance_id, game_seat_id, source_event_id, source_event_seq,
                 memory_type, visibility, subject_game_seat_id, content_json, metadata_json,
                 created_at, updated_at)
            values
                (99201, 99001, 99101, 99301, 99401, 7,
                 'ROLE_CLAIM', 'SELF', null, '{"claimedRole":"EMPATH"}', '{}',
                 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """);

    assertThatThrownBy(() -> jdbcTemplate.update("""
            insert into clocktower_agent_memory
                (id, game_id, agent_instance_id, game_seat_id, source_event_id, source_event_seq,
                 memory_type, visibility, subject_game_seat_id, content_json, metadata_json,
                 created_at, updated_at)
            values
                (99202, 99001, 99101, 99301, 99401, 7,
                 'ROLE_CLAIM', 'SELF', null, '{"claimedRole":"EMPATH"}', '{}',
                 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """)).isInstanceOf(Exception.class);

    Map<String, Object> row = jdbcTemplate.queryForMap("""
            select memory_type, visibility, confidence, content_json, deleted
            from clocktower_agent_memory
            where id = 99201
            """);

    assertThat(row.get("memory_type")).isEqualTo("ROLE_CLAIM");
    assertThat(row.get("visibility")).isEqualTo("SELF");
    assertThat(((Number) row.get("confidence")).intValue()).isEqualTo(50);
    assertThat(jsonValue(row, "content_json")).contains("EMPATH");
    assertThat(row.get("deleted")).isEqualTo(false);
}
```

- [ ] **Step 2: Write failing JPA mapping tests**

Add import:

```java
import top.egon.mario.clocktower.agent.memory.po.ClocktowerAgentMemoryPo;
```

Add `ClocktowerAgentMemoryPo.class` to `clocktowerAgentPoClassesAreManagedByJpaContext`.

Add a JSON round-trip test:

```java
@Test
void clocktowerAgentMemoryJsonColumnsRoundTripMinimalStrings() {
    ClocktowerAgentMemoryPo memory = new ClocktowerAgentMemoryPo();
    memory.setGameId(901L);
    memory.setAgentInstanceId(902L);
    memory.setGameSeatId(903L);
    memory.setSourceEventId(904L);
    memory.setSourceEventSeq(5L);
    memory.setMemoryType("ROLE_CLAIM");
    memory.setVisibility("SELF");
    memory.setSubjectGameSeatId(903L);
    memory.setContentJson("{\"claimedRole\":\"EMPATH\"}");
    memory.setConfidence(80);
    memory.setDayNo(1);
    memory.setNightNo(0);
    memory.setMetadataJson("{\"source\":\"test\"}");
    entityManager.persist(memory);

    entityManager.flush();
    entityManager.clear();

    ClocktowerAgentMemoryPo reloaded = entityManager.find(ClocktowerAgentMemoryPo.class, memory.getId());
    assertThat(reloaded.getGameId()).isEqualTo(901L);
    assertThat(reloaded.getAgentInstanceId()).isEqualTo(902L);
    assertThat(reloaded.getGameSeatId()).isEqualTo(903L);
    assertThat(reloaded.getSourceEventId()).isEqualTo(904L);
    assertThat(reloaded.getSourceEventSeq()).isEqualTo(5L);
    assertThat(reloaded.getMemoryType()).isEqualTo("ROLE_CLAIM");
    assertThat(reloaded.getVisibility()).isEqualTo("SELF");
    assertThat(reloaded.getSubjectGameSeatId()).isEqualTo(903L);
    assertThat(reloaded.getContentJson()).contains("EMPATH");
    assertThat(reloaded.getConfidence()).isEqualTo(80);
    assertThat(reloaded.getMetadataJson()).contains("test");
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run from `be/`:

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerSchemaMigrationTests,ClocktowerJpaMappingTests test
```

Expected: FAIL because `V38__clocktower_agent_memory.sql` and `ClocktowerAgentMemoryPo` do not exist.

- [ ] **Step 4: Commit red tests**

```bash
git add be/src/test/java/top/egon/mario/clocktower/ClocktowerSchemaMigrationTests.java \
        be/src/test/java/top/egon/mario/clocktower/ClocktowerJpaMappingTests.java
git commit -m "test(clocktower): cover agent memory persistence"
```

### Task 2: Persistence Green

**Files:**
- Create: `be/src/main/resources/db/migration/V38__clocktower_agent_memory.sql`
- Create: `be/src/main/java/top/egon/mario/clocktower/agent/memory/constant/ClocktowerAgentMemoryType.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/agent/memory/po/ClocktowerAgentMemoryPo.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/agent/memory/repository/ClocktowerAgentMemoryRepository.java`

- [ ] **Step 1: Add the migration**

Create `V38__clocktower_agent_memory.sql`:

```sql
CREATE TABLE clocktower_agent_memory (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    game_id BIGINT NOT NULL,
    agent_instance_id BIGINT NOT NULL,
    game_seat_id BIGINT NOT NULL,
    source_event_id BIGINT,
    source_event_seq BIGINT,
    memory_type VARCHAR(64) NOT NULL,
    visibility VARCHAR(32) NOT NULL DEFAULT 'SELF',
    subject_game_seat_id BIGINT,
    content_json JSONB NOT NULL DEFAULT '{}',
    confidence INTEGER NOT NULL DEFAULT 50,
    day_no INTEGER NOT NULL DEFAULT 0,
    night_no INTEGER NOT NULL DEFAULT 0,
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_clocktower_agent_memory_agent
    ON clocktower_agent_memory (game_id, agent_instance_id, created_at)
    WHERE deleted = FALSE;

CREATE INDEX idx_clocktower_agent_memory_subject
    ON clocktower_agent_memory (game_id, subject_game_seat_id)
    WHERE deleted = FALSE;

CREATE UNIQUE INDEX uk_clocktower_agent_memory_event
    ON clocktower_agent_memory (
        game_id,
        agent_instance_id,
        source_event_id,
        memory_type,
        COALESCE(subject_game_seat_id, -1),
        deleted
    );
```

- [ ] **Step 2: Add memory type constants**

Create `ClocktowerAgentMemoryType.java` using the exact code in the Shared Constants section.

- [ ] **Step 3: Add the JPA entity**

Create `ClocktowerAgentMemoryPo.java`:

```java
package top.egon.mario.clocktower.agent.memory.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import top.egon.mario.common.entity.BaseAuditablePo;

@Getter
@Setter
@Entity
@Table(name = "clocktower_agent_memory")
public class ClocktowerAgentMemoryPo extends BaseAuditablePo {

    @Column(name = "game_id", nullable = false)
    private Long gameId;

    @Column(name = "agent_instance_id", nullable = false)
    private Long agentInstanceId;

    @Column(name = "game_seat_id", nullable = false)
    private Long gameSeatId;

    @Column(name = "source_event_id")
    private Long sourceEventId;

    @Column(name = "source_event_seq")
    private Long sourceEventSeq;

    @Column(name = "memory_type", nullable = false, length = 64)
    private String memoryType;

    @Column(name = "visibility", nullable = false, length = 32)
    private String visibility = "SELF";

    @Column(name = "subject_game_seat_id")
    private Long subjectGameSeatId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content_json", nullable = false, columnDefinition = "jsonb")
    private String contentJson = "{}";

    @Column(name = "confidence", nullable = false)
    private int confidence = 50;

    @Column(name = "day_no", nullable = false)
    private int dayNo;

    @Column(name = "night_no", nullable = false)
    private int nightNo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";
}
```

- [ ] **Step 4: Add the repository**

Create `ClocktowerAgentMemoryRepository.java`:

```java
package top.egon.mario.clocktower.agent.memory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import top.egon.mario.clocktower.agent.memory.po.ClocktowerAgentMemoryPo;

import java.util.Collection;
import java.util.List;

public interface ClocktowerAgentMemoryRepository extends JpaRepository<ClocktowerAgentMemoryPo, Long> {

    List<ClocktowerAgentMemoryPo> findByGameIdAndAgentInstanceIdAndDeletedFalseOrderByCreatedAtAscIdAsc(
            Long gameId, Long agentInstanceId);

    boolean existsByGameIdAndAgentInstanceIdAndSourceEventIdAndMemoryTypeAndSubjectGameSeatIdAndDeletedFalse(
            Long gameId, Long agentInstanceId, Long sourceEventId, String memoryType, Long subjectGameSeatId);

    @Query("""
            select memory
            from ClocktowerAgentMemoryPo memory
            where memory.gameId = :gameId
              and memory.agentInstanceId = :agentInstanceId
              and memory.sourceEventId = :sourceEventId
              and memory.memoryType = :memoryType
              and memory.deleted = false
              and memory.subjectGameSeatId is null
            """)
    List<ClocktowerAgentMemoryPo> findNullSubjectEventMemory(
            @Param("gameId") Long gameId,
            @Param("agentInstanceId") Long agentInstanceId,
            @Param("sourceEventId") Long sourceEventId,
            @Param("memoryType") String memoryType);

    List<ClocktowerAgentMemoryPo> findByGameIdAndAgentInstanceIdAndMemoryTypeInAndDeletedFalseOrderByCreatedAtAscIdAsc(
            Long gameId, Long agentInstanceId, Collection<String> memoryTypes);
}
```

- [ ] **Step 5: Run persistence tests**

Run from `be/`:

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerSchemaMigrationTests,ClocktowerJpaMappingTests test
```

Expected: PASS.

- [ ] **Step 6: Commit persistence implementation**

```bash
git add be/src/main/resources/db/migration/V38__clocktower_agent_memory.sql \
        be/src/main/java/top/egon/mario/clocktower/agent/memory/constant/ClocktowerAgentMemoryType.java \
        be/src/main/java/top/egon/mario/clocktower/agent/memory/po/ClocktowerAgentMemoryPo.java \
        be/src/main/java/top/egon/mario/clocktower/agent/memory/repository/ClocktowerAgentMemoryRepository.java
git commit -m "feat(clocktower): add agent memory persistence"
```

### Task 3: Private View Red

**Files:**
- Create: `be/src/test/java/top/egon/mario/clocktower/agent/view/ClocktowerAgentPrivateViewServiceTests.java`

- [ ] **Step 1: Add private-view tests**

Create `ClocktowerAgentPrivateViewServiceTests.java`:

```java
package top.egon.mario.clocktower.agent.view;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import top.egon.mario.clocktower.agent.constant.ClocktowerAgentStatus;
import top.egon.mario.clocktower.agent.po.ClocktowerActorPo;
import top.egon.mario.clocktower.agent.po.ClocktowerAgentInstancePo;
import top.egon.mario.clocktower.agent.repository.ClocktowerActorRepository;
import top.egon.mario.clocktower.agent.repository.ClocktowerAgentInstanceRepository;
import top.egon.mario.clocktower.agent.repository.ClocktowerAgentProfileRepository;
import top.egon.mario.clocktower.agent.view.dto.AgentPrivateView;
import top.egon.mario.clocktower.agent.view.service.ClocktowerAgentPrivateViewService;
import top.egon.mario.clocktower.game.po.ClocktowerGamePo;
import top.egon.mario.clocktower.game.po.ClocktowerGameSeatPo;
import top.egon.mario.clocktower.game.repository.ClocktowerGameRepository;
import top.egon.mario.clocktower.game.repository.ClocktowerGameSeatRepository;
import top.egon.mario.clocktower.game.service.ClocktowerGameEventAppender;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.ai.dashscope.api-key=test-api-key",
        "clocktower.agent.worker.runner.enabled=false"
})
class ClocktowerAgentPrivateViewServiceTests {

    @Autowired
    private ClocktowerAgentPrivateViewService privateViewService;

    @Autowired
    private ClocktowerGameRepository gameRepository;

    @Autowired
    private ClocktowerGameSeatRepository gameSeatRepository;

    @Autowired
    private ClocktowerActorRepository actorRepository;

    @Autowired
    private ClocktowerAgentProfileRepository profileRepository;

    @Autowired
    private ClocktowerAgentInstanceRepository agentInstanceRepository;

    @Autowired
    private ClocktowerGameEventAppender eventAppender;

    @Test
    void normalAgentViewHidesGrimoireAndOtherPrivateInfo() {
        TestGame game = createGame("EMPATH");
        eventAppender.append(game.game(), "PUBLIC_SPEECH", game.agentSeat().getId(), null,
                "PUBLIC", List.of(), Map.of("content", "我是共情者"), Instant.now());
        eventAppender.append(game.game(), "PRIVATE_INFO_RECEIVED", game.agentSeat().getId(), game.agentSeat().getId(),
                "PRIVATE", List.of(game.agentSeat().getId()), Map.of("infoType", "EMPATH", "evilCount", 1),
                Instant.now());
        eventAppender.append(game.game(), "PRIVATE_INFO_RECEIVED", game.otherSeat().getId(), game.otherSeat().getId(),
                "PRIVATE", List.of(game.otherSeat().getId()), Map.of("infoType", "CHEF", "evilPairs", 0),
                Instant.now());

        AgentPrivateView view = privateViewService.build(game.game().getId(), game.instance().getId());

        assertThat(view.grimoire()).isEmpty();
        assertThat(view.myRoleCode()).isEqualTo("EMPATH");
        assertThat(view.publicSeats()).hasSize(2);
        assertThat(view.publicSeats()).allSatisfy(seat -> assertThat(seat.roleCode()).isNull());
        assertThat(view.visibleEvents()).extracting("eventType")
                .contains("PUBLIC_SPEECH", "PRIVATE_INFO_RECEIVED");
        assertThat(view.privateInfos()).hasSize(1);
        assertThat(view.privateInfos().getFirst().payload()).containsEntry("infoType", "EMPATH");
    }

    @Test
    void spyAgentViewIncludesGrimoireButStillHidesOtherPrivateInfoMessages() {
        TestGame game = createGame("SPY");
        eventAppender.append(game.game(), "PRIVATE_INFO_RECEIVED", game.otherSeat().getId(), game.otherSeat().getId(),
                "PRIVATE", List.of(game.otherSeat().getId()), Map.of("infoType", "CHEF", "evilPairs", 0),
                Instant.now());

        AgentPrivateView view = privateViewService.build(game.game().getId(), game.instance().getId());

        assertThat(view.grimoire()).hasSize(2);
        assertThat(view.grimoire()).extracting("roleCode").contains("SPY", "CHEF");
        assertThat(view.visibleEvents()).isEmpty();
        assertThat(view.privateInfos()).isEmpty();
    }

    private TestGame createGame(String agentRoleCode) {
        ClocktowerGamePo game = new ClocktowerGamePo();
        game.setRoomId(99001L + System.nanoTime());
        game.setGameNo(1);
        game.setScriptCode("TROUBLE_BREWING");
        game.setStatus("RUNNING");
        game.setPhase("DAY");
        game.setDayNo(1);
        game.setBoardSnapshotJson("{}");
        game.setMetadataJson("{}");
        game = gameRepository.saveAndFlush(game);

        ClocktowerActorPo actor = new ClocktowerActorPo();
        actor.setActorType("AGENT");
        actor.setDisplayName("Agent");
        actor = actorRepository.saveAndFlush(actor);

        ClocktowerAgentInstancePo instance = new ClocktowerAgentInstancePo();
        instance.setRoomId(game.getRoomId());
        instance.setGameId(game.getId());
        instance.setProfileId(profileRepository.findFirstByNameAndDeletedFalse("balanced").orElseThrow().getId());
        instance.setActorId(actor.getId());
        instance.setStatus(ClocktowerAgentStatus.ACTIVE);
        instance.setMetadataJson("{}");
        instance = agentInstanceRepository.saveAndFlush(instance);

        ClocktowerGameSeatPo agentSeat = new ClocktowerGameSeatPo();
        agentSeat.setGameId(game.getId());
        agentSeat.setSeatNo(1);
        agentSeat.setActorId(actor.getId());
        agentSeat.setActorType("AGENT");
        agentSeat.setAgentInstanceId(instance.getId());
        agentSeat.setDisplayName("Agent 1");
        agentSeat.setRoleCode(agentRoleCode);
        agentSeat.setRoleType("MINION");
        agentSeat.setAlignment("EVIL");
        agentSeat.setStatus("ACTIVE");
        agentSeat = gameSeatRepository.saveAndFlush(agentSeat);

        instance.setGameSeatId(agentSeat.getId());
        agentInstanceRepository.saveAndFlush(instance);

        ClocktowerGameSeatPo otherSeat = new ClocktowerGameSeatPo();
        otherSeat.setGameId(game.getId());
        otherSeat.setSeatNo(2);
        otherSeat.setActorType("HUMAN");
        otherSeat.setUserId(88002L);
        otherSeat.setDisplayName("Player 2");
        otherSeat.setRoleCode("CHEF");
        otherSeat.setRoleType("TOWNSFOLK");
        otherSeat.setAlignment("GOOD");
        otherSeat.setStatus("ACTIVE");
        otherSeat = gameSeatRepository.saveAndFlush(otherSeat);

        return new TestGame(game, instance, agentSeat, otherSeat);
    }

    private record TestGame(ClocktowerGamePo game, ClocktowerAgentInstancePo instance,
                            ClocktowerGameSeatPo agentSeat, ClocktowerGameSeatPo otherSeat) {
    }
}
```

- [ ] **Step 2: Run private-view tests to verify they fail**

Run from `be/`:

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerAgentPrivateViewServiceTests test
```

Expected: FAIL because DTOs and `ClocktowerAgentPrivateViewService` do not exist.

- [ ] **Step 3: Commit red tests**

```bash
git add be/src/test/java/top/egon/mario/clocktower/agent/view/ClocktowerAgentPrivateViewServiceTests.java
git commit -m "test(clocktower): cover agent private view visibility"
```

### Task 4: Private View Green

**Files:**
- Create all `be/src/main/java/top/egon/mario/clocktower/agent/view/dto/*.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/agent/view/service/ClocktowerAgentPrivateViewService.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/agent/view/service/ClocktowerRoleVisionPolicy.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/agent/view/service/impl/ClocktowerAgentPrivateViewServiceImpl.java`

- [ ] **Step 1: Add DTO records**

Create the DTO records:

```java
package top.egon.mario.clocktower.agent.view.dto;

public record AgentPublicSeatView(
        Long gameSeatId,
        int seatNo,
        String displayName,
        String roleCode,
        String roleType,
        String alignment,
        String lifeStatus,
        String publicLifeStatus,
        boolean hasDeadVote,
        boolean traveler,
        String actorType,
        boolean agent,
        String status
) {}
```

```java
package top.egon.mario.clocktower.agent.view.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AgentVisibleEventView(
        Long eventId,
        Long eventSeq,
        String eventType,
        String phase,
        int dayNo,
        int nightNo,
        Long actorGameSeatId,
        Long targetGameSeatId,
        String visibility,
        List<Long> visibleGameSeatIds,
        Map<String, Object> payload,
        Instant occurredAt
) {}
```

```java
package top.egon.mario.clocktower.agent.view.dto;

import java.time.Instant;
import java.util.Map;

public record AgentPrivateInfoView(
        Long eventId,
        Long eventSeq,
        String roleCode,
        String taskType,
        Map<String, Object> payload,
        Instant occurredAt
) {}
```

```java
package top.egon.mario.clocktower.agent.view.dto;

import java.time.Instant;
import java.util.Map;

public record AgentMemoryView(
        Long memoryId,
        Long sourceEventId,
        Long sourceEventSeq,
        String memoryType,
        Long subjectGameSeatId,
        Map<String, Object> content,
        int confidence,
        int dayNo,
        int nightNo,
        Instant createdAt
) {}
```

```java
package top.egon.mario.clocktower.agent.view.dto;

import java.util.Map;

public record AgentLegalIntentView(
        String intentType,
        Long taskId,
        Long nominationId,
        Boolean voteValue,
        Map<String, Object> payload
) {}
```

```java
package top.egon.mario.clocktower.agent.view.dto;

import java.util.List;
import java.util.Map;

public record AgentPrivateView(
        Long gameId,
        Long agentInstanceId,
        Long myGameSeatId,
        int mySeatNo,
        String phase,
        int dayNo,
        int nightNo,
        String myRoleCode,
        String myDisplayedRoleCode,
        String myAlignment,
        String myRoleType,
        String lifeStatus,
        String publicLifeStatus,
        boolean hasDeadVote,
        List<AgentPublicSeatView> publicSeats,
        List<AgentPublicSeatView> grimoire,
        List<AgentVisibleEventView> visibleEvents,
        List<AgentPrivateInfoView> privateInfos,
        List<AgentMemoryView> memories,
        List<AgentLegalIntentView> legalIntents,
        Map<String, Object> roleSpecificContext
) {}
```

- [ ] **Step 2: Add service interfaces and role policy**

```java
package top.egon.mario.clocktower.agent.view.service;

import top.egon.mario.clocktower.agent.view.dto.AgentPrivateView;

public interface ClocktowerAgentPrivateViewService {

    AgentPrivateView build(Long gameId, Long agentInstanceId);
}
```

```java
package top.egon.mario.clocktower.agent.view.service;

import org.springframework.stereotype.Service;

@Service
public class ClocktowerRoleVisionPolicy {

    public boolean canSeeGrimoire(String roleCode) {
        return "SPY".equals(roleCode);
    }
}
```

- [ ] **Step 3: Implement `ClocktowerAgentPrivateViewServiceImpl`**

Create implementation with these methods and keep each helper direct:

```java
package top.egon.mario.clocktower.agent.view.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.clocktower.agent.memory.po.ClocktowerAgentMemoryPo;
import top.egon.mario.clocktower.agent.memory.repository.ClocktowerAgentMemoryRepository;
import top.egon.mario.clocktower.agent.po.ClocktowerAgentInstancePo;
import top.egon.mario.clocktower.agent.repository.ClocktowerAgentInstanceRepository;
import top.egon.mario.clocktower.agent.view.dto.AgentLegalIntentView;
import top.egon.mario.clocktower.agent.view.dto.AgentMemoryView;
import top.egon.mario.clocktower.agent.view.dto.AgentPrivateInfoView;
import top.egon.mario.clocktower.agent.view.dto.AgentPrivateView;
import top.egon.mario.clocktower.agent.view.dto.AgentPublicSeatView;
import top.egon.mario.clocktower.agent.view.dto.AgentVisibleEventView;
import top.egon.mario.clocktower.agent.view.service.ClocktowerAgentPrivateViewService;
import top.egon.mario.clocktower.agent.view.service.ClocktowerRoleVisionPolicy;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.clocktower.game.night.po.ClocktowerGameNightTaskPo;
import top.egon.mario.clocktower.game.night.repository.ClocktowerGameNightTaskRepository;
import top.egon.mario.clocktower.game.nomination.po.ClocktowerGameNominationPo;
import top.egon.mario.clocktower.game.nomination.repository.ClocktowerGameNominationRepository;
import top.egon.mario.clocktower.game.po.ClocktowerGameEventPo;
import top.egon.mario.clocktower.game.po.ClocktowerGamePo;
import top.egon.mario.clocktower.game.po.ClocktowerGameSeatPo;
import top.egon.mario.clocktower.game.repository.ClocktowerGameEventRepository;
import top.egon.mario.clocktower.game.repository.ClocktowerGameRepository;
import top.egon.mario.clocktower.game.repository.ClocktowerGameSeatRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ClocktowerAgentPrivateViewServiceImpl implements ClocktowerAgentPrivateViewService {

    private static final TypeReference<List<Long>> LONG_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ClocktowerGameRepository gameRepository;
    private final ClocktowerGameSeatRepository gameSeatRepository;
    private final ClocktowerGameEventRepository gameEventRepository;
    private final ClocktowerAgentInstanceRepository agentInstanceRepository;
    private final ClocktowerAgentMemoryRepository memoryRepository;
    private final ClocktowerGameNominationRepository nominationRepository;
    private final ClocktowerGameNightTaskRepository nightTaskRepository;
    private final ClocktowerRoleVisionPolicy roleVisionPolicy;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public AgentPrivateView build(Long gameId, Long agentInstanceId) {
        ClocktowerAgentInstancePo instance = agentInstanceRepository.findByIdAndDeletedFalse(agentInstanceId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_AGENT_INSTANCE_INVALID"));
        ClocktowerGamePo game = gameRepository.findByIdAndDeletedFalse(gameId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_GAME_NOT_FOUND"));
        ClocktowerGameSeatPo mySeat = gameSeatRepository.findByIdAndGameIdAndDeletedFalse(
                        instance.getGameSeatId(), gameId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_AGENT_SEAT_INVALID"));
        List<ClocktowerGameSeatPo> seats = gameSeatRepository.findByGameIdAndDeletedFalseOrderBySeatNoAsc(gameId);
        List<AgentVisibleEventView> events = visibleEvents(gameId, mySeat.getId());

        return new AgentPrivateView(gameId, instance.getId(), mySeat.getId(), mySeat.getSeatNo(),
                game.getPhase(), game.getDayNo(), game.getNightNo(), mySeat.getRoleCode(),
                mySeat.getRoleCode(), mySeat.getAlignment(), mySeat.getRoleType(), mySeat.getLifeStatus(),
                mySeat.getPublicLifeStatus(), mySeat.isHasDeadVote(), publicSeats(seats),
                grimoire(seats, mySeat), events, privateInfos(events), memories(gameId, instance.getId()),
                legalIntents(game, mySeat), Map.of());
    }

    private List<AgentPublicSeatView> publicSeats(List<ClocktowerGameSeatPo> seats) {
        return seats.stream()
                .map(seat -> seatView(seat, false))
                .toList();
    }

    private List<AgentPublicSeatView> grimoire(List<ClocktowerGameSeatPo> seats, ClocktowerGameSeatPo mySeat) {
        if (!roleVisionPolicy.canSeeGrimoire(mySeat.getRoleCode())) {
            return List.of();
        }
        return seats.stream()
                .map(seat -> seatView(seat, true))
                .toList();
    }

    private AgentPublicSeatView seatView(ClocktowerGameSeatPo seat, boolean revealRole) {
        return new AgentPublicSeatView(seat.getId(), seat.getSeatNo(), seat.getDisplayName(),
                revealRole ? seat.getRoleCode() : null, revealRole ? seat.getRoleType() : null,
                revealRole ? seat.getAlignment() : null, revealRole ? seat.getLifeStatus() : null,
                seat.getPublicLifeStatus(), seat.isHasDeadVote(), seat.isTraveler(), seat.getActorType(),
                "AGENT".equals(seat.getActorType()), seat.getStatus());
    }

    private List<AgentVisibleEventView> visibleEvents(Long gameId, Long myGameSeatId) {
        return gameEventRepository.findByGameIdAndStatusAndDeletedFalseOrderByEventSeqAsc(gameId, "VISIBLE")
                .stream()
                .filter(event -> visibleToAgent(event, myGameSeatId))
                .map(this::eventView)
                .toList();
    }

    private boolean visibleToAgent(ClocktowerGameEventPo event, Long myGameSeatId) {
        if ("PUBLIC".equals(event.getVisibility())) {
            return true;
        }
        if (!"PRIVATE".equals(event.getVisibility())) {
            return false;
        }
        return readLongList(event.getVisibleGameSeatIdsJson()).contains(myGameSeatId);
    }

    private AgentVisibleEventView eventView(ClocktowerGameEventPo event) {
        return new AgentVisibleEventView(event.getId(), event.getEventSeq(), event.getEventType(),
                event.getPhase(), event.getDayNo(), event.getNightNo(), event.getActorGameSeatId(),
                event.getTargetGameSeatId(), event.getVisibility(),
                readLongList(event.getVisibleGameSeatIdsJson()),
                readMap(event.getPayloadJson()), event.getOccurredAt());
    }

    private List<AgentPrivateInfoView> privateInfos(List<AgentVisibleEventView> events) {
        return events.stream()
                .filter(event -> "PRIVATE_INFO_RECEIVED".equals(event.eventType()))
                .map(event -> new AgentPrivateInfoView(event.eventId(), event.eventSeq(),
                        stringValue(event.payload().get("roleCode")),
                        stringValue(event.payload().get("taskType")), event.payload(), event.occurredAt()))
                .toList();
    }

    private List<AgentMemoryView> memories(Long gameId, Long agentInstanceId) {
        return memoryRepository.findByGameIdAndAgentInstanceIdAndDeletedFalseOrderByCreatedAtAscIdAsc(
                        gameId, agentInstanceId)
                .stream()
                .map(this::memoryView)
                .toList();
    }

    private AgentMemoryView memoryView(ClocktowerAgentMemoryPo memory) {
        return new AgentMemoryView(memory.getId(), memory.getSourceEventId(), memory.getSourceEventSeq(),
                memory.getMemoryType(), memory.getSubjectGameSeatId(), readMap(memory.getContentJson()),
                memory.getConfidence(), memory.getDayNo(), memory.getNightNo(), memory.getCreatedAt());
    }

    private List<AgentLegalIntentView> legalIntents(ClocktowerGamePo game, ClocktowerGameSeatPo mySeat) {
        List<AgentLegalIntentView> intents = new ArrayList<>();
        if ("DAY".equals(game.getPhase())) {
            intents.add(new AgentLegalIntentView("PUBLIC_SPEECH", null, null, null, Map.of()));
            intents.add(new AgentLegalIntentView("PASS", null, null, null, Map.of("passType", "MIC_TURN")));
        }
        nominationRepository.findTopByGameIdAndStatusAndDeletedFalseOrderByIdDesc(game.getId(), "OPEN")
                .ifPresent(nomination -> {
                    intents.add(voteIntent(nomination, true));
                    intents.add(voteIntent(nomination, false));
                });
        nightTaskRepository.findByGameIdAndNightNoAndActorGameSeatIdAndDeletedFalseOrderBySortOrderAscIdAsc(
                        game.getId(), game.getNightNo(), mySeat.getId())
                .stream()
                .filter(task -> "PENDING".equals(task.getStatus()) || "CHOSEN".equals(task.getStatus()))
                .forEach(task -> intents.add(new AgentLegalIntentView("NIGHT_CHOICE", task.getId(),
                        null, null, Map.of("taskType", task.getTaskType()))));
        return intents;
    }

    private AgentLegalIntentView voteIntent(ClocktowerGameNominationPo nomination, boolean voteValue) {
        return new AgentLegalIntentView("VOTE", null, nomination.getId(), voteValue,
                Map.of("nomineeGameSeatId", nomination.getNomineeGameSeatId()));
    }

    private List<Long> readLongList(String json) {
        try {
            return objectMapper.readValue(json == null || json.isBlank() ? "[]" : json, LONG_LIST_TYPE);
        } catch (JsonProcessingException ex) {
            throw new ClocktowerException("CLOCKTOWER_AGENT_VIEW_JSON_INVALID");
        }
    }

    private Map<String, Object> readMap(String json) {
        try {
            return objectMapper.readValue(json == null || json.isBlank() ? "{}" : json, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            throw new ClocktowerException("CLOCKTOWER_AGENT_VIEW_JSON_INVALID");
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }
}
```

- [ ] **Step 4: Run private-view tests**

Run from `be/`:

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerAgentPrivateViewServiceTests test
```

Expected: PASS.

- [ ] **Step 5: Commit private-view implementation**

```bash
git add be/src/main/java/top/egon/mario/clocktower/agent/view \
        be/src/test/java/top/egon/mario/clocktower/agent/view/ClocktowerAgentPrivateViewServiceTests.java
git commit -m "feat(clocktower): add agent private view"
```

### Task 5: Memory Service Red

**Files:**
- Create: `be/src/test/java/top/egon/mario/clocktower/agent/memory/ClocktowerAgentMemoryServiceTests.java`
- Modify: `be/src/test/java/top/egon/mario/clocktower/agent/runtime/ClocktowerAgentTaskRuntimeTests.java`

- [ ] **Step 1: Add memory extraction and idempotency tests**

Create `ClocktowerAgentMemoryServiceTests.java` with the same `createGame(...)` helper style as the private-view tests, then add:

```java
@Test
void refreshRecordsPublicSpeechRoleClaimAndPrivateInfoOnce() {
    TestGame game = createGame("EMPATH");
    eventAppender.append(game.game(), "PUBLIC_SPEECH", game.otherSeat().getId(), null,
            "PUBLIC", List.of(), Map.of("content", "我是厨师，昨晚看到 0 对邪恶相邻。"), Instant.now());
    eventAppender.append(game.game(), "PRIVATE_INFO_RECEIVED", game.agentSeat().getId(), game.agentSeat().getId(),
            "PRIVATE", List.of(game.agentSeat().getId()), Map.of("infoType", "EMPATH", "evilCount", 1),
            Instant.now());
    eventAppender.append(game.game(), "PRIVATE_INFO_RECEIVED", game.otherSeat().getId(), game.otherSeat().getId(),
            "PRIVATE", List.of(game.otherSeat().getId()), Map.of("infoType", "CHEF", "evilPairs", 0),
            Instant.now());

    ClocktowerAgentMemoryRefreshResult first = memoryService.refresh(game.game().getId(), game.instance().getId());
    ClocktowerAgentMemoryRefreshResult second = memoryService.refresh(game.game().getId(), game.instance().getId());

    List<ClocktowerAgentMemoryPo> memories = memoryRepository
            .findByGameIdAndAgentInstanceIdAndDeletedFalseOrderByCreatedAtAscIdAsc(
                    game.game().getId(), game.instance().getId());
    ClocktowerAgentInstancePo reloaded = agentInstanceRepository.findByIdAndDeletedFalse(game.instance().getId())
            .orElseThrow();

    assertThat(first.insertedCount()).isEqualTo(3);
    assertThat(second.insertedCount()).isZero();
    assertThat(memories).extracting(ClocktowerAgentMemoryPo::getMemoryType)
            .containsExactlyInAnyOrder("PUBLIC_SPEECH_SUMMARY", "ROLE_CLAIM", "PRIVATE_INFO");
    assertThat(memories).noneSatisfy(memory -> assertThat(memory.getContentJson()).contains("evilPairs"));
    assertThat(reloaded.getMetadataJson()).contains("\"lastSeenEventSeq\":3");
}
```

Add:

```java
@Test
void refreshRecordsNominationVoteAndDeathObservations() {
    TestGame game = createGame("EMPATH");
    eventAppender.append(game.game(), "NOMINATION_OPENED", game.agentSeat().getId(), game.otherSeat().getId(),
            "PUBLIC", List.of(), Map.of("nominationId", 9901L, "nominatorGameSeatId",
                    game.agentSeat().getId(), "nomineeGameSeatId", game.otherSeat().getId()), Instant.now());
    eventAppender.append(game.game(), "VOTE_CAST", game.otherSeat().getId(), game.agentSeat().getId(),
            "PUBLIC", List.of(), Map.of("nominationId", 9901L, "voterGameSeatId",
                    game.otherSeat().getId(), "voteValue", true), Instant.now());
    eventAppender.append(game.game(), "PLAYER_DIED", null, game.otherSeat().getId(),
            "PUBLIC", List.of(), Map.of("targetGameSeatId", game.otherSeat().getId()), Instant.now());

    memoryService.refresh(game.game().getId(), game.instance().getId());

    assertThat(memoryRepository.findByGameIdAndAgentInstanceIdAndDeletedFalseOrderByCreatedAtAscIdAsc(
                    game.game().getId(), game.instance().getId()))
            .extracting(ClocktowerAgentMemoryPo::getMemoryType)
            .containsExactlyInAnyOrder("NOMINATION_OBSERVATION", "VOTE_OBSERVATION", "DEATH_OBSERVATION");
}
```

- [ ] **Step 2: Add runtime refresh assertion**

In `ClocktowerAgentTaskRuntimeTests.workerProcessesMicTurnPassAndMarksTaskDone`, append a visible speech before processing:

```java
humanActionService.submit(game.gameId(),
        new ClocktowerGameActionRequest(humanSeat.getId(), "PUBLIC_SPEECH", List.of(),
                null, null, "我是共情者。", Map.of()),
        principal(11L, "player1"));
```

After worker processing, assert the Agent instance metadata advanced:

```java
ClocktowerAgentInstancePo refreshedInstance = agentInstanceRepository
        .findByGameSeatIdAndDeletedFalse(firstAgentSeat.getId())
        .orElseThrow();
assertThat(refreshedInstance.getMetadataJson()).contains("lastSeenEventSeq");
```

- [ ] **Step 3: Run memory tests to verify they fail**

Run from `be/`:

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerAgentMemoryServiceTests,ClocktowerAgentTaskRuntimeTests test
```

Expected: FAIL because memory service, extractor, and runtime integration do not exist.

- [ ] **Step 4: Commit red tests**

```bash
git add be/src/test/java/top/egon/mario/clocktower/agent/memory/ClocktowerAgentMemoryServiceTests.java \
        be/src/test/java/top/egon/mario/clocktower/agent/runtime/ClocktowerAgentTaskRuntimeTests.java
git commit -m "test(clocktower): cover agent memory refresh"
```

### Task 6: Memory Service Green

**Files:**
- Create: `be/src/main/java/top/egon/mario/clocktower/agent/memory/service/ClocktowerAgentMemoryExtractor.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/agent/memory/service/ClocktowerAgentMemoryService.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/agent/memory/service/impl/RuleBasedClocktowerAgentMemoryExtractor.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/agent/memory/service/impl/ClocktowerAgentMemoryServiceImpl.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/agent/repository/ClocktowerAgentInstanceRepository.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/game/repository/ClocktowerGameEventRepository.java`

- [ ] **Step 1: Add locked Agent lookup**

Modify `ClocktowerAgentInstanceRepository`:

```java
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
```

Add:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("""
        select instance
        from ClocktowerAgentInstancePo instance
        where instance.id = :id
          and instance.deleted = false
        """)
Optional<ClocktowerAgentInstancePo> findLockedByIdAndDeletedFalse(@Param("id") Long id);
```

- [ ] **Step 2: Add event window query**

Modify `ClocktowerGameEventRepository`:

```java
List<ClocktowerGameEventPo> findByGameIdAndEventSeqGreaterThanAndStatusAndDeletedFalseOrderByEventSeqAsc(
        Long gameId, Long eventSeq, String status);
```

- [ ] **Step 3: Add memory service API**

Create `ClocktowerAgentMemoryService.java`:

```java
package top.egon.mario.clocktower.agent.memory.service;

import top.egon.mario.clocktower.agent.runtime.po.ClocktowerAgentTaskPo;

public interface ClocktowerAgentMemoryService {

    ClocktowerAgentMemoryRefreshResult refresh(Long gameId, Long agentInstanceId);

    default ClocktowerAgentMemoryRefreshResult refreshForRuntimeTask(ClocktowerAgentTaskPo task) {
        return refresh(task.getGameId(), task.getAgentInstanceId());
    }

    record ClocktowerAgentMemoryRefreshResult(long lastSeenEventSeq, int insertedCount) {
    }
}
```

Create `ClocktowerAgentMemoryExtractor.java`:

```java
package top.egon.mario.clocktower.agent.memory.service;

import top.egon.mario.clocktower.agent.memory.po.ClocktowerAgentMemoryPo;
import top.egon.mario.clocktower.game.po.ClocktowerGameEventPo;
import top.egon.mario.clocktower.game.po.ClocktowerGameSeatPo;

import java.util.List;

public interface ClocktowerAgentMemoryExtractor {

    List<ClocktowerAgentMemoryPo> extract(ClocktowerGameEventPo event,
                                          Long agentInstanceId,
                                          ClocktowerGameSeatPo agentSeat);
}
```

- [ ] **Step 4: Implement rule-based extractor**

Core behavior for `RuleBasedClocktowerAgentMemoryExtractor`:

```java
private List<ClocktowerAgentMemoryPo> publicSpeech(ClocktowerGameEventPo event,
                                                   Long agentInstanceId,
                                                   ClocktowerGameSeatPo agentSeat,
                                                   Map<String, Object> payload) {
    List<ClocktowerAgentMemoryPo> memories = new ArrayList<>();
    String content = stringValue(payload.get("content"));
    memories.add(memory(event, agentInstanceId, agentSeat, ClocktowerAgentMemoryType.PUBLIC_SPEECH_SUMMARY,
            event.getActorGameSeatId(), Map.of(
                    "actorGameSeatId", event.getActorGameSeatId(),
                    "contentSnippet", snippet(content),
                    "eventSeq", event.getEventSeq()
            ), 60));
    roleClaim(content, event, agentInstanceId, agentSeat)
            .ifPresent(memories::add);
    return memories;
}
```

Use these extraction rules:

```java
return switch (event.getEventType()) {
    case "PUBLIC_SPEECH" -> publicSpeech(event, agentInstanceId, agentSeat, payload);
    case "NOMINATION_OPENED" -> List.of(memory(event, agentInstanceId, agentSeat,
            ClocktowerAgentMemoryType.NOMINATION_OBSERVATION, event.getTargetGameSeatId(), payload, 60));
    case "VOTE_CAST" -> List.of(memory(event, agentInstanceId, agentSeat,
            ClocktowerAgentMemoryType.VOTE_OBSERVATION, event.getActorGameSeatId(), payload, 60));
    case "PRIVATE_INFO_RECEIVED" -> List.of(memory(event, agentInstanceId, agentSeat,
            ClocktowerAgentMemoryType.PRIVATE_INFO, event.getTargetGameSeatId(), payload, 80));
    case "PLAYER_DIED" -> List.of(memory(event, agentInstanceId, agentSeat,
            ClocktowerAgentMemoryType.DEATH_OBSERVATION, event.getTargetGameSeatId(), payload, 70));
    default -> List.of();
};
```

The helper `memory(...)` must set `gameId`, `agentInstanceId`, `gameSeatId`, `sourceEventId`, `sourceEventSeq`, `memoryType`, `visibility`, `subjectGameSeatId`, `contentJson`, `confidence`, `dayNo`, `nightNo`, and `metadataJson`.

- [ ] **Step 5: Implement memory refresh**

`ClocktowerAgentMemoryServiceImpl.refresh(...)` must:

```java
@Override
@Transactional
public ClocktowerAgentMemoryRefreshResult refresh(Long gameId, Long agentInstanceId) {
    ClocktowerAgentInstancePo instance = agentInstanceRepository.findLockedByIdAndDeletedFalse(agentInstanceId)
            .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_AGENT_INSTANCE_INVALID"));
    ClocktowerGameSeatPo agentSeat = gameSeatRepository.findByIdAndGameIdAndDeletedFalse(
                    instance.getGameSeatId(), gameId)
            .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_AGENT_SEAT_INVALID"));
    Map<String, Object> metadata = readMap(instance.getMetadataJson());
    long lastSeen = longValue(metadata.get("lastSeenEventSeq"));
    List<ClocktowerGameEventPo> events = gameEventRepository
            .findByGameIdAndEventSeqGreaterThanAndStatusAndDeletedFalseOrderByEventSeqAsc(
                    gameId, lastSeen, "VISIBLE");
    int inserted = 0;
    long highestEvaluated = lastSeen;
    for (ClocktowerGameEventPo event : events) {
        highestEvaluated = Math.max(highestEvaluated, event.getEventSeq());
        if (!visibleToAgent(event, agentSeat.getId())) {
            continue;
        }
        for (ClocktowerAgentMemoryPo candidate : extractor.extract(event, instance.getId(), agentSeat)) {
            if (!exists(candidate)) {
                memoryRepository.save(candidate);
                inserted++;
            }
        }
    }
    metadata.put("lastSeenEventSeq", highestEvaluated);
    metadata.putIfAbsent("suspicion", Map.of());
    metadata.putIfAbsent("trust", Map.of());
    instance.setMetadataJson(writeJson(metadata));
    agentInstanceRepository.saveAndFlush(instance);
    return new ClocktowerAgentMemoryRefreshResult(highestEvaluated, inserted);
}
```

For null-subject idempotency, `exists(candidate)` must call `findNullSubjectEventMemory(...)` when `candidate.getSubjectGameSeatId() == null`; otherwise call the derived `exists...SubjectGameSeatId...` method.

- [ ] **Step 6: Run memory service tests**

Run from `be/`:

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerAgentMemoryServiceTests test
```

Expected: PASS for memory service tests. Runtime test still fails until Task 7 wires Runtime.

- [ ] **Step 7: Commit memory implementation**

```bash
git add be/src/main/java/top/egon/mario/clocktower/agent/memory/service \
        be/src/main/java/top/egon/mario/clocktower/agent/memory/service/impl \
        be/src/main/java/top/egon/mario/clocktower/agent/repository/ClocktowerAgentInstanceRepository.java \
        be/src/main/java/top/egon/mario/clocktower/game/repository/ClocktowerGameEventRepository.java
git commit -m "feat(clocktower): refresh agent memory from events"
```

### Task 7: Runtime Integration Green

**Files:**
- Modify: `be/src/main/java/top/egon/mario/clocktower/agent/runtime/ClocktowerAgentRuntime.java`
- Modify: `be/src/test/java/top/egon/mario/clocktower/agent/runtime/ClocktowerAgentTaskRuntimeTests.java`

- [ ] **Step 1: Inject memory service**

In `ClocktowerAgentRuntime`, add:

```java
private final ClocktowerAgentMemoryService memoryService;
```

Import:

```java
import top.egon.mario.clocktower.agent.memory.service.ClocktowerAgentMemoryService;
import top.egon.mario.clocktower.agent.memory.service.ClocktowerAgentMemoryService.ClocktowerAgentMemoryRefreshResult;
```

- [ ] **Step 2: Refresh after active/full-auto validation**

In `handle(...)`, after the `ST_APPROVAL` skip block and before the switch:

```java
ClocktowerAgentMemoryRefreshResult memoryRefresh = memoryService.refreshForRuntimeTask(task);

return switch (task.getTriggerType()) {
    case ClocktowerAgentTriggerType.MIC_TURN_STARTED -> passMicTurn(task, memoryRefresh);
    case ClocktowerAgentTriggerType.VOTE_WINDOW_OPENED -> voteFalse(task, memoryRefresh);
    case ClocktowerAgentTriggerType.NIGHT_TASK_OPENED -> autoChooseNightTask(task, memoryRefresh);
    default -> done(withMemoryRefresh(Map.of("actionType", "NOOP", "triggerType", task.getTriggerType()),
            memoryRefresh));
};
```

Adjust action methods to accept `memoryRefresh` and wrap results:

```java
private ClocktowerAgentRuntimeResult passMicTurn(ClocktowerAgentTaskPo task,
                                                 ClocktowerAgentMemoryRefreshResult memoryRefresh) {
    ClocktowerGameActionResponse response = agentGameActionService.submitAgentAction(task.getGameId(),
            task.getAgentInstanceId(), new ClocktowerGameActionRequest(task.getGameSeatId(), "PASS", List.of(),
                    null, null, null, Map.of("passType", "MIC_TURN")));
    return done(withMemoryRefresh(actionResult("PASS", response), memoryRefresh));
}
```

Add helper:

```java
private Map<String, Object> withMemoryRefresh(Map<String, Object> result,
                                              ClocktowerAgentMemoryRefreshResult memoryRefresh) {
    Map<String, Object> enriched = new LinkedHashMap<>(result);
    enriched.put("memoryLastSeenEventSeq", memoryRefresh.lastSeenEventSeq());
    enriched.put("memoryInsertedCount", memoryRefresh.insertedCount());
    return enriched;
}
```

Do not refresh memory for inactive, paused, or approval-required Agents.

- [ ] **Step 3: Run runtime tests**

Run from `be/`:

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerAgentTaskRuntimeTests test
```

Expected: PASS.

- [ ] **Step 4: Commit runtime integration**

```bash
git add be/src/main/java/top/egon/mario/clocktower/agent/runtime/ClocktowerAgentRuntime.java \
        be/src/test/java/top/egon/mario/clocktower/agent/runtime/ClocktowerAgentTaskRuntimeTests.java
git commit -m "feat(clocktower): refresh memory before agent tasks"
```

### Task 8: Final Verification

**Files:**
- Inspect all changed task-11 files.

- [ ] **Step 1: Run targeted backend tests**

Run from `be/`:

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerSchemaMigrationTests,ClocktowerJpaMappingTests,ClocktowerAgentPrivateViewServiceTests,ClocktowerAgentMemoryServiceTests,ClocktowerAgentTaskRuntimeTests test
```

Expected: PASS.

- [ ] **Step 2: Run broader Clocktower Agent slice if targeted tests pass**

Run from `be/`:

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest='top.egon.mario.clocktower.agent.**.*Tests' test
```

Expected: PASS.

- [ ] **Step 3: Inspect scope**

Run:

```bash
git status --short
git log --oneline -8
git diff --stat HEAD~7..HEAD
```

Expected: only task-11 persistence, memory, private-view, runtime integration, and test files changed after the plan commit.

- [ ] **Step 4: Record validation in final response**

Final response must include:

- implementation summary
- changed modules
- commits made for each task
- exact test commands and pass/fail results
- any failures or skipped checks

## Scope Notes

- Do not modify existing Flyway migrations.
- Do not add HTTP endpoints for Agent private view.
- Do not use `actorType=AGENT` to grant grimoire access.
- Do not expose other players' `PRIVATE_INFO_RECEIVED` to Spy; Spy gets grimoire seat facts only.
- Do not implement task 12 heuristic decisions or task 15 LLM audit.
- Keep comments minimal and consistent with nearby Java style.
