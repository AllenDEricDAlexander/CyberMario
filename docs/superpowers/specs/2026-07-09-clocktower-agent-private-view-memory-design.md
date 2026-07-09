# Clocktower Agent Private View Memory Design

**Date:** 2026-07-09

**Status:** User-approved approach A. This spec covers task 11 from `docs/clocktower_agent_tasks`: add an Agent-only private view and a game-scoped memory system before implementing heuristic play in task 12. Implementation must be planned separately before code changes.

---

## 1. Background

Tasks 01 through 10 established the Agent-capable game path:

- `clocktower_game_seat` carries HUMAN and AGENT actors.
- `clocktower_game_event` is the game-scoped event log.
- `ClocktowerGameActionExecutor` is the shared HUMAN / AGENT action entry point.
- Night task resolution emits private `PRIVATE_INFO_RECEIVED` events with `visible_game_seat_ids_json`.
- `ClocktowerAgentRuntime` now consumes queued Agent tasks, but still performs deterministic placeholder actions and does not build a player-like view before acting.

`ClocktowerGameViewServiceImpl` is built for HTTP viewers and resolves a real player from `RbacPrincipal`. Agent runtime has no principal, so it needs a dedicated view service instead of reusing the human viewer resolver.

## 2. Goals

1. Add a new game-scoped `clocktower_agent_memory` table with project-standard audit fields.
2. Build `AgentPrivateView` for Agent runtime and later policy modules.
3. Enforce player-like visibility for normal Agents.
4. Allow Spy Agents to see grimoire-level seat information through a role policy only.
5. Let Agents see their own private night information and not other players' private information.
6. Extract basic memories from visible events:
   - public speech summaries
   - role claims
   - nominations
   - votes
   - deaths
   - private information
7. Update `clocktower_agent_instance.metadata_json.lastSeenEventSeq` after successful memory refresh.
8. Make memory refresh idempotent for repeated runtime handling.
9. Keep task 10's placeholder action behavior intact until task 12 replaces it with heuristic policy.

## 3. Non-Goals

This task does not:

- Implement heuristic play, nominations, voting strategy, or speech planning.
- Add LLM summarization or LLM decisions.
- Add decision audit tables.
- Add frontend UI.
- Add a generic long-term memory subsystem.
- Expose Agent private view as a public HTTP API.
- Rework human `ClocktowerGameViewServiceImpl`.
- Add full hidden marker semantics beyond the existing game seat fields.
- Modify existing Flyway migration files.

## 4. Selected Approach

Use approach A: add dedicated Agent private-view and memory domain services under the existing Clocktower Agent package boundary.

Rejected alternatives:

- Putting the view and memory logic directly in `ClocktowerAgentRuntime` would reduce files but make the runtime too broad before task 12 and task 15.
- Reusing the generic Agent memory schema would not model game, seat, event visibility, source event idempotency, and role-specific information cleanly.
- Reusing `ClocktowerViewerResolver` would require fake principals or actor-type shortcuts and risks leaking storyteller visibility to Agents.

Design pattern decision:

- Use Domain Services for view building and memory refresh because both are game-specific orchestration boundaries.
- Use a small Policy object, `ClocktowerRoleVisionPolicy`, for role-based visibility such as Spy grimoire access. The variation point is role vision, not actor type.
- Use an Extractor boundary, `ClocktowerAgentMemoryExtractor`, for event-to-memory conversion. It is Strategy-compatible for future LLM or script-specific extractors, but v1 has one rule-based implementation.
- Do not introduce factories, chains, or a state machine. Direct services plus one policy and one extractor are sufficient and match the current project style.

## 5. Package Boundary

Create:

```text
top.egon.mario.clocktower.agent.view
  dto
    AgentPrivateView
    AgentPublicSeatView
    AgentVisibleEventView
    AgentPrivateInfoView
    AgentMemoryView
    AgentLegalIntentView
  service
    ClocktowerAgentPrivateViewService
    ClocktowerRoleVisionPolicy
  service.impl
    ClocktowerAgentPrivateViewServiceImpl

top.egon.mario.clocktower.agent.memory
  constant
    ClocktowerAgentMemoryType
  po
    ClocktowerAgentMemoryPo
  repository
    ClocktowerAgentMemoryRepository
  service
    ClocktowerAgentMemoryService
    ClocktowerAgentMemoryExtractor
  service.impl
    ClocktowerAgentMemoryServiceImpl
    RuleBasedClocktowerAgentMemoryExtractor
```

Responsibilities:

- `ClocktowerAgentPrivateViewService` builds the runtime-safe Agent view from game, seat, event, task, and memory data.
- `ClocktowerRoleVisionPolicy` answers role-specific visibility questions. V1 only grants grimoire visibility to `SPY`.
- `ClocktowerAgentMemoryService` refreshes visible event memories and updates `lastSeenEventSeq`.
- `ClocktowerAgentMemoryExtractor` converts visible game events to memory write candidates.
- `ClocktowerAgentRuntime` calls memory refresh before handling each queued task, then continues its current placeholder action switch.

