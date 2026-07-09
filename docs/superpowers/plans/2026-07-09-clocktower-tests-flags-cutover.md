# Clocktower Tests Flags Cutover Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Clocktower task 16 feature flags, rollback gates, cutover metadata, and focused tests while preserving legacy room action and flow behavior.

**Architecture:** Use typed Spring Boot properties as an operational gate at existing service boundaries. Keep legacy room services unchanged except for lightweight legacy markers. Add tests around disabled-flag behavior and new-vs-old data separation instead of re-testing every task 01 through 15 behavior.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring Data JPA, JUnit 5, AssertJ, React 19, TypeScript, Vitest, Bun.

---

## File Structure

Create:

- `be/src/main/java/top/egon/mario/clocktower/config/ClocktowerFeatureProperties.java` - typed flags under `clocktower.agent-player`, `clocktower.game-actions`, `clocktower.new-flow`, and `clocktower.llm-agent`.
- `be/src/main/java/top/egon/mario/clocktower/config/ClocktowerFeatureConfiguration.java` - enables the new properties.
- `be/src/test/java/top/egon/mario/clocktower/config/ClocktowerFeaturePropertiesTests.java` - property defaults and binding tests.
- `be/src/test/java/top/egon/mario/clocktower/room/ClocktowerAgentPlayerFeatureFlagTests.java` - disabled Agent-player room creation behavior.
- `be/src/test/java/top/egon/mario/clocktower/game/action/ClocktowerGameActionFeatureFlagTests.java` - disabled game action behavior for human and Agent paths.
- `be/src/test/java/top/egon/mario/clocktower/game/mic/ClocktowerPublicMicFeatureFlagTests.java` - disabled mic behavior and DAY fallback speech.
- `be/src/test/java/top/egon/mario/clocktower/game/flow/ClocktowerGameFlowFeatureFlagTests.java` - disabled new flow behavior.

Modify:

- `be/src/main/resources/application.yaml` - declare main defaults.
- `be/src/test/resources/application.yaml` - declare test defaults.
- `be/src/main/java/top/egon/mario/clocktower/game/mic/config/ClocktowerPublicMicProperties.java` - add `enabled`.
- `be/src/main/java/top/egon/mario/clocktower/room/service/impl/ClocktowerRoomLobbyServiceImpl.java` - gate Agent seats and add `runtimeModel=GAME_V2` metadata.
- `be/src/main/java/top/egon/mario/clocktower/game/action/service/impl/ClocktowerGameActionExecutorImpl.java` - gate new game actions and allow DAY speech without mic when mic is disabled.
- `be/src/main/java/top/egon/mario/clocktower/game/mic/service/impl/ClocktowerPublicMicServiceImpl.java` - gate public mic entry points.
- `be/src/main/java/top/egon/mario/clocktower/game/flow/service/impl/ClocktowerGameFlowServiceImpl.java` - gate new flow and skip mic blockers when mic is disabled.
- `be/src/main/java/top/egon/mario/clocktower/agent/strategy/ConfigurableClocktowerAgentPolicy.java` - apply global LLM Agent kill switch.
- `be/src/test/java/top/egon/mario/clocktower/room/ClocktowerRoomRefactorServiceTests.java` - assert runtime metadata.
- `be/src/test/java/top/egon/mario/clocktower/game/action/ClocktowerGameActionServiceTests.java` - assert new game nomination/vote path does not write legacy tables.
- `be/src/test/java/top/egon/mario/clocktower/agent/strategy/ClocktowerAgentLlmPolicyTests.java` - assert global LLM kill switch prevents LLM calls.
- `be/src/main/java/top/egon/mario/clocktower/action/service/impl/ClocktowerActionServiceImpl.java` - mark legacy service.
- `be/src/main/java/top/egon/mario/clocktower/flow/service/impl/ClocktowerFlowServiceImpl.java` - mark legacy service.
- `fe/src/modules/clocktower/GameRoomPage.tsx` - extract action request builders for focused tests.
- `fe/src/modules/clocktower/GameRoomPage.test.tsx` - test new game action request shape.
- `fe/src/modules/clocktower/ClocktowerRoomPlayPage.test.tsx` - keep play surface coverage aligned with new action mode.

No Flyway migration is needed. Do not modify existing migration files.

## Design Pattern Decision

Do not add a Strategy, State machine, or routing framework for feature flags. The flags are operational kill switches, so typed properties plus direct boundary checks are simpler and match the existing Spring service style.

Keep the existing task 15 Strategy boundary for Agent policy. Task 16 only adds the global `clocktower.llm-agent.enabled` kill switch to the current configurable policy path.

---

### Task 1: Add Typed Feature Flags

**Files:**

- Create: `be/src/main/java/top/egon/mario/clocktower/config/ClocktowerFeatureProperties.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/config/ClocktowerFeatureConfiguration.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/game/mic/config/ClocktowerPublicMicProperties.java`
- Modify: `be/src/main/resources/application.yaml`
- Modify: `be/src/test/resources/application.yaml`
- Test: `be/src/test/java/top/egon/mario/clocktower/config/ClocktowerFeaturePropertiesTests.java`

- [ ] **Step 1: Write the failing property binding test**

Create `be/src/test/java/top/egon/mario/clocktower/config/ClocktowerFeaturePropertiesTests.java`:

