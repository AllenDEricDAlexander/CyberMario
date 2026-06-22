# Clocktower Flow Rule Engine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:
> executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a rule-driven Clocktower flow so the storyteller can run first night, day, nomination, execution, night,
and game-end suggestions without invalid phase jumps.

**Architecture:** Keep Java services as the persistent state machine and event writer. Add a focused Drools-backed flow
decision path for transitions, blocking reasons, nomination/vote legality, execution candidates, and victory
suggestions. Model execution resolution separately from death: confirming an execution consumes the day's execution
slot, while death/revival/public-life changes remain explicit rulings with reasons. Remove the old generic
`storytellerAction=ADVANCE_PHASE` path; normal progression uses `/flow/advance`, and forced phase changes use the ruling
API.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring Data JPA, Drools/KIE, JUnit 5, Mockito, AssertJ, React 19, TypeScript,
Ant Design 6, Vitest.

---

## Scope Check

This plan covers the core flow engine only:

- Phase transition decisions.
- Night task blocking and skip.
- Nomination and voting legality.
- Execution candidate and confirmation.
- Separation of execution resolution from death, revival, and public life-state changes.
- Core victory suggestions.
- Storyteller grimoire flow UI.

This plan intentionally does not implement full role ability automation, Agent autoplay changes, jBPM/Kogito, BPMN
workflow execution, or automatic game end.

## File Structure

Backend files to create:

- `be/src/main/java/top/egon/mario/clocktower/flow/ClocktowerFlowService.java`: flow service interface.
- `be/src/main/java/top/egon/mario/clocktower/flow/service/impl/ClocktowerFlowServiceImpl.java`: orchestration for flow
  decisions and state changes.
- `be/src/main/java/top/egon/mario/clocktower/flow/web/ClocktowerFlowController.java`: flow API.
- `be/src/main/java/top/egon/mario/clocktower/flow/dto/ClocktowerFlowResponse.java`: current flow decision response.
- `be/src/main/java/top/egon/mario/clocktower/flow/dto/ClocktowerFlowTransition.java`: normal next-transition enum.
- `be/src/main/java/top/egon/mario/clocktower/flow/dto/NightTaskSummaryResponse.java`: night task summary.
- `be/src/main/java/top/egon/mario/clocktower/flow/dto/NominationSummaryResponse.java`: open nomination summary.
- `be/src/main/java/top/egon/mario/clocktower/flow/dto/ExecutionCandidateResponse.java`: execution candidate and
  no-execution result.
- `be/src/main/java/top/egon/mario/clocktower/flow/dto/ClocktowerExecutionDeathPolicy.java`: explicit optional death
  policy used during execution confirmation.
- `be/src/main/java/top/egon/mario/clocktower/flow/dto/VictoryCandidateResponse.java`: storyteller victory suggestion.
- `be/src/main/java/top/egon/mario/clocktower/flow/dto/SkipNightTaskRequest.java`: skip task request.
- `be/src/main/java/top/egon/mario/clocktower/flow/dto/CloseNominationRequest.java`: close nomination request.
- `be/src/main/java/top/egon/mario/clocktower/flow/dto/ExecutionConfirmRequest.java`: execution/no-execution
  confirmation request.
- `be/src/main/java/top/egon/mario/clocktower/engine/flow/ClocktowerFlowFact.java`: Drools flow facts.
- `be/src/main/java/top/egon/mario/clocktower/engine/flow/ClocktowerFlowDecisionCollector.java`: Drools decision
  collector.
- `be/src/main/java/top/egon/mario/clocktower/engine/flow/ExecutionCandidateDecision.java`: execution decision.
- `be/src/main/java/top/egon/mario/clocktower/engine/flow/VictoryCandidateDecision.java`: victory decision.
- `be/src/main/resources/clocktower/rules/flow/flow-transition.drl`: transition and blocking rules.
- `be/src/test/java/top/egon/mario/clocktower/flow/ClocktowerFlowRuleEngineTests.java`: Drools flow decision tests.
- `be/src/test/java/top/egon/mario/clocktower/flow/ClocktowerFlowServiceTests.java`: service and state transition tests.

Backend files to modify:

- `be/src/main/java/top/egon/mario/clocktower/engine/ClocktowerRuleEngine.java`: add `evaluateFlow`.
- `be/src/main/java/top/egon/mario/clocktower/engine/ClocktowerRuleEngineConfiguration.java`: add flow `KieBase`.
- `be/src/main/java/top/egon/mario/clocktower/common/enums/ClocktowerEventType.java`: add execution-skipped event type
  for no-execution resolution.
- `be/src/main/java/top/egon/mario/clocktower/grimoire/service/impl/ClocktowerGrimoireServiceImpl.java`: remove generic
  phase advance from storyteller action and limit night task sync to night phases.
- `be/src/main/java/top/egon/mario/clocktower/ruling/service/impl/ClocktowerRulingServiceImpl.java`: make
  `EXECUTE_PLAYER` not mutate life status, and make `SKIP_EXECUTION` usable without a nomination.
- `be/src/main/java/top/egon/mario/clocktower/event/repository/ClocktowerEventRepository.java`: add current-day
  execution resolution lookup.
- `be/src/main/java/top/egon/mario/clocktower/action/service/impl/ClocktowerActionServiceImpl.java`: route
  nomination/vote legality through flow service and apply daily/dead-vote rules.
- `be/src/main/java/top/egon/mario/clocktower/grimoire/repository/ClocktowerNominationRepository.java`: add day/status
  query helpers.
- `be/src/main/java/top/egon/mario/clocktower/grimoire/repository/ClocktowerVoteRepository.java`: add dead-vote query
  helper.
- `be/src/main/java/top/egon/mario/clocktower/grimoire/repository/ClocktowerStorytellerTaskRepository.java`: add
  room/night/status query helpers.
- `be/src/main/java/top/egon/mario/clocktower/resource/ClocktowerRbacResourceProvider.java`: register flow APIs.
- `be/src/test/java/top/egon/mario/clocktower/room/ClocktowerRoomTestFactory.java`: update mock repositories with new
  helper methods.
- `be/src/test/java/top/egon/mario/clocktower/action/ClocktowerActionServiceTests.java`: nomination/vote regression
  tests.
- `be/src/test/java/top/egon/mario/clocktower/ruling/ClocktowerRulingServiceTests.java`: execution/death separation
  regression tests.
- `be/src/test/java/top/egon/mario/clocktower/grimoire/ClocktowerStorytellerActionServiceTests.java`: old`ADVANCE_PHASE`
  rejection test.
- `be/src/test/java/top/egon/mario/clocktower/resource/ClocktowerRbacResourceProviderTests.java`: flow API RBAC
  assertions.

Frontend files to modify:

- `fe/src/modules/clocktower/clocktowerTypes.ts`: add flow DTO types.
- `fe/src/modules/clocktower/clocktowerService.ts`: add flow API client functions.
- `fe/src/modules/clocktower/clocktowerService.test.ts`: client endpoint tests.
- `fe/src/modules/clocktower/StorytellerGrimoirePage.tsx`: add "流程" tab and load flow state.
- `fe/src/modules/clocktower/StorytellerGrimoirePage.test.tsx`: flow tab rendering tests.
- `fe/src/modules/clocktower/components/NightChecklist.tsx`: hide current pending night state outside night phases
  through parent flow usage, and render skipped status.
- `fe/src/modules/clocktower/GameRoomPage.tsx`: ensure nomination entry remains available in `DAY` and `NOMINATION`based
  on backend actions.
- `fe/src/modules/clocktower/GameRoomPage.test.tsx`: nomination action regression.

No Flyway migration is planned for this implementation. Existing string status fields, event day/night counters, ruling
rows, and vote/nomination columns support the first version.

## Shared Contracts

Use these exact backend enum values:

```java
public enum ClocktowerFlowTransition {
    COMPLETE_FIRST_NIGHT,
    START_NOMINATION,
    START_EXECUTION,
    START_NIGHT,
    COMPLETE_NIGHT,
    NONE
}
```

Use these exact execution death policy values:

```java
public enum ClocktowerExecutionDeathPolicy {
    NO_CHANGE,
    MARK_DEAD
}
```

Use this new event type:

```text
EXECUTION_SKIPPED
```

Use these exact blocking codes:

```text
CLOCKTOWER_NIGHT_TASKS_PENDING
CLOCKTOWER_OPEN_NOMINATION_EXISTS
CLOCKTOWER_EXECUTION_NOT_RESOLVED
CLOCKTOWER_GAME_ALREADY_ENDED
CLOCKTOWER_FLOW_TRANSITION_UNSUPPORTED
```

Use these exact action rejection codes:

```text
CLOCKTOWER_ADVANCE_PHASE_REPLACED_BY_FLOW
CLOCKTOWER_NOMINATOR_NOT_ALIVE
CLOCKTOWER_NOMINEE_NOT_ALIVE
CLOCKTOWER_NOMINATOR_ALREADY_NOMINATED_TODAY
CLOCKTOWER_NOMINEE_ALREADY_NOMINATED_TODAY
CLOCKTOWER_OPEN_NOMINATION_EXISTS
CLOCKTOWER_VOTE_PHASE_INVALID
CLOCKTOWER_VOTE_ALREADY_CAST
CLOCKTOWER_DEAD_VOTE_ALREADY_SPENT
CLOCKTOWER_EXECUTION_NOTE_REQUIRED
CLOCKTOWER_EXECUTION_DEATH_POLICY_INVALID
CLOCKTOWER_EXECUTION_TARGET_ALREADY_DEAD
CLOCKTOWER_EXECUTION_CANDIDATE_EXISTS
```

Backend targeted validation commands:

```bash
cd /Users/mario/SelfProject/CyberMario/be
./mvnw -Dmaven.build.cache.enabled=false -Dtest='ClocktowerFlowRuleEngineTests,ClocktowerFlowServiceTests' test
./mvnw -Dmaven.build.cache.enabled=false -Dtest='ClocktowerActionServiceTests,ClocktowerRulingServiceTests,ClocktowerStorytellerActionServiceTests,ClocktowerRbacResourceProviderTests' test
./mvnw -Dmaven.build.cache.enabled=false -Dtest='*Clocktower*Tests' test
```

Frontend targeted validation commands:

```bash
cd /Users/mario/SelfProject/CyberMario/fe
npm test -- clocktowerService.test.ts StorytellerGrimoirePage.test.tsx GameRoomPage.test.tsx
npm run typecheck
npx eslint src/modules/clocktower/StorytellerGrimoirePage.tsx src/modules/clocktower/GameRoomPage.tsx src/modules/clocktower/clocktowerService.ts src/modules/clocktower/clocktowerTypes.ts
npx antd lint src/modules/clocktower/StorytellerGrimoirePage.tsx --format json
```

---

### Task 1: Add Flow Decision DTOs And Drools Rule Entry

**Files:**

- Create: `be/src/main/java/top/egon/mario/clocktower/flow/dto/ClocktowerFlowTransition.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/flow/dto/ClocktowerFlowResponse.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/flow/dto/NightTaskSummaryResponse.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/flow/dto/NominationSummaryResponse.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/flow/dto/ExecutionCandidateResponse.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/flow/dto/ClocktowerExecutionDeathPolicy.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/flow/dto/VictoryCandidateResponse.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/engine/flow/ClocktowerFlowFact.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/engine/flow/ClocktowerFlowDecisionCollector.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/engine/flow/ExecutionCandidateDecision.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/engine/flow/VictoryCandidateDecision.java`
- Create: `be/src/main/resources/clocktower/rules/flow/flow-transition.drl`
- Modify: `be/src/main/java/top/egon/mario/clocktower/engine/ClocktowerRuleEngine.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/engine/ClocktowerRuleEngineConfiguration.java`
- Modify: `be/src/test/java/top/egon/mario/clocktower/board/ClocktowerBoardTestFactory.java`
- Test: `be/src/test/java/top/egon/mario/clocktower/flow/ClocktowerFlowRuleEngineTests.java`

- [ ] **Step 1: Write the failing Drools flow engine test**

Create `be/src/test/java/top/egon/mario/clocktower/flow/ClocktowerFlowRuleEngineTests.java`:

```java
package top.egon.mario.clocktower.flow;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import top.egon.mario.clocktower.common.enums.ClocktowerPhase;
import top.egon.mario.clocktower.engine.ClocktowerRuleEngine;
import top.egon.mario.clocktower.engine.ClocktowerRuleEngineConfiguration;
import top.egon.mario.clocktower.engine.flow.ClocktowerFlowFact;
import top.egon.mario.clocktower.flow.dto.ClocktowerFlowTransition;

import static org.assertj.core.api.Assertions.assertThat;

class ClocktowerFlowRuleEngineTests {

    private final ClocktowerRuleEngine engine = new ClocktowerRuleEngine(
            new ClocktowerRuleEngineConfiguration().clocktowerBoardValidationKieBase(new DefaultResourceLoader()),
            new ClocktowerRuleEngineConfiguration().clocktowerFlowKieBase(new DefaultResourceLoader()));

    @Test
    void firstNightIsBlockedByPendingNightTasks() {
        ClocktowerFlowFact fact = new ClocktowerFlowFact(ClocktowerPhase.FIRST_NIGHT, 0, 1,
                2, false, false, false, false, 5, 0, false, false);

        var collector = engine.evaluateFlow(fact);

        assertThat(collector.nextTransition()).isEqualTo(ClocktowerFlowTransition.COMPLETE_FIRST_NIGHT);
        assertThat(collector.advanceAllowed()).isFalse();
        assertThat(collector.blockingReasons()).containsExactly("CLOCKTOWER_NIGHT_TASKS_PENDING");
    }

    @Test
    void dayCanStartNomination() {
        ClocktowerFlowFact fact = new ClocktowerFlowFact(ClocktowerPhase.DAY, 1, 1,
                0, false, false, false, false, 5, 0, false, false);

        var collector = engine.evaluateFlow(fact);

        assertThat(collector.nextTransition()).isEqualTo(ClocktowerFlowTransition.START_NOMINATION);
        assertThat(collector.advanceAllowed()).isTrue();
        assertThat(collector.blockingReasons()).isEmpty();
    }

    @Test
    void executionCannotAdvanceBeforeResolution() {
        ClocktowerFlowFact fact = new ClocktowerFlowFact(ClocktowerPhase.EXECUTION, 1, 1,
                0, false, false, false, false, 5, 0, false, false);

        var collector = engine.evaluateFlow(fact);

        assertThat(collector.nextTransition()).isEqualTo(ClocktowerFlowTransition.START_NIGHT);
        assertThat(collector.advanceAllowed()).isFalse();
        assertThat(collector.blockingReasons()).containsExactly("CLOCKTOWER_EXECUTION_NOT_RESOLVED");
    }
}
```

- [ ] **Step 2: Run the failing rule engine test**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/be
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerFlowRuleEngineTests test
```

Expected: FAIL because `ClocktowerFlowFact`, `ClocktowerFlowTransition`, `ClocktowerExecutionDeathPolicy`,
`clocktowerFlowKieBase`, and `evaluateFlow` do not exist.

- [ ] **Step 3: Add flow DTOs**

Create `be/src/main/java/top/egon/mario/clocktower/flow/dto/ClocktowerFlowTransition.java`:

```java
package top.egon.mario.clocktower.flow.dto;

public enum ClocktowerFlowTransition {
    COMPLETE_FIRST_NIGHT,
    START_NOMINATION,
    START_EXECUTION,
    START_NIGHT,
    COMPLETE_NIGHT,
    NONE
}
```

Create `be/src/main/java/top/egon/mario/clocktower/flow/dto/NightTaskSummaryResponse.java`:

```java
package top.egon.mario.clocktower.flow.dto;