## 6. Database Design

Add exactly one migration:

```text
be/src/main/resources/db/migration/V38__clocktower_agent_memory.sql
```

Create:

```text
clocktower_agent_memory
  id
  game_id
  agent_instance_id
  game_seat_id
  source_event_id
  source_event_seq
  memory_type
  visibility
  subject_game_seat_id
  content_json
  confidence
  day_no
  night_no
  metadata_json
  created_at
  updated_at
  created_by
  updated_by
  version
  deleted
```

Project-standard fields `created_by`, `updated_by`, `version`, and `deleted` are included even though the task doc's sketch omits some of them.

Indexes:

```text
idx_clocktower_agent_memory_agent
  (game_id, agent_instance_id, created_at)
  where deleted = false

idx_clocktower_agent_memory_subject
  (game_id, subject_game_seat_id)
  where deleted = false

uk_clocktower_agent_memory_event
  (game_id, agent_instance_id, source_event_id, memory_type, coalesce(subject_game_seat_id, -1), deleted)
```

The unique key protects against duplicate event-derived memories if the same event window is processed more than once. Task 11 writes only event-derived memories, so `source_event_id` is always present for inserted rows. Memories without a source event are not required for task 11 and can be added by later tasks with a separate key if needed.

Memory types:

```text
PUBLIC_SPEECH_SUMMARY
ROLE_CLAIM
PRIVATE_INFO
NOMINATION_OBSERVATION
VOTE_OBSERVATION
DEATH_OBSERVATION
TRUST_SCORE
SUSPICION_SCORE
BLUFF_PLAN
EVIL_TEAM_INFO
QUESTION_TO_ASK
```

Only the event-derived subset is written in task 11. The rest are reserved so task 12 can use the same table without another schema change.

## 7. AgentPrivateView Contract

Use the task doc shape with v1-safe additions:

```java
public record AgentPrivateView(
        Long gameId,
        Long agentInstanceId,
        Long myGameSeatId,
        int mySeatNo,
        String phase,
        int dayNo,
        int nightNo,
        String myRoleCode,
        String myDisplayedRoleCode,
        String myAlignment,
        String myRoleType,
        String lifeStatus,
        String publicLifeStatus,
        boolean hasDeadVote,
        List<AgentPublicSeatView> publicSeats,
        List<AgentPublicSeatView> grimoire,
        List<AgentVisibleEventView> visibleEvents,
        List<AgentPrivateInfoView> privateInfos,
        List<AgentMemoryView> memories,
        List<AgentLegalIntentView> legalIntents,
        Map<String, Object> roleSpecificContext
) {}
```

`grimoire` is empty for normal Agents. For Spy, it contains full seat facts available in `ClocktowerGameSeatPo`: role code, role type, alignment, life status, public life status, dead vote, traveler, and status. It does not add legacy storyteller notes or hidden marker data.

## 8. Visibility Rules

Normal Agent can see:

- Own true role, displayed role code, alignment, role type, life status, public life status, and dead vote.
- Public seat facts: seat number, display name, public life status, traveler flag, Agent badge if already derivable from actor type, and status.
- `PUBLIC` game events.
- `PRIVATE` game events where `visible_game_seat_ids_json` contains the Agent's own game seat id.
- Own memories.
- Current legal intents derived by backend state.

Normal Agent cannot see:

- Storyteller grimoire.
- Other players' true roles or alignments.
- Other players' private information events.
- Storyteller-only events.
- Audit events.
- Hidden marker truth that is not already part of the Agent's legal role view.

Spy Agent can additionally see:

- Full game seat grimoire data through `ClocktowerRoleVisionPolicy#canSeeGrimoire("SPY")`.

Spy Agent still does not automatically see other players' private event messages. The Spy role grants grimoire vision, not unrestricted access to every `PRIVATE` event.

## 9. Memory Refresh Flow

`ClocktowerAgentRuntime.handle(...)` calls memory refresh after validating that the instance is active and not paused, before executing the task action.

Flow:

1. Load and lock the Agent instance for memory refresh.
2. Read `lastSeenEventSeq` from `metadata_json`; default to 0.
3. Load game events with `event_seq > lastSeenEventSeq`.
4. Filter with Agent visibility rules.
5. Extract memory candidates from visible events.
6. Insert memory rows, relying on deterministic source-event keys for idempotency.
7. Update `metadata_json.lastSeenEventSeq` to the highest evaluated event seq.
8. Save the Agent instance.

If an event is not visible to the Agent, it must not produce memory and must not leak through content JSON. `lastSeenEventSeq` can still advance to the highest processed game event only after the service has evaluated events in order. This prevents invisible old events from being rescanned forever while preserving the rule that only visible events become memories.