```java
package top.egon.mario.clocktower.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import top.egon.mario.clocktower.game.mic.config.ClocktowerPublicMicProperties;

import static org.assertj.core.api.Assertions.assertThat;

class ClocktowerFeaturePropertiesTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void featureFlagsDefaultToSafeTaskSixteenValues() {
        contextRunner.run(context -> {
            ClocktowerFeatureProperties flags = context.getBean(ClocktowerFeatureProperties.class);
            ClocktowerPublicMicProperties mic = context.getBean(ClocktowerPublicMicProperties.class);

            assertThat(flags.agentPlayer().enabled()).isTrue();
            assertThat(flags.gameActions().enabled()).isTrue();
            assertThat(flags.newFlow().enabled()).isTrue();
            assertThat(flags.llmAgent().enabled()).isFalse();
            assertThat(mic.isEnabled()).isTrue();
        });
    }

    @Test
    void featureFlagsBindFromClocktowerProperties() {
        contextRunner
                .withPropertyValues(
                        "clocktower.agent-player.enabled=false",
                        "clocktower.game-actions.enabled=false",
                        "clocktower.public-mic.enabled=false",
                        "clocktower.new-flow.enabled=false",
                        "clocktower.llm-agent.enabled=true")
                .run(context -> {
                    ClocktowerFeatureProperties flags = context.getBean(ClocktowerFeatureProperties.class);
                    ClocktowerPublicMicProperties mic = context.getBean(ClocktowerPublicMicProperties.class);

                    assertThat(flags.agentPlayer().enabled()).isFalse();
                    assertThat(flags.gameActions().enabled()).isFalse();
                    assertThat(flags.newFlow().enabled()).isFalse();
                    assertThat(flags.llmAgent().enabled()).isTrue();
                    assertThat(mic.isEnabled()).isFalse();
                });
    }

    @EnableConfigurationProperties({ClocktowerFeatureProperties.class, ClocktowerPublicMicProperties.class})
    static class TestConfiguration {
    }
}
```

- [ ] **Step 2: Run the failing property test**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/.worktrees/clocktower-actor-agent-foundation/be
./mvnw -Dtest=ClocktowerFeaturePropertiesTests test
```

Expected: FAIL because `ClocktowerFeatureProperties` does not exist and `ClocktowerPublicMicProperties` has no `enabled` property.

- [ ] **Step 3: Implement typed properties**

Create `be/src/main/java/top/egon/mario/clocktower/config/ClocktowerFeatureProperties.java`:

```java
package top.egon.mario.clocktower.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "clocktower")
public record ClocktowerFeatureProperties(
        FeatureFlag agentPlayer,
        FeatureFlag gameActions,
        FeatureFlag newFlow,
        FeatureFlag llmAgent
) {

    public ClocktowerFeatureProperties {
        agentPlayer = agentPlayer == null ? FeatureFlag.enabled() : agentPlayer;
        gameActions = gameActions == null ? FeatureFlag.enabled() : gameActions;
        newFlow = newFlow == null ? FeatureFlag.enabled() : newFlow;
        llmAgent = llmAgent == null ? FeatureFlag.disabled() : llmAgent;
    }

    public record FeatureFlag(boolean enabled) {

        static FeatureFlag enabled() {
            return new FeatureFlag(true);
        }

        static FeatureFlag disabled() {
            return new FeatureFlag(false);
        }
    }
}
```

Create `be/src/main/java/top/egon/mario/clocktower/config/ClocktowerFeatureConfiguration.java`:

```java
package top.egon.mario.clocktower.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ClocktowerFeatureProperties.class)
public class ClocktowerFeatureConfiguration {
}
```

Modify `be/src/main/java/top/egon/mario/clocktower/game/mic/config/ClocktowerPublicMicProperties.java` by adding this field above the existing duration fields:

```java
    private boolean enabled = true;
```

- [ ] **Step 4: Declare YAML defaults**

Modify `be/src/main/resources/application.yaml` under the existing `clocktower:` block so it contains:

```yaml
clocktower:
  agent-player:
    enabled: ${CLOCKTOWER_AGENT_PLAYER_ENABLED:true}
  game-actions:
    enabled: ${CLOCKTOWER_GAME_ACTIONS_ENABLED:true}
  public-mic:
    enabled: ${CLOCKTOWER_PUBLIC_MIC_ENABLED:true}
    round-robin-turn-seconds: ${CLOCKTOWER_PUBLIC_MIC_ROUND_ROBIN_TURN_SECONDS:45}
    grab-mic-total-seconds: ${CLOCKTOWER_PUBLIC_MIC_GRAB_MIC_TOTAL_SECONDS:300}
    grab-mic-hold-seconds: ${CLOCKTOWER_PUBLIC_MIC_GRAB_MIC_HOLD_SECONDS:45}
  new-flow:
    enabled: ${CLOCKTOWER_NEW_FLOW_ENABLED:true}
  llm-agent:
    enabled: ${CLOCKTOWER_LLM_AGENT_ENABLED:false}
  agent:
```

Keep the existing `clocktower.agent` values below `agent:` unchanged.

Modify `be/src/test/resources/application.yaml` under the existing `clocktower:` block so it contains:

```yaml
clocktower:
  agent-player:
    enabled: true
  game-actions:
    enabled: true
  public-mic:
    enabled: true
  new-flow:
    enabled: true
  llm-agent:
    enabled: false
  agent:
```

Keep the existing test `clocktower.agent.worker.runner.enabled=false` below `agent:`.

- [ ] **Step 5: Run the property test**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/.worktrees/clocktower-actor-agent-foundation/be
./mvnw -Dtest=ClocktowerFeaturePropertiesTests test
```

Expected: PASS.

- [ ] **Step 6: Commit Task 1**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/.worktrees/clocktower-actor-agent-foundation
git add be/src/main/java/top/egon/mario/clocktower/config/ClocktowerFeatureProperties.java \
  be/src/main/java/top/egon/mario/clocktower/config/ClocktowerFeatureConfiguration.java \
  be/src/main/java/top/egon/mario/clocktower/game/mic/config/ClocktowerPublicMicProperties.java \
  be/src/main/resources/application.yaml \
  be/src/test/resources/application.yaml \
  be/src/test/java/top/egon/mario/clocktower/config/ClocktowerFeaturePropertiesTests.java
