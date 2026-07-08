# Clocktower Public Mic Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement task 05: add a game-level public mic session with one round-robin speaking pass, a five-minute grab-mic window, one active holder at a time, storyteller controls, and a `requireCanSpeak(gameId, actorGameSeatId)` guard for task 06.

**Architecture:** Add an independent `top.egon.mario.clocktower.game.mic` backend module. Persistence owns session and turn state. A focused domain service owns transitions, timeout refresh, permission checks, game-event emission, and DTO mapping. A WebFlux controller exposes the mic API through the existing blocking support. Existing old room action/chat flows are not modified.

**Tech Stack:** Spring Boot, Spring Data JPA, Flyway, PostgreSQL-compatible SQL, H2 PostgreSQL mode migration tests, WebFlux controllers, JUnit 5, AssertJ.

---

## Scope Check

This plan implements the approved approach A from `docs/superpowers/specs/2026-07-08-clocktower-public-mic-design.md`.

Implement:

- Exactly one new Flyway migration: `be/src/main/resources/db/migration/V33__clocktower_public_mic.sql`.
- New mic PO, repository, DTO, properties, service, and controller classes under `clocktower/game/mic`.
- RBAC provider entries for mic read, player actions, and storyteller actions.
- Tests for schema, service transitions, guard behavior, and RBAC mapping.

Do not implement:

- New game action executor.
- Old `/api/clocktower/rooms/{roomId}/actions` public speech wiring.
- Agent automatic speech / Agent automatic grab decisions.
- Frontend mic UI.
- Nomination, vote, execution cutover, or old flow replacement.
- Browser/app startup verification.

## Design Pattern Note

Considered patterns:

- State pattern: rejected because the state graph is small and stable: `ROUND_ROBIN -> GRAB_MIC -> CLOSED`.
- Strategy pattern: rejected because task 05 has one ordering policy: seat-order round-robin, then first-come human grab.
- Domain Service: selected. The service centralizes transition rules, locks, permissions, timeout refresh, event emission, and speak-guard behavior while matching existing Clocktower service style.

Keep helper methods shallow and direct. Do not create an inheritance hierarchy or split small readable methods only for formality.

## Compatibility Note

The approved design requested PostgreSQL partial indexes for active rows. Existing project migration tests run all Flyway migrations under H2 PostgreSQL mode, and earlier task 01 deliberately avoided partial indexes for that reason. For task 05:

- Enforce one session per active `(game_id, day_no)` with a standard unique constraint on `(game_id, day_no, deleted)`.
- Enforce one active turn at the service layer while holding the locked game/session.
- Add normal lookup indexes for session status and turn order.
- Do not add PostgreSQL-only partial-index syntax unless H2 validation is updated separately.

This preserves the behavior without adding a second migration or breaking existing migration validation.

## File Structure

Create:

- `be/src/main/resources/db/migration/V33__clocktower_public_mic.sql`
- `be/src/main/java/top/egon/mario/clocktower/game/mic/po/ClocktowerGamePublicMicSessionPo.java`
- `be/src/main/java/top/egon/mario/clocktower/game/mic/po/ClocktowerGamePublicMicTurnPo.java`
- `be/src/main/java/top/egon/mario/clocktower/game/mic/repository/ClocktowerGamePublicMicSessionRepository.java`
- `be/src/main/java/top/egon/mario/clocktower/game/mic/repository/ClocktowerGamePublicMicTurnRepository.java`
- `be/src/main/java/top/egon/mario/clocktower/game/mic/config/ClocktowerPublicMicProperties.java`
- `be/src/main/java/top/egon/mario/clocktower/game/mic/dto/ClocktowerMicSessionView.java`
- `be/src/main/java/top/egon/mario/clocktower/game/mic/dto/ClocktowerMicTurnView.java`
- `be/src/main/java/top/egon/mario/clocktower/game/mic/dto/ClocktowerMicExtendRequest.java`
- `be/src/main/java/top/egon/mario/clocktower/game/mic/service/ClocktowerPublicMicService.java`
- `be/src/main/java/top/egon/mario/clocktower/game/mic/service/impl/ClocktowerPublicMicServiceImpl.java`
- `be/src/main/java/top/egon/mario/clocktower/game/mic/web/ClocktowerPublicMicController.java`
- `be/src/test/java/top/egon/mario/clocktower/game/mic/ClocktowerPublicMicServiceTests.java`

Modify:

- `be/src/test/java/top/egon/mario/clocktower/ClocktowerSchemaMigrationTests.java`
- `be/src/main/java/top/egon/mario/clocktower/resource/ClocktowerRbacResourceProvider.java`
- `be/src/test/java/top/egon/mario/clocktower/resource/ClocktowerRbacResourceProviderTests.java`

