# Clocktower Frontend Agent Mic UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Connect the Clocktower frontend to Agent-aware lobby seats, new game actions, public mic state, mic-gated public speech, and Agent badges.

**Architecture:** Keep the existing lobby and game surfaces, add small Agent-aware helpers, and introduce a focused public mic component. Preserve legacy room action behavior by making the new `/games/{gameId}/actions` path opt-in through `useGameActionApi`.

**Tech Stack:** React 19, TypeScript 6, Ant Design 6, Vitest, existing `requestJson` service wrapper, existing Clocktower frontend module.

---

## File Structure

Create:

- `fe/src/modules/clocktower/components/PublicMicPanel.tsx`: loads and renders public mic state, mic controls, and mic-aware public speech input.
- `fe/src/modules/clocktower/components/PublicMicPanel.test.tsx`: pure rendering and state-rule coverage for mic panel and speech input.

Modify:

- `fe/src/modules/clocktower/clocktowerTypes.ts`: add Actor, game action, mic, and Agent seat fields.
- `fe/src/modules/clocktower/clocktowerService.ts`: add game action and mic API functions.
- `fe/src/modules/clocktower/clocktowerService.test.ts`: assert new endpoint contracts.
- `fe/src/modules/clocktower/RoomLobbyPage.tsx`: Agent-aware lobby counts and start readiness helpers.
- `fe/src/modules/clocktower/RoomLobbyPage.test.tsx`: Agent seat readiness/count tests.
- `fe/src/modules/clocktower/components/ClocktowerSeatGrid.tsx`: Agent-aware seat states and badges.
- `fe/src/modules/clocktower/GameRoomPage.tsx`: optional game action API path, game-seat target mapping, Agent badges, and public mic panel composition.
- `fe/src/modules/clocktower/GameRoomPage.test.tsx`: new game action and mic UI rendering coverage.
- `fe/src/modules/clocktower/ClocktowerRoomPlayPage.tsx`: enable actions for new player game view and pass `useGameActionApi`.
- `fe/src/modules/clocktower/ClocktowerRoomPlayPage.test.tsx`: update player view expectation from disabled legacy controls to new game controls.

No backend files and no Flyway migration are expected.

## Design Pattern Decision

No additional design pattern is needed. The task is mostly frontend contract adaptation and a focused UI component. A direct helper/component split is simpler and safer than Strategy, Factory, or a full new `ClocktowerGameSurface` abstraction. The boundary that matters is endpoint selection: legacy room actions stay default, while new game actions are opt-in through `useGameActionApi`.

### Task 1: Type And Service Contracts

**Files:**
- Modify: `fe/src/modules/clocktower/clocktowerTypes.ts`
- Modify: `fe/src/modules/clocktower/clocktowerService.ts`
- Modify: `fe/src/modules/clocktower/clocktowerService.test.ts`

- [ ] **Step 1: Write failing service endpoint tests**

Add the new imports in `clocktowerService.test.ts`:

```ts
import {
    closeClocktowerMicSession,
    extendClocktowerMicSession,
    finishClocktowerMicTurn,
    getClocktowerMicSession,
    grabClocktowerMic,
    releaseClocktowerMic,
    skipClocktowerMicTurn,
    startClocktowerDayMic,
    submitClocktowerGameAction,
} from './clocktowerService'
```

Add these tests inside `describe('clocktowerService', () => { ... })`:

```ts
    it('submits new game actions through the game endpoint', async () => {
        const {requestJson} = await import('../../services/request')
        const request = {
            actorGameSeatId: 31,
            actionType: 'PUBLIC_SPEECH',
            targetGameSeatIds: [],
            nominationId: null,
            vote: null,
            content: '我先报信息。',
            payload: {},
        }

        await submitClocktowerGameAction(11, request)

        expect(requestJson).toHaveBeenCalledWith('/api/clocktower/games/11/actions', {
            method: 'POST',
            body: request,
        })
    })

    it('uses game mic endpoints for public mic actions', async () => {
        const {requestJson} = await import('../../services/request')

        await getClocktowerMicSession(11)
        await startClocktowerDayMic(11)
        await finishClocktowerMicTurn(11, 91)
        await skipClocktowerMicTurn(11, 92)
        await grabClocktowerMic(11)
        await releaseClocktowerMic(11)
        await extendClocktowerMicSession(11, 120)
        await closeClocktowerMicSession(11)

        expect(requestJson).toHaveBeenCalledWith('/api/clocktower/games/11/mic')
        expect(requestJson).toHaveBeenCalledWith('/api/clocktower/games/11/mic/start-day', {method: 'POST'})
        expect(requestJson).toHaveBeenCalledWith('/api/clocktower/games/11/mic/turns/91/finish', {method: 'POST'})
        expect(requestJson).toHaveBeenCalledWith('/api/clocktower/games/11/mic/turns/92/skip', {method: 'POST'})
        expect(requestJson).toHaveBeenCalledWith('/api/clocktower/games/11/mic/grab', {method: 'POST'})
        expect(requestJson).toHaveBeenCalledWith('/api/clocktower/games/11/mic/release', {method: 'POST'})
        expect(requestJson).toHaveBeenCalledWith('/api/clocktower/games/11/mic/extend', {
            method: 'POST',
            body: {seconds: 120},
        })
        expect(requestJson).toHaveBeenCalledWith('/api/clocktower/games/11/mic/close', {method: 'POST'})
    })
```

- [ ] **Step 2: Run service tests to verify they fail**

Run from `fe/`:

```bash
bun run test -- clocktowerService.test.ts
```

Expected: FAIL because the imported service functions do not exist.

- [ ] **Step 3: Add frontend type contracts**

In `clocktowerTypes.ts`, add this near the existing Clocktower enum-like types:

```ts
export type ClocktowerActorType = 'HUMAN' | 'AGENT' | 'STORYTELLER' | 'SYSTEM'
```

Extend `ClocktowerRoomSeatResponse`:

```ts
export type ClocktowerRoomSeatResponse = {
    seatId: number
    seatNo: number
    userId?: number | null
    actorId?: number | null
    actorType?: ClocktowerActorType | null
    agentInstanceId?: number | null
    isAgent?: boolean
    displayName: string
    roleCode?: string | null
    roleType?: ClocktowerRoleType | null
    lifeStatus: string | null
    publicLifeStatus: string | null
    connected: boolean
    hasDeadVote: boolean
    status?: string | null
    ready?: boolean
}
```

Extend `ClocktowerGameSeatResponse`:

```ts
export type ClocktowerGameSeatResponse = {
    gameSeatId: number
    roomSeatId: number
    seatNo: number
    userId?: number | null
    actorId?: number | null
    actorType?: ClocktowerActorType | null
    agentInstanceId?: number | null
    isAgent?: boolean
    displayName: string
    roleCode?: string | null
    roleType?: string | null
    alignment?: string | null
    lifeStatus: string
    publicLifeStatus: string
    hasDeadVote: boolean
    traveler: boolean
    status: string
}
```

Add the new action and mic types after `ClocktowerGameEventResponse`:

```ts
export type ClocktowerGameActionRequest = {
    actorGameSeatId: number
    actionType: string
    targetGameSeatIds: number[]
    nominationId?: number | null
    vote?: boolean | null
    content?: string | null
    payload?: Record<string, unknown> | null
}

export type ClocktowerGameActionResponse = {
    accepted: boolean
    rejectedCode?: string | null
    event?: ClocktowerGameEventResponse | null
}

export type ClocktowerMicTurnView = {
    turnId: number
    gameSeatId: number
    seatNo?: number | null
    displayName?: string | null
    actorType?: ClocktowerActorType | null
    agentInstanceId?: number | null
    turnOrder: number
    stage: string
    acquisitionType: string
    status: string
    startedAt?: string | null
    endedAt?: string | null
    expiresAt?: string | null
}

export type ClocktowerMicSessionView = {
    sessionId: number
    gameId: number
    dayNo?: number | null
    status: 'ROUND_ROBIN' | 'GRAB_MIC' | 'CLOSED' | string
    currentHolderGameSeatId?: number | null
    currentTurnId?: number | null
    roundStartedAt?: string | null
    roundFinishedAt?: string | null
    grabStartedAt?: string | null
    grabEndsAt?: string | null
    closedAt?: string | null
    turns: ClocktowerMicTurnView[]
}

export type ClocktowerMicExtendRequest = {
    seconds: number
}
```

- [ ] **Step 4: Add service functions**

In `clocktowerService.ts`, add these imports to the existing type import list:

```ts
    ClocktowerGameActionRequest,
    ClocktowerGameActionResponse,
    ClocktowerMicSessionView,
```

Add these functions after `submitClocktowerPlayerAction`:

```ts
export function submitClocktowerGameAction(gameId: number, request: ClocktowerGameActionRequest) {
    return requestJson<ClocktowerGameActionResponse>(`/api/clocktower/games/${gameId}/actions`, {
        method: 'POST',
        body: request,
    })
}

export function getClocktowerMicSession(gameId: number) {
    return requestJson<ClocktowerMicSessionView>(`/api/clocktower/games/${gameId}/mic`)
}

export function startClocktowerDayMic(gameId: number) {
    return requestJson<ClocktowerMicSessionView>(`/api/clocktower/games/${gameId}/mic/start-day`, {method: 'POST'})
}

export function finishClocktowerMicTurn(gameId: number, turnId: number) {
    return requestJson<ClocktowerMicSessionView>(`/api/clocktower/games/${gameId}/mic/turns/${turnId}/finish`, {
        method: 'POST',
    })
}

export function skipClocktowerMicTurn(gameId: number, turnId: number) {
    return requestJson<ClocktowerMicSessionView>(`/api/clocktower/games/${gameId}/mic/turns/${turnId}/skip`, {
        method: 'POST',
    })
}

export function grabClocktowerMic(gameId: number) {
    return requestJson<ClocktowerMicSessionView>(`/api/clocktower/games/${gameId}/mic/grab`, {method: 'POST'})
}

export function releaseClocktowerMic(gameId: number) {
    return requestJson<ClocktowerMicSessionView>(`/api/clocktower/games/${gameId}/mic/release`, {method: 'POST'})
}

export function extendClocktowerMicSession(gameId: number, seconds: number) {
    return requestJson<ClocktowerMicSessionView>(`/api/clocktower/games/${gameId}/mic/extend`, {
        method: 'POST',
        body: {seconds},
    })
}

export function closeClocktowerMicSession(gameId: number) {
    return requestJson<ClocktowerMicSessionView>(`/api/clocktower/games/${gameId}/mic/close`, {method: 'POST'})
}
```

- [ ] **Step 5: Run service tests**

Run from `fe/`:

```bash
bun run test -- clocktowerService.test.ts
```

Expected: PASS.

- [ ] **Step 6: Commit type and service contracts**

```bash
git add fe/src/modules/clocktower/clocktowerTypes.ts \
        fe/src/modules/clocktower/clocktowerService.ts \
        fe/src/modules/clocktower/clocktowerService.test.ts
git commit -m "feat(clocktower): add frontend game mic contracts"
```

### Task 2: Lobby Agent Seats And Start Readiness

**Files:**
- Modify: `fe/src/modules/clocktower/RoomLobbyPage.tsx`
- Modify: `fe/src/modules/clocktower/RoomLobbyPage.test.tsx`
- Modify: `fe/src/modules/clocktower/components/ClocktowerSeatGrid.tsx`

- [ ] **Step 1: Write failing lobby tests**

In `RoomLobbyPage.test.tsx`, add this helper after `clocktowerRoomFixture`:

```ts
function agentSeatFixture() {
    return {
        seatId: 14,
        seatNo: 4,
        displayName: 'Agent Alice',
        userId: null,
        actorType: 'AGENT' as const,
        actorId: 9001,
        agentInstanceId: 801,
        isAgent: true,
        roleCode: 'MONK',
        roleType: 'TOWNSFOLK' as const,
        lifeStatus: 'ALIVE',
        publicLifeStatus: 'ALIVE',
        connected: false,
        hasDeadVote: true,
        ready: true,
    }
}
```

Add these tests:

```ts
    test('counts agent seats as occupied players', () => {
        const room = clocktowerRoomFixture({
            playerCount: 4,
            seats: [...clocktowerRoomFixture().seats, agentSeatFixture()],
            reservations: [],
        })

        expect(roomLobbyCounts(room)).toEqual({
            occupied: 3,
            reserved: 0,
            required: 4,
        })
    })

    test('allows ready agent seats without user id to satisfy start readiness', () => {
        const base = clocktowerRoomFixture()
        const room = clocktowerRoomFixture({
            playerCount: 4,
            seats: [
                {
                    ...base.seats[0],
                    ready: true,
                },
                {
                    ...base.seats[1],
                    ready: true,
                },
                {
                    ...base.seats[2],
                    userId: 103,
                    displayName: '玩家三',
                    roleCode: 'WASHERWOMAN',
                    ready: true,
                },
                agentSeatFixture(),
            ],
            reservations: [],
        })

        expect(canStartClocktowerRoom(room)).toBe(true)
    })

    test('renders agent seat badges instead of empty seat copy', () => {
        const markup = renderToStaticMarkup(
            <ClocktowerSeatGrid
                claimingSeatNo={null}
                onClaimSeat={vi.fn()}
                reservations={[]}
                seats={[agentSeatFixture()]}
            />,
        )

        expect(markup).toContain('Agent Alice')
        expect(markup).toContain('Agent')
        expect(markup).toContain('自动')
        expect(markup).not.toContain('未入座')
    })
```

- [ ] **Step 2: Run lobby tests to verify they fail**

Run from `fe/`:

```bash
bun run test -- RoomLobbyPage.test.tsx
```

Expected: FAIL because Agent seats without `userId` are not counted as occupied and do not render Agent badges.

- [ ] **Step 3: Implement Agent-aware lobby helpers**

In `RoomLobbyPage.tsx`, add these exported helpers before `roomLobbyCounts`:

```ts
export function isAgentSeat(seat: { actorType?: string | null; isAgent?: boolean; agentInstanceId?: number | null }) {
    return seat.actorType === 'AGENT' || seat.isAgent === true || typeof seat.agentInstanceId === 'number'
}

export function hasClocktowerSeatPlayer(seat: { userId?: number | null; actorType?: string | null; isAgent?: boolean; agentInstanceId?: number | null }) {
    return Boolean(seat.userId) || isAgentSeat(seat)
}

export function canStartClocktowerSeat(seat: ClocktowerRoomResponse['seats'][number]) {
    return hasClocktowerSeatPlayer(seat)
        && typeof seat.roleCode === 'string'
        && seat.roleCode.trim().length > 0
        && (!('ready' in seat) || seat.ready === true)
}
```

Replace `roomLobbyCounts` with:

```ts
export function roomLobbyCounts(room: ClocktowerRoomResponse | null) {
    return {
        occupied: room?.seats.filter(hasClocktowerSeatPlayer).length ?? 0,
        reserved: room?.reservations?.length ?? 0,
        required: room?.playerCount ?? 0,
    }
}
```

Replace the `every` body in `canStartClocktowerRoom` with:

```ts
    return room.seats.slice(0, room.playerCount).every(canStartClocktowerSeat)
```

- [ ] **Step 4: Implement Agent badges in seat grid**

In `ClocktowerSeatGrid.tsx`, add:

```ts
import {isAgentSeat} from '../RoomLobbyPage'
```

Change `SeatDraftState`:

```ts
type SeatDraftState = 'occupied' | 'reserved' | 'ready' | 'agent' | 'open'
```

Add meta for `agent`:

```ts
    agent: {label: 'Agent', color: 'purple' as const, badge: 'processing' as const},
```

Update the player column render:

```tsx
            render: (_, row) => (
                <Typography.Text strong>
                    {row.seat.displayName || (row.state === 'reserved' ? '等待受邀玩家' : '未入座')}
                </Typography.Text>
            ),
```

Update the tags column so Agent seats show badges:

```tsx
            render: (_, row) => (
                <Flex gap={4} wrap>
                    {isAgentSeat(row.seat) ? <Tag color="purple">Agent</Tag> : (
                        row.seat.connected ? <Tag color="success">在线</Tag> : <Tag>离线</Tag>
                    )}
                    {isAgentSeat(row.seat) && <Tag color="blue">自动</Tag>}
                    {row.seat.ready === true && <Tag icon={<CheckCircleOutlined/>} color="success">已就绪</Tag>}
                    {row.seat.ready === false && <Tag color="warning">未就绪</Tag>}
                    {row.seat.roleCode && <Tag>{row.seat.roleCode}</Tag>}
                </Flex>
            ),
```

Replace `seatState`:

```ts
function seatState(seat: ClocktowerSeatResponse, reservationSeatNos: Set<number>): SeatDraftState {
    if (isAgentSeat(seat)) {
        return seat.ready === true ? 'ready' : 'agent'
    }
    if (seat.userId && seat.ready === true) {
        return 'ready'
    }
    if (seat.userId) {
        return 'occupied'
    }
    if (reservationSeatNos.has(seat.seatNo)) {
        return 'reserved'
    }
    return 'open'
}
```

- [ ] **Step 5: Run lobby tests**

Run from `fe/`:

```bash
bun run test -- RoomLobbyPage.test.tsx
```

Expected: PASS.

- [ ] **Step 6: Commit lobby Agent UI**

```bash
git add fe/src/modules/clocktower/RoomLobbyPage.tsx \
        fe/src/modules/clocktower/RoomLobbyPage.test.tsx \
        fe/src/modules/clocktower/components/ClocktowerSeatGrid.tsx
git commit -m "feat(clocktower): render agent seats in lobby"
```

