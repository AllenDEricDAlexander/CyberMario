# Clocktower Actor Agent Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the Clocktower Actor / Agent database foundation from task 01 without changing runtime behavior.

**Architecture:** This is a schema-only change with focused migration validation. One new Flyway migration introduces Actor, Agent Profile, Agent Instance, and seat identity fields; one test class verifies the SQL contract and H2 PostgreSQL-mode migration behavior. Java PO/Repository/service mapping remains reserved for task 02.

**Tech Stack:** Spring Boot, Flyway, PostgreSQL-compatible SQL, H2 PostgreSQL mode for migration tests, JUnit 5, AssertJ, Spring `JdbcTemplate`.

---

## Scope Check

The confirmed scope is strict approach A from `docs/superpowers/specs/2026-07-08-clocktower-actor-agent-foundation-design.md`.

This plan implements only task 01:

- Add exactly one new Flyway migration file.
- Add focused migration tests.
- Do not add Java PO, Repository, DTO, service, frontend, or runtime behavior.
- Do not edit existing migration files.

## Implementation Compatibility Note

H2 PostgreSQL mode does not parse PostgreSQL partial indexes such as:

```sql
create unique index uk_name on table_name(name) where deleted = false;
```

The project runs `classpath:db/migration` through H2 in existing migration tests, so the migration must stay cross-compatible. For active-row uniqueness in this task, use unique constraints on `(name, deleted)` and `(actor_id, deleted)`. This keeps non-deleted rows unique and avoids adding a second PostgreSQL-only migration file.

## File Structure

Create:

- `be/src/main/resources/db/migration/V32__clocktower_actor_agent_foundation.sql`
  - Owns all task 01 schema changes.
  - Creates `clocktower_actor`, `clocktower_agent_profile`, and `clocktower_agent_instance`.
  - Extends `clocktower_room_seat` and `clocktower_game_seat`.
  - Seeds exactly four default agent profiles.

Modify:

- `be/src/test/java/top/egon/mario/clocktower/ClocktowerSchemaMigrationTests.java`
  - Adds a SQL content contract test.
  - Adds a Flyway/H2 migration smoke test for human and agent seat shapes.
  - Adds small local helpers for the new test only.

## Task 1: Actor Agent Foundation Migration

**Files:**

- Create: `be/src/main/resources/db/migration/V32__clocktower_actor_agent_foundation.sql`
- Modify: `be/src/test/java/top/egon/mario/clocktower/ClocktowerSchemaMigrationTests.java`

- [ ] **Step 1: Add the migration path constant**

In `ClocktowerSchemaMigrationTests`, add this constant below `ROOM_IM_GAME_REFACTOR_MIGRATION`:

```java
    private static final Path ACTOR_AGENT_FOUNDATION_MIGRATION = Path.of(
            "src/main/resources/db/migration/V32__clocktower_actor_agent_foundation.sql");
```

- [ ] **Step 2: Write the SQL contract test**

In `ClocktowerSchemaMigrationTests`, add this test after `roomImGameRefactorMigrationCreatesClocktowerGameTables`:

```java
    @Test
    void actorAgentFoundationMigrationCreatesActorAgentTablesAndSeatColumns() throws IOException {
        assertThat(Files.exists(ACTOR_AGENT_FOUNDATION_MIGRATION)).isTrue();

        String sql = Files.readString(ACTOR_AGENT_FOUNDATION_MIGRATION);
        String lowerSql = sql.toLowerCase();

        assertThat(sql).contains("CREATE TABLE clocktower_actor");
        assertThat(sql).contains("CREATE TABLE clocktower_agent_profile");
        assertThat(sql).contains("CREATE TABLE clocktower_agent_instance");
        assertThat(sql).contains("ALTER TABLE clocktower_room_seat");
        assertThat(sql).contains("ALTER TABLE clocktower_game_seat");
        assertThat(sql).contains("ADD COLUMN actor_id BIGINT");
        assertThat(sql).contains("ADD COLUMN actor_type VARCHAR(32) NOT NULL DEFAULT 'HUMAN'");
        assertThat(sql).contains("ADD COLUMN agent_instance_id BIGINT");
        assertThat(sql).contains("created_by BIGINT");
        assertThat(sql).contains("updated_by BIGINT");
        assertThat(sql).contains("version BIGINT NOT NULL DEFAULT 0");
        assertThat(sql).contains("CONSTRAINT uk_clocktower_agent_profile_name UNIQUE (name, deleted)");
        assertThat(sql).contains("CONSTRAINT uk_clocktower_agent_instance_actor UNIQUE (actor_id, deleted)");
        assertThat(sql).contains("idx_clocktower_room_seat_actor");
        assertThat(sql).contains("idx_clocktower_room_seat_agent");
        assertThat(sql).contains("idx_clocktower_game_seat_actor");
        assertThat(sql).contains("idx_clocktower_game_seat_agent");
        assertThat(sql).contains("('balanced', 'Agent {n}', 'NORMAL', 50, 50, 50, 50)");
        assertThat(sql).contains("('quiet', 'Agent {n}', 'QUIET', 25, 40, 35, 40)");
        assertThat(sql).contains("('aggressive', 'Agent {n}', 'AGGRESSIVE', 65, 60, 75, 60)");
        assertThat(sql).contains("('careful', 'Agent {n}', 'CAREFUL', 45, 35, 35, 25)");
        assertThat(lowerSql).doesNotContain(" check ");
        assertThat(lowerSql).doesNotContain("where deleted = false");
        assertThat(sql).doesNotContain("uk_clocktower_actor_user");
    }
```