public record NightTaskSummaryResponse(
        int total,
        int pending,
        int done,
        int skipped
) {
}
```

Create `be/src/main/java/top/egon/mario/clocktower/flow/dto/NominationSummaryResponse.java`:

```java
package top.egon.mario.clocktower.flow.dto;

public record NominationSummaryResponse(
        Long nominationId,
        Long nominatorSeatId,
        Long nomineeSeatId,
        int voteCount,
        String status
) {
}
```

Create `be/src/main/java/top/egon/mario/clocktower/flow/dto/ExecutionCandidateResponse.java`:

```java
package top.egon.mario.clocktower.flow.dto;

public record ExecutionCandidateResponse(
        boolean resolved,
        boolean executable,
        Long nominationId,
        Long nomineeSeatId,
        int voteCount,
        int threshold,
        String reason
) {
}
```

Create `be/src/main/java/top/egon/mario/clocktower/flow/dto/ClocktowerExecutionDeathPolicy.java`:

```java
package top.egon.mario.clocktower.flow.dto;

public enum ClocktowerExecutionDeathPolicy {
    NO_CHANGE,
    MARK_DEAD
}
```

Create `be/src/main/java/top/egon/mario/clocktower/flow/dto/VictoryCandidateResponse.java`:

```java
package top.egon.mario.clocktower.flow.dto;

public record VictoryCandidateResponse(
        String winner,
        String reason
) {
}
```

Create `be/src/main/java/top/egon/mario/clocktower/flow/dto/ClocktowerFlowResponse.java`:

```java
package top.egon.mario.clocktower.flow.dto;

import top.egon.mario.clocktower.grimoire.dto.response.GamePhaseResponse;

import java.util.List;

public record ClocktowerFlowResponse(
        Long roomId,
        GamePhaseResponse phase,
        ClocktowerFlowTransition nextTransition,
        boolean advanceAllowed,
        List<String> blockingReasons,
        NightTaskSummaryResponse nightTaskSummary,
        NominationSummaryResponse openNomination,
        ExecutionCandidateResponse executionCandidate,
        VictoryCandidateResponse victoryCandidate
) {
}
```

- [ ] **Step 4: Add flow engine facts and collector**

Create `be/src/main/java/top/egon/mario/clocktower/engine/flow/ClocktowerFlowFact.java`:

```java
package top.egon.mario.clocktower.engine.flow;

import top.egon.mario.clocktower.common.enums.ClocktowerPhase;

public record ClocktowerFlowFact(
        ClocktowerPhase phase,
        int dayNo,
        int nightNo,
        int pendingNightTaskCount,
        boolean openNominationExists,
        boolean executionResolved,
        boolean demonAlive,
        boolean allDemonsDead,
        int realAliveCount,
        int executionTopVoteCount,
        boolean executionTopVoteTied,
        boolean executionCandidateExists
) {
}
```

Create `be/src/main/java/top/egon/mario/clocktower/engine/flow/ExecutionCandidateDecision.java`:

```java
package top.egon.mario.clocktower.engine.flow;

public record ExecutionCandidateDecision(
        boolean executable,
        String reason
) {
}
```

Create `be/src/main/java/top/egon/mario/clocktower/engine/flow/VictoryCandidateDecision.java`:

```java
package top.egon.mario.clocktower.engine.flow;

public record VictoryCandidateDecision(
        String winner,
        String reason
) {
}
```

Create `be/src/main/java/top/egon/mario/clocktower/engine/flow/ClocktowerFlowDecisionCollector.java`:

```java
package top.egon.mario.clocktower.engine.flow;

import top.egon.mario.clocktower.flow.dto.ClocktowerFlowTransition;

import java.util.ArrayList;
import java.util.List;

public class ClocktowerFlowDecisionCollector {

    private ClocktowerFlowTransition nextTransition = ClocktowerFlowTransition.NONE;
    private boolean advanceAllowed = true;
    private final List<String> blockingReasons = new ArrayList<>();
    private ExecutionCandidateDecision executionCandidate;
    private VictoryCandidateDecision victoryCandidate;

    public ClocktowerFlowTransition nextTransition() {
        return nextTransition;
    }

    public boolean advanceAllowed() {
        return advanceAllowed;
    }

    public List<String> blockingReasons() {
        return List.copyOf(blockingReasons);
    }

    public ExecutionCandidateDecision executionCandidate() {
        return executionCandidate;
    }

    public VictoryCandidateDecision victoryCandidate() {
        return victoryCandidate;
    }

    public void allow(ClocktowerFlowTransition transition) {
        this.nextTransition = transition;
        this.advanceAllowed = true;
    }

    public void block(ClocktowerFlowTransition transition, String reason) {
        this.nextTransition = transition;
        this.advanceAllowed = false;
        if (!blockingReasons.contains(reason)) {
            blockingReasons.add(reason);
        }
    }

    public void executionCandidate(ExecutionCandidateDecision decision) {
        this.executionCandidate = decision;
    }

    public void victoryCandidate(VictoryCandidateDecision decision) {
        this.victoryCandidate = decision;
    }
}
```

- [ ] **Step 5: Add the flow DRL**

Create `be/src/main/resources/clocktower/rules/flow/flow-transition.drl`:

```java
package top.egon.mario.clocktower.rules.flow;

import top.egon.mario.clocktower.common.enums.ClocktowerPhase;
import top.egon.mario.clocktower.engine.flow.ClocktowerFlowFact;
import top.egon.mario.clocktower.engine.flow.ClocktowerFlowDecisionCollector;
import top.egon.mario.clocktower.engine.flow.VictoryCandidateDecision;
import top.egon.mario.clocktower.flow.dto.ClocktowerFlowTransition;

rule "first night blocked by pending tasks"
when
$fact :

ClocktowerFlowFact(phase ==ClocktowerPhase.FIRST_NIGHT, pendingNightTaskCount >0)

$collector :

ClocktowerFlowDecisionCollector()

then
    $collector.

block(ClocktowerFlowTransition.COMPLETE_FIRST_NIGHT, "CLOCKTOWER_NIGHT_TASKS_PENDING");

end

rule "first night can complete"
when
$fact :

ClocktowerFlowFact(phase ==ClocktowerPhase.FIRST_NIGHT, pendingNightTaskCount ==0)

$collector :

ClocktowerFlowDecisionCollector()

then
    $collector.

allow(ClocktowerFlowTransition.COMPLETE_FIRST_NIGHT);

end

rule "day can start nomination"
when
$fact :

ClocktowerFlowFact(phase ==ClocktowerPhase.DAY)

$collector :

ClocktowerFlowDecisionCollector()

then
    $collector.

allow(ClocktowerFlowTransition.START_NOMINATION);

end

rule "nomination blocked by open nomination"
when
$fact :

ClocktowerFlowFact(phase ==ClocktowerPhase.NOMINATION, openNominationExists ==true)

$collector :

ClocktowerFlowDecisionCollector()

then
    $collector.

block(ClocktowerFlowTransition.START_EXECUTION, "CLOCKTOWER_OPEN_NOMINATION_EXISTS");

end

rule "nomination can start execution"
when
$fact :

ClocktowerFlowFact(phase ==ClocktowerPhase.NOMINATION, openNominationExists ==false)

$collector :

ClocktowerFlowDecisionCollector()

then
    $collector.

allow(ClocktowerFlowTransition.START_EXECUTION);

end

rule "execution blocked until resolved"
when
$fact :

ClocktowerFlowFact(phase ==ClocktowerPhase.EXECUTION, executionResolved ==false)

$collector :

ClocktowerFlowDecisionCollector()

then
    $collector.

block(ClocktowerFlowTransition.START_NIGHT, "CLOCKTOWER_EXECUTION_NOT_RESOLVED");

end

rule "execution can start night"
when
$fact :

ClocktowerFlowFact(phase ==ClocktowerPhase.EXECUTION, executionResolved ==true)

$collector :

ClocktowerFlowDecisionCollector()

then
    $collector.

allow(ClocktowerFlowTransition.START_NIGHT);

end

rule "night blocked by pending tasks"
when
$fact :

ClocktowerFlowFact(phase ==ClocktowerPhase.NIGHT, pendingNightTaskCount >0)

$collector :

ClocktowerFlowDecisionCollector()

then
    $collector.

block(ClocktowerFlowTransition.COMPLETE_NIGHT, "CLOCKTOWER_NIGHT_TASKS_PENDING");

end

rule "night can complete"
when
$fact :

ClocktowerFlowFact(phase ==ClocktowerPhase.NIGHT, pendingNightTaskCount ==0)

$collector :

ClocktowerFlowDecisionCollector()

then
    $collector.

allow(ClocktowerFlowTransition.COMPLETE_NIGHT);

end

rule "ended has no transition"
when
$fact :

ClocktowerFlowFact(phase ==ClocktowerPhase.ENDED)

$collector :

ClocktowerFlowDecisionCollector()

then
    $collector.

block(ClocktowerFlowTransition.NONE, "CLOCKTOWER_GAME_ALREADY_ENDED");

end

rule "all demons dead suggests good victory"
when
$fact :

ClocktowerFlowFact(allDemonsDead ==true)

$collector :

ClocktowerFlowDecisionCollector()

then
    $collector.

victoryCandidate(new VictoryCandidateDecision("GOOD", "ALL_DEMONS_DEAD"));
end

rule "two alive with demon suggests evil victory"
when
$fact :

ClocktowerFlowFact(realAliveCount <=2, demonAlive ==true)

$collector :

ClocktowerFlowDecisionCollector()

then
    $collector.

victoryCandidate(new VictoryCandidateDecision("EVIL", "TWO_ALIVE_WITH_LIVING_DEMON"));
end
```

- [ ] **Step 6: Wire flow KieBase and engine method**

Modify `be/src/main/java/top/egon/mario/clocktower/engine/ClocktowerRuleEngineConfiguration.java`.

Change the existing board bean method from package-private to public so cross-package tests can build the engine
directly:

```java

@Bean
public KieBase clocktowerBoardValidationKieBase(ResourceLoader resourceLoader) {
    return new KieHelper()
            .addResource(loadDrlResource(resourceLoader, BOARD_VALIDATION_RULE), ResourceType.DRL)
            .build();
}
```

Add the flow rule path and flow bean:

```java
private static final String FLOW_TRANSITION_RULE = "clocktower/rules/flow/flow-transition.drl";

@Bean
public KieBase clocktowerFlowKieBase(ResourceLoader resourceLoader) {
    return new KieHelper()
            .addResource(loadDrlResource(resourceLoader, FLOW_TRANSITION_RULE), ResourceType.DRL)
            .build();
}
```

Modify `be/src/main/java/top/egon/mario/clocktower/engine/ClocktowerRuleEngine.java` so the constructor has two`KieBase`
dependencies and add `evaluateFlow`:

```java
private final KieBase clocktowerBoardValidationKieBase;
private final KieBase clocktowerFlowKieBase;

public ClocktowerFlowDecisionCollector evaluateFlow(ClocktowerFlowFact fact) {
    ClocktowerFlowDecisionCollector collector = new ClocktowerFlowDecisionCollector();
    try (var session = clocktowerFlowKieBase.newKieSession()) {
        session.insert(fact);
        session.insert(collector);
        session.fireAllRules();
    }
    return collector;
}
```

Add imports:

```java
import top.egon.mario.clocktower.engine.flow.ClocktowerFlowDecisionCollector;
import top.egon.mario.clocktower.engine.flow.ClocktowerFlowFact;
```

Modify `be/src/test/java/top/egon/mario/clocktower/board/ClocktowerBoardTestFactory.java` so the test subclass
constructor matches the new parent constructor:

```java
private TestClocktowerRuleEngine() {
    super(null, null);
}
```

- [ ] **Step 7: Run the rule engine test**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/be
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerFlowRuleEngineTests test
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add \
  be/src/main/java/top/egon/mario/clocktower/flow/dto/ClocktowerFlowTransition.java \
  be/src/main/java/top/egon/mario/clocktower/flow/dto/ClocktowerFlowResponse.java \
  be/src/main/java/top/egon/mario/clocktower/flow/dto/NightTaskSummaryResponse.java \
  be/src/main/java/top/egon/mario/clocktower/flow/dto/NominationSummaryResponse.java \
  be/src/main/java/top/egon/mario/clocktower/flow/dto/ExecutionCandidateResponse.java \
  be/src/main/java/top/egon/mario/clocktower/flow/dto/ClocktowerExecutionDeathPolicy.java \
  be/src/main/java/top/egon/mario/clocktower/flow/dto/VictoryCandidateResponse.java \
  be/src/main/java/top/egon/mario/clocktower/engine/flow/ClocktowerFlowFact.java \
  be/src/main/java/top/egon/mario/clocktower/engine/flow/ClocktowerFlowDecisionCollector.java \
  be/src/main/java/top/egon/mario/clocktower/engine/flow/ExecutionCandidateDecision.java \
  be/src/main/java/top/egon/mario/clocktower/engine/flow/VictoryCandidateDecision.java \
  be/src/main/resources/clocktower/rules/flow/flow-transition.drl \
  be/src/main/java/top/egon/mario/clocktower/engine/ClocktowerRuleEngine.java \
  be/src/main/java/top/egon/mario/clocktower/engine/ClocktowerRuleEngineConfiguration.java \
  be/src/test/java/top/egon/mario/clocktower/board/ClocktowerBoardTestFactory.java \
  be/src/test/java/top/egon/mario/clocktower/flow/ClocktowerFlowRuleEngineTests.java
git commit -m "feat(clocktower): add flow decision rules"
```

---

### Task 2: Add Flow Service Read API And RBAC

**Files:**

- Create: `be/src/main/java/top/egon/mario/clocktower/flow/ClocktowerFlowService.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/flow/service/impl/ClocktowerFlowServiceImpl.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/flow/web/ClocktowerFlowController.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/grimoire/repository/ClocktowerStorytellerTaskRepository.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/grimoire/repository/ClocktowerNominationRepository.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/resource/ClocktowerRbacResourceProvider.java`
- Modify: `be/src/test/java/top/egon/mario/clocktower/room/ClocktowerRoomTestFactory.java`
- Test: `be/src/test/java/top/egon/mario/clocktower/flow/ClocktowerFlowServiceTests.java`
- Test: `be/src/test/java/top/egon/mario/clocktower/resource/ClocktowerRbacResourceProviderTests.java`

- [ ] **Step 1: Write the failing service read test**

Create `be/src/test/java/top/egon/mario/clocktower/flow/ClocktowerFlowServiceTests.java`:

