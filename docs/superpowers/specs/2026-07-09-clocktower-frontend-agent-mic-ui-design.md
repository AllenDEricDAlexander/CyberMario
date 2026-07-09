# Clocktower Frontend Agent Mic UI Design

## Context

Task 13 connects the frontend to the actor-agent game runtime already introduced by tasks 03 through 12. The backend now exposes Agent-aware room seats, new game views, game action submission, public mic sessions, nomination/vote execution, night tasks, Agent private views, and heuristic Agent play. The frontend still has several old assumptions:

- Lobby occupancy and start readiness are mostly derived from `seat.userId`, so Agent seats can look empty.
- `ClocktowerRoomPlayPage` loads the new `gameView`, but player action controls are disabled.
- `GameRoomSurface` still submits actions through the legacy room action endpoint.
- The frontend has no public mic session panel, holder countdown, grab-mic countdown, or mic-aware speech input.

The task is frontend-only. No database migration and no new backend endpoint are required.

## Goals

- Show Agent seats correctly in the lobby and count them as occupied seats for start readiness.
- Keep Agent seats visually distinct from human users and avoid treating Agents as IM users.
- Submit new game actions through `/api/clocktower/games/{gameId}/actions` when rendering a new `gameView`.
- Show public mic state: round-robin, grab-mic, closed, current holder, queue, current turn countdown, and grab window countdown.
- Let the current human mic holder submit `PUBLIC_SPEECH` and finish or release the mic.
- Show disabled speech input states when the viewer is not the mic holder.
- Preserve legacy room action behavior for old room view code.

## Non-Goals

- Do not change Agent decision logic.
- Do not build the full Storyteller Agent control console; task 14 owns that surface.
- Do not add private chat or add Agents to IM conversation members.
- Do not replace all old Clocktower room action UI. Only new `gameView` action submission is switched to the new game API.
- Do not introduce browser-only visual QA in this design step.

## Chosen Approach

Use方案 A: progressively adapt existing frontend surfaces.

This keeps the blast radius small. `RoomLobbyPage`, `ClocktowerSeatGrid`, and `GameRoomSurface` already own the relevant workflows, so the design adds Agent-aware helpers, a `useGameActionApi` path, and a focused `PublicMicPanel` rather than duplicating the entire game page into a new component. This is safer for task 13 because old room play and existing chat/timeline rendering remain intact.

Alternative B, a new `ClocktowerGameSurface`, would create cleaner long-term separation but would duplicate seat, event, chat, identity, and action layout now. Alternative C, service-only plus lobby-only changes, would be too small and would miss the public mic acceptance criteria.

## Type Contract

Extend `fe/src/modules/clocktower/clocktowerTypes.ts` with frontend types matching the existing backend contract:

- `ClocktowerActorType = 'HUMAN' | 'AGENT' | 'STORYTELLER' | 'SYSTEM'`
- Room seat fields: `actorType`, `actorId`, `agentInstanceId`, `isAgent`
- Game seat fields: `actorType`, `actorId`, `agentInstanceId`, `isAgent`
- `ClocktowerGameActionRequest` with `actorGameSeatId`, `actionType`, `targetGameSeatIds`, `nominationId`, `vote`, `content`, and `payload`
- `ClocktowerGameActionResponse` with `accepted`, `rejectedCode`, and a game event
- `ClocktowerMicSessionView`, `ClocktowerMicTurnView`, and `ClocktowerMicExtendRequest`

The existing legacy `ClocktowerPlayerActionRequest` and `ClocktowerActionResponse` remain unchanged.

## Service Contract

Add to `clocktowerService.ts`:

- `submitClocktowerGameAction(gameId, request)` posts to `/api/clocktower/games/{gameId}/actions`.
- `getClocktowerMicSession(gameId)` gets `/api/clocktower/games/{gameId}/mic`.
- `startClocktowerDayMic(gameId)` posts `/api/clocktower/games/{gameId}/mic/start-day`.
- `finishClocktowerMicTurn(gameId, turnId)` posts `/api/clocktower/games/{gameId}/mic/turns/{turnId}/finish`.
- `skipClocktowerMicTurn(gameId, turnId)` posts `/api/clocktower/games/{gameId}/mic/turns/{turnId}/skip`.
- `grabClocktowerMic(gameId)` posts `/api/clocktower/games/{gameId}/mic/grab`.
- `releaseClocktowerMic(gameId)` posts `/api/clocktower/games/{gameId}/mic/release`.
- `extendClocktowerMicSession(gameId, seconds)` posts `/api/clocktower/games/{gameId}/mic/extend`.
- `closeClocktowerMicSession(gameId)` posts `/api/clocktower/games/{gameId}/mic/close`.

