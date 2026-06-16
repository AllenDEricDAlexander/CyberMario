# Clocktower Phase 1 Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:
> executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first playable Clocktower slice: static script data, board validation, rooms, seats, role
assignment, grimoire, night checklist, player actions, storyteller actions, event stream, replay, RBAC, and frontend
shells.

**Architecture:** Add an independent `top.egon.mario.clocktower` backend domain and `fe/src/modules/clocktower` frontend
module. Use PostgreSQL/Flyway/JPA for static and runtime state, a thin Drools integration for Phase 1 rule checks,
event-first state changes, and a single Java visibility filter for player/storyteller views. This plan intentionally
stops before full three-script role automation and advanced Agent autoplay; those need separate plans after the core
game loop exists.

**Tech Stack:** Java 21, Spring Boot 3.5, WebFlux, Spring Security, Spring Data JPA, Flyway, PostgreSQL, Drools, Reactor
SSE, JUnit 5, AssertJ, Mockito, React 19, TypeScript, Vite, Ant Design 6, Vitest.

---

## Scope Check

The three Clocktower specs describe a full product with several independent subsystems: core game loop, Drools role
automation, Agent autoplay, rule RAG, replay analytics, and rule data maintenance. This plan is the first implementation
plan and covers the smallest useful product slice: a human storyteller can create a room, generate/validate a board,
start a game, view the grimoire, run night checklist, accept player actions, process nominations/votes/execution, stream
events, and open a basic replay. Separate plans should follow for full role ability automation, Agent autoplay, rule
query/RAG, admin rule maintenance, and polished UI.

Primary specs:

- `docs/superpowers/specs/2026-06-17-clocktower-agent-platform-design.md`
- `docs/superpowers/specs/2026-06-17-clocktower-drools-rule-engine-detail-design.md`
- `docs/superpowers/specs/2026-06-17-clocktower-api-and-module-detail-design.md`

## File Structure

Backend files to create:

- `be/src/main/resources/db/migration/V18__create_clocktower_core_schema.sql`: one migration for Phase 1 core tables and
  seed data.
- `be/src/main/java/top/egon/mario/clocktower/common/enums/*`: enum contracts shared across modules.
- `be/src/main/java/top/egon/mario/clocktower/script/*`: static script, role, night order, term, jinx query APIs.
- `be/src/main/java/top/egon/mario/clocktower/board/*`: board generate/validate APIs.
- `be/src/main/java/top/egon/mario/clocktower/room/*`: room and seat lifecycle.
- `be/src/main/java/top/egon/mario/clocktower/event/*`: event store, visibility filter, SSE stream, state projector.
- `be/src/main/java/top/egon/mario/clocktower/grimoire/*`: storyteller grimoire, markers, night checklist, storyteller
  actions.
- `be/src/main/java/top/egon/mario/clocktower/action/*`: player action command endpoint and orchestration.
- `be/src/main/java/top/egon/mario/clocktower/view/*`: player/storyteller view aggregation.
- `be/src/main/java/top/egon/mario/clocktower/replay/*`: basic replay.
- `be/src/main/java/top/egon/mario/clocktower/engine/*`: Phase 1 Drools wrapper, facts, decisions, collector, and simple
  DRL resources.
- `be/src/main/java/top/egon/mario/clocktower/resource/ClocktowerRbacResourceProvider.java`: RBAC resources and preset
  roles.

Backend files to modify:

- `be/pom.xml`: add Drools dependencies after the core schema and service tests prove the need.
- `be/src/main/java/top/egon/mario/MarioApplication.java`: no planned change unless package scanning fails; prefer no
  change.

Backend tests to create:

- `be/src/test/java/top/egon/mario/clocktower/ClocktowerSchemaMigrationTests.java`
- `be/src/test/java/top/egon/mario/clocktower/script/ClocktowerScriptServiceTests.java`
- `be/src/test/java/top/egon/mario/clocktower/board/ClocktowerBoardServiceTests.java`
- `be/src/test/java/top/egon/mario/clocktower/room/ClocktowerRoomServiceTests.java`
- `be/src/test/java/top/egon/mario/clocktower/event/ClocktowerVisibilityFilterTests.java`
- `be/src/test/java/top/egon/mario/clocktower/grimoire/ClocktowerGrimoireServiceTests.java`
- `be/src/test/java/top/egon/mario/clocktower/action/ClocktowerActionServiceTests.java`
- `be/src/test/java/top/egon/mario/clocktower/event/ClocktowerEventStreamServiceTests.java`
- `be/src/test/java/top/egon/mario/clocktower/replay/ClocktowerReplayServiceTests.java`
- `be/src/test/java/top/egon/mario/clocktower/resource/ClocktowerRbacResourceProviderTests.java`

Frontend files to create:

- `fe/src/modules/clocktower/clocktowerTypes.ts`
- `fe/src/modules/clocktower/clocktowerService.ts`
- `fe/src/modules/clocktower/clocktowerPermissionCodes.ts`
- `fe/src/modules/clocktower/BoardBuilderPage.tsx`
- `fe/src/modules/clocktower/RoomListPage.tsx`
- `fe/src/modules/clocktower/RoomLobbyPage.tsx`
- `fe/src/modules/clocktower/GameRoomPage.tsx`
- `fe/src/modules/clocktower/StorytellerGrimoirePage.tsx`
- `fe/src/modules/clocktower/ReplayPage.tsx`
- `fe/src/modules/clocktower/components/RoleTypeTag.tsx`
- `fe/src/modules/clocktower/components/BoardCandidateTable.tsx`
- `fe/src/modules/clocktower/components/EventTimeline.tsx`
- `fe/src/modules/clocktower/components/NightChecklist.tsx`
- `fe/src/modules/clocktower/components/VotePanel.tsx`

Frontend files to modify:

- `fe/src/app/routes.tsx`: add Clocktower lazy routes.
- `fe/src/layouts/AdminLayout/menu.tsx`: only if provider-synced menu constants require a local fallback.

Frontend tests to create:

- `fe/src/modules/clocktower/clocktowerService.test.ts`
- `fe/src/modules/clocktower/clocktowerPermissionCodes.test.ts`
- `fe/src/modules/clocktower/BoardBuilderPage.test.tsx`
- `fe/src/modules/clocktower/GameRoomPage.test.tsx`
- `fe/src/modules/clocktower/StorytellerGrimoirePage.test.tsx`

## Shared Phase 1 Contracts

Use these exact enum names in Java and TypeScript.

```java
public enum ClocktowerScriptCode {
    TROUBLE_BREWING,
    BAD_MOON_RISING,
    SECTS_AND_VIOLETS
}

public enum ClocktowerRoleType {
    TOWNSFOLK,
    OUTSIDER,
    MINION,
    DEMON,
    TRAVELER,
    FABLED
}

public enum ClocktowerRoomStatus {
    LOBBY,
    SETUP,
    RUNNING,
    ENDED,
    ARCHIVED
}

public enum ClocktowerPhase {
    LOBBY,
    SETUP,
    FIRST_NIGHT,
    DAY,
    NOMINATION,
    EXECUTION,
    NIGHT,
    ENDED
}

public enum ClocktowerVisibility {
    PUBLIC,
    PRIVATE,
    STORYTELLER,
    AUDIT
}
```

Phase 1 supported event types:

```java
public enum ClocktowerEventType {
    ROOM_CREATED,
    PLAYER_JOINED,
    PLAYER_LEFT,
    SEAT_UPDATED,
    ROLE_ASSIGNED,
    PHASE_CHANGED,
    PUBLIC_MESSAGE_SENT,
    PRIVATE_MESSAGE_SENT,
    NIGHT_STEP_UPDATED,
    NIGHT_ACTION_SUBMITTED,
    PLAYER_NOMINATED,
    VOTE_CAST,
    DEAD_VOTE_SPENT,
    PLAYER_EXECUTED,
    PLAYER_DIED,
    MARKER_ADDED,
    MARKER_REMOVED,
    STORYTELLER_RULING,
    GAME_ENDED,
    ACTION_REJECTED
}
```

Backend validation commands:

```bash
cd /Users/mario/SelfProject/CyberMario/be
mvn -Dmaven.build.cache.enabled=false -Djava.version=21 test
```

Frontend validation commands:

```bash
cd /Users/mario/SelfProject/CyberMario/fe
bun run typecheck
bun run test
```

---

### Task 1: Core Schema Migration And Seed Data Contract

**Files:**

- Create: `be/src/test/java/top/egon/mario/clocktower/ClocktowerSchemaMigrationTests.java`
- Create: `be/src/main/resources/db/migration/V18__create_clocktower_core_schema.sql`

- [ ] **Step 1: Write the failing migration test**

Create `be/src/test/java/top/egon/mario/clocktower/ClocktowerSchemaMigrationTests.java`:

```java
package top.egon.mario.clocktower;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ClocktowerSchemaMigrationTests {

    @Test
    void migrationCreatesCoreClocktowerTablesAndSeedsThreeScripts() throws IOException {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V18__create_clocktower_core_schema.sql"));

        assertThat(sql).contains("CREATE TABLE clocktower_script");
        assertThat(sql).contains("CREATE TABLE clocktower_role");
        assertThat(sql).contains("CREATE TABLE clocktower_night_order");
        assertThat(sql).contains("CREATE TABLE clocktower_jinx_rule");
        assertThat(sql).contains("CREATE TABLE clocktower_room");
        assertThat(sql).contains("CREATE TABLE clocktower_seat");
        assertThat(sql).contains("CREATE TABLE clocktower_event");
        assertThat(sql).contains("CREATE TABLE clocktower_grimoire_entry");
        assertThat(sql).contains("CREATE TABLE clocktower_status_marker");
        assertThat(sql).contains("CREATE TABLE clocktower_nomination");
        assertThat(sql).contains("CREATE TABLE clocktower_vote");
        assertThat(sql).contains("CREATE TABLE clocktower_board_config");
        assertThat(sql).contains("CREATE TABLE clocktower_board_role");
        assertThat(sql).contains("CREATE TABLE clocktower_storyteller_task");
        assertThat(sql).contains("TROUBLE_BREWING");
        assertThat(sql).contains("BAD_MOON_RISING");
        assertThat(sql).contains("SECTS_AND_VIOLETS");
        assertThat(sql).contains("idx_clocktower_event_room_seq");
        assertThat(sql).contains("uk_clocktower_room_code");
    }
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/be
mvn -Dmaven.build.cache.enabled=false -Djava.version=21 -Dtest=ClocktowerSchemaMigrationTests test
```

Expected: FAIL because `V18__create_clocktower_core_schema.sql` does not exist.

- [ ] **Step 3: Add the migration with Phase 1 tables**

Create `be/src/main/resources/db/migration/V18__create_clocktower_core_schema.sql` with all tables named in the test.
Include these constraints and indexes:

```sql
CREATE TABLE clocktower_script (
    id BIGSERIAL PRIMARY KEY,
    script_code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    edition VARCHAR(128) NOT NULL,
    min_players INTEGER NOT NULL,
    max_players INTEGER NOT NULL,
    role_count INTEGER NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    source_url VARCHAR(512),
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_clocktower_script_code UNIQUE (script_code)
);

CREATE TABLE clocktower_role (
    id BIGSERIAL PRIMARY KEY,
    script_code VARCHAR(64) NOT NULL,
    role_code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    role_type VARCHAR(32) NOT NULL,
    alignment VARCHAR(32) NOT NULL,
    ability_text TEXT NOT NULL,
    first_night BOOLEAN NOT NULL DEFAULT FALSE,
    other_night BOOLEAN NOT NULL DEFAULT FALSE,
    setup_modifier BOOLEAN NOT NULL DEFAULT FALSE,
    complexity INTEGER NOT NULL DEFAULT 1,
    source_url VARCHAR(512),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_clocktower_role_code UNIQUE (role_code)
);

CREATE TABLE clocktower_night_order (
    id BIGSERIAL PRIMARY KEY,
    script_code VARCHAR(64) NOT NULL,
    role_code VARCHAR(64) NOT NULL,
    night_type VARCHAR(32) NOT NULL,
    order_no INTEGER NOT NULL,
    wake_condition TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE clocktower_jinx_rule (
    id BIGSERIAL PRIMARY KEY,
    role_a_code VARCHAR(64) NOT NULL,
    role_b_code VARCHAR(64) NOT NULL,
    scope VARCHAR(32) NOT NULL,
    severity VARCHAR(32) NOT NULL,
    effect_type VARCHAR(64) NOT NULL,
    rule_text TEXT NOT NULL,
    source_url VARCHAR(512),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);
```