- [ ] **Step 3: Write the Flyway/H2 smoke test**

In `ClocktowerSchemaMigrationTests`, add this test after the SQL contract test:

```java
    @Test
    void actorAgentFoundationMigrationAppliesAndSupportsHumanAndAgentSeats() {
        JdbcTemplate jdbcTemplate = migratedJdbcTemplate("clocktower_actor_agent_foundation_%s"
                .formatted(UUID.randomUUID()));

        Integer profileCount = jdbcTemplate.queryForObject("""
                select count(*)
                from clocktower_agent_profile
                where name in ('balanced', 'quiet', 'aggressive', 'careful')
                """, Integer.class);
        assertThat(profileCount).isEqualTo(4);

        jdbcTemplate.update("""
                insert into clocktower_actor (id, actor_type, user_id, display_name)
                values (92001, 'AGENT', null, 'Agent 1')
                """);
        jdbcTemplate.update("""
                insert into clocktower_agent_instance (id, room_id, profile_id, actor_id, status, auto_mode)
                values (93001, 91001,
                        (select id from clocktower_agent_profile where name = 'balanced'),
                        92001, 'ACTIVE', 'FULL_AUTO')
                """);
        jdbcTemplate.update("""
                insert into clocktower_room_seat
                    (id, room_id, seat_no, user_id, display_name, role_code, status, actor_type)
                values
                    (94001, 91001, 1, 101, 'Human Player', 'EMPATH', 'OCCUPIED', 'HUMAN')
                """);
        jdbcTemplate.update("""
                insert into clocktower_room_seat
                    (id, room_id, seat_no, user_id, display_name, role_code, status,
                     actor_id, actor_type, agent_instance_id)
                values
                    (94002, 91001, 2, null, 'Agent 1', 'CHEF', 'OCCUPIED',
                     92001, 'AGENT', 93001)
                """);
        jdbcTemplate.update("""
                insert into clocktower_game
                    (id, room_id, game_no, script_code, status, phase, board_snapshot_json)
                values
                    (95001, 91001, 1, 'TROUBLE_BREWING', 'RUNNING', 'FIRST_NIGHT', '{}')
                """);
        jdbcTemplate.update("""
                insert into clocktower_game_seat
                    (id, game_id, room_seat_id, seat_no, user_id, display_name, role_code,
                     status, actor_type)
                values
                    (96001, 95001, 94001, 1, 101, 'Human Player', 'EMPATH',
                     'ACTIVE', 'HUMAN')
                """);
        jdbcTemplate.update("""
                insert into clocktower_game_seat
                    (id, game_id, room_seat_id, seat_no, user_id, display_name, role_code,
                     status, actor_id, actor_type, agent_instance_id)
                values
                    (96002, 95001, 94002, 2, null, 'Agent 1', 'CHEF',
                     'ACTIVE', 92001, 'AGENT', 93001)
                """);

        List<Map<String, Object>> roomSeats = jdbcTemplate.queryForList("""
                select seat_no, user_id, actor_id, actor_type, agent_instance_id
                from clocktower_room_seat
                where room_id = 91001
                order by seat_no
                """);
        assertThat(roomSeats).hasSize(2);
        assertThat(roomSeats.get(0).get("actor_type")).isEqualTo("HUMAN");
        assertThat(longValue(roomSeats.get(0), "user_id")).isEqualTo(101L);
        assertThat(roomSeats.get(0).get("actor_id")).isNull();
        assertThat(roomSeats.get(0).get("agent_instance_id")).isNull();
        assertThat(roomSeats.get(1).get("actor_type")).isEqualTo("AGENT");
        assertThat(roomSeats.get(1).get("user_id")).isNull();
        assertThat(longValue(roomSeats.get(1), "actor_id")).isEqualTo(92001L);
        assertThat(longValue(roomSeats.get(1), "agent_instance_id")).isEqualTo(93001L);

        List<Map<String, Object>> gameSeats = jdbcTemplate.queryForList("""
                select seat_no, user_id, actor_id, actor_type, agent_instance_id
                from clocktower_game_seat
                where game_id = 95001
                order by seat_no
                """);
        assertThat(gameSeats).hasSize(2);
        assertThat(gameSeats.get(0).get("actor_type")).isEqualTo("HUMAN");
        assertThat(longValue(gameSeats.get(0), "user_id")).isEqualTo(101L);
        assertThat(gameSeats.get(1).get("actor_type")).isEqualTo("AGENT");
        assertThat(gameSeats.get(1).get("user_id")).isNull();
        assertThat(longValue(gameSeats.get(1), "actor_id")).isEqualTo(92001L);
        assertThat(longValue(gameSeats.get(1), "agent_instance_id")).isEqualTo(93001L);

        String nullable = jdbcTemplate.queryForObject("""
                select is_nullable
                from information_schema.columns
                where table_name = 'im_conversation_member'
                  and column_name = 'user_id'
                """, String.class);
        assertThat(nullable).isEqualTo("NO");
    }
```

