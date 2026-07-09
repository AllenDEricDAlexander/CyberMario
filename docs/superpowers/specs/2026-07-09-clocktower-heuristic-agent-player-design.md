# Clocktower Heuristic Agent Player Design

**Date:** 2026-07-09

**Status:** User-approved approach A. This spec covers task 12 from `docs/clocktower_agent_tasks`: replace task 10 placeholder Agent actions with a deterministic heuristic player that uses task 11 private view and memory. Implementation must be planned separately before code changes.

---

## 1. Background

Tasks 01 through 11 established the Agent-capable game path:

- Room and game seats can be HUMAN or AGENT actors.
- `ClocktowerGameActionExecutor` is the shared action entry point for HUMAN and AGENT actors.
- Public mic, nomination, vote, execution, night tasks, and game flow now use game-scoped tables and events.
- `ClocktowerAgentRuntime` consumes queued tasks, refreshes memory, and currently performs placeholder behavior:
  - mic turn -> pass
  - vote window -> vote false
  - night task -> role skill `autoChoose`
- `ClocktowerAgentPrivateViewService` builds an Agent-only private view with legal intents, visible events, memory, private information, and Spy grimoire.

Task 12 turns that runtime into a real non-LLM player. The first version should be deterministic, testable, and safe. It should not attempt full human-like deception or natural-language reasoning.

## 2. Goals

1. Add a strategy boundary so Agent behavior is policy-driven instead of hard-coded in runtime.
2. Implement `HeuristicAgentPolicy` using task 11 `AgentPrivateView`.
3. Let Agents speak or pass on round-robin mic turns and always release the mic.
4. Let Agents grab mic during the grab-mic phase without violating the one-holder rule.
5. Let alive Agents nominate when the current phase and game state allow it.
6. Let Agents vote on open nominations, including dead-vote handling for dead Agents.
7. Let Agents choose legal night targets using basic Trouble Brewing heuristics.
8. Generate and reuse a bluff plan for evil Agents.
9. Write a basic decision summary into task result JSON.
10. Keep all behavior deterministic so tests can assert exact outcomes.

## 3. Non-Goals

This task does not:

- Add LLM policy selection or model calls.
- Add the task 15 decision audit table.
- Add frontend UI.
- Add a generic timer service for `TIMER_TICK`.
- Add public HTTP API endpoints.
- Add unsupported Clocktower actions such as Slayer shooting or Ravenkeeper death-trigger selection.
- Implement complex natural-language contradiction detection.
- Change existing Flyway migrations.

## 4. Selected Approach

Use approach A: add a strategy layer with small planners, then make `ClocktowerAgentRuntime` delegate to that strategy.

Rejected alternatives:

- A larger "full AI player" implementation with persisted suspicion, extensive event reactions, and timer tasks would cover more of the task doc but is too wide for this step.
- A policy-only package without runtime integration would be easy to test but would not meet the task 12 acceptance criteria.
- Embedding heuristics directly in `ClocktowerAgentRuntime` would ship faster but would make task 15 LLM policy and audit integration harder.

Design pattern decision:

- Use the Strategy pattern for `ClocktowerAgentPolicy`. The variation point is the decision engine: heuristic now, LLM policy later.
- Use small planner domain services for speech, nominations, votes, night choices, and bluff planning. They are not separate strategies unless there is a real variation point.
- Use a thin intent executor to map `AgentIntent` to the existing game action services. This keeps runtime orchestration readable and keeps action validation inside existing domain services.
- Do not add factories, chains, or a state machine. Current trigger types and legal intents are sufficient for v1.

## 5. Package Boundary

Create:

```text
top.egon.mario.clocktower.agent.strategy
  AgentDecision
  AgentDecisionContext
  AgentIntent
  ClocktowerAgentPolicy
  HeuristicAgentPolicy
  AgentSpeechPlanner
  AgentNominationPlanner
  AgentVotePlanner
  AgentNightChoicePlanner
  AgentIntentExecutor
  AgentDecisionSummary

top.egon.mario.clocktower.agent.strategy.troublebrewing
  TroubleBrewingBluffPlanner
```

Modify:

