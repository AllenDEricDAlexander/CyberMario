# Clocktower Actor Agent Domain Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the backend Actor / Agent domain layer from task 02 so later Clocktower room and game lifecycle work can create and query Agent seats without fake users.

**Architecture:** Add a dedicated `top.egon.mario.clocktower.agent` package for constants, JPA entities, repositories, DTOs, and a small domain service. The service owns the transactional invariant for creating an Agent Actor, Agent Instance, and room-seat binding while leaving room creation, game start, IM, frontend, and Agent runtime behavior unchanged.

**Tech Stack:** Spring Boot, Spring Data JPA, Hibernate JSON mapping, Lombok, Jackson `ObjectMapper`, JUnit 5, AssertJ, Maven.

---

## Scope Check

This plan implements only `docs/superpowers/specs/2026-07-08-clocktower-actor-agent-domain-design.md`.

It depends on task 01 already being present in this worktree:

- `be/src/main/resources/db/migration/V32__clocktower_actor_agent_foundation.sql`
- `clocktower_actor`
- `clocktower_agent_profile`
- `clocktower_agent_instance`
- new actor fields on `clocktower_room_seat` and `clocktower_game_seat`

This plan does not:

- Add another Flyway migration.
- Consume `ClocktowerRoomCreateRequest.agentSeatCount`.
- Modify `ClocktowerRoomLobbyServiceImpl#createRoom`.
- Modify `ClocktowerGameLifecycleServiceImpl#startGame`.
- Add Agent seats to IM.
- Implement Agent decisions, task queues, memory, frontend, public mic, nominations, votes, or night tasks.

## File Structure

Create:

- `be/src/main/java/top/egon/mario/clocktower/agent/constant/ClocktowerActorType.java`
  - String constants for actor identity.
- `be/src/main/java/top/egon/mario/clocktower/agent/constant/ClocktowerAgentStatus.java`
  - String constants for Agent row status.
- `be/src/main/java/top/egon/mario/clocktower/agent/constant/ClocktowerAgentAutoMode.java`
  - String constants for Agent automation mode.
- `be/src/main/java/top/egon/mario/clocktower/agent/po/ClocktowerActorPo.java`
  - JPA mapping for `clocktower_actor`.
- `be/src/main/java/top/egon/mario/clocktower/agent/po/ClocktowerAgentProfilePo.java`
  - JPA mapping for `clocktower_agent_profile`.
- `be/src/main/java/top/egon/mario/clocktower/agent/po/ClocktowerAgentInstancePo.java`
  - JPA mapping for `clocktower_agent_instance`.
- `be/src/main/java/top/egon/mario/clocktower/agent/repository/ClocktowerActorRepository.java`
  - Repository for Actor rows.
- `be/src/main/java/top/egon/mario/clocktower/agent/repository/ClocktowerAgentProfileRepository.java`
  - Repository for Agent Profile rows.
- `be/src/main/java/top/egon/mario/clocktower/agent/repository/ClocktowerAgentInstanceRepository.java`
  - Repository for Agent Instance rows.
- `be/src/main/java/top/egon/mario/clocktower/agent/dto/ClocktowerAgentSeatDescriptor.java`
  - Internal result DTO for Agent room-seat creation.
- `be/src/main/java/top/egon/mario/clocktower/agent/dto/ClocktowerAgentInstanceView.java`
  - Internal read DTO for Agent Instance summary.
- `be/src/main/java/top/egon/mario/clocktower/agent/service/ClocktowerAgentSeatService.java`
  - Service contract for Agent seat creation and lookup.
- `be/src/main/java/top/egon/mario/clocktower/agent/service/impl/ClocktowerAgentSeatServiceImpl.java`
  - Transactional implementation.
- `be/src/test/java/top/egon/mario/clocktower/agent/ClocktowerAgentRepositoryTests.java`
  - Repository and seed-profile coverage.
- `be/src/test/java/top/egon/mario/clocktower/agent/ClocktowerAgentSeatFieldMappingTests.java`
  - Seat PO field persistence coverage.
- `be/src/test/java/top/egon/mario/clocktower/agent/ClocktowerAgentSeatServiceTests.java`
  - Service behavior and metadata coverage.

Modify:

- `be/src/main/java/top/egon/mario/clocktower/game/po/ClocktowerRoomSeatPo.java`
  - Add actor fields.
- `be/src/main/java/top/egon/mario/clocktower/game/po/ClocktowerGameSeatPo.java`
  - Add actor fields.
- `be/src/test/java/top/egon/mario/clocktower/ClocktowerJpaMappingTests.java`
  - Add managed-entity coverage for new Agent PO classes.

## Task 1: Agent Constants, PO Classes, and Repositories

**Files:**

- Create: `be/src/main/java/top/egon/mario/clocktower/agent/constant/ClocktowerActorType.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/agent/constant/ClocktowerAgentStatus.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/agent/constant/ClocktowerAgentAutoMode.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/agent/po/ClocktowerActorPo.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/agent/po/ClocktowerAgentProfilePo.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/agent/po/ClocktowerAgentInstancePo.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/agent/repository/ClocktowerActorRepository.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/agent/repository/ClocktowerAgentProfileRepository.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/agent/repository/ClocktowerAgentInstanceRepository.java`
- Create: `be/src/test/java/top/egon/mario/clocktower/agent/ClocktowerAgentRepositoryTests.java`
- Modify: `be/src/test/java/top/egon/mario/clocktower/ClocktowerJpaMappingTests.java`

- [ ] **Step 1: Add failing JPA managed-entity coverage**

In `ClocktowerJpaMappingTests`, add these imports:

```java
import top.egon.mario.clocktower.agent.po.ClocktowerActorPo;
import top.egon.mario.clocktower.agent.po.ClocktowerAgentInstancePo;
import top.egon.mario.clocktower.agent.po.ClocktowerAgentProfilePo;
```

Add this test after `clocktowerGamePoClassesAreManagedByJpaContext`:

```java
    @Test
    void clocktowerAgentPoClassesAreManagedByJpaContext() {
        assertManaged(ClocktowerActorPo.class);
        assertManaged(ClocktowerAgentProfilePo.class);
        assertManaged(ClocktowerAgentInstancePo.class);
    }
```

