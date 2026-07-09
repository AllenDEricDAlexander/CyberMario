# Clocktower Night Task Engine Trouble Brewing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the game-scoped Trouble Brewing v0 night task engine so HUMAN and AGENT seats can submit night choices, ST / rule services can resolve them, and task 08 flow can unblock when mandatory night tasks are complete.

**Architecture:** Extend the task 08 `clocktower_game_night_task` seam with one new migration, then add a focused `clocktower/game/night` domain. `ClocktowerGameNightTaskService` owns task lifecycle, `ClocktowerNightResolutionService` owns effects, and small `RoleSkill` strategies own role-specific target and resolution behavior.

**Tech Stack:** Java 21, Spring Boot, Spring Data JPA, Flyway, H2 PostgreSQL mode tests, JUnit 5, AssertJ, existing Clocktower game/action/flow packages.

---

## File Structure

Create:

- `be/src/main/resources/db/migration/V36__extend_clocktower_game_night_task.sql`: extend the existing task table with task type, choice/result JSON, and resolver actor fields.
- `be/src/main/java/top/egon/mario/clocktower/game/night/dto/ClocktowerNightTaskView.java`: ST / service response for a task.
- `be/src/main/java/top/egon/mario/clocktower/game/night/dto/ClocktowerNightResolveRequest.java`: ST resolve request.
- `be/src/main/java/top/egon/mario/clocktower/game/night/dto/ClocktowerNightSkipRequest.java`: ST skip request.
- `be/src/main/java/top/egon/mario/clocktower/game/night/service/ClocktowerGameNightTaskService.java`: task creation, listing, choice, auto-choice, skip.
- `be/src/main/java/top/egon/mario/clocktower/game/night/service/ClocktowerNightOrderService.java`: present-role night-order lookup.
- `be/src/main/java/top/egon/mario/clocktower/game/night/service/ClocktowerNightResolutionService.java`: ordered resolution of ready tasks.
- `be/src/main/java/top/egon/mario/clocktower/game/night/service/ClocktowerRoleSkillRegistry.java`: role skill lookup.
- `be/src/main/java/top/egon/mario/clocktower/game/night/service/ClocktowerButlerMasterService.java`: focused Butler vote constraint lookup.
- `be/src/main/java/top/egon/mario/clocktower/game/night/service/impl/ClocktowerGameNightTaskServiceImpl.java`: lifecycle implementation.
- `be/src/main/java/top/egon/mario/clocktower/game/night/service/impl/ClocktowerNightOrderServiceImpl.java`: night-order implementation.
- `be/src/main/java/top/egon/mario/clocktower/game/night/service/impl/ClocktowerNightResolutionServiceImpl.java`: resolution implementation.
- `be/src/main/java/top/egon/mario/clocktower/game/night/service/impl/ClocktowerRoleSkillRegistryImpl.java`: registry implementation.
- `be/src/main/java/top/egon/mario/clocktower/game/night/service/impl/ClocktowerButlerMasterServiceImpl.java`: Butler constraint implementation.
- `be/src/main/java/top/egon/mario/clocktower/game/night/role/AvailableTargetSpec.java`: legal target descriptor.
- `be/src/main/java/top/egon/mario/clocktower/game/night/role/NightChoice.java`: normalized submitted choice.
- `be/src/main/java/top/egon/mario/clocktower/game/night/role/NightResolution.java`: role resolution result.
- `be/src/main/java/top/egon/mario/clocktower/game/night/role/NightTaskContext.java`: role execution context.
- `be/src/main/java/top/egon/mario/clocktower/game/night/role/NightTaskSpec.java`: generated task spec.
- `be/src/main/java/top/egon/mario/clocktower/game/night/role/RoleSkill.java`: strategy interface.
- `be/src/main/java/top/egon/mario/clocktower/game/night/role/troublebrewing/AbstractTroubleBrewingRoleSkill.java`: shared target helpers.
- `be/src/main/java/top/egon/mario/clocktower/game/night/role/troublebrewing/PoisonerRoleSkill.java`
- `be/src/main/java/top/egon/mario/clocktower/game/night/role/troublebrewing/MonkRoleSkill.java`
- `be/src/main/java/top/egon/mario/clocktower/game/night/role/troublebrewing/ImpRoleSkill.java`
- `be/src/main/java/top/egon/mario/clocktower/game/night/role/troublebrewing/FortuneTellerRoleSkill.java`
- `be/src/main/java/top/egon/mario/clocktower/game/night/role/troublebrewing/EmpathRoleSkill.java`
- `be/src/main/java/top/egon/mario/clocktower/game/night/role/troublebrewing/UndertakerRoleSkill.java`
- `be/src/main/java/top/egon/mario/clocktower/game/night/role/troublebrewing/FirstNightInfoRoleSkill.java`: Washerwoman / Librarian / Investigator via constructor.
- `be/src/main/java/top/egon/mario/clocktower/game/night/role/troublebrewing/ChefRoleSkill.java`
- `be/src/main/java/top/egon/mario/clocktower/game/night/role/troublebrewing/ButlerRoleSkill.java`
- `be/src/main/java/top/egon/mario/clocktower/game/night/role/troublebrewing/SpyRoleSkill.java`
- `be/src/main/java/top/egon/mario/clocktower/game/night/role/troublebrewing/TroubleBrewingRoleSkillConfiguration.java`: exposes first-night info role beans.
- `be/src/main/java/top/egon/mario/clocktower/game/night/web/ClocktowerGameNightTaskController.java`: ST-only night task endpoints.
- `be/src/test/java/top/egon/mario/clocktower/game/night/ClocktowerGameNightTaskServiceTests.java`
- `be/src/test/java/top/egon/mario/clocktower/game/night/ClocktowerNightResolutionServiceTests.java`

Modify:

- `be/src/main/java/top/egon/mario/clocktower/game/night/po/ClocktowerGameNightTaskPo.java`: add mapped fields.
- `be/src/main/java/top/egon/mario/clocktower/game/night/repository/ClocktowerGameNightTaskRepository.java`: add task lookups and locked task lookup.
- `be/src/main/java/top/egon/mario/clocktower/game/flow/service/impl/ClocktowerGameNightTaskGatewayImpl.java`: delegate initialization to night service.
- `be/src/main/java/top/egon/mario/clocktower/game/service/ClocktowerGameLifecycleServiceImpl.java`: initialize FIRST_NIGHT tasks after start.
- `be/src/main/java/top/egon/mario/clocktower/game/action/service/impl/ClocktowerGameActionExecutorImpl.java`: add `NIGHT_CHOICE`.
- `be/src/main/java/top/egon/mario/clocktower/game/nomination/service/impl/ClocktowerGameVoteServiceImpl.java`: add Butler vote constraint hook.
- `be/src/test/java/top/egon/mario/clocktower/ClocktowerJpaMappingTests.java`
- `be/src/test/java/top/egon/mario/clocktower/ClocktowerSchemaMigrationTests.java`
- `be/src/test/java/top/egon/mario/clocktower/game/action/ClocktowerGameActionServiceTests.java`
- `be/src/test/java/top/egon/mario/clocktower/game/flow/ClocktowerGameFlowServiceTests.java`
- `be/src/test/java/top/egon/mario/clocktower/game/nomination/ClocktowerGameNominationServiceTests.java`

## Shared Constants

Use string constants in implementation classes, not new enums, to match the current task-08 style:

```java
private static final String PHASE_FIRST_NIGHT = "FIRST_NIGHT";
private static final String PHASE_NIGHT = "NIGHT";
private static final String STATUS_PENDING = "PENDING";
private static final String STATUS_CHOSEN = "CHOSEN";
private static final String STATUS_DONE = "DONE";
private static final String STATUS_SKIPPED = "SKIPPED";
private static final String TASK_CHOOSE_TARGET = "CHOOSE_TARGET";
private static final String TASK_RECEIVE_INFO = "RECEIVE_INFO";
private static final String TASK_ST_RESOLVE = "ST_RESOLVE";
```

---

### Task 1: Extend Night Task Persistence

**Files:**
- Create: `be/src/main/resources/db/migration/V36__extend_clocktower_game_night_task.sql`
- Modify: `be/src/main/java/top/egon/mario/clocktower/game/night/po/ClocktowerGameNightTaskPo.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/game/night/repository/ClocktowerGameNightTaskRepository.java`
- Modify: `be/src/test/java/top/egon/mario/clocktower/ClocktowerJpaMappingTests.java`
- Modify: `be/src/test/java/top/egon/mario/clocktower/ClocktowerSchemaMigrationTests.java`

- [ ] **Step 1: Write failing migration coverage**

Add constants and a test to `ClocktowerSchemaMigrationTests`:

```java
private static final Path GAME_NIGHT_TASK_EXTENSION_MIGRATION = Path.of(
        "src/main/resources/db/migration/V36__extend_clocktower_game_night_task.sql");

@Test
void gameNightTaskExtensionMigrationAddsChoiceAndResolutionColumns() throws IOException {
    assertThat(Files.exists(GAME_NIGHT_TASK_EXTENSION_MIGRATION)).isTrue();

    String sql = Files.readString(GAME_NIGHT_TASK_EXTENSION_MIGRATION);

    assertThat(sql).contains("ALTER TABLE clocktower_game_night_task ADD COLUMN task_type VARCHAR(64)");
    assertThat(sql).contains("ALTER TABLE clocktower_game_night_task ADD COLUMN choice_json JSONB");
    assertThat(sql).contains("ALTER TABLE clocktower_game_night_task ADD COLUMN result_json JSONB");
    assertThat(sql).contains("ALTER TABLE clocktower_game_night_task ADD COLUMN resolved_by_actor_id BIGINT");
    assertThat(sql).contains("UPDATE clocktower_game_night_task");
    assertThat(sql).doesNotContain("DROP TABLE");
}

@Test
void gameNightTaskExtensionMigrationAppliesAndStoresChoiceJson() {
    JdbcTemplate jdbcTemplate = migratedJdbcTemplate("clocktower_game_night_task_%s"
            .formatted(UUID.randomUUID()));

    jdbcTemplate.update("""
            insert into clocktower_game_night_task
                (id, game_id, night_no, task_key, actor_game_seat_id, role_code, status,
                 mandatory, sort_order, metadata_json, created_at, updated_at,
                 task_type, choice_json, result_json)
            values
                (99001, 990, 1, 'POISONER:99101:CHOOSE_TARGET', 99101, 'POISONER', 'CHOSEN',
                 true, 1, '{}', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
                 'CHOOSE_TARGET', '{"targetGameSeatIds":[99102]}', '{}')
            """);

    Map<String, Object> row = jdbcTemplate.queryForMap("""
            select task_type, choice_json, result_json
            from clocktower_game_night_task
            where id = 99001
            """);

    assertThat(row.get("task_type").toString()).isEqualTo("CHOOSE_TARGET");
    assertThat(row.get("choice_json").toString()).contains("99102");
    assertThat(row.get("result_json").toString()).contains("{}");
}
```