```text
top.egon.mario.clocktower.agent.runtime.ClocktowerAgentRuntime
top.egon.mario.clocktower.agent.view.service.impl.ClocktowerAgentPrivateViewServiceImpl
top.egon.mario.clocktower.game.mic.service.ClocktowerPublicMicService
top.egon.mario.clocktower.game.mic.service.impl.ClocktowerPublicMicServiceImpl
top.egon.mario.clocktower.agent.runtime.ClocktowerAgentTriggerListener
```

Add repository methods only where the planners or private view need existing data:

```text
ClocktowerAgentProfileRepository
ClocktowerGameSeatRepository
ClocktowerGameVoteRepository
ClocktowerGameNightTaskRepository
ClocktowerAgentMemoryRepository
```

No new database migration is required for task 12.

## 6. Core Contracts

`ClocktowerAgentPolicy`:

```java
public interface ClocktowerAgentPolicy {
    AgentDecision decide(AgentDecisionContext context);
}
```

`AgentDecisionContext`:

```java
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

`AgentDecision`:

```java
public record AgentDecision(
        AgentIntent intent,
        String reasoningSummary,
        Map<String, Object> diagnostics
) {
}
```

`AgentIntent`:

```java
public sealed interface AgentIntent {
    record PublicSpeech(String content) implements AgentIntent {}
    record GrabMic(String reason) implements AgentIntent {}
    record FinishSpeech(String reason) implements AgentIntent {}
    record Nominate(Long targetGameSeatId, String reason) implements AgentIntent {}
    record Vote(Long nominationId, boolean vote, String reason) implements AgentIntent {}
    record NightChoice(Long taskId, List<Long> targetGameSeatIds, Map<String, Object> payload) implements AgentIntent {}
    record Pass(String reason) implements AgentIntent {}
    record Noop(String reason) implements AgentIntent {}
}
```

The policy returns one selected intent. The executor may perform a small sequence for speech-related intents:

- `PublicSpeech` -> submit `PUBLIC_SPEECH`, then submit `FINISH_SPEECH` if speech was accepted.
- `Pass` during mic turn -> submit `PASS` with `passType=MIC_TURN`.
- `GrabMic` -> call Agent-capable mic grab, then submit one short `PUBLIC_SPEECH`, then `FINISH_SPEECH`.

## 7. Legal Intent Enhancements

`AgentPrivateView` already exposes `List<AgentLegalIntentView>`. Task 12 should make the list accurate enough for policies:

- Add `PUBLIC_SPEECH` and `PASS` only when the Agent currently holds an active mic turn.
- Add `GRAB_MIC` only when the session is in `GRAB_MIC`, the grab window is still open, and no current holder exists.
- Add `NOMINATE` in `DAY` or `NOMINATION` only when:
  - the Agent is alive
  - no open nomination exists
  - the current mic session is absent or closed
  - the Agent has not nominated today
  - candidate seats are alive, active players, not self, and not already nominated today
- Add `VOTE` for open nominations only if this Agent has not voted on that nomination.
- Add `NIGHT_CHOICE` for the Agent's current pending night task and include legal selectable target IDs in payload.

The existing `AgentLegalIntentView` record is sufficient. Use payload fields such as:

```text
eligibleTargetGameSeatIds
legalTargetGameSeatIds
nomineeGameSeatId
taskType
roleCode
```

## 8. Runtime Flow

`ClocktowerAgentRuntime#handle` becomes:

```text
1. Load and validate active/full-auto Agent instance.
2. Refresh memory.
3. Build AgentPrivateView.
4. Load Agent profile.
5. Build AgentDecisionContext.
6. Ask ClocktowerAgentPolicy to decide.
7. Validate selected intent against legal intents.
8. Execute the intent through AgentIntentExecutor.
9. Return task result with memory refresh and decision summary.
```

Inactive, paused, and ST-approval Agents keep the current skip behavior and do not refresh memory or decide.

Task result JSON should include:

```json
{
  "policy": "HEURISTIC_V0",
  "triggerType": "MIC_TURN_STARTED",
  "legalIntents": ["PUBLIC_SPEECH", "PASS"],
  "selectedIntent": "PUBLIC_SPEECH",
  "reasoningSummary": "shared private info and released mic",
  "accepted": true,
  "eventType": "FINISH_SPEECH",
  "memoryLastSeenEventSeq": 7,
  "memoryInsertedCount": 2
}
```