git commit -m "feat(clocktower): add cutover feature flags"
```

---

### Task 2: Gate Agent Room Seats And Add Runtime Metadata

**Files:**

- Modify: `be/src/main/java/top/egon/mario/clocktower/room/service/impl/ClocktowerRoomLobbyServiceImpl.java`
- Modify: `be/src/test/java/top/egon/mario/clocktower/room/ClocktowerRoomRefactorServiceTests.java`
- Create: `be/src/test/java/top/egon/mario/clocktower/room/ClocktowerAgentPlayerFeatureFlagTests.java`

- [ ] **Step 1: Write failing tests**

In `ClocktowerRoomRefactorServiceTests#createRoomCreatesGenericRoomProfileSeatDraftAndRoomPublicConversation`, add this assertion after the existing profile assertions:

```java
        assertThat(profile.getMetadataJson())
                .contains("\"runtimeModel\":\"GAME_V2\"")
                .contains("\"seatingPolicy\":\"OPEN_SEATING\"");
```

Create `be/src/test/java/top/egon/mario/clocktower/room/ClocktowerAgentPlayerFeatureFlagTests.java`:

```java
package top.egon.mario.clocktower.room;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomCreateRequest;
import top.egon.mario.clocktower.room.dto.response.ClocktowerRoomResponse;
import top.egon.mario.clocktower.room.service.ClocktowerRoomLobbyService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.ai.dashscope.api-key=test-api-key",
        "clocktower.agent-player.enabled=false"
})
@Transactional
class ClocktowerAgentPlayerFeatureFlagTests {

    @Autowired
    private ClocktowerRoomLobbyService roomService;

    @Test
    void disabledAgentPlayerRejectsNonZeroAgentSeatCount() {
        assertThatThrownBy(() -> roomService.createRoom(createRequest(4), owner()))
                .isInstanceOf(ClocktowerException.class)
                .hasMessageContaining("CLOCKTOWER_AGENT_PLAYER_DISABLED");
    }

    @Test
    void disabledAgentPlayerKeepsZeroAgentRoomCreationAvailable() {
        ClocktowerRoomResponse room = roomService.createRoom(createRequest(0), owner());

        assertThat(room.seats()).hasSize(5);
        assertThat(room.seats()).allSatisfy(seat -> assertThat(seat.isAgent()).isFalse());
    }

    private ClocktowerRoomCreateRequest createRequest(int agentSeatCount) {
        return new ClocktowerRoomCreateRequest("No Agents", ClocktowerScriptCode.TROUBLE_BREWING,
                5, null, null, List.of("EMPATH", "CHEF", "MONK", "POISONER", "IMP"),
                "HUMAN_ST", false, false, agentSeatCount, "PUBLIC", "OPEN_SEATING");
    }

    private RbacPrincipal owner() {
        return new RbacPrincipal(1L, "owner", Set.of());
    }
}
```

- [ ] **Step 2: Run the failing tests**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/.worktrees/clocktower-actor-agent-foundation/be
./mvnw -Dtest=ClocktowerRoomRefactorServiceTests,ClocktowerAgentPlayerFeatureFlagTests test
```

Expected: FAIL because runtime metadata is missing and non-zero Agent seats are still accepted while the flag is disabled.

- [ ] **Step 3: Implement Agent-player gate and metadata**

In `ClocktowerRoomLobbyServiceImpl`, import:

```java
import top.egon.mario.clocktower.config.ClocktowerFeatureProperties;
```

Add this constant near the existing constants:

```java
    private static final String RUNTIME_MODEL_GAME_V2 = "GAME_V2";
```

Add this final field:

```java
    private final ClocktowerFeatureProperties featureProperties;
```

Replace the profile metadata write in `createRoom` with:

```java
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("runtimeModel", RUNTIME_MODEL_GAME_V2);
        metadata.put("seatingPolicy", resolvedSeatingPolicy);
        profile.setMetadataJson(writeJson(metadata));
```

Add `java.util.LinkedHashMap` to imports if it is not already present.

Update `requireAgentSeatCount`:

```java
    private int requireAgentSeatCount(int requested, int playerCount) {
        if (requested < 0) {
            throw new ClocktowerException("CLOCKTOWER_AGENT_SEAT_COUNT_INVALID");
        }
        if (requested >= playerCount) {
            throw new ClocktowerException("CLOCKTOWER_AGENT_SEAT_COUNT_INVALID");
        }
        if (requested > 0 && !featureProperties.agentPlayer().enabled()) {
            throw new ClocktowerException("CLOCKTOWER_AGENT_PLAYER_DISABLED");
        }
        return requested;
    }
```

- [ ] **Step 4: Run the room tests**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/.worktrees/clocktower-actor-agent-foundation/be
./mvnw -Dtest=ClocktowerRoomRefactorServiceTests,ClocktowerAgentPlayerFeatureFlagTests test
```

Expected: PASS.

- [ ] **Step 5: Commit Task 2**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/.worktrees/clocktower-actor-agent-foundation
git add be/src/main/java/top/egon/mario/clocktower/room/service/impl/ClocktowerRoomLobbyServiceImpl.java \
  be/src/test/java/top/egon/mario/clocktower/room/ClocktowerRoomRefactorServiceTests.java \
  be/src/test/java/top/egon/mario/clocktower/room/ClocktowerAgentPlayerFeatureFlagTests.java
git commit -m "feat(clocktower): gate agent room seats"
```

---

### Task 3: Gate New Game Actions And Prove New Data Isolation

**Files:**

- Modify: `be/src/main/java/top/egon/mario/clocktower/game/action/service/impl/ClocktowerGameActionExecutorImpl.java`
- Create: `be/src/test/java/top/egon/mario/clocktower/game/action/ClocktowerGameActionFeatureFlagTests.java`
- Modify: `be/src/test/java/top/egon/mario/clocktower/game/action/ClocktowerGameActionServiceTests.java`

- [ ] **Step 1: Write failing disabled-action tests**

Create `ClocktowerGameActionFeatureFlagTests` by copying the helper methods from `ClocktowerGameActionServiceTests` that create a 5-seat Agent game, then add these tests:

```java
@SpringBootTest(properties = {
        "spring.ai.dashscope.api-key=test-api-key",
        "clocktower.game-actions.enabled=false"
})
@Transactional
class ClocktowerGameActionFeatureFlagTests {

