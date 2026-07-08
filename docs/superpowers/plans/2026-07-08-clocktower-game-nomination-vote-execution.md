# Clocktower Game Nomination Vote Execution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement task 07 by moving nomination, voting, and daytime execution into the new game-level Clocktower model for both HUMAN and AGENT seats.

**Architecture:** Add one focused `top.egon.mario.clocktower.game.nomination` domain package. `ClocktowerGameActionExecutor` continues to authenticate the actor seat, then delegates `NOMINATE` and `VOTE` into domain services. Storyteller-only close and resolve endpoints operate on the same durable game-level tables and append game events for the game view.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring MVC/WebFlux controller wrappers, Spring Data JPA, H2/Flyway test database, JUnit 5, AssertJ.

---

## File Structure

Create:

- `be/src/main/resources/db/migration/V34__clocktower_game_nomination_vote.sql` - one migration for nomination, vote, and execution tables.
- `be/src/main/java/top/egon/mario/clocktower/game/nomination/po/ClocktowerGameNominationPo.java` - nomination row.
- `be/src/main/java/top/egon/mario/clocktower/game/nomination/po/ClocktowerGameVotePo.java` - vote row.
- `be/src/main/java/top/egon/mario/clocktower/game/nomination/po/ClocktowerGameExecutionPo.java` - daily execution row.
- `be/src/main/java/top/egon/mario/clocktower/game/nomination/repository/ClocktowerGameNominationRepository.java` - nomination lookups and locks.
- `be/src/main/java/top/egon/mario/clocktower/game/nomination/repository/ClocktowerGameVoteRepository.java` - vote lookups and counts.
- `be/src/main/java/top/egon/mario/clocktower/game/nomination/repository/ClocktowerGameExecutionRepository.java` - execution lookups and locks.
- `be/src/main/java/top/egon/mario/clocktower/game/nomination/dto/ClocktowerGameNominationResponse.java` - close/open response.
- `be/src/main/java/top/egon/mario/clocktower/game/nomination/dto/ClocktowerGameExecutionResolveRequest.java` - resolve request.
- `be/src/main/java/top/egon/mario/clocktower/game/nomination/dto/ClocktowerGameExecutionResponse.java` - execution response.
- `be/src/main/java/top/egon/mario/clocktower/game/nomination/service/ClocktowerGameNominationService.java` - nomination API.
- `be/src/main/java/top/egon/mario/clocktower/game/nomination/service/ClocktowerGameVoteService.java` - vote API.
- `be/src/main/java/top/egon/mario/clocktower/game/nomination/service/ClocktowerGameExecutionService.java` - close/resolve API.
- `be/src/main/java/top/egon/mario/clocktower/game/nomination/service/impl/ClocktowerGameNominationServiceImpl.java` - nomination rules.
- `be/src/main/java/top/egon/mario/clocktower/game/nomination/service/impl/ClocktowerGameVoteServiceImpl.java` - vote rules.
- `be/src/main/java/top/egon/mario/clocktower/game/nomination/service/impl/ClocktowerGameExecutionServiceImpl.java` - close/resolve rules.
- `be/src/main/java/top/egon/mario/clocktower/game/nomination/web/ClocktowerGameNominationController.java` - Storyteller close/resolve endpoints.
- `be/src/test/java/top/egon/mario/clocktower/game/nomination/ClocktowerGameNominationServiceTests.java` - nomination/vote/execution integration tests.

Modify:

- `be/src/main/java/top/egon/mario/clocktower/game/action/service/impl/ClocktowerGameActionExecutorImpl.java` - delegate `NOMINATE` and `VOTE`.
- `be/src/main/java/top/egon/mario/clocktower/game/repository/ClocktowerGameSeatRepository.java` - add alive/active count helper if derived query is readable enough.
- `be/src/test/java/top/egon/mario/clocktower/game/action/ClocktowerGameActionServiceTests.java` - replace task 06 not-implemented assertions with real nomination/vote coverage.
- `be/src/test/java/top/egon/mario/clocktower/ClocktowerJpaMappingTests.java` - assert new PO classes are managed and JSON fields persist.

