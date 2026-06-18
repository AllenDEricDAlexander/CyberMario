# Clocktower Ruling System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first production-ready Clocktower storyteller ruling system: persisted rulings, real/public life state, death/revive/execution/end-game/nomination flow rulings, audited undo, and grimoire UI controls.

**Architecture:** Add a dedicated `clocktower.ruling` backend module with its own entity, repository, DTOs, service, and controller. Rulings are command records with side effects: they update room/seat/nomination state, append events, and preserve enough snapshots for audited undo. Frontend grimoire calls the new ruling API directly, while player view and public seats read `publicLifeStatus`.

**Tech Stack:** Spring Boot 3.5, WebFlux controllers with blocking wrappers, Spring Data JPA, Flyway, Jackson, JUnit 5, Mockito, AssertJ, React 19, TypeScript, Ant Design 6, Vitest.

---

## Scope Check

This plan covers one coherent subsystem: storyteller rulings. It includes the required database migration, backend ruling module, public-vs-real life projection, grimoire UI entry points, and targeted validation. It does not implement per-player divergent realities, full role automation, or automatic win-condition inference.

## File Structure

Create backend files:

- `be/src/main/java/top/egon/mario/clocktower/common/enums/ClocktowerRulingReason.java`: fixed ruling reason enum.
- `be/src/main/java/top/egon/mario/clocktower/common/enums/ClocktowerRulingStatus.java`: ruling lifecycle enum.
- `be/src/main/java/top/egon/mario/clocktower/common/enums/ClocktowerRulingType.java`: supported ruling type enum.
- `be/src/main/java/top/egon/mario/clocktower/ruling/po/ClocktowerRulingPo.java`: persisted ruling command record and undo snapshot.
- `be/src/main/java/top/egon/mario/clocktower/ruling/repository/ClocktowerRulingRepository.java`: ruling repository.
- `be/src/main/java/top/egon/mario/clocktower/ruling/dto/ClocktowerRulingCreateRequest.java`: create request.
- `be/src/main/java/top/egon/mario/clocktower/ruling/dto/ClocktowerRulingUndoRequest.java`: undo request.
- `be/src/main/java/top/egon/mario/clocktower/ruling/dto/ClocktowerRulingResponse.java`: ruling response.
- `be/src/main/java/top/egon/mario/clocktower/ruling/dto/ClocktowerRulingApplyResponse.java`: create/undo response with grimoire.
- `be/src/main/java/top/egon/mario/clocktower/ruling/service/ClocktowerRulingService.java`: service interface.
- `be/src/main/java/top/egon/mario/clocktower/ruling/service/impl/ClocktowerRulingServiceImpl.java`: ruling implementation.
- `be/src/main/java/top/egon/mario/clocktower/ruling/web/ClocktowerRulingController.java`: ruling API controller.
- `be/src/test/java/top/egon/mario/clocktower/ruling/ClocktowerRulingServiceTests.java`: service tests.

Modify backend files:

- `be/src/main/resources/db/migration/V21__create_clocktower_ruling_system.sql`: one new Flyway migration.
- `be/src/main/java/top/egon/mario/clocktower/room/po/ClocktowerSeatPo.java`: add `publicLifeStatus`.
- `be/src/main/java/top/egon/mario/clocktower/grimoire/dto/response/GrimoireSeatResponse.java`: add public life fields.
- `be/src/main/java/top/egon/mario/clocktower/room/dto/response/ClocktowerSeatResponse.java`: add public life status and use it in public view.
- `be/src/main/java/top/egon/mario/clocktower/view/dto/PublicSeatResponse.java`: read `publicLifeStatus`.
- `be/src/main/java/top/egon/mario/clocktower/view/dto/PlayerSeatViewResponse.java`: expose `publicLifeStatus`.
- `be/src/main/java/top/egon/mario/clocktower/action/service/impl/ClocktowerActionServiceImpl.java`: keep rule checks on true `lifeStatus`.
- `be/src/main/java/top/egon/mario/clocktower/grimoire/repository/ClocktowerNominationRepository.java`: add `findByIdAndRoomIdAndDeletedFalse`.
- `be/src/test/java/top/egon/mario/clocktower/room/ClocktowerRoomTestFactory.java`: add ruling repository in-memory behavior and public status defaults.
- `be/src/test/java/top/egon/mario/clocktower/ClocktowerJpaMappingTests.java`: assert new enum/entity mapping.
- `be/src/test/java/top/egon/mario/clocktower/ClocktowerSchemaMigrationTests.java`: assert migration adds public life and ruling table.

Modify frontend files:

- `fe/src/modules/clocktower/clocktowerTypes.ts`: add ruling types and public life fields.
- `fe/src/modules/clocktower/clocktowerService.ts`: add ruling API clients.
- `fe/src/modules/clocktower/clocktowerService.test.ts`: add endpoint tests.
- `fe/src/modules/clocktower/StorytellerGrimoirePage.tsx`: add ruling controls/history.
- `fe/src/modules/clocktower/StorytellerGrimoirePage.test.tsx`: add rendering tests.
- `fe/src/modules/clocktower/GameRoomPage.test.tsx`: assert player room markup renders the public life state supplied by the player view response.

## Implementation Tasks

### Task 1: Migration and Persistence Model

**Files:**
- Create: `be/src/main/resources/db/migration/V21__create_clocktower_ruling_system.sql`
- Create: `be/src/main/java/top/egon/mario/clocktower/common/enums/ClocktowerRulingReason.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/common/enums/ClocktowerRulingStatus.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/common/enums/ClocktowerRulingType.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/ruling/po/ClocktowerRulingPo.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/ruling/repository/ClocktowerRulingRepository.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/room/po/ClocktowerSeatPo.java`
- Modify: `be/src/test/java/top/egon/mario/clocktower/ClocktowerJpaMappingTests.java`
- Modify: `be/src/test/java/top/egon/mario/clocktower/ClocktowerSchemaMigrationTests.java`

- [ ] **Step 1: Write failing mapping test for ruling enums and public life status**

Add this test to `ClocktowerJpaMappingTests`:

```java
@Test
void rulingEnumsAndSeatPublicLifeStatusExposePhaseOneContracts() throws Exception {
    assertThat(ClocktowerRulingType.valueOf("MARK_DEAD")).isEqualTo(ClocktowerRulingType.MARK_DEAD);
    assertThat(ClocktowerRulingReason.valueOf("ROLE_ABILITY")).isEqualTo(ClocktowerRulingReason.ROLE_ABILITY);
    assertThat(ClocktowerRulingStatus.valueOf("APPLIED")).isEqualTo(ClocktowerRulingStatus.APPLIED);

    Field publicLifeStatus = ClocktowerSeatPo.class.getDeclaredField("publicLifeStatus");
    assertThat(publicLifeStatus.getAnnotation(Column.class).name()).isEqualTo("public_life_status");

    ClocktowerSeatPo seat = new ClocktowerSeatPo();
    assertThat(seat.getLifeStatus()).isEqualTo("ALIVE");
    assertThat(seat.getPublicLifeStatus()).isEqualTo("ALIVE");
}
```

Add imports:

```java
import jakarta.persistence.Column;
import top.egon.mario.clocktower.common.enums.ClocktowerRulingReason;
import top.egon.mario.clocktower.common.enums.ClocktowerRulingStatus;
import top.egon.mario.clocktower.common.enums.ClocktowerRulingType;
import top.egon.mario.clocktower.room.po.ClocktowerSeatPo;
```

- [ ] **Step 2: Run mapping test to verify it fails**

Run:

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerJpaMappingTests#rulingEnumsAndSeatPublicLifeStatusExposePhaseOneContracts test
```

Expected: FAIL because `ClocktowerRulingType` and `publicLifeStatus` do not exist.

- [ ] **Step 3: Add ruling enums**

Create `ClocktowerRulingType.java`:

```java
package top.egon.mario.clocktower.common.enums;

public enum ClocktowerRulingType {
    MARK_DEAD,
    RESTORE_ALIVE,
    SET_PUBLIC_LIFE,
    EXECUTE_PLAYER,
    SKIP_EXECUTION,
    END_GAME,
    ADVANCE_PHASE,
    CLOSE_NOMINATION,
    REOPEN_NOMINATION,
    VOID_NOMINATION,
    UNDO_RULING
}
```

Create `ClocktowerRulingReason.java`:

```java
package top.egon.mario.clocktower.common.enums;

public enum ClocktowerRulingReason {
    VOTE_EXECUTION,
    ROLE_ABILITY,
    NIGHT_DEATH,
    STORYTELLER_RULING,
    PLAYER_REQUEST,
    MISTAKE_FIX,
    OTHER
}
```

Create `ClocktowerRulingStatus.java`:

```java
package top.egon.mario.clocktower.common.enums;

public enum ClocktowerRulingStatus {
    APPLIED,
    REVOKED
}
```

- [ ] **Step 4: Add public life status to seat entity**

Modify `ClocktowerSeatPo` by adding this field after `lifeStatus`:

```java
@Column(name = "public_life_status", nullable = false, length = 32)
private String publicLifeStatus = "ALIVE";
```

- [ ] **Step 5: Add ruling entity**

Create `ClocktowerRulingPo.java`:

```java
package top.egon.mario.clocktower.ruling.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import top.egon.mario.clocktower.common.enums.ClocktowerPhase;
import top.egon.mario.clocktower.common.enums.ClocktowerRulingReason;
import top.egon.mario.clocktower.common.enums.ClocktowerRulingStatus;
import top.egon.mario.clocktower.common.enums.ClocktowerRulingType;
import top.egon.mario.clocktower.common.enums.ClocktowerVisibility;
import top.egon.mario.common.entity.BaseAuditablePo;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "clocktower_ruling")
public class ClocktowerRulingPo extends BaseAuditablePo {

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Enumerated(EnumType.STRING)
    @Column(name = "ruling_type", nullable = false, length = 64)
    private ClocktowerRulingType rulingType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ClocktowerRulingStatus status = ClocktowerRulingStatus.APPLIED;

    @Column(name = "target_seat_id")
    private Long targetSeatId;

    @Column(name = "nomination_id")
    private Long nominationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_phase", length = 32)
    private ClocktowerPhase targetPhase;

    @Column(name = "public_life_status", length = 32)
    private String publicLifeStatus;