    @Autowired
    private ClocktowerHumanGameActionService humanActionService;

    @Autowired
    private ClocktowerAgentGameActionService agentActionService;

    @Autowired
    private ClocktowerAgentInstanceRepository agentInstanceRepository;

    @Test
    void disabledGameActionsRejectHumanNewGameAction() {
        StartedGame game = startDayGameWithAgents();
        ClocktowerGameSeatPo humanSeat = game.seats().getFirst();

        assertThatThrownBy(() -> humanActionService.submit(game.gameId(),
                new ClocktowerGameActionRequest(humanSeat.getId(), "PUBLIC_SPEECH", List.of(),
                        null, null, "blocked", Map.of()),
                principal(11L, "player1")))
                .isInstanceOf(ClocktowerException.class)
                .hasMessageContaining("CLOCKTOWER_GAME_ACTIONS_DISABLED");
    }

    @Test
    void disabledGameActionsRejectAgentInternalAction() {
        StartedGame game = startDayGameWithAgents();
        ClocktowerGameSeatPo agentSeat = game.seats().get(1);
        ClocktowerAgentInstancePo instance = agentInstanceRepository
                .findByGameSeatIdAndDeletedFalse(agentSeat.getId())
                .orElseThrow();

        assertThatThrownBy(() -> agentActionService.submitAgentAction(game.gameId(), instance.getId(),
                new ClocktowerGameActionRequest(agentSeat.getId(), "PASS", List.of(),
                        null, null, null, Map.of("passType", "NIGHT_TASK"))))
                .isInstanceOf(ClocktowerException.class)
                .hasMessageContaining("CLOCKTOWER_GAME_ACTIONS_DISABLED");
    }
}
```

The copied helpers must include `startDayGameWithAgents`, `assignReadyRoles`, `readyMetadata`, `createRequest`, `owner`, and `principal` exactly as used in `ClocktowerGameActionServiceTests`.

- [ ] **Step 2: Add new table isolation assertions**

In `ClocktowerGameActionServiceTests`, add imports:

```java
import top.egon.mario.clocktower.game.nomination.repository.ClocktowerGameNominationRepository;
import top.egon.mario.clocktower.game.nomination.repository.ClocktowerGameVoteRepository;
import top.egon.mario.clocktower.grimoire.repository.ClocktowerNominationRepository;
import top.egon.mario.clocktower.grimoire.repository.ClocktowerVoteRepository;
```

Add fields:

```java
    @Autowired
    private ClocktowerGameNominationRepository gameNominationRepository;

    @Autowired
    private ClocktowerGameVoteRepository gameVoteRepository;

    @Autowired
    private ClocktowerNominationRepository legacyNominationRepository;

    @Autowired
    private ClocktowerVoteRepository legacyVoteRepository;
```

Add this test after `alivePlayerVotesOnOpenNomination`:

```java
    @Test
    void gameNominationAndVoteDoNotWriteLegacyTables() {
        StartedGame game = startDayGameWithAgents();
        ClocktowerGameSeatPo nominator = game.seats().getFirst();
        ClocktowerGameSeatPo nominee = game.seats().get(1);
        Long nominationId = openNomination(game, nominator, nominee);

        humanActionService.submit(game.gameId(),
                new ClocktowerGameActionRequest(nominator.getId(), "VOTE", List.of(),
                        nominationId, true, null, Map.of()),
                principal(11L, "player1"));

        assertThat(gameNominationRepository.findByGameIdAndDayNoAndDeletedFalseOrderByIdAsc(
                game.gameId(), 1)).hasSize(1);
        assertThat(gameVoteRepository.findByNominationIdAndDeletedFalseOrderByIdAsc(nominationId)).hasSize(1);
        assertThat(legacyNominationRepository.findByRoomIdAndDeletedFalseOrderByIdAsc(game.roomId())).isEmpty();
        assertThat(legacyVoteRepository.findByRoomIdAndDeletedFalseOrderByIdAsc(game.roomId())).isEmpty();
    }
```

- [ ] **Step 3: Run the failing action tests**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/.worktrees/clocktower-actor-agent-foundation/be
./mvnw -Dtest=ClocktowerGameActionServiceTests,ClocktowerGameActionFeatureFlagTests test
```

Expected: disabled-action tests FAIL because the new action executor does not check `clocktower.game-actions.enabled`.

- [ ] **Step 4: Implement the game-actions gate**

In `ClocktowerGameActionExecutorImpl`, import:

```java
import top.egon.mario.clocktower.config.ClocktowerFeatureProperties;
import top.egon.mario.clocktower.game.mic.config.ClocktowerPublicMicProperties;
```

Add fields:

```java
    private final ClocktowerFeatureProperties featureProperties;
    private final ClocktowerPublicMicProperties micProperties;
```

At the top of `execute`, after the null command check and before repository reads, add:

```java
        if (!featureProperties.gameActions().enabled()) {
            throw new ClocktowerException("CLOCKTOWER_GAME_ACTIONS_DISABLED");
        }
```

Keep the public speech mic fallback for Task 4 by changing only this block in `publicSpeech`:

```java
        if (micProperties.isEnabled()) {
            try {
                publicMicService.requireCanSpeak(game.getId(), seat.getId());
            } catch (ClocktowerException ex) {
                return reject(game, seat, "PUBLIC_SPEECH", ex.getMessage());
            }
        } else if (!"DAY".equals(game.getPhase())) {
            return reject(game, seat, "PUBLIC_SPEECH", "CLOCKTOWER_PUBLIC_MIC_DISABLED");
        }
```

