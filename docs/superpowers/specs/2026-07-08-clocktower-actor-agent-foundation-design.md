# Clocktower Actor Agent Foundation Design

**Date:** 2026-07-08

**Status:** User-approved approach A. This spec covers only task 01 from `docs/clocktower_agent_tasks`: database foundation and focused validation. Implementation must be planned separately before code changes.

---

## 1. Background

`docs/clocktower_agent_tasks/00_task_index.md` defines the long-term direction for real Agent players in Clocktower. Agent players must not be represented by fake users, negative `user_id` values, or system users. They need to become first-class game actors:

- Human player: `actor_type = HUMAN`, `user_id != null`
- Agent player: `actor_type = AGENT`, `user_id = null`, `agent_instance_id != null`
- Storyteller: still a real user in this phase
- System: reserved for future internal events and automatic actions

The current repository already has the new room/game tables from `V26__create_room_im_clocktower_refactor_schema.sql`, but both `clocktower_room_seat` and `clocktower_game_seat` still identify players primarily through `user_id`. The current room creation, game start, and IM activation code is still human-user oriented. Task 01 creates the database surface that later tasks can safely build on.

## 2. Goals

1. Add a schema-level Actor and Agent foundation without changing runtime behavior.
2. Preserve existing human room and game behavior.
3. Keep existing Flyway migrations immutable by adding exactly one new versioned migration.
4. Make Agent seats representable without requiring a `user_id`.
5. Keep task 01 independent from later service, PO, Repository, frontend, and Agent runtime work.

## 3. Non-Goals

This task does not:

- Add Java PO, Repository, DTO, enum, or service code.
- Consume `agentSeatCount` during room creation.
- Change game start validation.
- Change IM member creation or conversation activation.
- Implement Agent decisions, task queues, memory, LLM policies, night tasks, nominations, votes, or public mic behavior.
- Change frontend types or UI.
- Backfill actors for existing human seats.

## 4. Selected Approach

Use strict approach A: implement only the database migration and SQL/Flyway verification for task 01.

This keeps the task boundary clean. Task 02 owns Java domain mapping. Task 03 owns room creation behavior. Task 04 owns game lifecycle behavior and IM filtering. Pulling those concerns into this task would make the first step larger and harder to review.

## 5. Database Migration

Create one new Flyway migration:

```text
be/src/main/resources/db/migration/V32__clocktower_actor_agent_foundation.sql
```

`V31__create_nutrition_mvp_schema.sql` is currently the newest versioned migration under `classpath:db/migration`, so `V32` is the next version.

### 5.1 Audit Columns

All new tables must follow the project audit shape used by `BaseAuditablePo` and nearby schemas:

```text
created_at timestamp with time zone not null default current_timestamp
updated_at timestamp with time zone not null default current_timestamp
created_by bigint
updated_by bigint
version bigint not null default 0
deleted boolean not null default false
```

The task draft did not include every audit column, but the user confirmed the migration should follow project conventions.

### 5.2 `clocktower_actor`

Add table `clocktower_actor`:

- `id`
- `actor_type`
- `user_id`
- `display_name`
- `status`
- `metadata_json`
- audit columns

Indexes:

- `idx_clocktower_actor_type` on `actor_type`
- `idx_clocktower_actor_user` on `user_id`

No active `HUMAN + user_id` unique constraint will be added in this task. This avoids locking in a human actor reuse policy before task 02 defines service-level creation and lookup behavior.

No database check constraint will be added for `actor_type`. Application-level enum validation can be introduced later with the Java model.

### 5.3 `clocktower_agent_profile`

Add table `clocktower_agent_profile`:

- `id`
- `name`
- `display_name_template`
- `strategy_level`
- `talkativeness`
- `deception_level`
- `aggression`
- `risk_tolerance`
- `model_provider`
- `model_name`
- `metadata_json`
- audit columns

Add one active-name uniqueness rule:

```sql
create unique index uk_clocktower_agent_profile_name
    on clocktower_agent_profile(name)
    where deleted = false;
```

Seed exactly four default profiles:

| name | display_name_template | strategy_level | talkativeness | deception_level | aggression | risk_tolerance |
|---|---|---|---:|---:|---:|---:|
| `balanced` | `Agent {n}` | `NORMAL` | 50 | 50 | 50 | 50 |
| `quiet` | `Agent {n}` | `QUIET` | 25 | 40 | 35 | 40 |
| `aggressive` | `Agent {n}` | `AGGRESSIVE` | 65 | 60 | 75 | 60 |
| `careful` | `Agent {n}` | `CAREFUL` | 45 | 35 | 35 | 25 |

### 5.4 `clocktower_agent_instance`

Add table `clocktower_agent_instance`:

