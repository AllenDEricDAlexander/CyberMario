# Clocktower Heuristic Agent Player Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build task 12 so Clocktower Agent runtime uses deterministic heuristic play instead of hard-coded placeholder pass, false-vote, and night auto-choice behavior.

**Architecture:** Add a Strategy boundary through `ClocktowerAgentPolicy`, keep the runtime as orchestration, and split deterministic behavior into small planner services for speech, bluffing, nominations, votes, and night choices. Legal action validation remains in existing game services; the private view only exposes what the policy is allowed to select.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring Data JPA, Jackson, JUnit 5, AssertJ, existing Clocktower agent runtime, action executor, public mic, nomination, vote, night task, and private view services.

---

## File Structure

Create:

- `be/src/main/java/top/egon/mario/clocktower/agent/strategy/AgentDecision.java`: selected intent plus deterministic reasoning summary and diagnostics.
- `be/src/main/java/top/egon/mario/clocktower/agent/strategy/AgentDecisionContext.java`: immutable policy input built from private view, profile, trigger metadata, and runtime state.
- `be/src/main/java/top/egon/mario/clocktower/agent/strategy/AgentIntent.java`: sealed intent contract for public speech, grab mic, nomination, vote, night choice, pass, and no-op.
- `be/src/main/java/top/egon/mario/clocktower/agent/strategy/ClocktowerAgentPolicy.java`: Strategy interface.
- `be/src/main/java/top/egon/mario/clocktower/agent/strategy/HeuristicAgentPolicy.java`: deterministic dispatch from trigger type to planners.
- `be/src/main/java/top/egon/mario/clocktower/agent/strategy/AgentSpeechPlanner.java`: mic-turn and grab-mic speech selection.
- `be/src/main/java/top/egon/mario/clocktower/agent/strategy/AgentNominationPlanner.java`: deterministic nomination target selection.
- `be/src/main/java/top/egon/mario/clocktower/agent/strategy/AgentVotePlanner.java`: deterministic vote selection including dead-vote threshold.
- `be/src/main/java/top/egon/mario/clocktower/agent/strategy/AgentNightChoicePlanner.java`: deterministic legal night target selection.
- `be/src/main/java/top/egon/mario/clocktower/agent/strategy/AgentIntentExecutor.java`: maps `AgentIntent` to existing Agent game action and public mic services.
- `be/src/main/java/top/egon/mario/clocktower/agent/strategy/AgentDecisionSummary.java`: stable task result payload builder.
- `be/src/main/java/top/egon/mario/clocktower/agent/strategy/troublebrewing/TroubleBrewingBluffPlanner.java`: idempotent evil bluff plan creation and loading.
- `be/src/test/java/top/egon/mario/clocktower/agent/strategy/ClocktowerAgentHeuristicPolicyTests.java`: focused planner and policy tests.

Modify:

- `be/src/main/java/top/egon/mario/clocktower/agent/runtime/ClocktowerAgentRuntime.java`: replace placeholder switch with private-view, policy, legality check, execution, and decision summary.
- `be/src/main/java/top/egon/mario/clocktower/agent/runtime/ClocktowerAgentTaskScheduler.java`: add an active-agent scheduling method that excludes one actor seat for public-event reactions.
- `be/src/main/java/top/egon/mario/clocktower/agent/runtime/ClocktowerAgentTriggerListener.java`: schedule `MIC_GRAB_OPENED` and filtered `PUBLIC_EVENT_APPENDED`.
- `be/src/main/java/top/egon/mario/clocktower/agent/repository/ClocktowerAgentProfileRepository.java`: add deleted-aware profile lookup by id.
- `be/src/main/java/top/egon/mario/clocktower/agent/view/service/impl/ClocktowerAgentPrivateViewServiceImpl.java`: expose accurate legal intents and evil-team role context.
- `be/src/main/java/top/egon/mario/clocktower/game/mic/service/ClocktowerPublicMicService.java`: add internal Agent-capable `grabMicAsActor`.
- `be/src/main/java/top/egon/mario/clocktower/game/mic/service/impl/ClocktowerPublicMicServiceImpl.java`: share grab-mic validation and support Agent actor seats.
- `be/src/test/java/top/egon/mario/clocktower/agent/runtime/ClocktowerAgentTaskRuntimeTests.java`: update mic placeholder expectation and add runtime summary, grab, nomination, vote, and night-choice coverage.
- `be/src/test/java/top/egon/mario/clocktower/agent/view/ClocktowerAgentPrivateViewServiceTests.java`: add legal-intent and evil-context coverage.
- `be/src/test/java/top/egon/mario/clocktower/game/mic/ClocktowerPublicMicServiceTests.java`: add internal Agent grab-mic coverage.

No Flyway migration is needed for task 12.

## Design Pattern Decision

Use Strategy for `ClocktowerAgentPolicy` because task 12 has a real variation point: deterministic heuristics now and later LLM policy or audited policy in task 15. Use small domain-service planners instead of additional strategies because speech, nomination, vote, night choice, and bluffing are fixed responsibilities in v1, not interchangeable policy implementations. Do not add Factory, Chain of Responsibility, State, or Command classes beyond the existing intent record because the current runtime trigger model is already explicit and direct code is easier to test.

## Shared Contracts

Create these contracts before planner implementation:

```java
package top.egon.mario.clocktower.agent.strategy;

import java.util.Map;

public record AgentDecision(
        AgentIntent intent,
        String reasoningSummary,
        Map<String, Object> diagnostics
) {
}
```

```java
package top.egon.mario.clocktower.agent.strategy;

import top.egon.mario.clocktower.agent.po.ClocktowerAgentProfilePo;
import top.egon.mario.clocktower.agent.view.dto.AgentLegalIntentView;
import top.egon.mario.clocktower.agent.view.dto.AgentPrivateView;

import java.util.List;
import java.util.Map;

public record AgentDecisionContext(
        AgentPrivateView view,
        ClocktowerAgentProfilePo profile,
        List<AgentLegalIntentView> legalIntents,
        String triggerType,
        Map<String, Object> taskMetadata,
        Map<String, Object> runtimeState
) {
}
```

```java
package top.egon.mario.clocktower.agent.strategy;

import java.util.List;
import java.util.Map;

public sealed interface AgentIntent {

    record PublicSpeech(String content) implements AgentIntent {
    }

    record GrabMic(String reason) implements AgentIntent {
    }

    record FinishSpeech(String reason) implements AgentIntent {
    }

    record Nominate(Long targetGameSeatId, String reason) implements AgentIntent {
    }

    record Vote(Long nominationId, boolean vote, String reason) implements AgentIntent {
    }

    record NightChoice(Long taskId, List<Long> targetGameSeatIds, Map<String, Object> payload)
            implements AgentIntent {
    }

    record Pass(String reason) implements AgentIntent {
    }

    record Noop(String reason) implements AgentIntent {
    }
}
```

```java
package top.egon.mario.clocktower.agent.strategy;

public interface ClocktowerAgentPolicy {

    AgentDecision decide(AgentDecisionContext context);
}
```

---

### Task 1: Strategy Contract And Policy Red Tests

**Files:**
- Create: `be/src/test/java/top/egon/mario/clocktower/agent/strategy/ClocktowerAgentHeuristicPolicyTests.java`
- Create in Task 2: the shared contract files listed in "Shared Contracts"

- [ ] **Step 1: Write failing policy tests**

Create `ClocktowerAgentHeuristicPolicyTests` with these tests and helpers. The tests deliberately instantiate planners directly so failures point at the new strategy package, not Spring wiring.