## Task 1: Schema, PO, And Repositories

**Files:**

- Create `be/src/main/resources/db/migration/V33__clocktower_public_mic.sql`
- Create `be/src/main/java/top/egon/mario/clocktower/game/mic/po/ClocktowerGamePublicMicSessionPo.java`
- Create `be/src/main/java/top/egon/mario/clocktower/game/mic/po/ClocktowerGamePublicMicTurnPo.java`
- Create `be/src/main/java/top/egon/mario/clocktower/game/mic/repository/ClocktowerGamePublicMicSessionRepository.java`
- Create `be/src/main/java/top/egon/mario/clocktower/game/mic/repository/ClocktowerGamePublicMicTurnRepository.java`
- Modify `be/src/test/java/top/egon/mario/clocktower/ClocktowerSchemaMigrationTests.java`

- [ ] Step 1: Add a migration path constant in `ClocktowerSchemaMigrationTests`.

```java
    private static final Path PUBLIC_MIC_MIGRATION = Path.of(
            "src/main/resources/db/migration/V33__clocktower_public_mic.sql");
```

- [ ] Step 2: Add a SQL contract test before implementing the migration.

```java
    @Test
    void publicMicMigrationCreatesSessionAndTurnTables() throws IOException {
        assertThat(Files.exists(PUBLIC_MIC_MIGRATION)).isTrue();

        String sql = Files.readString(PUBLIC_MIC_MIGRATION);

        assertThat(sql).contains("CREATE TABLE clocktower_game_public_mic_session");
        assertThat(sql).contains("CREATE TABLE clocktower_game_public_mic_turn");
        assertThat(sql).contains("game_id BIGINT NOT NULL");
        assertThat(sql).contains("day_no INTEGER NOT NULL");
        assertThat(sql).contains("current_holder_game_seat_id BIGINT");
        assertThat(sql).contains("current_turn_id BIGINT");
        assertThat(sql).contains("round_started_at TIMESTAMP WITH TIME ZONE");
        assertThat(sql).contains("grab_ends_at TIMESTAMP WITH TIME ZONE");
        assertThat(sql).contains("speech_event_id BIGINT");
        assertThat(sql).contains("created_by BIGINT");
        assertThat(sql).contains("updated_by BIGINT");
        assertThat(sql).contains("version BIGINT NOT NULL DEFAULT 0");
        assertThat(sql).contains("deleted BOOLEAN NOT NULL DEFAULT FALSE");
        assertThat(sql).contains("uk_clocktower_public_mic_session_game_day_deleted");
        assertThat(sql).contains("idx_clocktower_public_mic_session_game_status");
        assertThat(sql).contains("idx_clocktower_public_mic_turn_session_order");
        assertThat(sql).contains("idx_clocktower_public_mic_turn_game_seat");
    }
```

- [ ] Step 3: Add an H2/Flyway smoke test that inserts one session and two turns.

Use the existing `migratedJdbcTemplate(...)` helper in `ClocktowerSchemaMigrationTests`. Insert a `clocktower_game`, two `clocktower_game_seat` rows, one mic session, and two mic turns. Assert:

- table inserts succeed;
- `(game_id, day_no, deleted)` rejects a duplicate active session;
- turn rows can store `ROUND_ROBIN`, `GRAB_MIC`, `ACTIVE`, `DONE`, `PENDING`, and `AGENT`/`HUMAN` seat links.

- [ ] Step 4: Run the RED check.

```bash
cd be && ./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerSchemaMigrationTests test
```

Expected failure before implementation: the new migration file/table does not exist.

- [ ] Step 5: Create `V33__clocktower_public_mic.sql`.

Use project-style uppercase SQL and standard audit columns:

```sql
CREATE TABLE clocktower_game_public_mic_session
(
    id                          BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    game_id                     BIGINT                   NOT NULL,
    day_no                      INTEGER                  NOT NULL,
    status                      VARCHAR(32)              NOT NULL,
    current_holder_game_seat_id BIGINT,
    current_turn_id             BIGINT,
    round_started_at            TIMESTAMP WITH TIME ZONE,
    round_finished_at           TIMESTAMP WITH TIME ZONE,
    grab_started_at             TIMESTAMP WITH TIME ZONE,
    grab_ends_at                TIMESTAMP WITH TIME ZONE,
    closed_at                   TIMESTAMP WITH TIME ZONE,
    metadata_json               JSONB                    NOT NULL DEFAULT '{}',
    created_at                  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at                  TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by                  BIGINT,
    updated_by                  BIGINT,
    version                     BIGINT                   NOT NULL DEFAULT 0,
    deleted                     BOOLEAN                  NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_clocktower_public_mic_session_game_day_deleted UNIQUE (game_id, day_no, deleted)
);

CREATE INDEX idx_clocktower_public_mic_session_game_status
    ON clocktower_game_public_mic_session (game_id, status);

CREATE TABLE clocktower_game_public_mic_turn
(
    id               BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    session_id       BIGINT                   NOT NULL,
    game_id          BIGINT                   NOT NULL,
    day_no           INTEGER                  NOT NULL,
    game_seat_id     BIGINT                   NOT NULL,
    turn_order       INTEGER                  NOT NULL,
    stage            VARCHAR(32)              NOT NULL,
    acquisition_type VARCHAR(32)              NOT NULL,
    status           VARCHAR(32)              NOT NULL,
    started_at       TIMESTAMP WITH TIME ZONE,
    ended_at         TIMESTAMP WITH TIME ZONE,
    expires_at       TIMESTAMP WITH TIME ZONE,
    speech_event_id  BIGINT,
    metadata_json    JSONB                    NOT NULL DEFAULT '{}',
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by       BIGINT,
    updated_by       BIGINT,
    version          BIGINT                   NOT NULL DEFAULT 0,
    deleted          BOOLEAN                  NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_clocktower_public_mic_turn_session_order
    ON clocktower_game_public_mic_turn (session_id, turn_order);
CREATE INDEX idx_clocktower_public_mic_turn_game_seat
    ON clocktower_game_public_mic_turn (game_id, game_seat_id);
CREATE INDEX idx_clocktower_public_mic_turn_session_status
    ON clocktower_game_public_mic_turn (session_id, status);
```

- [ ] Step 6: Create PO classes extending `BaseAuditablePo`.

`ClocktowerGamePublicMicSessionPo` fields:

- `gameId`
- `dayNo`
- `status`
- `currentHolderGameSeatId`
- `currentTurnId`
- `roundStartedAt`
- `roundFinishedAt`
- `grabStartedAt`
- `grabEndsAt`
- `closedAt`
- `metadataJson = "{}"`

`ClocktowerGamePublicMicTurnPo` fields:

- `sessionId`
- `gameId`
- `dayNo`
- `gameSeatId`
- `turnOrder`
- `stage`
- `acquisitionType`
- `status`
- `startedAt`
- `endedAt`
- `expiresAt`
- `speechEventId`
- `metadataJson = "{}"`

Use `@JdbcTypeCode(SqlTypes.JSON)` for JSONB fields, matching `ClocktowerGameEventPo`.

- [ ] Step 7: Create repositories.

`ClocktowerGamePublicMicSessionRepository`:

```java
public interface ClocktowerGamePublicMicSessionRepository
        extends JpaRepository<ClocktowerGamePublicMicSessionPo, Long> {

    Optional<ClocktowerGamePublicMicSessionPo> findByGameIdAndDayNoAndDeletedFalse(Long gameId, int dayNo);

    Optional<ClocktowerGamePublicMicSessionPo> findTopByGameIdAndDeletedFalseOrderByDayNoDescIdDesc(Long gameId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select session
            from ClocktowerGamePublicMicSessionPo session
            where session.gameId = :gameId
              and session.dayNo = :dayNo
              and session.deleted = false
            """)
    Optional<ClocktowerGamePublicMicSessionPo> findLockedByGameIdAndDayNo(
            @Param("gameId") Long gameId, @Param("dayNo") int dayNo);
}
```

`ClocktowerGamePublicMicTurnRepository`:

```java
public interface ClocktowerGamePublicMicTurnRepository
        extends JpaRepository<ClocktowerGamePublicMicTurnPo, Long> {

    List<ClocktowerGamePublicMicTurnPo> findBySessionIdAndDeletedFalseOrderByTurnOrderAscIdAsc(Long sessionId);

    Optional<ClocktowerGamePublicMicTurnPo> findByIdAndDeletedFalse(Long id);

    Optional<ClocktowerGamePublicMicTurnPo> findFirstBySessionIdAndStatusAndDeletedFalseOrderByStartedAtDescIdDesc(
            Long sessionId, String status);

    Optional<ClocktowerGamePublicMicTurnPo> findFirstBySessionIdAndStatusAndDeletedFalseOrderByTurnOrderAscIdAsc(
            Long sessionId, String status);
}
```

- [ ] Step 8: Run the GREEN check for schema/mapping.

```bash
cd be && ./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerSchemaMigrationTests test
```

- [ ] Step 9: Commit task 1.