- [ ] **Step 2: Add failing repository tests**

Create `be/src/test/java/top/egon/mario/clocktower/agent/ClocktowerAgentRepositoryTests.java`:

```java
package top.egon.mario.clocktower.agent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.clocktower.agent.constant.ClocktowerActorType;
import top.egon.mario.clocktower.agent.constant.ClocktowerAgentAutoMode;
import top.egon.mario.clocktower.agent.constant.ClocktowerAgentStatus;
import top.egon.mario.clocktower.agent.po.ClocktowerActorPo;
import top.egon.mario.clocktower.agent.po.ClocktowerAgentInstancePo;
import top.egon.mario.clocktower.agent.po.ClocktowerAgentProfilePo;
import top.egon.mario.clocktower.agent.repository.ClocktowerActorRepository;
import top.egon.mario.clocktower.agent.repository.ClocktowerAgentInstanceRepository;
import top.egon.mario.clocktower.agent.repository.ClocktowerAgentProfileRepository;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
@Transactional
class ClocktowerAgentRepositoryTests {

    @Autowired
    private ClocktowerActorRepository actorRepository;

    @Autowired
    private ClocktowerAgentProfileRepository profileRepository;

    @Autowired
    private ClocktowerAgentInstanceRepository instanceRepository;

    @Test
    void seededAgentProfilesAreReadableInIdOrder() {
        assertThat(profileRepository.findByDeletedFalseOrderByIdAsc())
                .extracting(ClocktowerAgentProfilePo::getName)
                .contains("balanced", "quiet", "aggressive", "careful");

        ClocktowerAgentProfilePo balanced = profileRepository.findFirstByNameAndDeletedFalse("balanced")
                .orElseThrow();
        assertThat(balanced.getDisplayNameTemplate()).isEqualTo("Agent {n}");
        assertThat(balanced.getStrategyLevel()).isEqualTo("NORMAL");
        assertThat(balanced.getTalkativeness()).isEqualTo(50);
        assertThat(balanced.getDeceptionLevel()).isEqualTo(50);
        assertThat(balanced.getAggression()).isEqualTo(50);
        assertThat(balanced.getRiskTolerance()).isEqualTo(50);
    }

    @Test
    void repositoriesPersistAgentActorAndInstanceRows() {
        ClocktowerAgentProfilePo balanced = profileRepository.findFirstByNameAndDeletedFalse("balanced")
                .orElseThrow();

        ClocktowerActorPo actor = new ClocktowerActorPo();
        actor.setActorType(ClocktowerActorType.AGENT);
        actor.setDisplayName("Agent 7");
        actor.setStatus(ClocktowerAgentStatus.ACTIVE);
        actor.setMetadataJson("{\"source\":\"repository-test\"}");
        ClocktowerActorPo savedActor = actorRepository.saveAndFlush(actor);

        ClocktowerAgentInstancePo instance = new ClocktowerAgentInstancePo();
        instance.setRoomId(72001L);
        instance.setProfileId(balanced.getId());
        instance.setActorId(savedActor.getId());
        instance.setRoomSeatId(73001L);
        instance.setStatus(ClocktowerAgentStatus.ACTIVE);
        instance.setAutoMode(ClocktowerAgentAutoMode.FULL_AUTO);
        instance.setMetadataJson("{\"source\":\"repository-test\"}");
        ClocktowerAgentInstancePo savedInstance = instanceRepository.saveAndFlush(instance);

        assertThat(actorRepository.findByIdAndDeletedFalse(savedActor.getId()))
                .get()
                .extracting(ClocktowerActorPo::getDisplayName)
                .isEqualTo("Agent 7");
        assertThat(instanceRepository.findByIdAndDeletedFalse(savedInstance.getId())).isPresent();
        assertThat(instanceRepository.findByActorIdAndDeletedFalse(savedActor.getId()))
                .get()
                .extracting(ClocktowerAgentInstancePo::getRoomId)
                .isEqualTo(72001L);
        assertThat(instanceRepository.findByRoomIdAndDeletedFalseOrderByIdAsc(72001L))
                .extracting(ClocktowerAgentInstancePo::getId)
                .contains(savedInstance.getId());
        assertThat(instanceRepository.findByGameIdAndDeletedFalseOrderByIdAsc(99999L)).isEmpty();
    }
}
```

- [ ] **Step 3: Run tests to verify they fail before implementation**

Run:

```bash
cd be && ./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerJpaMappingTests,ClocktowerAgentRepositoryTests test
```

Expected result:

```text
Compilation failure
package top.egon.mario.clocktower.agent.po does not exist
```

- [ ] **Step 4: Create Actor type constants**

Create `be/src/main/java/top/egon/mario/clocktower/agent/constant/ClocktowerActorType.java`:

```java
package top.egon.mario.clocktower.agent.constant;

public final class ClocktowerActorType {

    public static final String HUMAN = "HUMAN";
    public static final String AGENT = "AGENT";
    public static final String STORYTELLER = "STORYTELLER";
    public static final String SYSTEM = "SYSTEM";

    private ClocktowerActorType() {
    }
}
```

- [ ] **Step 5: Create Agent status constants**

Create `be/src/main/java/top/egon/mario/clocktower/agent/constant/ClocktowerAgentStatus.java`:

```java
package top.egon.mario.clocktower.agent.constant;

public final class ClocktowerAgentStatus {

    public static final String ACTIVE = "ACTIVE";
    public static final String PAUSED = "PAUSED";
    public static final String ARCHIVED = "ARCHIVED";

    private ClocktowerAgentStatus() {
    }
}
```

- [ ] **Step 6: Create Agent auto-mode constants**

Create `be/src/main/java/top/egon/mario/clocktower/agent/constant/ClocktowerAgentAutoMode.java`:

```java
package top.egon.mario.clocktower.agent.constant;

public final class ClocktowerAgentAutoMode {

    public static final String FULL_AUTO = "FULL_AUTO";
    public static final String ST_APPROVAL = "ST_APPROVAL";
    public static final String PAUSED = "PAUSED";

    private ClocktowerAgentAutoMode() {
    }
}
```