```java
package top.egon.mario.clocktower.agent.strategy;

import org.junit.jupiter.api.Test;
import top.egon.mario.clocktower.agent.po.ClocktowerAgentProfilePo;
import top.egon.mario.clocktower.agent.view.dto.AgentLegalIntentView;
import top.egon.mario.clocktower.agent.view.dto.AgentMemoryView;
import top.egon.mario.clocktower.agent.view.dto.AgentPrivateInfoView;
import top.egon.mario.clocktower.agent.view.dto.AgentPrivateView;
import top.egon.mario.clocktower.agent.view.dto.AgentPublicSeatView;
import top.egon.mario.clocktower.agent.strategy.troublebrewing.TroubleBrewingBluffPlanner;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ClocktowerAgentHeuristicPolicyTests {

    @Test
    void goodAgentWithPrivateInfoSpeaksOnMicTurnAndDoesNotExposeRawHiddenMarkers() {
        HeuristicAgentPolicy policy = policy();
        AgentDecision decision = policy.decide(context(goodView(
                        List.of(publicSpeech(), passIntent()),
                        List.of(privateInfo(Map.of("infoType", "EMPATH", "evilCount", 1))),
                        List.of()),
                balancedProfile(70), "MIC_TURN_STARTED", Map.of()));

        assertThat(decision.intent()).isInstanceOf(AgentIntent.PublicSpeech.class);
        AgentIntent.PublicSpeech speech = (AgentIntent.PublicSpeech) decision.intent();
        assertThat(speech.content()).contains("我这边有信息");
        assertThat(speech.content()).doesNotContain("evilCount");
        assertThat(speech.content().length()).isBetween(10, 180);
        assertThat(decision.reasoningSummary()).contains("private info");
    }

    @Test
    void lowTalkativeGoodAgentPassesWhenNoUsefulContentExists() {
        HeuristicAgentPolicy policy = policy();
        AgentDecision decision = policy.decide(context(goodView(
                        List.of(publicSpeech(), passIntent()),
                        List.of(),
                        List.of()),
                balancedProfile(20), "MIC_TURN_STARTED", Map.of()));

        assertThat(decision.intent()).isInstanceOf(AgentIntent.Pass.class);
        assertThat(decision.reasoningSummary()).contains("low talkativeness");
    }

    @Test
    void evilAgentUsesBluffPlanInsteadOfTrueRoleOnMicTurn() {
        HeuristicAgentPolicy policy = policy();
        AgentDecision decision = policy.decide(context(evilView(
                        List.of(publicSpeech(), passIntent()),
                        List.of(memory("BLUFF_PLAN", Map.of(
                                "claimRoleCode", "CHEF",
                                "fakeInfo", Map.of("chefNumber", 1),
                                "pushTargets", List.of(2L))))),
                balancedProfile(80), "MIC_TURN_STARTED", Map.of()));

        assertThat(decision.intent()).isInstanceOf(AgentIntent.PublicSpeech.class);
        AgentIntent.PublicSpeech speech = (AgentIntent.PublicSpeech) decision.intent();
        assertThat(speech.content()).contains("厨师");
        assertThat(speech.content()).doesNotContain("间谍");
        assertThat(decision.diagnostics()).containsEntry("alignment", "EVIL");
    }

    @Test
    void nominationChoosesEligibleTargetAndDeadAgentDoesNotNominate() {
        HeuristicAgentPolicy policy = policy();
        AgentDecision aliveDecision = policy.decide(context(goodView(
                        List.of(nominateIntent(List.of(2L, 3L))),
                        List.of(),
                        List.of(memory("SUSPICION_SCORE", Map.of("score", 90), 2L))),
                balancedProfile(50), "PHASE_CHANGED", Map.of("phase", "NOMINATION")));

        assertThat(aliveDecision.intent()).isInstanceOf(AgentIntent.Nominate.class);
        assertThat(((AgentIntent.Nominate) aliveDecision.intent()).targetGameSeatId()).isEqualTo(2L);

        AgentPrivateView deadView = view("GOOD", "EMPATH", "DEAD",
                false, List.of(nominateIntent(List.of(2L))), List.of(), List.of(), Map.of());
        AgentDecision deadDecision = policy.decide(context(deadView, balancedProfile(50),
                "PHASE_CHANGED", Map.of("phase", "NOMINATION")));

        assertThat(deadDecision.intent()).isInstanceOf(AgentIntent.Noop.class);
        assertThat(deadDecision.reasoningSummary()).contains("dead");
    }

    @Test
    void evilNominationAvoidsDemonWhenNonDemonTargetExists() {
        HeuristicAgentPolicy policy = policy();
        AgentPrivateView evil = evilView(List.of(nominateIntent(List.of(2L, 3L, 4L))), List.of());
        AgentDecision decision = policy.decide(context(evil, balancedProfile(80),
                "PHASE_CHANGED", Map.of("phase", "NOMINATION")));

        assertThat(decision.intent()).isInstanceOf(AgentIntent.Nominate.class);
        assertThat(((AgentIntent.Nominate) decision.intent()).targetGameSeatId()).isEqualTo(2L);
    }

    @Test
    void deadAgentUsesYesVoteOnlyAboveDeadVoteThreshold() {
        HeuristicAgentPolicy policy = policy();
        AgentPrivateView weakDead = view("GOOD", "CHEF", "DEAD", true,
                List.of(voteIntent(99L, 2L, true), voteIntent(99L, 2L, false)),
                List.of(memory("SUSPICION_SCORE", Map.of("score", 70), 2L)), List.of(), Map.of());

        AgentDecision weakDecision = policy.decide(context(weakDead, balancedProfile(50),
                "VOTE_WINDOW_OPENED", Map.of("nominationId", 99L)));
        assertThat(weakDecision.intent()).isInstanceOf(AgentIntent.Vote.class);
        assertThat(((AgentIntent.Vote) weakDecision.intent()).vote()).isFalse();

        AgentPrivateView strongDead = view("GOOD", "CHEF", "DEAD", true,
                List.of(voteIntent(99L, 2L, true), voteIntent(99L, 2L, false)),
                List.of(memory("SUSPICION_SCORE", Map.of("score", 90), 2L)), List.of(), Map.of());

        AgentDecision strongDecision = policy.decide(context(strongDead, balancedProfile(50),
                "VOTE_WINDOW_OPENED", Map.of("nominationId", 99L)));
        assertThat(strongDecision.intent()).isInstanceOf(AgentIntent.Vote.class);
        assertThat(((AgentIntent.Vote) strongDecision.intent()).vote()).isTrue();
    }

    @Test
    void nightChoiceUsesRoleHeuristicAndFallsBackToFirstLegalTarget() {
        HeuristicAgentPolicy policy = policy();
        AgentPrivateView monk = view("GOOD", "MONK", "ALIVE", true,
                List.of(nightIntent(501L, "MONK", List.of(1L, 2L, 3L))),
                List.of(memory("TRUST_SCORE", Map.of("score", 85), 3L)), List.of(), Map.of());

        AgentDecision decision = policy.decide(context(monk, balancedProfile(50),
                "NIGHT_TASK_OPENED", Map.of("taskId", 501L)));

        assertThat(decision.intent()).isInstanceOf(AgentIntent.NightChoice.class);
        AgentIntent.NightChoice choice = (AgentIntent.NightChoice) decision.intent();
        assertThat(choice.taskId()).isEqualTo(501L);
        assertThat(choice.targetGameSeatIds()).containsExactly(3L);
    }

    private HeuristicAgentPolicy policy() {
        TroubleBrewingBluffPlanner bluffPlanner = new TroubleBrewingBluffPlanner(null, null);
        return new HeuristicAgentPolicy(new AgentSpeechPlanner(bluffPlanner),
                new AgentNominationPlanner(), new AgentVotePlanner(), new AgentNightChoicePlanner());
    }

    private AgentDecisionContext context(AgentPrivateView view, ClocktowerAgentProfilePo profile,
                                         String triggerType, Map<String, Object> metadata) {
        return new AgentDecisionContext(view, profile, view.legalIntents(), triggerType, metadata, Map.of());
    }

    private ClocktowerAgentProfilePo balancedProfile(int talkativeness) {
        ClocktowerAgentProfilePo profile = new ClocktowerAgentProfilePo();
        profile.setName("balanced-test");
        profile.setTalkativeness(talkativeness);
        profile.setAggression(50);
        profile.setRiskTolerance(50);
        profile.setDeceptionLevel(50);
        return profile;
    }

    private AgentPrivateView goodView(List<AgentLegalIntentView> intents,
                                      List<AgentPrivateInfoView> privateInfos,
                                      List<AgentMemoryView> memories) {
        return view("GOOD", "EMPATH", "ALIVE", true, intents, memories, privateInfos, Map.of());
    }

    private AgentPrivateView evilView(List<AgentLegalIntentView> intents, List<AgentMemoryView> memories) {
        return view("EVIL", "SPY", "ALIVE", true, intents, memories, List.of(),
                Map.of("evilTeam", List.of(Map.of(
                        "gameSeatId", 4L,
                        "seatNo", 4,
                        "roleCode", "IMP",
                        "roleType", "DEMON"))));
    }

    private AgentPrivateView view(String alignment, String roleCode, String lifeStatus, boolean hasDeadVote,
                                  List<AgentLegalIntentView> intents, List<AgentMemoryView> memories,
                                  List<AgentPrivateInfoView> privateInfos, Map<String, Object> roleContext) {
        return new AgentPrivateView(1L, 10L, 1L, 1, "DAY", 1, 1, roleCode, roleCode,
                alignment, "SPY".equals(roleCode) ? "MINION" : "TOWNSFOLK", lifeStatus, lifeStatus,
                hasDeadVote, publicSeats(), List.of(), List.of(), privateInfos, memories, intents, roleContext);
    }

    private List<AgentPublicSeatView> publicSeats() {
        return List.of(
                new AgentPublicSeatView(1L, 1, "Agent", null, null, null, null,
                        "ALIVE", true, false, "AGENT", true, "ACTIVE"),
                new AgentPublicSeatView(2L, 2, "Player 2", null, null, null, null,
                        "ALIVE", true, false, "HUMAN", false, "ACTIVE"),
                new AgentPublicSeatView(3L, 3, "Player 3", null, null, null, null,
                        "ALIVE", true, false, "HUMAN", false, "ACTIVE"),
                new AgentPublicSeatView(4L, 4, "Player 4", null, null, null, null,
                        "ALIVE", true, false, "AGENT", true, "ACTIVE"));
    }

    private AgentLegalIntentView publicSpeech() {
        return new AgentLegalIntentView("PUBLIC_SPEECH", null, null, null, Map.of());
    }

    private AgentLegalIntentView passIntent() {
        return new AgentLegalIntentView("PASS", null, null, null, Map.of("passType", "MIC_TURN"));
    }

    private AgentLegalIntentView nominateIntent(List<Long> targets) {
        return new AgentLegalIntentView("NOMINATE", null, null, null,
                Map.of("eligibleTargetGameSeatIds", targets));
    }

    private AgentLegalIntentView voteIntent(Long nominationId, Long nomineeSeatId, boolean vote) {
        return new AgentLegalIntentView("VOTE", null, nominationId, vote,
                Map.of("nomineeGameSeatId", nomineeSeatId));
    }

    private AgentLegalIntentView nightIntent(Long taskId, String roleCode, List<Long> targets) {
        return new AgentLegalIntentView("NIGHT_CHOICE", taskId, null, null,
                Map.of("roleCode", roleCode, "taskType", "TARGET",
                        "legalTargetGameSeatIds", targets));
    }

    private AgentPrivateInfoView privateInfo(Map<String, Object> payload) {
        return new AgentPrivateInfoView(700L, 7L, "EMPATH", "RECEIVE_INFO", payload, Instant.now());
    }

    private AgentMemoryView memory(String type, Map<String, Object> content) {
        return memory(type, content, null);
    }

    private AgentMemoryView memory(String type, Map<String, Object> content, Long subjectGameSeatId) {
        return new AgentMemoryView(900L + (subjectGameSeatId == null ? 0L : subjectGameSeatId),
                null, null, type, subjectGameSeatId, content, 80, 1, 1, Instant.now());
    }
}
```