```bash
git status --short
git add be/src/main/resources/db/migration/V33__clocktower_public_mic.sql \
        be/src/main/java/top/egon/mario/clocktower/game/mic/po \
        be/src/main/java/top/egon/mario/clocktower/game/mic/repository \
        be/src/test/java/top/egon/mario/clocktower/ClocktowerSchemaMigrationTests.java
git commit -m "feat(clocktower): add public mic schema"
```

## Task 2: Service Contract, DTOs, Properties, Start Session, And Speak Guard

**Files:**

- Create `ClocktowerPublicMicProperties`
- Create DTO records
- Create `ClocktowerPublicMicService`
- Create `ClocktowerPublicMicServiceImpl`
- Create `ClocktowerPublicMicServiceTests`

- [ ] Step 1: Write RED service tests.

Create `ClocktowerPublicMicServiceTests` with helper setup copied from `ClocktowerGameLifecycleServiceTests` rather than importing private helpers. Use `@SpringBootTest` if that is the existing Clocktower service-test style. Build rooms through real room/game services, start a game, then set:

```java
game.setPhase("DAY");
game.setDayNo(1);
gameRepository.saveAndFlush(game);
```

Initial tests:

- `startDayMicSessionCreatesSeatOrderRoundRobinTurns`
- `startDayMicSessionIsIdempotentForSameGameDay`
- `requireCanSpeakRejectsMissingSession`
- `requireCanSpeakAllowsOnlyCurrentHolder`
- `agentSeatIsQueuedButCannotUseHumanPrincipalToGrab`

Expected behavior assertions:

- session status is `ROUND_ROBIN`;
- first eligible seat is holder;
- all active HUMAN and AGENT game seats are included in seat number order;
- first turn is `ACTIVE`, later round-robin turns are `PENDING`;
- `requireCanSpeak(gameId, holderSeatId)` does not throw;
- non-holder throws `CLOCKTOWER_MIC_NOT_HOLDER`;
- no session throws `CLOCKTOWER_MIC_SESSION_NOT_FOUND`.

- [ ] Step 2: Run RED check.

```bash
cd be && ./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerPublicMicServiceTests test
```

Expected failure before implementation: missing service/DTO classes or missing behavior.

- [ ] Step 3: Create properties.

```java
@Getter
@Setter
@ConfigurationProperties(prefix = "clocktower.public-mic")
public class ClocktowerPublicMicProperties {

    private long roundRobinTurnSeconds = 45;
    private long grabMicTotalSeconds = 300;
    private long grabMicHoldSeconds = 45;

    public Duration roundRobinTurnDuration() {
        return Duration.ofSeconds(requirePositive(roundRobinTurnSeconds, "CLOCKTOWER_MIC_ROUND_SECONDS_INVALID"));
    }

    public Duration grabMicTotalDuration() {
        return Duration.ofSeconds(requirePositive(grabMicTotalSeconds, "CLOCKTOWER_MIC_GRAB_TOTAL_SECONDS_INVALID"));
    }

    public Duration grabMicHoldDuration() {
        return Duration.ofSeconds(requirePositive(grabMicHoldSeconds, "CLOCKTOWER_MIC_GRAB_HOLD_SECONDS_INVALID"));
    }
}
```

Register it with `@EnableConfigurationProperties(ClocktowerPublicMicProperties.class)` on the implementation class or a small configuration class in the mic package.

- [ ] Step 4: Create DTO records.

`ClocktowerMicTurnView`:

```java
public record ClocktowerMicTurnView(
        Long turnId,
        Long gameSeatId,
        Integer seatNo,
        String displayName,
        String actorType,
        Long agentInstanceId,
        Integer turnOrder,
        String stage,
        String acquisitionType,
        String status,
        Instant startedAt,
        Instant endedAt,
        Instant expiresAt
) {
}
```

`ClocktowerMicSessionView`:

```java
public record ClocktowerMicSessionView(
        Long sessionId,
        Long gameId,
        Integer dayNo,
        String status,
        Long currentHolderGameSeatId,
        Long currentTurnId,
        Instant roundStartedAt,
        Instant roundFinishedAt,
        Instant grabStartedAt,
        Instant grabEndsAt,
        Instant closedAt,
        List<ClocktowerMicTurnView> turns
) {
}
```

`ClocktowerMicExtendRequest`:

```java
public record ClocktowerMicExtendRequest(Long seconds) {
}
```

- [ ] Step 5: Create service interface exactly as approved.