### Task 3: New Game Action API Wiring

**Files:**
- Modify: `fe/src/modules/clocktower/GameRoomPage.tsx`
- Modify: `fe/src/modules/clocktower/GameRoomPage.test.tsx`
- Modify: `fe/src/modules/clocktower/ClocktowerRoomPlayPage.tsx`
- Modify: `fe/src/modules/clocktower/ClocktowerRoomPlayPage.test.tsx`

- [ ] **Step 1: Write failing game action surface tests**

In `ClocktowerRoomPlayPage.test.tsx`, change the last test name to:

```ts
    test('renders player play surface with new game action controls', () => {
```

Replace the final expectations in that test with:

```ts
        expect(markup).toContain('玩家视角')
        expect(markup).toContain('聊天')
        expect(markup).toContain('操作')
        expect(markup).toContain('公开发言')
```

In `GameRoomPage.test.tsx`, update the service mock to include:

```ts
    submitClocktowerGameAction: vi.fn(),
```

Add this test:

```ts
    test('maps game public seats to game seat ids for new game actions', () => {
        const markup = renderToStaticMarkup(
            <GameRoomSurface
                roomName="测试房间"
                useGameActionApi
                view={{
                    ...gameView,
                    publicSeats: [
                        {
                            ...gameView.publicSeats[0],
                            gameSeatId: 32,
                            roomSeatId: 4,
                            actorType: 'AGENT',
                            agentInstanceId: 802,
                            isAgent: true,
                        },
                    ],
                }}
            />,
        )

        expect(markup).toContain('玩家视角')
        expect(markup).toContain('公开发言')
        expect(markup).toContain('Agent')
    })
```

- [ ] **Step 2: Run page tests to verify they fail**

Run from `fe/`:

```bash
bun run test -- ClocktowerRoomPlayPage.test.tsx GameRoomPage.test.tsx
```

Expected: FAIL because `useGameActionApi` does not exist and `ClocktowerRoomPlayPage` still disables new player action controls.

- [ ] **Step 3: Enable new player game actions from room play**

In `ClocktowerRoomPlayPage.tsx`, replace:

```tsx
        return <GameRoomSurface actionControlsEnabled={false} roomName={room.name} view={gameView}/>
```

with:

```tsx
        return (
            <GameRoomSurface
                actionControlsEnabled={gameView.viewerMode === 'PLAYER'}
                roomName={room.name}
                useGameActionApi
                view={gameView}
            />
        )
```

- [ ] **Step 4: Add game action mode to `GameRoomSurface`**

In `GameRoomPage.tsx`, add `submitClocktowerGameAction` to the service imports:

```ts
    submitClocktowerGameAction,
```

Change the `GameRoomSurface` props signature:

```ts
export function GameRoomSurface({
    actionControlsEnabled = true,
    roomName,
    useGameActionApi = false,
    view,
}: {
    actionControlsEnabled?: boolean
    roomName?: string
    useGameActionApi?: boolean
    view: ClocktowerGameViewResponse
}) {
```

Replace `submitAction` inside `GameRoomSurface` with:

```ts
    async function submitAction(values: VotePanelValues) {
        if (!view.mySeat) {
            message.warning('当前视角没有绑定座位')
            return
        }
        setSubmitting(true)
        try {
            const response = useGameActionApi
                ? await submitClocktowerGameAction(view.gameId, {
                    actorGameSeatId: view.mySeat.gameSeatId,
                    actionType: values.actionType,
                    targetGameSeatIds: values.targetSeatIds ?? [],
                    nominationId: null,
                    vote: null,
                    content: values.content,
                    payload: {},
                })
                : await submitClocktowerPlayerAction(view.roomId, {
                    seatId: view.mySeat.roomSeatId,
                    actionType: values.actionType,
                    targetSeatIds: values.targetSeatIds ?? [],
                    content: values.content,
                    payload: {},
                    clientActionId: crypto.randomUUID(),
                })
            if (response.event) {
                const event = useGameActionApi
                    ? mapGameEvent(view.roomId, response.event as ClocktowerGameEventResponse)
                    : response.event as ClocktowerEventResponse
                setEvents((current) => [...current, event])
            }
            message.success(response.accepted ? '操作已提交' : `操作被拒绝：${response.rejectedCode ?? '-'}`)
        } catch (caught) {
            reportGlobalError(caught)
        } finally {
            setSubmitting(false)
        }
    }
```

Replace `mapGamePublicSeats` with:

```ts
function mapGamePublicSeats(seats: ClocktowerGameSeatResponse[]): PublicSeatResponse[] {
    return seats.map((seat) => ({
        seatId: seat.gameSeatId,
        seatNo: seat.seatNo,
        displayName: seat.displayName,
        roleCode: null,
        lifeStatus: seat.publicLifeStatus || seat.lifeStatus,
        connected: seat.status === 'ACTIVE',
        hasDeadVote: seat.hasDeadVote,
    }))
}
```

Update `SeatPublicList` rendering to show an Agent badge when the incoming seat object carries `actorType`, `isAgent`, or `agentInstanceId`. Use a local helper:

```ts
function gameSeatIsAgent(seat: { actorType?: string | null; isAgent?: boolean; agentInstanceId?: number | null }) {
    return seat.actorType === 'AGENT' || seat.isAgent === true || typeof seat.agentInstanceId === 'number'
}
```

For `SeatPublicList`, widen the prop type to:

```ts
export function SeatPublicList({seats}: { seats: Array<PublicSeatResponse | ClocktowerGameSeatResponse> }) {
```

Add the badge in the list item:

```tsx
                        {'actorType' in seat && gameSeatIsAgent(seat) && <Tag color="purple">Agent</Tag>}
```

- [ ] **Step 5: Run page tests**

Run from `fe/`:

```bash
bun run test -- ClocktowerRoomPlayPage.test.tsx GameRoomPage.test.tsx
```