Create the runtime tables with these columns. Each runtime table must include `created_at`, `updated_at`, `created_by`,
`updated_by`, `version`, and `deleted` with the same definitions shown on `clocktower_room`.

```sql
CREATE TABLE clocktower_room (
    id BIGSERIAL PRIMARY KEY,
    room_code VARCHAR(32) NOT NULL,
    name VARCHAR(128) NOT NULL,
    script_code VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    phase VARCHAR(32) NOT NULL,
    player_count INTEGER NOT NULL,
    storyteller_user_id BIGINT,
    storyteller_mode VARCHAR(32) NOT NULL,
    allow_spectators BOOLEAN NOT NULL DEFAULT FALSE,
    allow_private_chat BOOLEAN NOT NULL DEFAULT TRUE,
    current_day_no INTEGER NOT NULL DEFAULT 0,
    current_night_no INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_clocktower_room_code UNIQUE (room_code)
);

CREATE TABLE clocktower_seat (
    id BIGSERIAL PRIMARY KEY,
    room_id BIGINT NOT NULL,
    seat_no INTEGER NOT NULL,
    user_id BIGINT,
    display_name VARCHAR(128) NOT NULL,
    role_code VARCHAR(64),
    role_type VARCHAR(32),
    alignment VARCHAR(32),
    life_status VARCHAR(32) NOT NULL DEFAULT 'ALIVE',
    connected BOOLEAN NOT NULL DEFAULT FALSE,
    has_dead_vote BOOLEAN NOT NULL DEFAULT TRUE,
    is_traveler BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_clocktower_seat_room_no UNIQUE (room_id, seat_no)
);

CREATE TABLE clocktower_event (
    id BIGSERIAL PRIMARY KEY,
    room_id BIGINT NOT NULL,
    event_seq BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    phase VARCHAR(32) NOT NULL,
    day_no INTEGER NOT NULL DEFAULT 0,
    night_no INTEGER NOT NULL DEFAULT 0,
    actor_user_id BIGINT,
    actor_seat_id BIGINT,
    target_seat_id BIGINT,
    visibility VARCHAR(32) NOT NULL,
    visible_seat_ids_json JSONB NOT NULL DEFAULT '[]',
    payload_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_clocktower_event_room_seq UNIQUE (room_id, event_seq)
);

CREATE TABLE clocktower_grimoire_entry (
    id BIGSERIAL PRIMARY KEY,
    room_id BIGINT NOT NULL,
    seat_id BIGINT NOT NULL,
    role_code VARCHAR(64) NOT NULL,
    role_type VARCHAR(32) NOT NULL,
    alignment VARCHAR(32) NOT NULL,
    token_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    reminder_tokens_json JSONB NOT NULL DEFAULT '[]',
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_clocktower_grimoire_room_seat UNIQUE (room_id, seat_id)
);

CREATE TABLE clocktower_status_marker (
    id BIGSERIAL PRIMARY KEY,
    room_id BIGINT NOT NULL,
    seat_id BIGINT,
    marker_code VARCHAR(64) NOT NULL,
    marker_name VARCHAR(128) NOT NULL,
    marker_source VARCHAR(64),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    expires_phase VARCHAR(32),
    payload_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE clocktower_nomination (
    id BIGSERIAL PRIMARY KEY,
    room_id BIGINT NOT NULL,
    day_no INTEGER NOT NULL,
    nominator_seat_id BIGINT NOT NULL,
    nominee_seat_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    vote_count INTEGER NOT NULL DEFAULT 0,
    executed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE clocktower_vote (
    id BIGSERIAL PRIMARY KEY,
    room_id BIGINT NOT NULL,
    nomination_id BIGINT NOT NULL,
    voter_seat_id BIGINT NOT NULL,
    vote_value BOOLEAN NOT NULL,
    used_dead_vote BOOLEAN NOT NULL DEFAULT FALSE,
    event_id BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_clocktower_vote_nomination_voter UNIQUE (nomination_id, voter_seat_id)
);

CREATE TABLE clocktower_board_config (
    id BIGSERIAL PRIMARY KEY,
    board_code VARCHAR(64) NOT NULL,
    script_code VARCHAR(64) NOT NULL,
    player_count INTEGER NOT NULL,
    difficulty INTEGER NOT NULL,
    chaos INTEGER NOT NULL,
    evil_pressure INTEGER NOT NULL,
    newbie_friendly BOOLEAN NOT NULL DEFAULT FALSE,
    seed VARCHAR(128),
    validation_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_clocktower_board_code UNIQUE (board_code)
);

CREATE TABLE clocktower_board_role (
    id BIGSERIAL PRIMARY KEY,
    board_config_id BIGINT NOT NULL,
    role_code VARCHAR(64) NOT NULL,
    role_type VARCHAR(32) NOT NULL,
    seat_no INTEGER,
    locked BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE clocktower_storyteller_task (
    id BIGSERIAL PRIMARY KEY,
    room_id BIGINT NOT NULL,
    task_type VARCHAR(64) NOT NULL,
    phase VARCHAR(32) NOT NULL,
    day_no INTEGER NOT NULL DEFAULT 0,
    night_no INTEGER NOT NULL DEFAULT 0,
    role_code VARCHAR(64),
    seat_id BIGINT,
    status VARCHAR(32) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    note TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_clocktower_event_room_seq ON clocktower_event (room_id, event_seq);
CREATE INDEX idx_clocktower_seat_room ON clocktower_seat (room_id);
CREATE INDEX idx_clocktower_board_role_config ON clocktower_board_role (board_config_id);
```

Seed the three scripts exactly:

```sql
INSERT INTO clocktower_script (script_code, name, edition, min_players, max_players, role_count, source_url, sort_order)
VALUES
('TROUBLE_BREWING', '暗流涌动', 'BASE_3', 5, 15, 22, 'https://clocktower-wiki.gstonegames.com/index.php?title=%E6%9A%97%E6%B5%81%E6%B6%8C%E5%8A%A8', 10),
('BAD_MOON_RISING', '黯月初升', 'BASE_3', 7, 15, 22, 'https://clocktower-wiki.gstonegames.com/index.php?title=%E9%BB%AF%E6%9C%88%E5%88%9D%E5%8D%87', 20),
('SECTS_AND_VIOLETS', '梦殒春宵', 'BASE_3', 7, 15, 22, 'https://clocktower-wiki.gstonegames.com/index.php?title=%E6%A2%A6%E6%AE%92%E6%98%A5%E5%AE%B5', 30);
```