- [ ] **Step 2: Write failing JPA mapping coverage**

Extend `clocktowerGameNightTaskJsonColumnsRoundTripMinimalStrings` in `ClocktowerJpaMappingTests`:

```java
task.setTaskType("CHOOSE_TARGET");
task.setChoiceJson("{\"targetGameSeatIds\":[902]}");
task.setResultJson("{\"marker\":\"POISONED\"}");
task.setResolvedByActorId(77L);
```

Add assertions after reload:

```java
assertThat(reloaded.getTaskType()).isEqualTo("CHOOSE_TARGET");
assertThat(reloaded.getChoiceJson()).contains("targetGameSeatIds");
assertThat(reloaded.getResultJson()).contains("POISONED");
assertThat(reloaded.getResolvedByActorId()).isEqualTo(77L);
```

- [ ] **Step 3: Run tests to verify they fail**

Run from `be/`:

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerSchemaMigrationTests,ClocktowerJpaMappingTests test
```

Expected: FAIL because `V36__extend_clocktower_game_night_task.sql` and new PO accessors do not exist.

- [ ] **Step 4: Add the migration**

Create `V36__extend_clocktower_game_night_task.sql`:

```sql
ALTER TABLE clocktower_game_night_task ADD COLUMN task_type VARCHAR(64);
ALTER TABLE clocktower_game_night_task ADD COLUMN choice_json JSONB;
ALTER TABLE clocktower_game_night_task ADD COLUMN result_json JSONB;
ALTER TABLE clocktower_game_night_task ADD COLUMN resolved_by_actor_id BIGINT;

UPDATE clocktower_game_night_task
SET task_type = 'ST_RESOLVE',
    choice_json = '{}',
    result_json = '{}'
WHERE task_type IS NULL
   OR choice_json IS NULL
   OR result_json IS NULL;

ALTER TABLE clocktower_game_night_task ALTER COLUMN task_type SET NOT NULL;
ALTER TABLE clocktower_game_night_task ALTER COLUMN task_type SET DEFAULT 'ST_RESOLVE';
ALTER TABLE clocktower_game_night_task ALTER COLUMN choice_json SET NOT NULL;
ALTER TABLE clocktower_game_night_task ALTER COLUMN choice_json SET DEFAULT '{}';
ALTER TABLE clocktower_game_night_task ALTER COLUMN result_json SET NOT NULL;
ALTER TABLE clocktower_game_night_task ALTER COLUMN result_json SET DEFAULT '{}';

CREATE INDEX idx_clocktower_game_night_task_actor_status
    ON clocktower_game_night_task (actor_game_seat_id, status);
```

- [ ] **Step 5: Extend the PO**

Add fields to `ClocktowerGameNightTaskPo`:

```java
@Column(name = "task_type", nullable = false, length = 64)
private String taskType = "ST_RESOLVE";

@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "choice_json", nullable = false, columnDefinition = "jsonb")
private String choiceJson = "{}";

@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "result_json", nullable = false, columnDefinition = "jsonb")
private String resultJson = "{}";

@Column(name = "resolved_by_actor_id")
private Long resolvedByActorId;
```

- [ ] **Step 6: Extend the repository**

Add these methods:

```java
Optional<ClocktowerGameNightTaskPo> findByIdAndGameIdAndDeletedFalse(Long id, Long gameId);

Optional<ClocktowerGameNightTaskPo> findByGameIdAndNightNoAndTaskKeyAndDeletedFalse(
        Long gameId, int nightNo, String taskKey);

List<ClocktowerGameNightTaskPo> findByGameIdAndNightNoAndActorGameSeatIdAndDeletedFalseOrderBySortOrderAscIdAsc(
        Long gameId, int nightNo, Long actorGameSeatId);

@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("""
        select task
        from ClocktowerGameNightTaskPo task
        where task.id = :id
          and task.gameId = :gameId
          and task.deleted = false
        """)
Optional<ClocktowerGameNightTaskPo> findLockedByIdAndGameIdAndDeletedFalse(
        @Param("id") Long id,
        @Param("gameId") Long gameId);
```

Add imports:

```java
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import java.util.Optional;
```

- [ ] **Step 7: Run tests to verify they pass**

Run:

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerSchemaMigrationTests,ClocktowerJpaMappingTests test
```

Expected: PASS, with Flyway validating migration version 36.

- [ ] **Step 8: Commit**

```bash
git add be/src/main/resources/db/migration/V36__extend_clocktower_game_night_task.sql \
  be/src/main/java/top/egon/mario/clocktower/game/night/po/ClocktowerGameNightTaskPo.java \
  be/src/main/java/top/egon/mario/clocktower/game/night/repository/ClocktowerGameNightTaskRepository.java \
  be/src/test/java/top/egon/mario/clocktower/ClocktowerJpaMappingTests.java \
  be/src/test/java/top/egon/mario/clocktower/ClocktowerSchemaMigrationTests.java
git commit -m "feat(clocktower): extend game night task schema"
```

### Task 2: Add Night Domain Contracts

**Files:**
- Create all DTO, service interface, registry, role contract, and simple record files listed in File Structure.

- [ ] **Step 1: Write compile-facing service tests**

Create `ClocktowerGameNightTaskServiceTests` with this shell:

```java
package top.egon.mario.clocktower.game.night;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.clocktower.game.night.service.ClocktowerGameNightTaskService;
import top.egon.mario.clocktower.game.night.service.ClocktowerNightResolutionService;
import top.egon.mario.clocktower.game.night.service.ClocktowerRoleSkillRegistry;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
@Transactional
class ClocktowerGameNightTaskServiceTests {

    @Autowired(required = false)
    private ClocktowerGameNightTaskService taskService;

    @Autowired(required = false)
    private ClocktowerNightResolutionService resolutionService;

    @Autowired(required = false)
    private ClocktowerRoleSkillRegistry roleSkillRegistry;

    @Test
    void nightServicesAreAvailable() {
        assertThat(taskService).isNotNull();
        assertThat(resolutionService).isNotNull();
        assertThat(roleSkillRegistry).isNotNull();
        assertThat(roleSkillRegistry.find("POISONER")).isPresent();
        assertThat(roleSkillRegistry.find("IMP")).isPresent();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerGameNightTaskServiceTests test
```

Expected: FAIL because the interfaces and beans do not exist.

- [ ] **Step 3: Add DTOs**

Create `ClocktowerNightTaskView`:

```java
package top.egon.mario.clocktower.game.night.dto;

import top.egon.mario.clocktower.game.night.po.ClocktowerGameNightTaskPo;

import java.util.Map;

public record ClocktowerNightTaskView(
        Long taskId,
        Long gameId,
        int nightNo,
        Long actorGameSeatId,
        String roleCode,
        String taskType,
        String status,
        boolean mandatory,
        int sortOrder,
        Map<String, Object> choice,
        Map<String, Object> result,
        Map<String, Object> metadata
) {

    public static ClocktowerNightTaskView from(ClocktowerGameNightTaskPo task,
                                               Map<String, Object> choice,
                                               Map<String, Object> result,
                                               Map<String, Object> metadata) {
        return new ClocktowerNightTaskView(task.getId(), task.getGameId(), task.getNightNo(),
                task.getActorGameSeatId(), task.getRoleCode(), task.getTaskType(), task.getStatus(),
                task.isMandatory(), task.getSortOrder(), choice, result, metadata);
    }
}
```

Create `ClocktowerNightResolveRequest`:

```java
package top.egon.mario.clocktower.game.night.dto;

import java.util.Map;

public record ClocktowerNightResolveRequest(
        Map<String, Object> result,
        String note
) {
}
```

Create `ClocktowerNightSkipRequest`:

```java
package top.egon.mario.clocktower.game.night.dto;

public record ClocktowerNightSkipRequest(String reason) {
}
```

- [ ] **Step 4: Add role records and interface**

Create `AvailableTargetSpec`:

```java
package top.egon.mario.clocktower.game.night.role;

public record AvailableTargetSpec(Long gameSeatId, String displayName, boolean selectable, String reason) {
}
```

Create `NightChoice`:

```java
package top.egon.mario.clocktower.game.night.role;

import java.util.List;
import java.util.Map;

public record NightChoice(List<Long> targetGameSeatIds, Map<String, Object> payload) {
}
```

Create `NightResolution`:

```java
package top.egon.mario.clocktower.game.night.role;

import java.util.List;
import java.util.Map;

public record NightResolution(
        Map<String, Object> result,
        List<Map<String, Object>> privateInfos,
        List<Map<String, Object>> storytellerEvents,
        List<Map<String, Object>> publicEvents,
        String status
) {

    public static NightResolution done(Map<String, Object> result) {
        return new NightResolution(result, List.of(), List.of(), List.of(), "DONE");
    }
}
```

Create `NightTaskSpec`:

```java
package top.egon.mario.clocktower.game.night.role;

import java.util.Map;

public record NightTaskSpec(
        String taskType,
        boolean mandatory,
        Map<String, Object> metadata
) {
}
```

Create `NightTaskContext`:

```java
package top.egon.mario.clocktower.game.night.role;

import top.egon.mario.clocktower.game.night.po.ClocktowerGameNightTaskPo;
import top.egon.mario.clocktower.game.po.ClocktowerGamePo;
import top.egon.mario.clocktower.game.po.ClocktowerGameSeatPo;

import java.util.List;
import java.util.Map;

public record NightTaskContext(
        ClocktowerGamePo game,
        ClocktowerGameNightTaskPo task,
        ClocktowerGameSeatPo actorSeat,
        List<ClocktowerGameSeatPo> seats,
        List<ClocktowerGameNightTaskPo> currentNightTasks,
        Map<String, Object> metadata
) {
}
```