- [ ] **Step 2: Run red test**

Run from `be/`:

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerAgentHeuristicPolicyTests test
```

Expected: FAIL with missing `top.egon.mario.clocktower.agent.strategy` classes.

- [ ] **Step 3: Commit red tests**

```bash
git add be/src/test/java/top/egon/mario/clocktower/agent/strategy/ClocktowerAgentHeuristicPolicyTests.java
git commit -m "test(clocktower): cover heuristic agent policy"
```

### Task 2: Strategy Contract And Policy Green

**Files:**
- Create: `be/src/main/java/top/egon/mario/clocktower/agent/strategy/AgentDecision.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/agent/strategy/AgentDecisionContext.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/agent/strategy/AgentIntent.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/agent/strategy/ClocktowerAgentPolicy.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/agent/strategy/HeuristicAgentPolicy.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/agent/strategy/AgentSpeechPlanner.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/agent/strategy/AgentNominationPlanner.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/agent/strategy/AgentVotePlanner.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/agent/strategy/AgentNightChoicePlanner.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/agent/strategy/troublebrewing/TroubleBrewingBluffPlanner.java`
- Modify: `be/src/test/java/top/egon/mario/clocktower/agent/strategy/ClocktowerAgentHeuristicPolicyTests.java`

- [ ] **Step 1: Create shared contracts**

Add the four shared-contract files exactly as defined in "Shared Contracts".

- [ ] **Step 2: Create `HeuristicAgentPolicy` dispatch**

```java
package top.egon.mario.clocktower.agent.strategy;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.egon.mario.clocktower.agent.runtime.ClocktowerAgentTriggerType;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class HeuristicAgentPolicy implements ClocktowerAgentPolicy {

    public static final String POLICY_NAME = "HEURISTIC_V0";

    private final AgentSpeechPlanner speechPlanner;
    private final AgentNominationPlanner nominationPlanner;
    private final AgentVotePlanner votePlanner;
    private final AgentNightChoicePlanner nightChoicePlanner;

    @Override
    public AgentDecision decide(AgentDecisionContext context) {
        return switch (context.triggerType()) {
            case ClocktowerAgentTriggerType.MIC_TURN_STARTED -> speechPlanner.planMicTurn(context);
            case ClocktowerAgentTriggerType.MIC_GRAB_OPENED,
                    ClocktowerAgentTriggerType.PUBLIC_EVENT_APPENDED -> speechPlanner.planGrabMic(context);
            case ClocktowerAgentTriggerType.PHASE_CHANGED -> nominationPlanner.plan(context);
            case ClocktowerAgentTriggerType.VOTE_WINDOW_OPENED -> votePlanner.plan(context);
            case ClocktowerAgentTriggerType.NIGHT_TASK_OPENED -> nightChoicePlanner.plan(context);
            default -> new AgentDecision(new AgentIntent.Noop("trigger has no heuristic action"),
                    "no heuristic action for trigger", Map.of("triggerType", context.triggerType()));
        };
    }
}
```

- [ ] **Step 3: Create `AgentSpeechPlanner`**

```java
package top.egon.mario.clocktower.agent.strategy;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import top.egon.mario.clocktower.agent.strategy.troublebrewing.TroubleBrewingBluffPlanner;
import top.egon.mario.clocktower.agent.view.dto.AgentLegalIntentView;
import top.egon.mario.clocktower.agent.view.dto.AgentMemoryView;
import top.egon.mario.clocktower.agent.view.dto.AgentPrivateInfoView;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AgentSpeechPlanner {

    private final TroubleBrewingBluffPlanner bluffPlanner;

    public AgentDecision planMicTurn(AgentDecisionContext context) {
        if (!hasIntent(context, "PUBLIC_SPEECH")) {
            return passOrNoop(context, "cannot speak");
        }
        if ("EVIL".equals(context.view().myAlignment())) {
            Map<String, Object> bluff = bluffPlanner.loadOrCreatePlan(context);
            return new AgentDecision(new AgentIntent.PublicSpeech(evilSpeech(bluff)),
                    "evil bluff speech", Map.of("alignment", "EVIL", "claimRoleCode", bluff.get("claimRoleCode")));
        }
        if (!context.view().privateInfos().isEmpty()) {
            AgentPrivateInfoView info = context.view().privateInfos().getLast();
            return new AgentDecision(new AgentIntent.PublicSpeech(goodInfoSpeech(info)),
                    "shared private info and released mic", Map.of("privateInfoEventId", info.eventId()));
        }
        if (context.profile().getTalkativeness() < 55) {
            return passOrNoop(context, "low talkativeness");
        }
        Long target = context.view().publicSeats().stream()
                .filter(seat -> !seat.gameSeatId().equals(context.view().myGameSeatId()))
                .filter(seat -> "ACTIVE".equals(seat.status()))
                .map(seat -> seat.gameSeatId())
                .findFirst()
                .orElse(null);
        if (target == null) {
            return passOrNoop(context, "no useful speech target");
        }
        return new AgentDecision(new AgentIntent.PublicSpeech("我这轮先听一下" + target + "号的说法，尤其是白天投票和提名理由。"),
                "asked another seat for explanation", Map.of("questionTargetGameSeatId", target));
    }

    public AgentDecision planGrabMic(AgentDecisionContext context) {
        if (!hasIntent(context, "GRAB_MIC")) {
            return new AgentDecision(new AgentIntent.Noop("grab mic is not legal"),
                    "grab mic unavailable", Map.of("triggerType", context.triggerType()));
        }
        if ("EVIL".equals(context.view().myAlignment()) && context.profile().getTalkativeness() >= 65) {
            return new AgentDecision(new AgentIntent.GrabMic("evil agent wants to reinforce bluff"),
                    "grab mic for bluff reinforcement", Map.of("alignment", "EVIL"));
        }
        if (!context.view().privateInfos().isEmpty() && context.profile().getTalkativeness() >= 70) {
            return new AgentDecision(new AgentIntent.GrabMic("good agent has fresh info"),
                    "grab mic for fresh private info", Map.of("privateInfoCount", context.view().privateInfos().size()));
        }
        return new AgentDecision(new AgentIntent.Noop("no high priority grab reason"),
                "no grab reason", Map.of("talkativeness", context.profile().getTalkativeness()));
    }

    private AgentDecision passOrNoop(AgentDecisionContext context, String reason) {
        if (hasIntent(context, "PASS")) {
            return new AgentDecision(new AgentIntent.Pass(reason), reason, Map.of("passType", "MIC_TURN"));
        }
        return new AgentDecision(new AgentIntent.Noop(reason), reason, Map.of());
    }

    private boolean hasIntent(AgentDecisionContext context, String intentType) {
        return context.legalIntents().stream().map(AgentLegalIntentView::intentType).anyMatch(intentType::equals);
    }

    private String goodInfoSpeech(AgentPrivateInfoView info) {
        String role = info.roleCode() == null ? "身份信息" : info.roleCode();
        return "我这边有信息，偏向先按" + role + "的视角观察，暂时不把细节说死，想继续听相邻位置的发言。";
    }

    private String evilSpeech(Map<String, Object> bluff) {
        Object claim = bluff.getOrDefault("claimRoleCode", "CHEF");
        Object fakeInfo = bluff.getOrDefault("fakeInfo", Map.of("chefNumber", 1));
        return "我先报一个" + claim + "视角，信息是" + fakeInfo + "，这轮我更想听被推位置的解释。";
    }
}
```

- [ ] **Step 4: Create nomination, vote, and night planners**

Use these exact scoring helpers in each planner so test outcomes are deterministic.

```java
private int memoryScore(AgentDecisionContext context, Long targetGameSeatId, String memoryType) {
    return context.view().memories().stream()
            .filter(memory -> memoryType.equals(memory.memoryType()))
            .filter(memory -> targetGameSeatId.equals(memory.subjectGameSeatId()))
            .map(memory -> memory.content().get("score"))
            .filter(Number.class::isInstance)
            .map(Number.class::cast)
            .mapToInt(Number::intValue)
            .max()
            .orElse(50);
}
```

`AgentNominationPlanner#plan` must:

```java
if (!"ALIVE".equals(context.view().lifeStatus())) {
    return new AgentDecision(new AgentIntent.Noop("dead agents cannot nominate"),
            "dead agent cannot nominate", Map.of());
}
AgentLegalIntentView intent = firstIntent(context, "NOMINATE");
if (intent == null) {
    return new AgentDecision(new AgentIntent.Noop("nomination is not legal"),
            "nomination unavailable", Map.of());
}
List<Long> targets = longList(intent.payload().get("eligibleTargetGameSeatIds"));
Long chosen = targets.stream()
        .filter(target -> !evilDemonSeatIds(context).contains(target))
        .max(Comparator.comparingInt(target -> nominationScore(context, target)))
        .orElse(null);
if (chosen == null) {
    return new AgentDecision(new AgentIntent.Noop("no safe nomination target"),
            "no nomination target", Map.of("eligibleTargetGameSeatIds", targets));
}
int score = nominationScore(context, chosen);
int threshold = "EVIL".equals(context.view().myAlignment()) ? 55 : 65;
if (score < threshold) {
    return new AgentDecision(new AgentIntent.Noop("nomination score below threshold"),
            "nomination score below threshold", Map.of("score", score, "threshold", threshold));
}
return new AgentDecision(new AgentIntent.Nominate(chosen, "score " + score),
        "selected nomination target", Map.of("targetGameSeatId", chosen, "score", score));
```

`AgentVotePlanner#plan` must:

```java
AgentLegalIntentView yes = voteIntent(context, true);
AgentLegalIntentView no = voteIntent(context, false);
if (yes == null && no == null) {
    return new AgentDecision(new AgentIntent.Noop("vote is not legal"), "vote unavailable", Map.of());
}
AgentLegalIntentView reference = yes == null ? no : yes;
Long nominee = longValue(reference.payload().get("nomineeGameSeatId"));
int score = nominee == null ? 50 : memoryScore(context, nominee, "SUSPICION_SCORE");
boolean dead = "DEAD".equals(context.view().lifeStatus());
boolean voteYes = score >= (dead ? 80 : 65);
if ("EVIL".equals(context.view().myAlignment())) {
    voteYes = score >= 55 && !evilDemonSeatIds(context).contains(nominee);
}
if (voteYes && yes != null) {
    return new AgentDecision(new AgentIntent.Vote(yes.nominationId(), true, "score " + score),
            "selected yes vote", Map.of("nomineeGameSeatId", nominee, "score", score));
}
return new AgentDecision(new AgentIntent.Vote(reference.nominationId(), false, "score " + score),
        "selected no vote", Map.of("nomineeGameSeatId", nominee, "score", score));
```

`AgentNightChoicePlanner#plan` must select from `legalTargetGameSeatIds`:

```java
AgentLegalIntentView intent = firstIntent(context, "NIGHT_CHOICE");
if (intent == null) {
    return new AgentDecision(new AgentIntent.Noop("night choice is not legal"),
            "night choice unavailable", Map.of());
}
List<Long> legalTargets = longList(intent.payload().get("legalTargetGameSeatIds"));
String roleCode = stringValue(intent.payload().getOrDefault("roleCode", context.view().myRoleCode()));
String taskType = stringValue(intent.payload().get("taskType"));
if ("RECEIVE_INFO".equals(taskType) && legalTargets.isEmpty()) {
    return new AgentDecision(new AgentIntent.NightChoice(intent.taskId(), List.of(), Map.of("taskId", intent.taskId())),
            "receive info task has no target", Map.of("roleCode", roleCode));
}
Long target = chooseNightTarget(context, roleCode, legalTargets);
if (target == null) {
    return new AgentDecision(new AgentIntent.Noop("no legal night target"),
            "no legal night target", Map.of("roleCode", roleCode));
}
return new AgentDecision(new AgentIntent.NightChoice(intent.taskId(), List.of(target), Map.of("taskId", intent.taskId())),
        "selected night target", Map.of("roleCode", roleCode, "targetGameSeatId", target));
```

- [ ] **Step 5: Create in-memory plus repository-backed bluff planner**

`TroubleBrewingBluffPlanner` constructor must accept nullable repository and object mapper so pure unit tests can use memory-only plans.

```java
@Component
public class TroubleBrewingBluffPlanner {

    private final ClocktowerAgentMemoryRepository memoryRepository;
    private final ObjectMapper objectMapper;

    public TroubleBrewingBluffPlanner(ClocktowerAgentMemoryRepository memoryRepository, ObjectMapper objectMapper) {
        this.memoryRepository = memoryRepository;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> loadOrCreatePlan(AgentDecisionContext context) {
        return context.view().memories().stream()
                .filter(memory -> "BLUFF_PLAN".equals(memory.memoryType()))
                .map(AgentMemoryView::content)
                .findFirst()
                .orElseGet(() -> createPlan(context));
    }

    private Map<String, Object> createPlan(AgentDecisionContext context) {
        List<String> candidates = List.of("CHEF", "SOLDIER", "RAVENKEEPER", "MAYOR");
        String claimRoleCode = candidates.stream()
                .filter(candidate -> !candidate.equals(context.view().myRoleCode()))
                .findFirst()
                .orElse("CHEF");
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("claimRoleCode", claimRoleCode);
        plan.put("backupClaimRoleCode", candidates.stream()
                .filter(candidate -> !candidate.equals(claimRoleCode))
                .findFirst()
                .orElse("SOLDIER"));
        plan.put("fakeInfo", "CHEF".equals(claimRoleCode) ? Map.of("chefNumber", 1) : Map.of());
        plan.put("protectSeats", evilTeamSeatIds(context));
        plan.put("pushTargets", context.view().publicSeats().stream()
                .map(AgentPublicSeatView::gameSeatId)
                .filter(seatId -> !seatId.equals(context.view().myGameSeatId()))
                .filter(seatId -> !evilTeamSeatIds(context).contains(seatId))
                .limit(2)
                .toList());
        persistPlanIfRepositoryAvailable(context, plan);
        return plan;
    }
}
```

The persistence method must check existing `BLUFF_PLAN` memories through `findByGameIdAndAgentInstanceIdAndMemoryTypeInAndDeletedFalseOrderByCreatedAtAscIdAsc` before saving, then save one `ClocktowerAgentMemoryPo` with `memoryType=BLUFF_PLAN`, `visibility=SELF`, `contentJson` from `objectMapper.writeValueAsString(plan)`, `confidence=80`, and current `dayNo`/`nightNo`.

- [ ] **Step 6: Fix and run policy tests**

Remove the unused visible-event helper from the test file and run:

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerAgentHeuristicPolicyTests test
```

Expected: PASS.

- [ ] **Step 7: Commit strategy green**

```bash
git add be/src/main/java/top/egon/mario/clocktower/agent/strategy \
        be/src/test/java/top/egon/mario/clocktower/agent/strategy/ClocktowerAgentHeuristicPolicyTests.java