Do not modify:

- Existing Flyway migrations before `V34`.
- `be/src/main/java/top/egon/mario/clocktower/action/**`
- `be/src/main/java/top/egon/mario/clocktower/flow/**`
- Frontend files.

---

### Task 1: Migration, POs, And Repositories

**Files:**
- Create: `be/src/main/resources/db/migration/V34__clocktower_game_nomination_vote.sql`
- Create: `be/src/main/java/top/egon/mario/clocktower/game/nomination/po/ClocktowerGameNominationPo.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/game/nomination/po/ClocktowerGameVotePo.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/game/nomination/po/ClocktowerGameExecutionPo.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/game/nomination/repository/ClocktowerGameNominationRepository.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/game/nomination/repository/ClocktowerGameVoteRepository.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/game/nomination/repository/ClocktowerGameExecutionRepository.java`
- Modify: `be/src/test/java/top/egon/mario/clocktower/ClocktowerJpaMappingTests.java`

- [ ] **Step 1: Write failing JPA mapping test**

Add these imports to `ClocktowerJpaMappingTests`:

```java
import top.egon.mario.clocktower.game.nomination.po.ClocktowerGameExecutionPo;
import top.egon.mario.clocktower.game.nomination.po.ClocktowerGameNominationPo;
import top.egon.mario.clocktower.game.nomination.po.ClocktowerGameVotePo;
```

Extend `clocktowerGamePoClassesAreManagedByJpaContext()`:

```java
assertManaged(ClocktowerGameNominationPo.class);
assertManaged(ClocktowerGameVotePo.class);
assertManaged(ClocktowerGameExecutionPo.class);
```

Add `clocktowerGameNominationJsonColumnsRoundTripMinimalStrings()`:

```java
@Test
void clocktowerGameNominationJsonColumnsRoundTripMinimalStrings() {
    ClocktowerGameNominationPo nomination = new ClocktowerGameNominationPo();
    nomination.setGameId(701L);
    nomination.setDayNo(1);
    nomination.setNominatorGameSeatId(801L);
    nomination.setNomineeGameSeatId(802L);
    nomination.setStatus("OPEN");
    nomination.setRequiredVotes(3);
    nomination.setOpenedAt(Instant.parse("2026-01-01T00:00:00Z"));
    nomination.setMetadataJson("{}");
    entityManager.persist(nomination);

    ClocktowerGameVotePo vote = new ClocktowerGameVotePo();
    vote.setGameId(701L);
    vote.setNominationId(901L);
    vote.setVoterGameSeatId(803L);
    vote.setVoteValue(true);
    vote.setStatus("CAST");
    vote.setMetadataJson("{}");
    entityManager.persist(vote);

    ClocktowerGameExecutionPo execution = new ClocktowerGameExecutionPo();
    execution.setGameId(701L);
    execution.setDayNo(1);
    execution.setStatus("PENDING");
    execution.setMetadataJson("{}");
    entityManager.persist(execution);

    entityManager.flush();
    entityManager.clear();

    assertThat(entityManager.find(ClocktowerGameNominationPo.class, nomination.getId()).getMetadataJson())
            .isEqualTo("{}");
    assertThat(entityManager.find(ClocktowerGameVotePo.class, vote.getId()).getMetadataJson()).isEqualTo("{}");
    assertThat(entityManager.find(ClocktowerGameExecutionPo.class, execution.getId()).getMetadataJson())
            .isEqualTo("{}");
}
```

- [ ] **Step 2: Run the failing mapping test**

Run:

```bash
./mvnw -pl be -Dtest=ClocktowerJpaMappingTests test
```

Expected: fail to compile because the three PO classes do not exist.

- [ ] **Step 3: Add migration and persistence classes**

Implement the migration with all audit columns from `BaseAuditablePo`, JSONB `metadata_json`, unique open nomination index, unique vote index, and unique execution-per-day index.