Create `RoleSkill`:

```java
package top.egon.mario.clocktower.game.night.role;

import java.util.List;

public interface RoleSkill {

    String roleCode();

    boolean actsOnFirstNight();

    boolean actsOnOtherNights();

    List<NightTaskSpec> createTasks(NightTaskContext context);

    List<AvailableTargetSpec> legalTargets(NightTaskContext context);

    NightChoice autoChoose(NightTaskContext context);

    NightResolution resolve(NightTaskContext context, NightChoice choice);
}
```

- [ ] **Step 5: Add service interfaces**

Create `ClocktowerGameNightTaskService`:

```java
package top.egon.mario.clocktower.game.night.service;

import top.egon.mario.clocktower.game.action.dto.ActorContext;
import top.egon.mario.clocktower.game.action.dto.ClocktowerGameActionResponse;
import top.egon.mario.clocktower.game.action.dto.GameActionCommand;
import top.egon.mario.clocktower.game.night.dto.ClocktowerNightSkipRequest;
import top.egon.mario.clocktower.game.night.dto.ClocktowerNightTaskView;
import top.egon.mario.clocktower.game.po.ClocktowerGamePo;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;

public interface ClocktowerGameNightTaskService {

    void initializeNightTasks(ClocktowerGamePo game);

    List<ClocktowerNightTaskView> currentTasks(Long gameId, RbacPrincipal principal);

    ClocktowerGameActionResponse submitChoice(ClocktowerGamePo game, GameActionCommand command, ActorContext actor);

    ClocktowerGameActionResponse autoChooseTask(Long gameId, Long taskId, Long agentInstanceId);

    ClocktowerNightTaskView skipTask(Long gameId, Long taskId, ClocktowerNightSkipRequest request,
                                     RbacPrincipal principal);
}
```

Create `ClocktowerNightOrderService`:

```java
package top.egon.mario.clocktower.game.night.service;

import top.egon.mario.clocktower.game.po.ClocktowerGamePo;
import top.egon.mario.clocktower.game.po.ClocktowerGameSeatPo;
import top.egon.mario.clocktower.script.po.ClocktowerNightOrderPo;

import java.util.List;

public interface ClocktowerNightOrderService {

    List<ClocktowerNightOrderPo> currentOrders(ClocktowerGamePo game, List<ClocktowerGameSeatPo> seats);
}
```

Create `ClocktowerNightResolutionService`:

```java
package top.egon.mario.clocktower.game.night.service;

import top.egon.mario.clocktower.game.night.dto.ClocktowerNightResolveRequest;
import top.egon.mario.clocktower.game.night.dto.ClocktowerNightTaskView;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;

public interface ClocktowerNightResolutionService {

    ClocktowerNightTaskView resolveTask(Long gameId, Long taskId, ClocktowerNightResolveRequest request,
                                        RbacPrincipal principal);

    List<ClocktowerNightTaskView> resolveReady(Long gameId, RbacPrincipal principal);
}
```

Create `ClocktowerRoleSkillRegistry`:

```java
package top.egon.mario.clocktower.game.night.service;

import top.egon.mario.clocktower.game.night.role.RoleSkill;

import java.util.Optional;

public interface ClocktowerRoleSkillRegistry {

    Optional<RoleSkill> find(String roleCode);
}
```

Create `ClocktowerButlerMasterService`:

```java
package top.egon.mario.clocktower.game.night.service;

import java.util.Optional;

public interface ClocktowerButlerMasterService {

    Optional<Long> currentMasterGameSeatId(Long gameId, Long butlerGameSeatId);
}
```

- [ ] **Step 6: Add minimal beans**

Create `ClocktowerRoleSkillRegistryImpl`:

```java
package top.egon.mario.clocktower.game.night.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.egon.mario.clocktower.game.night.role.RoleSkill;
import top.egon.mario.clocktower.game.night.service.ClocktowerRoleSkillRegistry;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ClocktowerRoleSkillRegistryImpl implements ClocktowerRoleSkillRegistry {

    private final Map<String, RoleSkill> skillByRoleCode;

    public ClocktowerRoleSkillRegistryImpl(List<RoleSkill> skills) {
        this.skillByRoleCode = skills.stream()
                .collect(Collectors.toMap(RoleSkill::roleCode, Function.identity(), (left, right) -> left));
    }

    @Override
    public Optional<RoleSkill> find(String roleCode) {
        return Optional.ofNullable(skillByRoleCode.get(roleCode));
    }
}
```

Create `AbstractTroubleBrewingRoleSkill` with generic helpers:

```java
package top.egon.mario.clocktower.game.night.role.troublebrewing;

import top.egon.mario.clocktower.game.night.role.AvailableTargetSpec;
import top.egon.mario.clocktower.game.night.role.NightChoice;
import top.egon.mario.clocktower.game.night.role.NightTaskContext;
import top.egon.mario.clocktower.game.night.role.NightTaskSpec;
import top.egon.mario.clocktower.game.po.ClocktowerGameSeatPo;

import java.util.List;
import java.util.Map;

abstract class AbstractTroubleBrewingRoleSkill {

    protected static final String TASK_CHOOSE_TARGET = "CHOOSE_TARGET";
    protected static final String TASK_RECEIVE_INFO = "RECEIVE_INFO";
    protected static final String STATUS_DONE = "DONE";

    protected List<NightTaskSpec> targetTask() {
        return List.of(new NightTaskSpec(TASK_CHOOSE_TARGET, true, Map.of()));
    }

    protected List<NightTaskSpec> infoTask() {
        return List.of(new NightTaskSpec(TASK_RECEIVE_INFO, true, Map.of()));
    }

    protected List<AvailableTargetSpec> allActiveTargets(NightTaskContext context) {
        return context.seats().stream()
                .filter(seat -> "ACTIVE".equals(seat.getStatus()))
                .map(seat -> new AvailableTargetSpec(seat.getId(), seat.getDisplayName(), true, null))
                .toList();
    }

    protected NightChoice firstLegalTarget(NightTaskContext context) {
        return new NightChoice(allActiveTargets(context).stream()
                .filter(AvailableTargetSpec::selectable)
                .map(AvailableTargetSpec::gameSeatId)
                .limit(1)
                .toList(), Map.of());
    }

    protected ClocktowerGameSeatPo seat(Long id, NightTaskContext context) {
        return context.seats().stream()
                .filter(candidate -> candidate.getId().equals(id))
                .findFirst()
                .orElseThrow();
    }
}
```

Create minimal `PoisonerRoleSkill` and `ImpRoleSkill` beans so registry test passes:

```java
package top.egon.mario.clocktower.game.night.role.troublebrewing;

import org.springframework.stereotype.Component;
import top.egon.mario.clocktower.game.night.role.AvailableTargetSpec;
import top.egon.mario.clocktower.game.night.role.NightChoice;
import top.egon.mario.clocktower.game.night.role.NightResolution;
import top.egon.mario.clocktower.game.night.role.NightTaskContext;
import top.egon.mario.clocktower.game.night.role.NightTaskSpec;
import top.egon.mario.clocktower.game.night.role.RoleSkill;

import java.util.List;
import java.util.Map;

@Component
public class PoisonerRoleSkill extends AbstractTroubleBrewingRoleSkill implements RoleSkill {
    public String roleCode() { return "POISONER"; }
    public boolean actsOnFirstNight() { return true; }
    public boolean actsOnOtherNights() { return true; }
    public List<NightTaskSpec> createTasks(NightTaskContext context) { return targetTask(); }
    public List<AvailableTargetSpec> legalTargets(NightTaskContext context) { return allActiveTargets(context); }
    public NightChoice autoChoose(NightTaskContext context) { return firstLegalTarget(context); }
    public NightResolution resolve(NightTaskContext context, NightChoice choice) {
        return NightResolution.done(Map.of("marker", "POISONED", "targetGameSeatIds", choice.targetGameSeatIds()));
    }
}
```

```java
package top.egon.mario.clocktower.game.night.role.troublebrewing;

import org.springframework.stereotype.Component;
import top.egon.mario.clocktower.game.night.role.AvailableTargetSpec;
import top.egon.mario.clocktower.game.night.role.NightChoice;
import top.egon.mario.clocktower.game.night.role.NightResolution;
import top.egon.mario.clocktower.game.night.role.NightTaskContext;
import top.egon.mario.clocktower.game.night.role.NightTaskSpec;
import top.egon.mario.clocktower.game.night.role.RoleSkill;

import java.util.List;
import java.util.Map;

@Component
public class ImpRoleSkill extends AbstractTroubleBrewingRoleSkill implements RoleSkill {
    public String roleCode() { return "IMP"; }
    public boolean actsOnFirstNight() { return false; }
    public boolean actsOnOtherNights() { return true; }
    public List<NightTaskSpec> createTasks(NightTaskContext context) { return targetTask(); }
    public List<AvailableTargetSpec> legalTargets(NightTaskContext context) { return allActiveTargets(context); }
    public NightChoice autoChoose(NightTaskContext context) { return firstLegalTarget(context); }
    public NightResolution resolve(NightTaskContext context, NightChoice choice) {
        return NightResolution.done(Map.of("killTargetGameSeatIds", choice.targetGameSeatIds()));
    }
}
```

Add no-op service implementations that satisfy wiring and throw explicit errors for unimplemented operations. Create `ClocktowerGameNightTaskServiceImpl`:

```java
@Service
@RequiredArgsConstructor
public class ClocktowerGameNightTaskServiceImpl implements ClocktowerGameNightTaskService {
    @Override public void initializeNightTasks(ClocktowerGamePo game) { }
    @Override public List<ClocktowerNightTaskView> currentTasks(Long gameId, RbacPrincipal principal) { return List.of(); }
    @Override public ClocktowerGameActionResponse submitChoice(ClocktowerGamePo game, GameActionCommand command, ActorContext actor) {
        return ClocktowerGameActionResponse.rejected("CLOCKTOWER_NIGHT_TASK_SERVICE_NOT_READY");
    }
    @Override public ClocktowerGameActionResponse autoChooseTask(Long gameId, Long taskId, Long agentInstanceId) {
        return ClocktowerGameActionResponse.rejected("CLOCKTOWER_NIGHT_TASK_SERVICE_NOT_READY");
    }
    @Override public ClocktowerNightTaskView skipTask(Long gameId, Long taskId, ClocktowerNightSkipRequest request, RbacPrincipal principal) {
        throw new ClocktowerException("CLOCKTOWER_NIGHT_TASK_SERVICE_NOT_READY");
    }
}
```