The refresh is transactional. If memory writes fail, `lastSeenEventSeq` is not advanced.

## 10. Rule-Based Extraction

V1 extractor is deterministic and intentionally simple:

### PUBLIC_SPEECH

Create `PUBLIC_SPEECH_SUMMARY` with:

- `sourceEventId`
- `actorGameSeatId`
- `contentSnippet`
- `eventSeq`
- `phase`
- `dayNo`
- `nightNo`

The summary is a normalized, length-limited content snippet. No LLM summary is generated.

If the speech contains claim markers such as:

```text
我是
我跳
我声称
claim
```

or contains a known role code / role name from `clocktower_role`, create `ROLE_CLAIM` with actor seat, claimed role text, and confidence.

### NOMINATION_OPENED

Create `NOMINATION_OBSERVATION` with nominator, nominee, nomination id, and current day.

### VOTE_CAST

Create `VOTE_OBSERVATION` with voter, nominee or nomination id when present, vote value, and current day.

### PRIVATE_INFO_RECEIVED

Create `PRIVATE_INFO` only for the receiving Agent. Preserve the role-generated payload in `content_json` after removing fields that are not needed for the Agent view. This records private info such as Washerwoman, Investigator, Fortune Teller, Empath, Undertaker, Chef, Spy, or other night role outputs.

### Death Events

For public death events such as `PLAYER_DIED`, create `DEATH_OBSERVATION` with target seat and current phase.

### Trust and Suspicion

Task 11 creates the memory types and can initialize score maps in `agent_instance.metadata_json`:

```json
{
  "lastSeenEventSeq": 123,
  "suspicion": {},
  "trust": {}
}
```

It does not implement complex score updates. Task 12 owns strategic scoring.

## 11. Legal Intents in View

`AgentPrivateView.legalIntents` is a conservative snapshot, not a policy decision:

- Current mic turn for this Agent: `PUBLIC_SPEECH` and `PASS`.
- Open nomination vote window: `VOTE` intents with the open nomination id and boolean choices.
- Current pending night task for this Agent: `NIGHT_CHOICE` with task id and available targets when already derivable.
- Otherwise empty.

Task 11 does not decide which intent to take. Task 10's placeholder runtime actions remain unchanged.

## 12. Runtime Integration

Modify `ClocktowerAgentRuntime` narrowly:

- Load and validate the Agent instance as it already does.
- Skip memory refresh for inactive, paused, or storyteller-approval modes.
- Call `ClocktowerAgentMemoryService.refreshForRuntimeTask(task)` before the action switch.
- Include small refresh metadata in the runtime result only if useful for tests, such as `memoryRefreshed` or `lastSeenEventSeq`.
- Keep existing PASS, false VOTE, and auto night choice behavior intact.

This keeps task 11 from becoming task 12.

## 13. Error Handling

Use existing `ClocktowerException` style for invalid state:

```text
CLOCKTOWER_AGENT_INSTANCE_INVALID
CLOCKTOWER_AGENT_SEAT_INVALID
CLOCKTOWER_AGENT_MEMORY_JSON_INVALID
CLOCKTOWER_AGENT_MEMORY_EVENT_JSON_INVALID
```

Invalid event payload for one event should fail the refresh transaction rather than partially advancing `lastSeenEventSeq`. That makes data issues visible during tests and prevents silent memory loss.

## 14. Tests

Add focused backend tests for:

- `normalAgentView_hidesGrimoire`
- `spyAgentView_includesGrimoire`
- `agentView_includesOwnPrivateInfo`
- `agentView_hidesOthersPrivateInfo`
- `memoryExtractor_recordsPublicSpeech`
- `memoryExtractor_recordsRoleClaim`
- `memoryExtractor_recordsPrivateInfo`
- `memoryUpdate_isIdempotentByEventSeq`
- `runtime_refreshesMemoryBeforeAction`

Migration validation should confirm exactly one new migration file is added and existing migrations remain unchanged.

## 15. Acceptance Checklist

- Normal Agent private view contains no grimoire.
- Spy Agent private view contains grimoire seat facts through role policy.
- Agent sees own `PRIVATE_INFO_RECEIVED`.
- Agent does not see other seats' `PRIVATE_INFO_RECEIVED`.
- Public speech creates memory.
- Role claim creates memory.
- Agent instance metadata records `lastSeenEventSeq`.
- Reprocessing the same event window does not duplicate memory.
- Runtime still performs task 10 placeholder actions until task 12.

## 16. Open Decisions Resolved

- Migration version is `V38` because `V37__clocktower_agent_task.sql` is currently the latest.
- Audit fields follow project convention.
- Spy grimoire is full game seat data only, not legacy storyteller notes.
- `lastSeenEventSeq`, `trust`, and `suspicion` live in `clocktower_agent_instance.metadata_json` for v1.
- Trust and suspicion scoring is not implemented in this task beyond reserving structure.