- [ ] **Step 5: Run the action tests**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/.worktrees/clocktower-actor-agent-foundation/be
./mvnw -Dtest=ClocktowerGameActionServiceTests,ClocktowerGameActionFeatureFlagTests test
```

Expected: PASS.

- [ ] **Step 6: Commit Task 3**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/.worktrees/clocktower-actor-agent-foundation
git add be/src/main/java/top/egon/mario/clocktower/game/action/service/impl/ClocktowerGameActionExecutorImpl.java \
  be/src/test/java/top/egon/mario/clocktower/game/action/ClocktowerGameActionFeatureFlagTests.java \
  be/src/test/java/top/egon/mario/clocktower/game/action/ClocktowerGameActionServiceTests.java
git commit -m "feat(clocktower): gate new game actions"
```

---

### Task 4: Gate Public Mic And Prevent DAY Flow Deadlock

**Files:**

- Modify: `be/src/main/java/top/egon/mario/clocktower/game/mic/service/impl/ClocktowerPublicMicServiceImpl.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/game/flow/service/impl/ClocktowerGameFlowServiceImpl.java`
- Test: `be/src/test/java/top/egon/mario/clocktower/game/mic/ClocktowerPublicMicFeatureFlagTests.java`

- [ ] **Step 1: Write disabled public mic tests**

Create `ClocktowerPublicMicFeatureFlagTests` by copying the game-start helpers from `ClocktowerGameFlowServiceTests`, then add these tests:

```java
@SpringBootTest(properties = {
        "spring.ai.dashscope.api-key=test-api-key",
        "clocktower.public-mic.enabled=false"
})
@Transactional
class ClocktowerPublicMicFeatureFlagTests {

    @Autowired
    private ClocktowerGameFlowService flowService;

    @Autowired
    private ClocktowerPublicMicService micService;

    @Autowired
    private ClocktowerHumanGameActionService humanActionService;

    @Autowired
    private ClocktowerGameRepository gameRepository;

    @Test
    void disabledPublicMicRejectsDirectMicStart() {
        StartedGame game = startDayGame();

        assertThatThrownBy(() -> micService.startDayMicSession(game.gameId(), owner()))
                .isInstanceOf(ClocktowerException.class)
                .hasMessageContaining("CLOCKTOWER_PUBLIC_MIC_DISABLED");
    }

    @Test
    void disabledPublicMicLetsDayAdvanceWithoutMicSession() {
        StartedGame game = startDayGame();

        ClocktowerGameAdvanceResult result = flowService.advance(game.gameId(), emptyRequest(), owner());

        assertThat(result.previousPhase()).isEqualTo("DAY");
        assertThat(result.phase()).isEqualTo("NOMINATION");
        assertThat(gameRepository.findByIdAndDeletedFalse(game.gameId()).orElseThrow().getPhase())
                .isEqualTo("NOMINATION");
    }

    @Test
    void disabledPublicMicAllowsDayPublicSpeechWithoutHolder() {
        StartedGame game = startDayGame();
        ClocktowerGameSeatPo humanSeat = game.seats().getFirst();

        ClocktowerGameActionResponse response = humanActionService.submit(game.gameId(),
                new ClocktowerGameActionRequest(humanSeat.getId(), "PUBLIC_SPEECH", List.of(),
                        null, null, "mic is disabled but day speech is open", Map.of()),
                principal(11L, "player1"));

        assertThat(response.accepted()).isTrue();
        assertThat(response.event().eventType()).isEqualTo("PUBLIC_SPEECH");
    }
}
```

The copied helpers must include `startDayGame`, `assignReadyRoles`, `readyMetadata`, `createRequest`, `owner`, `principal`, and `emptyRequest`.

- [ ] **Step 2: Run the failing public mic tests**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/.worktrees/clocktower-actor-agent-foundation/be
./mvnw -Dtest=ClocktowerPublicMicServiceTests,ClocktowerGameFlowServiceTests,ClocktowerPublicMicFeatureFlagTests test
```

Expected: disabled tests FAIL because mic entry points still run and DAY flow still expects mic state.

- [ ] **Step 3: Gate public mic service methods**

In `ClocktowerPublicMicServiceImpl`, add this helper near the other private guard methods:

```java
    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new ClocktowerException("CLOCKTOWER_PUBLIC_MIC_DISABLED");
        }
    }
```

Call `requireEnabled();` at the start of these methods:

```java
startDayMicSession
getMicSession
finishCurrentTurn
finishCurrentTurnAsActor
skipTurn
grabMic
grabMicAsActor
releaseMic
extendGrabMic
closeSession
requireCanSpeak
```

Leave `canSpeak` unchanged except that it now returns `false` when `requireCanSpeak` throws `CLOCKTOWER_PUBLIC_MIC_DISABLED`.

- [ ] **Step 4: Skip mic blockers when mic is disabled**

In `ClocktowerGameFlowServiceImpl`, import:

```java
import top.egon.mario.clocktower.game.mic.config.ClocktowerPublicMicProperties;
```

Add field:

```java
    private final ClocktowerPublicMicProperties micProperties;
```

At the top of `nextAfterDay`, add:

```java
        if (!micProperties.isEnabled()) {
            counters.put("micEnabled", false);
            counters.put("micStatus", "DISABLED");
            return PHASE_NOMINATION;
        }
```

In `applyAdvance`, replace the day mic start block with:

```java
        if (PHASE_DAY.equals(saved.getPhase()) && micProperties.isEnabled()) {
            micService.startDayMicSession(saved.getId(), principal);
        }
```

- [ ] **Step 5: Run the public mic and flow tests**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/.worktrees/clocktower-actor-agent-foundation/be
./mvnw -Dtest=ClocktowerPublicMicServiceTests,ClocktowerGameFlowServiceTests,ClocktowerPublicMicFeatureFlagTests test
```

Expected: PASS.