- [ ] **Step 7: Create Actor PO**

Create `be/src/main/java/top/egon/mario/clocktower/agent/po/ClocktowerActorPo.java`:

```java
package top.egon.mario.clocktower.agent.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import top.egon.mario.clocktower.agent.constant.ClocktowerAgentStatus;
import top.egon.mario.common.entity.BaseAuditablePo;

@Getter
@Setter
@Entity
@Table(name = "clocktower_actor")
public class ClocktowerActorPo extends BaseAuditablePo {

    @Column(name = "actor_type", nullable = false, length = 32)
    private String actorType;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    @Column(name = "status", nullable = false, length = 32)
    private String status = ClocktowerAgentStatus.ACTIVE;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";
}
```

- [ ] **Step 8: Create Agent Profile PO**

Create `be/src/main/java/top/egon/mario/clocktower/agent/po/ClocktowerAgentProfilePo.java`:

```java
package top.egon.mario.clocktower.agent.po;

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
@Table(name = "clocktower_agent_profile")
public class ClocktowerAgentProfilePo extends BaseAuditablePo {

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "display_name_template", nullable = false, length = 128)
    private String displayNameTemplate;

    @Column(name = "strategy_level", nullable = false, length = 32)
    private String strategyLevel = "NORMAL";

    @Column(name = "talkativeness", nullable = false)
    private int talkativeness = 50;

    @Column(name = "deception_level", nullable = false)
    private int deceptionLevel = 50;

    @Column(name = "aggression", nullable = false)
    private int aggression = 50;

    @Column(name = "risk_tolerance", nullable = false)
    private int riskTolerance = 50;

    @Column(name = "model_provider", length = 64)
    private String modelProvider;

    @Column(name = "model_name", length = 128)
    private String modelName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";
}
```

- [ ] **Step 9: Create Agent Instance PO**

Create `be/src/main/java/top/egon/mario/clocktower/agent/po/ClocktowerAgentInstancePo.java`:

```java
package top.egon.mario.clocktower.agent.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import top.egon.mario.clocktower.agent.constant.ClocktowerAgentAutoMode;
import top.egon.mario.clocktower.agent.constant.ClocktowerAgentStatus;
import top.egon.mario.common.entity.BaseAuditablePo;

@Getter
@Setter
@Entity
@Table(name = "clocktower_agent_instance")
public class ClocktowerAgentInstancePo extends BaseAuditablePo {

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "game_id")
    private Long gameId;

    @Column(name = "profile_id", nullable = false)
    private Long profileId;

    @Column(name = "actor_id", nullable = false)
    private Long actorId;

    @Column(name = "room_seat_id")
    private Long roomSeatId;

    @Column(name = "game_seat_id")
    private Long gameSeatId;

    @Column(name = "status", nullable = false, length = 32)
    private String status = ClocktowerAgentStatus.ACTIVE;

    @Column(name = "auto_mode", nullable = false, length = 32)
    private String autoMode = ClocktowerAgentAutoMode.FULL_AUTO;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";
}
```

- [ ] **Step 10: Create repositories**

Create `be/src/main/java/top/egon/mario/clocktower/agent/repository/ClocktowerActorRepository.java`:

```java
package top.egon.mario.clocktower.agent.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.clocktower.agent.po.ClocktowerActorPo;

import java.util.Optional;

public interface ClocktowerActorRepository extends JpaRepository<ClocktowerActorPo, Long> {

    Optional<ClocktowerActorPo> findByIdAndDeletedFalse(Long id);
}
```

Create `be/src/main/java/top/egon/mario/clocktower/agent/repository/ClocktowerAgentProfileRepository.java`:

```java
package top.egon.mario.clocktower.agent.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.clocktower.agent.po.ClocktowerAgentProfilePo;

import java.util.List;
import java.util.Optional;

public interface ClocktowerAgentProfileRepository extends JpaRepository<ClocktowerAgentProfilePo, Long> {

    Optional<ClocktowerAgentProfilePo> findFirstByNameAndDeletedFalse(String name);

    List<ClocktowerAgentProfilePo> findByDeletedFalseOrderByIdAsc();
}
```

Create `be/src/main/java/top/egon/mario/clocktower/agent/repository/ClocktowerAgentInstanceRepository.java`:

```java
package top.egon.mario.clocktower.agent.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.clocktower.agent.po.ClocktowerAgentInstancePo;

import java.util.List;
import java.util.Optional;

public interface ClocktowerAgentInstanceRepository extends JpaRepository<ClocktowerAgentInstancePo, Long> {

    List<ClocktowerAgentInstancePo> findByRoomIdAndDeletedFalseOrderByIdAsc(Long roomId);

    List<ClocktowerAgentInstancePo> findByGameIdAndDeletedFalseOrderByIdAsc(Long gameId);

    Optional<ClocktowerAgentInstancePo> findByIdAndDeletedFalse(Long id);

    Optional<ClocktowerAgentInstancePo> findByGameSeatIdAndDeletedFalse(Long gameSeatId);

    Optional<ClocktowerAgentInstancePo> findByActorIdAndDeletedFalse(Long actorId);
}
```

- [ ] **Step 11: Run focused tests**

Run:

```bash
cd be && ./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerJpaMappingTests,ClocktowerAgentRepositoryTests test
```

Expected result:

```text
BUILD SUCCESS
```

- [ ] **Step 12: Commit Task 1**

Run:

```bash
git add be/src/main/java/top/egon/mario/clocktower/agent/constant \
        be/src/main/java/top/egon/mario/clocktower/agent/po \
        be/src/main/java/top/egon/mario/clocktower/agent/repository \
        be/src/test/java/top/egon/mario/clocktower/ClocktowerJpaMappingTests.java \
        be/src/test/java/top/egon/mario/clocktower/agent/ClocktowerAgentRepositoryTests.java
git commit -m "feat: map clocktower actor agent domain"
```

## Task 2: Seat Actor Fields in Room and Game Seat PO Classes

**Files:**