git commit -m "feat(clocktower): add heuristic agent policy"
```

### Task 3: Private View Legal Intents And Evil Context

**Files:**
- Modify: `be/src/test/java/top/egon/mario/clocktower/agent/view/ClocktowerAgentPrivateViewServiceTests.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/agent/view/service/impl/ClocktowerAgentPrivateViewServiceImpl.java`

- [ ] **Step 1: Write failing private-view tests**

Add these assertions to `ClocktowerAgentPrivateViewServiceTests`:

```java
@Test
void legalIntentsExposeSpeechOnlyForCurrentMicHolder() {
    TestGame game = createGame("EMPATH");
    micService.startDayMicSession(game.game().getId(), owner());

    AgentPrivateView view = privateViewService.build(game.game().getId(), game.instance().getId());

    assertThat(view.legalIntents()).extracting("intentType")
            .doesNotContain("PUBLIC_SPEECH", "PASS");
}

@Test
void evilAgentViewIncludesEvilTeamButGoodAgentDoesNot() {
    TestGame evilGame = createGame("SPY");
    AgentPrivateView evilView = privateViewService.build(evilGame.game().getId(), evilGame.instance().getId());

    assertThat(evilView.roleSpecificContext()).containsKey("evilTeam");
    assertThat(evilView.roleSpecificContext()).containsKey("demonGameSeatId");

    TestGame goodGame = createGoodGame("EMPATH");
    AgentPrivateView goodView = privateViewService.build(goodGame.game().getId(), goodGame.instance().getId());

    assertThat(goodView.roleSpecificContext()).doesNotContainKeys("evilTeam", "demonGameSeatId");
}

@Test
void legalIntentsExposeNominationAndNightTargets() {
    TestGame game = createGame("MONK");
    ClocktowerGamePo po = game.game();
    po.setPhase("NOMINATION");
    gameRepository.saveAndFlush(po);

    AgentPrivateView view = privateViewService.build(po.getId(), game.instance().getId());

    assertThat(view.legalIntents()).anySatisfy(intent -> {
        assertThat(intent.intentType()).isEqualTo("NOMINATE");
        assertThat(intent.payload()).containsKey("eligibleTargetGameSeatIds");
    });
}
```

Add this field to the test class:

```java
@Autowired
private ClocktowerPublicMicService micService;
```

Add these helper methods to the test class:

```java
private RbacPrincipal owner() {
    return principal(1L, "mario");
}

private RbacPrincipal principal(Long userId, String username) {
    return new RbacPrincipal(userId, username, Set.of(), Set.of(), "test");
}

private TestGame createGoodGame(String agentRoleCode) {
    TestGame game = createGame(agentRoleCode);
    ClocktowerGameSeatPo agentSeat = game.agentSeat();
    agentSeat.setAlignment("GOOD");
    agentSeat.setRoleType("TOWNSFOLK");
    gameSeatRepository.saveAndFlush(agentSeat);
    return game;
}
```

- [ ] **Step 2: Run red private-view tests**

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerAgentPrivateViewServiceTests test
```

Expected: FAIL because legal intents still include speech for every day phase and no evil-team context exists.

- [ ] **Step 3: Implement mic-aware and state-aware legal intents**

Modify `ClocktowerAgentPrivateViewServiceImpl`:

```java
private List<AgentLegalIntentView> legalIntents(ClocktowerGamePo game, ClocktowerGameSeatPo mySeat) {
    List<AgentLegalIntentView> intents = new ArrayList<>();
    appendMicIntents(game, mySeat, intents);
    appendNominationIntents(game, mySeat, intents);
    appendVoteIntents(game, mySeat, intents);
    appendNightChoiceIntents(game, mySeat, intents);
    return intents;
}
```

`appendMicIntents` must add `PUBLIC_SPEECH` and `PASS` only when `publicMicService.canSpeak(game.getId(), mySeat.getId())` is true. It must add `GRAB_MIC` only when the locked/current session for the game day has `status=GRAB_MIC`, `currentHolderGameSeatId=null`, `grabEndsAt` is after `Instant.now()`, and my seat is active.

`appendNominationIntents` must add one `NOMINATE` intent with `eligibleTargetGameSeatIds` only when the game phase is `DAY` or `NOMINATION`, the agent is alive, no open nomination exists, no mic session is currently open for an active holder, and `nominationRepository.existsByGameIdAndDayNoAndNominatorGameSeatIdAndDeletedFalse(game.getId(), game.getDayNo(), mySeat.getId())` is false. Target seats must be active, alive, not self, and not already nominated today.

`appendVoteIntents` must use the current open nomination and skip adding vote intents when `voteRepository.existsByNominationIdAndVoterGameSeatIdAndDeletedFalse(nomination.getId(), mySeat.getId())` is true. Add true and false vote intents when voting is still legal.

`appendNightChoiceIntents` must add `legalTargetGameSeatIds` by calling the existing role skill registry with a `NightTaskContext` built from game, task, actor seat, all seats, current markers, and current night events. For `RECEIVE_INFO` tasks with no selectable targets, the list is empty and the intent remains legal.

- [ ] **Step 4: Add role-specific context**

Replace the final `Map.of()` in the `AgentPrivateView` constructor with:

```java
roleSpecificContext(seats, mySeat)
```

Add:

```java
private Map<String, Object> roleSpecificContext(List<ClocktowerGameSeatPo> seats, ClocktowerGameSeatPo mySeat) {
    if (!"EVIL".equals(mySeat.getAlignment())) {
        return Map.of();
    }
    List<Map<String, Object>> evilTeam = seats.stream()
            .filter(seat -> "EVIL".equals(seat.getAlignment()))
            .map(seat -> {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("gameSeatId", seat.getId());
                entry.put("seatNo", seat.getSeatNo());
                entry.put("displayName", seat.getDisplayName());
                entry.put("roleCode", seat.getRoleCode());
                entry.put("roleType", seat.getRoleType());
                entry.put("isDemon", "DEMON".equals(seat.getRoleType()));
                entry.put("isMinion", "MINION".equals(seat.getRoleType()));
                return entry;
            })
            .toList();
    Map<String, Object> context = new LinkedHashMap<>();
    context.put("evilTeam", evilTeam);
    evilTeam.stream()
            .filter(entry -> Boolean.TRUE.equals(entry.get("isDemon")))
            .map(entry -> entry.get("gameSeatId"))
            .findFirst()
            .ifPresent(demonSeatId -> context.put("demonGameSeatId", demonSeatId));
    return context;
}
```

- [ ] **Step 5: Run private-view tests**

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerAgentPrivateViewServiceTests test
```

Expected: PASS.

- [ ] **Step 6: Commit private-view changes**

```bash
git add be/src/main/java/top/egon/mario/clocktower/agent/view/service/impl/ClocktowerAgentPrivateViewServiceImpl.java \
        be/src/test/java/top/egon/mario/clocktower/agent/view/ClocktowerAgentPrivateViewServiceTests.java
git commit -m "feat(clocktower): expose agent legal intents"
```

### Task 4: Agent Grab Mic And Public Event Scheduling

**Files:**
- Modify: `be/src/test/java/top/egon/mario/clocktower/game/mic/ClocktowerPublicMicServiceTests.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/game/mic/service/ClocktowerPublicMicService.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/game/mic/service/impl/ClocktowerPublicMicServiceImpl.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/agent/runtime/ClocktowerAgentTaskScheduler.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/agent/runtime/ClocktowerAgentTriggerListener.java`
- Modify: `be/src/test/java/top/egon/mario/clocktower/agent/runtime/ClocktowerAgentTaskRuntimeTests.java`

- [ ] **Step 1: Write failing Agent grab-mic test**

Add to `ClocktowerPublicMicServiceTests`:

```java
@Test
void grabMicAsActorAllowsAgentSeatDuringGrabWindow() {
    StartedGame game = startDayGameWithAgents();
    finishRoundRobin(game);
    ClocktowerGameSeatPo agentSeat = game.seats().stream()
            .filter(seat -> "AGENT".equals(seat.getActorType()))
            .findFirst()
            .orElseThrow();

    ClocktowerMicSessionView view = micService.grabMicAsActor(game.gameId(), agentSeat.getId());

    ClocktowerMicTurnView active = activeTurn(view);
    assertThat(active.actorType()).isEqualTo("AGENT");
    assertThat(active.gameSeatId()).isEqualTo(agentSeat.getId());
    assertThat(active.acquisitionType()).isEqualTo("GRAB");
    assertThat(micService.canSpeak(game.gameId(), agentSeat.getId())).isTrue();
}