```java
public interface ClocktowerPublicMicService {

    ClocktowerMicSessionView startDayMicSession(Long gameId, RbacPrincipal principal);

    ClocktowerMicSessionView getMicSession(Long gameId, RbacPrincipal principal);

    ClocktowerMicSessionView finishCurrentTurn(Long gameId, Long turnId, RbacPrincipal principal);

    ClocktowerMicSessionView skipTurn(Long gameId, Long turnId, RbacPrincipal principal);

    ClocktowerMicSessionView grabMic(Long gameId, RbacPrincipal principal);

    ClocktowerMicSessionView releaseMic(Long gameId, RbacPrincipal principal);

    ClocktowerMicSessionView extendGrabMic(Long gameId, long seconds, RbacPrincipal principal);

    ClocktowerMicSessionView closeSession(Long gameId, RbacPrincipal principal);

    boolean canSpeak(Long gameId, Long actorGameSeatId);

    void requireCanSpeak(Long gameId, Long actorGameSeatId);
}
```

- [ ] Step 6: Implement start/session/guard behavior.

Core constants:

```java
private static final String GAME_STATUS_RUNNING = "RUNNING";
private static final String PHASE_DAY = "DAY";
private static final String SEAT_STATUS_ACTIVE = "ACTIVE";
private static final String ACTOR_TYPE_HUMAN = "HUMAN";
private static final String ACTOR_TYPE_AGENT = "AGENT";
private static final String SESSION_ROUND_ROBIN = "ROUND_ROBIN";
private static final String SESSION_GRAB_MIC = "GRAB_MIC";
private static final String SESSION_CLOSED = "CLOSED";
private static final String STAGE_ROUND_ROBIN = "ROUND_ROBIN";
private static final String TURN_PENDING = "PENDING";
private static final String TURN_ACTIVE = "ACTIVE";
private static final String ACQUISITION_ROUND_ROBIN = "ROUND_ROBIN";
private static final String EVENT_MIC_SESSION_STARTED = "MIC_SESSION_STARTED";
private static final String EVENT_MIC_TURN_STARTED = "MIC_TURN_STARTED";
```

Implementation rules:

- Use `gameRepository.findLockedByIdAndDeletedFalse(gameId)` for state-changing methods.
- Start requires `game.status == RUNNING`, `game.phase == DAY`, and `game.dayNo > 0`.
- Storyteller permission is existing room owner check: load `RoomSpacePo` by `game.roomId` and call `ClocktowerRoomAccessPolicy.requireOwner(room, principal)`.
- Existing session for same `gameId + dayNo` returns refreshed view.
- Eligible seats: `status == ACTIVE`, actor type `HUMAN` or `AGENT`, metadata does not contain `"muted":true` and does not contain `"leftGame":true`.
- Build round-robin turns in `seatNo` order with `turnOrder` starting at `1`.
- Activate the first turn immediately with `startedAt=now`, `expiresAt=now + roundRobinTurnDuration`, session holder/current turn set.
- Save `MIC_SESSION_STARTED` and `MIC_TURN_STARTED` events with `visibility = "PUBLIC"`, event sequence from `ClocktowerGameEventRepository.findTopByGameIdAndDeletedFalseOrderByEventSeqDesc`.
- `canSpeak` returns false instead of throwing.
- `requireCanSpeak` uses current day session, checks session open, current holder, active current turn, and non-expired `expiresAt`.

- [ ] Step 7: Implement DTO mapping.

Map turns with game-seat display data from `ClocktowerGameSeatRepository.findByGameIdAndDeletedFalseOrderBySeatNoAsc(gameId)`.

```java
private ClocktowerMicSessionView toView(ClocktowerGamePublicMicSessionPo session) {
    List<ClocktowerGamePublicMicTurnPo> turns =
            turnRepository.findBySessionIdAndDeletedFalseOrderByTurnOrderAscIdAsc(session.getId());
    Map<Long, ClocktowerGameSeatPo> seatsById = gameSeatRepository
            .findByGameIdAndDeletedFalseOrderBySeatNoAsc(session.getGameId())
            .stream()
            .collect(Collectors.toMap(ClocktowerGameSeatPo::getId, Function.identity()));
    return new ClocktowerMicSessionView(...);
}
```

- [ ] Step 8: Run GREEN check.

```bash
cd be && ./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerPublicMicServiceTests test
```

- [ ] Step 9: Commit task 2.

```bash
git status --short
git add be/src/main/java/top/egon/mario/clocktower/game/mic \
        be/src/test/java/top/egon/mario/clocktower/game/mic/ClocktowerPublicMicServiceTests.java
git commit -m "feat(clocktower): start public mic sessions"
```

## Task 3: Turn Advancement, Grab Mic, Timeout Refresh, And Storyteller Controls

**Files:**

