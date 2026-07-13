# Clocktower GAME_V2 Access And Flow Repair Design

**Date:** 2026-07-13

**Goal:** Restore the storyteller GAME_V2 control path so authorized room owners can load agent and night-task controls, advance a running game from night to day, and use the public-mic UI without phase-invalid requests.

**Status:** User approved the recommended repair boundary in chat. This document records that design for review before implementation planning.

---

## 1. Confirmed Problem

The current failure is a combination of authorization registration drift and incomplete frontend orchestration.

- `GET /api/clocktower/games/{gameId}/agents` and the related control endpoints are implemented, but no RBAC API rule matches them.
- `GET /api/clocktower/games/{gameId}/night-tasks` and its storyteller operations are implemented, but no RBAC API rule matches them.
- The GAME_V2 flow, nomination-close, and execution-resolve controllers have the same registration gap.
- Public-mic routes are registered correctly, so their requests enter the service and expose the second problem: game 1 is still in `FIRST_NIGHT` with `dayNo=0` and has no day mic session.
- The storyteller frontend exposes an always-enabled “开启白天麦序” button and queries the mic session without considering the game phase.
- The storyteller “流程” tab is static and does not call the GAME_V2 flow API, so the UI cannot perform the required `FIRST_NIGHT -> DAY` transition.

For the current local data, game 1 belongs to room 4, user 1 (`admin`) is both room owner and storyteller, and the user has the `SUPER_ADMIN` role. The service-level owner check therefore passes for this game. The 403 responses occur before the controller because the dynamic authorization manager denies paths that match no API rule.

## 2. Approaches Considered

### Approach A: Repair canonical RBAC rules and wire phase-driven GAME_V2 UI

Extend the existing centralized Clocktower RBAC provider with the missing GAME_V2 routes. Keep instance-level storyteller authorization as the existing room-owner policy. Add a focused game-flow panel and make mic loading and actions depend on the current phase.

Advantages:

- Fixes the actual authorization and orchestration defects at their source.
- Preserves least-privilege and existing room ownership semantics.
- Reuses the existing `ClocktowerGameFlowService` transition and automatic mic-session creation.
- Requires no database schema change or new framework.

This is the selected approach.

### Approach B: Add a global `SUPER_ADMIN` authorization bypass

Allow `SUPER_ADMIN` to pass unmatched routes and bypass room ownership checks.

Rejected because it hides missing API registrations, weakens deny-by-default behavior, and grants operational room control beyond the current audit-oriented administrator boundary. It would also leave normal storyteller accounts broken.

### Approach C: Suppress the frontend errors only

Hide 403 and mic-session errors or disable the current controls without fixing backend routing.

Rejected because the game would remain unable to advance through the intended GAME_V2 workflow, and direct API consumers would still fail.

## 3. Backend Design

### 3.1 RBAC Route Registration

Keep the existing permission model and extend the canonical provider instead of adding one-off database records.

- `GET /api/clocktower/games/{gameId}/flow` maps to `api:clocktower:game:read`, matching the service contract that resolves any authorized game viewer.
- Mutating flow endpoints map to `api:clocktower:game:storyteller`.
- Agent console, night-task control, nomination close, and execution resolve endpoints map to `api:clocktower:game:storyteller`.
- Public-mic rules remain unchanged because they already distinguish read, player, and storyteller operations.
- Legacy room-scoped rules remain supported during the existing cutover window.

The canonical rules must cover:

```text
GET  /api/clocktower/games/{gameId}/flow
POST /api/clocktower/games/{gameId}/flow/advance
POST /api/clocktower/games/{gameId}/flow/force-advance
ANY  /api/clocktower/games/{gameId}/agents/**
ANY  /api/clocktower/games/{gameId}/night-tasks/**
POST /api/clocktower/games/{gameId}/nominations/{nominationId}/close
POST /api/clocktower/games/{gameId}/executions/resolve
```

Resource synchronization will update the provider-managed API definitions on application startup. No Flyway migration is required because there is no schema or manually managed data change.

### 3.2 Instance-Level Authorization

Do not introduce a super-admin bypass in this repair. Storyteller mutation services continue to call `ClocktowerRoomAccessPolicy.requireOwner(...)`.

This keeps two distinct authorization layers:

1. RBAC decides whether the account may use the API category.
2. The room policy decides whether the account may operate this concrete room/game.

For game 1, the current admin account satisfies both after the API rules are repaired.

### 3.3 Flow And Mic Lifecycle

The existing `ClocktowerGameFlowService` remains authoritative.

