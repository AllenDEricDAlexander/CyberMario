# Clocktower Flow Rule Engine Design

**Date:** 2026-06-18

**Status:** Draft updated after execution/death rules review, awaiting written spec review.

**Goal:** Redesign the Clocktower game flow so the normal storyteller workflow follows the real Blood on the Clocktower loop: setup and role distribution, first night, day discussion, nomination and voting, execution resolution, next night, next day, repeated until game end. The design fixes the current bug where the generic phase-advance action can jump from day directly to night and where night tasks can appear outside night phases.

## 1. Current Problem

The current `ADVANCE_PHASE` action in `ClocktowerGrimoireServiceImpl` is a thin generic action:

- `FIRST_NIGHT` and `NIGHT` advance to `DAY`.
- `DAY` advances directly to `NIGHT`.
- `NOMINATION` advances to `EXECUTION`.
- `EXECUTION` advances to `NIGHT`.
- A caller can pass `targetPhase` to force an arbitrary target.

This produces an invalid user experience:

- The first day can skip nomination and execution.
- A second click can move the room to the second night before the first day is resolved.
- Night checklist generation is not limited to `FIRST_NIGHT` and `NIGHT`, so day pages can still show night pending tasks.
- The UI does not have a clear "what is blocking the next phase" model.

## 2. Product Decisions

The confirmed design decisions are:

- The normal game loop is fixed:
  `SETUP -> FIRST_NIGHT -> DAY -> NOMINATION -> EXECUTION -> NIGHT -> DAY -> NOMINATION -> EXECUTION -> NIGHT ... -> ENDED`.
- The main advance button follows the fixed loop only. It does not accept an ordinary target phase.
- Forced phase changes remain possible, but only through the ruling system with target phase, reason, note, and audit history.
- Night tasks must be completed or skipped before advancing from `FIRST_NIGHT` or `NIGHT` to `DAY`.
- A player nomination in `DAY` automatically moves the room to `NOMINATION` if the nomination is legal.
- Storytellers can also manually move from `DAY` to `NOMINATION`.
- `NOMINATION` supports multiple nominations per day, one open nomination at a time.
- Every player can nominate at most once per day and be nominated at most once per day.
- A player can nominate themselves.
- Dead players cannot nominate and cannot be nominated.
- A live, publicly alive player can vote once on each nomination.
- A real-dead or publicly-dead player has one dead vote for the whole game. This includes false death: a real-alive but publicly-dead player is limited like a dead player to preserve public information.
- The storyteller closes each nomination to lock votes.
- When no one will nominate further, the storyteller advances from `NOMINATION` to `EXECUTION`.
- Execution uses the highest closed nomination of the day, but only if it reaches the alive-player majority threshold and is not tied.
- The majority threshold is `ceil(realAliveCount / 2)`.
- If no nomination reaches threshold, or the highest vote count is tied, nobody is executed.
- Execution or no-execution confirmation is required before advancing to the next night.
- Execution and death are separate concepts. A confirmed execution resolves the day's execution slot; it does not automatically change a player's real or public life status.
- If an execution causes death, the storyteller records an explicit death ruling with a reason. If the executed player survives, only the execution is recorded.
- Core victory checks produce storyteller suggestions, not automatic game end.
- This version does not introduce jBPM, Kogito, or BPMN workflow execution.

## 3. Engine Boundary

This version uses **Java persistent state machine + Drools flow decision rules + storyteller rulings**.

Java owns:

- Database transactions.
- Room phase, day number, and night number persistence.
- Event creation and visibility.
- Seat, nomination, vote, task, and ruling state mutation.
- Authorization and player visibility.

Drools owns deterministic flow decisions:

- Allowed next transition.
- Blocking reasons.
- Nomination legality.
- Vote legality.
- Execution candidate calculation.
- Victory candidate calculation.

The ruling system owns exceptions:

- Forced phase change.
- Execution override.
- No-execution override.
- Death, revival, and public life-state changes.
- Game end confirmation.
- Corrections after mistaken operation.

Drools must not directly write the database, publish events, or expose hidden facts to players.

## 4. Workflow Engine Decision

The project will not introduce jBPM or Kogito in this iteration.

Apache KIE documents jBPM as the KIE workflow/process toolkit for BPMN2 business processes, and Kogito as a cloud-native business automation stack powered by jBPM. Those are valid workflow tools, but they are too heavy for this immediate Clocktower flow fix. The project already has a persistent event model and room state. Adding a process engine would add process-instance lifecycle, persistence, deployment, and operational concerns before the game loop itself is stable.