- Modify `ClocktowerPublicMicServiceImpl`
- Modify `ClocktowerPublicMicServiceTests`

- [ ] Step 1: Add RED tests for transitions.

Add tests:

- `finishCurrentRoundRobinTurnAdvancesToNextSeat`
- `finishingLastRoundRobinTurnOpensGrabMicWindow`
- `skipPendingTurnRemovesItFromRoundRobinQueue`
- `skipCurrentTurnAdvancesQueue`
- `grabMicCreatesSingleActiveGrabTurn`
- `grabMicRejectsWhenHolderIsActive`
- `grabMicRejectsAfterGrabWindowExpires`
- `releaseGrabMicClearsHolder`
- `extendGrabMicMovesWindowEndButDoesNotExtendCurrentTurn`
- `closeSessionCancelsCurrentTurnAndRejectsCanSpeak`
- `lazyRefreshExpiresCurrentTurnAndAdvances`

Use the public service API only. Avoid sleeping; configure short durations in test properties or update timestamps directly through repositories, then call `getMicSession(...)` to trigger lazy refresh.

- [ ] Step 2: Run RED check.

```bash
cd be && ./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerPublicMicServiceTests test
```

- [ ] Step 3: Implement lazy refresh.

Every public service method should call a refresh helper while the game/session lock is held:

```java
private void refreshExpiredState(ClocktowerGamePo game, ClocktowerGamePublicMicSessionPo session, Instant now) {
    if (SESSION_CLOSED.equals(session.getStatus())) {
        return;
    }
    activeTurn(session).ifPresent(turn -> {
        if (turn.getExpiresAt() != null && !turn.getExpiresAt().isAfter(now)) {
            expireTurn(game, session, turn, now);
        }
    });
    if (SESSION_GRAB_MIC.equals(session.getStatus())
            && session.getGrabEndsAt() != null
            && !session.getGrabEndsAt().isAfter(now)
            && session.getCurrentTurnId() == null) {
        closeSessionInternal(game, session, now, "SYSTEM_TIMEOUT");
    }
}
```

Rules:

- Expired round-robin turn becomes `EXPIRED`, emits `MIC_TURN_EXPIRED`, then advances to next pending round-robin turn.
- If no pending round-robin turns remain, enter grab mode.
- Expired grab turn becomes `EXPIRED`, clears holder, emits `MIC_TURN_EXPIRED`, leaves session in `GRAB_MIC` until the grab window expires.
- Expired grab window closes session only when no holder remains.

- [ ] Step 4: Implement finish/release.

Rules:

- Finish is allowed for current human holder or storyteller.
- Finish on `ROUND_ROBIN` marks active turn `DONE`, emits `MIC_TURN_FINISHED`, then advances next pending turn or enters grab.
- Finish on `GRAB_MIC` behaves as release.
- Release is allowed for current human holder or storyteller.
- Release on grab marks current active turn `DONE`, clears holder/current turn, emits `MIC_TURN_FINISHED`.

- [ ] Step 5: Implement skip.

Rules:

- Storyteller only.
- `PENDING` turn -> `SKIPPED`, emit `MIC_TURN_SKIPPED`, no holder change.
- current `ACTIVE` turn -> `SKIPPED`, clear holder/current turn, emit `MIC_TURN_SKIPPED`, then advance or enter grab.
- reject terminal turns with `CLOCKTOWER_MIC_TURN_INVALID`.

- [ ] Step 6: Implement grab.

Rules:

- Session must be `GRAB_MIC`.
- `now` must be before `grabEndsAt`.
- No current holder.
- Principal must map to an active HUMAN game seat for this game.
- AGENT seats are queued in round-robin but do not grab through human principal APIs.
- Create an active `GRAB_MIC` turn with `acquisitionType = "GRAB"`.
- `expiresAt = min(now + grabMicHoldDuration, grabEndsAt)`.
- Set session holder/current turn and emit `MIC_TURN_STARTED`.

- [ ] Step 7: Implement extend and close.

Rules:

- Storyteller only.
- Extend requires session `GRAB_MIC`, `seconds > 0`, and not closed.
- Extend updates `grabEndsAt = grabEndsAt + seconds`.
- Extend does not alter current active turn `expiresAt`.
- Close marks current active turn `CANCELLED`, clears holder/current turn, sets status `CLOSED`, `closedAt=now`, emits `MIC_SESSION_CLOSED`.

- [ ] Step 8: Run GREEN check.

```bash
cd be && ./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerPublicMicServiceTests test
```

- [ ] Step 9: Commit task 3.