- [ ] **Step 4: Add test helpers**

In `ClocktowerSchemaMigrationTests`, add these helpers near the end of the class:

```java
    private JdbcTemplate migratedJdbcTemplate(String databaseName) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("""
                jdbc:h2:mem:%s;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1
                """.formatted(databaseName).trim());
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        return new JdbcTemplate(dataSource);
    }

    private static Long longValue(Map<String, Object> row, String column) {
        Object value = row.get(column);
        return value == null ? null : ((Number) value).longValue();
    }
```

- [ ] **Step 5: Add the missing import**

In `ClocktowerSchemaMigrationTests`, add:

```java
import java.util.Map;
```

The file already imports `List` and `UUID`.

- [ ] **Step 6: Run the focused test and confirm it fails before the migration exists**

Run:

```bash
cd be && ./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerSchemaMigrationTests#actorAgentFoundationMigrationCreatesActorAgentTablesAndSeatColumns test
```

Expected result:

```text
expected: true
 but was: false
```

The failure proves the SQL contract test is exercising the missing migration.

- [ ] **Step 7: Create the migration file**

Create `be/src/main/resources/db/migration/V32__clocktower_actor_agent_foundation.sql` with this exact content:

```sql
CREATE TABLE clocktower_actor (
    id BIGSERIAL PRIMARY KEY,
    actor_type VARCHAR(32) NOT NULL,
    user_id BIGINT,
    display_name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_clocktower_actor_type ON clocktower_actor (actor_type, deleted);
CREATE INDEX idx_clocktower_actor_user ON clocktower_actor (user_id, deleted);

CREATE TABLE clocktower_agent_profile (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    display_name_template VARCHAR(128) NOT NULL,
    strategy_level VARCHAR(32) NOT NULL DEFAULT 'NORMAL',
    talkativeness INTEGER NOT NULL DEFAULT 50,
    deception_level INTEGER NOT NULL DEFAULT 50,
    aggression INTEGER NOT NULL DEFAULT 50,
    risk_tolerance INTEGER NOT NULL DEFAULT 50,
    model_provider VARCHAR(64),
    model_name VARCHAR(128),
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_clocktower_agent_profile_name UNIQUE (name, deleted)
);

INSERT INTO clocktower_agent_profile
    (name, display_name_template, strategy_level, talkativeness, deception_level, aggression, risk_tolerance)
VALUES
    ('balanced', 'Agent {n}', 'NORMAL', 50, 50, 50, 50),
    ('quiet', 'Agent {n}', 'QUIET', 25, 40, 35, 40),
    ('aggressive', 'Agent {n}', 'AGGRESSIVE', 65, 60, 75, 60),
    ('careful', 'Agent {n}', 'CAREFUL', 45, 35, 35, 25);

CREATE TABLE clocktower_agent_instance (
    id BIGSERIAL PRIMARY KEY,
    room_id BIGINT NOT NULL,
    game_id BIGINT,
    profile_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    room_seat_id BIGINT,
    game_seat_id BIGINT,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    auto_mode VARCHAR(32) NOT NULL DEFAULT 'FULL_AUTO',
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_clocktower_agent_instance_actor UNIQUE (actor_id, deleted)
);

CREATE INDEX idx_clocktower_agent_instance_room ON clocktower_agent_instance (room_id, deleted);
CREATE INDEX idx_clocktower_agent_instance_game ON clocktower_agent_instance (game_id, deleted);

ALTER TABLE clocktower_room_seat
    ADD COLUMN actor_id BIGINT,
    ADD COLUMN actor_type VARCHAR(32) NOT NULL DEFAULT 'HUMAN',
    ADD COLUMN agent_instance_id BIGINT;

CREATE INDEX idx_clocktower_room_seat_actor ON clocktower_room_seat (actor_id);
CREATE INDEX idx_clocktower_room_seat_agent ON clocktower_room_seat (agent_instance_id);

ALTER TABLE clocktower_game_seat
    ADD COLUMN actor_id BIGINT,
    ADD COLUMN actor_type VARCHAR(32) NOT NULL DEFAULT 'HUMAN',
    ADD COLUMN agent_instance_id BIGINT;

CREATE INDEX idx_clocktower_game_seat_actor ON clocktower_game_seat (actor_id);
CREATE INDEX idx_clocktower_game_seat_agent ON clocktower_game_seat (agent_instance_id);
```