Expected: PASS.

- [ ] **Step 6: Commit game action wiring**

```bash
git add fe/src/modules/clocktower/GameRoomPage.tsx \
        fe/src/modules/clocktower/GameRoomPage.test.tsx \
        fe/src/modules/clocktower/ClocktowerRoomPlayPage.tsx \
        fe/src/modules/clocktower/ClocktowerRoomPlayPage.test.tsx
git commit -m "feat(clocktower): use game action api in new play view"
```

### Task 4: Public Mic Panel

**Files:**
- Create: `fe/src/modules/clocktower/components/PublicMicPanel.tsx`
- Create: `fe/src/modules/clocktower/components/PublicMicPanel.test.tsx`
- Modify: `fe/src/modules/clocktower/GameRoomPage.tsx`

- [ ] **Step 1: Write failing mic panel tests**

Create `PublicMicPanel.test.tsx`:

```tsx
import {renderToStaticMarkup} from 'react-dom/server'
import {describe, expect, test, vi} from 'vitest'
import {
    PublicMicPanelContent,
    canGrabClocktowerMic,
    currentMicTurn,
    formatMicRemaining,
    isMicHolder,
} from './PublicMicPanel'
import type {ClocktowerMicSessionView} from '../clocktowerTypes'

const session: ClocktowerMicSessionView = {
    sessionId: 51,
    gameId: 11,
    dayNo: 1,
    status: 'GRAB_MIC',
    currentHolderGameSeatId: null,
    currentTurnId: null,
    roundStartedAt: '2026-07-09T10:00:00Z',
    roundFinishedAt: '2026-07-09T10:05:00Z',
    grabStartedAt: '2026-07-09T10:05:00Z',
    grabEndsAt: '2026-07-09T10:10:00Z',
    closedAt: null,
    turns: [
        {
            turnId: 91,
            gameSeatId: 31,
            seatNo: 1,
            displayName: 'Alice',
            actorType: 'HUMAN',
            agentInstanceId: null,
            turnOrder: 1,
            stage: 'ROUND_ROBIN',
            acquisitionType: 'ROUND_ROBIN',
            status: 'DONE',
            startedAt: '2026-07-09T10:00:00Z',
            endedAt: '2026-07-09T10:01:00Z',
            expiresAt: '2026-07-09T10:01:00Z',
        },
        {
            turnId: 92,
            gameSeatId: 32,
            seatNo: 2,
            displayName: 'Agent Bob',
            actorType: 'AGENT',
            agentInstanceId: 802,
            turnOrder: 2,
            stage: 'ROUND_ROBIN',
            acquisitionType: 'ROUND_ROBIN',
            status: 'DONE',
            startedAt: '2026-07-09T10:01:00Z',
            endedAt: '2026-07-09T10:02:00Z',
            expiresAt: '2026-07-09T10:02:00Z',
        },
    ],
}

describe('PublicMicPanel', () => {
    test('formats remaining seconds as mm:ss', () => {
        expect(formatMicRemaining(0)).toBe('00:00')
        expect(formatMicRemaining(31)).toBe('00:31')
        expect(formatMicRemaining(252)).toBe('04:12')
    })

    test('derives current holder and grab availability', () => {
        expect(currentMicTurn(session)).toBeUndefined()
        expect(isMicHolder(session, 31)).toBe(false)
        expect(canGrabClocktowerMic(session, 'PLAYER', 31, new Date('2026-07-09T10:06:00Z'))).toBe(true)
        expect(canGrabClocktowerMic(session, 'PLAYER', 31, new Date('2026-07-09T10:11:00Z'))).toBe(false)
    })

    test('renders grab stage with agent badge and queue', () => {
        const markup = renderToStaticMarkup(
            <PublicMicPanelContent
                actionLoading={null}
                gameId={11}
                myGameSeatId={31}
                now={new Date('2026-07-09T10:06:00Z')}
                onFinish={vi.fn()}
                onGrab={vi.fn()}
                onRelease={vi.fn()}
                onSkip={vi.fn()}
                onExtend={vi.fn()}
                onSubmitSpeech={vi.fn()}
                session={session}
                submittingSpeech={false}
                viewerMode="PLAYER"
            />,
        )

        expect(markup).toContain('抢麦')
        expect(markup).toContain('04:00')
        expect(markup).toContain('Agent Bob')
        expect(markup).toContain('Agent')
    })
})
```

- [ ] **Step 2: Run mic panel tests to verify they fail**

Run from `fe/`:

```bash
bun run test -- PublicMicPanel.test.tsx
```

Expected: FAIL because `PublicMicPanel.tsx` does not exist.

- [ ] **Step 3: Implement `PublicMicPanel`**

Create `PublicMicPanel.tsx`:

```tsx
import {AudioOutlined, ClockCircleOutlined, StepForwardOutlined} from '@ant-design/icons'
import {App, Button, Card, Empty, Flex, Input, List, Space, Tag, Typography} from 'antd'
import {useCallback, useEffect, useMemo, useState} from 'react'
import {reportGlobalError} from '../../../app/globalError'
import {voidify} from '../../../utils/async'
import {
    extendClocktowerMicSession,
    finishClocktowerMicTurn,
    getClocktowerMicSession,
    grabClocktowerMic,
    releaseClocktowerMic,
    skipClocktowerMicTurn,
} from '../clocktowerService'
import type {ClocktowerGameEventResponse, ClocktowerMicSessionView, ClocktowerMicTurnView, ClocktowerViewerMode} from '../clocktowerTypes'

const SPEECH_MAX_LENGTH = 1000

type MicAction = 'grab' | 'finish' | 'release' | 'skip' | 'extend'

export function PublicMicPanel({
    gameId,
    myGameSeatId,
    onMicChanged,
    onSubmitSpeech,
    viewerMode,
}: {
    gameId: number
    myGameSeatId?: number | null
    viewerMode: ClocktowerViewerMode
    onMicChanged?: (session: ClocktowerMicSessionView) => void
    onSubmitSpeech?: (content: string) => Promise<ClocktowerGameEventResponse | null | undefined>
}) {
    const {message} = App.useApp()
    const [session, setSession] = useState<ClocktowerMicSessionView | null>(null)
    const [loading, setLoading] = useState(false)
    const [actionLoading, setActionLoading] = useState<MicAction | null>(null)
    const [submittingSpeech, setSubmittingSpeech] = useState(false)
    const [now, setNow] = useState(() => new Date())

    const refresh = useCallback(async () => {
        setLoading(true)
        try {
            const next = await getClocktowerMicSession(gameId)
            setSession(next)
            onMicChanged?.(next)
        } catch (caught) {
            setSession(null)
        } finally {
            setLoading(false)
        }
    }, [gameId, onMicChanged])

    useEffect(() => {
        void refresh()
    }, [refresh])

    useEffect(() => {
        const timer = window.setInterval(() => setNow(new Date()), 1000)
        return () => window.clearInterval(timer)
    }, [])

    async function runMicAction(action: MicAction) {
        if (!session) {
            return
        }
        setActionLoading(action)
        try {
            const next = await executeMicAction(action, gameId, session)
            setSession(next)
            onMicChanged?.(next)
        } catch (caught) {
            reportGlobalError(caught)
        } finally {
            setActionLoading(null)
        }
    }

    async function submitSpeech(content: string) {
        if (!onSubmitSpeech) {
            return
        }
        setSubmittingSpeech(true)
        try {
            const event = await onSubmitSpeech(content)
            message.success(event ? '发言已提交' : '发言已提交')
        } catch (caught) {
            reportGlobalError(caught)
        } finally {
            setSubmittingSpeech(false)
        }
    }

    return (
        <Card loading={loading} title="公聊麦序">
            <PublicMicPanelContent
                actionLoading={actionLoading}
                gameId={gameId}
                myGameSeatId={myGameSeatId}
                now={now}
                onExtend={() => void runMicAction('extend')}
                onFinish={() => void runMicAction('finish')}
                onGrab={() => void runMicAction('grab')}
                onRelease={() => void runMicAction('release')}
                onSkip={() => void runMicAction('skip')}
                onSubmitSpeech={submitSpeech}
                session={session}
                submittingSpeech={submittingSpeech}
                viewerMode={viewerMode}
            />
        </Card>
    )
}

async function executeMicAction(action: MicAction, gameId: number, session: ClocktowerMicSessionView) {
    if (action === 'grab') {
        return grabClocktowerMic(gameId)
    }
    if (action === 'release') {
        return releaseClocktowerMic(gameId)
    }
    if (action === 'extend') {
        return extendClocktowerMicSession(gameId, 120)
    }
    if (action === 'finish' && session.currentTurnId) {
        return finishClocktowerMicTurn(gameId, session.currentTurnId)
    }
    if (action === 'skip' && session.currentTurnId) {
        return skipClocktowerMicTurn(gameId, session.currentTurnId)
    }
    return session
}

export function PublicMicPanelContent({
    actionLoading,
    myGameSeatId,
    now,
    onExtend,
    onFinish,
    onGrab,
    onRelease,
    onSkip,
    onSubmitSpeech,
    session,
    submittingSpeech,
    viewerMode,
}: {
    actionLoading: MicAction | null
    gameId: number
    myGameSeatId?: number | null
    viewerMode: ClocktowerViewerMode
    session?: ClocktowerMicSessionView | null
    now: Date
    submittingSpeech: boolean
    onGrab: () => void
    onFinish: () => void
    onRelease: () => void
    onSkip: () => void
    onExtend: () => void
    onSubmitSpeech: (content: string) => Promise<void> | void
}) {
    const holder = currentMicTurn(session)
    const holderSelf = isMicHolder(session, myGameSeatId)
    const canGrab = canGrabClocktowerMic(session, viewerMode, myGameSeatId, now)
    const canFinish = holderSelf && Boolean(session?.currentTurnId)
    const canStorytellerOperate = viewerMode === 'STORYTELLER' && Boolean(session?.currentTurnId)
    const canExtend = viewerMode === 'STORYTELLER' && session?.status === 'GRAB_MIC'
    const grabRemaining = remainingUntil(session?.grabEndsAt, now)
    const turnRemaining = remainingUntil(holder?.expiresAt, now)

    if (!session) {
        return (
            <Space orientation="vertical" style={{width: '100%'}}>
                <Empty description="当前没有公聊麦序"/>
                <PublicSpeechComposer
                    disabled
                    loading={submittingSpeech}
                    onSubmit={onSubmitSpeech}
                    placeholder="当前没有公聊麦序"
                />
            </Space>
        )
    }

    return (
        <Space orientation="vertical" size="middle" style={{width: '100%'}}>
            <Flex align="center" justify="space-between" gap="middle" wrap>
                <Space wrap>
                    <Tag color={session.status === 'GRAB_MIC' ? 'warning' : session.status === 'CLOSED' ? 'default' : 'processing'}>
                        {micStatusLabel(session.status)}
                    </Tag>
                    {holder ? (
                        <Typography.Text>
                            当前发言人：#{holder.seatNo ?? holder.gameSeatId} {holder.displayName ?? holder.gameSeatId}
                        </Typography.Text>
                    ) : (
                        <Typography.Text type="secondary">当前无人持麦</Typography.Text>
                    )}
                    {holder?.actorType === 'AGENT' && <Tag color="purple">Agent</Tag>}
                </Space>
                <Space wrap>
                    <Tag icon={<ClockCircleOutlined/>}>发言 {formatMicRemaining(turnRemaining)}</Tag>
                    <Tag icon={<ClockCircleOutlined/>}>抢麦 {formatMicRemaining(grabRemaining)}</Tag>
                </Space>
            </Flex>
            <Space wrap>
                <Button disabled={!canFinish} icon={<StepForwardOutlined/>} loading={actionLoading === 'finish'} onClick={onFinish}>
                    说完了
                </Button>
                <Button disabled={!canGrab} icon={<AudioOutlined/>} loading={actionLoading === 'grab'} onClick={onGrab} type={canGrab ? 'primary' : 'default'}>
                    抢麦
                </Button>
                <Button disabled={!canFinish} loading={actionLoading === 'release'} onClick={onRelease}>释放麦</Button>
                <Button disabled={!canStorytellerOperate} loading={actionLoading === 'skip'} onClick={onSkip}>ST 跳过</Button>
                <Button disabled={!canExtend} loading={actionLoading === 'extend'} onClick={onExtend}>ST 延长 2 分钟</Button>
            </Space>
            <PublicSpeechComposer
                disabled={!holderSelf}
                loading={submittingSpeech}
                onSubmit={onSubmitSpeech}
                placeholder={speechPlaceholder(session, myGameSeatId)}
            />
            <List
                dataSource={[...session.turns].sort((left, right) => left.turnOrder - right.turnOrder)}
                renderItem={(turn) => (
                    <List.Item>
                        <Space wrap>
                            <Tag>#{turn.seatNo ?? turn.gameSeatId}</Tag>
                            <Typography.Text>{turn.displayName ?? turn.gameSeatId}</Typography.Text>
                            {turn.actorType === 'AGENT' && <Tag color="purple">Agent</Tag>}
                            <Tag>{turn.stage}</Tag>
                            <Tag>{turn.status}</Tag>
                        </Space>
                    </List.Item>
                )}
            />
        </Space>
    )
}

function PublicSpeechComposer({
    disabled,
    loading,
    onSubmit,
    placeholder,
}: {
    disabled: boolean
    loading: boolean
    placeholder: string
    onSubmit: (content: string) => Promise<void> | void
}) {
    const [content, setContent] = useState('')
    const trimmed = content.trim()
    return (
        <Space.Compact style={{width: '100%'}}>
            <Input.TextArea
                disabled={disabled}
                maxLength={SPEECH_MAX_LENGTH}
                onChange={(event) => setContent(event.target.value)}
                placeholder={placeholder}
                showCount
                style={{minHeight: 72}}
                value={content}
            />
            <Button
                disabled={disabled || trimmed.length === 0}
                loading={loading}
                onClick={() => {
                    void onSubmit(trimmed)
                    setContent('')
                }}
                type="primary"
            >
                发送
            </Button>
        </Space.Compact>
    )
}

export function currentMicTurn(session?: ClocktowerMicSessionView | null) {
    if (!session?.currentTurnId) {
        return undefined
    }
    return session.turns.find((turn) => turn.turnId === session.currentTurnId)
}

export function isMicHolder(session: ClocktowerMicSessionView | null | undefined, myGameSeatId?: number | null) {
    return typeof myGameSeatId === 'number' && session?.currentHolderGameSeatId === myGameSeatId
}

export function canGrabClocktowerMic(
    session: ClocktowerMicSessionView | null | undefined,
    viewerMode: ClocktowerViewerMode,
    myGameSeatId: number | null | undefined,
    now: Date,
) {
    return viewerMode === 'PLAYER'
        && typeof myGameSeatId === 'number'
        && session?.status === 'GRAB_MIC'
        && !session.currentHolderGameSeatId
        && remainingUntil(session.grabEndsAt, now) > 0
}

export function formatMicRemaining(seconds: number) {
    const safe = Math.max(0, Math.floor(seconds))
    const minutes = Math.floor(safe / 60).toString().padStart(2, '0')
    const rest = (safe % 60).toString().padStart(2, '0')
    return `${minutes}:${rest}`
}

function remainingUntil(value: string | null | undefined, now: Date) {
    if (!value) {
        return 0
    }
    return Math.max(0, Math.floor((new Date(value).getTime() - now.getTime()) / 1000))
}

function speechPlaceholder(session: ClocktowerMicSessionView, myGameSeatId?: number | null) {
    if (isMicHolder(session, myGameSeatId)) {
        return '当前你持麦，可以公开发言'
    }
    if (session.status === 'GRAB_MIC' && !session.currentHolderGameSeatId) {
        return '抢麦后发言'
    }
    if (session.status === 'CLOSED') {
        return '当前没有公聊麦序'
    }
    return '等待麦序'
}

function micStatusLabel(status: string) {
    if (status === 'ROUND_ROBIN') {
        return '轮流麦'
    }
    if (status === 'GRAB_MIC') {
        return '抢麦'
    }
    if (status === 'CLOSED') {
        return '已结束'
    }
    return status
}
```