- [ ] **Step 6: Commit Task 4**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/.worktrees/clocktower-actor-agent-foundation
git add be/src/main/java/top/egon/mario/clocktower/game/mic/service/impl/ClocktowerPublicMicServiceImpl.java \
  be/src/main/java/top/egon/mario/clocktower/game/flow/service/impl/ClocktowerGameFlowServiceImpl.java \
  be/src/test/java/top/egon/mario/clocktower/game/mic/ClocktowerPublicMicFeatureFlagTests.java
git commit -m "feat(clocktower): gate public mic runtime"
```

---

### Task 5: Gate New Game Flow And Mark Legacy Services

**Files:**

- Modify: `be/src/main/java/top/egon/mario/clocktower/game/flow/service/impl/ClocktowerGameFlowServiceImpl.java`
- Create: `be/src/test/java/top/egon/mario/clocktower/game/flow/ClocktowerGameFlowFeatureFlagTests.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/action/service/impl/ClocktowerActionServiceImpl.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/flow/service/impl/ClocktowerFlowServiceImpl.java`

- [ ] **Step 1: Write disabled new-flow tests**

Create `ClocktowerGameFlowFeatureFlagTests` by copying the game-start helpers from `ClocktowerGameFlowServiceTests`, then add:

```java
@SpringBootTest(properties = {
        "spring.ai.dashscope.api-key=test-api-key",
        "clocktower.new-flow.enabled=false"
})
@Transactional
class ClocktowerGameFlowFeatureFlagTests {

    @Autowired
    private ClocktowerGameFlowService flowService;

    @Test
    void disabledNewFlowRejectsGetAndAdvance() {
        StartedGame game = startGame();

        assertThatThrownBy(() -> flowService.getFlow(game.gameId(), owner()))
                .isInstanceOf(ClocktowerException.class)
                .hasMessageContaining("CLOCKTOWER_NEW_FLOW_DISABLED");
        assertThatThrownBy(() -> flowService.advance(game.gameId(), emptyRequest(), owner()))
                .isInstanceOf(ClocktowerException.class)
                .hasMessageContaining("CLOCKTOWER_NEW_FLOW_DISABLED");
        assertThatThrownBy(() -> flowService.forceAdvance(game.gameId(),
                new ClocktowerGameAdvanceRequest("DAY", "disabled", Map.of()), owner()))
                .isInstanceOf(ClocktowerException.class)
                .hasMessageContaining("CLOCKTOWER_NEW_FLOW_DISABLED");
    }
}
```

The copied helpers must include `startGame`, `assignReadyRoles`, `readyMetadata`, `createRequest`, `owner`, and `emptyRequest`.

- [ ] **Step 2: Run the failing new-flow test**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/.worktrees/clocktower-actor-agent-foundation/be
./mvnw -Dtest=ClocktowerGameFlowServiceTests,ClocktowerGameFlowFeatureFlagTests,ClocktowerFlowServiceTests test
```

Expected: disabled test FAIL because `ClocktowerGameFlowServiceImpl` does not check `clocktower.new-flow.enabled`.

- [ ] **Step 3: Implement the new-flow gate**

In `ClocktowerGameFlowServiceImpl`, import:

```java
import top.egon.mario.clocktower.config.ClocktowerFeatureProperties;
```

Add field:

```java
    private final ClocktowerFeatureProperties featureProperties;
```

Add helper:

```java
    private void requireNewFlowEnabled() {
        if (!featureProperties.newFlow().enabled()) {
            throw new ClocktowerException("CLOCKTOWER_NEW_FLOW_DISABLED");
        }
    }
```

Call `requireNewFlowEnabled();` at the start of:

```java
getFlow
advance
forceAdvance
```

- [ ] **Step 4: Mark legacy implementations**

Add a short class Javadoc and `@Deprecated` to `ClocktowerActionServiceImpl`:

```java
/**
 * Legacy room action service kept for pre-GAME_V2 rooms during the cutover window.
 */
@Deprecated
@Service
@RequiredArgsConstructor
public class ClocktowerActionServiceImpl implements ClocktowerActionService {
```

Add a matching marker to `ClocktowerFlowServiceImpl`:

```java
/**
 * Legacy room flow service kept for pre-GAME_V2 rooms during the cutover window.
 */
@Deprecated
@Service
@RequiredArgsConstructor
public class ClocktowerFlowServiceImpl implements ClocktowerFlowService {
```

Do not change any method behavior in either legacy service.

- [ ] **Step 5: Run new and legacy flow tests**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/.worktrees/clocktower-actor-agent-foundation/be
./mvnw -Dtest=ClocktowerGameFlowServiceTests,ClocktowerGameFlowFeatureFlagTests,ClocktowerFlowServiceTests,ClocktowerActionServiceTests test
```

Expected: PASS.

- [ ] **Step 6: Commit Task 5**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/.worktrees/clocktower-actor-agent-foundation
git add be/src/main/java/top/egon/mario/clocktower/game/flow/service/impl/ClocktowerGameFlowServiceImpl.java \
  be/src/test/java/top/egon/mario/clocktower/game/flow/ClocktowerGameFlowFeatureFlagTests.java \
  be/src/main/java/top/egon/mario/clocktower/action/service/impl/ClocktowerActionServiceImpl.java \
  be/src/main/java/top/egon/mario/clocktower/flow/service/impl/ClocktowerFlowServiceImpl.java
git commit -m "feat(clocktower): gate new game flow"
```

---

### Task 6: Add Global LLM Agent Kill Switch

**Files:**

- Modify: `be/src/main/java/top/egon/mario/clocktower/agent/strategy/ConfigurableClocktowerAgentPolicy.java`
- Modify: `be/src/test/java/top/egon/mario/clocktower/agent/strategy/ClocktowerAgentLlmPolicyTests.java`

- [ ] **Step 1: Write the failing LLM kill-switch test**

In `ClocktowerAgentLlmPolicyTests`, add import:

```java
import top.egon.mario.clocktower.config.ClocktowerFeatureProperties;
```