- [ ] **Step 4: Run the focused test and verify it passes**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/be
mvn -Dmaven.build.cache.enabled=false -Djava.version=21 -Dtest=ClocktowerSchemaMigrationTests test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add be/src/test/java/top/egon/mario/clocktower/ClocktowerSchemaMigrationTests.java be/src/main/resources/db/migration/V18__create_clocktower_core_schema.sql
git commit -m "feat(clocktower): add core schema migration"
```

---

### Task 2: Java Enums, Entities, And Repositories

**Files:**

- Create: `be/src/main/java/top/egon/mario/clocktower/common/enums/*.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/script/po/*.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/room/po/*.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/event/po/*.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/grimoire/po/*.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/board/po/*.java`
- Create: repository interfaces in each module
- Test: `be/src/test/java/top/egon/mario/clocktower/ClocktowerJpaMappingTests.java`

- [ ] **Step 1: Write the failing JPA mapping test**

Create `be/src/test/java/top/egon/mario/clocktower/ClocktowerJpaMappingTests.java`:

```java
package top.egon.mario.clocktower;

import org.junit.jupiter.api.Test;
import top.egon.mario.clocktower.common.enums.ClocktowerPhase;
import top.egon.mario.clocktower.common.enums.ClocktowerRoomStatus;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.room.po.ClocktowerRoomPo;
import top.egon.mario.clocktower.script.po.ClocktowerScriptPo;

import static org.assertj.core.api.Assertions.assertThat;

class ClocktowerJpaMappingTests {

    @Test
    void clocktowerEnumsExposePhaseOneContracts() {
        assertThat(ClocktowerScriptCode.valueOf("TROUBLE_BREWING")).isEqualTo(ClocktowerScriptCode.TROUBLE_BREWING);
        assertThat(ClocktowerRoomStatus.valueOf("LOBBY")).isEqualTo(ClocktowerRoomStatus.LOBBY);
        assertThat(ClocktowerPhase.valueOf("FIRST_NIGHT")).isEqualTo(ClocktowerPhase.FIRST_NIGHT);
    }

    @Test
    void roomAndScriptEntitiesUseProjectAuditBaseClass() {
        ClocktowerScriptPo script = new ClocktowerScriptPo();
        script.setScriptCode(ClocktowerScriptCode.TROUBLE_BREWING);
        script.setName("暗流涌动");

        ClocktowerRoomPo room = new ClocktowerRoomPo();
        room.setRoomCode("ABC123");
        room.setStatus(ClocktowerRoomStatus.LOBBY);
        room.setPhase(ClocktowerPhase.LOBBY);

        assertThat(script.getScriptCode()).isEqualTo(ClocktowerScriptCode.TROUBLE_BREWING);
        assertThat(room.getStatus()).isEqualTo(ClocktowerRoomStatus.LOBBY);
        assertThat(room.isDeleted()).isFalse();
    }
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

```bash
cd /Users/mario/SelfProject/CyberMario/be
mvn -Dmaven.build.cache.enabled=false -Djava.version=21 -Dtest=ClocktowerJpaMappingTests test
```

Expected: FAIL because the package and classes do not exist.

- [ ] **Step 3: Add shared enums**

Create the enum files under `be/src/main/java/top/egon/mario/clocktower/common/enums/` with the names and values from
the Shared Phase 1 Contracts section. Use this exact style:

```java
package top.egon.mario.clocktower.common.enums;

public enum ClocktowerRoomStatus {
    LOBBY,
    SETUP,
    RUNNING,
    ENDED,
    ARCHIVED
}
```

- [ ] **Step 4: Add the script and room entities**

Create `ClocktowerScriptPo` and `ClocktowerRoomPo` extending `BaseAuditablePo`:

```java
package top.egon.mario.clocktower.script.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.common.entity.BaseAuditablePo;

@Getter
@Setter
@Entity
@Table(name = "clocktower_script")
public class ClocktowerScriptPo extends BaseAuditablePo {

    @Enumerated(EnumType.STRING)
    @Column(name = "script_code", nullable = false, unique = true, length = 64)
    private ClocktowerScriptCode scriptCode;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "edition", nullable = false, length = 128)
    private String edition;

    @Column(name = "min_players", nullable = false)
    private int minPlayers;

    @Column(name = "max_players", nullable = false)
    private int maxPlayers;

    @Column(name = "role_count", nullable = false)
    private int roleCount;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "source_url", length = 512)
    private String sourceUrl;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
```

Create one PO per table, each extending `BaseAuditablePo`. Use these Java field names:

- `ClocktowerRolePo`: `scriptCode`, `roleCode`, `roleType`, `name`, `abilityText`, `firstNightOrder`, `otherNightOrder`,
  `firstNightReminder`, `otherNightReminder`, `enabled`, `sourceUrl`, `sortOrder`.
- `ClocktowerNightOrderPo`: `scriptCode`, `roleCode`, `nightType`, `sortOrder`, `reminderText`.
- `ClocktowerJinxRulePo`: `roleACode`, `roleBCode`, `scope`, `severity`, `effectType`, `ruleText`, `sourceUrl`.
- `ClocktowerRoomPo`: `roomCode`, `name`, `scriptCode`, `status`, `phase`, `playerCount`, `storytellerUserId`,
  `storytellerMode`, `allowSpectators`, `allowPrivateChat`, `currentDayNo`, `currentNightNo`.
- `ClocktowerSeatPo`: `roomId`, `seatNo`, `userId`, `displayName`, `roleCode`, `roleType`, `alignment`, `lifeStatus`,
  `connected`, `hasDeadVote`, `traveler`.
- `ClocktowerEventPo`: `roomId`, `eventSeq`, `eventType`, `phase`, `dayNo`, `nightNo`, `actorUserId`, `actorSeatId`,
  `targetSeatId`, `visibility`, `visibleSeatIdsJson`, `payloadJson`.
- `ClocktowerGrimoireEntryPo`: `roomId`, `seatId`, `roleCode`, `roleType`, `alignment`, `tokenStatus`,
  `reminderTokensJson`, `notes`.
- `ClocktowerStatusMarkerPo`: `roomId`, `seatId`, `markerCode`, `markerName`, `markerSource`, `active`, `expiresPhase`,
  `payloadJson`.
- `ClocktowerNominationPo`: `roomId`, `dayNo`, `nominatorSeatId`, `nomineeSeatId`, `status`, `voteCount`, `executed`.
- `ClocktowerVotePo`: `roomId`, `nominationId`, `voterSeatId`, `voteValue`, `usedDeadVote`, `eventId`.
- `ClocktowerBoardConfigPo`: `boardCode`, `scriptCode`, `playerCount`, `difficulty`, `chaos`, `evilPressure`,
  `newbieFriendly`, `seed`, `validationJson`.
- `ClocktowerBoardRolePo`: `boardConfigId`, `roleCode`, `roleType`, `seatNo`, `locked`, `sortOrder`.
- `ClocktowerStorytellerTaskPo`: `roomId`, `taskType`, `phase`, `dayNo`, `nightNo`, `roleCode`, `seatId`, `status`,
  `sortOrder`, `note`.

- [ ] **Step 5: Add repositories**

Create repositories with explicit query names used by later services:

```java
package top.egon.mario.clocktower.script.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.script.po.ClocktowerScriptPo;

import java.util.List;
import java.util.Optional;

public interface ClocktowerScriptRepository extends JpaRepository<ClocktowerScriptPo, Long> {

    List<ClocktowerScriptPo> findByEnabledTrueAndDeletedFalseOrderBySortOrderAsc();

    Optional<ClocktowerScriptPo> findByScriptCodeAndDeletedFalse(ClocktowerScriptCode scriptCode);
}
```

Add equivalent repositories for roles, room, seat, event, grimoire, marker, nomination, vote, board config, and
storyteller task.

- [ ] **Step 6: Run the focused test**

```bash
cd /Users/mario/SelfProject/CyberMario/be
mvn -Dmaven.build.cache.enabled=false -Djava.version=21 -Dtest=ClocktowerJpaMappingTests test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add be/src/main/java/top/egon/mario/clocktower be/src/test/java/top/egon/mario/clocktower/ClocktowerJpaMappingTests.java
git commit -m "feat(clocktower): add core domain mappings"
```

---

### Task 3: Script, Role, Night Order, Term, And Jinx Read APIs

**Files:**

- Create: `be/src/main/java/top/egon/mario/clocktower/script/dto/response/*.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/script/service/ClocktowerScriptService.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/script/service/impl/ClocktowerScriptServiceImpl.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/script/web/ClocktowerScriptController.java`
- Test: `be/src/test/java/top/egon/mario/clocktower/script/ClocktowerScriptServiceTests.java`

- [ ] **Step 1: Write the failing service test**

Create `ClocktowerScriptServiceTests.java`:

```java
package top.egon.mario.clocktower.script;

import org.junit.jupiter.api.Test;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.script.dto.response.ClocktowerScriptResponse;
import top.egon.mario.clocktower.script.po.ClocktowerScriptPo;
import top.egon.mario.clocktower.script.repository.ClocktowerScriptRepository;
import top.egon.mario.clocktower.script.service.impl.ClocktowerScriptServiceImpl;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class ClocktowerScriptServiceTests {

    @Test
    void listScriptsReturnsEnabledScriptsInSortOrder() {
        ClocktowerScriptRepository repository = mock(ClocktowerScriptRepository.class);
        ClocktowerScriptPo trouble = script(ClocktowerScriptCode.TROUBLE_BREWING, "暗流涌动", 5, 15, 10);
        ClocktowerScriptPo bmr = script(ClocktowerScriptCode.BAD_MOON_RISING, "黯月初升", 7, 15, 20);
        given(repository.findByEnabledTrueAndDeletedFalseOrderBySortOrderAsc()).willReturn(List.of(trouble, bmr));

        ClocktowerScriptServiceImpl service = new ClocktowerScriptServiceImpl(repository, null, null, null, null);

        List<ClocktowerScriptResponse> scripts = service.listScripts();

        assertThat(scripts).extracting(ClocktowerScriptResponse::scriptCode)
                .containsExactly(ClocktowerScriptCode.TROUBLE_BREWING, ClocktowerScriptCode.BAD_MOON_RISING);
        assertThat(scripts.getFirst().minPlayers()).isEqualTo(5);
    }

    private static ClocktowerScriptPo script(ClocktowerScriptCode code, String name, int min, int max, int order) {
        ClocktowerScriptPo po = new ClocktowerScriptPo();
        po.setScriptCode(code);
        po.setName(name);
        po.setEdition("BASE_3");
        po.setMinPlayers(min);
        po.setMaxPlayers(max);
        po.setRoleCount(22);
        po.setEnabled(true);
        po.setSortOrder(order);
        return po;
    }
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

```bash
cd /Users/mario/SelfProject/CyberMario/be
mvn -Dmaven.build.cache.enabled=false -Djava.version=21 -Dtest=ClocktowerScriptServiceTests test
```

Expected: FAIL because response and service classes do not exist.

- [ ] **Step 3: Add response records**

Create `ClocktowerScriptResponse.java`:

```java
package top.egon.mario.clocktower.script.dto.response;

import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.script.po.ClocktowerScriptPo;

public record ClocktowerScriptResponse(
        ClocktowerScriptCode scriptCode,
        String name,
        String edition,
        int minPlayers,
        int maxPlayers,
        int roleCount,
        boolean enabled,
        String sourceUrl
) {
    public static ClocktowerScriptResponse from(ClocktowerScriptPo script) {
        return new ClocktowerScriptResponse(script.getScriptCode(), script.getName(), script.getEdition(),
                script.getMinPlayers(), script.getMaxPlayers(), script.getRoleCount(), script.isEnabled(),
                script.getSourceUrl());
    }
}
```

Create these response records:

```java
public record ClocktowerRoleResponse(
        ClocktowerScriptCode scriptCode,
        String roleCode,
        ClocktowerRoleType roleType,
        String name,
        String abilityText,
        Integer firstNightOrder,
        Integer otherNightOrder,
        String firstNightReminder,
        String otherNightReminder,
        boolean enabled,
        String sourceUrl
) {
}

public record ClocktowerNightOrderResponse(
        ClocktowerScriptCode scriptCode,
        String roleCode,
        String roleName,
        ClocktowerRoleType roleType,
        String nightType,
        int sortOrder,
        String reminderText
) {
}

public record ClocktowerTermResponse(String term, String category, String description, String sourceUrl) {
}

public record ClocktowerJinxRuleResponse(
        String roleACode,
        String roleBCode,
        String scope,
        String severity,
        String effectType,
        String ruleText,
        String sourceUrl
) {
}
```

- [ ] **Step 4: Add service and controller**

Create `ClocktowerScriptService.java`:

```java
package top.egon.mario.clocktower.script.service;

import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.script.dto.response.ClocktowerJinxRuleResponse;
import top.egon.mario.clocktower.script.dto.response.ClocktowerNightOrderResponse;
import top.egon.mario.clocktower.script.dto.response.ClocktowerRoleResponse;
import top.egon.mario.clocktower.script.dto.response.ClocktowerScriptResponse;

import java.util.List;

public interface ClocktowerScriptService {

    List<ClocktowerScriptResponse> listScripts();

    ClocktowerScriptResponse getScript(ClocktowerScriptCode scriptCode);

    List<ClocktowerRoleResponse> listRoles(ClocktowerScriptCode scriptCode, String roleType, Boolean enabled);

    List<ClocktowerNightOrderResponse> nightOrder(ClocktowerScriptCode scriptCode, String nightType);

    List<ClocktowerJinxRuleResponse> jinxRules(String roleCode, String severity);
}
```

Implement it in `ClocktowerScriptServiceImpl` using repositories. Add `ClocktowerScriptController` with:

```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/clocktower")
@Validated
public class ClocktowerScriptController {

    private final ClocktowerScriptService scriptService;

    @GetMapping("/scripts")
    public Mono<List<ClocktowerScriptResponse>> scripts() {
        return Mono.fromSupplier(scriptService::listScripts);
    }
}
```

Add these GET endpoints:

```text
GET /api/clocktower/scripts/{scriptCode}
GET /api/clocktower/scripts/{scriptCode}/roles?roleType=&enabled=
GET /api/clocktower/scripts/{scriptCode}/night-order?nightType=
GET /api/clocktower/terms?keyword=&category=
GET /api/clocktower/jinx-rules?roleCode=&severity=
```

- [ ] **Step 5: Run focused service tests**

```bash
cd /Users/mario/SelfProject/CyberMario/be
mvn -Dmaven.build.cache.enabled=false -Djava.version=21 -Dtest=ClocktowerScriptServiceTests test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add be/src/main/java/top/egon/mario/clocktower/script be/src/test/java/top/egon/mario/clocktower/script/ClocktowerScriptServiceTests.java
git commit -m "feat(clocktower): expose script metadata APIs"
```

---

### Task 4: Phase 1 Drools Wrapper And Board Validation

**Files:**

- Modify: `be/pom.xml`
- Create: `be/src/main/java/top/egon/mario/clocktower/engine/*`
- Create: `be/src/main/resources/clocktower/rules/board/board-validation.drl`
- Create: `be/src/main/resources/clocktower/rules/kmodule.xml`
- Create: `be/src/main/java/top/egon/mario/clocktower/board/*`
- Test: `be/src/test/java/top/egon/mario/clocktower/board/ClocktowerBoardServiceTests.java`

- [ ] **Step 1: Write the failing board service test**

Create `ClocktowerBoardServiceTests.java`:

```java
package top.egon.mario.clocktower.board;

import org.junit.jupiter.api.Test;
import top.egon.mario.clocktower.board.dto.request.ClocktowerBoardValidateRequest;
import top.egon.mario.clocktower.board.dto.response.BoardValidationResponse;
import top.egon.mario.clocktower.board.service.ClocktowerBoardService;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClocktowerBoardServiceTests {

    private final ClocktowerBoardService boardService = ClocktowerBoardTestFactory.service();

    @Test
    void validateRejectsTroubleBrewingWithTooFewPlayers() {
        BoardValidationResponse response = boardService.validate(new ClocktowerBoardValidateRequest(
                ClocktowerScriptCode.TROUBLE_BREWING,
                4,
                List.of("EMPATH", "IMP", "CHEF", "MONK")
        ));

        assertThat(response.valid()).isFalse();
        assertThat(response.issues()).extracting(issue -> issue.code())
                .contains("BOARD_PLAYER_COUNT_TOO_LOW");
    }

    @Test
    void validateAcceptsFivePlayerTroubleBrewingShape() {
        BoardValidationResponse response = boardService.validate(new ClocktowerBoardValidateRequest(
                ClocktowerScriptCode.TROUBLE_BREWING,
                5,
                List.of("EMPATH", "CHEF", "MONK", "POISONER", "IMP")
        ));

        assertThat(response.valid()).isTrue();
        assertThat(response.typeCounts().townsfolk()).isEqualTo(3);
        assertThat(response.typeCounts().minion()).isEqualTo(1);
        assertThat(response.typeCounts().demon()).isEqualTo(1);
    }
}
```

- [ ] **Step 2: Run the test and verify it fails**

```bash
cd /Users/mario/SelfProject/CyberMario/be
mvn -Dmaven.build.cache.enabled=false -Djava.version=21 -Dtest=ClocktowerBoardServiceTests test
```

Expected: FAIL because board service and Drools wrapper are missing.

- [ ] **Step 3: Add Drools dependencies**

Modify `be/pom.xml` and add:

```xml
<dependency>
    <groupId>org.drools</groupId>
    <artifactId>drools-engine</artifactId>
    <version>10.0.0</version>
</dependency>
<dependency>
    <groupId>org.drools</groupId>
    <artifactId>drools-xml-support</artifactId>
    <version>10.0.0</version>
</dependency>
```

- [ ] **Step 4: Add Phase 1 rule engine contracts**

Create `RuleDecisionCollector`, `RuleViolationDecision`, `ScoreDecision`, `BoardCandidateFact`, and
`ClocktowerRuleEngine`. Implement `RuleDecisionCollector` exactly as shown below.

The collector must expose this exact API:

```java
public class RuleDecisionCollector {
    private final List<RuleViolationDecision> violations = new ArrayList<>();
    private final List<ScoreDecision> scores = new ArrayList<>();

    public void reject(String code, String message, String severity) {
        violations.add(new RuleViolationDecision(code, message, severity));
    }

    public void score(String scoreType, int delta, String reason) {
        scores.add(new ScoreDecision(scoreType, delta, reason));
    }

    public List<RuleViolationDecision> violations() {
        return List.copyOf(violations);
    }

    public List<ScoreDecision> scores() {
        return List.copyOf(scores);
    }
}
```

- [ ] **Step 5: Add minimal DRL and board service**

Create `board-validation.drl` with rules for player count and role count. Implement `ClocktowerBoardService.validate()`
by:

1. Counting role types from role metadata.
2. Creating `BoardCandidateFact`.
3. Running Drools.
4. Returning `BoardValidationResponse`.

Create `ClocktowerBoardTestFactory.service()` as a test-only factory with in-memory role metadata for `EMPATH`, `CHEF`,
`MONK`, `POISONER`, and `IMP`.

- [ ] **Step 6: Run focused tests**

```bash
cd /Users/mario/SelfProject/CyberMario/be
mvn -Dmaven.build.cache.enabled=false -Djava.version=21 -Dtest=ClocktowerBoardServiceTests test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add be/pom.xml be/src/main/java/top/egon/mario/clocktower/engine be/src/main/java/top/egon/mario/clocktower/board be/src/main/resources/clocktower/rules be/src/test/java/top/egon/mario/clocktower/board
git commit -m "feat(clocktower): add board validation engine"
```

---

### Task 5: Board Generate, Save, List, And Controller

**Files:**

- Create/Modify: `be/src/main/java/top/egon/mario/clocktower/board/**`
- Test: `be/src/test/java/top/egon/mario/clocktower/board/ClocktowerBoardControllerTests.java`

- [ ] **Step 1: Write controller tests for board APIs**

Create tests that assert these routes exist and return the expected DTO shapes:

```java
@Test
void generateBoardReturnsRequestedCandidateCount() {
    ClocktowerBoardGenerateRequest request = new ClocktowerBoardGenerateRequest(
            ClocktowerScriptCode.TROUBLE_BREWING, 5, 2, 2, 2, true, 2, List.of(), List.of(), "seed-1");

    ClocktowerBoardGenerateResponse response = boardService.generate(request, principal(1L));

    assertThat(response.candidates()).hasSize(2);
    assertThat(response.candidates()).allSatisfy(candidate -> {
        assertThat(candidate.playerCount()).isEqualTo(5);
        assertThat(candidate.validation().valid()).isTrue();
    });
}
```

- [ ] **Step 2: Run tests and verify failure**

```bash
cd /Users/mario/SelfProject/CyberMario/be
mvn -Dmaven.build.cache.enabled=false -Djava.version=21 -Dtest=ClocktowerBoardControllerTests test
```

Expected: FAIL because generate/list/save endpoints are not present.

- [ ] **Step 3: Implement DTOs and generation**

Add these request/response records:

```java
public record ClocktowerBoardGenerateRequest(
        ClocktowerScriptCode scriptCode,
        int playerCount,
        int difficulty,
        int chaos,
        int evilPressure,
        boolean newbieFriendly,
        int candidateCount,
        List<String> lockedRoleCodes,
        List<String> bannedRoleCodes,
        String seed
) {
}

public record ClocktowerBoardValidateRequest(
        ClocktowerScriptCode scriptCode,
        int playerCount,
        List<String> roleCodes
) {
}

public record ClocktowerBoardSaveRequest(
        ClocktowerScriptCode scriptCode,
        int playerCount,
        int difficulty,
        int chaos,
        int evilPressure,
        boolean newbieFriendly,
        String seed,
        List<String> roleCodes,
        ClocktowerBoardValidationResponse validation
) {
}

public record ClocktowerBoardGenerateResponse(List<ClocktowerBoardCandidateResponse> candidates) {
}

public record ClocktowerBoardCandidateResponse(
        String candidateId,
        ClocktowerScriptCode scriptCode,
        int playerCount,
        List<String> roleCodes,
        ClocktowerBoardValidationResponse validation,
        List<ClocktowerScoreResponse> scores
) {
}

public record ClocktowerBoardValidationResponse(
        boolean valid,
        Map<ClocktowerRoleType, Integer> roleTypeCounts,
        List<ClocktowerRuleViolationResponse> violations,
        List<ClocktowerScoreResponse> scores
) {
}

public record ClocktowerRuleViolationResponse(String code, String message, String severity) {
}

public record ClocktowerScoreResponse(String scoreType, int delta, String reason) {
}

public record ClocktowerBoardConfigResponse(
        Long boardId,
        String boardCode,
        ClocktowerScriptCode scriptCode,
        int playerCount,
        List<String> roleCodes,
        ClocktowerBoardValidationResponse validation
) {
}
```

Implement `generate()` by selecting deterministic role combinations for Phase 1:

- `TROUBLE_BREWING`, 5 players: 3 townsfolk, 1 minion, 1 demon.
- `TROUBLE_BREWING`, 6 players: 3 townsfolk, 1 outsider, 1 minion, 1 demon.
- `BAD_MOON_RISING` and `SECTS_AND_VIOLETS`: reject below 7 players and generate the same shape rules as Trouble Brewing
  for 7+ until full role automation plan.

- [ ] **Step 4: Add controller methods**

Create `ClocktowerBoardController`:

```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/clocktower/boards")
@Validated
public class ClocktowerBoardController {

    private final ClocktowerBoardService boardService;

    @PostMapping("/generate")
    public Mono<ClocktowerBoardGenerateResponse> generate(@Valid @RequestBody Mono<ClocktowerBoardGenerateRequest> request,
                                                          @AuthenticationPrincipal RbacPrincipal principal) {
        return request.map(body -> boardService.generate(body, principal));
    }
}
```

Add `/validate`, `/save`, `GET /api/clocktower/boards`, and `DELETE /api/clocktower/boards/{boardId}`.

- [ ] **Step 5: Run board tests**

```bash
cd /Users/mario/SelfProject/CyberMario/be
mvn -Dmaven.build.cache.enabled=false -Djava.version=21 -Dtest=ClocktowerBoardServiceTests,ClocktowerBoardControllerTests test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add be/src/main/java/top/egon/mario/clocktower/board be/src/test/java/top/egon/mario/clocktower/board
git commit -m "feat(clocktower): add board builder APIs"
```

---

### Task 6: Event Store, Projection Skeleton, And Visibility Filter

**Files:**

- Create: `be/src/main/java/top/egon/mario/clocktower/event/**`
- Test: `be/src/test/java/top/egon/mario/clocktower/event/ClocktowerVisibilityFilterTests.java`

- [ ] **Step 1: Write visibility tests**

```java
@Test
void playerSeesPublicEventsAndOwnPrivateEventsOnly() {
    ClocktowerEventResponse publicEvent = event(1L, ClocktowerVisibility.PUBLIC, List.of());
    ClocktowerEventResponse ownPrivate = event(2L, ClocktowerVisibility.PRIVATE, List.of(10L));
    ClocktowerEventResponse otherPrivate = event(3L, ClocktowerVisibility.PRIVATE, List.of(20L));
    ClocktowerEventResponse storyteller = event(4L, ClocktowerVisibility.STORYTELLER, List.of());

    ClocktowerVisibilityFilter filter = new ClocktowerVisibilityFilter();

    List<ClocktowerEventResponse> visible = filter.visibleEvents(
            List.of(publicEvent, ownPrivate, otherPrivate, storyteller),
            ViewerContext.player(10L));

    assertThat(visible).extracting(ClocktowerEventResponse::eventId).containsExactly(1L, 2L);
}
```

- [ ] **Step 2: Run the visibility tests and verify failure**

```bash
cd /Users/mario/SelfProject/CyberMario/be
mvn -Dmaven.build.cache.enabled=false -Djava.version=21 -Dtest=ClocktowerVisibilityFilterTests test
```

Expected: FAIL because event DTOs and filter are missing.

- [ ] **Step 3: Implement event DTOs and visibility filter**

Create `ViewerContext` with static constructors:

```java
public record ViewerContext(Long userId, Long seatId, ClocktowerViewerMode mode) {
    public static ViewerContext player(Long seatId) {
        return new ViewerContext(null, seatId, ClocktowerViewerMode.PLAYER);
    }

    public static ViewerContext storyteller(Long userId) {
        return new ViewerContext(userId, null, ClocktowerViewerMode.STORYTELLER);
    }
}
```

Create `ClocktowerVisibilityFilter.visibleEvents(...)` implementing:

- PUBLIC visible to all.
- PRIVATE visible to recipients and storyteller.
- STORYTELLER visible only to storyteller.
- AUDIT hidden outside admin audit.

- [ ] **Step 4: Add event service**

Create `ClocktowerEventService.append(...)` that:

1. Computes next `seqNo` per room.
2. Saves `ClocktowerEventPo`.
3. Calls `ClocktowerEventProjector.project(event)`.
4. Returns `ClocktowerEventResponse`.

For Phase 1, projector may route to no-op methods until room/grimoire/action tasks add projections.

- [ ] **Step 5: Run event tests**

```bash
cd /Users/mario/SelfProject/CyberMario/be
mvn -Dmaven.build.cache.enabled=false -Djava.version=21 -Dtest=ClocktowerVisibilityFilterTests test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add be/src/main/java/top/egon/mario/clocktower/event be/src/test/java/top/egon/mario/clocktower/event
git commit -m "feat(clocktower): add event visibility foundation"
```

---

### Task 7: Room And Seat Lifecycle

**Files:**

- Create: `be/src/main/java/top/egon/mario/clocktower/room/**`
- Test: `be/src/test/java/top/egon/mario/clocktower/room/ClocktowerRoomServiceTests.java`

- [ ] **Step 1: Write room lifecycle tests**

```java
@Test
void createRoomCreatesLobbySeatsAndRoomCreatedEvent() {
    ClocktowerRoomCreateRequest request = new ClocktowerRoomCreateRequest(
            "周五暗流", ClocktowerScriptCode.TROUBLE_BREWING, 5, null, null,
            List.of("EMPATH", "CHEF", "MONK", "POISONER", "IMP"),
            "HUMAN", false, true, 0);

    ClocktowerRoomResponse room = roomService.create(request, principal(1L, "mario"));

    assertThat(room.status()).isEqualTo(ClocktowerRoomStatus.LOBBY);
    assertThat(room.phase()).isEqualTo(ClocktowerPhase.LOBBY);
    assertThat(room.seats()).hasSize(5);
    assertThat(room.roomCode()).hasSize(6);
}

@Test
void joinRoomBindsCurrentUserToRequestedSeat() {
    ClocktowerRoomResponse room = createFivePlayerRoom();

    ClocktowerSeatResponse seat = roomService.join(room.roomId(),
            new ClocktowerRoomJoinRequest(2, "Luigi", null), principal(2L, "luigi"));

    assertThat(seat.seatNo()).isEqualTo(2);
    assertThat(seat.userId()).isEqualTo(2L);
    assertThat(seat.displayName()).isEqualTo("Luigi");
}
```

- [ ] **Step 2: Run tests and verify failure**

```bash
cd /Users/mario/SelfProject/CyberMario/be
mvn -Dmaven.build.cache.enabled=false -Djava.version=21 -Dtest=ClocktowerRoomServiceTests test
```

Expected: FAIL because room service is missing.

- [ ] **Step 3: Implement room DTOs and service**

Add these request/response records:

```java
public record ClocktowerCreateRoomRequest(
        String name,
        ClocktowerScriptCode scriptCode,
        int playerCount,
        Long boardConfigId,
        List<String> roleCodes,
        String storytellerMode,
        boolean allowSpectators,
        boolean allowPrivateChat
) {
}

public record ClocktowerJoinRoomRequest(String displayName, Integer seatNo) {
}

public record ClocktowerUpdateSeatRequest(String displayName, Integer seatNo) {
}

public record ClocktowerRoomResponse(
        Long roomId,
        String roomCode,
        String name,
        ClocktowerScriptCode scriptCode,
        ClocktowerRoomStatus status,
        ClocktowerPhase phase,
        int playerCount,
        Long storytellerUserId,
        List<ClocktowerSeatResponse> seats
) {
}

public record ClocktowerSeatResponse(
        Long seatId,
        int seatNo,
        Long userId,
        String displayName,
        String roleCode,
        ClocktowerRoleType roleType,
        String lifeStatus,
        boolean connected,
        boolean hasDeadVote
) {
}
```

Implement:

- `create()`: validates board, creates room, creates seats, appends `ROOM_CREATED`.
- `list()`: current user rooms.
- `get()`: public room detail.
- `join()`: binds user to empty seat, appends `PLAYER_JOINED`.
- `leave()`: frees lobby seat or marks running seat disconnected.
- `updateSeat()`: storyteller can change displayName and seatNo before start.

- [ ] **Step 4: Add `ClocktowerRoomController`**

Expose:

```text
POST /api/clocktower/rooms
GET /api/clocktower/rooms
GET /api/clocktower/rooms/{roomId}
POST /api/clocktower/rooms/{roomId}/join
POST /api/clocktower/rooms/{roomId}/leave
PATCH /api/clocktower/rooms/{roomId}/seats/{seatId}
```

- [ ] **Step 5: Run room tests**

```bash
cd /Users/mario/SelfProject/CyberMario/be
mvn -Dmaven.build.cache.enabled=false -Djava.version=21 -Dtest=ClocktowerRoomServiceTests test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add be/src/main/java/top/egon/mario/clocktower/room be/src/test/java/top/egon/mario/clocktower/room
git commit -m "feat(clocktower): add room lifecycle"
```

---

### Task 8: Start Game, Role Assignment, And Grimoire Projection

**Files:**

- Create/Modify: `be/src/main/java/top/egon/mario/clocktower/grimoire/**`
- Modify: `be/src/main/java/top/egon/mario/clocktower/room/**`
- Test: `be/src/test/java/top/egon/mario/clocktower/grimoire/ClocktowerGrimoireServiceTests.java`

- [ ] **Step 1: Write grimoire start tests**

```java
@Test
void startRoomAssignsRolesAndCreatesPrivateRoleEvents() {
    ClocktowerRoomResponse room = createJoinedFivePlayerRoom();

    ClocktowerStartGameResponse started = roomService.start(room.roomId(), new ClocktowerRoomStartRequest(
            List.of(
                    new RoleAssignmentRequest(room.seats().get(0).seatId(), "EMPATH"),
                    new RoleAssignmentRequest(room.seats().get(1).seatId(), "CHEF"),
                    new RoleAssignmentRequest(room.seats().get(2).seatId(), "MONK"),
                    new RoleAssignmentRequest(room.seats().get(3).seatId(), "POISONER"),
                    new RoleAssignmentRequest(room.seats().get(4).seatId(), "IMP")
            ),
            false
    ), storytellerPrincipal());

    ClocktowerGrimoireResponse grimoire = grimoireService.getGrimoire(room.roomId(), storytellerPrincipal());

    assertThat(started.phase()).isEqualTo(ClocktowerPhase.FIRST_NIGHT);
    assertThat(grimoire.seats()).extracting(GrimoireSeatResponse::roleCode)
            .containsExactly("EMPATH", "CHEF", "MONK", "POISONER", "IMP");
}
```

- [ ] **Step 2: Run tests and verify failure**

```bash
cd /Users/mario/SelfProject/CyberMario/be
mvn -Dmaven.build.cache.enabled=false -Djava.version=21 -Dtest=ClocktowerGrimoireServiceTests test
```

Expected: FAIL because start and grimoire services are missing.

- [ ] **Step 3: Implement start game**

Implement `ClocktowerRoomService.start()`:

1. Load room and seats.
2. Require all seats have users or agent controllers.
3. Validate assignments match seat count.
4. Create one `ClocktowerGrimoireEntryPo` per seat.
5. Append private `ROLE_ASSIGNED` events with recipient seat id.
6. Append `PHASE_CHANGED` to `FIRST_NIGHT`.
7. Update room status to `RUNNING` and phase to `FIRST_NIGHT`.

- [ ] **Step 4: Implement grimoire response**

Create `ClocktowerGrimoireService.getGrimoire()` returning:

- phase
- seats with role, alignment, alive, dead vote
- markers
- pending tasks
- optional night checklist placeholder from Task 9

- [ ] **Step 5: Expose room start and grimoire endpoints**

Add:

```text
POST /api/clocktower/rooms/{roomId}/start
GET /api/clocktower/rooms/{roomId}/grimoire
```

- [ ] **Step 6: Run grimoire tests**

```bash
cd /Users/mario/SelfProject/CyberMario/be
mvn -Dmaven.build.cache.enabled=false -Djava.version=21 -Dtest=ClocktowerGrimoireServiceTests test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add be/src/main/java/top/egon/mario/clocktower/room be/src/main/java/top/egon/mario/clocktower/grimoire be/src/test/java/top/egon/mario/clocktower/grimoire
git commit -m "feat(clocktower): start games with grimoire state"
```

---

### Task 9: Night Checklist

**Files:**

- Create/Modify: `be/src/main/java/top/egon/mario/clocktower/grimoire/**`
- Create: `be/src/main/resources/clocktower/rules/night/night-order.drl`
- Test: `be/src/test/java/top/egon/mario/clocktower/grimoire/ClocktowerNightChecklistServiceTests.java`

- [ ] **Step 1: Write night checklist tests**

```java
@Test
void firstNightChecklistContainsAliveRolesWithFirstNightOrder() {
    ClocktowerRoomResponse room = startedTroubleBrewingRoomWithRoles("EMPATH", "CHEF", "MONK", "POISONER", "IMP");

    NightChecklistResponse checklist = grimoireService.nightChecklist(room.roomId(), storytellerPrincipal());

    assertThat(checklist.nightType()).isEqualTo("FIRST_NIGHT");
    assertThat(checklist.steps()).extracting(NightStepResponse::roleCode)
            .contains("POISONER", "EMPATH");
    assertThat(checklist.completed()).isFalse();
}
```

- [ ] **Step 2: Run tests and verify failure**

```bash
cd /Users/mario/SelfProject/CyberMario/be
mvn -Dmaven.build.cache.enabled=false -Djava.version=21 -Dtest=ClocktowerNightChecklistServiceTests test
```

Expected: FAIL because night checklist method is missing.

- [ ] **Step 3: Implement checklist DTOs and service**

Create:

```java
public record NightChecklistResponse(
        int nightNo,
        String nightType,
        List<NightStepResponse> steps,
        boolean completed
) {
}

public record NightStepResponse(
        int orderNo,
        Long seatId,
        String roleCode,
        String roleName,
        boolean wakeRequired,
        String skipReason,
        boolean completed
) {
}
```

Implement with static `clocktower_night_order` data for Phase 1. Wire Drools facts after Task 4 engine exists; if Drools
emits no steps, fallback to static order filtered by in-play roles.

- [ ] **Step 4: Add endpoint**

`GET /api/clocktower/rooms/{roomId}/night-checklist`

- [ ] **Step 5: Run tests**

```bash
cd /Users/mario/SelfProject/CyberMario/be
mvn -Dmaven.build.cache.enabled=false -Djava.version=21 -Dtest=ClocktowerNightChecklistServiceTests test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add be/src/main/java/top/egon/mario/clocktower/grimoire be/src/main/resources/clocktower/rules/night be/src/test/java/top/egon/mario/clocktower/grimoire/ClocktowerNightChecklistServiceTests.java
git commit -m "feat(clocktower): add night checklist"
```

---

### Task 10: Player View Aggregation

**Files:**

- Create: `be/src/main/java/top/egon/mario/clocktower/view/**`
- Test: `be/src/test/java/top/egon/mario/clocktower/view/ClocktowerViewServiceTests.java`

- [ ] **Step 1: Write player view visibility tests**

```java
@Test
void playerViewIncludesOwnRoleAndHidesOtherRoles() {
    ClocktowerRoomResponse room = startedTroubleBrewingRoomWithJoinedUsers();
    Long marioSeatId = room.seats().getFirst().seatId();

    ClocktowerPlayerViewResponse view = viewService.playerView(room.roomId(), marioSeatId, principal(1L, "mario"));

    assertThat(view.mySeat().roleCode()).isEqualTo("EMPATH");
    assertThat(view.publicSeats()).allSatisfy(seat -> assertThat(seat.roleCode()).isNull());
    assertThat(view.recentEvents()).noneMatch(event -> event.visibility() == ClocktowerVisibility.STORYTELLER);
}
```

- [ ] **Step 2: Run tests and verify failure**

```bash
cd /Users/mario/SelfProject/CyberMario/be
mvn -Dmaven.build.cache.enabled=false -Djava.version=21 -Dtest=ClocktowerViewServiceTests test
```

Expected: FAIL because view service is missing.

- [ ] **Step 3: Implement view DTOs and service**

Create `ClocktowerPlayerViewResponse`, `PlayerSeatViewResponse`, `PublicSeatResponse`, `AvailableActionResponse`,
`PrivateThreadSummaryResponse`, and `GamePhaseResponse`.

Implement:

- Resolve viewer seat from principal and optional seatId.
- Include `mySeat.roleCode`.
- Include public seats without role codes.
- Include recent visible events through `ClocktowerVisibilityFilter`.
- Include available actions based on phase: `PUBLIC_SPEECH` during DAY/NOMINATION, `NOMINATE` during DAY, `VOTE` during
  NOMINATION, `NIGHT_CHOICE` during NIGHT/FIRST_NIGHT when checklist says actor wakes.

- [ ] **Step 4: Add endpoint**

`GET /api/clocktower/rooms/{roomId}/view`

- [ ] **Step 5: Run tests**

```bash
cd /Users/mario/SelfProject/CyberMario/be
mvn -Dmaven.build.cache.enabled=false -Djava.version=21 -Dtest=ClocktowerViewServiceTests test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add be/src/main/java/top/egon/mario/clocktower/view be/src/test/java/top/egon/mario/clocktower/view
git commit -m "feat(clocktower): add player view aggregation"
```

---

### Task 11: Player Actions For Speech, Private Message, Nomination, Vote, And Pass

**Files:**

- Create: `be/src/main/java/top/egon/mario/clocktower/action/**`
- Test: `be/src/test/java/top/egon/mario/clocktower/action/ClocktowerActionServiceTests.java`

- [ ] **Step 1: Write action service tests**

```java
@Test
void publicSpeechCreatesPublicEvent() {
    ClocktowerRoomResponse room = runningDayRoom();
    Long seatId = room.seats().getFirst().seatId();

    ClocktowerActionResponse response = actionService.submit(room.roomId(), new ClocktowerActionRequest(
            seatId, "PUBLIC_SPEECH", List.of(), null, "我今天想听 3 号发言。", Map.of(), "client-1"), principal(1L, "mario"));

    assertThat(response.accepted()).isTrue();
    assertThat(eventRepository.findByRoomIdOrderBySeqNoAsc(room.roomId()))
            .extracting(ClocktowerEventPo::getEventType)
            .contains(ClocktowerEventType.PUBLIC_MESSAGE_SENT);
}

@Test
void nominationRejectsDeadNominator() {
    ClocktowerRoomResponse room = runningDayRoomWithDeadFirstSeat();
    Long deadSeat = room.seats().getFirst().seatId();
    Long target = room.seats().get(1).seatId();

    ClocktowerActionResponse response = actionService.submit(room.roomId(), new ClocktowerActionRequest(
            deadSeat, "NOMINATE", List.of(target), null, "我提名 2 号。", Map.of(), "client-2"), principal(1L, "mario"));

    assertThat(response.accepted()).isFalse();
    assertThat(response.rejectedCode()).isEqualTo("NOMINATOR_NOT_ALIVE");
}
```

- [ ] **Step 2: Run tests and verify failure**

```bash
cd /Users/mario/SelfProject/CyberMario/be
mvn -Dmaven.build.cache.enabled=false -Djava.version=21 -Dtest=ClocktowerActionServiceTests test
```

Expected: FAIL because action service is missing.

- [ ] **Step 3: Implement action request/response and service**

Create `ClocktowerActionRequest` exactly:

```java
public record ClocktowerActionRequest(
        Long seatId,
        String actionType,
        List<Long> targetSeatIds,
        Long privateThreadId,
        String content,
        Map<String, Object> payload,
        String clientActionId
) {
}
```

Implement actions:

- `PUBLIC_SPEECH`: appends `PUBLIC_MESSAGE_SENT`.
- `PRIVATE_MESSAGE`: appends `PRIVATE_MESSAGE_SENT` with recipients.
- `NOMINATE`: validates alive nominator and target, creates nomination row, appends `PLAYER_NOMINATED`, moves room phase
  to `NOMINATION`.
- `VOTE`: records vote, spends dead vote if needed, appends `VOTE_CAST`.
- `PASS`: appends `PLAYER_PASSED`.

- [ ] **Step 4: Add endpoint**

`POST /api/clocktower/rooms/{roomId}/actions`

- [ ] **Step 5: Run tests**

```bash
cd /Users/mario/SelfProject/CyberMario/be
mvn -Dmaven.build.cache.enabled=false -Djava.version=21 -Dtest=ClocktowerActionServiceTests test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add be/src/main/java/top/egon/mario/clocktower/action be/src/test/java/top/egon/mario/clocktower/action
git commit -m "feat(clocktower): add player actions"
```

---

### Task 12: Storyteller Actions, Markers, Execution, And Phase Advance

**Files:**

- Modify: `be/src/main/java/top/egon/mario/clocktower/grimoire/**`
- Test: `be/src/test/java/top/egon/mario/clocktower/grimoire/ClocktowerStorytellerActionServiceTests.java`

- [ ] **Step 1: Write storyteller action tests**

```java
@Test
void addMarkerCreatesMarkerAndEvent() {
    ClocktowerRoomResponse room = startedTroubleBrewingRoomWithJoinedUsers();
    Long targetSeat = room.seats().getFirst().seatId();

    StorytellerActionResponse response = grimoireService.storytellerAction(room.roomId(), new StorytellerActionRequest(
            "ADD_MARKER", List.of(targetSeat), null,
            Map.of("markerType", "POISONED", "note", "投毒者选择")), storytellerPrincipal());

    assertThat(response.accepted()).isTrue();
    assertThat(response.grimoire().markers()).extracting(StatusMarkerResponse::markerType).contains("POISONED");
}

@Test
void markDeadUpdatesGrimoireAndAppendsDeathEvent() {
    ClocktowerRoomResponse room = startedTroubleBrewingRoomWithJoinedUsers();
    Long targetSeat = room.seats().getFirst().seatId();

    StorytellerActionResponse response = grimoireService.storytellerAction(room.roomId(), new StorytellerActionRequest(
            "MARK_DEAD", List.of(targetSeat), "夜晚死亡", Map.of("reason", "NIGHT_DEATH")), storytellerPrincipal());

    assertThat(response.grimoire().seats().getFirst().alive()).isFalse();
}
```

- [ ] **Step 2: Run tests and verify failure**

```bash
cd /Users/mario/SelfProject/CyberMario/be
mvn -Dmaven.build.cache.enabled=false -Djava.version=21 -Dtest=ClocktowerStorytellerActionServiceTests test
```

Expected: FAIL because storyteller action service is missing.

- [ ] **Step 3: Implement `StorytellerActionRequest` and service method**

Implement action types:

- `ADD_MARKER`
- `REMOVE_MARKER`
- `MARK_DEAD`
- `RESTORE_ALIVE`
- `PUBLIC_ANNOUNCEMENT`
- `PRIVATE_INFO`
- `ADVANCE_PHASE`

For Phase 1, `CHANGE_ROLE`, `CHANGE_ALIGNMENT`, and `RESOLVE_TASK` may return `accepted=false` with code
`ACTION_NOT_ENABLED_IN_PHASE_ONE` because full role editing and generated tasks belong to the role automation plan.

- [ ] **Step 4: Implement execution and phase advance**

`ADVANCE_PHASE` rules:

- `FIRST_NIGHT` -> `DAY`.
- `DAY` -> `NIGHT` if no nomination is active.
- `NOMINATION` -> `EXECUTION` after vote closes.
- `EXECUTION` -> `NIGHT` after storyteller executes or skips.
- `NIGHT` -> `DAY`.

Append `PHASE_CHANGED` every time.

- [ ] **Step 5: Add endpoint**

`POST /api/clocktower/rooms/{roomId}/storyteller/actions`

- [ ] **Step 6: Run tests**

```bash
cd /Users/mario/SelfProject/CyberMario/be
mvn -Dmaven.build.cache.enabled=false -Djava.version=21 -Dtest=ClocktowerStorytellerActionServiceTests test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add be/src/main/java/top/egon/mario/clocktower/grimoire be/src/test/java/top/egon/mario/clocktower/grimoire/ClocktowerStorytellerActionServiceTests.java
git commit -m "feat(clocktower): add storyteller actions"
```

---

### Task 13: SSE Event Stream

**Files:**

- Create: `be/src/main/java/top/egon/mario/clocktower/event/web/ClocktowerEventStreamController.java`
- Create/Modify: `be/src/main/java/top/egon/mario/clocktower/event/service/ClocktowerEventStreamService.java`
- Test: `be/src/test/java/top/egon/mario/clocktower/event/ClocktowerEventStreamServiceTests.java`

- [ ] **Step 1: Write event stream tests**

```java
@Test
void streamBackfillsEventsAfterLastSeqForViewer() {
    ClocktowerRoomResponse room = runningDayRoomWithEvents();
    ViewerContext viewer = ViewerContext.player(room.seats().getFirst().seatId());

    List<ClocktowerEventResponse> backfill = streamService.backfill(room.roomId(), 1L, viewer);

    assertThat(backfill).allSatisfy(event -> assertThat(event.seqNo()).isGreaterThan(1L));
    assertThat(backfill).noneMatch(event -> event.visibility() == ClocktowerVisibility.STORYTELLER);
}
```

- [ ] **Step 2: Run tests and verify failure**

```bash
cd /Users/mario/SelfProject/CyberMario/be
mvn -Dmaven.build.cache.enabled=false -Djava.version=21 -Dtest=ClocktowerEventStreamServiceTests test
```

Expected: FAIL because stream service is missing.

- [ ] **Step 3: Implement backfill and live sink**

Use `Sinks.Many<ClocktowerEventResponse>` per room:

```java
private final ConcurrentMap<Long, Sinks.Many<ClocktowerEventResponse>> roomSinks = new ConcurrentHashMap<>();

public Flux<ClocktowerEventResponse> stream(Long roomId, Long lastEventSeq, ViewerContext viewer) {
    Flux<ClocktowerEventResponse> backfill = Flux.fromIterable(backfill(roomId, lastEventSeq, viewer));
    Flux<ClocktowerEventResponse> live = roomSinks.computeIfAbsent(roomId,
            id -> Sinks.many().multicast().directBestEffort()).asFlux()
            .filter(event -> visibilityFilter.isVisible(event, viewer));
    return backfill.concatWith(live);
}
```

- [ ] **Step 4: Publish appended events**

After `ClocktowerEventService.append(...)` saves an event, call `streamService.publish(response)`.

- [ ] **Step 5: Add controller**

Expose:

```java
@GetMapping(path = "/api/clocktower/rooms/{roomId}/events/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<ClocktowerEventResponse>> stream(...)
```

- [ ] **Step 6: Run tests**

```bash
cd /Users/mario/SelfProject/CyberMario/be
mvn -Dmaven.build.cache.enabled=false -Djava.version=21 -Dtest=ClocktowerEventStreamServiceTests test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add be/src/main/java/top/egon/mario/clocktower/event be/src/test/java/top/egon/mario/clocktower/event/ClocktowerEventStreamServiceTests.java
git commit -m "feat(clocktower): stream room events"
```

---

### Task 14: Basic Replay

**Files:**

- Create: `be/src/main/java/top/egon/mario/clocktower/replay/**`
- Test: `be/src/test/java/top/egon/mario/clocktower/replay/ClocktowerReplayServiceTests.java`

- [ ] **Step 1: Write replay tests**

```java
@Test
void publicReplayHidesPrivateAndStorytellerEvents() {
    ClocktowerRoomResponse room = completedRoomWithMixedEvents();

    ClocktowerReplayResponse replay = replayService.replay(room.roomId(), "PUBLIC", null, null, principal(1L, "mario"));

    assertThat(replay.mode()).isEqualTo("PUBLIC");
    assertThat(replay.events()).allSatisfy(event -> assertThat(event.visibility()).isEqualTo(ClocktowerVisibility.PUBLIC));
}

@Test
void fullReplayRequiresStorytellerOrAdmin() {
    ClocktowerRoomResponse room = completedRoomWithMixedEvents();

    assertThatThrownBy(() -> replayService.replay(room.roomId(), "FULL", null, null, principal(2L, "luigi")))
            .isInstanceOf(ClocktowerException.class)
            .hasMessageContaining("CLOCKTOWER_REPLAY_FORBIDDEN");
}
```

- [ ] **Step 2: Run tests and verify failure**

```bash
cd /Users/mario/SelfProject/CyberMario/be
mvn -Dmaven.build.cache.enabled=false -Djava.version=21 -Dtest=ClocktowerReplayServiceTests test
```

Expected: FAIL because replay service is missing.

- [ ] **Step 3: Implement replay service**

Implement:

- `replay(roomId, mode, fromSeq, toSeq, principal)`
- `votes(roomId, principal)`

Use event repository ordered by `seqNo`. Apply visibility for `PUBLIC`. Require storyteller/admin for `FULL`.

- [ ] **Step 4: Add controller**

Expose:

```text
GET /api/clocktower/replays/{roomId}
GET /api/clocktower/replays/{roomId}/votes
```

- [ ] **Step 5: Run tests**

```bash
cd /Users/mario/SelfProject/CyberMario/be
mvn -Dmaven.build.cache.enabled=false -Djava.version=21 -Dtest=ClocktowerReplayServiceTests test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add be/src/main/java/top/egon/mario/clocktower/replay be/src/test/java/top/egon/mario/clocktower/replay
git commit -m "feat(clocktower): add basic replay"
```

---

### Task 15: RBAC Resources

**Files:**

- Create: `be/src/main/java/top/egon/mario/clocktower/resource/ClocktowerRbacResourceProvider.java`
- Test: `be/src/test/java/top/egon/mario/clocktower/resource/ClocktowerRbacResourceProviderTests.java`

- [ ] **Step 1: Write RBAC provider tests**

```java
@Test
void providerDeclaresClocktowerMenusApisAndPresetRoles() {
    ClocktowerRbacResourceProvider provider = new ClocktowerRbacResourceProvider();

    assertThat(provider.appCode()).isEqualTo("clocktower");
    assertThat(provider.resources()).extracting(resource -> resource.code())
            .contains("menu:clocktower:boards",
                    "menu:clocktower:rooms",
                    "api:clocktower:rooms:*",
                    "api:clocktower:events:stream",
                    "api:clocktower:grimoire:*");
    assertThat(provider.rolePresets()).extracting(role -> role.roleCode())
            .contains("CLOCKTOWER_PLAYER", "CLOCKTOWER_STORYTELLER");
}
```

- [ ] **Step 2: Run tests and verify failure**

```bash
cd /Users/mario/SelfProject/CyberMario/be
mvn -Dmaven.build.cache.enabled=false -Djava.version=21 -Dtest=ClocktowerRbacResourceProviderTests test
```

Expected: FAIL because provider is missing.

- [ ] **Step 3: Implement provider**

Follow `AgentDebugRbacResourceProvider` style. Create menus:

- `menu:clocktower:boards`
- `menu:clocktower:rooms`
- `menu:clocktower:rules`
- `menu:clocktower:replays`

Create API seeds:

- `GET /api/clocktower/scripts/**`
- `ANY /api/clocktower/boards/**`
- `ANY /api/clocktower/rooms/**`
- `GET /api/clocktower/rooms/*/events/stream`
- `ANY /api/clocktower/replays/**`

Preset roles:

- `CLOCKTOWER_PLAYER`
- `CLOCKTOWER_STORYTELLER`

- [ ] **Step 4: Run tests**

```bash
cd /Users/mario/SelfProject/CyberMario/be
mvn -Dmaven.build.cache.enabled=false -Djava.version=21 -Dtest=ClocktowerRbacResourceProviderTests test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add be/src/main/java/top/egon/mario/clocktower/resource be/src/test/java/top/egon/mario/clocktower/resource
git commit -m "feat(clocktower): register rbac resources"
```

---

### Task 16: Backend Phase 1 Integration Test

**Files:**

- Create: `be/src/test/java/top/egon/mario/clocktower/ClocktowerCoreFlowTests.java`

- [ ] **Step 1: Write end-to-end service flow test**

```java
@Test
void storytellerCanRunCoreFivePlayerTroubleBrewingFlow() {
    ClocktowerRoomResponse room = roomService.create(createFivePlayerRequest(), principal(1L, "storyteller"));
    joinAllSeats(room);
    ClocktowerStartGameResponse started = roomService.start(room.roomId(), fixedAssignments(room), principal(1L, "storyteller"));

    assertThat(started.phase()).isEqualTo(ClocktowerPhase.FIRST_NIGHT);

    StorytellerActionResponse day = grimoireService.storytellerAction(room.roomId(),
            new StorytellerActionRequest("ADVANCE_PHASE", List.of(), null, Map.of("targetPhase", "DAY")),
            principal(1L, "storyteller"));
    assertThat(day.grimoire().phase().phase()).isEqualTo(ClocktowerPhase.DAY);

    Long seatOne = room.seats().getFirst().seatId();
    Long seatTwo = room.seats().get(1).seatId();
    ClocktowerActionResponse nominate = actionService.submit(room.roomId(),
            new ClocktowerActionRequest(seatOne, "NOMINATE", List.of(seatTwo), null, "我提名 2 号。", Map.of(), "nom-1"),
            principal(2L, "player1"));

    assertThat(nominate.accepted()).isTrue();
    assertThat(replayService.replay(room.roomId(), "PUBLIC", null, null, principal(2L, "player1")).events())
            .extracting(ClocktowerEventResponse::eventType)
            .contains(ClocktowerEventType.PLAYER_NOMINATED);
}
```

- [ ] **Step 2: Run the integration test**

```bash
cd /Users/mario/SelfProject/CyberMario/be
mvn -Dmaven.build.cache.enabled=false -Djava.version=21 -Dtest=ClocktowerCoreFlowTests test
```

Expected: PASS after Tasks 1-15.

- [ ] **Step 3: Run backend clocktower test suite**

```bash
cd /Users/mario/SelfProject/CyberMario/be
mvn -Dmaven.build.cache.enabled=false -Djava.version=21 -Dtest='top.egon.mario.clocktower.**' test
```

Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add be/src/test/java/top/egon/mario/clocktower/ClocktowerCoreFlowTests.java
git commit -m "test(clocktower): cover core game flow"
```

---

### Task 17: Frontend Types And API Service

**Files:**

- Create: `fe/src/modules/clocktower/clocktowerTypes.ts`
- Create: `fe/src/modules/clocktower/clocktowerService.ts`
- Test: `fe/src/modules/clocktower/clocktowerService.test.ts`

- [ ] **Step 1: Write service URL tests**

Create `clocktowerService.test.ts` using the existing request test style:

```ts
import {describe, expect, it, vi} from 'vitest'
import {generateClocktowerBoard, getClocktowerScripts, streamClocktowerEvents} from './clocktowerService'

vi.mock('../../services/request', () => ({
    requestJson: vi.fn(),
    streamJsonLines: vi.fn(),
}))

describe('clocktowerService', () => {
    it('loads script list from the clocktower script endpoint', async () => {
        const {requestJson} = await import('../../services/request')
        await getClocktowerScripts()
        expect(requestJson).toHaveBeenCalledWith('/api/clocktower/scripts')
    })

    it('generates boards with POST body', async () => {
        const {requestJson} = await import('../../services/request')
        const request = {
            scriptCode: 'TROUBLE_BREWING' as const,
            playerCount: 5,
            difficulty: 2,
            chaos: 2,
            evilPressure: 2,
            newbieFriendly: true,
            candidateCount: 2,
        }
        await generateClocktowerBoard(request)
        expect(requestJson).toHaveBeenCalledWith('/api/clocktower/boards/generate', {
            method: 'POST',
            body: request,
        })
    })

    it('streams room events with query parameters', async () => {
        const {streamJsonLines} = await import('../../services/request')
        const signal = new AbortController().signal
        const onEvent = vi.fn()
        await streamClocktowerEvents(7, {seatId: 3, lastEventSeq: 10}, signal, onEvent)
        expect(streamJsonLines).toHaveBeenCalledWith(
            '/api/clocktower/rooms/7/events/stream?seatId=3&lastEventSeq=10',
            {signal},
            onEvent,
        )
    })
})
```

- [ ] **Step 2: Run test and verify failure**

```bash
cd /Users/mario/SelfProject/CyberMario/fe
bun run test -- clocktowerService.test.ts
```

Expected: FAIL because files do not exist.

- [ ] **Step 3: Add TypeScript contracts and service functions**

Create `clocktowerTypes.ts` with exported TypeScript types matching the Java DTOs from Tasks 3, 5, 7, 8, 9, 10, 11, 12,
and 14. Include these literal unions:

```ts
export type ClocktowerScriptCode = 'TROUBLE_BREWING' | 'BAD_MOON_RISING' | 'SECTS_AND_VIOLETS'
export type ClocktowerRoleType = 'TOWNSFOLK' | 'OUTSIDER' | 'MINION' | 'DEMON' | 'TRAVELER' | 'FABLED'
export type ClocktowerRoomStatus = 'LOBBY' | 'SETUP' | 'RUNNING' | 'ENDED' | 'ARCHIVED'
export type ClocktowerPhase = 'LOBBY' | 'SETUP' | 'FIRST_NIGHT' | 'DAY' | 'NOMINATION' | 'EXECUTION' | 'NIGHT' | 'ENDED'
export type ClocktowerVisibility = 'PUBLIC' | 'PRIVATE' | 'STORYTELLER' | 'AUDIT'
```

Create `clocktowerService.ts` with:

```ts
import {requestJson, streamJsonLines} from '../../services/request'
import {buildSearchParams} from '../../services/urlSearch'
import type {ClocktowerBoardGenerateRequest, ClocktowerBoardGenerateResponse, ClocktowerEventResponse, ClocktowerScriptResponse} from './clocktowerTypes'

export function getClocktowerScripts() {
    return requestJson<ClocktowerScriptResponse[]>('/api/clocktower/scripts')
}

export function generateClocktowerBoard(request: ClocktowerBoardGenerateRequest) {
    return requestJson<ClocktowerBoardGenerateResponse>('/api/clocktower/boards/generate', {
        method: 'POST',
        body: request,
    })
}

export function streamClocktowerEvents(
    roomId: number,
    params: { seatId?: number; lastEventSeq?: number },
    signal: AbortSignal,
    onEvent: (event: ClocktowerEventResponse) => void,
) {
    return streamJsonLines<ClocktowerEventResponse>(
        `/api/clocktower/rooms/${roomId}/events/stream?${buildSearchParams(params)}`,
        {signal},
        onEvent,
    )
}
```

Add these service functions in the same file:

```ts
export function getClocktowerScript(scriptCode: ClocktowerScriptCode): Promise<ClocktowerScriptResponse>
export function getClocktowerRoles(scriptCode: ClocktowerScriptCode, params?: {roleType?: ClocktowerRoleType; enabled?: boolean}): Promise<ClocktowerRoleResponse[]>
export function getClocktowerNightOrder(scriptCode: ClocktowerScriptCode, params?: {nightType?: string}): Promise<ClocktowerNightOrderResponse[]>
export function validateClocktowerBoard(request: ClocktowerBoardValidateRequest): Promise<ClocktowerBoardValidationResponse>
export function saveClocktowerBoard(request: ClocktowerBoardSaveRequest): Promise<ClocktowerBoardConfigResponse>
export function listClocktowerBoards(): Promise<ClocktowerBoardConfigResponse[]>
export function deleteClocktowerBoard(boardId: number): Promise<void>
export function createClocktowerRoom(request: ClocktowerCreateRoomRequest): Promise<ClocktowerRoomResponse>
export function listClocktowerRooms(): Promise<ClocktowerRoomResponse[]>
export function getClocktowerRoom(roomId: number): Promise<ClocktowerRoomResponse>
export function joinClocktowerRoom(roomId: number, request: ClocktowerJoinRoomRequest): Promise<ClocktowerRoomResponse>
export function leaveClocktowerRoom(roomId: number): Promise<void>
export function updateClocktowerSeat(roomId: number, seatId: number, request: ClocktowerUpdateSeatRequest): Promise<ClocktowerRoomResponse>
export function startClocktowerGame(roomId: number, request: ClocktowerRoomStartRequest): Promise<ClocktowerStartGameResponse>
export function getClocktowerGrimoire(roomId: number): Promise<ClocktowerGrimoireResponse>
export function getClocktowerNightChecklist(roomId: number): Promise<ClocktowerNightChecklistResponse>
export function submitClocktowerPlayerAction(roomId: number, request: ClocktowerPlayerActionRequest): Promise<ClocktowerActionResponse>
export function submitClocktowerStorytellerAction(roomId: number, request: ClocktowerStorytellerActionRequest): Promise<ClocktowerActionResponse>
export function getClocktowerReplay(roomId: number): Promise<ClocktowerReplayResponse>
```

- [ ] **Step 4: Run service tests**

```bash
cd /Users/mario/SelfProject/CyberMario/fe
bun run test -- clocktowerService.test.ts
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add fe/src/modules/clocktower/clocktowerTypes.ts fe/src/modules/clocktower/clocktowerService.ts fe/src/modules/clocktower/clocktowerService.test.ts
git commit -m "feat(clocktower): add frontend api client"
```

---

### Task 18: Frontend Routes, Board Builder, Room List, And Lobby

**Files:**

- Modify: `fe/src/app/routes.tsx`
- Create: `fe/src/modules/clocktower/BoardBuilderPage.tsx`
- Create: `fe/src/modules/clocktower/RoomListPage.tsx`
- Create: `fe/src/modules/clocktower/RoomLobbyPage.tsx`
- Create: `fe/src/modules/clocktower/components/BoardCandidateTable.tsx`
- Create: `fe/src/modules/clocktower/components/RoleTypeTag.tsx`
- Test: `fe/src/modules/clocktower/BoardBuilderPage.test.tsx`

- [ ] **Step 1: Write BoardBuilderPage test**

```tsx
import {render, screen} from '@testing-library/react'
import {describe, expect, it, vi} from 'vitest'
import {Component as BoardBuilderPage} from './BoardBuilderPage'

vi.mock('./clocktowerService', () => ({
    getClocktowerScripts: vi.fn().mockResolvedValue([
        {scriptCode: 'TROUBLE_BREWING', name: '暗流涌动', edition: 'BASE_3', minPlayers: 5, maxPlayers: 15, roleCount: 22, enabled: true},
    ]),
    generateClocktowerBoard: vi.fn(),
}))

describe('BoardBuilderPage', () => {
    it('renders script and board controls', async () => {
        render(<BoardBuilderPage/>)
        expect(await screen.findByText('钟楼配板')).toBeInTheDocument()
        expect(screen.getByText('剧本')).toBeInTheDocument()
        expect(screen.getByText('人数')).toBeInTheDocument()
        expect(screen.getByRole('button', {name: /生成配板/})).toBeInTheDocument()
    })
})
```

- [ ] **Step 2: Run frontend test and verify failure**

```bash
cd /Users/mario/SelfProject/CyberMario/fe
bun run test -- BoardBuilderPage.test.tsx
```

Expected: FAIL because page does not exist.

- [ ] **Step 3: Add routes**

Modify `fe/src/app/routes.tsx`:

```tsx
{path: 'clocktower/boards', lazy: () => import('../modules/clocktower/BoardBuilderPage')},
{path: 'clocktower/rooms', lazy: () => import('../modules/clocktower/RoomListPage')},
{path: 'clocktower/rooms/:roomId/lobby', lazy: () => import('../modules/clocktower/RoomLobbyPage')},
```

- [ ] **Step 4: Implement board and lobby shells**

Create pages using Ant Design `Form`, `Select`, `InputNumber`, `Slider`, `Switch`, `Button`, `Table`, and `Alert`. Keep
pages functional:

- Board page loads scripts and calls `generateClocktowerBoard`.
- Room list loads rooms and links to lobby.
- Lobby shows seats and start button.

- [ ] **Step 5: Run page test and typecheck**

```bash
cd /Users/mario/SelfProject/CyberMario/fe
bun run test -- BoardBuilderPage.test.tsx
bun run typecheck
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add fe/src/app/routes.tsx fe/src/modules/clocktower
git commit -m "feat(clocktower): add board and lobby pages"
```

---

### Task 19: Frontend Game Room, Grimoire, And Replay Shells

**Files:**

- Create: `fe/src/modules/clocktower/GameRoomPage.tsx`
- Create: `fe/src/modules/clocktower/StorytellerGrimoirePage.tsx`
- Create: `fe/src/modules/clocktower/ReplayPage.tsx`
- Create: `fe/src/modules/clocktower/components/EventTimeline.tsx`
- Create: `fe/src/modules/clocktower/components/NightChecklist.tsx`
- Create: `fe/src/modules/clocktower/components/VotePanel.tsx`
- Test: `fe/src/modules/clocktower/GameRoomPage.test.tsx`
- Test: `fe/src/modules/clocktower/StorytellerGrimoirePage.test.tsx`

- [ ] **Step 1: Write GameRoomPage test**

```tsx
import {render, screen} from '@testing-library/react'
import {describe, expect, it, vi} from 'vitest'
import {Component as GameRoomPage} from './GameRoomPage'

vi.mock('react-router', async () => {
    const actual = await vi.importActual<typeof import('react-router')>('react-router')
    return {...actual, useParams: () => ({roomId: '7'})}
})

vi.mock('./clocktowerService', () => ({
    getClocktowerPlayerView: vi.fn().mockResolvedValue({
        room: {roomId: 7, name: '测试房间', phase: 'DAY'},
        viewer: {viewerMode: 'PLAYER'},
        publicSeats: [],
        phase: {phase: 'DAY', dayNo: 1, nightNo: 0},
        availableActions: [{actionType: 'PUBLIC_SPEECH', label: '公开发言'}],
        recentEvents: [],
        privateThreads: [],
    }),
    streamClocktowerEvents: vi.fn(),
    submitClocktowerAction: vi.fn(),
}))

describe('GameRoomPage', () => {
    it('renders player room surface', async () => {
        render(<GameRoomPage/>)
        expect(await screen.findByText('测试房间')).toBeInTheDocument()
        expect(screen.getByText('公开发言')).toBeInTheDocument()
    })
})
```

- [ ] **Step 2: Write GrimoirePage test**

```tsx
import {render, screen} from '@testing-library/react'
import {describe, expect, it, vi} from 'vitest'
import {Component as StorytellerGrimoirePage} from './StorytellerGrimoirePage'

vi.mock('react-router', async () => {
    const actual = await vi.importActual<typeof import('react-router')>('react-router')
    return {...actual, useParams: () => ({roomId: '7'})}
})

vi.mock('./clocktowerService', () => ({
    getClocktowerGrimoire: vi.fn().mockResolvedValue({
        roomId: 7,
        phase: {phase: 'FIRST_NIGHT', dayNo: 0, nightNo: 1},
        seats: [],
        markers: [],
        reminders: [],
        pendingTasks: [],
        ruleTraceEnabled: false,
    }),
    getClocktowerNightChecklist: vi.fn().mockResolvedValue({nightNo: 1, nightType: 'FIRST_NIGHT', steps: [], completed: false}),
    submitStorytellerAction: vi.fn(),
}))

describe('StorytellerGrimoirePage', () => {
    it('renders grimoire controls', async () => {
        render(<StorytellerGrimoirePage/>)
        expect(await screen.findByText('说书人魔典')).toBeInTheDocument()
        expect(screen.getByText('夜晚顺序')).toBeInTheDocument()
    })
})
```

- [ ] **Step 3: Run tests and verify failure**

```bash
cd /Users/mario/SelfProject/CyberMario/fe
bun run test -- GameRoomPage.test.tsx StorytellerGrimoirePage.test.tsx
```

Expected: FAIL because pages do not exist.

- [ ] **Step 4: Implement routes and page shells**

Add routes:

```tsx
{path: 'clocktower/rooms/:roomId/play', lazy: () => import('../modules/clocktower/GameRoomPage')},
{path: 'clocktower/rooms/:roomId/grimoire', lazy: () => import('../modules/clocktower/StorytellerGrimoirePage')},
{path: 'clocktower/replays/:roomId', lazy: () => import('../modules/clocktower/ReplayPage')},
```

Implement:

- `GameRoomPage`: load view, subscribe stream, render event timeline and action buttons.
- `StorytellerGrimoirePage`: load grimoire and checklist, render marker controls and phase advance button.
- `ReplayPage`: load replay and event timeline.

- [ ] **Step 5: Run tests and typecheck**

```bash
cd /Users/mario/SelfProject/CyberMario/fe
bun run test -- GameRoomPage.test.tsx StorytellerGrimoirePage.test.tsx
bun run typecheck
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add fe/src/app/routes.tsx fe/src/modules/clocktower
git commit -m "feat(clocktower): add game and grimoire pages"
```

---

### Task 20: Full Phase 1 Validation And Documentation Update

**Files:**

- Modify: `README.md`
- Modify: `FEATURE_CHECKLIST.md`
- Modify: `docs/superpowers/specs/2026-06-17-clocktower-agent-platform-design.md` only if Phase 1 scope changed during
  implementation.

- [ ] **Step 1: Run backend Clocktower tests**

```bash
cd /Users/mario/SelfProject/CyberMario/be
mvn -Dmaven.build.cache.enabled=false -Djava.version=21 -Dtest='top.egon.mario.clocktower.**' test
```

Expected: PASS.

- [ ] **Step 2: Run backend full tests**

```bash
cd /Users/mario/SelfProject/CyberMario/be
mvn -Dmaven.build.cache.enabled=false -Djava.version=21 test
```

Expected: PASS. If unrelated existing tests fail, record exact failing class and stack trace in the final implementation
report.

- [ ] **Step 3: Run frontend tests**

```bash
cd /Users/mario/SelfProject/CyberMario/fe
bun run typecheck
bun run test
```

Expected: PASS.

- [ ] **Step 4: Update docs**

Add a short Clocktower Phase 1 feature bullet to `README.md`:

```markdown
- Clocktower Phase 1 game support with script data, board validation, rooms, grimoire, player actions, event stream, and basic replay.
```

Add a corresponding section to `FEATURE_CHECKLIST.md`:

```markdown
## Clocktower Phase 1

- [x] Script, role, night-order, term, and jinx query APIs.
- [x] Board generation and validation.
- [x] Room, seat, start-game, and role assignment flow.
- [x] Storyteller grimoire, markers, and night checklist.
- [x] Player view, actions, nomination, vote, execution basics.
- [x] SSE room event stream and basic replay.
- [x] RBAC resource provider and frontend route shells.
```

- [ ] **Step 5: Commit**

```bash
git add README.md FEATURE_CHECKLIST.md docs/superpowers/specs/2026-06-17-clocktower-agent-platform-design.md
git commit -m "docs(clocktower): document phase one core"
```

---

## Self-Review Notes

Spec coverage:

- Platform product boundary: covered by Tasks 1-20.
- Drools core: covered by Task 4 for Phase 1 board validation and night/action integration points; full role automation
  is intentionally a separate plan.
- API and DTO design: covered by Tasks 3, 5, 7-14, and 17.
- Frontend module: covered by Tasks 17-19.
- RBAC: covered by Task 15.
- Validation: covered by Task 20.

Phase 1 exclusions requiring follow-up plans:

- Full role-by-role automation for all three scripts.
- Agent assign/step/autoplay.
- Rule query with RAG citations.
- Admin rule data maintenance UI.
- Advanced replay analytics and rule trace UI.