@Test
void grabMicAsActorRejectsOccupiedMic() {
    StartedGame game = startDayGameWithAgents();
    finishRoundRobin(game);
    ClocktowerGameSeatPo agentSeat = game.seats().stream()
            .filter(seat -> "AGENT".equals(seat.getActorType()))
            .findFirst()
            .orElseThrow();
    micService.grabMic(game.gameId(), principal(11L, "player1"));

    assertThatThrownBy(() -> micService.grabMicAsActor(game.gameId(), agentSeat.getId()))
            .isInstanceOf(ClocktowerException.class)
            .hasMessageContaining("CLOCKTOWER_MIC_OCCUPIED");
}
```

- [ ] **Step 2: Run red mic test**

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerPublicMicServiceTests test
```

Expected: FAIL because `grabMicAsActor` is not on the service interface.

- [ ] **Step 3: Implement `grabMicAsActor` by sharing grab logic**

Add to `ClocktowerPublicMicService`:

```java
ClocktowerMicSessionView grabMicAsActor(Long gameId, Long actorGameSeatId);
```

In `ClocktowerPublicMicServiceImpl`, refactor the body of human `grabMic` into:

```java
private ClocktowerMicSessionView grabMicForSeat(ClocktowerGamePo game,
                                                ClocktowerGamePublicMicSessionPo session,
                                                ClocktowerGameSeatPo seat,
                                                Instant now) {
    requireSessionOpen(session);
    if (!SESSION_GRAB_MIC.equals(session.getStatus())) {
        throw new ClocktowerException("CLOCKTOWER_MIC_GRAB_NOT_OPEN");
    }
    if (session.getGrabEndsAt() == null || !session.getGrabEndsAt().isAfter(now)) {
        closeSessionInternal(game, session, now);
        return toView(session);
    }
    if (session.getCurrentHolderGameSeatId() != null) {
        throw new ClocktowerException("CLOCKTOWER_MIC_OCCUPIED");
    }
    if (!SEAT_STATUS_ACTIVE.equals(seat.getStatus())) {
        throw new ClocktowerException("CLOCKTOWER_GAME_SEAT_INACTIVE");
    }
    ClocktowerGamePublicMicTurnPo turn = new ClocktowerGamePublicMicTurnPo();
    turn.setSessionId(session.getId());
    turn.setGameId(session.getGameId());
    turn.setDayNo(session.getDayNo());
    turn.setGameSeatId(seat.getId());
    turn.setTurnOrder(nextTurnOrder(session.getId()));
    turn.setStage(SESSION_GRAB_MIC);
    turn.setAcquisitionType(ACQUISITION_GRAB);
    turn.setStatus(TURN_ACTIVE);
    turn.setStartedAt(now);
    turn.setExpiresAt(earlier(now.plus(properties.grabMicHoldDuration()), session.getGrabEndsAt()));
    turn = turnRepository.saveAndFlush(turn);
    session.setCurrentHolderGameSeatId(turn.getGameSeatId());
    session.setCurrentTurnId(turn.getId());
    appendGameEvent(game, EVENT_MIC_TURN_STARTED, now, turnPayload(session, turn));
    return toView(session);
}
```

Implement:

```java
@Override
@Transactional
public ClocktowerMicSessionView grabMicAsActor(Long gameId, Long actorGameSeatId) {
    ClocktowerGamePo game = lockedGame(gameId);
    ClocktowerGamePublicMicSessionPo session = lockedCurrentSession(game);
    Instant now = Instant.now();
    refreshExpiredState(game, session, now);
    ClocktowerGameSeatPo seat = gameSeatRepository.findByIdAndGameIdAndDeletedFalse(actorGameSeatId, game.getId())
            .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_GAME_SEAT_NOT_FOUND"));
    if (!ClocktowerActorType.HUMAN.equals(seat.getActorType()) && !ClocktowerActorType.AGENT.equals(seat.getActorType())) {
        throw new ClocktowerException("CLOCKTOWER_GAME_SEAT_ACTOR_INVALID");
    }
    return grabMicForSeat(game, session, seat, now);
}
```

Make existing `grabMic(Long, RbacPrincipal)` resolve `requireHumanPlayerSeat(game, principal)` and then call `grabMicForSeat`.

- [ ] **Step 4: Add scheduling tests**

Add to `ClocktowerAgentTaskRuntimeTests`:

```java
@Test
void micGrabOpenedSchedulesActiveAgents() {
    StartedGame game = startDayGameWithAgents(4);
    micService.startDayMicSession(game.gameId(), owner());
    while (micService.getMicSession(game.gameId(), owner()).status().equals("ROUND_ROBIN")) {
        Long turnId = micService.getMicSession(game.gameId(), owner()).currentTurnId();
        micService.finishCurrentTurn(game.gameId(), turnId, owner());
    }

    List<ClocktowerAgentTaskPo> tasks = agentTaskRepository
            .findByGameIdAndTriggerTypeAndDeletedFalseOrderByIdAsc(
                    game.gameId(), ClocktowerAgentTriggerType.MIC_GRAB_OPENED);

    assertThat(tasks).hasSize(4);
    assertThat(tasks).allSatisfy(task -> assertThat(task.getTriggerKey()).contains("micGrab:"));
}

@Test
void publicSpeechSchedulesReactionForOtherAgentsOnly() {
    StartedGame game = startDayGameWithAgents(4);
    ClocktowerGameSeatPo firstAgent = game.agentSeats().getFirst();

    eventAppender.append(gameRepository.findByIdAndDeletedFalse(game.gameId()).orElseThrow(),
            "PUBLIC_SPEECH", firstAgent.getId(), null,
            "PUBLIC", List.of(), Map.of("content", "我先报信息。"), Instant.now());

    List<ClocktowerAgentTaskPo> tasks = agentTaskRepository
            .findByGameIdAndTriggerTypeAndDeletedFalseOrderByIdAsc(
                    game.gameId(), ClocktowerAgentTriggerType.PUBLIC_EVENT_APPENDED);

    assertThat(tasks).hasSize(3);
    assertThat(tasks).noneSatisfy(task -> assertThat(task.getGameSeatId()).isEqualTo(firstAgent.getId()));
}
```

- [ ] **Step 5: Implement scheduling**

Add to `ClocktowerAgentTaskScheduler`:

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public List<ClocktowerAgentTaskPo> scheduleForActiveAgentSeatsExcept(Long gameId, Long excludedGameSeatId,
                                                                     String triggerType, String triggerKey,
                                                                     Map<String, Object> metadata) {
    return gameSeatRepository.findByGameIdAndDeletedFalseOrderBySeatNoAsc(gameId)
            .stream()
            .filter(this::activeAgentSeat)
            .filter(seat -> !Objects.equals(seat.getId(), excludedGameSeatId))
            .map(seat -> scheduleForAgent(gameId, seat.getAgentInstanceId(), seat.getId(),
                    triggerType, triggerKey, metadata))
            .toList();
}
```

In `ClocktowerAgentTriggerListener`, add switch cases:

```java
case "MIC_GRAB_OPENED" -> scheduleMicGrab(signal);
case "PUBLIC_SPEECH", "NOMINATION_OPENED", "VOTE_CAST", "PLAYER_DIED" -> schedulePublicReaction(signal);
```

Add methods:

```java
private void scheduleMicGrab(ClocktowerGameEventAppendedSignal signal) {
    Long sessionId = longValue(signal.payload().get("sessionId"));
    taskScheduler.scheduleForActiveAgentSeats(signal.gameId(), ClocktowerAgentTriggerType.MIC_GRAB_OPENED,
            "micGrab:%s".formatted(sessionId == null ? signal.eventId() : sessionId), metadata(signal));
}

private void schedulePublicReaction(ClocktowerGameEventAppendedSignal signal) {
    taskScheduler.scheduleForActiveAgentSeatsExcept(signal.gameId(), signal.actorGameSeatId(),
            ClocktowerAgentTriggerType.PUBLIC_EVENT_APPENDED,
            "publicEvent:%s:react".formatted(signal.eventId()), metadata(signal));
}
```

- [ ] **Step 6: Run mic and scheduler tests**

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerPublicMicServiceTests,ClocktowerAgentTaskRuntimeTests test
```

Expected: PASS for the new mic and scheduling coverage. Existing runtime placeholder assertions may fail until task 5 updates them.

- [ ] **Step 7: Commit mic and scheduling changes**

```bash
git add be/src/main/java/top/egon/mario/clocktower/game/mic/service/ClocktowerPublicMicService.java \
        be/src/main/java/top/egon/mario/clocktower/game/mic/service/impl/ClocktowerPublicMicServiceImpl.java \
        be/src/main/java/top/egon/mario/clocktower/agent/runtime/ClocktowerAgentTaskScheduler.java \
        be/src/main/java/top/egon/mario/clocktower/agent/runtime/ClocktowerAgentTriggerListener.java \
        be/src/test/java/top/egon/mario/clocktower/game/mic/ClocktowerPublicMicServiceTests.java \
        be/src/test/java/top/egon/mario/clocktower/agent/runtime/ClocktowerAgentTaskRuntimeTests.java
git commit -m "feat(clocktower): allow agents to grab public mic"
```