```text
FIRST_NIGHT
  -> POST /games/{gameId}/flow/advance
  -> validate mandatory night tasks are complete
  -> set phase=DAY and dayNo=1
  -> create the day public-mic session automatically
  -> return the updated flow
```

The frontend must not use `/mic/start-day` to bypass a night phase. That endpoint remains an idempotent day-phase recovery/control endpoint and continues to reject non-`DAY` games.

## 4. Frontend Design

### 4.1 GAME_V2 Flow Client

Add TypeScript contracts matching the backend `ClocktowerGameFlowView`, `ClocktowerGameAdvanceRequest`, and `ClocktowerGameAdvanceResult` records. Add service functions for:

- `GET /api/clocktower/games/{gameId}/flow`
- `POST /api/clocktower/games/{gameId}/flow/advance`

`force-advance` remains an API-only emergency operation in this repair. It will be authorized but will not receive a normal UI button because forced state changes require an explicit reason and are outside the reported failure.

### 4.2 Storyteller Flow Panel

Replace the static flow-tab content with a focused `StorytellerGameFlowPanel` component following the existing agent, mic, and night-task panel structure.

The panel displays:

- current status, phase, day number, and night number;
- next phase;
- blocking reasons;
- relevant counters;
- a normal “推进流程” action enabled only when `advanceAllowed` is true.

After a successful advance, the panel refreshes its flow view and asks the parent page to reload the game view so every tab receives the new phase.

### 4.3 Phase-Aware Mic Panel

Pass the current game phase into `StorytellerMicControlPanel`.

- During `FIRST_NIGHT` and `NIGHT`, do not query the day mic session and disable the start action.
- During `DAY`, load the current mic session and allow the storyteller to start one only when necessary.
- The automatic flow transition should normally create the session, so manual start is retained as an idempotent recovery control.
- Existing skip, extend, and close rules continue to depend on the current session state.

The empty state explains that public mic is available during the day instead of reporting `CLOCKTOWER_MIC_SESSION_NOT_FOUND` during a night phase.

## 5. Error Handling

- Unmatched GAME_V2 routes must be prevented by provider matcher tests rather than handled as runtime 403 errors.
- Backend domain errors remain explicit and unchanged.
- The frontend reports genuine flow and mic failures through the existing global error mechanism.
- Expected night-phase absence of a mic session is represented as UI state, not an error request.
- Parent reload failures remain visible and do not pretend that the phase changed locally.

## 6. Testing Strategy

### Backend

- Add provider matcher assertions for every missing GAME_V2 route and its expected permission code.
- Add negative assertions where useful so player-readable flow access does not accidentally grant storyteller mutations.
- Keep existing service tests for owner enforcement, phase validation, night tasks, agents, and automatic mic creation.

### Frontend

- Add service tests for the GAME_V2 flow URLs and request bodies.
- Add flow-panel tests for loading, blocked state, successful advance, and parent reload callback.
- Add mic-panel tests proving night phases do not query/start a day mic and DAY preserves current behavior.
- Update storyteller surface/page tests for the real flow panel and reload propagation.

### Verification

- Run focused backend RBAC, game-flow, night-task, agent-control, and public-mic tests.
- Run focused frontend service, flow-panel, mic-panel, storyteller-page, and room-play-page tests.
- Run frontend typecheck.
- Run the full backend and frontend suites if focused verification remains green.

## 7. Scope And Compatibility

In scope:

- GAME_V2 RBAC route repair.
- Storyteller normal flow advancement UI.
- Phase-aware public-mic control UI.
- Regression tests that cover the missing integration seams.

Out of scope:

- Global super-admin room-operation bypass.
- Changes to database schema or existing Flyway migrations.
- Reworking game rules, role behavior, agent strategy, or mic timing.
- Removing the legacy room-scoped Clocktower flow during this repair.
- Adding a force-advance UI.

No new design pattern is required. The existing centralized Resource Provider, domain-service orchestration, and focused React panel structure already provide the right extension points; direct changes at those seams are smaller and safer than adding new abstractions.

## 8. Acceptance Criteria

- The RBAC matcher resolves agents, night tasks, flow, nomination close, and execution resolve GAME_V2 paths to the intended permission codes.
- The current room owner with storyteller permissions can call the repaired endpoints without a pre-controller 403.
- A non-owner still fails the existing service-level storyteller check.
- A `FIRST_NIGHT` game with no pending mandatory tasks can advance to `DAY` from the storyteller UI.
- Advancing to `DAY` creates or returns the day mic session through the existing backend flow.
- The mic panel does not query or start a day session during a night phase.
- Existing Clocktower behavior and unrelated modules remain unchanged.