- [ ] **Step 4: Wire mic panel into `GameRoomSurface`**

In `GameRoomPage.tsx`, import:

```ts
import {PublicMicPanel} from './components/PublicMicPanel'
```

In the right column, add this card before the tabs when `useGameActionApi` is true:

```tsx
                    {useGameActionApi && (
                        <div style={{marginTop: view.viewerMode === 'SPECTATOR' ? 0 : 16}}>
                            <PublicMicPanel
                                gameId={view.gameId}
                                myGameSeatId={view.mySeat?.gameSeatId}
                                onSubmitSpeech={submitPublicSpeech}
                                viewerMode={view.viewerMode}
                            />
                        </div>
                    )}
```

Add `submitPublicSpeech` inside `GameRoomSurface`:

```ts
    async function submitPublicSpeech(content: string) {
        if (!view.mySeat) {
            message.warning('当前视角没有绑定座位')
            return null
        }
        const response = await submitClocktowerGameAction(view.gameId, {
            actorGameSeatId: view.mySeat.gameSeatId,
            actionType: 'PUBLIC_SPEECH',
            targetGameSeatIds: [],
            nominationId: null,
            vote: null,
            content,
            payload: {},
        })
        if (response.event) {
            setEvents((current) => [...current, mapGameEvent(view.roomId, response.event as ClocktowerGameEventResponse)])
        }
        if (!response.accepted) {
            message.warning(`操作被拒绝：${response.rejectedCode ?? '-'}`)
        }
        return response.event
    }
```