- Modify: `be/src/main/java/top/egon/mario/clocktower/game/po/ClocktowerRoomSeatPo.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/game/po/ClocktowerGameSeatPo.java`
- Create: `be/src/test/java/top/egon/mario/clocktower/agent/ClocktowerAgentSeatFieldMappingTests.java`

- [ ] **Step 1: Add failing seat field mapping tests**

Create `be/src/test/java/top/egon/mario/clocktower/agent/ClocktowerAgentSeatFieldMappingTests.java`:

```java
package top.egon.mario.clocktower.agent;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.clocktower.agent.constant.ClocktowerActorType;
import top.egon.mario.clocktower.game.po.ClocktowerGameSeatPo;
import top.egon.mario.clocktower.game.po.ClocktowerRoomSeatPo;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
@Transactional
class ClocktowerAgentSeatFieldMappingTests {

    @Autowired
    private EntityManager entityManager;

    @Test
    void roomSeatActorFieldsPersistAndReload() {
        ClocktowerRoomSeatPo seat = new ClocktowerRoomSeatPo();
        seat.setRoomId(81001L);
        seat.setSeatNo(1);
        seat.setUserId(null);
        seat.setDisplayName("Agent 1");
        seat.setRoleCode("CHEF");
        seat.setStatus("OCCUPIED");
        seat.setActorId(82001L);
        seat.setActorType(ClocktowerActorType.AGENT);
        seat.setAgentInstanceId(83001L);
        seat.setMetadataJson("{\"ready\":true,\"agentSeat\":true}");
        entityManager.persist(seat);

        entityManager.flush();
        entityManager.clear();

        ClocktowerRoomSeatPo reloaded = entityManager.find(ClocktowerRoomSeatPo.class, seat.getId());
        assertThat(reloaded.getActorId()).isEqualTo(82001L);
        assertThat(reloaded.getActorType()).isEqualTo(ClocktowerActorType.AGENT);
        assertThat(reloaded.getAgentInstanceId()).isEqualTo(83001L);
        assertThat(reloaded.getUserId()).isNull();
    }

    @Test
    void gameSeatActorFieldsPersistAndReload() {
        ClocktowerGameSeatPo seat = new ClocktowerGameSeatPo();
        seat.setGameId(84001L);
        seat.setRoomSeatId(85001L);
        seat.setSeatNo(2);
        seat.setUserId(null);
        seat.setDisplayName("Agent 2");
        seat.setRoleCode("EMPATH");
        seat.setStatus("ACTIVE");
        seat.setActorId(86001L);
        seat.setActorType(ClocktowerActorType.AGENT);
        seat.setAgentInstanceId(87001L);
        seat.setMetadataJson("{\"ready\":true,\"agentSeat\":true}");
        entityManager.persist(seat);

        entityManager.flush();
        entityManager.clear();

        ClocktowerGameSeatPo reloaded = entityManager.find(ClocktowerGameSeatPo.class, seat.getId());
        assertThat(reloaded.getActorId()).isEqualTo(86001L);
        assertThat(reloaded.getActorType()).isEqualTo(ClocktowerActorType.AGENT);
        assertThat(reloaded.getAgentInstanceId()).isEqualTo(87001L);
        assertThat(reloaded.getUserId()).isNull();
    }

    @Test
    void newSeatObjectsDefaultToHumanActorType() {
        assertThat(new ClocktowerRoomSeatPo().getActorType()).isEqualTo(ClocktowerActorType.HUMAN);
        assertThat(new ClocktowerGameSeatPo().getActorType()).isEqualTo(ClocktowerActorType.HUMAN);
    }
}
```

- [ ] **Step 2: Run test to verify it fails before seat fields exist**

Run:

```bash
cd be && ./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerAgentSeatFieldMappingTests test
```

Expected result:

```text
Compilation failure
cannot find symbol
symbol: method setActorId(java.lang.Long)
```

- [ ] **Step 3: Add fields to `ClocktowerRoomSeatPo`**

In `be/src/main/java/top/egon/mario/clocktower/game/po/ClocktowerRoomSeatPo.java`, add this import:

```java
import top.egon.mario.clocktower.agent.constant.ClocktowerActorType;
```

Add these fields after `userId`:

```java
    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "actor_type", nullable = false, length = 32)
    private String actorType = ClocktowerActorType.HUMAN;

    @Column(name = "agent_instance_id")
    private Long agentInstanceId;
```

- [ ] **Step 4: Add fields to `ClocktowerGameSeatPo`**

In `be/src/main/java/top/egon/mario/clocktower/game/po/ClocktowerGameSeatPo.java`, add this import:

```java
import top.egon.mario.clocktower.agent.constant.ClocktowerActorType;
```

Add these fields after `userId`:

```java
    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "actor_type", nullable = false, length = 32)
    private String actorType = ClocktowerActorType.HUMAN;

    @Column(name = "agent_instance_id")
    private Long agentInstanceId;
```

- [ ] **Step 5: Run focused seat mapping test**

Run:

```bash
cd be && ./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerAgentSeatFieldMappingTests test
```

Expected result:

```text
BUILD SUCCESS
```

- [ ] **Step 6: Run room/game regression tests that rely on human seats**

Run:

```bash
cd be && ./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerRoomRefactorServiceTests,ClocktowerGameLifecycleServiceTests test
```

Expected result:

```text
BUILD SUCCESS
```

- [ ] **Step 7: Commit Task 2**

Run:

```bash
git add be/src/main/java/top/egon/mario/clocktower/game/po/ClocktowerRoomSeatPo.java \
        be/src/main/java/top/egon/mario/clocktower/game/po/ClocktowerGameSeatPo.java \
        be/src/test/java/top/egon/mario/clocktower/agent/ClocktowerAgentSeatFieldMappingTests.java
git commit -m "feat: expose actor fields on clocktower seats"
```

## Task 3: Agent Seat DTOs and Domain Service

**Files:**

- Create: `be/src/main/java/top/egon/mario/clocktower/agent/dto/ClocktowerAgentSeatDescriptor.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/agent/dto/ClocktowerAgentInstanceView.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/agent/service/ClocktowerAgentSeatService.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/agent/service/impl/ClocktowerAgentSeatServiceImpl.java`
- Create: `be/src/test/java/top/egon/mario/clocktower/agent/ClocktowerAgentSeatServiceTests.java`