- `id`
- `room_id`
- `game_id`
- `profile_id`
- `actor_id`
- `room_seat_id`
- `game_seat_id`
- `status`
- `auto_mode`
- `metadata_json`
- audit columns

Indexes:

- `idx_clocktower_agent_instance_room` on `room_id`
- `idx_clocktower_agent_instance_game` on `game_id`
- `uk_clocktower_agent_instance_actor` unique on `actor_id` where `deleted = false`

`auto_mode` values are data conventions only in this task:

- `FULL_AUTO`
- `ST_APPROVAL`
- `PAUSED`

No runtime behavior is attached to these values yet.

### 5.5 Extend `clocktower_room_seat`

Alter `clocktower_room_seat`:

```sql
alter table clocktower_room_seat
    add column actor_id bigint null,
    add column actor_type varchar(32) not null default 'HUMAN',
    add column agent_instance_id bigint null;
```

Indexes:

- `idx_clocktower_room_seat_actor` on `actor_id`
- `idx_clocktower_room_seat_agent` on `agent_instance_id`

Existing human seats remain compatible:

- `user_id` remains unchanged
- `actor_type = HUMAN`
- `actor_id = null`
- `agent_instance_id = null`

Agent seats become representable later as:

- `user_id = null`
- `actor_type = AGENT`
- `actor_id != null`
- `agent_instance_id != null`

### 5.6 Extend `clocktower_game_seat`

Alter `clocktower_game_seat`:

```sql
alter table clocktower_game_seat
    add column actor_id bigint null,
    add column actor_type varchar(32) not null default 'HUMAN',
    add column agent_instance_id bigint null;
```

Indexes:

- `idx_clocktower_game_seat_actor` on `actor_id`
- `idx_clocktower_game_seat_agent` on `agent_instance_id`

The existing `user_id` based queries must continue to work for human seats.

## 6. Compatibility Rules

1. Existing room and game rows do not need an `actor_id`.
2. Existing human seats keep their current `user_id`.
3. The migration must not change `uk_clocktower_room_seat_room_user`.
4. Agent seats are not inserted by this task, so no IM behavior changes.
5. `im_conversation_member.user_id not null` remains unchanged. Later lifecycle work must filter Agent seats before calling IM activation.

## 7. Design Pattern Consideration

This task is schema-only, so introducing Strategy, Factory, Adapter, or Domain Service code here would be premature.

The relevant design boundary is a migration contract:

- The schema introduces stable extension points for later Actor and Agent services.
- Application-level validation remains the right place for actor invariants until the service model is implemented.
- Avoiding DB check constraints and human uniqueness constraints is intentional because the human actor lifecycle is not defined in task 01.

The later implementation can use a Factory or Domain Service to create Actor and Agent Instance rows, but that belongs to task 02 or task 03.

## 8. Validation Plan

Add focused migration coverage in `ClocktowerSchemaMigrationTests`:

1. Assert that `V32__clocktower_actor_agent_foundation.sql` exists and contains the three new tables, seat extensions, indexes, audit columns, and four profile seeds.
2. Run Flyway over `classpath:db/migration` on H2 PostgreSQL mode to confirm the new migration applies after existing migrations.
3. Insert a human-style `clocktower_room_seat` with `user_id != null`, `actor_type = HUMAN`, and no actor or agent instance.
4. Insert an agent-style `clocktower_room_seat` with `user_id = null`, `actor_type = AGENT`, `actor_id != null`, and `agent_instance_id != null`.
5. Insert a human-style and an agent-style `clocktower_game_seat` with the same identity patterns.
6. Assert that `im_conversation_member.user_id` remains non-null by attempting or inspecting schema metadata.
7. Assert that exactly four default agent profiles are present after migration.

Repository smoke tests are intentionally not part of task 01 because PO and Repository classes are task 02.

Recommended command after implementation:

```bash
cd be && ./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerSchemaMigrationTests test
```

## 9. Completion Criteria

Task 01 is complete when:

- Exactly one new Flyway migration file is added under `be/src/main/resources/db/migration`.
- No existing migration file is modified.
- The new migration creates Actor and Agent foundation tables with project audit fields.
- `clocktower_room_seat` and `clocktower_game_seat` support `actor_id`, `actor_type`, and `agent_instance_id`.
- Four default profiles are seeded.
- No database check constraint is added for actor type.
- No active `HUMAN + user_id` uniqueness rule is added.
- Focused migration tests pass.

## 10. User Confirmed Decisions

The user confirmed:

1. Use strict approach A.
2. Follow project audit-field conventions.
3. Seed four default profiles.
4. Do not add DB check constraints.
5. Do not add a `HUMAN + user_id` unique constraint.