```java
package top.egon.mario.clocktower.flow;

import org.junit.jupiter.api.Test;
import top.egon.mario.clocktower.common.enums.ClocktowerPhase;
import top.egon.mario.clocktower.flow.dto.ClocktowerFlowTransition;
import top.egon.mario.clocktower.grimoire.service.impl.ClocktowerGrimoireServiceImpl;
import top.egon.mario.clocktower.room.ClocktowerRoomTestFactory;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomCreateRequest;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomJoinRequest;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomStartRequest;
import top.egon.mario.clocktower.room.dto.request.RoleAssignmentRequest;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ClocktowerFlowServiceTests {

    private final ClocktowerRoomTestFactory.Context context = ClocktowerRoomTestFactory.context();
    private final ClocktowerFlowService flowService = TestClocktowerFlowServices.flowService(context);
    private final ClocktowerGrimoireServiceImpl grimoireService = TestClocktowerFlowServices.grimoireService(context);

    @Test
    void firstNightFlowReportsPendingTasksAndBlocksAdvance() {
        Long roomId = startedRoom();
        grimoireService.getGrimoire(roomId, storyteller());

        var flow = flowService.getFlow(roomId, storyteller());

        assertThat(flow.phase().phase()).isEqualTo(ClocktowerPhase.FIRST_NIGHT);
        assertThat(flow.nextTransition()).isEqualTo(ClocktowerFlowTransition.COMPLETE_FIRST_NIGHT);
        assertThat(flow.advanceAllowed()).isFalse();
        assertThat(flow.blockingReasons()).containsExactly("CLOCKTOWER_NIGHT_TASKS_PENDING");
        assertThat(flow.nightTaskSummary().pending()).isGreaterThan(0);
    }

    private Long startedRoom() {
        var room = context.roomService().create(new ClocktowerRoomCreateRequest("流程测试",
                top.egon.mario.clocktower.common.enums.ClocktowerScriptCode.TROUBLE_BREWING, 5,
                null, null, List.of("EMPATH", "CHEF", "MONK", "POISONER", "IMP"),
                "HUMAN", false, true, 0), storyteller());
        for (int i = 0; i < room.seats().size(); i++) {
            context.roomService().join(room.roomId(), new ClocktowerRoomJoinRequest(i + 1, "P" + (i + 1), null),
                    principal((long) i + 2, "p" + (i + 1)));
        }
        var joined = context.roomService().get(room.roomId());
        context.roomService().start(room.roomId(), new ClocktowerRoomStartRequest(List.of(
                new RoleAssignmentRequest(joined.seats().get(0).seatId(), "EMPATH"),
                new RoleAssignmentRequest(joined.seats().get(1).seatId(), "CHEF"),
                new RoleAssignmentRequest(joined.seats().get(2).seatId(), "MONK"),
                new RoleAssignmentRequest(joined.seats().get(3).seatId(), "POISONER"),
                new RoleAssignmentRequest(joined.seats().get(4).seatId(), "IMP")
        ), false), storyteller());
        return room.roomId();
    }

    private static RbacPrincipal storyteller() {
        return principal(1L, "storyteller");
    }

    private static RbacPrincipal principal(Long userId, String username) {
        return new RbacPrincipal(userId, username,
                Set.of("CLOCKTOWER_STORYTELLER", "CLOCKTOWER_PLAYER"), Set.of(), "v1");
    }
}
```

Create `be/src/test/java/top/egon/mario/clocktower/flow/TestClocktowerFlowServices.java`:

```java
package top.egon.mario.clocktower.flow;

import org.springframework.core.io.DefaultResourceLoader;
import top.egon.mario.clocktower.engine.ClocktowerRuleEngine;
import top.egon.mario.clocktower.engine.ClocktowerRuleEngineConfiguration;
import top.egon.mario.clocktower.flow.service.impl.ClocktowerFlowServiceImpl;
import top.egon.mario.clocktower.grimoire.service.impl.ClocktowerGrimoireServiceImpl;
import top.egon.mario.clocktower.room.ClocktowerRoomTestFactory;

final class TestClocktowerFlowServices {

    private TestClocktowerFlowServices() {
    }

    static ClocktowerFlowService flowService(ClocktowerRoomTestFactory.Context context) {
        ClocktowerRuleEngineConfiguration configuration = new ClocktowerRuleEngineConfiguration();
        ClocktowerRuleEngine ruleEngine = new ClocktowerRuleEngine(
                configuration.clocktowerBoardValidationKieBase(new DefaultResourceLoader()),
                configuration.clocktowerFlowKieBase(new DefaultResourceLoader()));
        return new ClocktowerFlowServiceImpl(context.roomRepository(), context.seatRepository(),
                context.storytellerTaskRepository(), context.nominationRepository(), context.voteRepository(),
                context.roleRepository(), context.eventService(), ruleEngine);
    }

    static ClocktowerGrimoireServiceImpl grimoireService(ClocktowerRoomTestFactory.Context context) {
        return new ClocktowerGrimoireServiceImpl(context.roomRepository(), context.seatRepository(),
                context.grimoireEntryRepository(), context.markerRepository(), context.storytellerTaskRepository(),
                context.nightOrderRepository(), context.roleRepository(), context.eventService());
    }
}
```

- [ ] **Step 2: Run the failing service test**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/be
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerFlowServiceTests test
```

Expected: FAIL because `ClocktowerFlowService` and `ClocktowerFlowServiceImpl` do not exist.

- [ ] **Step 3: Add repository helpers**

Modify `be/src/main/java/top/egon/mario/clocktower/grimoire/repository/ClocktowerStorytellerTaskRepository.java`:

```java
List<ClocktowerStorytellerTaskPo> findByRoomIdAndNightNoAndDeletedFalseOrderBySortOrderAsc(Long roomId, int nightNo);
```

Modify `be/src/main/java/top/egon/mario/clocktower/grimoire/repository/ClocktowerNominationRepository.java`:

```java
List<ClocktowerNominationPo> findByRoomIdAndDayNoAndDeletedFalseOrderByIdAsc(Long roomId, int dayNo);

List<ClocktowerNominationPo> findByRoomIdAndDayNoAndStatusAndDeletedFalseOrderByIdAsc(
        Long roomId, int dayNo, String status);
```

Update `ClocktowerRoomTestFactory` mocks with matching answers:

```java
when(taskRepository.findByRoomIdAndNightNoAndDeletedFalseOrderBySortOrderAsc(any(),anyInt()))
        .

thenAnswer(invocation ->tasks.

stream()
                .

filter(task ->!task.

isDeleted()
                        &&task.

getRoomId().

equals(invocation.getArgument(0))
        &&task.

getNightNo() ==(Integer)invocation.

getArgument(1))
        .

sorted(Comparator.comparing(ClocktowerStorytellerTaskPo::getSortOrder))
        .

toList());

when(nominationRepository.findByRoomIdAndDayNoAndDeletedFalseOrderByIdAsc(any(),anyInt()))
        .

thenAnswer(invocation ->nominations.

stream()
                .

filter(nomination ->!nomination.

isDeleted()
                        &&nomination.

getRoomId().

equals(invocation.getArgument(0))
        &&nomination.

getDayNo() ==(Integer)invocation.

getArgument(1))
        .

sorted(Comparator.comparing(ClocktowerNominationPo::getId))
        .

toList());

when(nominationRepository.findByRoomIdAndDayNoAndStatusAndDeletedFalseOrderByIdAsc(any(),anyInt(),

any()))
        .

thenAnswer(invocation ->nominations.

stream()
                .

filter(nomination ->!nomination.

isDeleted()
                        &&nomination.

getRoomId().

equals(invocation.getArgument(0))
        &&nomination.

getDayNo() ==(Integer)invocation.

getArgument(1)
                        &&nomination.

getStatus().

equals(invocation.getArgument(2)))
        .

sorted(Comparator.comparing(ClocktowerNominationPo::getId))
        .

toList());
```

- [ ] **Step 4: Add flow service interface**

Create `be/src/main/java/top/egon/mario/clocktower/flow/ClocktowerFlowService.java`:

```java
package top.egon.mario.clocktower.flow;

import top.egon.mario.clocktower.flow.dto.ClocktowerFlowResponse;
import top.egon.mario.clocktower.flow.dto.CloseNominationRequest;
import top.egon.mario.clocktower.flow.dto.ExecutionConfirmRequest;
import top.egon.mario.clocktower.flow.dto.SkipNightTaskRequest;
import top.egon.mario.rbac.service.security.RbacPrincipal;

public interface ClocktowerFlowService {

    ClocktowerFlowResponse getFlow(Long roomId, RbacPrincipal principal);

    ClocktowerFlowResponse advance(Long roomId, RbacPrincipal principal);

    ClocktowerFlowResponse skipNightTask(Long roomId, Long taskId, SkipNightTaskRequest request, RbacPrincipal principal);

    ClocktowerFlowResponse closeNomination(Long roomId, Long nominationId, CloseNominationRequest request,
                                           RbacPrincipal principal);

    ClocktowerFlowResponse confirmExecution(Long roomId, ExecutionConfirmRequest request, RbacPrincipal principal);
}
```

- [ ] **Step 5: Add request DTOs**

Create `be/src/main/java/top/egon/mario/clocktower/flow/dto/SkipNightTaskRequest.java`:

```java
package top.egon.mario.clocktower.flow.dto;

public record SkipNightTaskRequest(String reason) {
}
```

Create `be/src/main/java/top/egon/mario/clocktower/flow/dto/CloseNominationRequest.java`:

```java
package top.egon.mario.clocktower.flow.dto;

public record CloseNominationRequest(String note) {
}
```

Create `be/src/main/java/top/egon/mario/clocktower/flow/dto/ExecutionConfirmRequest.java`:

```java
package top.egon.mario.clocktower.flow.dto;

public record ExecutionConfirmRequest(
        Boolean execute,
        ClocktowerExecutionDeathPolicy deathPolicy,
        String note
) {
}
```

- [ ] **Step 6: Add minimal flow service implementation for getFlow**

Create `be/src/main/java/top/egon/mario/clocktower/flow/service/impl/ClocktowerFlowServiceImpl.java`:

```java
package top.egon.mario.clocktower.flow.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.egon.mario.clocktower.common.ClocktowerAccess;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.clocktower.common.enums.ClocktowerPhase;
import top.egon.mario.clocktower.engine.ClocktowerRuleEngine;
import top.egon.mario.clocktower.engine.flow.ClocktowerFlowFact;
import top.egon.mario.clocktower.flow.service.ClocktowerFlowService;
import top.egon.mario.clocktower.flow.dto.ClocktowerFlowResponse;
import top.egon.mario.clocktower.flow.dto.ClocktowerFlowTransition;
import top.egon.mario.clocktower.flow.dto.CloseNominationRequest;
import top.egon.mario.clocktower.flow.dto.ExecutionCandidateResponse;
import top.egon.mario.clocktower.flow.dto.ExecutionConfirmRequest;
import top.egon.mario.clocktower.flow.dto.NightTaskSummaryResponse;
import top.egon.mario.clocktower.flow.dto.NominationSummaryResponse;
import top.egon.mario.clocktower.flow.dto.SkipNightTaskRequest;
import top.egon.mario.clocktower.flow.dto.VictoryCandidateResponse;
import top.egon.mario.clocktower.grimoire.dto.response.GamePhaseResponse;
import top.egon.mario.clocktower.grimoire.po.ClocktowerNominationPo;
import top.egon.mario.clocktower.grimoire.po.ClocktowerStorytellerTaskPo;
import top.egon.mario.clocktower.grimoire.repository.ClocktowerNominationRepository;
import top.egon.mario.clocktower.grimoire.repository.ClocktowerStorytellerTaskRepository;
import top.egon.mario.clocktower.grimoire.repository.ClocktowerVoteRepository;
import top.egon.mario.clocktower.room.po.ClocktowerRoomPo;
import top.egon.mario.clocktower.room.po.ClocktowerSeatPo;
import top.egon.mario.clocktower.room.repository.ClocktowerRoomRepository;
import top.egon.mario.clocktower.room.repository.ClocktowerSeatRepository;
import top.egon.mario.clocktower.script.repository.ClocktowerRoleRepository;
import top.egon.mario.clocktower.event.service.ClocktowerEventService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ClocktowerFlowServiceImpl implements ClocktowerFlowService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_DONE = "DONE";
    private static final String STATUS_SKIPPED = "SKIPPED";
    private static final String NOMINATION_OPEN = "OPEN";
    private static final String NOMINATION_CLOSED = "CLOSED";

    private final ClocktowerRoomRepository roomRepository;
    private final ClocktowerSeatRepository seatRepository;
    private final ClocktowerStorytellerTaskRepository taskRepository;
    private final ClocktowerNominationRepository nominationRepository;
    private final ClocktowerVoteRepository voteRepository;
    private final ClocktowerRoleRepository roleRepository;
    private final ClocktowerEventService eventService;
    private final ClocktowerRuleEngine ruleEngine;

    @Override
    @Transactional(readOnly = true)
    public ClocktowerFlowResponse getFlow(Long roomId, RbacPrincipal principal) {
        ClocktowerRoomPo room = room(roomId);
        ClocktowerAccess.requireStoryteller(room, principal);
        return buildFlow(room);
    }

    @Override
    @Transactional
    public ClocktowerFlowResponse advance(Long roomId, RbacPrincipal principal) {
        throw new ClocktowerException("CLOCKTOWER_FLOW_TRANSITION_UNSUPPORTED");
    }

    @Override
    @Transactional
    public ClocktowerFlowResponse skipNightTask(Long roomId, Long taskId, SkipNightTaskRequest request,
                                                RbacPrincipal principal) {
        throw new ClocktowerException("CLOCKTOWER_FLOW_TRANSITION_UNSUPPORTED");
    }

    @Override
    @Transactional
    public ClocktowerFlowResponse closeNomination(Long roomId, Long nominationId, CloseNominationRequest request,
                                                  RbacPrincipal principal) {
        throw new ClocktowerException("CLOCKTOWER_FLOW_TRANSITION_UNSUPPORTED");
    }

    @Override
    @Transactional
    public ClocktowerFlowResponse confirmExecution(Long roomId, ExecutionConfirmRequest request,
                                                   RbacPrincipal principal) {
        throw new ClocktowerException("CLOCKTOWER_FLOW_TRANSITION_UNSUPPORTED");
    }

    private ClocktowerFlowResponse buildFlow(ClocktowerRoomPo room) {
        List<ClocktowerStorytellerTaskPo> nightTasks = isNight(room.getPhase())
                ? taskRepository.findByRoomIdAndNightNoAndDeletedFalseOrderBySortOrderAsc(
                room.getId(), room.getCurrentNightNo())
                : List.of();
        NightTaskSummaryResponse nightSummary = nightSummary(nightTasks);
        List<ClocktowerNominationPo> dayNominations = nominationRepository
                .findByRoomIdAndDayNoAndDeletedFalseOrderByIdAsc(room.getId(), room.getCurrentDayNo());
        NominationSummaryResponse openNomination = dayNominations.stream()
                .filter(nomination -> NOMINATION_OPEN.equals(nomination.getStatus()))
                .max(Comparator.comparing(ClocktowerNominationPo::getId))
                .map(this::nominationSummary)
                .orElse(null);
        List<ClocktowerSeatPo> seats = seatRepository.findByRoomIdAndDeletedFalseOrderBySeatNoAsc(room.getId());
        int aliveCount = (int) seats.stream().filter(seat -> "ALIVE".equals(seat.getLifeStatus())).count();
        ExecutionCandidateResponse execution = executionCandidate(room, dayNominations, aliveCount);
        ClocktowerFlowFact fact = new ClocktowerFlowFact(room.getPhase(), room.getCurrentDayNo(),
                room.getCurrentNightNo(), nightSummary.pending(), openNomination != null,
                execution.resolved(), demonAlive(seats), allDemonsDead(seats), aliveCount,
                execution.voteCount(), false, execution.executable());
        var decision = ruleEngine.evaluateFlow(fact);
        VictoryCandidateResponse victory = decision.victoryCandidate() == null ? null
                : new VictoryCandidateResponse(decision.victoryCandidate().winner(),
                decision.victoryCandidate().reason());
        return new ClocktowerFlowResponse(room.getId(), GamePhaseResponse.from(room), decision.nextTransition(),
                decision.advanceAllowed(), decision.blockingReasons(), nightSummary, openNomination, execution, victory);
    }

    private ExecutionCandidateResponse executionCandidate(ClocktowerRoomPo room,
                                                          List<ClocktowerNominationPo> nominations,
                                                          int aliveCount) {
        int threshold = (aliveCount + 1) / 2;
        List<ClocktowerNominationPo> closed = nominations.stream()
                .filter(nomination -> NOMINATION_CLOSED.equals(nomination.getStatus()))
                .toList();
        if (executionResolved(room, closed)) {
            return new ExecutionCandidateResponse(true, false, null, null, 0, threshold, "EXECUTION_CONFIRMED");
        }
        if (room.getPhase() != ClocktowerPhase.EXECUTION || closed.isEmpty()) {
            return new ExecutionCandidateResponse(false, false, null, null, 0, threshold, "NO_CLOSED_NOMINATION");
        }
        int topVote = closed.stream().mapToInt(ClocktowerNominationPo::getVoteCount).max().orElse(0);
        if (topVote < threshold) {
            return new ExecutionCandidateResponse(false, false, null, null, topVote, threshold, "BELOW_THRESHOLD");
        }
        List<ClocktowerNominationPo> top = closed.stream()
                .filter(nomination -> nomination.getVoteCount() == topVote)
                .toList();
        if (top.size() != 1) {
            return new ExecutionCandidateResponse(false, false, null, null, topVote, threshold, "TIED_TOP_VOTE");
        }
        ClocktowerNominationPo candidate = top.getFirst();
        return new ExecutionCandidateResponse(false, true, candidate.getId(), candidate.getNomineeSeatId(),
                candidate.getVoteCount(), threshold, "EXECUTION_CANDIDATE");
    }

    private boolean executionResolved(ClocktowerRoomPo room, List<ClocktowerNominationPo> closed) {
        return closed.stream().anyMatch(ClocktowerNominationPo::isExecuted);
    }

    private NightTaskSummaryResponse nightSummary(List<ClocktowerStorytellerTaskPo> tasks) {
        int pending = (int) tasks.stream().filter(task -> STATUS_PENDING.equals(task.getStatus())).count();
        int done = (int) tasks.stream().filter(task -> STATUS_DONE.equals(task.getStatus())).count();
        int skipped = (int) tasks.stream().filter(task -> STATUS_SKIPPED.equals(task.getStatus())).count();
        return new NightTaskSummaryResponse(tasks.size(), pending, done, skipped);
    }

    private NominationSummaryResponse nominationSummary(ClocktowerNominationPo nomination) {
        return new NominationSummaryResponse(nomination.getId(), nomination.getNominatorSeatId(),
                nomination.getNomineeSeatId(), nomination.getVoteCount(), nomination.getStatus());
    }

    private boolean demonAlive(List<ClocktowerSeatPo> seats) {
        return seats.stream().anyMatch(seat -> "DEMON".equals(String.valueOf(seat.getRoleType()))
                && "ALIVE".equals(seat.getLifeStatus()));
    }

    private boolean allDemonsDead(List<ClocktowerSeatPo> seats) {
        return seats.stream().anyMatch(seat -> "DEMON".equals(String.valueOf(seat.getRoleType())))
                && seats.stream().filter(seat -> "DEMON".equals(String.valueOf(seat.getRoleType())))
                .noneMatch(seat -> "ALIVE".equals(seat.getLifeStatus()));
    }

    private static boolean isNight(ClocktowerPhase phase) {
        return phase == ClocktowerPhase.FIRST_NIGHT || phase == ClocktowerPhase.NIGHT;
    }

    private ClocktowerRoomPo room(Long roomId) {
        return roomRepository.findByIdAndDeletedFalse(roomId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_ROOM_NOT_FOUND"));
    }
}
```

- [ ] **Step 7: Add flow controller**

Create `be/src/main/java/top/egon/mario/clocktower/flow/web/ClocktowerFlowController.java`:

```java
package top.egon.mario.clocktower.flow.web;

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
import top.egon.mario.clocktower.flow.service.ClocktowerFlowService;
import top.egon.mario.clocktower.flow.dto.ClocktowerFlowResponse;
import top.egon.mario.clocktower.flow.dto.CloseNominationRequest;
import top.egon.mario.clocktower.flow.dto.ExecutionConfirmRequest;
import top.egon.mario.clocktower.flow.dto.SkipNightTaskRequest;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/clocktower/rooms/{roomId}")
@Validated
public class ClocktowerFlowController extends ClocktowerReactiveSupport {

