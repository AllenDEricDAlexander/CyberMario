# Clocktower Actor Agent Domain Design

**Date:** 2026-07-08

**Status:** User-approved approach A. This spec covers only task 02 from `docs/clocktower_agent_tasks`: backend Actor / Agent PO, Repository, DTO, constants, and basic service. Implementation must be planned separately before code changes.

---

## 1. Background

Task 01 added the database foundation for first-class Clocktower actors:

- `clocktower_actor`
- `clocktower_agent_profile`
- `clocktower_agent_instance`
- `clocktower_room_seat.actor_id / actor_type / agent_instance_id`
- `clocktower_game_seat.actor_id / actor_type / agent_instance_id`

Task 02 connects that schema to Java code. The current room creation path still creates only human-oriented open seats, and game start still rejects seats that do not have a real `user_id`. This task does not change those flows. Its purpose is to provide a focused domain layer that later tasks can call instead of duplicating Agent row creation and JSON metadata assembly in room or game services.

## 2. Goals

1. Add Java mappings for the Actor / Agent tables created by task 01.
2. Add repository methods needed by later room creation, game start, and Agent runtime work.
3. Add a small service for creating an Agent Actor + Agent Instance for an existing room seat.
4. Backfill the Java seat models with the new actor fields.
5. Keep existing human room and game behavior unchanged.
6. Add focused tests for JPA mappings, repository access, service creation, seat field persistence, and metadata JSON.

## 3. Non-Goals

This task does not:

- Consume `ClocktowerRoomCreateRequest.agentSeatCount`.
- Change `ClocktowerRoomLobbyServiceImpl#createRoom`.
- Change `ClocktowerGameLifecycleServiceImpl#startGame` or `validateStartSeats`.
- Copy Agent fields from room seat to game seat during start.
- Add Agent seats to IM conversations.
- Implement Agent runtime, task queues, automatic decisions, memory, LLM policy, public mic, nominations, votes, or night tasks.
- Add frontend changes or API endpoints.
- Add a new Flyway migration.

## 4. Selected Approach

Use strict approach A from the task document: add a new `top.egon.mario.clocktower.agent` package with constants, PO classes, repositories, DTOs, and `ClocktowerAgentSeatService`.

This keeps the Clocktower Agent domain separate from the generic `top.egon.mario.agent` runtime package. It also avoids pushing Agent creation details into room or game services before task 03 and task 04 define those lifecycle call sites.

Alternative approaches were rejected:

- Mixing Agent classes into `clocktower.room` or `clocktower.game` would blur ownership and make later runtime work harder to isolate.
- Adding only PO and repositories would force later services to duplicate Agent metadata and instance creation rules.
- Changing room creation or game start now would cross into task 03 and task 04.

## 5. Package Structure

Add:

```text
be/src/main/java/top/egon/mario/clocktower/agent
  constant
    ClocktowerActorType.java
    ClocktowerAgentStatus.java
    ClocktowerAgentAutoMode.java
  dto
    ClocktowerAgentInstanceView.java
    ClocktowerAgentSeatDescriptor.java
  po
    ClocktowerActorPo.java
    ClocktowerAgentProfilePo.java
    ClocktowerAgentInstancePo.java
  repository
    ClocktowerActorRepository.java
    ClocktowerAgentProfileRepository.java
    ClocktowerAgentInstanceRepository.java
  service
    ClocktowerAgentSeatService.java
    impl/ClocktowerAgentSeatServiceImpl.java
```

This follows the existing Clocktower module layout: feature package first, then `dto`, `po`, `repository`, `service`, and optional `impl`.

## 6. Constants

The task recommends String constants rather than Java enum converters. That matches many of the current room/game status fields, which are stored as strings and only validate at service boundaries.

Add final utility classes:

- `ClocktowerActorType`: `HUMAN`, `AGENT`, `STORYTELLER`, `SYSTEM`
- `ClocktowerAgentStatus`: `ACTIVE`, `PAUSED`, `ARCHIVED`
- `ClocktowerAgentAutoMode`: `FULL_AUTO`, `ST_APPROVAL`, `PAUSED`

The values are domain constants only. No JPA enum converter or database check constraint is introduced in this task.

## 7. Persistence Model

All new PO classes extend `BaseAuditablePo` and map only the task 01 columns. JSON metadata fields use the existing Hibernate JSON style:

```java
@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
private String metadataJson = "{}";
```