Create `ClocktowerNightResolutionServiceImpl`:

```java
package top.egon.mario.clocktower.game.night.service.impl;

import org.springframework.stereotype.Service;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.clocktower.game.night.dto.ClocktowerNightResolveRequest;
import top.egon.mario.clocktower.game.night.dto.ClocktowerNightTaskView;
import top.egon.mario.clocktower.game.night.service.ClocktowerNightResolutionService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;

@Service
public class ClocktowerNightResolutionServiceImpl implements ClocktowerNightResolutionService {

    @Override
    public ClocktowerNightTaskView resolveTask(Long gameId, Long taskId, ClocktowerNightResolveRequest request,
                                               RbacPrincipal principal) {
        throw new ClocktowerException("CLOCKTOWER_NIGHT_RESOLUTION_SERVICE_NOT_READY");
    }

    @Override
    public List<ClocktowerNightTaskView> resolveReady(Long gameId, RbacPrincipal principal) {
        return List.of();
    }
}
```

Create `ClocktowerNightOrderServiceImpl`:

```java
package top.egon.mario.clocktower.game.night.service.impl;

import org.springframework.stereotype.Service;
import top.egon.mario.clocktower.game.night.service.ClocktowerNightOrderService;
import top.egon.mario.clocktower.game.po.ClocktowerGamePo;
import top.egon.mario.clocktower.game.po.ClocktowerGameSeatPo;
import top.egon.mario.clocktower.script.po.ClocktowerNightOrderPo;

import java.util.List;

@Service
public class ClocktowerNightOrderServiceImpl implements ClocktowerNightOrderService {

    @Override
    public List<ClocktowerNightOrderPo> currentOrders(ClocktowerGamePo game, List<ClocktowerGameSeatPo> seats) {
        return List.of();
    }
}
```

Create `ClocktowerButlerMasterServiceImpl`:

```java
package top.egon.mario.clocktower.game.night.service.impl;

import org.springframework.stereotype.Service;
import top.egon.mario.clocktower.game.night.service.ClocktowerButlerMasterService;

import java.util.Optional;

@Service
public class ClocktowerButlerMasterServiceImpl implements ClocktowerButlerMasterService {

    @Override
    public Optional<Long> currentMasterGameSeatId(Long gameId, Long butlerGameSeatId) {
        return Optional.empty();
    }
}
```

- [ ] **Step 7: Run test to verify it passes**

Run:

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerGameNightTaskServiceTests test
```

Expected: PASS for `nightServicesAreAvailable`.

- [ ] **Step 8: Commit**

```bash
git add be/src/main/java/top/egon/mario/clocktower/game/night be/src/test/java/top/egon/mario/clocktower/game/night
git commit -m "feat(clocktower): add night task domain contracts"
```

### Task 3: Generate Trouble Brewing Night Tasks

**Files:**
- Modify: `ClocktowerGameNightTaskServiceImpl`
- Modify: `ClocktowerNightOrderServiceImpl`
- Modify: `ClocktowerGameNightTaskGatewayImpl`
- Modify: `ClocktowerGameLifecycleServiceImpl`
- Modify: `ClocktowerGameNightTaskServiceTests`
- Modify: role skill classes to return concrete first / other night task specs.

- [ ] **Step 1: Add task creation tests**

Replace the service test shell with helpers from existing action/flow tests and add:

```java
@Test
void createFirstNightTasks_troubleBrewing_ordered() {
    StartedGame game = startGameWithRoles(List.of("POISONER", "CHEF", "EMPATH", "FORTUNETELLER", "SPY"));

    List<ClocktowerGameNightTaskPo> tasks = nightTaskRepository
            .findByGameIdAndNightNoAndDeletedFalseOrderBySortOrderAscIdAsc(game.gameId(), 1);

    assertThat(tasks).extracting(ClocktowerGameNightTaskPo::getRoleCode)
            .containsExactly("POISONER", "CHEF", "EMPATH", "FORTUNETELLER", "SPY");
    assertThat(tasks).allSatisfy(task -> {
        assertThat(task.getStatus()).isEqualTo("PENDING");
        assertThat(task.isMandatory()).isTrue();
        assertThat(task.getTaskKey()).contains(task.getRoleCode()).contains(task.getActorGameSeatId().toString());
    });
    assertThat(tasks).extracting(ClocktowerGameNightTaskPo::getTaskType)
            .containsExactly("CHOOSE_TARGET", "RECEIVE_INFO", "RECEIVE_INFO", "CHOOSE_TARGET", "RECEIVE_INFO");
}

@Test
void initializeNightTasks_isIdempotent() {
    StartedGame game = startGameWithRoles(List.of("POISONER", "CHEF", "EMPATH", "FORTUNETELLER", "SPY"));
    ClocktowerGamePo entity = gameRepository.findByIdAndDeletedFalse(game.gameId()).orElseThrow();

    taskService.initializeNightTasks(entity);
    taskService.initializeNightTasks(entity);

    assertThat(nightTaskRepository.findByGameIdAndNightNoAndDeletedFalseOrderBySortOrderAscIdAsc(
            game.gameId(), 1)).hasSize(5);
}

@Test
void createOtherNightTasks_troubleBrewing_ordered() {
    StartedGame game = startGameWithRoles(List.of("POISONER", "MONK", "IMP", "EMPATH", "BUTLER"));
    ClocktowerGamePo entity = gameRepository.findByIdAndDeletedFalse(game.gameId()).orElseThrow();
    entity.setPhase("NIGHT");
    entity.setNightNo(2);
    gameRepository.saveAndFlush(entity);

    taskService.initializeNightTasks(entity);

    List<ClocktowerGameNightTaskPo> tasks = nightTaskRepository
            .findByGameIdAndNightNoAndDeletedFalseOrderBySortOrderAscIdAsc(game.gameId(), 2);
    assertThat(tasks).extracting(ClocktowerGameNightTaskPo::getRoleCode)
            .containsExactly("POISONER", "MONK", "IMP", "EMPATH", "BUTLER");
}
```

Use this helper record and methods in the test:

```java
private record StartedGame(Long roomId, Long gameId, List<ClocktowerGameSeatPo> seats) { }