    @Column(name = "winner", length = 32)
    private String winner;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 64)
    private ClocktowerRulingReason reason;

    @Column(name = "note", nullable = false)
    private String note;

    @Column(name = "public_note")
    private String publicNote;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 32)
    private ClocktowerVisibility visibility = ClocktowerVisibility.PUBLIC;

    @Column(name = "undo_of_ruling_id")
    private Long undoOfRulingId;

    @Column(name = "event_ids_json", nullable = false)
    private String eventIdsJson = "[]";

    @Column(name = "snapshot_json", nullable = false)
    private String snapshotJson = "{}";

    @Column(name = "revoked_by")
    private Long revokedBy;

    @Column(name = "revoked_at")
    private Instant revokedAt;
}
```

- [ ] **Step 6: Add ruling repository**

Create `ClocktowerRulingRepository.java`:

```java
package top.egon.mario.clocktower.ruling.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.clocktower.ruling.po.ClocktowerRulingPo;

import java.util.List;
import java.util.Optional;

public interface ClocktowerRulingRepository extends JpaRepository<ClocktowerRulingPo, Long> {

    List<ClocktowerRulingPo> findByRoomIdAndDeletedFalseOrderByIdDesc(Long roomId);

    Optional<ClocktowerRulingPo> findByIdAndRoomIdAndDeletedFalse(Long id, Long roomId);
}
```

- [ ] **Step 7: Add Flyway migration**

Create `V21__create_clocktower_ruling_system.sql`:

```sql
ALTER TABLE clocktower_seat
    ADD COLUMN public_life_status VARCHAR(32) NOT NULL DEFAULT 'ALIVE';

UPDATE clocktower_seat
SET public_life_status = life_status
WHERE public_life_status = 'ALIVE';