All functions use the existing `requestJson` wrapper and return typed responses. Existing room action functions are retained for legacy surfaces.

## Lobby Design

Introduce shared frontend helpers near `RoomLobbyPage` or in a small local utility file:

- `isAgentSeat(seat)` is true when `actorType === 'AGENT'`, `isAgent` is true, or `agentInstanceId` exists.
- `hasPlayer(seat)` is true when `seat.userId` exists or the seat is an Agent seat.
- `canStartSeat(seat)` is true when the seat has a human/Agent actor, has a nonblank role, and is ready.

`roomLobbyCounts` should count `hasPlayer(seat)`, not only `userId`.

`canStartClocktowerRoom` should keep the existing room status, reservation, and seat-count rules, but replace the occupancy check with `hasPlayer(seat)`. Agent seats still need role assignment and `ready === true` when the backend provides a ready field.

`ClocktowerSeatGrid` should render Agent seats as occupied/ready rather than open. It should show a light `Agent` badge and an `自动` badge. It must not show Agent seats as online IM users. The claim button stays disabled for Agent seats because they are already occupied actors.

## Game Surface Design

`ClocktowerRoomPlayPage` should pass player `gameView` into `GameRoomSurface` with:

- `actionControlsEnabled={gameView.viewerMode === 'PLAYER'}`
- `useGameActionApi`

`GameRoomSurface` keeps its existing legacy behavior by default. When `useGameActionApi` is true:

- `VotePanel` submissions call `submitClocktowerGameAction`.
- The actor id is `view.mySeat.gameSeatId`.
- Target ids use game seat ids, not room seat ids.
- Accepted game events are mapped through the existing `mapGameEvent` helper and appended to local event state.

`mapGamePublicSeats` should preserve the game seat id as `seatId` for new game-action use. It can still expose room seat id only through a separate field if a consumer needs it later.

## Public Mic Panel

Add `fe/src/modules/clocktower/components/PublicMicPanel.tsx`.

Inputs:

- `gameId`
- `viewerMode`
- `myGameSeatId`
- `seats`
- optional `onEventAccepted`
- optional `onMicChanged`

State and behavior:

- Loads `ClocktowerMicSessionView` with `getClocktowerMicSession`.
- Stores session locally and refreshes it after every mic operation.
- Uses a one-second interval to recompute remaining time from `currentTurn.expiresAt` and `grabEndsAt`.
- Displays session status as round-robin, grab-mic, or closed.
- Displays current holder as seat number, display name, and Agent badge when applicable.
- Displays queue from `session.turns` sorted by `turnOrder`; pending/active/done/skipped states are visible.

Controls:

- `抢麦`: enabled for player viewers when status is `GRAB_MIC`, there is no current holder, and the grab window has not expired.
- `说完了`: enabled when the viewer is the current holder and `currentTurnId` exists.
- `释放麦`: enabled when the viewer is the current holder.
- `ST 跳过`: enabled for storyteller viewers when `currentTurnId` exists.
- `ST 延长 2 分钟`: enabled for storyteller viewers during `GRAB_MIC`.

Errors use `reportGlobalError`; accepted actions refresh the mic session. Button loading should be local to the action being run.

## Public Speech Input

`GameRoomSurface` should show a `PUBLIC_SPEECH` input for player viewers when using the game action API. This input is mic-aware:

- Enabled only when `view.mySeat.gameSeatId === micSession.currentHolderGameSeatId`.
- Disabled with text `等待麦序` when another seat holds the mic.
- Disabled with text `抢麦后发言` when grab-mic is open without a holder.
- Disabled with text `当前没有公聊麦序` when no mic session exists or the session is closed.
- Maximum length is `1000`, matching the backend action executor.