If an action is rejected due to a race, such as another actor taking the grab mic first, the task should complete with `accepted=false` and the rejected code. It should not retry indefinitely for normal gameplay races.

## 9. Public Mic Agent Grab

Current `ClocktowerPublicMicService#grabMic` only accepts a human principal. Task 12 should add an internal Agent-capable path:

```java
ClocktowerMicSessionView grabMicAsActor(Long gameId, Long actorGameSeatId);
```

Rules:

- Same session checks as human grab mic.
- Requires status `GRAB_MIC`.
- Requires no current holder.
- Requires the actor seat to be an active HUMAN or AGENT player.
- Creates a `GRAB` mic turn and emits `MIC_TURN_STARTED`.

The public HTTP controller can keep using the existing human principal method. The new method is for runtime and action execution only.

## 10. Trigger Scope

Use existing triggers:

- `MIC_TURN_STARTED`
- `VOTE_WINDOW_OPENED`
- `NIGHT_TASK_OPENED`
- `PHASE_CHANGED`

Add scheduling for:

- `MIC_GRAB_OPENED`: schedule active Agents so they may decide whether to grab.
- `PUBLIC_EVENT_APPENDED`: schedule active Agents for public `PUBLIC_SPEECH`, `NOMINATION_OPENED`, `VOTE_CAST`, and `PLAYER_DIED` events with trigger keys like `publicEvent:{eventId}:react`. The v1 policy returns `Noop` unless there is a free grab-mic window and a deterministic high-priority reason to react.

Do not implement `TIMER_TICK` in this task. It needs a real scheduler policy and should be handled separately.

## 11. Heuristic Rules

### Speech

When holding mic:

- If good and has private info, share a short partial statement without exposing hidden markers.
- If evil, use bluff plan instead of real role.
- If no strong information, ask one seat to explain a vote, claim, or nomination.
- If talkativeness is low or no useful content exists, pass.

Hard limits:

- Content length target: 40 to 180 Chinese characters.
- Never include grimoire-only facts unless the Agent is Spy and the chosen bluff can plausibly be public.
- Always release mic after one speech.

### Bluff

For evil Agents, `TroubleBrewingBluffPlanner` idempotently creates or loads one `BLUFF_PLAN` memory.

Plan shape:

```json
{
  "claimRoleCode": "CHEF",
  "backupClaimRoleCode": "SOLDIER",
  "fakeInfo": {
    "chefNumber": 1
  },
  "protectSeats": [4],
  "pushTargets": [2, 5]
}
```

Rules:

- Evil team information is provided through `roleSpecificContext.evilTeam`; this does not grant full grimoire.
- Avoid selecting an evil teammate's real role as the main bluff.
- Prefer safe Trouble Brewing claims such as `CHEF`, `SOLDIER`, or `RAVENKEEPER`.
- Spy may use grimoire to pick a stronger non-conflicting bluff, but v1 remains deterministic.

### Nomination

Good scoring:

```text
score =
  suspicion(target)
  + privateInfoHit(target)
  + roleClaimSuspicion(target)
  + votePatternSuspicion(target)
  - trust(target)
  - usefulClaimRisk(target)
```

Evil scoring:

```text
score =
  goodThreat(target)
  + frameable(target)
  - evilAllyRisk(target)
```

Thresholds:

- Good nominates at `score >= 65`.
- Evil nominates at `score >= 55`.
- Dead Agents never nominate.
- Evil Agents do not nominate the Demon unless no non-Demon candidate exists, and v1 should choose `Noop` rather than sacrificing the Demon.

### Vote

Good:

- Vote yes when target score is high.
- Raise threshold for dead vote to `score >= 80`.
- Vote no when information is weak.

Evil:

- Vote yes on likely good targets if the vote may pass.
- Vote no on Demon.
- May vote yes on a minion only if it protects the Demon, but v1 should prefer no.

Dead-vote behavior:

- Dead Agent may vote no freely.
- Dead Agent votes yes only above the dead-vote threshold.
- Existing vote service consumes `hasDeadVote=false`; strategy should avoid a second yes if the flag is already false.