    private final ClocktowerFlowService flowService;

    @GetMapping("/flow")
    public Mono<ApiResponse<ClocktowerFlowResponse>> getFlow(@PathVariable Long roomId,
                                                             @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> flowService.getFlow(roomId, principal));
    }

    @PostMapping("/flow/advance")
    public Mono<ApiResponse<ClocktowerFlowResponse>> advance(@PathVariable Long roomId,
                                                             @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> flowService.advance(roomId, principal));
    }

    @PostMapping("/night-tasks/{taskId}/skip")
    public Mono<ApiResponse<ClocktowerFlowResponse>> skipNightTask(@PathVariable Long roomId,
                                                                   @PathVariable Long taskId,
                                                                   @RequestBody SkipNightTaskRequest request,
                                                                   @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> flowService.skipNightTask(roomId, taskId, request, principal));
    }

    @PostMapping("/nominations/{nominationId}/close")
    public Mono<ApiResponse<ClocktowerFlowResponse>> closeNomination(@PathVariable Long roomId,
                                                                     @PathVariable Long nominationId,
                                                                     @RequestBody CloseNominationRequest request,
                                                                     @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> flowService.closeNomination(roomId, nominationId, request, principal));
    }

    @PostMapping("/execution/confirm")
    public Mono<ApiResponse<ClocktowerFlowResponse>> confirmExecution(@PathVariable Long roomId,
                                                                      @RequestBody ExecutionConfirmRequest request,
                                                                      @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> flowService.confirmExecution(roomId, request, principal));
    }
}
```

- [ ] **Step 8: Add RBAC resources**

Modify `ClocktowerRbacResourceProvider.resources()` by adding:

```java
resources.add(api("api:clocktower:rooms:storyteller:flow", "Clocktower storyteller flow","ANY",
        "/api/clocktower/rooms/*/flow/**",ApiRiskLevel.HIGH));
        resources.

add(api("api:clocktower:rooms:storyteller:night-task", "Clocktower storyteller night task","ANY",
        "/api/clocktower/rooms/*/night-tasks/**",ApiRiskLevel.HIGH));
        resources.

add(api("api:clocktower:rooms:storyteller:nomination", "Clocktower storyteller nomination","ANY",
        "/api/clocktower/rooms/*/nominations/**",ApiRiskLevel.HIGH));
        resources.

add(api("api:clocktower:rooms:storyteller:execution", "Clocktower storyteller execution","ANY",
        "/api/clocktower/rooms/*/execution/**",ApiRiskLevel.HIGH));
```

Modify the `CLOCKTOWER_STORYTELLER` preset list by adding those four permission codes.

Add assertions to `ClocktowerRbacResourceProviderTests`:

```java
assertThat(codes).

contains(
        "api:clocktower:rooms:storyteller:flow",
                "api:clocktower:rooms:storyteller:night-task",
                "api:clocktower:rooms:storyteller:nomination",
                "api:clocktower:rooms:storyteller:execution");

assertThat(storyteller.permissionCodes()).

contains(
        "api:clocktower:rooms:storyteller:flow",
                "api:clocktower:rooms:storyteller:night-task",
                "api:clocktower:rooms:storyteller:nomination",
                "api:clocktower:rooms:storyteller:execution");
```

- [ ] **Step 9: Run service and RBAC tests**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/be
./mvnw -Dmaven.build.cache.enabled=false -Dtest='ClocktowerFlowServiceTests,ClocktowerRbacResourceProviderTests' test
```

Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add \
  be/src/main/java/top/egon/mario/clocktower/flow \
  be/src/main/java/top/egon/mario/clocktower/grimoire/repository/ClocktowerStorytellerTaskRepository.java \
  be/src/main/java/top/egon/mario/clocktower/grimoire/repository/ClocktowerNominationRepository.java \
  be/src/main/java/top/egon/mario/clocktower/resource/ClocktowerRbacResourceProvider.java \
  be/src/test/java/top/egon/mario/clocktower/flow \
  be/src/test/java/top/egon/mario/clocktower/room/ClocktowerRoomTestFactory.java \
  be/src/test/java/top/egon/mario/clocktower/resource/ClocktowerRbacResourceProviderTests.java
git commit -m "feat(clocktower): expose flow decision api"
```

---

### Task 3: Implement Night Task Guard, Skip, And Normal Night Advance

**Files:**

- Modify: `be/src/main/java/top/egon/mario/clocktower/flow/service/impl/ClocktowerFlowServiceImpl.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/grimoire/service/impl/ClocktowerGrimoireServiceImpl.java`
- Test: `be/src/test/java/top/egon/mario/clocktower/flow/ClocktowerFlowServiceTests.java`
- Test: `be/src/test/java/top/egon/mario/clocktower/grimoire/ClocktowerNightChecklistServiceTests.java`

- [ ] **Step 1: Add failing tests for night advance and skip**

Append to `ClocktowerFlowServiceTests`:

```java

@Test
void firstNightCannotAdvanceUntilTasksDoneOrSkipped() {
    Long roomId = startedRoom();
    grimoireService.getGrimoire(roomId, storyteller());

    assertThatThrownBy(() -> flowService.advance(roomId, storyteller()))
            .hasMessageContaining("CLOCKTOWER_NIGHT_TASKS_PENDING");
}

@Test
void skippingAllNightTasksAllowsFirstNightToEnterDay() {
    Long roomId = startedRoom();
    grimoireService.getGrimoire(roomId, storyteller());
    var pending = context.storytellerTaskRepository()
            .findByRoomIdAndStatusAndDeletedFalseOrderBySortOrderAsc(roomId, "PENDING");
    for (var task : pending) {
        flowService.skipNightTask(roomId, task.getId(),
                new top.egon.mario.clocktower.flow.dto.SkipNightTaskRequest("本轮无需唤醒"), storyteller());
    }

    var flow = flowService.advance(roomId, storyteller());

    assertThat(flow.phase().phase()).isEqualTo(ClocktowerPhase.DAY);
    assertThat(flow.phase().dayNo()).isEqualTo(1);
    assertThat(flow.phase().nightNo()).isEqualTo(1);
}

@Test
void skipNightTaskRequiresReason() {
    Long roomId = startedRoom();
    grimoireService.getGrimoire(roomId, storyteller());
    var task = context.storytellerTaskRepository()
            .findByRoomIdAndStatusAndDeletedFalseOrderBySortOrderAsc(roomId, "PENDING")
            .getFirst();

    assertThatThrownBy(() -> flowService.skipNightTask(roomId, task.getId(),
            new top.egon.mario.clocktower.flow.dto.SkipNightTaskRequest(" "), storyteller()))
            .hasMessageContaining("CLOCKTOWER_NIGHT_TASK_SKIP_REASON_REQUIRED");
}
```

Add import:

```java
import static org.assertj.core.api.Assertions.assertThatThrownBy;
```

- [ ] **Step 2: Run the failing night tests**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/be
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerFlowServiceTests test
```

Expected: FAIL because `advance` and `skipNightTask` still throw unsupported.

- [ ] **Step 3: Implement night task skip and advance**

In `ClocktowerFlowServiceImpl.skipNightTask`, replace the unsupported throw with:

```java
ClocktowerRoomPo room = roomRepository.findLockedByIdAndDeletedFalse(roomId)
        .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_ROOM_NOT_FOUND"));
ClocktowerAccess.

requireStoryteller(room, principal);
if(!StringUtils.

hasText(request.reason())){
        throw new

ClocktowerException("CLOCKTOWER_NIGHT_TASK_SKIP_REASON_REQUIRED");
}
ClocktowerStorytellerTaskPo task = taskRepository.findById(taskId)
        .filter(candidate -> !candidate.isDeleted() && candidate.getRoomId().equals(roomId))
        .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_TASK_NOT_FOUND"));
if(!

isNight(room.getPhase())||task.

getNightNo() !=room.

getCurrentNightNo()){
        throw new

ClocktowerException("CLOCKTOWER_NIGHT_TASK_NOT_CURRENT");
}
        task.

setStatus(STATUS_SKIPPED);
task.

setNote(request.reason());
        taskRepository.

save(task);
return

buildFlow(room);
```

In `ClocktowerFlowServiceImpl.advance`, replace the unsupported throw with:

```java
ClocktowerRoomPo room = roomRepository.findLockedByIdAndDeletedFalse(roomId)
        .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_ROOM_NOT_FOUND"));
ClocktowerAccess.

requireStoryteller(room, principal);

ClocktowerFlowResponse flow = buildFlow(room);
if(!flow.

advanceAllowed()){
        throw new

ClocktowerException(flow.blockingReasons().

getFirst());
        }
        switch(flow.

nextTransition()){
        case COMPLETE_FIRST_NIGHT ->{
        room.

setPhase(ClocktowerPhase.DAY);
        room.

setCurrentDayNo(1);
    }
            case COMPLETE_NIGHT ->{
        room.

setPhase(ClocktowerPhase.DAY);
        room.

setCurrentDayNo(room.getCurrentDayNo() +1);
        }
default ->throw new

ClocktowerException("CLOCKTOWER_FLOW_TRANSITION_UNSUPPORTED");
}
        roomRepository.

save(room);
eventService.

append(new top.egon.mario.clocktower.event.dto.ClocktowerEventAppendRequest(room.getId(),

top.egon.mario.clocktower.common.enums.ClocktowerEventType.PHASE_CHANGED,
        room.

getPhase(),room.

getCurrentDayNo(),room.

getCurrentNightNo(),

principal ==null?null:principal.

userId(), null,null,
top.egon.mario.clocktower.common.enums.ClocktowerVisibility.PUBLIC,List.

of(),
        java.util.Map.

of("phase",room.getPhase().

name())));
        return

buildFlow(room);
```

- [ ] **Step 4: Restrict grimoire night task sync to night phases**

Modify `ClocktowerGrimoireServiceImpl.canHaveNightTasks` to:

```java
private static boolean canHaveNightTasks(ClocktowerRoomPo room) {
    return room.getCurrentNightNo() > 0
            && (room.getPhase() == ClocktowerPhase.FIRST_NIGHT || room.getPhase() == ClocktowerPhase.NIGHT);
}
```

Modify `nightChecklist` so non-night phases return an empty checklist with `completed=false`:

```java
if(room.getPhase() !=ClocktowerPhase.FIRST_NIGHT &&room.

getPhase() !=ClocktowerPhase.NIGHT){
ClocktowerNightType nightType = room.getCurrentNightNo() <= 1
        ? ClocktowerNightType.FIRST_NIGHT : ClocktowerNightType.OTHER_NIGHT;
    return new

NightChecklistResponse(room.getCurrentNightNo(),nightType,List.

of(), false);
        }
```

Place the guard after access checks and before loading seats.

- [ ] **Step 5: Add non-night checklist regression test**

Append to `ClocktowerNightChecklistServiceTests`:

```java

@Test
void dayPhaseDoesNotExposeCurrentNightPendingSteps() {
    ClocktowerRoomTestFactory.Context context = ClocktowerRoomTestFactory.context();
    ClocktowerGrimoireServiceImpl service = new ClocktowerGrimoireServiceImpl(context.roomRepository(),
            context.seatRepository(), context.grimoireEntryRepository(), context.markerRepository(),
            context.storytellerTaskRepository(), context.nightOrderRepository(), context.roleRepository(),
            context.eventService());
    Long roomId = startedRoom(context);
    service.getGrimoire(roomId, storytellerPrincipal());
    context.roomRepository().findByIdAndDeletedFalse(roomId).orElseThrow().setPhase(ClocktowerPhase.DAY);

    NightChecklistResponse checklist = service.nightChecklist(roomId, storytellerPrincipal());

    assertThat(checklist.steps()).isEmpty();
    assertThat(checklist.completed()).isFalse();
}
```

If `startedRoom` or `storytellerPrincipal` are private in that test, copy the helper implementations from
`ClocktowerFlowServiceTests` into `ClocktowerNightChecklistServiceTests`.

- [ ] **Step 6: Run night tests**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/be
./mvnw -Dmaven.build.cache.enabled=false -Dtest='ClocktowerFlowServiceTests,ClocktowerNightChecklistServiceTests' test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add \
  be/src/main/java/top/egon/mario/clocktower/flow/service/impl/ClocktowerFlowServiceImpl.java \
  be/src/main/java/top/egon/mario/clocktower/grimoire/service/impl/ClocktowerGrimoireServiceImpl.java \
  be/src/test/java/top/egon/mario/clocktower/flow/ClocktowerFlowServiceTests.java \
  be/src/test/java/top/egon/mario/clocktower/grimoire/ClocktowerNightChecklistServiceTests.java