- [ ] **Step 1: Add failing service tests**

Create `be/src/test/java/top/egon/mario/clocktower/agent/ClocktowerAgentSeatServiceTests.java`:

```java
package top.egon.mario.clocktower.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.clocktower.agent.constant.ClocktowerActorType;
import top.egon.mario.clocktower.agent.constant.ClocktowerAgentAutoMode;
import top.egon.mario.clocktower.agent.dto.ClocktowerAgentSeatDescriptor;
import top.egon.mario.clocktower.agent.po.ClocktowerActorPo;
import top.egon.mario.clocktower.agent.po.ClocktowerAgentInstancePo;
import top.egon.mario.clocktower.agent.repository.ClocktowerActorRepository;
import top.egon.mario.clocktower.agent.repository.ClocktowerAgentInstanceRepository;
import top.egon.mario.clocktower.agent.service.ClocktowerAgentSeatService;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.clocktower.game.po.ClocktowerRoomSeatPo;
import top.egon.mario.clocktower.room.repository.ClocktowerRoomSeatRepository;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
@Transactional
class ClocktowerAgentSeatServiceTests {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    @Autowired
    private ClocktowerAgentSeatService agentSeatService;

    @Autowired
    private ClocktowerActorRepository actorRepository;

    @Autowired
    private ClocktowerAgentInstanceRepository instanceRepository;

    @Autowired
    private ClocktowerRoomSeatRepository roomSeatRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createAgentForRoomSeatCreatesActorInstanceAndBindsRoomSeat() throws Exception {
        ClocktowerRoomSeatPo seat = openRoomSeat(77001L, 3, "CHEF");

        ClocktowerAgentSeatDescriptor descriptor = agentSeatService.createAgentForRoomSeat(
                77001L, seat.getId(), 3, null, "CHEF", null, null);

        assertThat(descriptor.actorId()).isNotNull();
        assertThat(descriptor.agentInstanceId()).isNotNull();
        assertThat(descriptor.roomId()).isEqualTo(77001L);
        assertThat(descriptor.roomSeatId()).isEqualTo(seat.getId());
        assertThat(descriptor.seatNo()).isEqualTo(3);
        assertThat(descriptor.displayName()).isEqualTo("Agent 3");
        assertThat(descriptor.roleCode()).isEqualTo("CHEF");
        assertThat(descriptor.profileName()).isEqualTo("balanced");
        assertThat(descriptor.autoMode()).isEqualTo(ClocktowerAgentAutoMode.FULL_AUTO);
        assertThat(descriptor.metadata())
                .containsEntry("ready", true)
                .containsEntry("actorType", ClocktowerActorType.AGENT)
                .containsEntry("agent", true)
                .containsEntry("agentSeat", true)
                .containsEntry("systemManaged", true)
                .containsEntry("agentPolicy", "HEURISTIC_V0")
                .containsEntry("autoMode", ClocktowerAgentAutoMode.FULL_AUTO)
                .containsEntry("createdBy", "agentSeatCount");

        ClocktowerActorPo actor = actorRepository.findByIdAndDeletedFalse(descriptor.actorId()).orElseThrow();
        assertThat(actor.getActorType()).isEqualTo(ClocktowerActorType.AGENT);
        assertThat(actor.getUserId()).isNull();
        assertThat(actor.getDisplayName()).isEqualTo("Agent 3");

        ClocktowerAgentInstancePo instance = instanceRepository
                .findByActorIdAndDeletedFalse(descriptor.actorId())
                .orElseThrow();
        assertThat(instance.getId()).isEqualTo(descriptor.agentInstanceId());
        assertThat(instance.getRoomId()).isEqualTo(77001L);
        assertThat(instance.getRoomSeatId()).isEqualTo(seat.getId());
        assertThat(instance.getAutoMode()).isEqualTo(ClocktowerAgentAutoMode.FULL_AUTO);

        ClocktowerRoomSeatPo boundSeat = roomSeatRepository.findById(seat.getId()).orElseThrow();
        assertThat(boundSeat.getUserId()).isNull();
        assertThat(boundSeat.getActorId()).isEqualTo(descriptor.actorId());
        assertThat(boundSeat.getActorType()).isEqualTo(ClocktowerActorType.AGENT);
        assertThat(boundSeat.getAgentInstanceId()).isEqualTo(descriptor.agentInstanceId());
        assertThat(boundSeat.getDisplayName()).isEqualTo("Agent 3");
        assertThat(boundSeat.getRoleCode()).isEqualTo("CHEF");
        assertThat(boundSeat.getStatus()).isEqualTo("OCCUPIED");

        Map<String, Object> metadata = objectMapper.readValue(boundSeat.getMetadataJson(), MAP_TYPE);
        assertThat(agentSeatService.isSystemAgentSeat(
                boundSeat.getActorType(), boundSeat.getAgentInstanceId(), metadata)).isTrue();
    }

    @Test
    void createAgentForRoomSeatSupportsExplicitProfileDisplayNameAndAutoMode() {
        ClocktowerRoomSeatPo seat = openRoomSeat(78001L, 2, "EMPATH");

        ClocktowerAgentSeatDescriptor descriptor = agentSeatService.createAgentForRoomSeat(
                78001L, seat.getId(), 2, "Quiet Bot", "EMPATH", "quiet", ClocktowerAgentAutoMode.ST_APPROVAL);

        assertThat(descriptor.displayName()).isEqualTo("Quiet Bot");
        assertThat(descriptor.profileName()).isEqualTo("quiet");
        assertThat(descriptor.autoMode()).isEqualTo(ClocktowerAgentAutoMode.ST_APPROVAL);
        assertThat(agentSeatService.agentsOfRoom(78001L))
                .extracting(ClocktowerAgentInstancePo::getId)
                .containsExactly(descriptor.agentInstanceId());
    }

    @Test
    void createAgentForRoomSeatRejectsUnknownProfileMismatchedSeatAndDuplicateBinding() {
        ClocktowerRoomSeatPo seat = openRoomSeat(79001L, 1, "MONK");

        assertThatThrownBy(() -> agentSeatService.createAgentForRoomSeat(
                79001L, seat.getId(), 1, null, "MONK", "missing-profile", null))
                .isInstanceOf(ClocktowerException.class)
                .hasMessageContaining("CLOCKTOWER_AGENT_PROFILE_NOT_FOUND");

        assertThatThrownBy(() -> agentSeatService.createAgentForRoomSeat(
                79002L, seat.getId(), 1, null, "MONK", null, null))
                .isInstanceOf(ClocktowerException.class)
                .hasMessageContaining("CLOCKTOWER_AGENT_ROOM_SEAT_MISMATCH");

        ClocktowerAgentSeatDescriptor descriptor = agentSeatService.createAgentForRoomSeat(
                79001L, seat.getId(), 1, null, "MONK", null, null);
        assertThat(descriptor.agentInstanceId()).isNotNull();

        assertThatThrownBy(() -> agentSeatService.createAgentForRoomSeat(
                79001L, seat.getId(), 1, null, "MONK", null, null))
                .isInstanceOf(ClocktowerException.class)
                .hasMessageContaining("CLOCKTOWER_AGENT_SEAT_ALREADY_BOUND");
    }

    @Test
    void createAgentForRoomSeatRejectsInvalidAutoMode() {
        ClocktowerRoomSeatPo seat = openRoomSeat(80001L, 1, "IMP");

        assertThatThrownBy(() -> agentSeatService.createAgentForRoomSeat(
                80001L, seat.getId(), 1, null, "IMP", null, "ROBOT"))
                .isInstanceOf(ClocktowerException.class)
                .hasMessageContaining("CLOCKTOWER_AGENT_AUTO_MODE_INVALID");
    }

    @Test
    void isSystemAgentSeatRequiresAllStrongSignals() {
        Map<String, Object> validMetadata = Map.of(
                "systemManaged", true,
                "createdBy", "agentSeatCount",
                "agent", true
        );

        assertThat(agentSeatService.isSystemAgentSeat(
                ClocktowerActorType.AGENT, 90001L, validMetadata)).isTrue();
        assertThat(agentSeatService.isSystemAgentSeat(
                ClocktowerActorType.HUMAN, 90001L, validMetadata)).isFalse();
        assertThat(agentSeatService.isSystemAgentSeat(
                ClocktowerActorType.AGENT, null, validMetadata)).isFalse();
        assertThat(agentSeatService.isSystemAgentSeat(
                ClocktowerActorType.AGENT, 90001L, Map.of("agent", true))).isFalse();
        assertThat(agentSeatService.isSystemAgentSeat(
                ClocktowerActorType.AGENT, 90001L, Map.of("systemManaged", true, "createdBy", "manual"))).isFalse();
    }

    @Test
    void agentsOfGameReturnsLinkedInstances() {
        ClocktowerRoomSeatPo seat = openRoomSeat(81001L, 4, "POISONER");
        ClocktowerAgentSeatDescriptor descriptor = agentSeatService.createAgentForRoomSeat(
                81001L, seat.getId(), 4, null, "POISONER", null, null);
        ClocktowerAgentInstancePo instance = instanceRepository.findByIdAndDeletedFalse(
                descriptor.agentInstanceId()).orElseThrow();
        instance.setGameId(82001L);
        instanceRepository.saveAndFlush(instance);

        assertThat(agentSeatService.agentsOfGame(82001L))
                .extracting(ClocktowerAgentInstancePo::getId)
                .containsExactly(descriptor.agentInstanceId());
        assertThat(agentSeatService.agentsOfGame(null)).isEmpty();
    }

    private ClocktowerRoomSeatPo openRoomSeat(Long roomId, int seatNo, String roleCode) {
        ClocktowerRoomSeatPo seat = new ClocktowerRoomSeatPo();
        seat.setRoomId(roomId);
        seat.setSeatNo(seatNo);
        seat.setDisplayName("Seat " + seatNo);
        seat.setRoleCode(roleCode);
        seat.setStatus("OPEN");
        seat.setMetadataJson("{\"ready\":false}");
        return roomSeatRepository.saveAndFlush(seat);
    }
}
```