```bash
git status --short
git add be/src/main/java/top/egon/mario/clocktower/game/mic/service/impl/ClocktowerPublicMicServiceImpl.java \
        be/src/test/java/top/egon/mario/clocktower/game/mic/ClocktowerPublicMicServiceTests.java
git commit -m "feat(clocktower): advance public mic turns"
```

## Task 4: Controller And RBAC Resources

**Files:**

- Create `ClocktowerPublicMicController`
- Modify `ClocktowerRbacResourceProvider`
- Modify `ClocktowerRbacResourceProviderTests`

- [ ] Step 1: Add RED RBAC tests.

In `ClocktowerRbacResourceProviderTests`:

- Add resource codes to `containsExactlyInAnyOrder`:
  - `api:clocktower:game:mic:read`
  - `api:clocktower:game:mic:player`
  - `api:clocktower:game:mic:storyteller`
- Assert player preset contains read + player, not storyteller.
- Assert storyteller preset contains all three.
- Add matcher assertions:

```java
assertMatches(rules, "GET", "/api/clocktower/games/9/mic",
        "api:clocktower:game:mic:read");
assertMatches(rules, "POST", "/api/clocktower/games/9/mic/grab",
        "api:clocktower:game:mic:player");
assertMatches(rules, "POST", "/api/clocktower/games/9/mic/release",
        "api:clocktower:game:mic:player");
assertMatches(rules, "POST", "/api/clocktower/games/9/mic/start-day",
        "api:clocktower:game:mic:storyteller");
assertMatches(rules, "POST", "/api/clocktower/games/9/mic/turns/3/skip",
        "api:clocktower:game:mic:storyteller");
assertMatches(rules, "POST", "/api/clocktower/games/9/mic/extend",
        "api:clocktower:game:mic:storyteller");
assertMatches(rules, "POST", "/api/clocktower/games/9/mic/close",
        "api:clocktower:game:mic:storyteller");
```

- [ ] Step 2: Run RED check.

```bash
cd be && ./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerRbacResourceProviderTests test
```

- [ ] Step 3: Add RBAC constants and grants.

In `ClocktowerRbacResourceProvider` add:

```java
private static final String GAME_MIC_READ = "api:clocktower:game:mic:read";
private static final String GAME_MIC_PLAYER = "api:clocktower:game:mic:player";
private static final String GAME_MIC_STORYTELLER = "api:clocktower:game:mic:storyteller";
```

Add to `PLAYER_PERMISSION_CODES`: `GAME_MIC_READ`, `GAME_MIC_PLAYER`.

Add to `STORYTELLER_PERMISSION_CODES`: `GAME_MIC_READ`, `GAME_MIC_PLAYER`, `GAME_MIC_STORYTELLER`.

Add resources:

```java
resources.add(api(GAME_MIC_READ, "Clocktower game mic read", "GET",
        "^/api/clocktower/games/[^/]+/mic$", ApiMatcherType.REGEX, ApiRiskLevel.LOW));
resources.add(api(GAME_MIC_PLAYER, "Clocktower game mic player", "POST",
        "^/api/clocktower/games/[^/]+/mic/(grab|release|turns/[^/]+/finish)$",
        ApiMatcherType.REGEX, ApiRiskLevel.MEDIUM));
resources.add(api(GAME_MIC_STORYTELLER, "Clocktower game mic storyteller", "POST",
        "^/api/clocktower/games/[^/]+/mic/(start-day|extend|close|turns/[^/]+/(finish|skip))$",
        ApiMatcherType.REGEX, ApiRiskLevel.HIGH));
```

Keep `finish` matched by both player and storyteller. Service permissions remain authoritative.

- [ ] Step 4: Create controller.

`ClocktowerPublicMicController`:

```java
@RestController
@RequestMapping("/api/clocktower/games/{gameId}/mic")
@RequiredArgsConstructor
public class ClocktowerPublicMicController extends ClocktowerReactiveSupport {

    private final ClocktowerPublicMicService micService;

    @GetMapping
    public Mono<ApiResponse<ClocktowerMicSessionView>> getMicSession(@PathVariable Long gameId,
                                                                      RbacPrincipal principal) {
        return blocking(() -> micService.getMicSession(gameId, principal));
    }

    @PostMapping("/start-day")
    public Mono<ApiResponse<ClocktowerMicSessionView>> startDay(@PathVariable Long gameId,
                                                                 RbacPrincipal principal) {
        return blocking(() -> micService.startDayMicSession(gameId, principal));
    }

    @PostMapping("/turns/{turnId}/finish")
    public Mono<ApiResponse<ClocktowerMicSessionView>> finish(@PathVariable Long gameId,
                                                               @PathVariable Long turnId,
                                                               RbacPrincipal principal) {
        return blocking(() -> micService.finishCurrentTurn(gameId, turnId, principal));
    }

    @PostMapping("/turns/{turnId}/skip")
    public Mono<ApiResponse<ClocktowerMicSessionView>> skip(@PathVariable Long gameId,
                                                             @PathVariable Long turnId,
                                                             RbacPrincipal principal) {
        return blocking(() -> micService.skipTurn(gameId, turnId, principal));
    }

    @PostMapping("/grab")
    public Mono<ApiResponse<ClocktowerMicSessionView>> grab(@PathVariable Long gameId,
                                                             RbacPrincipal principal) {
        return blocking(() -> micService.grabMic(gameId, principal));
    }

    @PostMapping("/release")
    public Mono<ApiResponse<ClocktowerMicSessionView>> release(@PathVariable Long gameId,
                                                                RbacPrincipal principal) {
        return blocking(() -> micService.releaseMic(gameId, principal));
    }

    @PostMapping("/extend")
    public Mono<ApiResponse<ClocktowerMicSessionView>> extend(@PathVariable Long gameId,
                                                               @RequestBody ClocktowerMicExtendRequest request,
                                                               RbacPrincipal principal) {
        return blocking(() -> micService.extendGrabMic(gameId, request.seconds(), principal));
    }

    @PostMapping("/close")
    public Mono<ApiResponse<ClocktowerMicSessionView>> close(@PathVariable Long gameId,
                                                              RbacPrincipal principal) {
        return blocking(() -> micService.closeSession(gameId, principal));
    }
}
```

- [ ] Step 5: Run GREEN check.

```bash
cd be && ./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerRbacResourceProviderTests test
```

- [ ] Step 6: Commit task 4.

```bash
git status --short
git add be/src/main/java/top/egon/mario/clocktower/game/mic/web/ClocktowerPublicMicController.java \
        be/src/main/java/top/egon/mario/clocktower/resource/ClocktowerRbacResourceProvider.java \
        be/src/test/java/top/egon/mario/clocktower/resource/ClocktowerRbacResourceProviderTests.java
git commit -m "feat(clocktower): expose public mic api"
```

## Task 5: Integrated Verification And Cleanup

**Files:**

- Any files changed by tasks 1-4.

- [ ] Step 1: Run targeted test suite.

```bash
cd be && ./mvnw -Dmaven.build.cache.enabled=false \
  -Dtest=ClocktowerSchemaMigrationTests,ClocktowerPublicMicServiceTests,ClocktowerRbacResourceProviderTests \
  test
```

- [ ] Step 2: Run package compile/test smoke if targeted tests pass.

```bash
cd be && ./mvnw -Dmaven.build.cache.enabled=false test
```

If the full suite fails outside changed scope, capture the failing command, failing class/method, and whether targeted task-05 tests still passed.

- [ ] Step 3: Inspect diff for scope control.

```bash
git status --short
git diff --stat HEAD
git diff --check
```

Confirm:

- exactly one new migration was added;
- no existing migration was edited;
- no frontend files were changed;
- no old room action/chat flow was changed;
- no app/browser startup was attempted.

- [ ] Step 4: Commit final verification-only adjustments if any.

Only commit if task 5 required code/test cleanup:

```bash
git add <changed-files>
git commit -m "test(clocktower): verify public mic behavior"
```

Do not create an empty commit.

## Acceptance Checklist

- [ ] One public mic session per game day is persisted.
- [ ] Round-robin turns include active HUMAN and AGENT game seats in seat number order.
- [ ] Only one active holder exists through service locking and transition checks.
- [ ] First round-robin turn starts immediately.
- [ ] Holder or storyteller can finish/release an active turn.
- [ ] Storyteller can skip, extend, and close.
- [ ] Session moves to grab mode after the round-robin queue is exhausted.
- [ ] Human players can grab during the five-minute window when no holder is active.
- [ ] Grab window expiration closes the session.
- [ ] `requireCanSpeak(gameId, actorGameSeatId)` rejects missing/closed/non-holder/expired cases.
- [ ] Public mic lifecycle game events are emitted.
- [ ] RBAC resource provider exposes read/player/storyteller mic resources.
- [ ] Targeted tests pass.

## Recommended Execution Order

1. Task 1: Schema, PO, and repositories.
2. Task 2: Service API, properties, start session, and guard.
3. Task 3: Turn transitions, grab mic, timeout refresh, and storyteller controls.
4. Task 4: Controller and RBAC resources.
5. Task 5: Integrated verification and cleanup.

Each implementation task should be committed once after its GREEN check, matching the project collaboration rule.