### 7.1 `ClocktowerActorPo`

Maps `clocktower_actor`:

- `actorType`
- `userId`
- `displayName`
- `status`
- `metadataJson`

Defaults:

- `actorType = AGENT` should be set by the service for Agent creation.
- `status = ACTIVE`
- `metadataJson = "{}"`

### 7.2 `ClocktowerAgentProfilePo`

Maps `clocktower_agent_profile`:

- `name`
- `displayNameTemplate`
- `strategyLevel`
- `talkativeness`
- `deceptionLevel`
- `aggression`
- `riskTolerance`
- `modelProvider`
- `modelName`
- `metadataJson`

The four seeded profiles from task 01 must be readable through the repository.

### 7.3 `ClocktowerAgentInstancePo`

Maps `clocktower_agent_instance`:

- `roomId`
- `gameId`
- `profileId`
- `actorId`
- `roomSeatId`
- `gameSeatId`
- `status`
- `autoMode`
- `metadataJson`

Defaults:

- `status = ACTIVE`
- `autoMode = FULL_AUTO`
- `metadataJson = "{}"`

### 7.4 Seat POs

Add to both `ClocktowerRoomSeatPo` and `ClocktowerGameSeatPo`:

```java
@Column(name = "actor_id")
private Long actorId;

@Column(name = "actor_type", nullable = false, length = 32)
private String actorType = ClocktowerActorType.HUMAN;

@Column(name = "agent_instance_id")
private Long agentInstanceId;
```

Existing human seats remain unchanged in behavior because `userId`, `status`, and existing metadata are still the current runtime source of truth.

## 8. Repositories

Add repositories exactly around current task needs:

### 8.1 `ClocktowerActorRepository`

```java
Optional<ClocktowerActorPo> findByIdAndDeletedFalse(Long id);
```

### 8.2 `ClocktowerAgentProfileRepository`

```java
Optional<ClocktowerAgentProfilePo> findFirstByNameAndDeletedFalse(String name);
List<ClocktowerAgentProfilePo> findByDeletedFalseOrderByIdAsc();
```

### 8.3 `ClocktowerAgentInstanceRepository`

```java
List<ClocktowerAgentInstancePo> findByRoomIdAndDeletedFalseOrderByIdAsc(Long roomId);
List<ClocktowerAgentInstancePo> findByGameIdAndDeletedFalseOrderByIdAsc(Long gameId);
Optional<ClocktowerAgentInstancePo> findByIdAndDeletedFalse(Long id);
Optional<ClocktowerAgentInstancePo> findByGameSeatIdAndDeletedFalse(Long gameSeatId);
Optional<ClocktowerAgentInstancePo> findByActorIdAndDeletedFalse(Long actorId);
```

No broad `JpaSpecificationExecutor` is needed for this task.

## 9. DTOs

Add small record DTOs for internal service consumers:

### 9.1 `ClocktowerAgentSeatDescriptor`

Represents the result of creating or resolving an Agent room seat:

- `actorId`
- `agentInstanceId`
- `roomId`
- `roomSeatId`
- `seatNo`
- `displayName`
- `roleCode`
- `profileName`
- `autoMode`
- `metadata`

### 9.2 `ClocktowerAgentInstanceView`

Represents a joined view of Agent Instance + Actor + Profile without exposing entity mutation:

- `instanceId`
- `actorId`
- `roomId`
- `gameId`
- `roomSeatId`
- `gameSeatId`
- `displayName`
- `profileName`
- `status`
- `autoMode`

The first implementation can build this DTO where it is useful, but no external web contract is added.

## 10. Service Contract

Add:

```java
public interface ClocktowerAgentSeatService {
    ClocktowerAgentSeatDescriptor createAgentForRoomSeat(
            Long roomId,
            Long roomSeatId,
            int seatNo,
            String displayName,
            String roleCode,
            String profileName,
            String autoMode
    );

    boolean isSystemAgentSeat(String actorType, Long agentInstanceId, Map<String, Object> metadata);

    List<ClocktowerAgentInstancePo> agentsOfRoom(Long roomId);

    List<ClocktowerAgentInstancePo> agentsOfGame(Long gameId);
}
```

### 10.1 Creation Flow

`createAgentForRoomSeat` will:

1. Validate `roomId`, `roomSeatId`, and `seatNo`.
2. Resolve `profileName`, defaulting blank values to `balanced`.
3. Resolve `autoMode`, defaulting blank values to `FULL_AUTO`.
4. Load the target `ClocktowerRoomSeatPo`.
5. Reject missing seats or seats from a different room.
6. Reject seats that already have a different `agentInstanceId`.
7. Create `ClocktowerActorPo` with `actorType = AGENT`, `userId = null`, resolved display name, `status = ACTIVE`, and Agent metadata.
8. Create `ClocktowerAgentInstancePo` linked to room, profile, actor, and room seat.
9. Update the room seat:
   - `actorId = actor.id`
   - `actorType = AGENT`
   - `agentInstanceId = instance.id`
   - `userId = null`
   - `displayName = resolvedDisplayName`
   - `roleCode = roleCode`
   - `status = OCCUPIED`
   - `metadataJson = agent room seat metadata`
10. Return `ClocktowerAgentSeatDescriptor`.

The method is transactional. It does not call IM services and does not create room members.

### 10.2 Defaults

Default values:

- blank `profileName` -> `balanced`
- blank `autoMode` -> `FULL_AUTO`
- blank `displayName` -> render profile `display_name_template` with `{n}` replaced by `seatNo`

If the profile template is blank, fall back to `Agent <seatNo>`.

### 10.3 Metadata

Agent room seat metadata must be assembled in one place:

```json
{
  "ready": true,
  "actorType": "AGENT",
  "agent": true,
  "agentSeat": true,
  "systemManaged": true,
  "agentPolicy": "HEURISTIC_V0",
  "autoMode": "FULL_AUTO",
  "createdBy": "agentSeatCount"
}
```

Human seat metadata remains untouched unless this service is explicitly creating an Agent seat.

`isSystemAgentSeat` must require all of these signals together:

- `actorType = AGENT`
- `agentInstanceId != null`
- `metadata.systemManaged = true`
- `metadata.createdBy = agentSeatCount`

It must not rely on only `metadata.agent = true`.

## 11. Error Handling

Use existing `ClocktowerException` with concise error codes. Proposed codes:

- `CLOCKTOWER_AGENT_PROFILE_NOT_FOUND`
- `CLOCKTOWER_AGENT_ROOM_SEAT_NOT_FOUND`
- `CLOCKTOWER_AGENT_ROOM_SEAT_MISMATCH`
- `CLOCKTOWER_AGENT_SEAT_ALREADY_BOUND`
- `CLOCKTOWER_AGENT_AUTO_MODE_INVALID`

Do not add a new exception type for this narrow package.

## 12. Design Pattern Consideration

Use a Domain Service backed by repositories.

This is enough because the task has a real domain operation: create a consistent Agent Actor + Agent Instance + seat binding in one transaction. A simple repository-only implementation would scatter that invariant across later room and game services.

Do not introduce Strategy, Factory Method, or Builder yet:

- Strategy belongs to later Agent decision policy tasks.
- Factory would be thin ceremony around one creation path.
- Builder would not simplify the current PO construction.

The service remains small, direct, and aligned with existing Spring service patterns.

## 13. Tests

Add focused tests under `be/src/test/java/top/egon/mario/clocktower/agent`.

Recommended coverage:

1. `ClocktowerJpaMappingTests` recognizes the new PO classes as managed entities.
2. New fields on `ClocktowerRoomSeatPo` and `ClocktowerGameSeatPo` persist and reload.
3. `ClocktowerAgentProfileRepository` reads the four seeded profiles in id order.
4. `ClocktowerAgentSeatServiceImpl#createAgentForRoomSeat` creates:
   - one `clocktower_actor`
   - one `clocktower_agent_instance`
   - a room seat with Agent actor fields
   - metadata matching the task contract
5. `isSystemAgentSeat` rejects incomplete or spoofed metadata.
6. Existing human seat behavior stays intact in existing room/game tests.

Validation command:

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerJpaMappingTests,ClocktowerAgentSeatServiceTests test
```

If the focused suite passes, run any touched Clocktower room/game test class that proves no human-seat regression.

## 14. Execution Boundary

Implement this task in the existing feature worktree:

```text
/Users/mario/SelfProject/CyberMario/.worktrees/clocktower-actor-agent-foundation
```

The main checkout currently has staged `docs/clocktower_agent_tasks/*` files from the user. They are task source documents and must remain untouched by this implementation unless the user explicitly asks otherwise.