- [ ] **Step 2: Run service test to verify it fails before service exists**

Run:

```bash
cd be && ./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerAgentSeatServiceTests test
```

Expected result:

```text
Compilation failure
package top.egon.mario.clocktower.agent.service does not exist
```

- [ ] **Step 3: Create DTO records**

Create `be/src/main/java/top/egon/mario/clocktower/agent/dto/ClocktowerAgentSeatDescriptor.java`:

```java
package top.egon.mario.clocktower.agent.dto;

import java.util.Map;

public record ClocktowerAgentSeatDescriptor(
        Long actorId,
        Long agentInstanceId,
        Long roomId,
        Long roomSeatId,
        int seatNo,
        String displayName,
        String roleCode,
        String profileName,
        String autoMode,
        Map<String, Object> metadata
) {
}
```

Create `be/src/main/java/top/egon/mario/clocktower/agent/dto/ClocktowerAgentInstanceView.java`:

```java
package top.egon.mario.clocktower.agent.dto;

public record ClocktowerAgentInstanceView(
        Long instanceId,
        Long actorId,
        Long roomId,
        Long gameId,
        Long roomSeatId,
        Long gameSeatId,
        String displayName,
        String profileName,
        String status,
        String autoMode
) {
}
```

- [ ] **Step 4: Create service interface**

Create `be/src/main/java/top/egon/mario/clocktower/agent/service/ClocktowerAgentSeatService.java`:

```java
package top.egon.mario.clocktower.agent.service;

import top.egon.mario.clocktower.agent.dto.ClocktowerAgentSeatDescriptor;
import top.egon.mario.clocktower.agent.po.ClocktowerAgentInstancePo;

import java.util.List;
import java.util.Map;

public interface ClocktowerAgentSeatService {

    ClocktowerAgentSeatDescriptor createAgentForRoomSeat(Long roomId,
                                                         Long roomSeatId,
                                                         int seatNo,
                                                         String displayName,
                                                         String roleCode,
                                                         String profileName,
                                                         String autoMode);

    boolean isSystemAgentSeat(String actorType, Long agentInstanceId, Map<String, Object> metadata);

    List<ClocktowerAgentInstancePo> agentsOfRoom(Long roomId);

    List<ClocktowerAgentInstancePo> agentsOfGame(Long gameId);
}
```