- [ ] **Step 5: Run mic panel tests**

Run from `fe/`:

```bash
bun run test -- PublicMicPanel.test.tsx GameRoomPage.test.tsx
```

Expected: PASS.

- [ ] **Step 6: Commit mic panel**

```bash
git add fe/src/modules/clocktower/components/PublicMicPanel.tsx \
        fe/src/modules/clocktower/components/PublicMicPanel.test.tsx \
        fe/src/modules/clocktower/GameRoomPage.tsx
git commit -m "feat(clocktower): add public mic panel"
```

### Task 5: Focused Frontend Verification And Integration Polish

**Files:**
- Modify as needed from failed verification:
  - `fe/src/modules/clocktower/GameRoomPage.tsx`
  - `fe/src/modules/clocktower/ClocktowerRoomPlayPage.tsx`
  - `fe/src/modules/clocktower/components/PublicMicPanel.tsx`
  - corresponding tests

- [ ] **Step 1: Run focused task-13 tests**

Run from `fe/`:

```bash
bun run test -- RoomLobbyPage.test.tsx ClocktowerRoomPlayPage.test.tsx GameRoomPage.test.tsx PublicMicPanel.test.tsx clocktowerService.test.ts
```

Expected: PASS.

- [ ] **Step 2: Run frontend typecheck**

Run from `fe/`:

```bash
bun run typecheck
```

Expected: PASS.

- [ ] **Step 3: Fix exact failures if verification reports any**

If `bun run typecheck` reports `ClocktowerGameActionResponse` event type incompatibility, use this explicit cast at the append site:

```ts
const gameEvent = response.event as ClocktowerGameEventResponse
setEvents((current) => [...current, mapGameEvent(view.roomId, gameEvent)])
```

If Ant Design `Space.Compact` rejects a `TextArea` child type, replace the composer layout with:

```tsx
<Space orientation="vertical" style={{width: '100%'}}>
    <Input.TextArea
        disabled={disabled}
        maxLength={SPEECH_MAX_LENGTH}
        onChange={(event) => setContent(event.target.value)}
        placeholder={placeholder}
        showCount
        style={{minHeight: 72}}
        value={content}
    />
    <Button
        disabled={disabled || trimmed.length === 0}
        loading={loading}
        onClick={() => {
            void onSubmit(trimmed)
            setContent('')
        }}
        type="primary"
    >
        发送
    </Button>
</Space>
```

If `SeatPublicList` type widening causes `roleCode` or `connected` access errors, add this mapper before rendering:

```ts
const displaySeat = 'gameSeatId' in seat
    ? {
        seatId: seat.gameSeatId,
        seatNo: seat.seatNo,
        displayName: seat.displayName,
        roleCode: null,
        lifeStatus: seat.publicLifeStatus || seat.lifeStatus,
        connected: seat.status === 'ACTIVE',
        hasDeadVote: seat.hasDeadVote,
        agent: gameSeatIsAgent(seat),
    }
    : {
        ...seat,
        agent: false,
    }
```

Then render from `displaySeat`.

- [ ] **Step 4: Re-run verification after any fix**

Run from `fe/`:

```bash
bun run test -- RoomLobbyPage.test.tsx ClocktowerRoomPlayPage.test.tsx GameRoomPage.test.tsx PublicMicPanel.test.tsx clocktowerService.test.ts
bun run typecheck
```

Expected: PASS for both commands.

- [ ] **Step 5: Commit verification polish if files changed**

Only commit if Step 3 changed files:

```bash
git add fe/src/modules/clocktower
git commit -m "fix(clocktower): stabilize frontend agent mic ui"
```

### Task 6: Final Verification

**Files:**
- Inspect: all frontend files changed by tasks 1 through 5.

- [ ] **Step 1: Run focused frontend verification**

Run from `fe/`:

```bash
bun run test -- RoomLobbyPage.test.tsx ClocktowerRoomPlayPage.test.tsx GameRoomPage.test.tsx PublicMicPanel.test.tsx clocktowerService.test.ts
```

Expected: PASS.

- [ ] **Step 2: Run typecheck**

Run from `fe/`:

```bash
bun run typecheck
```

Expected: PASS.

- [ ] **Step 3: Inspect diff scope**

Run from repository root:

```bash
git diff --stat HEAD~5..HEAD
git status --short
```

Expected: changes are limited to Clocktower frontend types, service, lobby, game surface, mic panel, tests, and task13 docs/plan. `git status --short` is clean after commits.

- [ ] **Step 4: Commit final verification changes only if needed**

If final verification required a code or test adjustment:

```bash
git add fe/src/modules/clocktower
git commit -m "chore(clocktower): verify frontend agent mic ui"
```

## Acceptance Mapping

- Agent seats visible in lobby: Task 2 `ClocktowerSeatGrid` badge and tests.
- Start button counts Agent seats: Task 2 `hasClocktowerSeatPlayer` and `canStartClocktowerRoom`.
- New game view submits to `/games/{gameId}/actions`: Task 1 service and Task 3 `useGameActionApi`.
- No-mic public speech disabled: Task 4 `PublicMicPanelContent` and `speechPlaceholder`.
- Round-robin queue and current holder visible: Task 4 session turns rendering.
- Grab-mic countdown visible: Task 4 `grabEndsAt` countdown.
- Agent speech/seat badge visible: Task 2 and Task 4 badges, Task 3 game seat badge.
- Old room action page preserved: Task 3 keeps `useGameActionApi=false` as default.

## Validation Commands

Run from `fe/`:

```bash
bun run test -- RoomLobbyPage.test.tsx ClocktowerRoomPlayPage.test.tsx GameRoomPage.test.tsx PublicMicPanel.test.tsx clocktowerService.test.ts
bun run typecheck
```

Do not start the application runtime after implementation; the user will run it manually.