private StartedGame startGameWithRoles(List<String> roleCodes) {
    ClocktowerRoomResponse room = roomService.createRoom(createRequest(roleCodes, 0), owner());
    for (int index = 0; index < roleCodes.size(); index++) {
        roomService.claimSeat(room.roomId(), index + 1, new ClocktowerSeatClaimRequest("Player " + (index + 1)),
                principal(20L + index, "player" + (index + 1)));
    }
    assignReadyRoles(room.roomId(), roleCodes);
    ClocktowerGameResponse started = gameService.startGame(room.roomId(), owner());
    return new StartedGame(room.roomId(), started.gameId(),
            gameSeatRepository.findByGameIdAndDeletedFalseOrderBySeatNoAsc(started.gameId()));
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerGameNightTaskServiceTests test
```

Expected: FAIL because tasks are not generated.

- [ ] **Step 3: Implement `ClocktowerNightOrderServiceImpl`**

Use this implementation shape:

```java
@Service
@RequiredArgsConstructor
public class ClocktowerNightOrderServiceImpl implements ClocktowerNightOrderService {

    private final ClocktowerNightOrderRepository nightOrderRepository;

    @Override
    public List<ClocktowerNightOrderPo> currentOrders(ClocktowerGamePo game, List<ClocktowerGameSeatPo> seats) {
        ClocktowerScriptCode scriptCode = ClocktowerScriptCode.valueOf(game.getScriptCode());
        ClocktowerNightType nightType = game.getNightNo() <= 1
                ? ClocktowerNightType.FIRST_NIGHT : ClocktowerNightType.OTHER_NIGHT;
        List<String> roleCodes = seats.stream()
                .map(ClocktowerGameSeatPo::getRoleCode)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (roleCodes.isEmpty()) {
            return List.of();
        }
        return nightOrderRepository.findByScriptCodeAndNightTypeAndRoleCodeInAndDeletedFalseOrderBySortOrderAsc(
                scriptCode, nightType, roleCodes);
    }
}
```

Add imports for `ClocktowerNightType`, `ClocktowerScriptCode`, `Objects`, and `List`.

- [ ] **Step 4: Implement first role set task specs**

Add role beans:

- `MonkRoleSkill`: other nights only, `CHOOSE_TARGET`.
- `FortuneTellerRoleSkill`: first and other nights, `CHOOSE_TARGET`.
- `EmpathRoleSkill`: first and other nights, `RECEIVE_INFO`.
- `ChefRoleSkill`: first night only, `RECEIVE_INFO`.
- `ButlerRoleSkill`: first and other nights, `CHOOSE_TARGET`.
- `SpyRoleSkill`: first and other nights, `RECEIVE_INFO`.
- `FirstNightInfoRoleSkill` plus configuration beans for `WASHERWOMAN`, `LIBRARIAN`, and `INVESTIGATOR`.

Each simple bean should follow this exact pattern:

```java
@Component
public class EmpathRoleSkill extends AbstractTroubleBrewingRoleSkill implements RoleSkill {
    public String roleCode() { return "EMPATH"; }
    public boolean actsOnFirstNight() { return true; }
    public boolean actsOnOtherNights() { return true; }
    public List<NightTaskSpec> createTasks(NightTaskContext context) { return infoTask(); }
    public List<AvailableTargetSpec> legalTargets(NightTaskContext context) { return List.of(); }
    public NightChoice autoChoose(NightTaskContext context) { return new NightChoice(List.of(), Map.of()); }
    public NightResolution resolve(NightTaskContext context, NightChoice choice) {
        return NightResolution.done(Map.of("roleCode", roleCode()));
    }
}
```

Use `targetTask()` instead of `infoTask()` for target-choosing roles.

- [ ] **Step 5: Implement `ClocktowerGameNightTaskServiceImpl.initializeNightTasks`**

Constructor dependencies:

```java
private final ClocktowerGameRepository gameRepository;
private final ClocktowerGameSeatRepository gameSeatRepository;
private final ClocktowerGameNightTaskRepository nightTaskRepository;
private final ClocktowerNightOrderService nightOrderService;
private final ClocktowerRoleSkillRegistry roleSkillRegistry;
private final ClocktowerGameEventAppender eventAppender;
private final ObjectMapper objectMapper;
```

Implementation outline:

```java
@Override
@Transactional
public void initializeNightTasks(ClocktowerGamePo game) {
    if (game == null || (!PHASE_FIRST_NIGHT.equals(game.getPhase()) && !PHASE_NIGHT.equals(game.getPhase()))) {
        return;
    }
    List<ClocktowerGameSeatPo> seats = gameSeatRepository.findByGameIdAndDeletedFalseOrderBySeatNoAsc(game.getId());
    Map<String, ClocktowerGameSeatPo> seatByRole = seats.stream()
            .filter(seat -> seat.getRoleCode() != null)
            .collect(Collectors.toMap(ClocktowerGameSeatPo::getRoleCode, Function.identity(), (left, right) -> left));
    List<Map<String, Object>> created = new ArrayList<>();
    List<String> skippedRoles = new ArrayList<>();
    for (ClocktowerNightOrderPo order : nightOrderService.currentOrders(game, seats)) {
        ClocktowerGameSeatPo seat = seatByRole.get(order.getRoleCode());
        if (seat == null) {
            continue;
        }
        RoleSkill skill = roleSkillRegistry.find(order.getRoleCode()).orElse(null);
        if (skill == null || !actsThisNight(game, skill)) {
            skippedRoles.add(order.getRoleCode());
            continue;
        }
        NightTaskContext context = new NightTaskContext(game, null, seat, seats, List.of(), Map.of());
        for (NightTaskSpec spec : skill.createTasks(context)) {
            String taskKey = order.getRoleCode() + ":" + seat.getId() + ":" + spec.taskType();
            if (nightTaskRepository.findByGameIdAndNightNoAndTaskKeyAndDeletedFalse(
                    game.getId(), game.getNightNo(), taskKey).isPresent()) {
                continue;
            }
            ClocktowerGameNightTaskPo task = new ClocktowerGameNightTaskPo();
            task.setGameId(game.getId());
            task.setNightNo(game.getNightNo());
            task.setTaskKey(taskKey);
            task.setActorGameSeatId(seat.getId());
            task.setRoleCode(order.getRoleCode());
            task.setTaskType(spec.taskType());
            task.setStatus(STATUS_PENDING);
            task.setMandatory(spec.mandatory());
            task.setSortOrder(order.getSortOrder());
            task.setMetadataJson(writeJson(spec.metadata()));
            nightTaskRepository.save(task);
            created.add(Map.of("taskKey", taskKey, "roleCode", order.getRoleCode(), "taskType", spec.taskType()));
        }
    }
    nightTaskRepository.flush();
    if (!created.isEmpty() || !skippedRoles.isEmpty()) {
        eventAppender.append(game, "NIGHT_TASKS_CREATED", null, null, "STORYTELLER", List.of(),
                Map.of("nightNo", game.getNightNo(), "created", created, "skippedRoles", skippedRoles),
                Instant.now());
    }
}
```

Add helper:

```java
private boolean actsThisNight(ClocktowerGamePo game, RoleSkill skill) {
    return game.getNightNo() <= 1 ? skill.actsOnFirstNight() : skill.actsOnOtherNights();
}
```

- [ ] **Step 6: Wire gateway and lifecycle**

Change `ClocktowerGameNightTaskGatewayImpl.initializeNightTasks`:

```java
nightTaskService.initializeNightTasks(game);
```

Inject `ClocktowerGameNightTaskService`.

In `ClocktowerGameLifecycleServiceImpl`, inject `ClocktowerGameNightTaskGateway` or `ClocktowerGameNightTaskService`. After the game is saved and game seats are copied in `startGame`, call:

```java
nightTaskGateway.initializeNightTasks(savedGame);
```

Use the variable name already present in that method for the persisted `ClocktowerGamePo`.

- [ ] **Step 7: Run tests to verify they pass**

Run:

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerGameNightTaskServiceTests,ClocktowerGameFlowServiceTests test
```

Expected: PASS. Existing flow tests that manually insert tasks may now also see generated tasks; update those tests to mark generated first-night tasks `DONE` before advancing.

- [ ] **Step 8: Commit**

```bash
git add be/src/main/java/top/egon/mario/clocktower/game/night \
  be/src/main/java/top/egon/mario/clocktower/game/flow/service/impl/ClocktowerGameNightTaskGatewayImpl.java \
  be/src/main/java/top/egon/mario/clocktower/game/service/ClocktowerGameLifecycleServiceImpl.java \
  be/src/test/java/top/egon/mario/clocktower/game/night/ClocktowerGameNightTaskServiceTests.java \
  be/src/test/java/top/egon/mario/clocktower/game/flow/ClocktowerGameFlowServiceTests.java
git commit -m "feat(clocktower): generate trouble brewing night tasks"
```

### Task 4: Submit HUMAN and Agent Night Choices

**Files:**
- Modify: `ClocktowerGameNightTaskServiceImpl`
- Modify: `ClocktowerGameActionExecutorImpl`
- Modify: `ClocktowerAgentGameActionServiceImpl` only if helper method is easiest there; otherwise keep auto-choice in night service.
- Modify: `ClocktowerGameActionServiceTests`
- Modify: `ClocktowerGameNightTaskServiceTests`

- [ ] **Step 1: Add failing choice tests**

Add to `ClocktowerGameNightTaskServiceTests`:

```java
@Test
void humanNightChoice_submitsChoice() {
    StartedGame game = startGameWithRoles(List.of("POISONER", "CHEF", "EMPATH", "FORTUNETELLER", "SPY"));
    ClocktowerGameSeatPo poisoner = game.seats().getFirst();
    ClocktowerGameNightTaskPo task = taskFor(game.gameId(), "POISONER");

    ClocktowerGameActionResponse response = humanActionService.submit(game.gameId(),
            new ClocktowerGameActionRequest(poisoner.getId(), "NIGHT_CHOICE", List.of(game.seats().get(1).getId()),
                    null, null, null, Map.of("taskId", task.getId())),
            principal(20L, "player1"));

    assertThat(response.accepted()).isTrue();
    assertThat(response.event().eventType()).isEqualTo("NIGHT_CHOICE_SUBMITTED");
    ClocktowerGameNightTaskPo reloaded = nightTaskRepository.findById(task.getId()).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo("CHOSEN");
    assertThat(reloaded.getChoiceJson()).contains(game.seats().get(1).getId().toString());
}
```

Add to `ClocktowerGameActionServiceTests`:

```java
@Test
void nightChoiceRequiresOwnTaskAndNightPhase() {
    StartedGame game = startDayGameWithAgents();
    ClocktowerGamePo entity = gameRepository.findByIdAndDeletedFalse(game.gameId()).orElseThrow();
    entity.setPhase("FIRST_NIGHT");
    entity.setNightNo(1);
    gameRepository.saveAndFlush(entity);
    ClocktowerGameSeatPo humanSeat = game.seats().getFirst();
    ClocktowerGameSeatPo agentSeat = game.seats().get(1);
    ClocktowerGameNightTaskPo task = new ClocktowerGameNightTaskPo();
    task.setGameId(game.gameId());
    task.setNightNo(1);
    task.setTaskKey("CHEF:" + agentSeat.getId() + ":RECEIVE_INFO");
    task.setActorGameSeatId(agentSeat.getId());
    task.setRoleCode("CHEF");
    task.setTaskType("RECEIVE_INFO");
    task.setStatus("PENDING");
    task.setMandatory(true);
    task.setSortOrder(1);
    task.setMetadataJson("{}");
    task.setChoiceJson("{}");
    task.setResultJson("{}");
    nightTaskRepository.saveAndFlush(task);

    ClocktowerGameActionResponse response = humanActionService.submit(game.gameId(),
            new ClocktowerGameActionRequest(humanSeat.getId(), "NIGHT_CHOICE", List.of(agentSeat.getId()),
                    null, null, null, Map.of("taskId", task.getId())),
            principal(11L, "player1"));

    assertThat(response.accepted()).isFalse();
    assertThat(response.rejectedCode()).isEqualTo("CLOCKTOWER_NIGHT_TASK_NOT_OWNED");
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerGameNightTaskServiceTests,ClocktowerGameActionServiceTests test
```

Expected: FAIL because `NIGHT_CHOICE` is rejected as unknown or service not ready.

- [ ] **Step 3: Implement choice submission**

In `ClocktowerGameActionExecutorImpl`, inject `ClocktowerGameNightTaskService` and add:

```java
case "NIGHT_CHOICE" -> nightTaskService.submitChoice(game, command, actor);
```

In `ClocktowerGameNightTaskServiceImpl.submitChoice`, implement:

```java
if (!PHASE_FIRST_NIGHT.equals(game.getPhase()) && !PHASE_NIGHT.equals(game.getPhase())) {
    return reject(game, command.actorGameSeatId(), "CLOCKTOWER_NIGHT_CHOICE_PHASE_INVALID");
}
Long taskId = longPayload(command.payload(), "taskId");
if (taskId == null) {
    return reject(game, command.actorGameSeatId(), "CLOCKTOWER_NIGHT_TASK_REQUIRED");
}
ClocktowerGameNightTaskPo task = nightTaskRepository.findLockedByIdAndGameIdAndDeletedFalse(taskId, game.getId())
        .orElse(null);
if (task == null || task.getNightNo() != game.getNightNo()) {
    return reject(game, command.actorGameSeatId(), "CLOCKTOWER_NIGHT_TASK_NOT_CURRENT");
}
if (!Objects.equals(task.getActorGameSeatId(), command.actorGameSeatId())) {
    return reject(game, command.actorGameSeatId(), "CLOCKTOWER_NIGHT_TASK_NOT_OWNED");
}
if (!STATUS_PENDING.equals(task.getStatus())) {
    return reject(game, command.actorGameSeatId(), "CLOCKTOWER_NIGHT_TASK_ALREADY_RESOLVED");
}
ClocktowerGameSeatPo actorSeat = gameSeatRepository.findByIdAndGameIdAndDeletedFalse(
        command.actorGameSeatId(), game.getId()).orElseThrow();
List<ClocktowerGameSeatPo> seats = gameSeatRepository.findByGameIdAndDeletedFalseOrderBySeatNoAsc(game.getId());
RoleSkill skill = roleSkillRegistry.find(task.getRoleCode()).orElse(null);
if (skill == null) {
    return reject(game, command.actorGameSeatId(), "CLOCKTOWER_NIGHT_ROLE_UNSUPPORTED");
}
NightChoice choice = new NightChoice(command.targetGameSeatIds() == null ? List.of() : command.targetGameSeatIds(),
        command.payload() == null ? Map.of() : command.payload());
ClocktowerGameActionResponse targetValidation = validateTargets(game, task, actorSeat, seats, skill, choice);
if (targetValidation != null) {
    return targetValidation;
}
task.setChoiceJson(writeJson(Map.of("targetGameSeatIds", choice.targetGameSeatIds(), "payload", choice.payload())));
task.setStatus(STATUS_CHOSEN);
ClocktowerGameNightTaskPo saved = nightTaskRepository.saveAndFlush(task);
ClocktowerGameEventResponse event = eventAppender.append(game, "NIGHT_CHOICE_SUBMITTED", actorSeat.getId(), null,
        "PRIVATE", List.of(actorSeat.getId()),
        Map.of("taskId", saved.getId(), "roleCode", saved.getRoleCode(), "taskType", saved.getTaskType()),
        Instant.now());
return ClocktowerGameActionResponse.accepted(event);
```

Target validation:

```java
private ClocktowerGameActionResponse validateTargets(ClocktowerGamePo game,
                                                     ClocktowerGameNightTaskPo task,
                                                     ClocktowerGameSeatPo actorSeat,
                                                     List<ClocktowerGameSeatPo> seats,
                                                     RoleSkill skill,
                                                     NightChoice choice) {
    NightTaskContext context = new NightTaskContext(game, task, actorSeat, seats,
            nightTaskRepository.findByGameIdAndNightNoAndDeletedFalseOrderBySortOrderAscIdAsc(
                    game.getId(), game.getNightNo()),
            readMap(task.getMetadataJson()));
    List<AvailableTargetSpec> legalTargets = skill.legalTargets(context);
    if (TASK_RECEIVE_INFO.equals(task.getTaskType()) && choice.targetGameSeatIds().isEmpty()) {
        return null;
    }
    Set<Long> legalIds = legalTargets.stream()
            .filter(AvailableTargetSpec::selectable)
            .map(AvailableTargetSpec::gameSeatId)
            .collect(Collectors.toSet());
    if (choice.targetGameSeatIds().isEmpty()) {
        return reject(game, actorSeat.getId(), "CLOCKTOWER_NIGHT_TARGET_COUNT_INVALID");
    }
    if (!legalIds.containsAll(choice.targetGameSeatIds())) {
        return reject(game, actorSeat.getId(), "CLOCKTOWER_NIGHT_TARGET_INVALID");
    }
    return null;
}
```

- [ ] **Step 4: Implement auto-choice**

Use `ClocktowerAgentInstanceRepository` in `ClocktowerGameNightTaskServiceImpl`:

```java
ClocktowerAgentInstancePo instance = agentInstanceRepository.findByIdAndDeletedFalse(agentInstanceId)
        .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_AGENT_INSTANCE_INVALID"));
ClocktowerGameNightTaskPo task = nightTaskRepository.findByIdAndGameIdAndDeletedFalse(taskId, gameId)
        .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_NIGHT_TASK_NOT_FOUND"));
ClocktowerGamePo game = gameRepository.findByIdAndDeletedFalse(gameId)
        .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_GAME_NOT_FOUND"));
ClocktowerGameSeatPo seat = gameSeatRepository.findByIdAndGameIdAndDeletedFalse(task.getActorGameSeatId(), gameId)
        .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_GAME_SEAT_NOT_FOUND"));
if (!Objects.equals(instance.getGameSeatId(), seat.getId())) {
    throw new ClocktowerException("CLOCKTOWER_AGENT_GAME_SEAT_MISMATCH");
}
RoleSkill skill = roleSkillRegistry.find(task.getRoleCode())
        .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_NIGHT_ROLE_UNSUPPORTED"));
NightChoice choice = skill.autoChoose(new NightTaskContext(game, task, seat,
        gameSeatRepository.findByGameIdAndDeletedFalseOrderBySeatNoAsc(gameId),
        nightTaskRepository.findByGameIdAndNightNoAndDeletedFalseOrderBySortOrderAscIdAsc(gameId, game.getNightNo()),
        readMap(task.getMetadataJson())));
return submitChoice(game, new GameActionCommand(gameId, seat.getId(), "NIGHT_CHOICE",
        choice.targetGameSeatIds(), null, null, null, Map.of("taskId", taskId)), 
        ActorContext.agent(instance.getActorId(), instance.getId()));
```

- [ ] **Step 5: Run tests to verify they pass**

Run:

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerGameNightTaskServiceTests,ClocktowerGameActionServiceTests test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add be/src/main/java/top/egon/mario/clocktower/game/night \
  be/src/main/java/top/egon/mario/clocktower/game/action/service/impl/ClocktowerGameActionExecutorImpl.java \
  be/src/test/java/top/egon/mario/clocktower/game/night/ClocktowerGameNightTaskServiceTests.java \
  be/src/test/java/top/egon/mario/clocktower/game/action/ClocktowerGameActionServiceTests.java
git commit -m "feat(clocktower): submit game night choices"
```

### Task 5: Resolve Marker and Death Roles

**Files:**
- Modify: `ClocktowerNightResolutionServiceImpl`
- Modify: `PoisonerRoleSkill`, `MonkRoleSkill`, `ImpRoleSkill`
- Create/modify: `ClocktowerNightResolutionServiceTests`

- [ ] **Step 1: Add failing resolution tests**

Create `ClocktowerNightResolutionServiceTests` with helpers matching Task 3 and add:

```java
@Test
void poisoner_appliesMarker() {
    StartedGame game = startGameWithRoles(List.of("POISONER", "CHEF", "EMPATH", "FORTUNETELLER", "SPY"));
    ClocktowerGameNightTaskPo poisoner = chooseTask(game, "POISONER", game.seats().get(1).getId());

    resolutionService.resolveReady(game.gameId(), owner());

    ClocktowerGameNightTaskPo reloaded = nightTaskRepository.findById(poisoner.getId()).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo("DONE");
    assertThat(reloaded.getResultJson()).contains("POISONED");
    assertThat(eventTypes(game.gameId())).contains("MARKER_APPLIED");
}

@Test
void impKill_protectedByMonk_noDeath() {
    StartedGame game = startNightTwoWithRoles(List.of("POISONER", "MONK", "IMP", "EMPATH", "BUTLER"));
    Long targetId = game.seats().getFirst().getId();
    chooseTask(game, "MONK", targetId);
    chooseTask(game, "IMP", targetId);

    resolutionService.resolveReady(game.gameId(), owner());

    assertThat(gameSeatRepository.findByIdAndDeletedFalse(targetId).orElseThrow().getLifeStatus()).isEqualTo("ALIVE");
    assertThat(eventTypes(game.gameId())).contains("DEMON_KILL_PROTECTED");
}

@Test
void impKill_unprotected_marksDead() {
    StartedGame game = startNightTwoWithRoles(List.of("POISONER", "MONK", "IMP", "EMPATH", "BUTLER"));
    Long targetId = game.seats().getFirst().getId();
    chooseTask(game, "MONK", game.seats().get(3).getId());
    chooseTask(game, "IMP", targetId);

    resolutionService.resolveReady(game.gameId(), owner());

    ClocktowerGameSeatPo target = gameSeatRepository.findByIdAndDeletedFalse(targetId).orElseThrow();
    assertThat(target.getLifeStatus()).isEqualTo("DEAD");
    assertThat(target.getPublicLifeStatus()).isEqualTo("DEAD");
    assertThat(eventTypes(game.gameId())).contains("PLAYER_DIED");
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerNightResolutionServiceTests test
```

Expected: FAIL because resolution is not implemented.

- [ ] **Step 3: Implement role resolutions**

Update `PoisonerRoleSkill.resolve`:

```java
Long targetId = choice.targetGameSeatIds().getFirst();
return new NightResolution(
        Map.of("marker", "POISONED", "targetGameSeatId", targetId),
        List.of(),
        List.of(Map.of("eventType", "MARKER_APPLIED", "targetGameSeatId", targetId,
                "marker", "POISONED", "sourceRole", roleCode())),
        List.of(),
        STATUS_DONE);
```

Update `MonkRoleSkill.resolve` similarly with marker `PROTECTED`.

Update `ImpRoleSkill.resolve`:

```java
Long targetId = choice.targetGameSeatIds().getFirst();
boolean protectedByMonk = context.currentNightTasks().stream()
        .filter(task -> "MONK".equals(task.getRoleCode()))
        .filter(task -> "DONE".equals(task.getStatus()) || "CHOSEN".equals(task.getStatus()))
        .map(ClocktowerGameNightTaskPo::getResultJson)
        .anyMatch(json -> json != null && json.contains("\"targetGameSeatId\":" + targetId));
boolean soldier = "SOLDIER".equals(seat(targetId, context).getRoleCode());
if (protectedByMonk || soldier) {
    return new NightResolution(
            Map.of("killTargetGameSeatId", targetId, "protected", true),
            List.of(),
            List.of(Map.of("eventType", "DEMON_KILL_PROTECTED", "targetGameSeatId", targetId)),
            List.of(),
            STATUS_DONE);
}
return new NightResolution(
        Map.of("killTargetGameSeatId", targetId, "protected", false),
        List.of(),
        List.of(),
        List.of(Map.of("eventType", "PLAYER_DIED", "targetGameSeatId", targetId)),
        STATUS_DONE);
```

- [ ] **Step 4: Implement `resolveReady`**

Core loop:

```java
ClocktowerGamePo game = lockedGame(gameId);
requireStoryteller(game, principal);
List<ClocktowerGameNightTaskPo> tasks = nightTaskRepository
        .findByGameIdAndNightNoAndDeletedFalseOrderBySortOrderAscIdAsc(game.getId(), game.getNightNo());
List<ClocktowerNightTaskView> resolved = new ArrayList<>();
for (ClocktowerGameNightTaskPo task : tasks) {
    if (!ready(task)) {
        continue;
    }
    resolved.add(resolveLoadedTask(game, task, principal));
}
return resolved;
```

Ready helper:

```java
private boolean ready(ClocktowerGameNightTaskPo task) {
    return (TASK_CHOOSE_TARGET.equals(task.getTaskType()) && STATUS_CHOSEN.equals(task.getStatus()))
            || (TASK_RECEIVE_INFO.equals(task.getTaskType()) && STATUS_PENDING.equals(task.getStatus()));
}
```

`resolveLoadedTask` must:

1. Load actor seat and all game seats.
2. Read `choice_json` into a `NightChoice`.
3. Call role skill `resolve`.
4. Apply public death events by setting target `lifeStatus` and `publicLifeStatus` to `DEAD`.
5. Append `MARKER_APPLIED`, `DEMON_KILL_PROTECTED`, and `PLAYER_DIED` events using `ClocktowerGameEventAppender`.
6. Set `result_json`, `status`, and `completed_at`.

Use this event helper:

```java
private void appendResolutionEvent(ClocktowerGamePo game, Map<String, Object> event) {
    String eventType = event.get("eventType").toString();
    Long targetGameSeatId = number(event.get("targetGameSeatId"));
    String visibility = "PLAYER_DIED".equals(eventType) ? "PUBLIC" : "STORYTELLER";
    eventAppender.append(game, eventType, null, targetGameSeatId, visibility, List.of(), event, Instant.now());
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run:

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerNightResolutionServiceTests test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add be/src/main/java/top/egon/mario/clocktower/game/night \
  be/src/test/java/top/egon/mario/clocktower/game/night/ClocktowerNightResolutionServiceTests.java
git commit -m "feat(clocktower): resolve night marker and death roles"
```

### Task 6: Resolve Information Roles and Spy

**Files:**
- Modify: information role skill classes.
- Modify: `ClocktowerNightResolutionServiceImpl`.
- Modify: `ClocktowerNightResolutionServiceTests`.

- [ ] **Step 1: Add failing private info tests**

Add:

```java
@Test
void fortuneTeller_receivesPrivateInfo() {
    StartedGame game = startGameWithRoles(List.of("FORTUNETELLER", "IMP", "EMPATH", "CHEF", "SPY"));
    chooseTask(game, "FORTUNETELLER", game.seats().get(1).getId(), game.seats().get(2).getId());

    resolutionService.resolveReady(game.gameId(), owner());

    List<ClocktowerGameEventPo> events = gameEventRepository
            .findByGameIdAndStatusAndDeletedFalseOrderByEventSeqAsc(game.gameId(), "VISIBLE");
    assertThat(events).anySatisfy(event -> {
        assertThat(event.getEventType()).isEqualTo("PRIVATE_INFO_RECEIVED");
        assertThat(event.getVisibility()).isEqualTo("PRIVATE");
        assertThat(event.getVisibleGameSeatIdsJson()).contains(game.seats().getFirst().getId().toString());
        assertThat(event.getPayloadJson()).contains("fortuneTellerYes");
    });
}

@Test
void spy_receivesPrivateGrimoireInfo() {
    StartedGame game = startGameWithRoles(List.of("SPY", "IMP", "EMPATH", "CHEF", "POISONER"));

    resolutionService.resolveReady(game.gameId(), owner());

    assertThat(gameEventRepository.findByGameIdAndStatusAndDeletedFalseOrderByEventSeqAsc(
            game.gameId(), "VISIBLE")).anySatisfy(event -> {
        assertThat(event.getEventType()).isEqualTo("PRIVATE_INFO_RECEIVED");
        assertThat(event.getVisibleGameSeatIdsJson()).contains(game.seats().getFirst().getId().toString());
        assertThat(event.getPayloadJson()).contains("grimoire");
        assertThat(event.getPayloadJson()).contains("IMP");
    });
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerNightResolutionServiceTests test
```

Expected: FAIL because private info payloads are generic.

- [ ] **Step 3: Implement info role payloads**

Use these payload keys:

- Fortune Teller: `fortuneTellerYes`, `selectedGameSeatIds`.
- Empath: `evilNeighborCount`.
- Chef: `evilAdjacentPairCount`.
- Undertaker: `executedGameSeatId`, `executedRoleCode`.
- Washerwoman / Librarian / Investigator: `candidateGameSeatIds`, `shownRoleCode`, `zero`.
- Spy: `grimoire`, a list of maps with `gameSeatId`, `seatNo`, `displayName`, `roleCode`, `alignment`, `lifeStatus`.

Each role returns:

```java
return new NightResolution(
        result,
        List.of(Map.of("targetGameSeatId", context.actorSeat().getId(), "info", result)),
        List.of(),
        List.of(),
        STATUS_DONE);
```

Add helper in `AbstractTroubleBrewingRoleSkill`:

```java
protected boolean evil(ClocktowerGameSeatPo seat) {
    return "EVIL".equals(seat.getAlignment()) || "MINION".equals(seat.getRoleType()) || "DEMON".equals(seat.getRoleType());
}

protected boolean living(ClocktowerGameSeatPo seat) {
    return "ALIVE".equals(seat.getLifeStatus());
}
```

- [ ] **Step 4: Append private info events**

In `ClocktowerNightResolutionServiceImpl.resolveLoadedTask`, for each `privateInfos` map:

```java
Long visibleSeatId = number(info.get("targetGameSeatId"));
eventAppender.append(game, "PRIVATE_INFO_RECEIVED", null, visibleSeatId, "PRIVATE",
        List.of(visibleSeatId), info, Instant.now());
```

- [ ] **Step 5: Run tests to verify they pass**

Run:

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerNightResolutionServiceTests test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add be/src/main/java/top/egon/mario/clocktower/game/night \
  be/src/test/java/top/egon/mario/clocktower/game/night/ClocktowerNightResolutionServiceTests.java
git commit -m "feat(clocktower): resolve night information roles"
```

### Task 7: Add Butler Vote Constraint

**Files:**
- Modify: `ClocktowerButlerMasterServiceImpl`
- Modify: `ClocktowerGameVoteServiceImpl`
- Modify: `ClocktowerGameNominationServiceTests` or add focused tests to existing vote/action tests.

- [ ] **Step 1: Add failing Butler vote test**

Add to `ClocktowerGameActionServiceTests`:

```java
@Test
void butlerTrueVoteRequiresMasterTrueVote() {
    StartedGame game = startDayGameWithAgents();
    ClocktowerGameSeatPo butler = game.seats().getFirst();
    butler.setRoleCode("BUTLER");
    gameSeatRepository.saveAndFlush(butler);
    ClocktowerGameSeatPo master = game.seats().get(1);
    ClocktowerGameSeatPo nominee = game.seats().get(2);
    saveDoneNightTask(game.gameId(), butler.getId(), "BUTLER", "BUTLER_MASTER",
            Map.of("targetGameSeatId", master.getId()));
    Long nominationId = openNomination(game, butler, nominee);

    ClocktowerGameActionResponse blocked = humanActionService.submit(game.gameId(),
            new ClocktowerGameActionRequest(butler.getId(), "VOTE", List.of(),
                    nominationId, true, null, Map.of()),
            principal(11L, "player1"));
    assertThat(blocked.accepted()).isFalse();
    assertThat(blocked.rejectedCode()).isEqualTo("CLOCKTOWER_BUTLER_MASTER_NOT_VOTING");

    ClocktowerAgentInstancePo masterInstance = agentInstanceRepository
            .findByGameSeatIdAndDeletedFalse(master.getId()).orElseThrow();
    ClocktowerGameActionResponse masterVote = agentActionService.submitAgentAction(game.gameId(), masterInstance.getId(),
            new ClocktowerGameActionRequest(master.getId(), "VOTE", List.of(),
                    nominationId, true, null, Map.of()));
    assertThat(masterVote.accepted()).isTrue();

    ClocktowerGameActionResponse allowed = humanActionService.submit(game.gameId(),
            new ClocktowerGameActionRequest(butler.getId(), "VOTE", List.of(),
                    nominationId, true, null, Map.of()),
            principal(11L, "player1"));
    assertThat(allowed.accepted()).isTrue();
}
```

- [ ] **Step 1a: Add Butler test helper**

Add this helper to `ClocktowerGameActionServiceTests` near `openNomination(...)`:

```java
private void saveDoneNightTask(Long gameId, Long actorGameSeatId, String roleCode, String marker,
                               Map<String, Object> result) {
    ClocktowerGameNightTaskPo task = new ClocktowerGameNightTaskPo();
    task.setGameId(gameId);
    task.setNightNo(1);
    task.setTaskKey(roleCode + ":" + actorGameSeatId + ":CHOOSE_TARGET");
    task.setActorGameSeatId(actorGameSeatId);
    task.setRoleCode(roleCode);
    task.setTaskType("CHOOSE_TARGET");
    task.setStatus("DONE");
    task.setMandatory(true);
    task.setSortOrder(1);
    task.setMetadataJson("{}");
    task.setChoiceJson("{}");
    task.setResultJson(new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(result).toString());
    nightTaskRepository.saveAndFlush(task);
}
```

Add imports if missing:

```java
import top.egon.mario.clocktower.game.night.po.ClocktowerGameNightTaskPo;
import top.egon.mario.clocktower.game.night.repository.ClocktowerGameNightTaskRepository;
```

Autowire:

```java
@Autowired
private ClocktowerGameNightTaskRepository nightTaskRepository;
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerGameActionServiceTests test
```

Expected: FAIL because Butler vote is not constrained.

- [ ] **Step 3: Implement master lookup**

In `ClocktowerButlerMasterServiceImpl`:

```java
@Service
@RequiredArgsConstructor
public class ClocktowerButlerMasterServiceImpl implements ClocktowerButlerMasterService {

    private final ClocktowerGameNightTaskRepository nightTaskRepository;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<Long> currentMasterGameSeatId(Long gameId, Long butlerGameSeatId) {
        return nightTaskRepository.findByGameIdAndNightNoAndActorGameSeatIdAndDeletedFalseOrderBySortOrderAscIdAsc(
                        gameId, currentLatestNight(gameId), butlerGameSeatId)
                .stream()
                .filter(task -> "BUTLER".equals(task.getRoleCode()))
                .filter(task -> "DONE".equals(task.getStatus()))
                .map(ClocktowerGameNightTaskPo::getResultJson)
                .map(this::readTargetGameSeatId)
                .filter(Objects::nonNull)
                .findFirst();
    }
}
```

Implement `currentLatestNight(gameId)` by reading max night from task rows with a repository query:

```java
@Query("""
        select coalesce(max(task.nightNo), 0)
        from ClocktowerGameNightTaskPo task
        where task.gameId = :gameId
          and task.deleted = false
        """)
int findMaxNightNoByGameId(@Param("gameId") Long gameId);
```

Add these helper methods in `ClocktowerButlerMasterServiceImpl`:

```java
private int currentLatestNight(Long gameId) {
    return nightTaskRepository.findMaxNightNoByGameId(gameId);
}

private Long readTargetGameSeatId(String json) {
    try {
        Object value = objectMapper.readValue(json, Map.class).get("targetGameSeatId");
        return value instanceof Number number ? number.longValue() : null;
    } catch (JsonProcessingException ex) {
        return null;
    }
}
```

Add imports:

```java
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.egon.mario.clocktower.game.night.po.ClocktowerGameNightTaskPo;
import top.egon.mario.clocktower.game.night.repository.ClocktowerGameNightTaskRepository;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
```

- [ ] **Step 4: Enforce vote hook**

In `ClocktowerGameVoteServiceImpl`, inject `ClocktowerButlerMasterService`. Before dead-vote handling:

```java
if (voteValue && "BUTLER".equals(actorSeat.getRoleCode())) {
    Long masterGameSeatId = butlerMasterService.currentMasterGameSeatId(game.getId(), actorSeat.getId())
            .orElse(null);
    if (masterGameSeatId != null && !voteRepository.existsByNominationIdAndVoterGameSeatIdAndVoteValueTrueAndDeletedFalse(
            nomination.getId(), masterGameSeatId)) {
        return reject(game, actorSeat, "CLOCKTOWER_BUTLER_MASTER_NOT_VOTING");
    }
}
```

Add repository method:

```java
boolean existsByNominationIdAndVoterGameSeatIdAndVoteValueTrueAndDeletedFalse(
        Long nominationId, Long voterGameSeatId);
```

- [ ] **Step 5: Run test to verify it passes**

Run:

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerGameActionServiceTests test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add be/src/main/java/top/egon/mario/clocktower/game/night \
  be/src/main/java/top/egon/mario/clocktower/game/nomination \
  be/src/test/java/top/egon/mario/clocktower/game/action/ClocktowerGameActionServiceTests.java
git commit -m "feat(clocktower): enforce butler master voting"
```

### Task 8: Add ST Night Task Endpoints and Flow Integration Coverage

**Files:**
- Create: `ClocktowerGameNightTaskController`
- Modify: `ClocktowerGameNightTaskServiceImpl`
- Modify: `ClocktowerNightResolutionServiceImpl`
- Modify: `ClocktowerGameFlowServiceTests`
- Modify: `ClocktowerGameNightTaskServiceTests`

- [ ] **Step 1: Add flow completion test**

In `ClocktowerGameFlowServiceTests`, add:

```java
@Test
void advanceFirstNightGeneratedTasksCompleteEntersDay() {
    StartedGame game = startGame();
    nightTaskRepository.findByGameIdAndNightNoAndDeletedFalseOrderBySortOrderAscIdAsc(game.gameId(), 1)
            .forEach(task -> {
                task.setStatus("DONE");
                task.setCompletedAt(java.time.Instant.now());
            });
    nightTaskRepository.flush();

    ClocktowerGameAdvanceResult result = flowService.advance(game.gameId(), emptyRequest(), owner());

    assertThat(result.phase()).isEqualTo("DAY");
    assertThat(result.flow().blockingReasons()).doesNotContain("PENDING_NIGHT_TASKS");
}
```

- [ ] **Step 2: Add controller smoke test through service methods**

In `ClocktowerGameNightTaskServiceTests`, add:

```java
@Test
void storytellerCanListAndSkipCurrentNightTask() {
    StartedGame game = startGameWithRoles(List.of("POISONER", "CHEF", "EMPATH", "FORTUNETELLER", "SPY"));
    ClocktowerGameNightTaskPo task = taskFor(game.gameId(), "POISONER");

    List<ClocktowerNightTaskView> current = taskService.currentTasks(game.gameId(), owner());
    assertThat(current).extracting(ClocktowerNightTaskView::taskId).contains(task.getId());

    ClocktowerNightTaskView skipped = taskService.skipTask(game.gameId(), task.getId(),
            new ClocktowerNightSkipRequest("manual skip"), owner());
    assertThat(skipped.status()).isEqualTo("SKIPPED");
    assertThat(nightTaskRepository.findById(task.getId()).orElseThrow().getSkippedAt()).isNotNull();
}
```

- [ ] **Step 3: Run tests to verify they fail if list/skip missing**

Run:

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerGameNightTaskServiceTests,ClocktowerGameFlowServiceTests test
```

Expected: FAIL until `currentTasks` and `skipTask` are implemented.

- [ ] **Step 4: Implement listing and skip**

`currentTasks`:

```java
viewerResolver.resolveGameViewer(gameId, principal);
ClocktowerGamePo game = gameRepository.findByIdAndDeletedFalse(gameId)
        .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_GAME_NOT_FOUND"));
return nightTaskRepository.findByGameIdAndNightNoAndDeletedFalseOrderBySortOrderAscIdAsc(gameId, game.getNightNo())
        .stream()
        .map(this::toView)
        .toList();
```

`skipTask` must require ST:

```java
ClocktowerGamePo game = gameRepository.findByIdAndDeletedFalse(gameId)
        .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_GAME_NOT_FOUND"));
requireStoryteller(game, principal);
if (request == null || !StringUtils.hasText(request.reason())) {
    throw new ClocktowerException("CLOCKTOWER_NIGHT_TASK_SKIP_REASON_REQUIRED");
}
ClocktowerGameNightTaskPo task = nightTaskRepository.findLockedByIdAndGameIdAndDeletedFalse(taskId, gameId)
        .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_NIGHT_TASK_NOT_FOUND"));
task.setStatus(STATUS_SKIPPED);
task.setSkippedAt(Instant.now());
task.setResultJson(writeJson(Map.of("skipReason", request.reason())));
return toView(nightTaskRepository.saveAndFlush(task));
```

- [ ] **Step 5: Add controller**

Use `ClocktowerReactiveSupport` style:

```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/clocktower/games/{gameId}/night")
public class ClocktowerGameNightTaskController extends ClocktowerReactiveSupport {

    private final ClocktowerGameNightTaskService taskService;
    private final ClocktowerNightResolutionService resolutionService;

    @GetMapping("/tasks")
    public Mono<ApiResponse<List<ClocktowerNightTaskView>>> tasks(@PathVariable Long gameId,
                                                                  @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> taskService.currentTasks(gameId, principal));
    }

    @PostMapping("/tasks/{taskId}/resolve")
    public Mono<ApiResponse<ClocktowerNightTaskView>> resolve(@PathVariable Long gameId,
                                                              @PathVariable Long taskId,
                                                              @RequestBody ClocktowerNightResolveRequest request,
                                                              @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> resolutionService.resolveTask(gameId, taskId, request, principal));
    }

    @PostMapping("/tasks/{taskId}/skip")
    public Mono<ApiResponse<ClocktowerNightTaskView>> skip(@PathVariable Long gameId,
                                                           @PathVariable Long taskId,
                                                           @RequestBody ClocktowerNightSkipRequest request,
                                                           @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> taskService.skipTask(gameId, taskId, request, principal));
    }

    @PostMapping("/resolve-ready")
    public Mono<ApiResponse<List<ClocktowerNightTaskView>>> resolveReady(@PathVariable Long gameId,
                                                                         @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> resolutionService.resolveReady(gameId, principal));
    }
}
```

Use imports matching existing game controllers:

```java
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import top.egon.mario.rbac.service.security.RbacPrincipal;
```

- [ ] **Step 6: Run tests to verify they pass**

Run:

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerGameNightTaskServiceTests,ClocktowerGameFlowServiceTests test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add be/src/main/java/top/egon/mario/clocktower/game/night \
  be/src/test/java/top/egon/mario/clocktower/game/night/ClocktowerGameNightTaskServiceTests.java \
  be/src/test/java/top/egon/mario/clocktower/game/flow/ClocktowerGameFlowServiceTests.java
git commit -m "feat(clocktower): expose storyteller night task controls"
```

### Task 9: Full Verification and Cleanup

**Files:**
- No planned source changes unless verification exposes a task-09 regression.

- [ ] **Step 1: Run focused persistence and night tests**

Run:

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerJpaMappingTests,ClocktowerSchemaMigrationTests,ClocktowerGameNightTaskServiceTests,ClocktowerNightResolutionServiceTests test
```

Expected: PASS with 0 failures and Flyway applying through version 36.

- [ ] **Step 2: Run related game action / flow / nomination tests**

Run:

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerGameActionServiceTests,ClocktowerGameFlowServiceTests,ClocktowerGameNominationServiceTests test
```

Expected: PASS with 0 failures.

- [ ] **Step 3: Run full backend test suite**

Run:

```bash
./mvnw -Dmaven.build.cache.enabled=false test
```

Expected: PASS with 0 failures.

- [ ] **Step 4: Inspect git diff scope**

Run:

```bash
git status --short
git diff --stat HEAD
```

Expected: only task-09 backend source, migration, and tests changed.

- [ ] **Step 5: Commit verification adjustments if any**

If verification required code or test fixes, commit them:

```bash
git add be/src/main/java/top/egon/mario/clocktower be/src/test/java/top/egon/mario/clocktower be/src/main/resources/db/migration
git commit -m "test(clocktower): verify night task engine"
```

If no fixes were required, do not create an empty commit.

---

## Implementation Notes

- Do not edit existing migrations. `V36` is the only new migration for task 09.
- Keep all new JSON fields as `String` with `@JdbcTypeCode(SqlTypes.JSON)` to match current game event and task style.
- Do not add frontend work.
- Do not add async Agent queue behavior. `autoChooseTask(...)` is a synchronous internal helper for task 09 tests and later task 10 integration.
- Do not introduce a marker table. Store v0 markers in `result_json` and events.
- Use `STORYTELLER` visibility for marker and protected-kill details; use `PRIVATE` for role information.
- Reuse existing `ClocktowerException` codes as literal strings; do not add a new error enum.