git commit -m "feat(clocktower): gate night phase advancement"
```

---

### Task 4: Remove Old Phase Advance And Implement Day/Nomination/Execution/Night Transitions

**Files:**

- Modify: `be/src/main/java/top/egon/mario/clocktower/flow/service/impl/ClocktowerFlowServiceImpl.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/grimoire/service/impl/ClocktowerGrimoireServiceImpl.java`
- Test: `be/src/test/java/top/egon/mario/clocktower/flow/ClocktowerFlowServiceTests.java`
- Test: `be/src/test/java/top/egon/mario/clocktower/grimoire/ClocktowerStorytellerActionServiceTests.java`

- [ ] **Step 1: Add failing transition and old-action tests**

Append to `ClocktowerFlowServiceTests`:

```java

@Test
void normalFlowMovesDayToNominationThenExecution() {
    Long roomId = dayRoom();

    var nominationFlow = flowService.advance(roomId, storyteller());
    assertThat(nominationFlow.phase().phase()).isEqualTo(ClocktowerPhase.NOMINATION);

    var executionFlow = flowService.advance(roomId, storyteller());
    assertThat(executionFlow.phase().phase()).isEqualTo(ClocktowerPhase.EXECUTION);
}

@Test
void executionResolvedMovesToNextNightAndNightCompletionMovesToNextDay() {
    Long roomId = executionResolvedRoom();

    var nightFlow = flowService.advance(roomId, storyteller());
    assertThat(nightFlow.phase().phase()).isEqualTo(ClocktowerPhase.NIGHT);
    assertThat(nightFlow.phase().dayNo()).isEqualTo(1);
    assertThat(nightFlow.phase().nightNo()).isEqualTo(2);
}
```

Add helper methods to `ClocktowerFlowServiceTests`:

```java
private Long dayRoom() {
    Long roomId = startedRoom();
    grimoireService.getGrimoire(roomId, storyteller());
    context.storytellerTaskRepository()
            .findByRoomIdAndStatusAndDeletedFalseOrderBySortOrderAsc(roomId, "PENDING")
            .forEach(task -> flowService.skipNightTask(roomId, task.getId(),
                    new top.egon.mario.clocktower.flow.dto.SkipNightTaskRequest("跳过"), storyteller()));
    flowService.advance(roomId, storyteller());
    return roomId;
}

private Long executionResolvedRoom() {
    Long roomId = dayRoom();
    flowService.advance(roomId, storyteller());
    flowService.advance(roomId, storyteller());
    flowService.confirmExecution(roomId,
            new top.egon.mario.clocktower.flow.dto.ExecutionConfirmRequest(false,
                    top.egon.mario.clocktower.flow.dto.ClocktowerExecutionDeathPolicy.NO_CHANGE,
                    "无人处决"), storyteller());
    return roomId;
}
```

Append to `ClocktowerStorytellerActionServiceTests`:

```java

@Test
void oldAdvancePhaseActionIsRejected() {
    var response = service.storytellerAction(roomId,
            new StorytellerActionRequest("ADVANCE_PHASE", List.of(), "推进", Map.of()),
            storytellerPrincipal());

    assertThat(response.accepted()).isFalse();
    assertThat(response.rejectedCode()).isEqualTo("CLOCKTOWER_ADVANCE_PHASE_REPLACED_BY_FLOW");
}
```

If the test class uses different field names, create the room using its existing helper and instantiate
`ClocktowerGrimoireServiceImpl` the same way its current tests do.

- [ ] **Step 2: Run failing transition tests**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/be
./mvnw -Dmaven.build.cache.enabled=false -Dtest='ClocktowerFlowServiceTests,ClocktowerStorytellerActionServiceTests' test
```

Expected: FAIL because `advance` does not yet handle day/nomination/execution and old `ADVANCE_PHASE` still advances.

- [ ] **Step 3: Remove old generic advance from grimoire action**

Modify the `storytellerAction` switch in `ClocktowerGrimoireServiceImpl`:

```java
case"ADVANCE_PHASE"->StorytellerActionResponse.rejected(
        "CLOCKTOWER_ADVANCE_PHASE_REPLACED_BY_FLOW",getGrimoire(roomId, principal));
```

Delete the private `advancePhase` and `targetPhase` methods from `ClocktowerGrimoireServiceImpl`. If another method uses
`targetPhase`, stop and inspect that call before deleting.

- [ ] **Step 4: Implement remaining normal transitions**

In `ClocktowerFlowServiceImpl.advance`, extend the switch:

```java
case START_NOMINATION ->room.

setPhase(ClocktowerPhase.NOMINATION);
case START_EXECUTION ->room.

setPhase(ClocktowerPhase.EXECUTION);
case START_NIGHT ->{
        room.

setPhase(ClocktowerPhase.NIGHT);
    room.

setCurrentNightNo(room.getCurrentNightNo() +1);
        }
```

Keep the existing `COMPLETE_FIRST_NIGHT` and `COMPLETE_NIGHT` branches from Task 3. Keep throwing
`CLOCKTOWER_FLOW_TRANSITION_UNSUPPORTED` for `NONE`.

- [ ] **Step 5: Run transition tests**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/be
./mvnw -Dmaven.build.cache.enabled=false -Dtest='ClocktowerFlowServiceTests,ClocktowerStorytellerActionServiceTests' test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add \
  be/src/main/java/top/egon/mario/clocktower/flow/service/impl/ClocktowerFlowServiceImpl.java \
  be/src/main/java/top/egon/mario/clocktower/grimoire/service/impl/ClocktowerGrimoireServiceImpl.java \
  be/src/test/java/top/egon/mario/clocktower/flow/ClocktowerFlowServiceTests.java \
  be/src/test/java/top/egon/mario/clocktower/grimoire/ClocktowerStorytellerActionServiceTests.java
git commit -m "feat(clocktower): replace generic phase advance"
```

---

### Task 5: Enforce Nomination And Vote Rules

**Files:**

- Modify: `be/src/main/java/top/egon/mario/clocktower/action/service/impl/ClocktowerActionServiceImpl.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/grimoire/repository/ClocktowerVoteRepository.java`
- Modify: `be/src/test/java/top/egon/mario/clocktower/room/ClocktowerRoomTestFactory.java`
- Test: `be/src/test/java/top/egon/mario/clocktower/action/ClocktowerActionServiceTests.java`

- [ ] **Step 1: Add failing nomination tests**

Append to `ClocktowerActionServiceTests`:

```java

@Test
void nominationRejectsDeadNominatorAndDeadNominee() {
    Long roomId = runningDayRoom();
    var seats = context.seatRepository().findByRoomIdAndDeletedFalseOrderBySeatNoAsc(roomId);
    seats.getFirst().setLifeStatus("DEAD");

    ClocktowerActionResponse deadNominator = actionService.submit(roomId,
            new ClocktowerActionRequest(seats.getFirst().getId(), "NOMINATE", List.of(seats.get(1).getId()),
                    null, "dead nominates", Map.of(), "dead-nom"),
            principal(seats.getFirst().getUserId(), "p1"));

    assertThat(deadNominator.accepted()).isFalse();
    assertThat(deadNominator.rejectedCode()).isEqualTo("CLOCKTOWER_NOMINATOR_NOT_ALIVE");

    seats.getFirst().setLifeStatus("ALIVE");
    seats.get(1).setLifeStatus("DEAD");

    ClocktowerActionResponse deadNominee = actionService.submit(roomId,
            new ClocktowerActionRequest(seats.getFirst().getId(), "NOMINATE", List.of(seats.get(1).getId()),
                    null, "nominee dead", Map.of(), "dead-target"),
            principal(seats.getFirst().getUserId(), "p1"));

    assertThat(deadNominee.accepted()).isFalse();
    assertThat(deadNominee.rejectedCode()).isEqualTo("CLOCKTOWER_NOMINEE_NOT_ALIVE");
}

@Test
void nominationRejectsDailyRepeatAndOpenNomination() {
    Long roomId = runningDayRoom();
    var seats = context.seatRepository().findByRoomIdAndDeletedFalseOrderBySeatNoAsc(roomId);

    ClocktowerActionResponse first = nominate(roomId, seats.getFirst(), seats.get(1));
    assertThat(first.accepted()).isTrue();

    ClocktowerActionResponse whileOpen = nominate(roomId, seats.get(2), seats.get(3));
    assertThat(whileOpen.accepted()).isFalse();
    assertThat(whileOpen.rejectedCode()).isEqualTo("CLOCKTOWER_OPEN_NOMINATION_EXISTS");

    closeOpenNomination(roomId);

    ClocktowerActionResponse sameNominator = nominate(roomId, seats.getFirst(), seats.get(2));
    assertThat(sameNominator.accepted()).isFalse();
    assertThat(sameNominator.rejectedCode()).isEqualTo("CLOCKTOWER_NOMINATOR_ALREADY_NOMINATED_TODAY");

    ClocktowerActionResponse sameNominee = nominate(roomId, seats.get(2), seats.get(1));
    assertThat(sameNominee.accepted()).isFalse();
    assertThat(sameNominee.rejectedCode()).isEqualTo("CLOCKTOWER_NOMINEE_ALREADY_NOMINATED_TODAY");
}
```

Add helper methods to `ClocktowerActionServiceTests`:

```java
private ClocktowerActionResponse nominate(Long roomId, ClocktowerSeatPo nominator, ClocktowerSeatPo nominee) {
    return actionService.submit(roomId,
            new ClocktowerActionRequest(nominator.getId(), "NOMINATE", List.of(nominee.getId()),
                    null, "nominate", Map.of(), "nom-" + nominator.getId() + "-" + nominee.getId()),
            principal(nominator.getUserId(), "p" + nominator.getSeatNo()));
}

private void closeOpenNomination(Long roomId) {
    context.nominationRepository().findTopByRoomIdAndStatusAndDeletedFalseOrderByIdDesc(roomId, "OPEN")
            .orElseThrow()
            .setStatus("CLOSED");
}
```

If `ClocktowerSeatPo` is not imported, add:

```java
import top.egon.mario.clocktower.room.po.ClocktowerSeatPo;
```

- [ ] **Step 2: Add failing dead-vote tests**

Append to `ClocktowerActionServiceTests`:

```java

@Test
void publiclyDeadPlayerSpendsOnlyOneDeadVoteAcrossGame() {
    Long roomId = runningNominationRoom();
    var seats = context.seatRepository().findByRoomIdAndDeletedFalseOrderBySeatNoAsc(roomId);
    ClocktowerSeatPo voter = seats.getFirst();
    voter.setLifeStatus("ALIVE");
    voter.setPublicLifeStatus("DEAD");
    voter.setHasDeadVote(true);

    ClocktowerActionResponse firstVote = vote(roomId, voter, true);
    assertThat(firstVote.accepted()).isTrue();
    assertThat(voter.isHasDeadVote()).isFalse();

    closeOpenNomination(roomId);
    context.nominationRepository().save(newOpenNomination(roomId, seats.get(2).getId(), seats.get(3).getId()));

    ClocktowerActionResponse secondVote = vote(roomId, voter, true);
    assertThat(secondVote.accepted()).isFalse();
    assertThat(secondVote.rejectedCode()).isEqualTo("CLOCKTOWER_DEAD_VOTE_ALREADY_SPENT");
}

@Test
void alivePubliclyAlivePlayerCanVoteOncePerNomination() {
    Long roomId = runningNominationRoom();
    var seats = context.seatRepository().findByRoomIdAndDeletedFalseOrderBySeatNoAsc(roomId);
    ClocktowerSeatPo voter = seats.getFirst();

    ClocktowerActionResponse firstVote = vote(roomId, voter, true);
    ClocktowerActionResponse duplicate = vote(roomId, voter, true);

    assertThat(firstVote.accepted()).isTrue();
    assertThat(duplicate.accepted()).isFalse();
    assertThat(duplicate.rejectedCode()).isEqualTo("CLOCKTOWER_VOTE_ALREADY_CAST");
}
```

Add helpers:

```java
private Long runningNominationRoom() {
    Long roomId = runningDayRoom();
    var room = context.roomRepository().findByIdAndDeletedFalse(roomId).orElseThrow();
    room.setPhase(ClocktowerPhase.NOMINATION);
    var seats = context.seatRepository().findByRoomIdAndDeletedFalseOrderBySeatNoAsc(roomId);
    context.nominationRepository().save(newOpenNomination(roomId, seats.get(1).getId(), seats.get(2).getId()));
    return roomId;
}

private ClocktowerNominationPo newOpenNomination(Long roomId, Long nominatorSeatId, Long nomineeSeatId) {
    ClocktowerNominationPo nomination = new ClocktowerNominationPo();
    nomination.setRoomId(roomId);
    nomination.setDayNo(context.roomRepository().findByIdAndDeletedFalse(roomId).orElseThrow().getCurrentDayNo());
    nomination.setNominatorSeatId(nominatorSeatId);
    nomination.setNomineeSeatId(nomineeSeatId);
    nomination.setStatus("OPEN");
    return nomination;
}

private ClocktowerActionResponse vote(Long roomId, ClocktowerSeatPo voter, boolean voteValue) {
    return actionService.submit(roomId,
            new ClocktowerActionRequest(voter.getId(), "VOTE", List.of(), null, "vote",
                    Map.of("vote", voteValue), "vote-" + voter.getId() + "-" + System.nanoTime()),
            principal(voter.getUserId(), "p" + voter.getSeatNo()));
}
```

Add imports:

```java
import top.egon.mario.clocktower.grimoire.po.ClocktowerNominationPo;
```

- [ ] **Step 3: Run failing action tests**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/be
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerActionServiceTests test
```

Expected: FAIL because daily nomination limits, open nomination rejection, and public-dead vote handling are incomplete.

- [ ] **Step 4: Add vote repository helper**

Modify `ClocktowerVoteRepository.java`:

```java
boolean existsByRoomIdAndVoterSeatIdAndUsedDeadVoteTrueAndDeletedFalse(Long roomId, Long voterSeatId);
```

Update `ClocktowerRoomTestFactory`:

```java
when(voteRepository.existsByRoomIdAndVoterSeatIdAndUsedDeadVoteTrueAndDeletedFalse(any(),any()))
        .

thenAnswer(invocation ->votes.

stream()
                .

anyMatch(vote ->!vote.

isDeleted()
                        &&vote.

getRoomId().

equals(invocation.getArgument(0))
        &&vote.

getVoterSeatId().

equals(invocation.getArgument(1))
        &&vote.

isUsedDeadVote()));
```

- [ ] **Step 5: Implement nomination rules**

In `ClocktowerActionServiceImpl.nominate`, after target lookup and life checks, replace old logic with:

```java
if(room.getPhase() !=ClocktowerPhase.DAY &&room.

getPhase() !=ClocktowerPhase.NOMINATION){
        return

reject(room, actor, principal, "CLOCKTOWER_NOMINATION_PHASE_INVALID");
}
        if(!"ALIVE".

equals(actor.getLifeStatus())){
        return

reject(room, actor, principal, "CLOCKTOWER_NOMINATOR_NOT_ALIVE");
}
Long targetSeatId = firstTarget(request);
ClocktowerSeatPo target = seat(room.getId(), targetSeatId);
if(!"ALIVE".

equals(target.getLifeStatus())){
        return

reject(room, actor, principal, "CLOCKTOWER_NOMINEE_NOT_ALIVE");
}
        if(nominationRepository.

findTopByRoomIdAndStatusAndDeletedFalseOrderByIdDesc(room.getId(), "OPEN").

isPresent()){
        return

reject(room, actor, principal, "CLOCKTOWER_OPEN_NOMINATION_EXISTS");
}
List<ClocktowerNominationPo> today = nominationRepository
        .findByRoomIdAndDayNoAndDeletedFalseOrderByIdAsc(room.getId(), room.getCurrentDayNo());
if(today.

stream().

anyMatch(nomination ->nomination.

getNominatorSeatId().

equals(actor.getId()))){
        return

reject(room, actor, principal, "CLOCKTOWER_NOMINATOR_ALREADY_NOMINATED_TODAY");
}
        if(today.

stream().

anyMatch(nomination ->nomination.

getNomineeSeatId().

equals(target.getId()))){
        return

reject(room, actor, principal, "CLOCKTOWER_NOMINEE_ALREADY_NOMINATED_TODAY");
}
ClocktowerNominationPo nomination = new ClocktowerNominationPo();
nomination.

setRoomId(room.getId());
        nomination.

setDayNo(room.getCurrentDayNo());
        nomination.

setNominatorSeatId(actor.getId());
        nomination.

setNomineeSeatId(target.getId());
        nomination.

setStatus("OPEN");
nominationRepository.

save(nomination);
room.

setPhase(ClocktowerPhase.NOMINATION);
roomRepository.

save(room);
return

append(room, actor, request, principal, ClocktowerEventType.PLAYER_NOMINATED,
       ClocktowerVisibility.PUBLIC, List.of(),
        Map.

of("nominationId",nomination.getId(), "targetSeatId",target.

getId(), "content",

text(request.content())));
```

- [ ] **Step 6: Implement vote rules**

In `ClocktowerActionServiceImpl.vote`, replace dead-vote calculation with:

```java
if(room.getPhase() !=ClocktowerPhase.NOMINATION){
        return

reject(room, actor, principal, "CLOCKTOWER_VOTE_PHASE_INVALID");
}
ClocktowerNominationPo nomination = nominationRepository
        .findTopByRoomIdAndStatusAndDeletedFalseOrderByIdDesc(room.getId(), "OPEN")
        .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_NOMINATION_NOT_FOUND"));
if(voteRepository.

findByNominationIdAndVoterSeatIdAndDeletedFalse(nomination.getId(),actor.

getId()).

isPresent()){
        return

reject(room, actor, principal, "CLOCKTOWER_VOTE_ALREADY_CAST");
}
boolean usedDeadVote = "DEAD".equals(actor.getLifeStatus()) || "DEAD".equals(actor.getPublicLifeStatus());
if(usedDeadVote &&(!actor.

isHasDeadVote()
        ||voteRepository.

existsByRoomIdAndVoterSeatIdAndUsedDeadVoteTrueAndDeletedFalse(room.getId(),actor.

getId()))){
        return

reject(room, actor, principal, "CLOCKTOWER_DEAD_VOTE_ALREADY_SPENT");
}
```

Keep the existing vote persistence and vote count increment below this block.

- [ ] **Step 7: Run action tests**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/be
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerActionServiceTests test
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add \
  be/src/main/java/top/egon/mario/clocktower/action/service/impl/ClocktowerActionServiceImpl.java \
  be/src/main/java/top/egon/mario/clocktower/grimoire/repository/ClocktowerVoteRepository.java \
  be/src/test/java/top/egon/mario/clocktower/room/ClocktowerRoomTestFactory.java \
  be/src/test/java/top/egon/mario/clocktower/action/ClocktowerActionServiceTests.java
git commit -m "feat(clocktower): enforce nomination and vote flow rules"
```

---

### Task 6: Close Nominations, Resolve Execution, And Suggest Victory

**Files:**

- Modify: `be/src/main/java/top/egon/mario/clocktower/common/enums/ClocktowerEventType.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/event/repository/ClocktowerEventRepository.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/flow/service/impl/ClocktowerFlowServiceImpl.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/ruling/service/impl/ClocktowerRulingServiceImpl.java`
- Test: `be/src/test/java/top/egon/mario/clocktower/flow/ClocktowerFlowServiceTests.java`
- Test: `be/src/test/java/top/egon/mario/clocktower/ruling/ClocktowerRulingServiceTests.java`

- [ ] **Step 1: Add failing close nomination and execution tests**

Append to `ClocktowerFlowServiceTests`:

```java

@Test
void closeNominationLocksOpenNominationAndAllowsExecutionTransition() {
    Long roomId = dayRoom();
    var seats = context.seatRepository().findByRoomIdAndDeletedFalseOrderBySeatNoAsc(roomId);
    context.nominationRepository().save(openNomination(roomId, seats.getFirst().getId(), seats.get(1).getId()));

    var open = context.nominationRepository()
            .findTopByRoomIdAndStatusAndDeletedFalseOrderByIdDesc(roomId, "OPEN")
            .orElseThrow();
    flowService.closeNomination(roomId, open.getId(), new CloseNominationRequest("投票结束"), storyteller());

    assertThat(open.getStatus()).isEqualTo("CLOSED");
    var flow = flowService.advance(roomId, storyteller());
    assertThat(flow.phase().phase()).isEqualTo(ClocktowerPhase.EXECUTION);
}

@Test
void executionCandidateRequiresThresholdAndUniqueTopVote() {
    Long roomId = executionRoomWithClosedNominations(3, 2);

    var flow = flowService.getFlow(roomId, storyteller());

    assertThat(flow.executionCandidate().executable()).isTrue();
    assertThat(flow.executionCandidate().voteCount()).isEqualTo(3);
    assertThat(flow.executionCandidate().threshold()).isEqualTo(3);
}

@Test
void executionCandidateAbsentOnTie() {
    Long roomId = executionRoomWithClosedNominations(3, 3);

    var flow = flowService.getFlow(roomId, storyteller());

    assertThat(flow.executionCandidate().executable()).isFalse();
    assertThat(flow.executionCandidate().reason()).isEqualTo("TIED_TOP_VOTE");
}

@Test
void confirmExecutionMarksNominationExecutedWithoutDeathAndAllowsNight() {
    Long roomId = executionRoomWithClosedNominations(3, 2);
    var seats = context.seatRepository().findByRoomIdAndDeletedFalseOrderBySeatNoAsc(roomId);
    Long nomineeSeatId = seats.get(1).getId();

    var confirmed = flowService.confirmExecution(roomId,
            new ExecutionConfirmRequest(true, ClocktowerExecutionDeathPolicy.NO_CHANGE, "处决但未死亡"), storyteller());
    assertThat(confirmed.executionCandidate().resolved()).isTrue();
    assertThat(context.seatRepository().findByIdAndRoomIdAndDeletedFalse(nomineeSeatId, roomId)
            .orElseThrow().getLifeStatus()).isEqualTo("ALIVE");

    var night = flowService.advance(roomId, storyteller());
    assertThat(night.phase().phase()).isEqualTo(ClocktowerPhase.NIGHT);
}

@Test
void confirmExecutionWithDeathPolicyRecordsDeathSeparately() {
    Long roomId = executionRoomWithClosedNominations(3, 2);
    var seats = context.seatRepository().findByRoomIdAndDeletedFalseOrderBySeatNoAsc(roomId);
    Long nomineeSeatId = seats.get(1).getId();

    flowService.confirmExecution(roomId,
            new ExecutionConfirmRequest(true, ClocktowerExecutionDeathPolicy.MARK_DEAD, "处决造成死亡"), storyteller());

    var nominee = context.seatRepository().findByIdAndRoomIdAndDeletedFalse(nomineeSeatId, roomId).orElseThrow();
    assertThat(nominee.getLifeStatus()).isEqualTo("DEAD");
    assertThat(context.rulingRepository().findByRoomIdAndDeletedFalseOrderByIdDesc(roomId))
            .extracting(ruling -> ruling.getRulingType().name())
            .containsSubsequence("MARK_DEAD", "EXECUTE_PLAYER");
}

@Test
void confirmNoExecutionRecordsResolutionWithoutSyntheticNomination() {
    Long roomId = executionRoomWithClosedNominations(2, 1);

    flowService.confirmExecution(roomId,
            new ExecutionConfirmRequest(false, ClocktowerExecutionDeathPolicy.NO_CHANGE, "无人达到处决门槛"), storyteller());

    assertThat(context.nominationRepository().findByRoomIdAndDayNoAndDeletedFalseOrderByIdAsc(roomId, 1))
            .allSatisfy(nomination -> {
                assertThat(nomination.getNominatorSeatId()).isNotZero();
                assertThat(nomination.getNomineeSeatId()).isNotZero();
            });
    assertThat(flowService.getFlow(roomId, storyteller()).executionCandidate().resolved()).isTrue();
}
```

Add helpers:

```java
private ClocktowerNominationPo openNomination(Long roomId, Long nominatorSeatId, Long nomineeSeatId) {
    ClocktowerNominationPo nomination = new ClocktowerNominationPo();
    nomination.setRoomId(roomId);
    nomination.setDayNo(context.roomRepository().findByIdAndDeletedFalse(roomId).orElseThrow().getCurrentDayNo());
    nomination.setNominatorSeatId(nominatorSeatId);
    nomination.setNomineeSeatId(nomineeSeatId);
    nomination.setStatus("OPEN");
    return nomination;
}

private Long executionRoomWithClosedNominations(int firstVotes, int secondVotes) {
    Long roomId = dayRoom();
    var room = context.roomRepository().findByIdAndDeletedFalse(roomId).orElseThrow();
    room.setPhase(ClocktowerPhase.EXECUTION);
    var seats = context.seatRepository().findByRoomIdAndDeletedFalseOrderBySeatNoAsc(roomId);
    ClocktowerNominationPo first = openNomination(roomId, seats.getFirst().getId(), seats.get(1).getId());
    first.setStatus("CLOSED");
    first.setVoteCount(firstVotes);
    context.nominationRepository().save(first);
    ClocktowerNominationPo second = openNomination(roomId, seats.get(2).getId(), seats.get(3).getId());
    second.setStatus("CLOSED");
    second.setVoteCount(secondVotes);
    context.nominationRepository().save(second);
    return roomId;
}
```

Add imports:

```java
import top.egon.mario.clocktower.flow.dto.CloseNominationRequest;
import top.egon.mario.clocktower.flow.dto.ClocktowerExecutionDeathPolicy;
import top.egon.mario.clocktower.flow.dto.ExecutionConfirmRequest;
import top.egon.mario.clocktower.grimoire.po.ClocktowerNominationPo;
```

In `ClocktowerRulingServiceTests`, replace the existing execution tests that expect `PLAYER_DIED` with:

```java

@Test
void executePlayerCanTargetAnySeatWithoutChangingLifeStatus() {
    ClocktowerRoomResponse room = startedRoom();
    Long targetSeatId = room.seats().get(3).seatId();

    ClocktowerRulingApplyResponse response = rulingService.create(room.roomId(), new ClocktowerRulingCreateRequest(
                    ClocktowerRulingType.EXECUTE_PLAYER, targetSeatId, null, null, null, null,
                    ClocktowerRulingReason.ROLE_ABILITY, "猎手开枪处决邪恶玩家", "一名玩家被处决", ClocktowerVisibility.PUBLIC, false),
            storytellerPrincipal());

    assertThat(response.events()).extracting(event -> event.eventType())
            .containsExactly(ClocktowerEventType.PLAYER_EXECUTED);
    assertThat(context.seatRepository().findByIdAndRoomIdAndDeletedFalse(targetSeatId, room.roomId()).orElseThrow()
            .getLifeStatus()).isEqualTo("ALIVE");
}

@Test
void executePlayerWithMatchingNominationClosesNominationAndPreservesVotesWithoutDeath() {
    ClocktowerRoomResponse room = startedRoom();
    Long nominator = room.seats().getFirst().seatId();
    Long nominee = room.seats().get(1).seatId();
    submitPlayerAction(room.roomId(), nominator, "NOMINATE", List.of(nominee));
    Long nominationId = context.nominationRepository().findByRoomIdAndDeletedFalseOrderByIdAsc(room.roomId())
            .getFirst().getId();
    submitPlayerAction(room.roomId(), nominator, "VOTE", List.of());
    List<ClocktowerVotePo> votesBeforeExecution = context.voteRepository()
            .findByNominationIdAndDeletedFalseOrderByIdAsc(nominationId);

    ClocktowerRulingApplyResponse response = rulingService.create(room.roomId(), new ClocktowerRulingCreateRequest(
            ClocktowerRulingType.EXECUTE_PLAYER, nominee, nominationId, null, null, null,
            ClocktowerRulingReason.STORYTELLER_RULING, "处决投票目标", "一名玩家被处决",
            ClocktowerVisibility.PUBLIC, false), storytellerPrincipal());

    assertThat(response.events()).extracting(event -> event.eventType())
            .containsExactly(ClocktowerEventType.PLAYER_EXECUTED);
    assertThat(context.seatRepository().findByIdAndRoomIdAndDeletedFalse(nominee, room.roomId()).orElseThrow()
            .getLifeStatus()).isEqualTo("ALIVE");
    ClocktowerNominationPo nomination = context.nominationRepository().findById(nominationId).orElseThrow();
    assertThat(nomination.getStatus()).isEqualTo("CLOSED");
    assertThat(nomination.isExecuted()).isTrue();
    assertThat(context.voteRepository().findByNominationIdAndDeletedFalseOrderByIdAsc(nominationId))
            .extracting(ClocktowerVotePo::getId)
            .containsExactly(votesBeforeExecution.getFirst().getId());
}

@Test
void skipExecutionDoesNotRequireNomination() {
    ClocktowerRoomResponse room = startedRoom();

    ClocktowerRulingApplyResponse response = rulingService.create(room.roomId(), new ClocktowerRulingCreateRequest(
            ClocktowerRulingType.SKIP_EXECUTION, null, null, null, null, null,
            ClocktowerRulingReason.VOTE_EXECUTION, "无人处决", "今日无人被处决",
            ClocktowerVisibility.PUBLIC, false), storytellerPrincipal());

    assertThat(response.events()).extracting(event -> event.eventType())
            .containsExactly(ClocktowerEventType.EXECUTION_SKIPPED);
}
```

- [ ] **Step 2: Run failing execution tests**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/be
./mvnw -Dmaven.build.cache.enabled=false -Dtest='ClocktowerFlowServiceTests,ClocktowerRulingServiceTests' test
```

Expected: FAIL because close/confirm are unsupported, execution still mutates life status, no-execution still requires a
nomination, and tie calculation may not be complete.

- [ ] **Step 3: Improve execution candidate tie calculation**

In `ClocktowerFlowServiceImpl.executionCandidate`, after computing `topVote`, use this tie logic:

```java
List<ClocktowerNominationPo> top = closed.stream()
        .filter(nomination -> nomination.getVoteCount() == topVote)
        .toList();
if(top.

size() !=1){
        return new

ExecutionCandidateResponse(false,false,null,null,topVote, threshold, "TIED_TOP_VOTE");
}
```

The existing code from Task 2 already has this shape; keep it and verify below-threshold is checked before tie.

- [ ] **Step 4: Implement close nomination**

Replace unsupported throw in `closeNomination`:

```java
ClocktowerRoomPo room = roomRepository.findLockedByIdAndDeletedFalse(roomId)
        .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_ROOM_NOT_FOUND"));
ClocktowerAccess.

requireStoryteller(room, principal);
if(room.

getPhase() !=ClocktowerPhase.NOMINATION){
        throw new

ClocktowerException("CLOCKTOWER_NOMINATION_PHASE_INVALID");
}
ClocktowerNominationPo nomination = nominationRepository.findByIdAndRoomIdAndDeletedFalse(nominationId, roomId)
        .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_NOMINATION_NOT_FOUND"));
if(!NOMINATION_OPEN.

equals(nomination.getStatus())){
        throw new

ClocktowerException("CLOCKTOWER_NOMINATION_NOT_OPEN");
}
        nomination.

setStatus(NOMINATION_CLOSED);
nominationRepository.

save(nomination);
return

buildFlow(room);
```