Implement each PO by extending `BaseAuditablePo`, using `@Entity`, `@Table`, `@Getter`, `@Setter`, and JSONB mapping:

```java
@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
private String metadataJson = "{}";
```

Repository methods to include:

```java
Optional<ClocktowerGameNominationPo> findByIdAndGameIdAndDeletedFalse(Long id, Long gameId);
Optional<ClocktowerGameNominationPo> findTopByGameIdAndStatusAndDeletedFalseOrderByIdDesc(Long gameId, String status);
boolean existsByGameIdAndDayNoAndNominatorGameSeatIdAndDeletedFalse(Long gameId, int dayNo, Long seatId);
boolean existsByGameIdAndDayNoAndNomineeGameSeatIdAndDeletedFalse(Long gameId, int dayNo, Long seatId);
List<ClocktowerGameNominationPo> findByGameIdAndDayNoAndStatusAndDeletedFalse(Long gameId, int dayNo, String status);
Optional<ClocktowerGameVotePo> findByNominationIdAndVoterGameSeatIdAndDeletedFalse(Long nominationId, Long voterSeatId);
long countByNominationIdAndVoteValueTrueAndDeletedFalse(Long nominationId);
Optional<ClocktowerGameExecutionPo> findByGameIdAndDayNoAndDeletedFalse(Long gameId, int dayNo);
```

Add lock queries for nomination and execution rows that are mutated.

- [ ] **Step 4: Run mapping test until it passes**

Run:

```bash
./mvnw -pl be -Dtest=ClocktowerJpaMappingTests test
```

Expected: pass.

- [ ] **Step 5: Commit**

```bash
git add be/src/main/resources/db/migration/V34__clocktower_game_nomination_vote.sql \
    be/src/main/java/top/egon/mario/clocktower/game/nomination/po \
    be/src/main/java/top/egon/mario/clocktower/game/nomination/repository \
    be/src/test/java/top/egon/mario/clocktower/ClocktowerJpaMappingTests.java
git commit -m "feat(clocktower): add game nomination tables"
```

---

### Task 2: Nomination Execution

**Files:**
- Create: `be/src/main/java/top/egon/mario/clocktower/game/nomination/dto/ClocktowerGameNominationResponse.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/game/nomination/service/ClocktowerGameNominationService.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/game/nomination/service/impl/ClocktowerGameNominationServiceImpl.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/game/action/service/impl/ClocktowerGameActionExecutorImpl.java`
- Test: `be/src/test/java/top/egon/mario/clocktower/game/action/ClocktowerGameActionServiceTests.java`

- [ ] **Step 1: Write failing nomination tests**

Replace `nominateVote_notImplementedUntilTask07()` with:

```java
@Test
void nominateOpensGameNominationAfterMicClosed() {
    StartedGame game = startDayGameWithAgents();
    ClocktowerGameSeatPo humanSeat = game.seats().getFirst();
    ClocktowerGameSeatPo agentSeat = game.seats().get(1);
    micService.startDayMicSession(game.gameId(), owner());
    micService.closeSession(game.gameId(), owner());

    ClocktowerGameActionResponse response = humanActionService.submit(game.gameId(),
            new ClocktowerGameActionRequest(humanSeat.getId(), "NOMINATE", List.of(agentSeat.getId()),
                    null, null, "nominate", Map.of()),
            principal(11L, "player1"));

    assertThat(response.accepted()).isTrue();
    assertThat(response.event().eventType()).isEqualTo("NOMINATION_OPENED");
    assertThat(gameRepository.findByIdAndDeletedFalse(game.gameId()).orElseThrow().getPhase())
            .isEqualTo("NOMINATION");
    assertThat(gameEventTypes(game.gameId())).contains("NOMINATION_OPENED");
}
```

Add:

```java
@Test
void nominateRejectsOpenMicAndSelfNomination() {
    StartedGame game = startDayGameWithAgents();
    ClocktowerGameSeatPo humanSeat = game.seats().getFirst();
    micService.startDayMicSession(game.gameId(), owner());

    ClocktowerGameActionResponse openMic = humanActionService.submit(game.gameId(),
            new ClocktowerGameActionRequest(humanSeat.getId(), "NOMINATE", List.of(game.seats().get(1).getId()),
                    null, null, null, Map.of()),
            principal(11L, "player1"));

    assertThat(openMic.accepted()).isFalse();
    assertThat(openMic.rejectedCode()).isEqualTo("CLOCKTOWER_NOMINATION_MIC_SESSION_OPEN");

    micService.closeSession(game.gameId(), owner());
    ClocktowerGameActionResponse self = humanActionService.submit(game.gameId(),
            new ClocktowerGameActionRequest(humanSeat.getId(), "NOMINATE", List.of(humanSeat.getId()),
                    null, null, null, Map.of()),
            principal(11L, "player1"));

    assertThat(self.accepted()).isFalse();
    assertThat(self.rejectedCode()).isEqualTo("CLOCKTOWER_NOMINATION_SELF_NOT_ALLOWED");
}
```

Add:

```java
@Test
void agentCanNominateHumanSeat() {
    StartedGame game = startDayGameWithAgents();
    ClocktowerGameSeatPo humanSeat = game.seats().getFirst();
    ClocktowerGameSeatPo agentSeat = game.seats().get(1);
    ClocktowerAgentInstancePo instance = agentInstanceRepository
            .findByGameSeatIdAndDeletedFalse(agentSeat.getId())
            .orElseThrow();
    micService.startDayMicSession(game.gameId(), owner());
    micService.closeSession(game.gameId(), owner());

    ClocktowerGameActionResponse response = agentActionService.submitAgentAction(game.gameId(), instance.getId(),
            new ClocktowerGameActionRequest(agentSeat.getId(), "NOMINATE", List.of(humanSeat.getId()),
                    null, null, null, Map.of()));

    assertThat(response.accepted()).isTrue();
    assertThat(response.event().payload()).containsEntry("actorType", "AGENT");
}
```

- [ ] **Step 2: Run failing nomination tests**

Run:

```bash
./mvnw -pl be -Dtest=ClocktowerGameActionServiceTests test
```

Expected: fail because `NOMINATE` still returns `CLOCKTOWER_ACTION_NOT_IMPLEMENTED`.

- [ ] **Step 3: Implement nomination service**

Implement `ClocktowerGameNominationService#nominate(ClocktowerGamePo game, ClocktowerGameSeatPo actorSeat, GameActionCommand command, ActorContext actor)`.

The method must:

- Validate phase `DAY` or `NOMINATION`.
- Require exactly one target seat id.
- Reject self nomination.
- Require active/alive nominator and nominee.
- Require current-day mic session closed when a session exists.
- Reject existing open nomination.
- Reject duplicate daily nominator and nominee.
- Count active alive players from `clocktower_game_seat`.
- Save nomination with `status = OPEN`, `openedAt = now`, `voteCount = 0`, and computed `requiredVotes`.
- Set game phase to `NOMINATION` and save the game.
- Append `NOMINATION_OPENED` as a public game event.
- Return `ClocktowerGameActionResponse.accepted(event)`.

Wire the executor:

```java
case "NOMINATE" -> nominationService.nominate(game, seat, command, actor);
```

- [ ] **Step 4: Run nomination tests until they pass**

Run:

```bash
./mvnw -pl be -Dtest=ClocktowerGameActionServiceTests test
```

Expected: pass for existing task 06 tests and new nomination tests.

- [ ] **Step 5: Commit**

```bash
git add be/src/main/java/top/egon/mario/clocktower/game/nomination \
    be/src/main/java/top/egon/mario/clocktower/game/action/service/impl/ClocktowerGameActionExecutorImpl.java \
    be/src/test/java/top/egon/mario/clocktower/game/action/ClocktowerGameActionServiceTests.java
git commit -m "feat(clocktower): execute game nominations"
```

---

### Task 3: Vote Execution