Submit payload:

```ts
submitClocktowerGameAction(gameId, {
  actorGameSeatId: view.mySeat.gameSeatId,
  actionType: 'PUBLIC_SPEECH',
  targetGameSeatIds: [],
  nominationId: null,
  vote: null,
  content,
  payload: {},
})
```

After accepted speech, append the returned game event locally and clear the input. The user manually clicks `说完了`; the frontend should not automatically finish the turn for human players.

## Agent Badges

Create a small reusable render helper, or keep a local helper if the call sites are few:

- Room seats: show `Agent` and `自动`.
- Game public seats: show `Agent` near the name.
- Mic holder and queue rows: show `Agent` for Agent actor turns.
- Event rows may display the badge when the actor seat can be resolved from game seats.

Do not add Agents to `ClocktowerChatPanel` members. Public speeches from Agents are game events, not IM messages.

## Data Flow

Lobby:

```text
enterClocktowerRoom -> RoomLobbyPage state -> Agent-aware seat helpers -> start button and grid
```

New game action:

```text
ClocktowerRoomPlayPage -> getClocktowerGameView -> GameRoomSurface(useGameActionApi)
  -> submitClocktowerGameAction -> ClocktowerGameActionResponse
  -> mapGameEvent -> local event timeline
```

Public mic:

```text
PublicMicPanel -> getClocktowerMicSession -> local session
  -> grab/finish/release/skip/extend
  -> refresh session
```

Public speech:

```text
Mic session + myGameSeatId -> speech input enabled state
  -> submitClocktowerGameAction(PUBLIC_SPEECH)
  -> append game event -> user manually finishes mic turn
```

## Error Handling

- Service calls use the existing `requestJson` behavior.
- UI action failures call `reportGlobalError`.
- Rejected game actions show `message.warning` with the backend `rejectedCode`.
- If mic session loading fails because a session does not exist yet, the panel should show an empty/disabled state rather than blocking the whole game page.
- The panel should not infer permission from labels alone; it uses `viewerMode`, `myGameSeatId`, `currentHolderGameSeatId`, and session status.

## Testing

Frontend tests should cover the contract and behavior without starting the app:

- `RoomLobbyPage.test.tsx`
  - Agent seats count as occupied in `roomLobbyCounts`.
  - `canStartClocktowerRoom` accepts ready Agent seats without `userId`.
  - Agent seat markup contains `Agent` and `自动`.
- `clocktowerService.test.ts`
  - `submitClocktowerGameAction` uses `/api/clocktower/games/{gameId}/actions`.
  - Mic service functions use the expected `/mic` endpoints.
- `ClocktowerRoomPlayPage.test.tsx` or `GameRoomPage.test.tsx`
  - New game player view renders action controls.
  - New game action submissions use game endpoint and `actorGameSeatId`.
  - Public speech input is disabled when the viewer is not the mic holder.
  - Grab button is visible/enabled during grab stage.
- Optional targeted component test for `PublicMicPanel` if existing test utilities make async service mocking straightforward.

Validation commands:

```bash
bun run test -- RoomLobbyPage.test.tsx ClocktowerRoomPlayPage.test.tsx clocktowerService.test.ts
bun run typecheck
```

## Acceptance Mapping

- Creating a room with `agentSeatCount=4` shows 4 Agent seats: lobby type fields and seat grid badges.
- Start button treats Agent seats as occupied and ready: `hasPlayer` and `canStartSeat`.
- New game view submits player actions through `/games/{gameId}/actions`: `useGameActionApi`.
- Speech input is disabled without the mic: mic-aware public speech input.
- Round-robin queue and current speaker are visible: `PublicMicPanel`.
- Grab-mic countdown is visible: `grabEndsAt` countdown.
- Agent speech has Agent badge: seat and event actor resolution.
- Old room action page is not broken: legacy `submitClocktowerPlayerAction` path remains default.

## Implementation Boundary

The implementation should be split into small commits in the next plan:

1. Type and service contracts.
2. Lobby Agent seat display and start readiness.
3. Game action API wiring.
4. Public mic panel and speech input.
5. Focused frontend tests and typecheck.

The next step is to write the implementation plan after this spec is reviewed.