### Night Choice

Use current role skill legal target data and policy scoring:

- `IMP`: target trusted/informational good players; avoid evil teammates and obvious framed targets.
- `POISONER`: target information roles first: `FORTUNETELLER`, `EMPATH`, `UNDERTAKER`, `MONK`, then high-trust good seats.
- `MONK`: protect trusted information roles; never self when illegal.
- `FORTUNETELLER`: choose one suspicious target and one trusted/low-suspicion target when two targets are legal.
- `BUTLER`: choose the most trusted alive player.
- receive-info roles such as `CHEF` and `EMPATH`: submit empty target choices for receive-info tasks if the task allows it.
- fallback: choose the first legal target to prevent task blockage.

## 12. Role-Specific Context

Extend `ClocktowerAgentPrivateViewServiceImpl` to populate `roleSpecificContext`:

- For evil Agents:
  - `evilTeam`: seat IDs, seat numbers, role codes, and whether each is Demon or Minion.
  - `demonGameSeatId` when known.
- For Spy:
  - grimoire remains in `grimoire`, not duplicated into `roleSpecificContext`.
- For good Agents:
  - no hidden team data.

This context follows legal Trouble Brewing knowledge boundaries for v1 and does not make every evil Agent all-knowing.

## 13. Error Handling

- If no legal intent is available, return `Noop` with a reason and mark the task done.
- If policy selects an illegal intent, the runtime should downgrade to `Noop` and include an `illegalIntent` diagnostic.
- If an action is rejected by current game state, mark task done with `accepted=false` and the rejected code.
- If required entities are missing or corrupted, keep current exception behavior so the worker retry/failure path records the issue.
- JSON parsing failures should use existing Clocktower exception style and not be swallowed.

## 14. Testing Plan

Add focused tests under the existing Clocktower Agent test style:

```text
ClocktowerAgentHeuristicPolicyTests
ClocktowerAgentTaskRuntimeTests
ClocktowerAgentPrivateViewServiceTests
ClocktowerPublicMicServiceTests
```

Required coverage:

- `agentMicTurn_speaksAndFinishes`
- `agentGrabMic_respectsMicAvailability`
- `goodAgentNominatesHighSuspicionTarget`
- `evilAgentAvoidsNominatingDemon`
- `deadAgentCannotNominate`
- `deadAgentUsesDeadVoteOnce`
- `agentNightChoice_usesRoleHeuristic`
- `evilAgentCreatesAndUsesBluffPlan`
- `runtimeWritesDecisionSummary`

Validation commands:

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerAgentHeuristicPolicyTests,ClocktowerAgentTaskRuntimeTests,ClocktowerAgentPrivateViewServiceTests,ClocktowerPublicMicServiceTests test
```

If targeted tests pass, run the broader Agent slice:

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest='top.egon.mario.clocktower.agent.**.*Tests' test
```

## 15. Acceptance Mapping

- Agent speaks or passes on mic turn: `AgentSpeechPlanner` + `AgentIntentExecutor`.
- Agent releases mic: executor submits `FINISH_SPEECH` after accepted speech.
- Agent can grab mic: new `grabMicAsActor` internal path plus `MIC_GRAB_OPENED` scheduling.
- Agent nominates: `AgentNominationPlanner` returns `Nominate` only when legal.
- Agent votes: `AgentVotePlanner` returns `Vote` with open nomination ID.
- Dead Agent cannot nominate: legal intents exclude nomination, planner also guards.
- Dead Agent uses dead vote once: vote service remains authoritative, policy respects `hasDeadVote`.
- Agent chooses legal night targets: legal target IDs come from private view, planner falls back to legal first target.
- Evil Agent bluffs: `TroubleBrewingBluffPlanner` writes and reuses `BLUFF_PLAN` memory.

## 16. Implementation Boundaries

Keep this task scoped to backend strategy/runtime:

- No frontend changes.
- No new Flyway migration.
- No LLM policy.
- No decision audit table.
- No generic timer tick scheduler.
- No cleanup of legacy task docs missing from the worktree.

Each implementation step should be small and committed separately, following the existing task series style.