### Task 5: Runtime Intent Execution And Decision Summary

**Files:**
- Create: `be/src/main/java/top/egon/mario/clocktower/agent/strategy/AgentIntentExecutor.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/agent/strategy/AgentDecisionSummary.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/agent/runtime/ClocktowerAgentRuntime.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/agent/repository/ClocktowerAgentProfileRepository.java`
- Modify: `be/src/test/java/top/egon/mario/clocktower/agent/runtime/ClocktowerAgentTaskRuntimeTests.java`

- [ ] **Step 1: Write failing runtime assertions**

Rename `workerProcessesMicTurnPassAndMarksTaskDone` to `workerProcessesMicTurnSpeechAndMarksTaskDone`, then replace result assertions with:

```java
assertThat(reloaded.getStatus()).isEqualTo(ClocktowerAgentTaskStatus.DONE);
assertThat(reloaded.getResultJson()).contains("HEURISTIC_V0");
assertThat(reloaded.getResultJson()).contains("PUBLIC_SPEECH");
assertThat(reloaded.getResultJson()).contains("FINISH_SPEECH");
assertThat(refreshedInstance.getMetadataJson()).contains("lastSeenEventSeq");
assertThat(micService.canSpeak(game.gameId(), firstAgentSeat.getId())).isFalse();
```

Add:

```java
@Test
void runtimeWritesDecisionSummaryForNoopTrigger() {
    StartedGame game = startDayGameWithAgents(4);
    ClocktowerGameSeatPo firstAgentSeat = game.agentSeats().getFirst();
    ClocktowerAgentInstancePo instance = agentInstanceRepository
            .findByGameSeatIdAndDeletedFalse(firstAgentSeat.getId())
            .orElseThrow();
    ClocktowerAgentTaskPo task = taskScheduler.scheduleForAgent(game.gameId(), instance.getId(),
            firstAgentSeat.getId(), ClocktowerAgentTriggerType.PUBLIC_EVENT_APPENDED,
            "publicEvent:%s:manual".formatted(game.gameId()), Map.of("eventType", "PUBLIC_SPEECH"));

    taskWorker.processBatch("test-worker", 20);

    ClocktowerAgentTaskPo reloaded = agentTaskRepository.findByIdAndDeletedFalse(task.getId()).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(ClocktowerAgentTaskStatus.DONE);
    assertThat(reloaded.getResultJson()).contains("HEURISTIC_V0");
    assertThat(reloaded.getResultJson()).contains("NOOP");
    assertThat(reloaded.getResultJson()).contains("legalIntents");
}
```

- [ ] **Step 2: Run red runtime tests**

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerAgentTaskRuntimeTests test
```

Expected: FAIL because runtime still performs placeholder actions and no decision summary exists.

- [ ] **Step 3: Implement profile lookup**

Add to `ClocktowerAgentProfileRepository`:

```java
Optional<ClocktowerAgentProfilePo> findByIdAndDeletedFalse(Long id);
```

- [ ] **Step 4: Implement `AgentIntentExecutor`**

The executor must translate intents to existing services:

```java
@Service
@RequiredArgsConstructor
public class AgentIntentExecutor {

    private final ClocktowerAgentGameActionService agentGameActionService;
    private final ClocktowerPublicMicService publicMicService;

    public List<ClocktowerGameActionResponse> execute(ClocktowerAgentTaskPo task, AgentIntent intent) {
        if (intent instanceof AgentIntent.PublicSpeech speech) {
            ClocktowerGameActionResponse spoken = submit(task, "PUBLIC_SPEECH", List.of(), null, null,
                    speech.content(), Map.of("policy", HeuristicAgentPolicy.POLICY_NAME));
            if (!spoken.accepted()) {
                return List.of(spoken);
            }
            ClocktowerGameActionResponse finished = submit(task, "FINISH_SPEECH", List.of(), null, null,
                    null, Map.of("policy", HeuristicAgentPolicy.POLICY_NAME));
            return List.of(spoken, finished);
        }
        if (intent instanceof AgentIntent.GrabMic grabMic) {
            publicMicService.grabMicAsActor(task.getGameId(), task.getGameSeatId());
            ClocktowerGameActionResponse spoken = submit(task, "PUBLIC_SPEECH", List.of(), null, null,
                    grabMic.reason(), Map.of("policy", HeuristicAgentPolicy.POLICY_NAME, "grabMic", true));
            if (!spoken.accepted()) {
                return List.of(spoken);
            }
            ClocktowerGameActionResponse finished = submit(task, "FINISH_SPEECH", List.of(), null, null,
                    null, Map.of("policy", HeuristicAgentPolicy.POLICY_NAME));
            return List.of(spoken, finished);
        }
        if (intent instanceof AgentIntent.Pass pass) {
            return List.of(submit(task, "PASS", List.of(), null, null, null,
                    Map.of("passType", "MIC_TURN", "reason", pass.reason())));
        }
        if (intent instanceof AgentIntent.Nominate nominate) {
            return List.of(submit(task, "NOMINATE", List.of(nominate.targetGameSeatId()), null, null,
                    nominate.reason(), Map.of("reason", nominate.reason())));
        }
        if (intent instanceof AgentIntent.Vote vote) {
            return List.of(submit(task, "VOTE", List.of(), vote.nominationId(), vote.vote(),
                    null, Map.of("reason", vote.reason())));
        }
        if (intent instanceof AgentIntent.NightChoice choice) {
            return List.of(submit(task, "NIGHT_CHOICE", choice.targetGameSeatIds(), null, null,
                    null, choice.payload()));
        }
        return List.of();
    }

    private ClocktowerGameActionResponse submit(ClocktowerAgentTaskPo task, String actionType,
                                                List<Long> targetGameSeatIds, Long nominationId,
                                                Boolean vote, String content, Map<String, Object> payload) {
        return agentGameActionService.submitAgentAction(task.getGameId(), task.getAgentInstanceId(),
                new ClocktowerGameActionRequest(task.getGameSeatId(), actionType, targetGameSeatIds,
                        nominationId, vote, content, payload));
    }
}
```

- [ ] **Step 5: Implement `AgentDecisionSummary`**

```java
public final class AgentDecisionSummary {

    private AgentDecisionSummary() {
    }

    public static Map<String, Object> build(ClocktowerAgentTaskPo task,
                                            AgentDecision decision,
                                            List<AgentLegalIntentView> legalIntents,
                                            List<ClocktowerGameActionResponse> responses,
                                            ClocktowerAgentMemoryRefreshResult memoryRefresh,
                                            boolean illegalIntentDowngraded) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("policy", HeuristicAgentPolicy.POLICY_NAME);
        result.put("triggerType", task.getTriggerType());
        result.put("legalIntents", legalIntents.stream().map(AgentLegalIntentView::intentType).distinct().toList());
        result.put("selectedIntent", intentType(decision.intent()));
        result.put("reasoningSummary", decision.reasoningSummary());
        result.put("diagnostics", decision.diagnostics());
        result.put("illegalIntentDowngraded", illegalIntentDowngraded);
        result.put("accepted", responses.stream().allMatch(ClocktowerGameActionResponse::accepted));
        result.put("actions", responses.stream().map(AgentDecisionSummary::actionResult).toList());
        result.put("memoryLastSeenEventSeq", memoryRefresh.lastSeenEventSeq());
        result.put("memoryInsertedCount", memoryRefresh.insertedCount());
        return result;
    }
}
```

`intentType` must return `PUBLIC_SPEECH`, `GRAB_MIC`, `FINISH_SPEECH`, `NOMINATE`, `VOTE`, `NIGHT_CHOICE`, `PASS`, or `NOOP`.

- [ ] **Step 6: Replace runtime placeholder switch**

Modify `ClocktowerAgentRuntime` constructor dependencies to include:

```java
private final ClocktowerAgentProfileRepository agentProfileRepository;
private final ClocktowerAgentPrivateViewService privateViewService;
private final ClocktowerAgentPolicy agentPolicy;
private final AgentIntentExecutor intentExecutor;
```

Then replace the switch in `handle` with:

```java
ClocktowerAgentMemoryRefreshResult memoryRefresh = memoryService.refreshForRuntimeTask(task);
AgentPrivateView view = privateViewService.build(task.getGameId(), task.getAgentInstanceId());
ClocktowerAgentProfilePo profile = agentProfileRepository.findByIdAndDeletedFalse(instance.getProfileId())
        .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_AGENT_PROFILE_INVALID"));
AgentDecisionContext context = new AgentDecisionContext(view, profile, view.legalIntents(),
        task.getTriggerType(), metadata(task), Map.of());