Add this test after `configurablePolicyHeuristicModeDoesNotCallLlm`:

```java
    @Test
    void configurablePolicyGlobalLlmAgentDisabledDoesNotCallLlm() {
        FakeLlmClient llmClient = new FakeLlmClient("""
                {"intentId":"intent-1","content":"LLM speech","reasoningSummary":"llm"}
                """);
        ConfigurableClocktowerAgentPolicy policy = configurablePolicy("LLM", true, false, llmClient);

        AgentPolicyResult result = policy.decideWithMetadata(context(normalView()));

        assertThat(llmClient.calls).isZero();
        assertThat(result.policyType()).isEqualTo("HEURISTIC");
        assertThat(result.metadata()).containsEntry("llmAgentEnabled", false);
    }
```

Change the existing `configurablePolicy(String mode, boolean enabled, FakeLlmClient llmClient)` helper to:

```java
    private ConfigurableClocktowerAgentPolicy configurablePolicy(String mode, boolean enabled,
                                                                FakeLlmClient llmClient) {
        return configurablePolicy(mode, enabled, true, llmClient);
    }

    private ConfigurableClocktowerAgentPolicy configurablePolicy(String mode, boolean enabled,
                                                                boolean llmAgentEnabled,
                                                                FakeLlmClient llmClient) {
        ClocktowerAgentPolicyProperties properties = new ClocktowerAgentPolicyProperties(
                mode,
                new ClocktowerAgentPolicyProperties.Llm(enabled,
                        top.egon.mario.agent.model.dto.enums.ModelProviderType.DASHSCOPE,
                        "qwen-plus",
                        8000,
                        800,
                        500,
                        false,
                        top.egon.mario.agent.model.dto.enums.ModelScenario.AGENT_CHAT)
        );
        ClocktowerFeatureProperties featureProperties = new ClocktowerFeatureProperties(
                new ClocktowerFeatureProperties.FeatureFlag(true),
                new ClocktowerFeatureProperties.FeatureFlag(true),
                new ClocktowerFeatureProperties.FeatureFlag(true),
                new ClocktowerFeatureProperties.FeatureFlag(llmAgentEnabled)
        );
        ClocktowerAgentLlmPolicy llmPolicy = new ClocktowerAgentLlmPolicy(
                llmClient,
                new ClocktowerAgentPromptBuilder(),
                new ClocktowerAgentLlmOutputParser(new ClocktowerAgentDecisionSanitizer(500))
        );
        return new ConfigurableClocktowerAgentPolicy(properties, featureProperties, policy(), llmPolicy);
    }
```

- [ ] **Step 2: Run the failing LLM test**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/.worktrees/clocktower-actor-agent-foundation/be
./mvnw -Dtest=ClocktowerAgentLlmPolicyTests test
```

Expected: FAIL because `ConfigurableClocktowerAgentPolicy` does not accept `ClocktowerFeatureProperties` and does not record `llmAgentEnabled=false`.

- [ ] **Step 3: Implement the kill switch**

In `ConfigurableClocktowerAgentPolicy`, import:

```java
import top.egon.mario.clocktower.config.ClocktowerFeatureProperties;
```

Add field and constructor parameter:

```java
    private final ClocktowerFeatureProperties featureProperties;

    public ConfigurableClocktowerAgentPolicy(ClocktowerAgentPolicyProperties properties,
                                             ClocktowerFeatureProperties featureProperties,
                                             HeuristicAgentPolicy heuristicPolicy,
                                             ClocktowerAgentLlmPolicy llmPolicy) {
        this.properties = properties;
        this.featureProperties = featureProperties;
        this.heuristicPolicy = heuristicPolicy;
        this.llmPolicy = llmPolicy;
    }
```

Replace the first branch in `decideWithMetadata` with:

```java
        if (ClocktowerAgentDecisionPolicyType.HEURISTIC.equals(mode)
                || !featureProperties.llmAgent().enabled()
                || !properties.llm().enabled()) {
            return new AgentPolicyResult(heuristicPolicy.decide(context),
                    ClocktowerAgentDecisionPolicyType.HEURISTIC,
                    ClocktowerAgentDecisionStatus.ACCEPTED,
                    null, null, null, null,
                    Map.of("configuredPolicy", mode,
                            "llmAgentEnabled", featureProperties.llmAgent().enabled(),
                            "agentLlmEnabled", properties.llm().enabled()));
        }
```

- [ ] **Step 4: Run the LLM tests**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/.worktrees/clocktower-actor-agent-foundation/be
./mvnw -Dtest=ClocktowerAgentLlmPolicyTests test
```

Expected: PASS.

- [ ] **Step 5: Commit Task 6**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/.worktrees/clocktower-actor-agent-foundation
git add be/src/main/java/top/egon/mario/clocktower/agent/strategy/ConfigurableClocktowerAgentPolicy.java \
  be/src/test/java/top/egon/mario/clocktower/agent/strategy/ClocktowerAgentLlmPolicyTests.java
git commit -m "feat(clocktower): add llm agent kill switch"
```

---

### Task 7: Harden Frontend Action Endpoint Tests

**Files:**

- Modify: `fe/src/modules/clocktower/GameRoomPage.tsx`
- Modify: `fe/src/modules/clocktower/GameRoomPage.test.tsx`
- Modify: `fe/src/modules/clocktower/ClocktowerRoomPlayPage.test.tsx`

- [ ] **Step 1: Write failing request-builder tests**

In `GameRoomPage.test.tsx`, extend the import:

```ts
import {
    buildClocktowerGameActionRequest,
    buildClocktowerLegacyActionRequest,
    Component as GameRoomPage,
    GameRoomSurface,
    SeatPublicList,
} from './GameRoomPage'
```

Add these tests:

```ts
    test('builds game action payload with game seat ids for GAME_V2 submit', () => {
        const request = buildClocktowerGameActionRequest(gameView, {
            actionType: 'NOMINATE',
            targetSeatIds: [32],
            content: 'nominate 2',
        })

        expect(request).toEqual({
            actorGameSeatId: 31,
            actionType: 'NOMINATE',
            targetGameSeatIds: [32],
            nominationId: null,
            vote: null,
            content: 'nominate 2',
            payload: {},
        })
    })

    test('builds legacy action payload with room seat ids only for legacy submit', () => {
        const request = buildClocktowerLegacyActionRequest(gameView, {
            actionType: 'NOMINATE',
            targetSeatIds: [4],
            content: 'nominate old 2',
        }, 'client-action-1')

        expect(request).toEqual({
            seatId: 3,
            actionType: 'NOMINATE',
            targetSeatIds: [4],
            content: 'nominate old 2',
            payload: {},
            clientActionId: 'client-action-1',
        })
    })