**Files:**
- Create: `be/src/main/java/top/egon/mario/clocktower/game/nomination/service/ClocktowerGameVoteService.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/game/nomination/service/impl/ClocktowerGameVoteServiceImpl.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/game/action/service/impl/ClocktowerGameActionExecutorImpl.java`
- Test: `be/src/test/java/top/egon/mario/clocktower/game/action/ClocktowerGameActionServiceTests.java`

- [ ] **Step 1: Write failing vote tests**

Add:

```java
@Test
void alivePlayerVotesOnOpenNomination() {
    StartedGame game = startDayGameWithAgents();
    ClocktowerGameSeatPo nominator = game.seats().getFirst();
    ClocktowerGameSeatPo nominee = game.seats().get(1);
    Long nominationId = openNomination(game, nominator, nominee);

    ClocktowerGameActionResponse response = humanActionService.submit(game.gameId(),
            new ClocktowerGameActionRequest(nominator.getId(), "VOTE", List.of(),
                    nominationId, true, null, Map.of()),
            principal(11L, "player1"));

    assertThat(response.accepted()).isTrue();
    assertThat(response.event().eventType()).isEqualTo("VOTE_CAST");
    assertThat(response.event().payload())
            .containsEntry("voteValue", true)
            .containsEntry("usedDeadVote", false);
}
```

Add:

```java
@Test
void deadYesVoteSpendsDeadVoteAndDuplicateVoteIsRejected() {
    StartedGame game = startDayGameWithAgents();
    ClocktowerGameSeatPo voter = game.seats().getFirst();
    ClocktowerGameSeatPo nominee = game.seats().get(1);
    Long nominationId = openNomination(game, voter, nominee);
    voter.setLifeStatus("DEAD");
    voter.setPublicLifeStatus("DEAD");
    voter.setHasDeadVote(true);
    gameSeatRepository.saveAndFlush(voter);

    ClocktowerGameActionResponse first = humanActionService.submit(game.gameId(),
            new ClocktowerGameActionRequest(voter.getId(), "VOTE", List.of(),
                    nominationId, true, null, Map.of()),
            principal(11L, "player1"));
    ClocktowerGameActionResponse duplicate = humanActionService.submit(game.gameId(),
            new ClocktowerGameActionRequest(voter.getId(), "VOTE", List.of(),
                    nominationId, false, null, Map.of()),
            principal(11L, "player1"));

    assertThat(first.accepted()).isTrue();
    assertThat(first.event().payload()).containsEntry("usedDeadVote", true);
    assertThat(gameSeatRepository.findByIdAndDeletedFalse(voter.getId()).orElseThrow().isHasDeadVote()).isFalse();
    assertThat(duplicate.accepted()).isFalse();
    assertThat(duplicate.rejectedCode()).isEqualTo("CLOCKTOWER_VOTE_ALREADY_CAST");
}
```

Add helper:

```java
private Long openNomination(StartedGame game, ClocktowerGameSeatPo nominator, ClocktowerGameSeatPo nominee) {
    micService.startDayMicSession(game.gameId(), owner());
    micService.closeSession(game.gameId(), owner());
    ClocktowerGameActionResponse response = humanActionService.submit(game.gameId(),
            new ClocktowerGameActionRequest(nominator.getId(), "NOMINATE", List.of(nominee.getId()),
                    null, null, null, Map.of()),
            principal(11L, "player1"));
    assertThat(response.accepted()).isTrue();
    return ((Number) response.event().payload().get("nominationId")).longValue();
}
```

- [ ] **Step 2: Run failing vote tests**

Run:

```bash
./mvnw -pl be -Dtest=ClocktowerGameActionServiceTests test
```

Expected: fail because `VOTE` is not wired or vote service does not exist.

- [ ] **Step 3: Implement vote service**

Implement `ClocktowerGameVoteService#vote(ClocktowerGamePo game, ClocktowerGameSeatPo actorSeat, GameActionCommand command, ActorContext actor)`.

The method must:

- Require explicit `command.vote()`.
- Load locked nomination by `command.nominationId()` or current open nomination.
- Require nomination status `OPEN`.
- Reject duplicate voter.
- Determine alive state from both `lifeStatus` and `publicLifeStatus`.
- Allow alive votes true/false.
- Allow dead false vote without changing `hasDeadVote`.
- Allow dead true vote only when `hasDeadVote = true`, then set it false.
- Save `ClocktowerGameVotePo` with `status = CAST`.
- Recalculate yes count and update nomination `voteCount`.
- Append `VOTE_CAST` with `voteValue`, `usedDeadVote`, `voteCount`, and `nominationId`.

Wire the executor:

```java
case "VOTE" -> voteService.vote(game, seat, command, actor);
```

- [ ] **Step 4: Run vote tests until they pass**

Run:

```bash
./mvnw -pl be -Dtest=ClocktowerGameActionServiceTests test
```

Expected: pass.

- [ ] **Step 5: Commit**

```bash
git add be/src/main/java/top/egon/mario/clocktower/game/nomination \
    be/src/main/java/top/egon/mario/clocktower/game/action/service/impl/ClocktowerGameActionExecutorImpl.java \
    be/src/test/java/top/egon/mario/clocktower/game/action/ClocktowerGameActionServiceTests.java
git commit -m "feat(clocktower): execute game votes"
```

---

### Task 4: Close Nomination And Resolve Execution

**Files:**
- Create: `be/src/main/java/top/egon/mario/clocktower/game/nomination/dto/ClocktowerGameExecutionResolveRequest.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/game/nomination/dto/ClocktowerGameExecutionResponse.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/game/nomination/service/ClocktowerGameExecutionService.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/game/nomination/service/impl/ClocktowerGameExecutionServiceImpl.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/game/nomination/web/ClocktowerGameNominationController.java`
- Test: `be/src/test/java/top/egon/mario/clocktower/game/nomination/ClocktowerGameNominationServiceTests.java`

- [ ] **Step 1: Write failing close/resolve tests**

Create `ClocktowerGameNominationServiceTests` by reusing the helper style from `ClocktowerGameActionServiceTests`.

Add:

```java
@Test
void closeNominationCreatesExecutionCandidate() {
    StartedGame game = startDayGameWithAgents();
    Long nominationId = openNomination(game, game.seats().getFirst(), game.seats().get(1));
    voteYes(game, game.seats().getFirst(), nominationId);
    voteYesAsAgent(game, game.seats().get(2), nominationId);

    ClocktowerGameNominationResponse response = executionService.closeNomination(game.gameId(), nominationId, owner());

    assertThat(response.status()).isEqualTo("CLOSED");
    assertThat(response.voteCount()).isEqualTo(2);
    assertThat(response.execution().nomineeGameSeatId()).isEqualTo(game.seats().get(1).getId());
    assertThat(gameRepository.findByIdAndDeletedFalse(game.gameId()).orElseThrow().getPhase())
            .isEqualTo("EXECUTION");
    assertThat(gameEventTypes(game.gameId())).contains("NOMINATION_CLOSED", "EXECUTION_CANDIDATE_UPDATED");
}
```

Add:

```java
@Test
void resolveExecutionKillsCandidateAndAppendsEvents() {
    StartedGame game = startDayGameWithAgents();
    Long nominationId = openAndCloseQualifyingNomination(game);
    ClocktowerGameSeatPo nominee = game.seats().get(1);

    ClocktowerGameExecutionResponse response = executionService.resolveExecution(game.gameId(),
            new ClocktowerGameExecutionResolveRequest(true, nominee.getId(), nominationId, "execute"),
            owner());

    assertThat(response.status()).isEqualTo("RESOLVED");
    assertThat(response.executed()).isTrue();
    ClocktowerGameSeatPo reloaded = gameSeatRepository.findByIdAndDeletedFalse(nominee.getId()).orElseThrow();
    assertThat(reloaded.getLifeStatus()).isEqualTo("DEAD");
    assertThat(reloaded.getPublicLifeStatus()).isEqualTo("DEAD");
    assertThat(gameEventTypes(game.gameId())).contains("PLAYER_EXECUTED", "PLAYER_DIED");
}
```