- [ ] **Step 5: Separate execution, death, and no-execution persistence**

Add `EXECUTION_SKIPPED` to `ClocktowerEventType`:

```java
EXECUTION_SKIPPED,
```

Add this helper to `ClocktowerEventRepository`:

```java
boolean existsByRoomIdAndDayNoAndEventTypeAndDeletedFalse(Long roomId, int dayNo, ClocktowerEventType eventType);
```

Add `ClocktowerEventRepository` and `ClocktowerRulingService` dependencies to `ClocktowerFlowServiceImpl`:

```java
private final ClocktowerEventRepository eventRepository;
private final ClocktowerRulingService rulingService;
```

Add imports:

```java
import top.egon.mario.clocktower.common.enums.ClocktowerEventType;
import top.egon.mario.clocktower.event.repository.ClocktowerEventRepository;
import top.egon.mario.clocktower.ruling.service.ClocktowerRulingService;
```

Replace `executionResolved`:

```java
private boolean executionResolved(ClocktowerRoomPo room, List<ClocktowerNominationPo> closed) {
    boolean executedNomination = closed.stream().anyMatch(ClocktowerNominationPo::isExecuted);
    boolean skippedExecution = eventRepository.existsByRoomIdAndDayNoAndEventTypeAndDeletedFalse(
            room.getId(), room.getCurrentDayNo(), ClocktowerEventType.EXECUTION_SKIPPED);
    return executedNomination || skippedExecution;
}
```

Update `TestClocktowerFlowServices.flowService` in `ClocktowerFlowServiceTests` so the new constructor dependencies are
available:

```java
static ClocktowerFlowService flowService(ClocktowerRoomTestFactory.Context context) {
    ClocktowerRuleEngineConfiguration configuration = new ClocktowerRuleEngineConfiguration();
    ClocktowerRuleEngine ruleEngine = new ClocktowerRuleEngine(
            configuration.clocktowerBoardValidationKieBase(new DefaultResourceLoader()),
            configuration.clocktowerFlowKieBase(new DefaultResourceLoader()));
    ClocktowerGrimoireServiceImpl grimoireService = grimoireService(context);
    ClocktowerRulingServiceImpl rulingService = new ClocktowerRulingServiceImpl(context.roomRepository(),
            context.seatRepository(), context.nominationRepository(), context.rulingRepository(),
            context.eventRepository(), context.eventService(), context.objectMapper(), grimoireService);
    return new ClocktowerFlowServiceImpl(context.roomRepository(), context.seatRepository(),
            context.storytellerTaskRepository(), context.nominationRepository(), context.voteRepository(),
            context.roleRepository(), context.eventService(), context.eventRepository(), rulingService, ruleEngine);
}
```

Add imports:

```java
import top.egon.mario.clocktower.ruling.service.impl.ClocktowerRulingServiceImpl;
```

In `ClocktowerRulingServiceImpl.requiresNomination`, remove `SKIP_EXECUTION` from the nomination-required list:

```java
private boolean requiresNomination(ClocktowerRulingType rulingType) {
    return rulingType == ClocktowerRulingType.CLOSE_NOMINATION
            || rulingType == ClocktowerRulingType.REOPEN_NOMINATION
            || rulingType == ClocktowerRulingType.VOID_NOMINATION;
}
```

Replace the `SKIP_EXECUTION` branch in `apply`:

```java
case SKIP_EXECUTION ->

applySkipExecution(room, ruling, principal);
```

Replace `applyExecution` so execution does not kill the target:

```java
private List<ClocktowerEventResponse> applyExecution(ClocktowerRoomPo room, ClocktowerRulingPo ruling,
                                                     RbacPrincipal principal) {
    ClocktowerSeatPo seat = seatRepository.findByIdAndRoomIdAndDeletedFalse(ruling.getTargetSeatId(), room.getId())
            .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_SEAT_NOT_FOUND"));
    ClocktowerNominationPo nomination = null;
    if (ruling.getNominationId() != null) {
        nomination = nominationRepository
                .findByIdAndRoomIdAndDeletedFalse(ruling.getNominationId(), room.getId())
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_NOMINATION_NOT_FOUND"));
        if (!seat.getId().equals(nomination.getNomineeSeatId())) {
            throw new ClocktowerException("CLOCKTOWER_RULING_NOMINATION_TARGET_MISMATCH");
        }
    }
    if (nomination != null) {
        nomination.setExecuted(true);
        nomination.setStatus("CLOSED");
        nominationRepository.save(nomination);
    }
    return List.of(append(room, principal, seat.getId(), ClocktowerEventType.PLAYER_EXECUTED, ruling));
}
```

Add no-execution persistence:

```java
private List<ClocktowerEventResponse> applySkipExecution(ClocktowerRoomPo room, ClocktowerRulingPo ruling,
                                                         RbacPrincipal principal) {
    return List.of(append(room, principal, null, ClocktowerEventType.EXECUTION_SKIPPED, ruling));
}
```

- [ ] **Step 6: Implement execution confirmation**

Replace unsupported throw in `confirmExecution`:

```java
ClocktowerRoomPo room = roomRepository.findLockedByIdAndDeletedFalse(roomId)
        .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_ROOM_NOT_FOUND"));
ClocktowerAccess.

requireStoryteller(room, principal);
if(room.

getPhase() !=ClocktowerPhase.EXECUTION){
        throw new

ClocktowerException("CLOCKTOWER_EXECUTION_PHASE_INVALID");
}
        if(request ==null||!StringUtils.

hasText(request.note())){
        throw new

ClocktowerException("CLOCKTOWER_EXECUTION_NOTE_REQUIRED");
}
ClocktowerExecutionDeathPolicy deathPolicy = request.deathPolicy() == null
        ? ClocktowerExecutionDeathPolicy.NO_CHANGE
        : request.deathPolicy();
ClocktowerFlowResponse flow = buildFlow(room);
ExecutionCandidateResponse candidate = flow.executionCandidate();
if(Boolean.TRUE.

equals(request.execute())){
        if(!candidate.

executable() ||candidate.

nominationId() ==null||candidate.

nomineeSeatId() ==null){
        throw new

ClocktowerException("CLOCKTOWER_EXECUTION_CANDIDATE_REQUIRED");
    }
            rulingService.

create(roomId, new ClocktowerRulingCreateRequest(
        ClocktowerRulingType.EXECUTE_PLAYER, candidate.nomineeSeatId(),candidate.

nominationId(),
            null,null,null,ClocktowerRulingReason.VOTE_EXECUTION,request.

note(),
            "一名玩家被处决",ClocktowerVisibility.PUBLIC,false),principal);
        if(deathPolicy ==ClocktowerExecutionDeathPolicy.MARK_DEAD){
ClocktowerSeatPo nominee = seatRepository
        .findByIdAndRoomIdAndDeletedFalse(candidate.nomineeSeatId(), roomId)
        .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_SEAT_NOT_FOUND"));
        if("DEAD".

equals(nominee.getLifeStatus())){
        throw new

ClocktowerException("CLOCKTOWER_EXECUTION_TARGET_ALREADY_DEAD");
        }
                rulingService.

create(roomId, new ClocktowerRulingCreateRequest(
        ClocktowerRulingType.MARK_DEAD, candidate.nomineeSeatId(), null,
        null,null,null,ClocktowerRulingReason.VOTE_EXECUTION,request.

note(),
                "一名玩家死亡",ClocktowerVisibility.PUBLIC,false),principal);
        }
        }else{
        if(deathPolicy ==ClocktowerExecutionDeathPolicy.MARK_DEAD){
        throw new

ClocktowerException("CLOCKTOWER_EXECUTION_DEATH_POLICY_INVALID");
    }
            if(candidate.

executable()){
        throw new

ClocktowerException("CLOCKTOWER_EXECUTION_CANDIDATE_EXISTS");
    }
            rulingService.

create(roomId, new ClocktowerRulingCreateRequest(
        ClocktowerRulingType.SKIP_EXECUTION, null,null,null,
               null,null,ClocktowerRulingReason.VOTE_EXECUTION, request.note(),
            "今日无人被处决",ClocktowerVisibility.PUBLIC,false),principal);
        }
ClocktowerRoomPo refreshed = roomRepository.findByIdAndDeletedFalse(roomId)
        .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_ROOM_NOT_FOUND"));
return

buildFlow(refreshed);
```

Add imports to `ClocktowerFlowServiceImpl`:

```java
import top.egon.mario.clocktower.common.enums.ClocktowerRulingReason;
import top.egon.mario.clocktower.common.enums.ClocktowerRulingType;
import top.egon.mario.clocktower.common.enums.ClocktowerVisibility;
import top.egon.mario.clocktower.flow.dto.ClocktowerExecutionDeathPolicy;
import top.egon.mario.clocktower.ruling.dto.ClocktowerRulingCreateRequest;
```

- [ ] **Step 7: Run flow tests**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/be
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerFlowServiceTests test
```

Expected: PASS.

- [ ] **Step 8: Add victory suggestion tests**

Append to `ClocktowerFlowServiceTests`:

```java

@Test
void demonDeathSuggestsGoodVictory() {
    Long roomId = dayRoom();
    context.seatRepository().findByRoomIdAndDeletedFalseOrderBySeatNoAsc(roomId).stream()
            .filter(seat -> "DEMON".equals(String.valueOf(seat.getRoleType())))
            .forEach(seat -> seat.setLifeStatus("DEAD"));

    var flow = flowService.getFlow(roomId, storyteller());

    assertThat(flow.victoryCandidate()).isNotNull();
    assertThat(flow.victoryCandidate().winner()).isEqualTo("GOOD");
}

@Test
void twoAliveWithLivingDemonSuggestsEvilVictory() {
    Long roomId = dayRoom();
    var seats = context.seatRepository().findByRoomIdAndDeletedFalseOrderBySeatNoAsc(roomId);
    seats.stream()
            .filter(seat -> !"DEMON".equals(String.valueOf(seat.getRoleType())))
            .skip(1)
            .forEach(seat -> seat.setLifeStatus("DEAD"));

    var flow = flowService.getFlow(roomId, storyteller());

    assertThat(flow.victoryCandidate()).isNotNull();
    assertThat(flow.victoryCandidate().winner()).isEqualTo("EVIL");
}
```

- [ ] **Step 9: Run victory tests**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/be
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerFlowServiceTests test
```

Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add \
  be/src/main/java/top/egon/mario/clocktower/common/enums/ClocktowerEventType.java \
  be/src/main/java/top/egon/mario/clocktower/event/repository/ClocktowerEventRepository.java \
  be/src/main/java/top/egon/mario/clocktower/flow/service/impl/ClocktowerFlowServiceImpl.java \
  be/src/main/java/top/egon/mario/clocktower/ruling/service/impl/ClocktowerRulingServiceImpl.java \
  be/src/test/java/top/egon/mario/clocktower/flow/ClocktowerFlowServiceTests.java \
  be/src/test/java/top/egon/mario/clocktower/ruling/ClocktowerRulingServiceTests.java
git commit -m "feat(clocktower): resolve execution flow"
```

---

### Task 7: Add Frontend Flow Client And Storyteller Flow Tab

**Files:**

- Modify: `fe/src/modules/clocktower/clocktowerTypes.ts`
- Modify: `fe/src/modules/clocktower/clocktowerService.ts`
- Modify: `fe/src/modules/clocktower/clocktowerService.test.ts`
- Modify: `fe/src/modules/clocktower/StorytellerGrimoirePage.tsx`
- Modify: `fe/src/modules/clocktower/StorytellerGrimoirePage.test.tsx`
- Modify: `fe/src/modules/clocktower/components/NightChecklist.tsx`

- [ ] **Step 1: Add failing frontend service tests**

Modify imports in `fe/src/modules/clocktower/clocktowerService.test.ts`:

```ts
import {
    advanceClocktowerFlow,
    closeClocktowerNomination,
    confirmClocktowerExecution,
    getClocktowerFlow,
    skipClocktowerNightTask,
    // keep existing imports
} from './clocktowerService'
```

Add tests:

```ts
it('loads and advances room flow', async () => {
    const {requestJson} = await import('../../services/request')

    await getClocktowerFlow(7)
    await advanceClocktowerFlow(7)

    expect(requestJson).toHaveBeenCalledWith('/api/clocktower/rooms/7/flow')
    expect(requestJson).toHaveBeenCalledWith('/api/clocktower/rooms/7/flow/advance', {method: 'POST'})
})

it('submits flow control actions', async () => {
    const {requestJson} = await import('../../services/request')

    await skipClocktowerNightTask(7, 11, {reason: '无需唤醒'})
    await closeClocktowerNomination(7, 12, {note: '投票结束'})
    await confirmClocktowerExecution(7, {execute: true, deathPolicy: 'NO_CHANGE', note: '执行但未死亡'})

    expect(requestJson).toHaveBeenCalledWith('/api/clocktower/rooms/7/night-tasks/11/skip', {
        method: 'POST',
        body: {reason: '无需唤醒'},
    })
    expect(requestJson).toHaveBeenCalledWith('/api/clocktower/rooms/7/nominations/12/close', {
        method: 'POST',
        body: {note: '投票结束'},
    })
    expect(requestJson).toHaveBeenCalledWith('/api/clocktower/rooms/7/execution/confirm', {
        method: 'POST',
        body: {execute: true, deathPolicy: 'NO_CHANGE', note: '执行但未死亡'},
    })
})
```

- [ ] **Step 2: Run failing frontend service tests**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/fe
npm test -- clocktowerService.test.ts
```

Expected: FAIL because the flow client functions do not exist.

- [ ] **Step 3: Add frontend flow types**

Append to `fe/src/modules/clocktower/clocktowerTypes.ts` after `GamePhaseResponse`:

```ts
export type ClocktowerFlowTransition =
    | 'COMPLETE_FIRST_NIGHT'
    | 'START_NOMINATION'
    | 'START_EXECUTION'
    | 'START_NIGHT'
    | 'COMPLETE_NIGHT'
    | 'NONE'

export type NightTaskSummaryResponse = {
    total: number
    pending: number
    done: number
    skipped: number
}

export type NominationSummaryResponse = {
    nominationId: number
    nominatorSeatId: number
    nomineeSeatId: number
    voteCount: number
    status: string
}

export type ExecutionCandidateResponse = {
    resolved: boolean
    executable: boolean
    nominationId?: number | null
    nomineeSeatId?: number | null
    voteCount: number
    threshold: number
    reason: string
}

export type VictoryCandidateResponse = {
    winner: 'GOOD' | 'EVIL'
    reason: string
}

export type ClocktowerFlowResponse = {
    roomId: number
    phase: GamePhaseResponse
    nextTransition: ClocktowerFlowTransition
    advanceAllowed: boolean
    blockingReasons: string[]
    nightTaskSummary: NightTaskSummaryResponse
    openNomination?: NominationSummaryResponse | null
    executionCandidate?: ExecutionCandidateResponse | null
    victoryCandidate?: VictoryCandidateResponse | null
}

export type SkipNightTaskRequest = {
    reason: string
}

export type CloseNominationRequest = {
    note?: string | null
}

export type ClocktowerExecutionDeathPolicy = 'NO_CHANGE' | 'MARK_DEAD'

export type ExecutionConfirmRequest = {
    execute: boolean
    deathPolicy: ClocktowerExecutionDeathPolicy
    note?: string | null
}
```

- [ ] **Step 4: Add frontend flow client functions**

Add imports to `clocktowerService.ts`:

```ts
ClocktowerFlowResponse,
    CloseNominationRequest,
    ExecutionConfirmRequest,
    SkipNightTaskRequest,
```

Add functions after `getClocktowerNightChecklist`:

```ts
export function getClocktowerFlow(roomId: number) {
    return requestJson<ClocktowerFlowResponse>(`/api/clocktower/rooms/${roomId}/flow`)
}

export function advanceClocktowerFlow(roomId: number) {
    return requestJson<ClocktowerFlowResponse>(`/api/clocktower/rooms/${roomId}/flow/advance`, {method: 'POST'})
}

export function skipClocktowerNightTask(roomId: number, taskId: number, request: SkipNightTaskRequest) {
    return requestJson<ClocktowerFlowResponse>(`/api/clocktower/rooms/${roomId}/night-tasks/${taskId}/skip`, {
        method: 'POST',
        body: request,
    })
}

export function closeClocktowerNomination(roomId: number, nominationId: number, request: CloseNominationRequest) {
    return requestJson<ClocktowerFlowResponse>(`/api/clocktower/rooms/${roomId}/nominations/${nominationId}/close`, {
        method: 'POST',
        body: request,
    })
}

export function confirmClocktowerExecution(roomId: number, request: ExecutionConfirmRequest) {
    return requestJson<ClocktowerFlowResponse>(`/api/clocktower/rooms/${roomId}/execution/confirm`, {
        method: 'POST',
        body: request,
    })
}
```

- [ ] **Step 5: Run frontend service tests**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/fe
npm test -- clocktowerService.test.ts
```

Expected: PASS.

- [ ] **Step 6: Add failing flow tab rendering test**

Modify `StorytellerGrimoirePage.test.tsx` service mock:

```ts
getClocktowerFlow: vi.fn().mockResolvedValue({
    roomId: 7,
    phase: {phase: 'FIRST_NIGHT', dayNo: 0, nightNo: 1},
    nextTransition: 'COMPLETE_FIRST_NIGHT',
    advanceAllowed: false,
    blockingReasons: ['CLOCKTOWER_NIGHT_TASKS_PENDING'],
    nightTaskSummary: {total: 2, pending: 2, done: 0, skipped: 0},
    openNomination: null,
    executionCandidate: {
        resolved: false,
        executable: false,
        nominationId: null,
        nomineeSeatId: null,
        voteCount: 0,
        threshold: 3,
        reason: 'NO_CLOSED_NOMINATION'
    },
    victoryCandidate: null,
}),
    advanceClocktowerFlow
:
vi.fn(),
    skipClocktowerNightTask
:
vi.fn(),
    closeClocktowerNomination
:
vi.fn(),
    confirmClocktowerExecution
:
vi.fn(),
```

Add import:

```ts
FlowPanel,
```

Add test:

```tsx
test('renders flow panel with blocking reason', () => {
    const markup = renderToStaticMarkup(
        <FlowPanel
            flow={{
                roomId: 7,
                phase: {phase: 'FIRST_NIGHT', dayNo: 0, nightNo: 1},
                nextTransition: 'COMPLETE_FIRST_NIGHT',
                advanceAllowed: false,
                blockingReasons: ['CLOCKTOWER_NIGHT_TASKS_PENDING'],
                nightTaskSummary: {total: 2, pending: 2, done: 0, skipped: 0},
                openNomination: null,
                executionCandidate: {
                    resolved: false,
                    executable: false,
                    nominationId: null,
                    nomineeSeatId: null,
                    voteCount: 0,
                    threshold: 3,
                    reason: 'NO_CLOSED_NOMINATION'
                },
                victoryCandidate: null,
            }}
            loading={false}
            onAdvance={() => Promise.resolve()}
            onConfirmExecution={() => Promise.resolve()}
            onConfirmNoExecution={() => Promise.resolve()}
        />,
    )

    expect(markup).toContain('流程')
    expect(markup).toContain('首夜')
    expect(markup).toContain('待处理 2')
    expect(markup).toContain('夜晚任务未完成')
})

test('renders execution resolution controls with required note', () => {
    const markup = renderToStaticMarkup(
        <FlowPanel
            flow={{
                roomId: 7,
                phase: {phase: 'EXECUTION', dayNo: 1, nightNo: 1},
                nextTransition: 'START_NIGHT',
                advanceAllowed: false,
                blockingReasons: ['CLOCKTOWER_EXECUTION_NOT_RESOLVED'],
                nightTaskSummary: {total: 0, pending: 0, done: 0, skipped: 0},
                openNomination: null,
                executionCandidate: {
                    resolved: false,
                    executable: true,
                    nominationId: 12,
                    nomineeSeatId: 3,
                    voteCount: 4,
                    threshold: 3,
                    reason: 'EXECUTION_CANDIDATE'
                },
                victoryCandidate: null,
            }}
            loading={false}
            onAdvance={() => Promise.resolve()}
            onConfirmExecution={() => Promise.resolve()}
            onConfirmNoExecution={() => Promise.resolve()}
        />,
    )

    expect(markup).toContain('处决结算')
    expect(markup).toContain('结算原因')
    expect(markup).toContain('确认处决但不死亡')
    expect(markup).toContain('确认处决并标记死亡')
})
```

- [ ] **Step 7: Run failing flow panel test**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/fe
npm test -- StorytellerGrimoirePage.test.tsx
```

Expected: FAIL because `FlowPanel` does not exist.

- [ ] **Step 8: Implement flow tab**

Modify imports in `StorytellerGrimoirePage.tsx`:

```ts
import {Button, Empty, Input, Space, Tag, Typography} from 'antd'
import {
    advanceClocktowerFlow,
    confirmClocktowerExecution,
    getClocktowerFlow,
    // keep existing imports
} from './clocktowerService'
import type {
    ClocktowerExecutionDeathPolicy,
    ClocktowerFlowResponse,
    // keep existing imports
} from './clocktowerTypes'
```

Add state:

```ts
const [flow, setFlow] = useState<ClocktowerFlowResponse | null>(null)
const [flowLoading, setFlowLoading] = useState(false)
```

In `load`, include `getClocktowerFlow(numericRoomId)` in `Promise.all` and `setFlow(flowResponse)`.

Add handler:

```ts
async function advanceFlow() {
    if (!Number.isFinite(numericRoomId)) {
        return
    }
    setFlowLoading(true)
    try {
        const response = await advanceClocktowerFlow(numericRoomId)
        setFlow(response)
        await load()
        message.success('流程已推进')
    } catch (caught) {
        message.error(resolveErrorMessage(caught))
    } finally {
        setFlowLoading(false)
    }
}

async function confirmExecution(deathPolicy: ClocktowerExecutionDeathPolicy, note: string) {
    if (!Number.isFinite(numericRoomId)) {
        return
    }
    setFlowLoading(true)
    try {
        const response = await confirmClocktowerExecution(numericRoomId, {execute: true, deathPolicy, note})
        setFlow(response)
        await load()
        message.success('处决已结算')
    } catch (caught) {
        message.error(resolveErrorMessage(caught))
    } finally {
        setFlowLoading(false)
    }
}

async function confirmNoExecution(note: string) {
    if (!Number.isFinite(numericRoomId)) {
        return
    }
    setFlowLoading(true)
    try {
        const response = await confirmClocktowerExecution(numericRoomId, {
            execute: false,
            deathPolicy: 'NO_CHANGE',
            note,
        })
        setFlow(response)
        await load()
        message.success('无人处决已确认')
    } catch (caught) {
        message.error(resolveErrorMessage(caught))
    } finally {
        setFlowLoading(false)
    }
}
```

Add a tab item before "待处理任务":

```tsx
{
    key: 'flow',
        label
:
    '流程',
        children
:
    (
        <FlowPanel
            flow={flow}
            loading={flowLoading}
            onAdvance={advanceFlow}
            onConfirmExecution={confirmExecution}
            onConfirmNoExecution={confirmNoExecution}
        />
    ),
}
,
```

Export `FlowPanel`:

```tsx
export function FlowPanel({
                              flow,
                              loading,
                              onAdvance,
                              onConfirmExecution,
                              onConfirmNoExecution,
                          }: {
    flow: ClocktowerFlowResponse | null
    loading: boolean
    onAdvance: () => Promise<void>
    onConfirmExecution: (deathPolicy: ClocktowerExecutionDeathPolicy, note: string) => Promise<void>
    onConfirmNoExecution: (note: string) => Promise<void>
}) {
    const [executionNote, setExecutionNote] = useState('')
    if (!flow) {
        return <Empty description="暂无流程信息"/>
    }
    const canSubmitExecution = executionNote.trim().length > 0
    return (
        <Space direction="vertical" size="middle" style={{width: '100%'}}>
            <Space wrap>
                <Tag color="blue">{phaseText(flow.phase.phase)}</Tag>
                <Typography.Text type="secondary">
                    第 {flow.phase.dayNo} 天 / 第 {flow.phase.nightNo} 夜
                </Typography.Text>
                <Tag color={flow.advanceAllowed ? 'success' : 'warning'}>
                    {flow.advanceAllowed ? '可推进' : '待处理'}
                </Tag>
            </Space>
            <Space wrap>
                <Tag>夜晚任务 {flow.nightTaskSummary.total}</Tag>
                <Tag color={flow.nightTaskSummary.pending > 0 ? 'warning' : 'success'}>
                    待处理 {flow.nightTaskSummary.pending}
                </Tag>
                <Tag color="success">完成 {flow.nightTaskSummary.done}</Tag>
                <Tag>跳过 {flow.nightTaskSummary.skipped}</Tag>
            </Space>
            {flow.blockingReasons.map((reason) => (
                <Tag color="warning" key={reason}>{flowBlockingText(reason)}</Tag>
            ))}
            {flow.executionCandidate && (
                <>
                    <Space wrap>
                        <Tag color={flow.executionCandidate.executable ? 'processing' : 'default'}>
                            {flow.executionCandidate.executable ? '有处决候选' : '无人处决'}
                        </Tag>
                        <Typography.Text type="secondary">
                            票数 {flow.executionCandidate.voteCount} / 门槛 {flow.executionCandidate.threshold}
                        </Typography.Text>
                    </Space>
                    {flow.phase.phase === 'EXECUTION' && !flow.executionCandidate.resolved && (
                        <Space direction="vertical" size="small" style={{width: '100%'}}>
                            <Input.TextArea
                                autoSize={{minRows: 2, maxRows: 4}}
                                onChange={(event) => setExecutionNote(event.target.value)}
                                placeholder="结算原因"
                                value={executionNote}
                            />
                            {flow.executionCandidate.executable ? (
                                <Space wrap>
                                    <Button
                                        disabled={!canSubmitExecution}
                                        loading={loading}
                                        onClick={voidify(() => onConfirmExecution('NO_CHANGE', executionNote.trim()))}
                                    >
                                        确认处决但不死亡
                                    </Button>
                                    <Button
                                        danger
                                        disabled={!canSubmitExecution}
                                        loading={loading}
                                        onClick={voidify(() => onConfirmExecution('MARK_DEAD', executionNote.trim()))}
                                    >
                                        确认处决并标记死亡
                                    </Button>
                                </Space>
                            ) : (
                                <Button
                                    disabled={!canSubmitExecution}
                                    loading={loading}
                                    onClick={voidify(() => onConfirmNoExecution(executionNote.trim()))}
                                >
                                    确认无人处决
                                </Button>
                            )}
                        </Space>
                    )}
                </>
            )}
            {flow.victoryCandidate && (
                <Tag color="error">胜负建议：{flow.victoryCandidate.winner}</Tag>
            )}
            <Button disabled={!flow.advanceAllowed} loading={loading} onClick={voidify(onAdvance)} type="primary">
                {transitionText(flow.nextTransition)}
            </Button>
        </Space>
    )
}

function phaseText(phase: string) {
    if (phase === 'FIRST_NIGHT') {
        return '首夜'
    }
    if (phase === 'NOMINATION') {
        return '提名投票'
    }
    if (phase === 'EXECUTION') {
        return '处决结算'
    }
    if (phase === 'NIGHT') {
        return '夜晚'
    }
    if (phase === 'DAY') {
        return '白天'
    }
    return phase
}

function transitionText(transition: string) {
    switch (transition) {
        case 'COMPLETE_FIRST_NIGHT':
            return '进入第一天'
        case 'START_NOMINATION':
            return '进入提名投票'
        case 'START_EXECUTION':
            return '进入处决结算'
        case 'START_NIGHT':
            return '进入下一夜'
        case 'COMPLETE_NIGHT':
            return '进入下一天'
        default:
            return '暂无可推进流程'
    }
}

function flowBlockingText(reason: string) {
    switch (reason) {
        case 'CLOCKTOWER_NIGHT_TASKS_PENDING':
            return '夜晚任务未完成'
        case 'CLOCKTOWER_OPEN_NOMINATION_EXISTS':
            return '仍有提名未关闭'
        case 'CLOCKTOWER_EXECUTION_NOT_RESOLVED':
            return '处决尚未结算'
        case 'CLOCKTOWER_GAME_ALREADY_ENDED':
            return '游戏已结束'
        default:
            return reason
    }
}
```

- [ ] **Step 9: Run frontend flow tests**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/fe
npm test -- clocktowerService.test.ts StorytellerGrimoirePage.test.tsx
npm run typecheck
```

Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add \
  fe/src/modules/clocktower/clocktowerTypes.ts \
  fe/src/modules/clocktower/clocktowerService.ts \
  fe/src/modules/clocktower/clocktowerService.test.ts \
  fe/src/modules/clocktower/StorytellerGrimoirePage.tsx \
  fe/src/modules/clocktower/StorytellerGrimoirePage.test.tsx \
  fe/src/modules/clocktower/components/NightChecklist.tsx
git commit -m "feat(clocktower): add storyteller flow panel"
```

---

### Task 8: Final Integration Validation And Review

**Files:**

- Review only unless validation reveals a defect.

- [ ] **Step 1: Run all Clocktower backend tests**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/be
./mvnw -Dmaven.build.cache.enabled=false -Dtest='*Clocktower*Tests' test
```

Expected: PASS with zero failures.

- [ ] **Step 2: Run focused frontend tests**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/fe
npm test -- clocktowerService.test.ts StorytellerGrimoirePage.test.tsx GameRoomPage.test.tsx
```

Expected: PASS with zero failures.

- [ ] **Step 3: Run frontend typecheck**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/fe
npm run typecheck
```

Expected: PASS.

- [ ] **Step 4: Run lint checks**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/fe
npx eslint src/modules/clocktower/StorytellerGrimoirePage.tsx src/modules/clocktower/GameRoomPage.tsx src/modules/clocktower/clocktowerService.ts src/modules/clocktower/clocktowerTypes.ts
npx antd lint src/modules/clocktower/StorytellerGrimoirePage.tsx --format json
```

Expected: ESLint exits with no errors. Existing warnings outside changed logic may be reported, but do not claim a clean
lint if the command exits nonzero. AntD lint summary must show `"total": 0`.

- [ ] **Step 5: Run whitespace check**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario
git diff --check
git status --short
```

Expected: `git diff --check` prints nothing. `git status --short` shows only intended changed files before final commit,
or is clean after commits.

- [ ] **Step 6: Request code review**

Use `superpowers:requesting-code-review` and ask for review of:

- Flow service state transitions.
- Drools decision facts and rules.
- Nomination and vote rule regressions.
- Execution confirmation using existing schema without migration.
- Frontend flow panel correctness.

- [ ] **Step 7: Fix review findings**

For each accepted finding, make the smallest scoped code change, run the targeted test from the affected task, and
commit with one of:

```bash
git commit -m "fix(clocktower): harden flow transition rules"
git commit -m "fix(clocktower): correct nomination voting guard"
git commit -m "fix(clocktower): polish storyteller flow panel"
```

- [ ] **Step 8: Final commit status**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario
git log --oneline -8
git status --short
```

Expected: latest commits are the flow implementation commits and worktree is clean except unrelated user-owned untracked
files.