```

- [ ] **Step 2: Run the failing frontend tests**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/.worktrees/clocktower-actor-agent-foundation/fe
bun run --bun vitest run src/modules/clocktower/GameRoomPage.test.tsx src/modules/clocktower/ClocktowerRoomPlayPage.test.tsx
```

Expected: FAIL because the request builder functions are not exported.

- [ ] **Step 3: Extract request builders**

In `GameRoomPage.tsx`, add these imports to the existing type import block:

```ts
    ClocktowerGameActionRequest,
    ClocktowerPlayerActionRequest,
```

Add these exported helpers above `GameRoomSurface`:

```ts
export function buildClocktowerGameActionRequest(
    view: ClocktowerGameViewResponse,
    values: VotePanelValues,
): ClocktowerGameActionRequest {
    if (!view.mySeat) {
        throw new Error('CLOCKTOWER_GAME_SEAT_REQUIRED')
    }
    return {
        actorGameSeatId: view.mySeat.gameSeatId,
        actionType: values.actionType,
        targetGameSeatIds: values.targetSeatIds ?? [],
        nominationId: null,
        vote: null,
        content: values.content,
        payload: {},
    }
}

export function buildClocktowerLegacyActionRequest(
    view: ClocktowerGameViewResponse,
    values: VotePanelValues,
    clientActionId: string,
): ClocktowerPlayerActionRequest {
    if (!view.mySeat) {
        throw new Error('CLOCKTOWER_ROOM_SEAT_REQUIRED')
    }
    return {
        seatId: view.mySeat.roomSeatId,
        actionType: values.actionType,
        targetSeatIds: values.targetSeatIds ?? [],
        content: values.content,
        payload: {},
        clientActionId,
    }
}
```

In `GameRoomSurface.submitAction`, replace the inline request objects with:

```ts
            const response = useGameActionApi
                ? await submitClocktowerGameAction(view.gameId, buildClocktowerGameActionRequest(view, values))
                : await submitClocktowerPlayerAction(
                    view.roomId,
                    buildClocktowerLegacyActionRequest(view, values, crypto.randomUUID()),
                )
```

- [ ] **Step 4: Keep play-surface test explicit**

In `ClocktowerRoomPlayPage.test.tsx`, keep the existing `renders player play surface with new game action controls` test and add:

```ts
        expect(markup).toContain('公开发言')
```

This keeps the route-level coverage on the new play surface while `GameRoomPage.test.tsx` verifies the action payload builder.

- [ ] **Step 5: Run frontend tests**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/.worktrees/clocktower-actor-agent-foundation/fe
bun run --bun vitest run src/modules/clocktower/clocktowerService.test.ts src/modules/clocktower/GameRoomPage.test.tsx src/modules/clocktower/ClocktowerRoomPlayPage.test.tsx src/modules/clocktower/RoomLobbyPage.test.tsx src/modules/clocktower/components/PublicMicPanel.test.tsx
```

Expected: PASS.

- [ ] **Step 6: Commit Task 7**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/.worktrees/clocktower-actor-agent-foundation
git add fe/src/modules/clocktower/GameRoomPage.tsx \
  fe/src/modules/clocktower/GameRoomPage.test.tsx \
  fe/src/modules/clocktower/ClocktowerRoomPlayPage.test.tsx
git commit -m "test(clocktower): harden game action endpoint coverage"
```

---

### Task 8: Final Targeted Validation

**Files:**

- Validate backend and frontend only.
- Do not start backend or frontend servers.

- [ ] **Step 1: Run backend targeted validation**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/.worktrees/clocktower-actor-agent-foundation/be
./mvnw -Dtest=ClocktowerFeaturePropertiesTests,ClocktowerRoomRefactorServiceTests,ClocktowerAgentPlayerFeatureFlagTests,ClocktowerGameLifecycleServiceTests,ClocktowerPublicMicServiceTests,ClocktowerPublicMicFeatureFlagTests,ClocktowerGameActionServiceTests,ClocktowerGameActionFeatureFlagTests,ClocktowerGameFlowServiceTests,ClocktowerGameFlowFeatureFlagTests,ClocktowerFlowServiceTests,ClocktowerActionServiceTests,ClocktowerAgentLlmPolicyTests,ClocktowerSchemaMigrationTests test
```

Expected: PASS.

- [ ] **Step 2: Run frontend targeted validation**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/.worktrees/clocktower-actor-agent-foundation/fe
bun run --bun vitest run src/modules/clocktower/clocktowerService.test.ts src/modules/clocktower/GameRoomPage.test.tsx src/modules/clocktower/ClocktowerRoomPlayPage.test.tsx src/modules/clocktower/RoomLobbyPage.test.tsx src/modules/clocktower/components/PublicMicPanel.test.tsx
```

Expected: PASS.

- [ ] **Step 3: Check working tree**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/.worktrees/clocktower-actor-agent-foundation
git status --short
```

Expected: clean working tree after all task commits.

If a validation command fails, keep the failure visible, fix only in-scope issues, rerun the failing command, and amend or create the task commit that owns the failure.