- [ ] **Step 5: Create service implementation**

Create `be/src/main/java/top/egon/mario/clocktower/agent/service/impl/ClocktowerAgentSeatServiceImpl.java`:

```java
package top.egon.mario.clocktower.agent.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.egon.mario.clocktower.agent.constant.ClocktowerActorType;
import top.egon.mario.clocktower.agent.constant.ClocktowerAgentAutoMode;
import top.egon.mario.clocktower.agent.constant.ClocktowerAgentStatus;
import top.egon.mario.clocktower.agent.dto.ClocktowerAgentSeatDescriptor;
import top.egon.mario.clocktower.agent.po.ClocktowerActorPo;
import top.egon.mario.clocktower.agent.po.ClocktowerAgentInstancePo;
import top.egon.mario.clocktower.agent.po.ClocktowerAgentProfilePo;
import top.egon.mario.clocktower.agent.repository.ClocktowerActorRepository;
import top.egon.mario.clocktower.agent.repository.ClocktowerAgentInstanceRepository;
import top.egon.mario.clocktower.agent.repository.ClocktowerAgentProfileRepository;
import top.egon.mario.clocktower.agent.service.ClocktowerAgentSeatService;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.clocktower.game.po.ClocktowerRoomSeatPo;
import top.egon.mario.clocktower.room.repository.ClocktowerRoomSeatRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ClocktowerAgentSeatServiceImpl implements ClocktowerAgentSeatService {

    private static final String DEFAULT_PROFILE_NAME = "balanced";
    private static final String DEFAULT_AGENT_POLICY = "HEURISTIC_V0";
    private static final String CREATED_BY_AGENT_SEAT_COUNT = "agentSeatCount";
    private static final String SEAT_STATUS_OCCUPIED = "OCCUPIED";
    private static final Set<String> SUPPORTED_AUTO_MODES = Set.of(
            ClocktowerAgentAutoMode.FULL_AUTO,
            ClocktowerAgentAutoMode.ST_APPROVAL,
            ClocktowerAgentAutoMode.PAUSED
    );

    private final ClocktowerActorRepository actorRepository;
    private final ClocktowerAgentProfileRepository profileRepository;
    private final ClocktowerAgentInstanceRepository instanceRepository;
    private final ClocktowerRoomSeatRepository roomSeatRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public ClocktowerAgentSeatDescriptor createAgentForRoomSeat(Long roomId, Long roomSeatId, int seatNo,
                                                               String displayName, String roleCode,
                                                               String profileName, String autoMode) {
        if (roomId == null || roomSeatId == null || seatNo <= 0) {
            throw new ClocktowerException("CLOCKTOWER_AGENT_ROOM_SEAT_NOT_FOUND");
        }
        String resolvedProfileName = resolveProfileName(profileName);
        String resolvedAutoMode = resolveAutoMode(autoMode);
        ClocktowerAgentProfilePo profile = profileRepository.findFirstByNameAndDeletedFalse(resolvedProfileName)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_AGENT_PROFILE_NOT_FOUND"));
        ClocktowerRoomSeatPo seat = roomSeatRepository.findById(roomSeatId)
                .filter(existing -> !existing.isDeleted())
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_AGENT_ROOM_SEAT_NOT_FOUND"));
        if (!roomId.equals(seat.getRoomId()) || seatNo != seat.getSeatNo()) {
            throw new ClocktowerException("CLOCKTOWER_AGENT_ROOM_SEAT_MISMATCH");
        }
        if (seat.getAgentInstanceId() != null) {
            throw new ClocktowerException("CLOCKTOWER_AGENT_SEAT_ALREADY_BOUND");
        }

        String resolvedDisplayName = resolveDisplayName(displayName, profile, seatNo);
        Map<String, Object> seatMetadata = agentSeatMetadata(resolvedAutoMode);

        ClocktowerActorPo actor = new ClocktowerActorPo();
        actor.setActorType(ClocktowerActorType.AGENT);
        actor.setDisplayName(resolvedDisplayName);
        actor.setStatus(ClocktowerAgentStatus.ACTIVE);
        actor.setMetadataJson(writeJson(actorMetadata(profile, seatNo)));
        ClocktowerActorPo savedActor = actorRepository.saveAndFlush(actor);

        ClocktowerAgentInstancePo instance = new ClocktowerAgentInstancePo();
        instance.setRoomId(roomId);
        instance.setProfileId(profile.getId());
        instance.setActorId(savedActor.getId());
        instance.setRoomSeatId(roomSeatId);
        instance.setStatus(ClocktowerAgentStatus.ACTIVE);
        instance.setAutoMode(resolvedAutoMode);
        instance.setMetadataJson(writeJson(instanceMetadata(profile, seatNo, resolvedAutoMode)));
        ClocktowerAgentInstancePo savedInstance = instanceRepository.saveAndFlush(instance);

        seat.setActorId(savedActor.getId());
        seat.setActorType(ClocktowerActorType.AGENT);
        seat.setAgentInstanceId(savedInstance.getId());
        seat.setUserId(null);
        seat.setDisplayName(resolvedDisplayName);
        seat.setRoleCode(roleCode);
        seat.setStatus(SEAT_STATUS_OCCUPIED);
        seat.setMetadataJson(writeJson(seatMetadata));
        roomSeatRepository.saveAndFlush(seat);

        return new ClocktowerAgentSeatDescriptor(savedActor.getId(), savedInstance.getId(), roomId, roomSeatId,
                seatNo, resolvedDisplayName, roleCode, profile.getName(), resolvedAutoMode, Map.copyOf(seatMetadata));
    }

    @Override
    public boolean isSystemAgentSeat(String actorType, Long agentInstanceId, Map<String, Object> metadata) {
        if (!ClocktowerActorType.AGENT.equals(actorType) || agentInstanceId == null || metadata == null) {
            return false;
        }
        return Boolean.TRUE.equals(metadata.get("systemManaged"))
                && CREATED_BY_AGENT_SEAT_COUNT.equals(String.valueOf(metadata.get("createdBy")));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClocktowerAgentInstancePo> agentsOfRoom(Long roomId) {
        if (roomId == null) {
            return List.of();
        }
        return instanceRepository.findByRoomIdAndDeletedFalseOrderByIdAsc(roomId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClocktowerAgentInstancePo> agentsOfGame(Long gameId) {
        if (gameId == null) {
            return List.of();
        }
        return instanceRepository.findByGameIdAndDeletedFalseOrderByIdAsc(gameId);
    }

    private String resolveProfileName(String profileName) {
        return StringUtils.hasText(profileName) ? profileName.trim() : DEFAULT_PROFILE_NAME;
    }

    private String resolveAutoMode(String autoMode) {
        String resolved = StringUtils.hasText(autoMode) ? autoMode.trim() : ClocktowerAgentAutoMode.FULL_AUTO;
        if (!SUPPORTED_AUTO_MODES.contains(resolved)) {
            throw new ClocktowerException("CLOCKTOWER_AGENT_AUTO_MODE_INVALID");
        }
        return resolved;
    }

    private String resolveDisplayName(String displayName, ClocktowerAgentProfilePo profile, int seatNo) {
        if (StringUtils.hasText(displayName)) {
            return displayName.trim();
        }
        if (StringUtils.hasText(profile.getDisplayNameTemplate())) {
            String rendered = profile.getDisplayNameTemplate().replace("{n}", String.valueOf(seatNo));
            if (StringUtils.hasText(rendered)) {
                return rendered;
            }
        }
        return "Agent " + seatNo;
    }

    private Map<String, Object> agentSeatMetadata(String autoMode) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("ready", true);
        metadata.put("actorType", ClocktowerActorType.AGENT);
        metadata.put("agent", true);
        metadata.put("agentSeat", true);
        metadata.put("systemManaged", true);
        metadata.put("agentPolicy", DEFAULT_AGENT_POLICY);
        metadata.put("autoMode", autoMode);
        metadata.put("createdBy", CREATED_BY_AGENT_SEAT_COUNT);
        return metadata;
    }

    private Map<String, Object> actorMetadata(ClocktowerAgentProfilePo profile, int seatNo) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("profileName", profile.getName());
        metadata.put("seatNo", seatNo);
        metadata.put("createdBy", CREATED_BY_AGENT_SEAT_COUNT);
        return metadata;
    }

    private Map<String, Object> instanceMetadata(ClocktowerAgentProfilePo profile, int seatNo, String autoMode) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("profileName", profile.getName());
        metadata.put("seatNo", seatNo);
        metadata.put("agentPolicy", DEFAULT_AGENT_POLICY);
        metadata.put("autoMode", autoMode);
        metadata.put("createdBy", CREATED_BY_AGENT_SEAT_COUNT);
        return metadata;
    }

    private String writeJson(Map<String, ?> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new ClocktowerException("CLOCKTOWER_METADATA_INVALID");
        }
    }
}
```