AgentDecision decision = agentPolicy.decide(context);
boolean illegalIntentDowngraded = !isLegal(decision.intent(), view.legalIntents());
if (illegalIntentDowngraded) {
    decision = new AgentDecision(new AgentIntent.Noop("policy selected illegal intent"),
            "policy selected illegal intent", Map.of("originalIntent", intentType(decision.intent())));
}
List<ClocktowerGameActionResponse> responses = intentExecutor.execute(task, decision.intent());
return done(AgentDecisionSummary.build(task, decision, view.legalIntents(), responses, memoryRefresh,
        illegalIntentDowngraded));
```

`isLegal` must allow `Noop`, require matching `PUBLIC_SPEECH`, `GRAB_MIC`, `PASS`, and `NOMINATE` intent type, require matching nomination id and vote value for `Vote`, and require matching task id for `NightChoice`.

- [ ] **Step 7: Run runtime tests**

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerAgentTaskRuntimeTests test
```

Expected: PASS.

- [ ] **Step 8: Commit runtime integration**

```bash
git add be/src/main/java/top/egon/mario/clocktower/agent/runtime/ClocktowerAgentRuntime.java \
        be/src/main/java/top/egon/mario/clocktower/agent/repository/ClocktowerAgentProfileRepository.java \
        be/src/main/java/top/egon/mario/clocktower/agent/strategy/AgentIntentExecutor.java \
        be/src/main/java/top/egon/mario/clocktower/agent/strategy/AgentDecisionSummary.java \
        be/src/test/java/top/egon/mario/clocktower/agent/runtime/ClocktowerAgentTaskRuntimeTests.java
git commit -m "feat(clocktower): execute heuristic agent decisions"
```

### Task 6: Runtime Coverage For Nomination Vote Night And Bluff Persistence

**Files:**
- Modify: `be/src/test/java/top/egon/mario/clocktower/agent/runtime/ClocktowerAgentTaskRuntimeTests.java`
- Modify: `be/src/test/java/top/egon/mario/clocktower/agent/strategy/ClocktowerAgentHeuristicPolicyTests.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/agent/strategy/HeuristicAgentPolicy.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/agent/strategy/AgentSpeechPlanner.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/agent/strategy/AgentNominationPlanner.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/agent/strategy/AgentVotePlanner.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/agent/strategy/AgentNightChoicePlanner.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/agent/strategy/troublebrewing/TroubleBrewingBluffPlanner.java`

- [ ] **Step 1: Add runtime scenario tests**

Add focused integration tests using existing helpers:

```java
@Test
void runtimeNominationUsesLegalTargetAndCompletesTask() {
    StartedGame game = startDayGameWithAgents(4);
    ClocktowerGamePo po = gameRepository.findByIdAndDeletedFalse(game.gameId()).orElseThrow();
    po.setPhase("NOMINATION");
    gameRepository.saveAndFlush(po);
    ClocktowerGameSeatPo firstAgent = game.agentSeats().getFirst();
    ClocktowerAgentInstancePo instance = agentInstanceRepository.findByGameSeatIdAndDeletedFalse(firstAgent.getId())
            .orElseThrow();
    ClocktowerAgentTaskPo task = taskScheduler.scheduleForAgent(game.gameId(), instance.getId(),
            firstAgent.getId(), ClocktowerAgentTriggerType.PHASE_CHANGED,
            "phase:%s:nomination-runtime".formatted(game.gameId()), Map.of("phase", "NOMINATION"));

    taskWorker.processBatch("test-worker", 20);

    ClocktowerAgentTaskPo reloaded = agentTaskRepository.findByIdAndDeletedFalse(task.getId()).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(ClocktowerAgentTaskStatus.DONE);
    assertThat(reloaded.getResultJson()).contains("NOMINATE");
}

@Test
void runtimeNightChoiceUsesHeuristicInsteadOfAutoChooseShortcut() {
    StartedGame game = startFirstNightGameWithAgents(4);
    ClocktowerAgentTaskPo task = agentTaskRepository
            .findByGameIdAndTriggerTypeAndDeletedFalseOrderByIdAsc(
                    game.gameId(), ClocktowerAgentTriggerType.NIGHT_TASK_OPENED)
            .getFirst();

    taskWorker.processBatch("test-worker", 20);

    ClocktowerAgentTaskPo reloaded = agentTaskRepository.findByIdAndDeletedFalse(task.getId()).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(ClocktowerAgentTaskStatus.DONE);
    assertThat(reloaded.getResultJson()).contains("NIGHT_CHOICE");
    assertThat(reloaded.getResultJson()).contains("HEURISTIC_V0");
}
```

- [ ] **Step 2: Add bluff persistence test**

In `ClocktowerAgentHeuristicPolicyTests`, add a pure policy test that starts with no `BLUFF_PLAN` memory and asserts the returned speech includes a deterministic claim. In an integration test, load the same Agent twice and assert `clocktower_agent_memory` has one `BLUFF_PLAN` row by querying `ClocktowerAgentMemoryRepository.findByGameIdAndAgentInstanceIdAndMemoryTypeInAndDeletedFalseOrderByCreatedAtAscIdAsc(gameId, instanceId, List.of("BLUFF_PLAN"))`.

- [ ] **Step 3: Run new coverage**

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerAgentHeuristicPolicyTests,ClocktowerAgentTaskRuntimeTests test
```

Expected: PASS.

- [ ] **Step 4: Commit coverage hardening**

```bash
git add be/src/test/java/top/egon/mario/clocktower/agent/runtime/ClocktowerAgentTaskRuntimeTests.java \
        be/src/test/java/top/egon/mario/clocktower/agent/strategy/ClocktowerAgentHeuristicPolicyTests.java \
        be/src/main/java/top/egon/mario/clocktower/agent/strategy \
        be/src/main/java/top/egon/mario/clocktower/agent/strategy/troublebrewing
git commit -m "test(clocktower): cover heuristic agent runtime scenarios"
```

### Task 7: Final Verification

**Files:**
- Inspect: changed files from tasks 1 through 6.

- [ ] **Step 1: Run targeted task-12 verification**

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerAgentHeuristicPolicyTests,ClocktowerAgentTaskRuntimeTests,ClocktowerAgentPrivateViewServiceTests,ClocktowerPublicMicServiceTests test
```

Expected: PASS.

- [ ] **Step 2: Run broader Agent slice**

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest='top.egon.mario.clocktower.agent.**.*Tests' test
```

Expected: PASS.

- [ ] **Step 3: Inspect diff scope**

```bash
git diff --stat HEAD~6..HEAD
git status --short
```

Expected: only strategy, runtime, private-view, public-mic, scheduler/listener, and targeted test files changed. `git status --short` should be clean after the final task commit.

- [ ] **Step 4: Final task commit if task 7 changed files**

Only if verification required a code or test adjustment, commit it:

```bash
git add be/src/main/java/top/egon/mario/clocktower be/src/test/java/top/egon/mario/clocktower
git commit -m "chore(clocktower): verify heuristic agent player"
```

## Acceptance Mapping

- Agent speaks or passes on mic turn: `AgentSpeechPlanner` plus `AgentIntentExecutor`.
- Agent releases mic: `AgentIntentExecutor` submits `FINISH_SPEECH` after accepted speech or `PASS` for mic-turn pass.
- Agent can grab mic: `ClocktowerPublicMicService#grabMicAsActor`, `MIC_GRAB_OPENED` scheduling, and `AgentSpeechPlanner#planGrabMic`.
- Agent nominates: `ClocktowerAgentPrivateViewServiceImpl` exposes `NOMINATE`; `AgentNominationPlanner` chooses a legal target.
- Agent votes: `ClocktowerAgentPrivateViewServiceImpl` exposes yes/no vote intents; `AgentVotePlanner` chooses using deterministic score thresholds.
- Dead Agent cannot nominate: private view excludes nomination and nomination planner guards dead life status.
- Dead Agent uses dead vote once: vote service remains authoritative and private view excludes already-voted nominations.
- Agent chooses legal night targets: private view includes `legalTargetGameSeatIds`; `AgentNightChoicePlanner` selects from that list only.
- Evil Agent bluffs: `TroubleBrewingBluffPlanner` creates or loads a single `BLUFF_PLAN` memory and speech planner uses it instead of true role.
- Runtime writes decision summary: `AgentDecisionSummary` includes policy, trigger, legal intents, selected intent, diagnostics, action results, and memory refresh counts.

## Validation Commands

Run from `be/`:

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerAgentHeuristicPolicyTests,ClocktowerAgentTaskRuntimeTests,ClocktowerAgentPrivateViewServiceTests,ClocktowerPublicMicServiceTests test
./mvnw -Dmaven.build.cache.enabled=false -Dtest='top.egon.mario.clocktower.agent.**.*Tests' test
```

Do not start the application runtime after implementation; the user will run it manually.