- [ ] **Step 8: Run the focused SQL contract test**

Run:

```bash
cd be && ./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerSchemaMigrationTests#actorAgentFoundationMigrationCreatesActorAgentTablesAndSeatColumns test
```

Expected result:

```text
BUILD SUCCESS
```

- [ ] **Step 9: Run the functional migration smoke test**

Run:

```bash
cd be && ./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerSchemaMigrationTests#actorAgentFoundationMigrationAppliesAndSupportsHumanAndAgentSeats test
```

Expected result:

```text
BUILD SUCCESS
```

- [ ] **Step 10: Run the full Clocktower schema migration test class**

Run:

```bash
cd be && ./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerSchemaMigrationTests test
```

Expected result:

```text
BUILD SUCCESS
```

- [ ] **Step 11: Inspect the changed files**

Run:

```bash
git diff -- be/src/main/resources/db/migration/V32__clocktower_actor_agent_foundation.sql be/src/test/java/top/egon/mario/clocktower/ClocktowerSchemaMigrationTests.java
```

Expected checks:

- The diff adds one new migration file.
- No existing migration file is modified.
- The test file only adds task 01 migration coverage and local helpers.
- No Java PO, Repository, service, DTO, or frontend file is changed.

- [ ] **Step 12: Commit task 1**

Run:

```bash
git add be/src/main/resources/db/migration/V32__clocktower_actor_agent_foundation.sql \
    be/src/test/java/top/egon/mario/clocktower/ClocktowerSchemaMigrationTests.java
git commit -m "feat: add clocktower actor agent foundation schema"
```

Expected result:

```text
[branch commit] feat: add clocktower actor agent foundation schema
```

## Final Verification

After task 1 is committed, run:

```bash
git status --short
```

Expected result:

```text
[no entries for be/src/main/resources/db/migration/V32__clocktower_actor_agent_foundation.sql]
[no entries for be/src/test/java/top/egon/mario/clocktower/ClocktowerSchemaMigrationTests.java]
```

If unrelated pre-existing files are staged or untracked, including plan/spec docs or `docs/clocktower_agent_tasks` files, report them separately and do not modify or revert them.

Then run:

```bash
cd be && ./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerSchemaMigrationTests test
```

Expected result:

```text
BUILD SUCCESS
```

## Completion Checklist

- [ ] Exactly one new Flyway migration file was added.
- [ ] Existing migration files were not changed.
- [ ] New tables include project audit columns.
- [ ] Four default profiles were seeded.
- [ ] No DB check constraint was added for `actor_type`.
- [ ] No `HUMAN + user_id` unique constraint was added.
- [ ] `clocktower_room_seat` and `clocktower_game_seat` include `actor_id`, `actor_type`, and `agent_instance_id`.
- [ ] `im_conversation_member.user_id` remains non-null.
- [ ] Focused Clocktower schema migration tests pass.