Add:

```java
@Test
void tiedQualifyingNominationsResolveNoExecution() {
    StartedGame game = startDayGameWithAgents();
    Long firstNominationId = openCloseWithVotes(game, game.seats().getFirst(), game.seats().get(1), 2);
    Long secondNominationId = openCloseWithVotes(game, game.seats().get(2), game.seats().get(3), 2);

    ClocktowerGameExecutionResponse response = executionService.resolveExecution(game.gameId(),
            new ClocktowerGameExecutionResolveRequest(false, null, null, "tie"),
            owner());

    assertThat(firstNominationId).isNotEqualTo(secondNominationId);
    assertThat(response.status()).isEqualTo("RESOLVED");
    assertThat(response.executed()).isFalse();
    assertThat(response.nomineeGameSeatId()).isNull();
    assertThat(gameEventTypes(game.gameId())).contains("NO_EXECUTION");
}
```

- [ ] **Step 2: Run failing close/resolve tests**

Run:

```bash
./mvnw -pl be -Dtest=ClocktowerGameNominationServiceTests test
```

Expected: fail to compile because the execution service and DTOs do not exist.

- [ ] **Step 3: Implement close and resolve services**

Implement:

```java
ClocktowerGameNominationResponse closeNomination(Long gameId, Long nominationId, RbacPrincipal principal);
ClocktowerGameExecutionResponse resolveExecution(Long gameId,
        ClocktowerGameExecutionResolveRequest request,
        RbacPrincipal principal);
```

Close must require owner access via `ClocktowerRoomAccessPolicy`, lock the game, close the nomination, compute the candidate, upsert the execution row, set game phase `EXECUTION`, and append close/candidate events.

Resolve must require owner access, lock game and execution row, enforce candidate matching, kill the candidate when executing, append execution/death/no-execution events, set execution status `RESOLVED`, and leave phase advancement to task 08.

- [ ] **Step 4: Add controller**

Expose:

```java
@PostMapping("/nominations/{nominationId}/close")
public Mono<ApiResponse<ClocktowerGameNominationResponse>> closeNomination(...)

@PostMapping("/executions/resolve")
public Mono<ApiResponse<ClocktowerGameExecutionResponse>> resolveExecution(...)
```

Use `ClocktowerReactiveSupport#blocking` and `@AuthenticationPrincipal RbacPrincipal principal`.

- [ ] **Step 5: Run close/resolve tests until they pass**

Run:

```bash
./mvnw -pl be -Dtest=ClocktowerGameNominationServiceTests test
```

Expected: pass.

- [ ] **Step 6: Commit**

```bash
git add be/src/main/java/top/egon/mario/clocktower/game/nomination \
    be/src/test/java/top/egon/mario/clocktower/game/nomination/ClocktowerGameNominationServiceTests.java
git commit -m "feat(clocktower): resolve game executions"
```

---

### Task 5: Full Validation

**Files:**
- Modify only files required to fix validation failures directly caused by tasks 1-4.

- [ ] **Step 1: Run targeted task 07 tests**

Run:

```bash
./mvnw -pl be -Dtest=ClocktowerJpaMappingTests,ClocktowerGameActionServiceTests,ClocktowerGameNominationServiceTests test
```

Expected: pass.

- [ ] **Step 2: Run full backend tests**

Run:

```bash
./mvnw -pl be test
```

Expected: pass.

- [ ] **Step 3: Check repository state**

Run:

```bash
git status --short
```

Expected: only intended files changed, or clean if all implementation commits are complete.

- [ ] **Step 4: Commit validation fixes if any**

If validation required a code fix, commit it:

```bash
git add be/src/main/java/top/egon/mario/clocktower/game \
    be/src/test/java/top/egon/mario/clocktower \
    be/src/main/resources/db/migration/V34__clocktower_game_nomination_vote.sql
git commit -m "test(clocktower): stabilize game nomination flow"
```

If there are no validation fixes, do not create an empty commit.