CREATE TABLE clocktower_ruling (
    id BIGSERIAL PRIMARY KEY,
    room_id BIGINT NOT NULL,
    ruling_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    target_seat_id BIGINT,
    nomination_id BIGINT,
    target_phase VARCHAR(32),
    public_life_status VARCHAR(32),
    winner VARCHAR(32),
    reason VARCHAR(64) NOT NULL,
    note TEXT NOT NULL,
    public_note TEXT,
    visibility VARCHAR(32) NOT NULL,
    undo_of_ruling_id BIGINT,
    event_ids_json TEXT NOT NULL DEFAULT '[]',
    snapshot_json TEXT NOT NULL DEFAULT '{}',
    revoked_by BIGINT,
    revoked_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_clocktower_ruling_room ON clocktower_ruling (room_id);
CREATE INDEX idx_clocktower_ruling_target_seat ON clocktower_ruling (target_seat_id);
CREATE INDEX idx_clocktower_ruling_nomination ON clocktower_ruling (nomination_id);
```

- [ ] **Step 8: Add migration test assertions**

In `ClocktowerSchemaMigrationTests`, add this new test:

```java
@Test
void rulingMigrationAddsPublicLifeAndRulingTable() throws Exception {
    Path migration = Path.of("src/main/resources/db/migration/V21__create_clocktower_ruling_system.sql");

    String sql = Files.readString(migration);

    assertThat(sql).contains("ALTER TABLE clocktower_seat");
    assertThat(sql).contains("public_life_status");
    assertThat(sql).contains("CREATE TABLE clocktower_ruling");
    assertThat(sql).contains("snapshot_json");
}
```

Add imports if missing:

```java
import java.nio.file.Files;
import java.nio.file.Path;
```

- [ ] **Step 9: Run mapping and migration tests**

Run:

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false -Dtest='ClocktowerJpaMappingTests,ClocktowerSchemaMigrationTests' test
```

Expected: PASS.

- [ ] **Step 10: Commit persistence foundation**

```bash
git add be/src/main/resources/db/migration/V21__create_clocktower_ruling_system.sql \
  be/src/main/java/top/egon/mario/clocktower/common/enums/ClocktowerRulingReason.java \
  be/src/main/java/top/egon/mario/clocktower/common/enums/ClocktowerRulingStatus.java \
  be/src/main/java/top/egon/mario/clocktower/common/enums/ClocktowerRulingType.java \
  be/src/main/java/top/egon/mario/clocktower/ruling/po/ClocktowerRulingPo.java \
  be/src/main/java/top/egon/mario/clocktower/ruling/repository/ClocktowerRulingRepository.java \
  be/src/main/java/top/egon/mario/clocktower/room/po/ClocktowerSeatPo.java \
  be/src/test/java/top/egon/mario/clocktower/ClocktowerJpaMappingTests.java \
  be/src/test/java/top/egon/mario/clocktower/ClocktowerSchemaMigrationTests.java
git commit -m "feat(clocktower): add ruling persistence model"
```

### Task 2: Ruling DTOs and Test Factory Support

**Files:**
- Create: `be/src/main/java/top/egon/mario/clocktower/ruling/dto/ClocktowerRulingCreateRequest.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/ruling/dto/ClocktowerRulingUndoRequest.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/ruling/dto/ClocktowerRulingResponse.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/ruling/dto/ClocktowerRulingApplyResponse.java`
- Modify: `be/src/test/java/top/egon/mario/clocktower/room/ClocktowerRoomTestFactory.java`

- [ ] **Step 1: Add DTO records**

Create `ClocktowerRulingCreateRequest.java`:

```java
package top.egon.mario.clocktower.ruling.dto;

import top.egon.mario.clocktower.common.enums.ClocktowerPhase;
import top.egon.mario.clocktower.common.enums.ClocktowerRulingReason;
import top.egon.mario.clocktower.common.enums.ClocktowerRulingType;
import top.egon.mario.clocktower.common.enums.ClocktowerVisibility;

public record ClocktowerRulingCreateRequest(
        ClocktowerRulingType rulingType,
        Long targetSeatId,
        Long nominationId,
        ClocktowerPhase targetPhase,
        String publicLifeStatus,
        String winner,
        ClocktowerRulingReason reason,
        String note,
        String publicNote,
        ClocktowerVisibility visibility,
        boolean force
) {
}
```

Create `ClocktowerRulingUndoRequest.java`:

```java
package top.egon.mario.clocktower.ruling.dto;

public record ClocktowerRulingUndoRequest(
        String note,
        boolean force
) {
}
```

Create `ClocktowerRulingResponse.java`:

```java
package top.egon.mario.clocktower.ruling.dto;

import top.egon.mario.clocktower.common.enums.ClocktowerPhase;
import top.egon.mario.clocktower.common.enums.ClocktowerRulingReason;
import top.egon.mario.clocktower.common.enums.ClocktowerRulingStatus;
import top.egon.mario.clocktower.common.enums.ClocktowerRulingType;
import top.egon.mario.clocktower.common.enums.ClocktowerVisibility;
import top.egon.mario.clocktower.ruling.po.ClocktowerRulingPo;

public record ClocktowerRulingResponse(
        Long rulingId,
        Long roomId,
        ClocktowerRulingType rulingType,
        ClocktowerRulingStatus status,
        Long targetSeatId,
        Long nominationId,
        ClocktowerPhase targetPhase,
        String publicLifeStatus,
        String winner,
        ClocktowerRulingReason reason,
        String note,
        String publicNote,
        ClocktowerVisibility visibility,
        Long undoOfRulingId
) {

    public static ClocktowerRulingResponse from(ClocktowerRulingPo ruling) {
        return new ClocktowerRulingResponse(ruling.getId(), ruling.getRoomId(), ruling.getRulingType(),
                ruling.getStatus(), ruling.getTargetSeatId(), ruling.getNominationId(), ruling.getTargetPhase(),
                ruling.getPublicLifeStatus(), ruling.getWinner(), ruling.getReason(), ruling.getNote(),
                ruling.getPublicNote(), ruling.getVisibility(), ruling.getUndoOfRulingId());
    }
}
```

Create `ClocktowerRulingApplyResponse.java`:

```java
package top.egon.mario.clocktower.ruling.dto;

import top.egon.mario.clocktower.event.dto.ClocktowerEventResponse;
import top.egon.mario.clocktower.grimoire.dto.response.ClocktowerGrimoireResponse;

import java.util.List;

public record ClocktowerRulingApplyResponse(
        ClocktowerRulingResponse ruling,
        ClocktowerGrimoireResponse grimoire,
        List<ClocktowerEventResponse> events
) {
}
```

- [ ] **Step 2: Extend test factory with ruling repository**

In `ClocktowerRoomTestFactory.context`, add:

```java
List<ClocktowerRulingPo> rulings = new ArrayList<>();
AtomicLong rulingId = new AtomicLong(1L);
ClocktowerRulingRepository rulingRepository = mock(ClocktowerRulingRepository.class);
```

Add repository behavior:

```java
when(rulingRepository.save(any(ClocktowerRulingPo.class))).thenAnswer(saveRuling(rulings, rulingId));
when(rulingRepository.findByRoomIdAndDeletedFalseOrderByIdDesc(any())).thenAnswer(invocation -> rulings.stream()
        .filter(ruling -> !ruling.isDeleted() && ruling.getRoomId().equals(invocation.getArgument(0)))
        .sorted(Comparator.comparing(ClocktowerRulingPo::getId).reversed())
        .toList());
when(rulingRepository.findByIdAndRoomIdAndDeletedFalse(any(), any())).thenAnswer(invocation -> rulings.stream()
        .filter(ruling -> !ruling.isDeleted()
                && ruling.getId().equals(invocation.getArgument(0))
                && ruling.getRoomId().equals(invocation.getArgument(1)))
        .findFirst());
```

Add `saveRuling` near other save helpers:

```java
private static Answer<ClocktowerRulingPo> saveRuling(List<ClocktowerRulingPo> rulings, AtomicLong nextId) {
    return invocation -> {
        ClocktowerRulingPo ruling = invocation.getArgument(0);
        if (ruling.getId() == null) {
            ruling.setId(nextId.getAndIncrement());
            rulings.add(ruling);
        }
        return ruling;
    };
}
```

Add imports:

```java
import top.egon.mario.clocktower.ruling.po.ClocktowerRulingPo;
import top.egon.mario.clocktower.ruling.repository.ClocktowerRulingRepository;
```

Add `ClocktowerRulingRepository rulingRepository` to the `Context` record.

- [ ] **Step 3: Run test compile**

Run:

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerRoomServiceTests test
```

Expected: PASS.

- [ ] **Step 4: Commit DTOs and test support**

```bash
git add be/src/main/java/top/egon/mario/clocktower/ruling/dto \
  be/src/test/java/top/egon/mario/clocktower/room/ClocktowerRoomTestFactory.java
git commit -m "feat(clocktower): add ruling DTOs and test support"
```

### Task 3: Core Ruling Service for Life State and Game End

**Files:**
- Create: `be/src/main/java/top/egon/mario/clocktower/ruling/service/ClocktowerRulingService.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/ruling/service/impl/ClocktowerRulingServiceImpl.java`
- Create: `be/src/test/java/top/egon/mario/clocktower/ruling/ClocktowerRulingServiceTests.java`
- Modify: `be/src/test/java/top/egon/mario/clocktower/room/ClocktowerRoomTestFactory.java`

- [ ] **Step 1: Write failing tests for death, fake death, revive, and game end**

Create `ClocktowerRulingServiceTests.java` with these tests:

```java
package top.egon.mario.clocktower.ruling;

import org.junit.jupiter.api.Test;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.clocktower.common.enums.ClocktowerEventType;
import top.egon.mario.clocktower.common.enums.ClocktowerRulingReason;
import top.egon.mario.clocktower.common.enums.ClocktowerRulingType;
import top.egon.mario.clocktower.common.enums.ClocktowerRoomStatus;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.common.enums.ClocktowerVisibility;
import top.egon.mario.clocktower.grimoire.service.impl.ClocktowerGrimoireServiceImpl;
import top.egon.mario.clocktower.room.ClocktowerRoomTestFactory;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomCreateRequest;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomJoinRequest;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomStartRequest;
import top.egon.mario.clocktower.room.dto.request.RoleAssignmentRequest;
import top.egon.mario.clocktower.room.dto.response.ClocktowerRoomResponse;
import top.egon.mario.clocktower.room.service.ClocktowerRoomService;
import top.egon.mario.clocktower.ruling.dto.ClocktowerRulingCreateRequest;
import top.egon.mario.clocktower.ruling.dto.ClocktowerRulingApplyResponse;
import top.egon.mario.clocktower.ruling.service.ClocktowerRulingService;
import top.egon.mario.clocktower.ruling.service.impl.ClocktowerRulingServiceImpl;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClocktowerRulingServiceTests {

    private final ClocktowerRoomTestFactory.Context context = ClocktowerRoomTestFactory.context();
    private final ClocktowerRoomService roomService = context.roomService();
    private final ClocktowerRulingService rulingService = new ClocktowerRulingServiceImpl(context.roomRepository(),
            context.seatRepository(), context.nominationRepository(), context.rulingRepository(),
            context.eventService(), context.objectMapper(), new ClocktowerGrimoireServiceImpl(context.roomRepository(),
            context.seatRepository(), context.grimoireEntryRepository(), context.markerRepository(),
            context.storytellerTaskRepository(), context.nightOrderRepository(), context.roleRepository(),
            context.eventService()));

    @Test
    void markDeadUpdatesRealAndPublicLife() {
        ClocktowerRoomResponse room = startedRoom();
        Long targetSeatId = room.seats().getFirst().seatId();

        ClocktowerRulingApplyResponse response = rulingService.create(room.roomId(), new ClocktowerRulingCreateRequest(
                ClocktowerRulingType.MARK_DEAD, targetSeatId, null, null, null, null,
                ClocktowerRulingReason.NIGHT_DEATH, "夜晚死亡", "一名玩家死亡", ClocktowerVisibility.PUBLIC, false),
                storytellerPrincipal());

        assertThat(response.ruling().rulingType()).isEqualTo(ClocktowerRulingType.MARK_DEAD);
        assertThat(response.events()).extracting(event -> event.eventType()).contains(ClocktowerEventType.PLAYER_DIED);
        assertThat(context.seatRepository().findByIdAndRoomIdAndDeletedFalse(targetSeatId, room.roomId()).orElseThrow()
                .getLifeStatus()).isEqualTo("DEAD");
        assertThat(context.seatRepository().findByIdAndRoomIdAndDeletedFalse(targetSeatId, room.roomId()).orElseThrow()
                .getPublicLifeStatus()).isEqualTo("DEAD");
    }

    @Test
    void setPublicLifeOnlyChangesPublicLife() {
        ClocktowerRoomResponse room = startedRoom();
        Long targetSeatId = room.seats().getFirst().seatId();

        rulingService.create(room.roomId(), new ClocktowerRulingCreateRequest(
                ClocktowerRulingType.SET_PUBLIC_LIFE, targetSeatId, null, null, "DEAD", null,
                ClocktowerRulingReason.STORYTELLER_RULING, "假死", "一名玩家死亡", ClocktowerVisibility.PUBLIC, false),
                storytellerPrincipal());

        assertThat(context.seatRepository().findByIdAndRoomIdAndDeletedFalse(targetSeatId, room.roomId()).orElseThrow()
                .getLifeStatus()).isEqualTo("ALIVE");
        assertThat(context.seatRepository().findByIdAndRoomIdAndDeletedFalse(targetSeatId, room.roomId()).orElseThrow()
                .getPublicLifeStatus()).isEqualTo("DEAD");
    }

    @Test
    void restoreAliveUpdatesRealAndPublicLife() {
        ClocktowerRoomResponse room = startedRoom();
        Long targetSeatId = room.seats().getFirst().seatId();
        rulingService.create(room.roomId(), new ClocktowerRulingCreateRequest(
                ClocktowerRulingType.MARK_DEAD, targetSeatId, null, null, null, null,
                ClocktowerRulingReason.NIGHT_DEATH, "夜晚死亡", "一名玩家死亡", ClocktowerVisibility.PUBLIC, false),
                storytellerPrincipal());

        rulingService.create(room.roomId(), new ClocktowerRulingCreateRequest(
                ClocktowerRulingType.RESTORE_ALIVE, targetSeatId, null, null, null, null,
                ClocktowerRulingReason.ROLE_ABILITY, "角色能力复活", "一名玩家复活", ClocktowerVisibility.PUBLIC, false),
                storytellerPrincipal());

        assertThat(context.seatRepository().findByIdAndRoomIdAndDeletedFalse(targetSeatId, room.roomId()).orElseThrow()
                .getLifeStatus()).isEqualTo("ALIVE");
        assertThat(context.seatRepository().findByIdAndRoomIdAndDeletedFalse(targetSeatId, room.roomId()).orElseThrow()
                .getPublicLifeStatus()).isEqualTo("ALIVE");
    }

    @Test
    void endGameRequiresNoteAndCanOmitWinner() {
        ClocktowerRoomResponse room = startedRoom();

        assertThatThrownBy(() -> rulingService.create(room.roomId(), new ClocktowerRulingCreateRequest(
                ClocktowerRulingType.END_GAME, null, null, null, null, null,
                ClocktowerRulingReason.STORYTELLER_RULING, "", "游戏结束", ClocktowerVisibility.PUBLIC, false),
                storytellerPrincipal())).isInstanceOf(ClocktowerException.class)
                .hasMessageContaining("CLOCKTOWER_RULING_NOTE_REQUIRED");

        rulingService.create(room.roomId(), new ClocktowerRulingCreateRequest(
                ClocktowerRulingType.END_GAME, null, null, null, null, null,
                ClocktowerRulingReason.STORYTELLER_RULING, "测试局结束", "游戏结束", ClocktowerVisibility.PUBLIC, false),
                storytellerPrincipal());

        assertThat(context.roomRepository().findByIdAndDeletedFalse(room.roomId()).orElseThrow().getStatus())
                .isEqualTo(ClocktowerRoomStatus.ENDED);
    }

    private ClocktowerRoomResponse startedRoom() {
        ClocktowerRoomResponse room = roomService.create(new ClocktowerRoomCreateRequest(
                "周五暗流", ClocktowerScriptCode.TROUBLE_BREWING, 5, null, null,
                List.of("EMPATH", "CHEF", "MONK", "POISONER", "IMP"), "HUMAN", false, true, 0),
                storytellerPrincipal());
        for (int i = 0; i < room.seats().size(); i++) {
            roomService.join(room.roomId(), new ClocktowerRoomJoinRequest(i + 1, "Player " + (i + 1), null),
                    principal((long) i + 2, "player-" + (i + 1)));
        }
        ClocktowerRoomResponse joined = roomService.get(room.roomId());
        roomService.start(joined.roomId(), new ClocktowerRoomStartRequest(List.of(
                new RoleAssignmentRequest(joined.seats().get(0).seatId(), "EMPATH"),
                new RoleAssignmentRequest(joined.seats().get(1).seatId(), "CHEF"),
                new RoleAssignmentRequest(joined.seats().get(2).seatId(), "MONK"),
                new RoleAssignmentRequest(joined.seats().get(3).seatId(), "POISONER"),
                new RoleAssignmentRequest(joined.seats().get(4).seatId(), "IMP")
        ), false), storytellerPrincipal());
        return roomService.get(joined.roomId());
    }

    private static RbacPrincipal storytellerPrincipal() {
        return principal(1L, "storyteller");
    }

    private static RbacPrincipal principal(Long userId, String username) {
        return new RbacPrincipal(userId, username, Set.of("CLOCKTOWER_STORYTELLER"), Set.of(), "v1");
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerRulingServiceTests test
```

Expected: FAIL because `ClocktowerRulingService` and implementation do not exist.

- [ ] **Step 3: Create service interface**

Create `ClocktowerRulingService.java`:

```java
package top.egon.mario.clocktower.ruling.service;

import top.egon.mario.clocktower.ruling.dto.ClocktowerRulingApplyResponse;
import top.egon.mario.clocktower.ruling.dto.ClocktowerRulingCreateRequest;
import top.egon.mario.clocktower.ruling.dto.ClocktowerRulingResponse;
import top.egon.mario.clocktower.ruling.dto.ClocktowerRulingUndoRequest;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;

public interface ClocktowerRulingService {

    ClocktowerRulingApplyResponse create(Long roomId, ClocktowerRulingCreateRequest request, RbacPrincipal principal);

    List<ClocktowerRulingResponse> list(Long roomId, RbacPrincipal principal);

    ClocktowerRulingApplyResponse undo(Long roomId, Long rulingId, ClocktowerRulingUndoRequest request,
                                       RbacPrincipal principal);
}
```

- [ ] **Step 4: Implement minimal life and end-game rulings**

Create `ClocktowerRulingServiceImpl.java` with this structure:

```java
package top.egon.mario.clocktower.ruling.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.egon.mario.clocktower.common.ClocktowerAccess;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.clocktower.common.enums.ClocktowerEventType;
import top.egon.mario.clocktower.common.enums.ClocktowerPhase;
import top.egon.mario.clocktower.common.enums.ClocktowerRoomStatus;
import top.egon.mario.clocktower.common.enums.ClocktowerRulingStatus;
import top.egon.mario.clocktower.common.enums.ClocktowerRulingType;
import top.egon.mario.clocktower.common.enums.ClocktowerVisibility;
import top.egon.mario.clocktower.event.dto.ClocktowerEventAppendRequest;
import top.egon.mario.clocktower.event.dto.ClocktowerEventResponse;
import top.egon.mario.clocktower.event.service.ClocktowerEventService;
import top.egon.mario.clocktower.grimoire.dto.response.ClocktowerGrimoireResponse;
import top.egon.mario.clocktower.grimoire.po.ClocktowerNominationPo;
import top.egon.mario.clocktower.grimoire.repository.ClocktowerNominationRepository;
import top.egon.mario.clocktower.grimoire.service.ClocktowerGrimoireService;
import top.egon.mario.clocktower.room.po.ClocktowerRoomPo;
import top.egon.mario.clocktower.room.po.ClocktowerSeatPo;
import top.egon.mario.clocktower.room.repository.ClocktowerRoomRepository;
import top.egon.mario.clocktower.room.repository.ClocktowerSeatRepository;
import top.egon.mario.clocktower.ruling.dto.ClocktowerRulingApplyResponse;
import top.egon.mario.clocktower.ruling.dto.ClocktowerRulingCreateRequest;
import top.egon.mario.clocktower.ruling.dto.ClocktowerRulingResponse;
import top.egon.mario.clocktower.ruling.dto.ClocktowerRulingUndoRequest;
import top.egon.mario.clocktower.ruling.po.ClocktowerRulingPo;
import top.egon.mario.clocktower.ruling.repository.ClocktowerRulingRepository;
import top.egon.mario.clocktower.ruling.service.ClocktowerRulingService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ClocktowerRulingServiceImpl implements ClocktowerRulingService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ClocktowerRoomRepository roomRepository;
    private final ClocktowerSeatRepository seatRepository;
    private final ClocktowerNominationRepository nominationRepository;
    private final ClocktowerRulingRepository rulingRepository;
    private final ClocktowerEventService eventService;
    private final ObjectMapper objectMapper;
    private final ClocktowerGrimoireService grimoireService;

    @Override
    @Transactional
    public ClocktowerRulingApplyResponse create(Long roomId, ClocktowerRulingCreateRequest request,
                                                RbacPrincipal principal) {
        ClocktowerRoomPo room = roomRepository.findLockedByIdAndDeletedFalse(roomId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_ROOM_NOT_FOUND"));
        ClocktowerAccess.requireStoryteller(room, principal);
        validate(request);
        ClocktowerRulingPo ruling = newRuling(room, request);
        ruling.setSnapshotJson(snapshot(room, request));
        List<ClocktowerEventResponse> events = apply(room, ruling, request, principal);
        ruling.setEventIdsJson(writeJson(events.stream().map(ClocktowerEventResponse::eventId).toList()));
        ClocktowerRulingPo saved = rulingRepository.save(ruling);
        ClocktowerGrimoireResponse grimoire = grimoireService.getGrimoire(roomId, principal);
        return new ClocktowerRulingApplyResponse(ClocktowerRulingResponse.from(saved), grimoire, events);
    }

    @Override
    public List<ClocktowerRulingResponse> list(Long roomId, RbacPrincipal principal) {
        ClocktowerRoomPo room = roomRepository.findByIdAndDeletedFalse(roomId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_ROOM_NOT_FOUND"));
        ClocktowerAccess.requireStoryteller(room, principal);
        return rulingRepository.findByRoomIdAndDeletedFalseOrderByIdDesc(roomId).stream()
                .map(ClocktowerRulingResponse::from)
                .toList();
    }

    @Override
    public ClocktowerRulingApplyResponse undo(Long roomId, Long rulingId, ClocktowerRulingUndoRequest request,
                                              RbacPrincipal principal) {
        throw new ClocktowerException("CLOCKTOWER_RULING_UNDO_NOT_READY");
    }

    private void validate(ClocktowerRulingCreateRequest request) {
        if (request.rulingType() == null) {
            throw new ClocktowerException("CLOCKTOWER_RULING_TYPE_REQUIRED");
        }
        if (request.reason() == null) {
            throw new ClocktowerException("CLOCKTOWER_RULING_REASON_REQUIRED");
        }
        boolean highRisk = request.rulingType() != ClocktowerRulingType.MARK_DEAD
                && request.rulingType() != ClocktowerRulingType.RESTORE_ALIVE
                && request.rulingType() != ClocktowerRulingType.ADVANCE_PHASE;
        if (highRisk && !StringUtils.hasText(request.note())) {
            throw new ClocktowerException("CLOCKTOWER_RULING_NOTE_REQUIRED");
        }
        if (request.rulingType() == ClocktowerRulingType.END_GAME && !StringUtils.hasText(request.note())) {
            throw new ClocktowerException("CLOCKTOWER_RULING_NOTE_REQUIRED");
        }
    }

    private ClocktowerRulingPo newRuling(ClocktowerRoomPo room, ClocktowerRulingCreateRequest request) {
        ClocktowerRulingPo ruling = new ClocktowerRulingPo();
        ruling.setRoomId(room.getId());
        ruling.setRulingType(request.rulingType());
        ruling.setStatus(ClocktowerRulingStatus.APPLIED);
        ruling.setTargetSeatId(request.targetSeatId());
        ruling.setNominationId(request.nominationId());
        ruling.setTargetPhase(request.targetPhase());
        ruling.setPublicLifeStatus(request.publicLifeStatus());
        ruling.setWinner(request.winner());
        ruling.setReason(request.reason());
        ruling.setNote(text(request.note()));
        ruling.setPublicNote(request.publicNote());
        ruling.setVisibility(request.visibility() == null ? ClocktowerVisibility.PUBLIC : request.visibility());
        return ruling;
    }

    private List<ClocktowerEventResponse> apply(ClocktowerRoomPo room, ClocktowerRulingPo ruling,
                                                ClocktowerRulingCreateRequest request, RbacPrincipal principal) {
        return switch (request.rulingType()) {
            case MARK_DEAD -> applyLife(room, ruling, principal, "DEAD", "DEAD", ClocktowerEventType.PLAYER_DIED);
            case RESTORE_ALIVE -> applyLife(room, ruling, principal, "ALIVE", "ALIVE",
                    ClocktowerEventType.STORYTELLER_RULING);
            case SET_PUBLIC_LIFE -> applyPublicLife(room, ruling, request, principal);
            case END_GAME -> applyEndGame(room, ruling, principal);
            default -> throw new ClocktowerException("CLOCKTOWER_RULING_TYPE_NOT_SUPPORTED");
        };
    }

    private List<ClocktowerEventResponse> applyLife(ClocktowerRoomPo room, ClocktowerRulingPo ruling,
                                                    RbacPrincipal principal, String realStatus, String publicStatus,
                                                    ClocktowerEventType eventType) {
        ClocktowerSeatPo seat = seatRepository.findByIdAndRoomIdAndDeletedFalse(ruling.getTargetSeatId(), room.getId())
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_SEAT_NOT_FOUND"));
        seat.setLifeStatus(realStatus);
        seat.setPublicLifeStatus(publicStatus);
        seatRepository.save(seat);
        return List.of(append(room, principal, ruling.getTargetSeatId(), eventType, ruling));
    }

    private List<ClocktowerEventResponse> applyPublicLife(ClocktowerRoomPo room, ClocktowerRulingPo ruling,
                                                          ClocktowerRulingCreateRequest request,
                                                          RbacPrincipal principal) {
        if (!StringUtils.hasText(request.publicLifeStatus())) {
            throw new ClocktowerException("CLOCKTOWER_PUBLIC_LIFE_STATUS_REQUIRED");
        }
        ClocktowerSeatPo seat = seatRepository.findByIdAndRoomIdAndDeletedFalse(ruling.getTargetSeatId(), room.getId())
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_SEAT_NOT_FOUND"));
        seat.setPublicLifeStatus(request.publicLifeStatus());
        seatRepository.save(seat);
        return List.of(append(room, principal, ruling.getTargetSeatId(), ClocktowerEventType.STORYTELLER_RULING, ruling));
    }

    private List<ClocktowerEventResponse> applyEndGame(ClocktowerRoomPo room, ClocktowerRulingPo ruling,
                                                       RbacPrincipal principal) {
        room.setStatus(ClocktowerRoomStatus.ENDED);
        room.setPhase(ClocktowerPhase.ENDED);
        roomRepository.save(room);
        return List.of(append(room, principal, null, ClocktowerEventType.GAME_ENDED, ruling));
    }

    private ClocktowerEventResponse append(ClocktowerRoomPo room, RbacPrincipal principal, Long targetSeatId,
                                           ClocktowerEventType eventType, ClocktowerRulingPo ruling) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("rulingType", ruling.getRulingType().name());
        payload.put("reason", ruling.getReason().name());
        payload.put("publicNote", publicText(ruling));
        payload.put("winner", ruling.getWinner());
        return eventService.append(new ClocktowerEventAppendRequest(room.getId(), eventType,
                room.getPhase(), room.getCurrentDayNo(), room.getCurrentNightNo(),
                principal == null ? null : principal.userId(), null, targetSeatId, ruling.getVisibility(), List.of(),
                payload));
    }

    private String snapshot(ClocktowerRoomPo room, ClocktowerRulingCreateRequest request) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("roomStatus", room.getStatus().name());
        snapshot.put("roomPhase", room.getPhase().name());
        snapshot.put("currentDayNo", room.getCurrentDayNo());
        snapshot.put("currentNightNo", room.getCurrentNightNo());
        if (request.targetSeatId() != null) {
            seatRepository.findByIdAndRoomIdAndDeletedFalse(request.targetSeatId(), room.getId()).ifPresent(seat -> {
                snapshot.put("seatId", seat.getId());
                snapshot.put("lifeStatus", seat.getLifeStatus());
                snapshot.put("publicLifeStatus", seat.getPublicLifeStatus());
                snapshot.put("hasDeadVote", seat.isHasDeadVote());
            });
        }
        if (request.nominationId() != null) {
            nominationRepository.findById(request.nominationId()).ifPresent(nomination -> {
                snapshot.put("nominationId", nomination.getId());
                snapshot.put("nominationStatus", nomination.getStatus());
                snapshot.put("voteCount", nomination.getVoteCount());
                snapshot.put("executed", nomination.isExecuted());
            });
        }
        return writeJson(snapshot);
    }

    private String publicText(ClocktowerRulingPo ruling) {
        if (StringUtils.hasText(ruling.getPublicNote())) {
            return ruling.getPublicNote();
        }
        return switch (ruling.getRulingType()) {
            case MARK_DEAD -> "一名玩家死亡";
            case RESTORE_ALIVE -> "一名玩家复活";
            case END_GAME -> "游戏结束";
            default -> "说书人裁定";
        };
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("CLOCKTOWER_RULING_JSON_INVALID", e);
        }
    }

    private Map<String, Object> readSnapshot(ClocktowerRulingPo ruling) {
        try {
            return objectMapper.readValue(ruling.getSnapshotJson(), MAP_TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("CLOCKTOWER_RULING_JSON_INVALID", e);
        }
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
```

- [ ] **Step 5: Run core ruling tests**

Run:

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerRulingServiceTests test
```

Expected: PASS for the four tests in this task.

- [ ] **Step 6: Commit core ruling service**

```bash
git add be/src/main/java/top/egon/mario/clocktower/ruling/service \
  be/src/main/java/top/egon/mario/clocktower/ruling/service/impl \
  be/src/test/java/top/egon/mario/clocktower/ruling/ClocktowerRulingServiceTests.java
git commit -m "feat(clocktower): add core ruling service"
```

### Task 4: Execution and Nomination Flow Rulings

**Files:**
- Modify: `be/src/main/java/top/egon/mario/clocktower/grimoire/repository/ClocktowerNominationRepository.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/ruling/service/impl/ClocktowerRulingServiceImpl.java`
- Modify: `be/src/test/java/top/egon/mario/clocktower/ruling/ClocktowerRulingServiceTests.java`
- Modify: `be/src/test/java/top/egon/mario/clocktower/room/ClocktowerRoomTestFactory.java`

- [ ] **Step 1: Add failing tests for execution and nomination control**

Add these tests to `ClocktowerRulingServiceTests`:

```java
@Test
void executePlayerCanTargetAnySeatAndMarksExecutedDeath() {
    ClocktowerRoomResponse room = startedRoom();
    Long targetSeatId = room.seats().get(3).seatId();

    ClocktowerRulingApplyResponse response = rulingService.create(room.roomId(), new ClocktowerRulingCreateRequest(
            ClocktowerRulingType.EXECUTE_PLAYER, targetSeatId, null, null, null, null,
            ClocktowerRulingReason.ROLE_ABILITY, "猎手开枪处决邪恶玩家", "一名玩家被处决", ClocktowerVisibility.PUBLIC, false),
            storytellerPrincipal());

    assertThat(response.events()).extracting(event -> event.eventType())
            .contains(ClocktowerEventType.PLAYER_EXECUTED, ClocktowerEventType.PLAYER_DIED);
    assertThat(context.seatRepository().findByIdAndRoomIdAndDeletedFalse(targetSeatId, room.roomId()).orElseThrow()
            .getLifeStatus()).isEqualTo("DEAD");
}

@Test
void nominationRulingsCloseReopenAndVoidWithoutDeletingVotes() {
    ClocktowerRoomResponse room = startedRoom();
    Long nominator = room.seats().getFirst().seatId();
    Long nominee = room.seats().get(1).seatId();
    submitPlayerAction(room.roomId(), nominator, "NOMINATE", List.of(nominee));
    Long nominationId = context.nominationRepository().findByRoomIdAndDeletedFalseOrderByIdAsc(room.roomId())
            .getFirst().getId();

    rulingService.create(room.roomId(), new ClocktowerRulingCreateRequest(
            ClocktowerRulingType.CLOSE_NOMINATION, null, nominationId, null, null, null,
            ClocktowerRulingReason.STORYTELLER_RULING, "关闭投票", "提名关闭", ClocktowerVisibility.PUBLIC, false),
            storytellerPrincipal());
    assertThat(context.nominationRepository().findById(nominationId).orElseThrow().getStatus()).isEqualTo("CLOSED");

    rulingService.create(room.roomId(), new ClocktowerRulingCreateRequest(
            ClocktowerRulingType.REOPEN_NOMINATION, null, nominationId, null, null, null,
            ClocktowerRulingReason.MISTAKE_FIX, "误关，重开", "提名重开", ClocktowerVisibility.PUBLIC, false),
            storytellerPrincipal());
    assertThat(context.nominationRepository().findById(nominationId).orElseThrow().getStatus()).isEqualTo("OPEN");

    rulingService.create(room.roomId(), new ClocktowerRulingCreateRequest(
            ClocktowerRulingType.VOID_NOMINATION, null, nominationId, null, null, null,
            ClocktowerRulingReason.MISTAKE_FIX, "误提名，撤销", "提名撤销", ClocktowerVisibility.PUBLIC, false),
            storytellerPrincipal());
    assertThat(context.nominationRepository().findById(nominationId).orElseThrow().getStatus()).isEqualTo("VOID");
}
```

Add helper to the test class:

```java
private void submitPlayerAction(Long roomId, Long actorSeatId, String actionType, List<Long> targets) {
    new top.egon.mario.clocktower.action.service.impl.ClocktowerActionServiceImpl(context.roomRepository(),
            context.seatRepository(), context.nominationRepository(), context.voteRepository(), context.eventService())
            .submit(roomId, new top.egon.mario.clocktower.action.dto.ClocktowerActionRequest(
                    actorSeatId, actionType, targets, null, "action", java.util.Map.of(), "client-" + actionType),
                    principal(2L, "player-1"));
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerRulingServiceTests test
```

Expected: FAIL with `CLOCKTOWER_RULING_TYPE_NOT_SUPPORTED`.

- [ ] **Step 3: Add nomination repository lookup**

Modify `ClocktowerNominationRepository`:

```java
Optional<ClocktowerNominationPo> findByIdAndRoomIdAndDeletedFalse(Long id, Long roomId);
```

Update `ClocktowerRoomTestFactory` repository behavior:

```java
when(nominationRepository.findByIdAndRoomIdAndDeletedFalse(any(), any())).thenAnswer(invocation -> nominations.stream()
        .filter(nomination -> !nomination.isDeleted()
                && nomination.getId().equals(invocation.getArgument(0))
                && nomination.getRoomId().equals(invocation.getArgument(1)))
        .findFirst());
```

- [ ] **Step 4: Implement execution and nomination cases**

In `ClocktowerRulingServiceImpl.apply`, add cases:

```java
case EXECUTE_PLAYER -> applyExecution(room, ruling, principal);
case SKIP_EXECUTION -> applyNominationStatus(room, ruling, principal, "CLOSED", ClocktowerEventType.STORYTELLER_RULING);
case CLOSE_NOMINATION -> applyNominationStatus(room, ruling, principal, "CLOSED", ClocktowerEventType.STORYTELLER_RULING);
case REOPEN_NOMINATION -> applyNominationStatus(room, ruling, principal, "OPEN", ClocktowerEventType.STORYTELLER_RULING);
case VOID_NOMINATION -> applyNominationStatus(room, ruling, principal, "VOID", ClocktowerEventType.STORYTELLER_RULING);
```

Add methods:

```java
private List<ClocktowerEventResponse> applyExecution(ClocktowerRoomPo room, ClocktowerRulingPo ruling,
                                                     RbacPrincipal principal) {
    ClocktowerSeatPo seat = seatRepository.findByIdAndRoomIdAndDeletedFalse(ruling.getTargetSeatId(), room.getId())
            .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_SEAT_NOT_FOUND"));
    seat.setLifeStatus("DEAD");
    seat.setPublicLifeStatus("DEAD");
    seatRepository.save(seat);
    if (ruling.getNominationId() != null) {
        ClocktowerNominationPo nomination = nominationRepository
                .findByIdAndRoomIdAndDeletedFalse(ruling.getNominationId(), room.getId())
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_NOMINATION_NOT_FOUND"));
        nomination.setExecuted(true);
        nomination.setStatus("CLOSED");
        nominationRepository.save(nomination);
    }
    List<ClocktowerEventResponse> events = new ArrayList<>();
    events.add(append(room, principal, seat.getId(), ClocktowerEventType.PLAYER_EXECUTED, ruling));
    events.add(append(room, principal, seat.getId(), ClocktowerEventType.PLAYER_DIED, ruling));
    return events;
}

private List<ClocktowerEventResponse> applyNominationStatus(ClocktowerRoomPo room, ClocktowerRulingPo ruling,
                                                            RbacPrincipal principal, String status,
                                                            ClocktowerEventType eventType) {
    ClocktowerNominationPo nomination = nominationRepository
            .findByIdAndRoomIdAndDeletedFalse(ruling.getNominationId(), room.getId())
            .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_NOMINATION_NOT_FOUND"));
    nomination.setStatus(status);
    nominationRepository.save(nomination);
    return List.of(append(room, principal, nomination.getNomineeSeatId(), eventType, ruling));
}
```

- [ ] **Step 5: Run ruling service tests**

Run:

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerRulingServiceTests test
```

Expected: PASS.

- [ ] **Step 6: Commit execution and nomination rulings**

```bash
git add be/src/main/java/top/egon/mario/clocktower/grimoire/repository/ClocktowerNominationRepository.java \
  be/src/main/java/top/egon/mario/clocktower/ruling/service/impl/ClocktowerRulingServiceImpl.java \
  be/src/test/java/top/egon/mario/clocktower/ruling/ClocktowerRulingServiceTests.java \
  be/src/test/java/top/egon/mario/clocktower/room/ClocktowerRoomTestFactory.java
git commit -m "feat(clocktower): add execution and nomination rulings"
```

### Task 5: Audited Undo

**Files:**
- Modify: `be/src/main/java/top/egon/mario/clocktower/ruling/service/impl/ClocktowerRulingServiceImpl.java`
- Modify: `be/src/test/java/top/egon/mario/clocktower/ruling/ClocktowerRulingServiceTests.java`

- [ ] **Step 1: Add failing undo test**

Add this test:

```java
@Test
void undoRulingRevokesOriginalAndRestoresSnapshot() {
    ClocktowerRoomResponse room = startedRoom();
    Long targetSeatId = room.seats().getFirst().seatId();
    ClocktowerRulingApplyResponse applied = rulingService.create(room.roomId(), new ClocktowerRulingCreateRequest(
            ClocktowerRulingType.MARK_DEAD, targetSeatId, null, null, null, null,
            ClocktowerRulingReason.NIGHT_DEATH, "夜晚死亡", "一名玩家死亡", ClocktowerVisibility.PUBLIC, false),
            storytellerPrincipal());

    ClocktowerRulingApplyResponse undone = rulingService.undo(room.roomId(), applied.ruling().rulingId(),
            new ClocktowerRulingUndoRequest("误操作撤销", true), storytellerPrincipal());

    assertThat(context.rulingRepository().findByIdAndRoomIdAndDeletedFalse(applied.ruling().rulingId(), room.roomId())
            .orElseThrow().getStatus()).isEqualTo(ClocktowerRulingStatus.REVOKED);
    assertThat(undone.ruling().rulingType()).isEqualTo(ClocktowerRulingType.UNDO_RULING);
    assertThat(context.seatRepository().findByIdAndRoomIdAndDeletedFalse(targetSeatId, room.roomId()).orElseThrow()
            .getLifeStatus()).isEqualTo("ALIVE");
    assertThat(context.seatRepository().findByIdAndRoomIdAndDeletedFalse(targetSeatId, room.roomId()).orElseThrow()
            .getPublicLifeStatus()).isEqualTo("ALIVE");
}
```

Add import:

```java
import top.egon.mario.clocktower.common.enums.ClocktowerRulingStatus;
import top.egon.mario.clocktower.ruling.dto.ClocktowerRulingUndoRequest;
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerRulingServiceTests#undoRulingRevokesOriginalAndRestoresSnapshot test
```

Expected: FAIL with `CLOCKTOWER_RULING_UNDO_NOT_READY`.

- [ ] **Step 3: Implement undo**

Replace `undo` implementation with:

```java
@Override
@Transactional
public ClocktowerRulingApplyResponse undo(Long roomId, Long rulingId, ClocktowerRulingUndoRequest request,
                                          RbacPrincipal principal) {
    ClocktowerRoomPo room = roomRepository.findLockedByIdAndDeletedFalse(roomId)
            .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_ROOM_NOT_FOUND"));
    ClocktowerAccess.requireStoryteller(room, principal);
    ClocktowerRulingPo original = rulingRepository.findByIdAndRoomIdAndDeletedFalse(rulingId, roomId)
            .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_RULING_NOT_FOUND"));
    if (original.getStatus() == ClocktowerRulingStatus.REVOKED) {
        throw new ClocktowerException("CLOCKTOWER_RULING_ALREADY_REVOKED");
    }
    restoreSnapshot(room, original);
    original.setStatus(ClocktowerRulingStatus.REVOKED);
    original.setRevokedBy(principal == null ? null : principal.userId());
    original.setRevokedAt(java.time.Instant.now());
    rulingRepository.save(original);

    ClocktowerRulingPo undo = new ClocktowerRulingPo();
    undo.setRoomId(roomId);
    undo.setRulingType(ClocktowerRulingType.UNDO_RULING);
    undo.setStatus(ClocktowerRulingStatus.APPLIED);
    undo.setReason(top.egon.mario.clocktower.common.enums.ClocktowerRulingReason.MISTAKE_FIX);
    undo.setNote(text(request == null ? null : request.note()));
    undo.setPublicNote("撤销一条说书人裁定");
    undo.setVisibility(ClocktowerVisibility.STORYTELLER);
    undo.setUndoOfRulingId(original.getId());
    undo.setSnapshotJson("{}");
    ClocktowerEventResponse event = append(room, principal, original.getTargetSeatId(),
            ClocktowerEventType.STORYTELLER_RULING, undo);
    undo.setEventIdsJson(writeJson(List.of(event.eventId())));
    ClocktowerRulingPo savedUndo = rulingRepository.save(undo);
    return new ClocktowerRulingApplyResponse(ClocktowerRulingResponse.from(savedUndo),
            grimoireService.getGrimoire(roomId, principal), List.of(event));
}
```

Add helper:

```java
private void restoreSnapshot(ClocktowerRoomPo room, ClocktowerRulingPo ruling) {
    Map<String, Object> snapshot = readSnapshot(ruling);
    if (snapshot.containsKey("roomStatus")) {
        room.setStatus(ClocktowerRoomStatus.valueOf(snapshot.get("roomStatus").toString()));
        room.setPhase(ClocktowerPhase.valueOf(snapshot.get("roomPhase").toString()));
        room.setCurrentDayNo(((Number) snapshot.get("currentDayNo")).intValue());
        room.setCurrentNightNo(((Number) snapshot.get("currentNightNo")).intValue());
        roomRepository.save(room);
    }
    if (snapshot.containsKey("seatId")) {
        Long seatId = ((Number) snapshot.get("seatId")).longValue();
        ClocktowerSeatPo seat = seatRepository.findByIdAndRoomIdAndDeletedFalse(seatId, room.getId())
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_SEAT_NOT_FOUND"));
        seat.setLifeStatus(snapshot.get("lifeStatus").toString());
        seat.setPublicLifeStatus(snapshot.get("publicLifeStatus").toString());
        seat.setHasDeadVote((Boolean) snapshot.get("hasDeadVote"));
        seatRepository.save(seat);
    }
    if (snapshot.containsKey("nominationId")) {
        Long nominationId = ((Number) snapshot.get("nominationId")).longValue();
        ClocktowerNominationPo nomination = nominationRepository
                .findByIdAndRoomIdAndDeletedFalse(nominationId, room.getId())
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_NOMINATION_NOT_FOUND"));
        nomination.setStatus(snapshot.get("nominationStatus").toString());
        nomination.setVoteCount(((Number) snapshot.get("voteCount")).intValue());
        nomination.setExecuted((Boolean) snapshot.get("executed"));
        nominationRepository.save(nomination);
    }
}
```

- [ ] **Step 4: Run undo test**

Run:

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerRulingServiceTests#undoRulingRevokesOriginalAndRestoresSnapshot test
```

Expected: PASS.

- [ ] **Step 5: Run full ruling service tests**

Run:

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerRulingServiceTests test
```

Expected: PASS.

- [ ] **Step 6: Commit undo**

```bash
git add be/src/main/java/top/egon/mario/clocktower/ruling/service/impl/ClocktowerRulingServiceImpl.java \
  be/src/test/java/top/egon/mario/clocktower/ruling/ClocktowerRulingServiceTests.java
git commit -m "feat(clocktower): add audited ruling undo"
```

### Task 6: Ruling API Controller

**Files:**
- Create: `be/src/main/java/top/egon/mario/clocktower/ruling/web/ClocktowerRulingController.java`
- Modify: `be/src/test/java/top/egon/mario/clocktower/ruling/ClocktowerRulingServiceTests.java`

- [ ] **Step 1: Add authorization test at service level**

Add this test:

```java
@Test
void nonStorytellerCannotCreateRuling() {
    ClocktowerRoomResponse room = startedRoom();

    assertThatThrownBy(() -> rulingService.create(room.roomId(), new ClocktowerRulingCreateRequest(
            ClocktowerRulingType.MARK_DEAD, room.seats().getFirst().seatId(), null, null, null, null,
            ClocktowerRulingReason.NIGHT_DEATH, "夜晚死亡", "一名玩家死亡", ClocktowerVisibility.PUBLIC, false),
            principal(99L, "not-storyteller"))).isInstanceOf(ClocktowerException.class)
            .hasMessageContaining("CLOCKTOWER_STORYTELLER_FORBIDDEN");
}
```

- [ ] **Step 2: Run authorization test**

Run:

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerRulingServiceTests#nonStorytellerCannotCreateRuling test
```

Expected: PASS because service already uses `ClocktowerAccess.requireStoryteller`.

- [ ] **Step 3: Add controller**

Create `ClocktowerRulingController.java`:

```java
package top.egon.mario.clocktower.ruling.web;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.egon.mario.clocktower.common.web.ClocktowerReactiveSupport;
import top.egon.mario.clocktower.ruling.dto.ClocktowerRulingApplyResponse;
import top.egon.mario.clocktower.ruling.dto.ClocktowerRulingCreateRequest;
import top.egon.mario.clocktower.ruling.dto.ClocktowerRulingResponse;
import top.egon.mario.clocktower.ruling.dto.ClocktowerRulingUndoRequest;
import top.egon.mario.clocktower.ruling.service.ClocktowerRulingService;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/clocktower/rooms/{roomId}/rulings")
@Validated
public class ClocktowerRulingController extends ClocktowerReactiveSupport {

    private final ClocktowerRulingService rulingService;

    @PostMapping
    public Mono<ApiResponse<ClocktowerRulingApplyResponse>> create(@PathVariable Long roomId,
                                                                   @RequestBody ClocktowerRulingCreateRequest request,
                                                                   @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> rulingService.create(roomId, request, principal));
    }

    @GetMapping
    public Mono<ApiResponse<List<ClocktowerRulingResponse>>> list(@PathVariable Long roomId,
                                                                 @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> rulingService.list(roomId, principal));
    }

    @PostMapping("/{rulingId}/undo")
    public Mono<ApiResponse<ClocktowerRulingApplyResponse>> undo(@PathVariable Long roomId,
                                                                 @PathVariable Long rulingId,
                                                                 @RequestBody ClocktowerRulingUndoRequest request,
                                                                 @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> rulingService.undo(roomId, rulingId, request, principal));
    }
}
```

- [ ] **Step 4: Run backend compile tests**

Run:

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerRulingServiceTests test
```

Expected: PASS.

- [ ] **Step 5: Commit API controller**

```bash
git add be/src/main/java/top/egon/mario/clocktower/ruling/web/ClocktowerRulingController.java \
  be/src/test/java/top/egon/mario/clocktower/ruling/ClocktowerRulingServiceTests.java
git commit -m "feat(clocktower): expose ruling API"
```

### Task 7: Public Life Projection in Room, Grimoire, and Player View

**Files:**
- Modify: `be/src/main/java/top/egon/mario/clocktower/grimoire/dto/response/GrimoireSeatResponse.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/room/dto/response/ClocktowerSeatResponse.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/view/dto/PlayerSeatViewResponse.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/view/dto/PublicSeatResponse.java`
- Modify: `be/src/test/java/top/egon/mario/clocktower/ruling/ClocktowerRulingServiceTests.java`
- Modify: `be/src/test/java/top/egon/mario/clocktower/view/ClocktowerViewServiceTests.java`

- [ ] **Step 1: Add failing projection assertions**

Add this test to `ClocktowerRulingServiceTests`:

```java
@Test
void grimoireShowsRealAndPublicLifeAfterFakeDeath() {
    ClocktowerRoomResponse room = startedRoom();
    Long targetSeatId = room.seats().getFirst().seatId();

    ClocktowerRulingApplyResponse response = rulingService.create(room.roomId(), new ClocktowerRulingCreateRequest(
            ClocktowerRulingType.SET_PUBLIC_LIFE, targetSeatId, null, null, "DEAD", null,
            ClocktowerRulingReason.STORYTELLER_RULING, "假死", "一名玩家死亡", ClocktowerVisibility.PUBLIC, false),
            storytellerPrincipal());

    assertThat(response.grimoire().seats()).filteredOn(seat -> seat.seatId().equals(targetSeatId))
            .allMatch(seat -> seat.alive() && !seat.publicAlive());
}
```

- [ ] **Step 2: Run projection test to verify it fails**

Run:

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerRulingServiceTests#grimoireShowsRealAndPublicLifeAfterFakeDeath test
```

Expected: FAIL because `GrimoireSeatResponse.publicAlive()` does not exist.

- [ ] **Step 3: Update grimoire seat response**

Change `GrimoireSeatResponse` record fields to include public state after `alive`:

```java
boolean alive,
boolean publicAlive,
String lifeStatus,
String publicLifeStatus,
boolean hasDeadVote,
```

Update factory:

```java
return new GrimoireSeatResponse(seat.getId(), seat.getSeatNo(), seat.getUserId(), seat.getDisplayName(),
        entry == null ? seat.getRoleCode() : entry.getRoleCode(),
        entry == null ? seat.getRoleType() : entry.getRoleType(),
        entry == null ? seat.getAlignment() : entry.getAlignment(),
        "ALIVE".equals(seat.getLifeStatus()), "ALIVE".equals(seat.getPublicLifeStatus()),
        seat.getLifeStatus(), seat.getPublicLifeStatus(), seat.isHasDeadVote(), seat.isConnected(),
        entry == null ? null : entry.getNotes());
```

- [ ] **Step 4: Update room and player view responses**

Change `ClocktowerSeatResponse` to include `publicLifeStatus` after `lifeStatus`:

```java
String lifeStatus,
String publicLifeStatus,
boolean connected,
```

Update `from`:

```java
return new ClocktowerSeatResponse(seat.getId(), seat.getSeatNo(), seat.getUserId(), seat.getDisplayName(),
        seat.getRoleCode(), seat.getRoleType(), seat.getLifeStatus(), seat.getPublicLifeStatus(),
        seat.isConnected(), seat.isHasDeadVote());
```

Update `publicView`:

```java
return new ClocktowerSeatResponse(seat.getId(), seat.getSeatNo(), seat.getUserId(), seat.getDisplayName(),
        null, null, seat.getPublicLifeStatus(), seat.getPublicLifeStatus(), seat.isConnected(), seat.isHasDeadVote());
```

Change `PlayerSeatViewResponse` to include `publicLifeStatus`:

```java
String lifeStatus,
String publicLifeStatus,
boolean hasDeadVote
```

Update `from`:

```java
return new PlayerSeatViewResponse(seat.getId(), seat.getSeatNo(), seat.getDisplayName(), seat.getRoleCode(),
        seat.getRoleType(), seat.getAlignment(), seat.getLifeStatus(), seat.getPublicLifeStatus(),
        seat.isHasDeadVote());
```

Update `PublicSeatResponse.from`:

```java
return new PublicSeatResponse(seat.getId(), seat.getSeatNo(), seat.getDisplayName(), null,
        seat.getPublicLifeStatus(), seat.isConnected(), seat.isHasDeadVote());
```

- [ ] **Step 5: Run projection and view tests**

Run:

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false -Dtest='ClocktowerRulingServiceTests,ClocktowerViewServiceTests,ClocktowerRoomServiceTests' test
```

Expected: PASS.

- [ ] **Step 6: Commit projection changes**

```bash
git add be/src/main/java/top/egon/mario/clocktower/grimoire/dto/response/GrimoireSeatResponse.java \
  be/src/main/java/top/egon/mario/clocktower/room/dto/response/ClocktowerSeatResponse.java \
  be/src/main/java/top/egon/mario/clocktower/view/dto/PlayerSeatViewResponse.java \
  be/src/main/java/top/egon/mario/clocktower/view/dto/PublicSeatResponse.java \
  be/src/test/java/top/egon/mario/clocktower/ruling/ClocktowerRulingServiceTests.java \
  be/src/test/java/top/egon/mario/clocktower/view/ClocktowerViewServiceTests.java
git commit -m "feat(clocktower): project public life status"
```

### Task 8: Frontend Types and API Clients

**Files:**
- Modify: `fe/src/modules/clocktower/clocktowerTypes.ts`
- Modify: `fe/src/modules/clocktower/clocktowerService.ts`
- Modify: `fe/src/modules/clocktower/clocktowerService.test.ts`

- [ ] **Step 1: Add failing service tests**

Add tests to `clocktowerService.test.ts`:

```ts
it('creates room rulings through the ruling endpoint', async () => {
    const {requestJson} = await import('../../services/request')
    const request = {
        rulingType: 'MARK_DEAD' as const,
        targetSeatId: 3,
        reason: 'NIGHT_DEATH' as const,
        note: '夜晚死亡',
        publicNote: '一名玩家死亡',
        visibility: 'PUBLIC' as const,
        force: false,
    }

    await createClocktowerRuling(7, request)

    expect(requestJson).toHaveBeenCalledWith('/api/clocktower/rooms/7/rulings', {
        method: 'POST',
        body: request,
    })
})

it('loads and undoes room rulings', async () => {
    const {requestJson} = await import('../../services/request')

    await listClocktowerRulings(7)
    await undoClocktowerRuling(7, 9, {note: '误操作撤销', force: true})

    expect(requestJson).toHaveBeenCalledWith('/api/clocktower/rooms/7/rulings')
    expect(requestJson).toHaveBeenCalledWith('/api/clocktower/rooms/7/rulings/9/undo', {
        method: 'POST',
        body: {note: '误操作撤销', force: true},
    })
})
```

Update import list in the test:

```ts
createClocktowerRuling,
listClocktowerRulings,
undoClocktowerRuling,
```

- [ ] **Step 2: Run service tests to verify failure**

Run:

```bash
cd fe
npm test -- clocktowerService.test.ts
```

Expected: FAIL because ruling service functions do not exist.

- [ ] **Step 3: Add frontend ruling types**

Add to `clocktowerTypes.ts`:

```ts
export type ClocktowerRulingType =
    | 'MARK_DEAD'
    | 'RESTORE_ALIVE'
    | 'SET_PUBLIC_LIFE'
    | 'EXECUTE_PLAYER'
    | 'SKIP_EXECUTION'
    | 'END_GAME'
    | 'ADVANCE_PHASE'
    | 'CLOSE_NOMINATION'
    | 'REOPEN_NOMINATION'
    | 'VOID_NOMINATION'
    | 'UNDO_RULING'

export type ClocktowerRulingReason =
    | 'VOTE_EXECUTION'
    | 'ROLE_ABILITY'
    | 'NIGHT_DEATH'
    | 'STORYTELLER_RULING'
    | 'PLAYER_REQUEST'
    | 'MISTAKE_FIX'
    | 'OTHER'

export type ClocktowerRulingStatus = 'APPLIED' | 'REVOKED'

export type ClocktowerRulingCreateRequest = {
    rulingType: ClocktowerRulingType
    targetSeatId?: number | null
    nominationId?: number | null
    targetPhase?: ClocktowerPhase | null
    publicLifeStatus?: string | null
    winner?: 'GOOD' | 'EVIL' | null
    reason: ClocktowerRulingReason
    note: string
    publicNote?: string | null
    visibility: ClocktowerVisibility
    force: boolean
}

export type ClocktowerRulingUndoRequest = {
    note: string
    force: boolean
}

export type ClocktowerRulingResponse = {
    rulingId: number
    roomId: number
    rulingType: ClocktowerRulingType
    status: ClocktowerRulingStatus
    targetSeatId?: number | null
    nominationId?: number | null
    targetPhase?: ClocktowerPhase | null
    publicLifeStatus?: string | null
    winner?: string | null
    reason: ClocktowerRulingReason
    note: string
    publicNote?: string | null
    visibility: ClocktowerVisibility
    undoOfRulingId?: number | null
}

export type ClocktowerRulingApplyResponse = {
    ruling: ClocktowerRulingResponse
    grimoire: ClocktowerGrimoireResponse
    events: ClocktowerEventResponse[]
}
```

Extend seat types:

```ts
lifeStatus: string
publicLifeStatus: string
```

For `GrimoireSeatResponse`, add:

```ts
publicAlive: boolean
lifeStatus: string
publicLifeStatus: string
```

- [ ] **Step 4: Add service functions**

Update imports in `clocktowerService.ts`:

```ts
ClocktowerRulingApplyResponse,
ClocktowerRulingCreateRequest,
ClocktowerRulingResponse,
ClocktowerRulingUndoRequest,
```

Add functions after `getClocktowerNightChecklist`:

```ts
export function createClocktowerRuling(roomId: number, request: ClocktowerRulingCreateRequest) {
    return requestJson<ClocktowerRulingApplyResponse>(`/api/clocktower/rooms/${roomId}/rulings`, {
        method: 'POST',
        body: request,
    })
}

export function listClocktowerRulings(roomId: number) {
    return requestJson<ClocktowerRulingResponse[]>(`/api/clocktower/rooms/${roomId}/rulings`)
}

export function undoClocktowerRuling(roomId: number, rulingId: number, request: ClocktowerRulingUndoRequest) {
    return requestJson<ClocktowerRulingApplyResponse>(`/api/clocktower/rooms/${roomId}/rulings/${rulingId}/undo`, {
        method: 'POST',
        body: request,
    })
}
```

- [ ] **Step 5: Run frontend service tests and typecheck**

Run:

```bash
cd fe
npm test -- clocktowerService.test.ts
npm run typecheck
```

Expected: PASS.

- [ ] **Step 6: Commit frontend API client**

```bash
git add fe/src/modules/clocktower/clocktowerTypes.ts \
  fe/src/modules/clocktower/clocktowerService.ts \
  fe/src/modules/clocktower/clocktowerService.test.ts
git commit -m "feat(clocktower): add ruling frontend client"
```

### Task 9: Grimoire Ruling UI

**Files:**
- Modify: `fe/src/modules/clocktower/StorytellerGrimoirePage.tsx`
- Modify: `fe/src/modules/clocktower/StorytellerGrimoirePage.test.tsx`

- [ ] **Step 1: Query Ant Design APIs for changed components**

Run:

```bash
cd fe
npx antd info Popconfirm --format json
npx antd info Modal --format json
npx antd info Form --format json
```

Expected: commands return JSON API summaries. Use `Popconfirm` for quick death/revive and `Form` inside the existing action tab for high-risk rulings.

- [ ] **Step 2: Add failing SSR tests for ruling controls**

Add to `StorytellerGrimoirePage.test.tsx`:

```tsx
test('renders grimoire seats with real and public life status plus ruling actions', () => {
    const markup = renderToStaticMarkup(
        <GrimoireSeatList
            grimoire={{
                roomId: 7,
                phase: {phase: 'DAY', dayNo: 1, nightNo: 1},
                seats: [
                    {
                        seatId: 3,
                        seatNo: 1,
                        displayName: 'Alice',
                        roleCode: 'EMPATH',
                        roleType: 'TOWNSFOLK',
                        alignment: 'GOOD',
                        alive: true,
                        publicAlive: false,
                        lifeStatus: 'ALIVE',
                        publicLifeStatus: 'DEAD',
                        hasDeadVote: true,
                        connected: true,
                    },
                ],
                markers: [],
                reminders: [],
                pendingTasks: [],
                ruleTraceEnabled: false,
            }}
            onQuickRuling={async () => {}}
            rulingLoadingKey={null}
        />,
    )

    expect(markup).toContain('真实存活')
    expect(markup).toContain('公开死亡')
    expect(markup).toContain('判死亡')
    expect(markup).toContain('复活')
})

test('renders ruling history with undo state', () => {
    const markup = renderToStaticMarkup(
        <RulingHistory
            onUndo={async () => {}}
            rulings={[
                {
                    rulingId: 5,
                    roomId: 7,
                    rulingType: 'MARK_DEAD',
                    status: 'APPLIED',
                    targetSeatId: 3,
                    reason: 'NIGHT_DEATH',
                    note: '夜晚死亡',
                    publicNote: '一名玩家死亡',
                    visibility: 'PUBLIC',
                },
            ]}
            undoingRulingId={null}
        />,
    )

    expect(markup).toContain('MARK_DEAD')
    expect(markup).toContain('NIGHT_DEATH')
    expect(markup).toContain('撤销')
})
```

Export `GrimoireSeatList` and `RulingHistory` from the page for test access.

- [ ] **Step 3: Run page test to verify failure**

Run:

```bash
cd fe
npm test -- StorytellerGrimoirePage.test.tsx
```

Expected: FAIL because exported components and ruling fields do not exist yet.

- [ ] **Step 4: Wire page state and API calls**

Update service imports:

```ts
createClocktowerRuling,
listClocktowerRulings,
undoClocktowerRuling,
```

Update type imports:

```ts
ClocktowerRulingCreateRequest,
ClocktowerRulingResponse,
ClocktowerRulingType,
```

Add state:

```tsx
const [rulings, setRulings] = useState<ClocktowerRulingResponse[]>([])
const [rulingLoadingKey, setRulingLoadingKey] = useState<string | null>(null)
const [undoingRulingId, setUndoingRulingId] = useState<number | null>(null)
```

Update `load` Promise:

```tsx
const [grimoireResponse, checklistResponse, rulingRows] = await Promise.all([
    getClocktowerGrimoire(numericRoomId),
    getClocktowerNightChecklist(numericRoomId),
    listClocktowerRulings(numericRoomId),
])
setRulings(rulingRows)
```

Add helper:

```tsx
async function submitRuling(request: ClocktowerRulingCreateRequest, loadingKey: string) {
    if (!Number.isFinite(numericRoomId)) {
        return
    }
    setRulingLoadingKey(loadingKey)
    try {
        const response = await createClocktowerRuling(numericRoomId, request)
        const rulingRows = await listClocktowerRulings(numericRoomId)
        setGrimoire(response.grimoire)
        setRulings(rulingRows)
        message.success('裁定已生效')
    } catch (caught) {
        message.error(resolveErrorMessage(caught))
    } finally {
        setRulingLoadingKey(null)
    }
}
```

Add quick ruling helper:

```tsx
async function quickSeatRuling(seatId: number, rulingType: ClocktowerRulingType) {
    await submitRuling({
        rulingType,
        targetSeatId: seatId,
        reason: rulingType === 'MARK_DEAD' ? 'NIGHT_DEATH' : 'ROLE_ABILITY',
        note: rulingType === 'MARK_DEAD' ? '判死亡' : '复活',
        publicNote: rulingType === 'MARK_DEAD' ? '一名玩家死亡' : '一名玩家复活',
        visibility: 'PUBLIC',
        force: false,
    }, `${rulingType}:${seatId}`)
}
```

Add undo helper:

```tsx
async function undoRuling(rulingId: number) {
    if (!Number.isFinite(numericRoomId)) {
        return
    }
    setUndoingRulingId(rulingId)
    try {
        const response = await undoClocktowerRuling(numericRoomId, rulingId, {note: '撤销裁定', force: true})
        const rulingRows = await listClocktowerRulings(numericRoomId)
        setGrimoire(response.grimoire)
        setRulings(rulingRows)
        message.success('裁定已撤销')
    } catch (caught) {
        message.error(resolveErrorMessage(caught))
    } finally {
        setUndoingRulingId(null)
    }
}
```

- [ ] **Step 5: Update grimoire seat list**

Export component and change signature:

```tsx
export function GrimoireSeatList({
    grimoire,
    onQuickRuling,
    rulingLoadingKey,
}: {
    grimoire: ClocktowerGrimoireResponse | null
    onQuickRuling: (seatId: number, rulingType: ClocktowerRulingType) => Promise<void>
    rulingLoadingKey: string | null
}) {
```

Inside each `List.Item`, use `actions`:

```tsx
actions={[
    <Button
        key="dead"
        loading={rulingLoadingKey === `MARK_DEAD:${seat.seatId}`}
        onClick={voidify(() => onQuickRuling(seat.seatId, 'MARK_DEAD'))}
        size="small"
    >
        判死亡
    </Button>,
    <Button
        key="alive"
        loading={rulingLoadingKey === `RESTORE_ALIVE:${seat.seatId}`}
        onClick={voidify(() => onQuickRuling(seat.seatId, 'RESTORE_ALIVE'))}
        size="small"
    >
        复活
    </Button>,
]}
```

Replace life tags:

```tsx
<Tag color={seat.alive ? 'success' : 'error'}>{seat.alive ? '真实存活' : '真实死亡'}</Tag>
<Tag color={seat.publicAlive ? 'success' : 'error'}>{seat.publicAlive ? '公开存活' : '公开死亡'}</Tag>
```

- [ ] **Step 6: Add ruling history component**

Add below `TaskList`:

```tsx
export function RulingHistory({
    rulings,
    onUndo,
    undoingRulingId,
}: {
    rulings: ClocktowerRulingResponse[]
    onUndo: (rulingId: number) => Promise<void>
    undoingRulingId: number | null
}) {
    if (rulings.length === 0) {
        return <Empty description="暂无裁定"/>
    }
    return (
        <List
            dataSource={rulings}
            renderItem={(ruling) => (
                <List.Item
                    actions={[
                        <Button
                            disabled={ruling.status === 'REVOKED'}
                            key="undo"
                            loading={undoingRulingId === ruling.rulingId}
                            onClick={voidify(() => onUndo(ruling.rulingId))}
                            size="small"
                            type="link"
                        >
                            撤销
                        </Button>,
                    ]}
                >
                    <Space wrap>
                        <Tag>{ruling.rulingType}</Tag>
                        <Tag color={ruling.status === 'APPLIED' ? 'processing' : 'default'}>{ruling.status}</Tag>
                        <Tag>{ruling.reason}</Tag>
                        {ruling.targetSeatId && <Typography.Text>座位 {ruling.targetSeatId}</Typography.Text>}
                        <Typography.Text type="secondary">{ruling.publicNote ?? ruling.note}</Typography.Text>
                    </Space>
                </List.Item>
            )}
        />
    )
}
```

Add a tab:

```tsx
{
    key: 'rulings',
    label: '裁定历史',
    children: <RulingHistory rulings={rulings} onUndo={undoRuling} undoingRulingId={undoingRulingId}/>,
}
```

- [ ] **Step 7: Run frontend tests**

Run:

```bash
cd fe
npm test -- StorytellerGrimoirePage.test.tsx
npm run typecheck
npx eslint src/modules/clocktower/StorytellerGrimoirePage.tsx
antd lint src/modules/clocktower/StorytellerGrimoirePage.tsx --format json
```

Expected: PASS and antd lint summary has `"total": 0`.

- [ ] **Step 8: Commit grimoire ruling UI**

```bash
git add fe/src/modules/clocktower/StorytellerGrimoirePage.tsx \
  fe/src/modules/clocktower/StorytellerGrimoirePage.test.tsx
git commit -m "feat(clocktower): add grimoire ruling controls"
```

### Task 10: Final Integration Validation

**Files:**
- No planned source edits.

- [ ] **Step 1: Run backend Clocktower tests**

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false -Dtest='*Clocktower*Tests' test
```

Expected: PASS with all Clocktower tests green.

- [ ] **Step 2: Run frontend targeted tests**

```bash
cd fe
npm test -- clocktowerService.test.ts StorytellerGrimoirePage.test.tsx GameRoomPage.test.tsx
```

Expected: PASS.

- [ ] **Step 3: Run frontend typecheck**

```bash
cd fe
npm run typecheck
```

Expected: PASS.

- [ ] **Step 4: Run targeted lint**

```bash
cd fe
npx eslint src/modules/clocktower/StorytellerGrimoirePage.tsx src/modules/clocktower/clocktowerService.ts src/modules/clocktower/clocktowerTypes.ts
antd lint src/modules/clocktower/StorytellerGrimoirePage.tsx --format json
```

Expected: ESLint exits 0. Ant Design lint reports zero issues.

- [ ] **Step 5: Inspect git diff**

```bash
git diff --stat
git diff --check
git status --short
```

Expected: `git diff --check` exits 0. Status only contains files from this plan if the final commit has not yet been made.

- [ ] **Step 6: Final commit if any validation-only fixes were made**

If Step 5 shows source changes from validation fixes, commit them:

```bash
git add be/src/main/java/top/egon/mario/clocktower be/src/test/java/top/egon/mario/clocktower \
  fe/src/modules/clocktower
git commit -m "test(clocktower): validate ruling system integration"
```

Expected: commit succeeds only when there are staged changes. If no files changed after Task 9, skip this commit.

## Self-Review Checklist

- Spec coverage:
  - Dedicated ruling module: Tasks 1, 2, 3, 6.
  - Persisted ruling history: Tasks 1, 2, 6, 9.
  - Real/public life split: Tasks 1, 3, 7, 8, 9.
  - Death/revive/public life: Tasks 3, 9.
  - Execution and nomination flow: Task 4.
  - End game with required reason and optional winner: Task 3.
  - Audited undo: Task 5.
  - Frontend grimoire controls: Task 9.
  - Player view uses public status: Task 7.
  - Exactly one new Flyway migration: Task 1.
- Placeholder scan:
  - The plan contains no unfinished placeholder markers.
  - Every code-changing task includes concrete snippets, commands, and expected results.
- Type consistency:
  - Backend enum names match frontend union values.
  - Request/response names match service and controller signatures.
  - `publicLifeStatus` is camelCase in Java/TypeScript and `public_life_status` in SQL.