- [ ] **Step 6: Run service tests**

Run:

```bash
cd be && ./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerAgentSeatServiceTests test
```

Expected result:

```text
BUILD SUCCESS
```

- [ ] **Step 7: Run all focused 02 tests**

Run:

```bash
cd be && ./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerJpaMappingTests,ClocktowerAgentRepositoryTests,ClocktowerAgentSeatFieldMappingTests,ClocktowerAgentSeatServiceTests test
```

Expected result:

```text
BUILD SUCCESS
```

- [ ] **Step 8: Run human-seat regression tests**

Run:

```bash
cd be && ./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerRoomRefactorServiceTests,ClocktowerGameLifecycleServiceTests test
```

Expected result:

```text
BUILD SUCCESS
```

- [ ] **Step 9: Commit Task 3**

Run:

```bash
git add be/src/main/java/top/egon/mario/clocktower/agent/dto \
        be/src/main/java/top/egon/mario/clocktower/agent/service \
        be/src/test/java/top/egon/mario/clocktower/agent/ClocktowerAgentSeatServiceTests.java
git commit -m "feat: add clocktower agent seat service"
```

## Final Verification

- [ ] **Step 1: Run the full 02 focused suite**

Run:

```bash
cd be && ./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerJpaMappingTests,ClocktowerSchemaMigrationTests,ClocktowerAgentRepositoryTests,ClocktowerAgentSeatFieldMappingTests,ClocktowerAgentSeatServiceTests test
```

Expected result:

```text
BUILD SUCCESS
```

- [ ] **Step 2: Run room/game regression suite**

Run:

```bash
cd be && ./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerRoomRefactorServiceTests,ClocktowerGameLifecycleServiceTests test
```

Expected result:

```text
BUILD SUCCESS
```

- [ ] **Step 3: Check formatting whitespace**

Run:

```bash
git diff --check
```

Expected result: no output and exit code `0`.

- [ ] **Step 4: Check worktree status**

Run:

```bash
git status --short
```

Expected result: no unstaged or uncommitted files after the task commits.

## Completion Checklist

- [ ] New `clocktower.agent` package exists and stays separate from generic `top.egon.mario.agent`.
- [ ] New PO classes extend `BaseAuditablePo`.
- [ ] JSON columns use `@JdbcTypeCode(SqlTypes.JSON)`.
- [ ] Repository methods from the task document exist.
- [ ] `ClocktowerRoomSeatPo` and `ClocktowerGameSeatPo` expose `actorId`, `actorType`, and `agentInstanceId`.
- [ ] `ClocktowerAgentSeatService#createAgentForRoomSeat` creates Actor + Instance and binds the room seat without `userId`.
- [ ] `isSystemAgentSeat` requires strong signals and does not trust only `metadata.agent`.
- [ ] No room creation, game start, IM, frontend, or runtime behavior is changed.
- [ ] Focused tests and human-seat regression tests pass.