References:

- [jBPM | Apache KIE](https://kie.apache.org/components/jbpm/)
- [Kogito | Apache KIE](https://kie.apache.org/components/kogito/)

## 5. Phase Semantics

The existing `ClocktowerPhase` enum remains:

```text
LOBBY
SETUP
FIRST_NIGHT
DAY
NOMINATION
EXECUTION
NIGHT
ENDED
```

The semantics become stricter:

| Phase | Meaning | Main allowed flow |
|---|---|---|
| `LOBBY` | Room and seats are being prepared | Role distribution starts the game |
| `SETUP` | Reserved setup state | Start moves into first night |
| `FIRST_NIGHT` | First-night wake order and private storyteller work | Advance to day only after night tasks finish |
| `DAY` | Public discussion | Storyteller or first legal nomination enters nomination |
| `NOMINATION` | Multi-round nomination and voting for the current day | Advance to execution only when no nomination is open |
| `EXECUTION` | Resolve today's execution result | Advance to next night only after execution/no-execution confirmation |
| `NIGHT` | Second and later nights | Advance to next day only after night tasks finish |
| `ENDED` | Game ended by storyteller ruling | No normal advance |

Counters:

- Start game sets `phase=FIRST_NIGHT`, `dayNo=0`, `nightNo=1`.
- `FIRST_NIGHT -> DAY` sets `dayNo=1`, keeps `nightNo=1`.
- `EXECUTION -> NIGHT` increments `nightNo`.
- `NIGHT -> DAY` increments `dayNo`.

## 6. Flow Decision Model

Add a flow decision model returned by the backend. Conceptually:

```text
FlowDecision
  phase
  dayNo
  nightNo
  nextTransition
  advanceAllowed
  blockingReasons[]
  nightTaskSummary
  openNomination
  executionCandidate
  victoryCandidate
```

`nextTransition` examples:

- `COMPLETE_FIRST_NIGHT`
- `START_NOMINATION`
- `START_EXECUTION`
- `START_NIGHT`
- `COMPLETE_NIGHT`
- `NONE`

Blocking reason examples:

- `CLOCKTOWER_NIGHT_TASKS_PENDING`
- `CLOCKTOWER_OPEN_NOMINATION_EXISTS`
- `CLOCKTOWER_EXECUTION_NOT_RESOLVED`
- `CLOCKTOWER_GAME_ALREADY_ENDED`

The UI should render backend decisions rather than duplicate transition rules.

## 7. Night Flow

Night tasks exist only in `FIRST_NIGHT` and `NIGHT`.

Each night step has one storyteller task with status:

- `PENDING`
- `DONE`
- `SKIPPED`

Rules:

- When the room is in `FIRST_NIGHT` or `NIGHT`, the system syncs wake tasks from the script night order and current roles.
- When the room is not in a night phase, pending night wake tasks are not generated or shown as current tasks.
- `FIRST_NIGHT/NIGHT -> DAY` is blocked while any current-night task is `PENDING`.
- A storyteller can mark a task `DONE`.
- A storyteller can mark a task `SKIPPED`, but must provide a reason.
- Player night actions, when implemented, attach to the matching night task or night step.

## 8. Nomination Flow

Nomination can start in two ways:

- Storyteller advances `DAY -> NOMINATION`.
- A player submits the first legal nomination during `DAY`; the service moves the room to `NOMINATION` and creates the nomination in one transaction.

Nomination legality:

- Room phase must be `DAY` or `NOMINATION`.
- Nominator must be real-alive.
- Nominee must be real-alive.
- Self-nomination is allowed.
- The nominator must not have nominated earlier on the same day.
- The nominee must not have been nominated earlier on the same day.
- There must be no open nomination.

Open nomination rules:

- Only one nomination can be open at a time.
- Votes apply only to the current open nomination.
- Storyteller closes the open nomination to lock its votes.
- After closing, the room remains in `NOMINATION` so another legal nomination can begin.
- When nobody will nominate further, the storyteller advances to `EXECUTION`.

## 9. Voting Rules

Voting legality:

- Voting is only allowed in `NOMINATION`.
- There must be an open nomination.
- A player can vote once per nomination.

Vote availability:

- Real-alive and publicly-alive players can vote once in each nomination.
- Real-dead players have one dead vote for the whole game.
- Publicly-dead players also have one dead vote for the whole game, even if real-alive.
- After a real-dead or publicly-dead player votes once, `hasDeadVote=false`.

This intentionally treats false death as vote-limited from the public perspective.

## 10. Execution Resolution

Entering `EXECUTION` freezes nomination for the day.

Execution resolution and death are intentionally modeled as separate facts:

- Execution is a daytime decision that consumes the single execution opportunity for the current day.
- Death is a player life-state change. It can happen because of execution, night abilities, role abilities, or storyteller ruling.
- A player can be executed and remain alive.
- A dead player can be executed again, but cannot die again.
- Victory suggestions use real life status, not whether a player was executed.
- Public death and real death can differ for false-death situations; execution does not change either one by itself.

Execution candidate calculation:

1. Consider only current-day valid closed nominations.
2. Compute threshold as `ceil(realAliveCount / 2)`.
3. If no nomination reaches threshold, result is no execution.
4. If the highest vote count is tied, result is no execution.
5. If exactly one nomination has the highest vote count and reaches threshold, its nominee is the execution candidate.

Storyteller actions in `EXECUTION`:

- If there is a candidate, confirm execution.
- If there is no candidate, confirm no execution.
- A confirmed execution writes an `EXECUTE_PLAYER` ruling/event and marks the source nomination as executed.
- A confirmed execution does not automatically write `MARK_DEAD` and does not mutate `lifeStatus` or `publicLifeStatus`.
- If the execution causes death, the storyteller explicitly applies a `MARK_DEAD` ruling with a reason. The UI may offer this as a checked "同时标记死亡" action, but the backend still records it as a separate ruling.
- If the executed player survives, the storyteller records only the execution and explains the survival in the execution note.
- A confirmed no-execution writes a `SKIP_EXECUTION` ruling/event for the current day. It must not create a synthetic nomination.
- The room cannot advance to `NIGHT` until execution is resolved.
- Special role outcomes such as survival, changed target, extra execution, no-death execution, death after execution, or revival use the ruling system with a reason.

## 11. Victory Suggestions

Drools produces victory candidates after major state changes:

- Execution confirmation.
- Death or revive ruling.
- End of night.
- Start of day.

First-version victory rules:

- If all demons are real-dead, suggest good victory.
- If only two real-alive players remain and a demon is still real-alive, suggest evil victory.

The system does not automatically end the game. It creates or returns a storyteller-visible suggestion. The storyteller ends the game through an `END_GAME` ruling with winner and reason.

## 12. API Design

Add or adjust these endpoints:

| Endpoint | Purpose |
|---|---|
| `GET /api/clocktower/rooms/{roomId}/flow` | Return current flow decision |
| `POST /api/clocktower/rooms/{roomId}/flow/advance` | Advance through the normal state machine |
| `POST /api/clocktower/rooms/{roomId}/night-tasks/{taskId}/skip` | Skip a night task with reason |
| `POST /api/clocktower/rooms/{roomId}/nominations/{nominationId}/close` | Close and lock an open nomination |
| `POST /api/clocktower/rooms/{roomId}/execution/confirm` | Confirm execution or no execution |
| `POST /api/clocktower/rooms/{roomId}/rulings` | Existing ruling API, extended for forced `ADVANCE_PHASE` |

Removal of the old generic phase action:

- Remove `ADVANCE_PHASE` from the old `storytellerAction` command path.
- Normal progression must use `flow/advance`.
- Forced phase changes must use the ruling API with ruling type `ADVANCE_PHASE`.
- If an old client submits `storytellerAction.actionType=ADVANCE_PHASE`, reject it with a clear error such as `CLOCKTOWER_ADVANCE_PHASE_REPLACED_BY_FLOW`.
- Do not map the old action to the new flow service. Keeping both paths would make phase invariants harder to reason about and test.

## 13. Frontend Design

Add a "流程" tab to the storyteller grimoire page.

The flow tab shows:

- Current phase and counters.
- Next action button from backend `nextTransition`.
- Disabled state and blocking reasons.
- Night task summary.
- Open nomination summary.
- Execution candidate or no-execution reason.
- Victory suggestion if present.

Night UI:

- Night checklist is active only in `FIRST_NIGHT` and `NIGHT`.
- Non-night phases do not display current pending night tasks.
- Each night task supports "完成" and "跳过".
- Skipping requires a reason.

Nomination UI:

- Player room shows nomination entry in `DAY` and `NOMINATION`.
- First legal nomination in `DAY` moves the room into `NOMINATION`.
- Storyteller grimoire shows open nomination, current votes, and close action.
- If no nomination is open, the storyteller can advance to execution.

Execution UI:

- Shows candidate, vote count, threshold, source nomination, and tie/no-threshold reason.
- Requires "确认无人处决", "确认处决但不改变生死", or "确认处决并标记死亡".
- "确认处决并标记死亡" creates two audited facts: execution first, death second.
- The UI must display execution and death as distinct concepts so the storyteller can handle survival, already-dead targets, and false-death cases.
- After resolution, the next action becomes "进入下一夜".

Forced phase UI:

- Lives in the ruling form as a secondary/high-risk operation.
- Requires target phase, reason, and note.
- Writes ruling history.

## 14. Data and Migration Notes

The first implementation should prefer the existing tables and should not add a migration unless implementation review proves a field is missing.

The current schema already supports the confirmed first-version flow:

- `clocktower_storyteller_task.status` is a string and can store `PENDING`, `DONE`, and `SKIPPED`.
- `clocktower_storyteller_task.note` can hold skip reasons.
- `clocktower_nomination.day_no` supports daily nomination limits.
- `clocktower_nomination.status` supports `OPEN`, `CLOSED`, and `VOID`.
- `clocktower_nomination.vote_count` supports execution candidate calculation.
- `clocktower_nomination.executed` records which nomination was executed, but does not imply death.
- `clocktower_ruling` and `clocktower_event` record current-day execution/no-execution resolution without synthetic nominations.
- `clocktower_vote.used_dead_vote` records dead-vote spending.
- `clocktower_vote` already enforces one vote per nomination per voter through its unique constraint.
- `clocktower_seat.has_dead_vote` tracks whether a player still has their one dead vote.
- Flow decisions are assembled from existing room, seat, task, nomination, vote, event, and ruling data; they do not need a dedicated persistence table.

If implementation discovers an unavoidable schema gap, add exactly one new Flyway migration. Do not modify existing migrations.

## 15. Testing Plan

Backend tests:

- Start game enters `FIRST_NIGHT dayNo=0 nightNo=1`.
- First-night advance is blocked while current night tasks are pending.
- First-night advance succeeds after all tasks are done or skipped.
- First-night completion enters `DAY dayNo=1 nightNo=1`.
- `DAY -> NOMINATION -> EXECUTION -> NIGHT -> DAY` counters are correct.
- Night tasks are generated only in `FIRST_NIGHT` and `NIGHT`.
- Skipping a night task requires a reason.
- Dead players cannot nominate or be nominated.
- One player cannot nominate twice in one day.
- One player cannot be nominated twice in one day.
- A self-nomination is legal when the player is alive and not already nominated that day.
- An open nomination blocks a new nomination.
- Closing a nomination locks its votes.
- Real-dead and publicly-dead players can spend only one dead vote across the game.
- False-dead players are dead-vote limited.
- Execution candidate is absent below threshold.
- Execution candidate is absent on top-vote tie.
- Execution candidate is present when top vote is unique and reaches threshold.
- Confirming execution without death leaves real and public life status unchanged.
- Confirming execution with death records execution and death separately.
- Confirming no execution records current-day resolution without creating a fake nomination.
- Execution must be resolved before entering night.
- Demon death suggests good victory.
- Two real-alive players with a living demon suggests evil victory.
- Victory suggestion does not automatically end the game.

Frontend tests:

- Flow tab renders current phase, next action, and blocking reasons.
- Pending night tasks disable advance.
- Non-night phases do not show night pending tasks as current work.
- Nomination phase renders open nomination and close action.
- Execution phase renders candidate or no-execution reason.
- Execution confirmation unlocks next-night transition.
- Forced phase change is available only through ruling UI and requires note/reason.

## 16. Out of Scope

This design does not implement:

- Full role ability automation.
- Trouble Brewing character-specific information generation.
- jBPM/Kogito workflow execution.
- BPMN process authoring.
- Agent autoplay changes.
- Full UI polish for all edge cases.
- Automatic game end.

Those should be separate specs or follow-up plans after the core flow is stable.

## 17. Acceptance Criteria

The feature is accepted when:

- A storyteller can run this normal loop without invalid jumps:
  `FIRST_NIGHT -> DAY -> NOMINATION -> EXECUTION -> NIGHT -> DAY`.
- The first day cannot jump directly to the second night.
- Night tasks block daybreak until done or skipped.
- Day pages no longer show current pending night tasks.
- Nomination and voting enforce the confirmed daily limits and dead-vote rules.
- Execution result follows threshold and tie rules.
- Execution and death are separate: a player can be executed without becoming dead.
- Death and revival happen through explicit rulings with reasons.
- Execution/no-execution confirmation is required before next night.
- Forced phase changes are audited as rulings with reasons.
- Tests cover the core flow, nomination, vote, execution, and UI blocking behavior.
