# Clocktower GAME_V2 Access And Flow Repair Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Repair the missing GAME_V2 authorization mappings and give the storyteller a phase-aware flow/mic console so a valid room owner can move a game from night to day without false 403, missing-session, or phase-invalid failures.

**Architecture:** Extend the canonical Clocktower RBAC resource provider at the existing authorization seam, keep `ClocktowerRoomAccessPolicy.requireOwner(...)` as the instance-level mutation boundary, and expose the existing `ClocktowerGameFlowService` through a focused React panel. Treat mic availability as derived from the current game phase; normal flow advancement remains the only UI path from night to day and continues to create the day mic session in the backend. No global `SUPER_ADMIN` bypass, Flyway migration, force-advance UI, new dependency, or new design-pattern layer is introduced.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring Security/RBAC, JUnit 5, AssertJ, React 19, TypeScript 6, Ant Design 6, Vitest, Bun 1.3.

---

## Scope Guardrails

- Keep the user-owned staged `docs/clocktower_agent_tasks/**` files, the unstaged `.gitignore` change, and unrelated untracked files out of every task commit.
- Use `git commit --only <task paths> ...` for tracked files. For a newly created file, stage only that file first, then still use `git commit --only` with the exact task paths.
- Do not edit any existing Flyway migration. This repair has no database migration.
- Do not start the backend or frontend dev server. Validation is test/typecheck/build only.
- Do not add a global `SUPER_ADMIN` bypass. RBAC grants the API category; the service continues to enforce concrete room ownership.
- Do not add a normal UI entry for `/flow/force-advance`.
- Preserve the legacy room-scoped flow implementation and its TypeScript types while adding clearly named GAME_V2 contracts.

## Design Pattern Decision

The existing code already has the appropriate extension points: Resource Provider for API classification, domain service for state transitions, and focused React panels for storyteller controls. Strategy, State, Command, and Facade were considered, but none addresses an actual variation point in this repair; adding them would duplicate the current flow service or create indirection around two direct API calls. Implement the repair directly in the existing patterns.

## Task 1: Register All Missing GAME_V2 RBAC Paths

**Files:**

- Modify: `be/src/test/java/top/egon/mario/clocktower/resource/ClocktowerRbacResourceProviderTests.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/resource/ClocktowerRbacResourceProvider.java`

- [ ] **Step 1: Add failing matcher assertions for the actual controller routes**

Extend `providerMapsCurrentClocktowerApisToCanonicalResources()` with the GAME_V2 routes below. Assert the read-only flow endpoint separately from storyteller mutations so a future broad regex cannot silently grant the wrong permission.

```java
assertMatches(rules, "GET", "/api/clocktower/games/9/flow",
        "api:clocktower:game:read");
assertMatches(rules, "POST", "/api/clocktower/games/9/flow/advance",
        "api:clocktower:game:storyteller");
assertMatches(rules, "POST", "/api/clocktower/games/9/flow/force-advance",
        "api:clocktower:game:storyteller");

assertMatches(rules, "GET", "/api/clocktower/games/9/agents",
        "api:clocktower:game:storyteller");
assertMatches(rules, "POST", "/api/clocktower/games/9/agents/81/pause",
        "api:clocktower:game:storyteller");
assertMatches(rules, "GET", "/api/clocktower/games/9/agents/81/memory",
        "api:clocktower:game:storyteller");

assertMatches(rules, "GET", "/api/clocktower/games/9/night-tasks",
        "api:clocktower:game:storyteller");
assertMatches(rules, "POST", "/api/clocktower/games/9/night-tasks/91/resolve",
        "api:clocktower:game:storyteller");
assertMatches(rules, "POST", "/api/clocktower/games/9/night-tasks/91/skip",
        "api:clocktower:game:storyteller");
assertMatches(rules, "POST", "/api/clocktower/games/9/night-tasks/91/random-choice",
        "api:clocktower:game:storyteller");
assertMatches(rules, "POST", "/api/clocktower/games/9/night-tasks/resolve-ready",
        "api:clocktower:game:storyteller");

assertMatches(rules, "POST", "/api/clocktower/games/9/nominations/12/close",
        "api:clocktower:game:storyteller");
assertMatches(rules, "POST", "/api/clocktower/games/9/executions/resolve",
        "api:clocktower:game:storyteller");
```

- [ ] **Step 2: Run the provider test and confirm RED**

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClocktowerRbacResourceProviderTests test
```

Expected: `providerMapsCurrentClocktowerApisToCanonicalResources` fails because at least `/games/9/flow`, `/agents`, and `/night-tasks` currently produce an empty match.

- [ ] **Step 3: Extend the canonical regexes without changing permissions**

Change `GAME_READ` so GAME_V2 flow reads share the same permission as the existing game view:

```java
resources.add(api(GAME_READ, "Clocktower game read", "GET",
        "^/api/clocktower/(games/[^/]+/(view|flow)|rooms/[^/]+/view)$", ApiMatcherType.REGEX,
        ApiRiskLevel.MEDIUM));
```

Extend `GAME_STORYTELLER` while retaining the legacy room-scoped alternatives:

```java
resources.add(api(GAME_STORYTELLER, "Clocktower game storyteller", "ANY",
        "^/api/clocktower/(rooms/[^/]+/(flow(/.*)?|night-tasks/.*|nominations/.*|execution/.*|"
                + "grimoire(/.*)?|night-checklist|storyteller/actions|rulings(/.*)?)|"
                + "games/[^/]+/(agents(/.*)?|flow/(advance|force-advance)|night-tasks(/.*)?|"
                + "nominations/[^/]+/close|executions/resolve))$",
        ApiMatcherType.REGEX, ApiRiskLevel.HIGH));
```

Do not match bare `games/{id}/flow` in `GAME_STORYTELLER`; it must continue to resolve to `GAME_READ` for authorized viewers.

- [ ] **Step 4: Run focused backend authorization and ownership checks**

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false \
  -Dtest=ClocktowerRbacResourceProviderTests,ClocktowerGameFlowServiceTests,ClocktowerGameLifecycleServiceTests \
  test
```

Expected: PASS. `ClocktowerGameLifecycleServiceTests.timeoutAbortRequiresRoomOwner` remains green, proving the existing `CLOCKTOWER_STORYTELLER_FORBIDDEN` instance boundary was not bypassed.

- [ ] **Step 5: Commit only the RBAC task**

```bash
git commit --only \
  be/src/main/java/top/egon/mario/clocktower/resource/ClocktowerRbacResourceProvider.java \
  be/src/test/java/top/egon/mario/clocktower/resource/ClocktowerRbacResourceProviderTests.java \
  -m "fix(clocktower): register game v2 storyteller routes"
```

## Task 2: Add The GAME_V2 Flow Client Contract

**Files:**

- Modify: `fe/src/modules/clocktower/clocktowerTypes.ts`
- Modify: `fe/src/modules/clocktower/clocktowerService.ts`
- Modify: `fe/src/modules/clocktower/clocktowerService.test.ts`

- [ ] **Step 1: Add failing URL and request-shape tests**

Import `getClocktowerGameFlow` and `advanceClocktowerGameFlow` in `clocktowerService.test.ts`, then add:

```ts
it('loads and advances GAME_V2 flow by game id', async () => {
    const {requestJson} = await import('../../services/request')

    await getClocktowerGameFlow(11)
    await advanceClocktowerGameFlow(11)

    expect(requestJson).toHaveBeenCalledWith('/api/clocktower/games/11/flow')
    expect(requestJson).toHaveBeenCalledWith('/api/clocktower/games/11/flow/advance', {method: 'POST'})
})
```

Keep the existing room flow test unchanged; both APIs coexist during cutover.

- [ ] **Step 2: Run the service test and confirm RED**

```bash
cd fe
bun run test -- src/modules/clocktower/clocktowerService.test.ts
```

Expected: TypeScript/Vitest fails because the two GAME_V2 exports do not exist.

- [ ] **Step 3: Add TypeScript contracts matching the backend records**

Place these near the legacy flow types but keep the names distinct:

```ts
export type ClocktowerGameFlowView = {
    gameId: number
    status: string
    phase: ClocktowerPhase
    dayNo: number
    nightNo: number
    advanceAllowed: boolean
    blockingReasons: string[]
    nextPhase?: ClocktowerPhase | null
    counters: Record<string, unknown>
}

export type ClocktowerGameAdvanceRequest = {
    targetPhase?: ClocktowerPhase | null
    reason?: string | null
    metadata?: Record<string, unknown>
}

export type ClocktowerGameAdvanceResult = {
    gameId: number
    previousPhase: ClocktowerPhase
    phase: ClocktowerPhase
    advanced: boolean
    forced: boolean
    flow: ClocktowerGameFlowView
}
```

- [ ] **Step 4: Add the two service functions**

Import the three new types and add the functions beside `getClocktowerGameView(...)`:

```ts
export function getClocktowerGameFlow(gameId: number) {
    return requestJson<ClocktowerGameFlowView>(`/api/clocktower/games/${gameId}/flow`)
}

export function advanceClocktowerGameFlow(gameId: number, request?: ClocktowerGameAdvanceRequest) {
    if (request === undefined) {
        return requestJson<ClocktowerGameAdvanceResult>(`/api/clocktower/games/${gameId}/flow/advance`, {
            method: 'POST',
        })
    }
    return requestJson<ClocktowerGameAdvanceResult>(`/api/clocktower/games/${gameId}/flow/advance`, {
        method: 'POST',
        body: request,
    })
}
```

Do not add a force-advance client function unless another current caller already requires it.

- [ ] **Step 5: Run the focused test and typecheck**

```bash
cd fe
bun run test -- src/modules/clocktower/clocktowerService.test.ts
bun run typecheck
```

Expected: PASS.

- [ ] **Step 6: Commit only the client contract task**

```bash
git commit --only \
  fe/src/modules/clocktower/clocktowerTypes.ts \
  fe/src/modules/clocktower/clocktowerService.ts \
  fe/src/modules/clocktower/clocktowerService.test.ts \
  -m "feat(clocktower): add game v2 flow client"
```

## Task 3: Build The Storyteller GAME_V2 Flow Panel

**Files:**

- Create: `fe/src/modules/clocktower/components/StorytellerGameFlowPanel.tsx`
- Create: `fe/src/modules/clocktower/components/StorytellerGameFlowPanel.test.tsx`

- [ ] **Step 1: Write failing panel and orchestration tests**

Mock only `getClocktowerGameFlow` and `advanceClocktowerGameFlow`. Cover:

1. `StorytellerGameFlowPanelContent` renders phase/day/night/next phase and counters.
2. Blocking reasons are visible and “推进流程” is disabled when `advanceAllowed=false`.
3. The advance helper calls the API, then awaits the parent reload callback, then returns the updated flow.

Use the existing server-render test style:

```ts
const blockedFlow: ClocktowerGameFlowView = {
    gameId: 11,
    status: 'RUNNING',
    phase: 'FIRST_NIGHT',
    dayNo: 0,
    nightNo: 1,
    advanceAllowed: false,
    blockingReasons: ['PENDING_NIGHT_TASKS'],
    nextPhase: 'DAY',
    counters: {nightPendingMandatoryCount: 2},
}

expect(renderToStaticMarkup(
    <StorytellerGameFlowPanelContent
        actionLoading={false}
        flow={blockedFlow}
        loading={false}
        onAdvance={vi.fn()}
        onRefresh={vi.fn()}
    />,
)).toContain('PENDING_NIGHT_TASKS')
```

For orchestration:

```ts
const onGameChanged = vi.fn().mockResolvedValue(undefined)
vi.mocked(advanceClocktowerGameFlow).mockResolvedValue({
    gameId: 11,
    previousPhase: 'FIRST_NIGHT',
    phase: 'DAY',
    advanced: true,
    forced: false,
    flow: {...blockedFlow, phase: 'DAY', dayNo: 1, advanceAllowed: false},
})

const flow = await advanceAndReloadClocktowerGameFlow(11, onGameChanged)

expect(advanceClocktowerGameFlow).toHaveBeenCalledWith(11)
expect(onGameChanged).toHaveBeenCalledOnce()
expect(flow.phase).toBe('DAY')
```

- [ ] **Step 2: Run the new test and confirm RED**

```bash
cd fe
bun run test -- src/modules/clocktower/components/StorytellerGameFlowPanel.test.tsx
```

Expected: FAIL because the component module does not exist.

- [ ] **Step 3: Implement the focused container/content component**

Follow `StorytellerAgentPanel`, `StorytellerNightTaskPanel`, and `StorytellerMicControlPanel` conventions:

- `StorytellerGameFlowPanel({gameId, onGameChanged})` owns load/action state.
- `useEffect` loads `getClocktowerGameFlow(gameId)`.
- `reportGlobalError` handles genuine load/advance failures.
- `StorytellerGameFlowPanelContent` receives data/callback props and remains server-render testable.
- Render status, phase, `dayNo`, `nightNo`, `nextPhase`, blocking reasons, and `Object.entries(counters)`.
- Disable advance unless `flow?.advanceAllowed === true`.
- Do not render force advance.

Use this helper so callback order has a direct unit test:

```ts
export async function advanceAndReloadClocktowerGameFlow(
    gameId: number,
    onGameChanged?: () => Promise<void>,
) {
    const result = await advanceClocktowerGameFlow(gameId)
    await onGameChanged?.()
    return result.flow
}
```

In the action handler, update local `flow` only after this helper resolves. A parent reload failure therefore remains visible and does not pretend the whole page has already adopted the new phase.

- [ ] **Step 4: Run focused tests and typecheck**

```bash
cd fe
bun run test -- src/modules/clocktower/components/StorytellerGameFlowPanel.test.tsx
bun run typecheck
```

Expected: PASS.

- [ ] **Step 5: Commit only the new flow panel**

```bash
git add \
  fe/src/modules/clocktower/components/StorytellerGameFlowPanel.tsx \
  fe/src/modules/clocktower/components/StorytellerGameFlowPanel.test.tsx
git commit --only \
  fe/src/modules/clocktower/components/StorytellerGameFlowPanel.tsx \
  fe/src/modules/clocktower/components/StorytellerGameFlowPanel.test.tsx \
  -m "feat(clocktower): add storyteller game flow panel"
```

## Task 4: Make The Storyteller Mic Panel Phase-Aware

**Files:**

- Modify: `fe/src/modules/clocktower/components/StorytellerMicControlPanel.tsx`
- Modify: `fe/src/modules/clocktower/components/StorytellerMicControlPanel.test.tsx`
- Modify: `fe/src/modules/clocktower/StorytellerGrimoirePage.tsx`

- [ ] **Step 1: Add failing night/day behavior tests**

Mock `getClocktowerMicSession` and `startClocktowerDayMic`, then test the exported phase guards through their actual async helpers:

```ts
await expect(loadStorytellerMicSession(11, 'FIRST_NIGHT')).resolves.toBeNull()
await expect(loadStorytellerMicSession(11, 'NIGHT')).resolves.toBeNull()
expect(getClocktowerMicSession).not.toHaveBeenCalled()

await expect(startStorytellerMicSession(11, 'NIGHT')).resolves.toBeNull()
expect(startClocktowerDayMic).not.toHaveBeenCalled()

await loadStorytellerMicSession(11, 'DAY')
expect(getClocktowerMicSession).toHaveBeenCalledWith(11)
```

Update the content render test to pass `dayPhase`. Add a night case that verifies the explanation “白天阶段可开启公聊麦序” and a disabled start button.

- [ ] **Step 2: Run the mic test and confirm RED**

```bash
cd fe
bun run test -- src/modules/clocktower/components/StorytellerMicControlPanel.test.tsx
```

Expected: FAIL because the phase prop/helpers and disabled night state do not exist.

- [ ] **Step 3: Guard reads and starts by phase**

Change the container signature to accept the current phase:

```ts
export function StorytellerMicControlPanel({gameId, phase}: { gameId: number; phase: string })
```

Add small helpers and use them inside `refresh()` and the start branch of `runAction()`:

```ts
export function isClocktowerDayPhase(phase: string) {
    return phase === 'DAY'
}

export function loadStorytellerMicSession(gameId: number, phase: string) {
    if (!isClocktowerDayPhase(phase)) {
        return Promise.resolve(null)
    }
    return getClocktowerMicSession(gameId)
}

export function startStorytellerMicSession(gameId: number, phase: string) {
    if (!isClocktowerDayPhase(phase)) {
        return Promise.resolve(null)
    }
    return startClocktowerDayMic(gameId)
}
```

Requirements for the component logic:

- Include `phase` in `refresh` dependencies.
- On night phases, clear stale `session` and do not report a missing-session error.
- Pass `dayPhase={isClocktowerDayPhase(phase)}` to the content component.
- Disable “开启白天麦序” when `dayPhase` is false.
- Keep skip/extend/close rules unchanged for a real day session.
- Do not weaken backend phase validation; the client guard is UX orchestration, not authorization.

Update the only production call site at the same time so this task remains type-safe on its own:

```tsx
<StorytellerMicControlPanel gameId={view.gameId} phase={view.phase}/>
```

- [ ] **Step 4: Run focused tests and typecheck**

```bash
cd fe
bun run test -- src/modules/clocktower/components/StorytellerMicControlPanel.test.tsx
bun run typecheck
```

Expected: PASS. The phase prop is wired at the only production call site in this task, so no known typecheck failure is carried into the commit.

- [ ] **Step 5: Commit only the mic behavior task**

```bash
git commit --only \
  fe/src/modules/clocktower/components/StorytellerMicControlPanel.tsx \
  fe/src/modules/clocktower/components/StorytellerMicControlPanel.test.tsx \
  fe/src/modules/clocktower/StorytellerGrimoirePage.tsx \
  -m "fix(clocktower): guard mic controls by game phase"
```

## Task 5: Integrate Flow, Mic Phase, And Parent Reload

**Files:**

- Modify: `fe/src/modules/clocktower/StorytellerGrimoirePage.tsx`
- Modify: `fe/src/modules/clocktower/StorytellerGrimoirePage.test.tsx`
- Modify: `fe/src/modules/clocktower/ClocktowerRoomPlayPage.tsx`
- Modify: `fe/src/modules/clocktower/ClocktowerRoomPlayPage.test.tsx`

- [ ] **Step 1: Add failing structural integration assertions**

In `StorytellerGrimoirePage.test.tsx`, import `Tabs`, `isValidElement`, `StorytellerGameFlowPanel`, and `StorytellerMicControlPanel`. Call `StorytellerGameSurface(...)` directly (it has no hooks), recursively locate the raw `Tabs` React element, then inspect its `items` without forcing Ant Design to render inactive tabs.

Assert:

```ts
const onGameChanged = vi.fn().mockResolvedValue(undefined)
const surface = StorytellerGameSurface({
    roomName: 'Friday',
    view: storytellerGameView(),
    onGameChanged,
})
const tabs = findElementByType(surface, Tabs)
const flowItem = tabs.props.items.find((item) => item.key === 'flow')
const micItem = tabs.props.items.find((item) => item.key === 'mic')

expect(flowItem.children.type).toBe(StorytellerGameFlowPanel)
expect(flowItem.children.props).toMatchObject({gameId: 11, onGameChanged})
expect(micItem.children.type).toBe(StorytellerMicControlPanel)
expect(micItem.children.props).toMatchObject({gameId: 11, phase: 'NIGHT'})
```

Keep the recursive helper test-local. It should traverse arrays and `element.props.children`; do not add a production helper solely for testing.

In `ClocktowerRoomPlayPage.test.tsx`, call `ClocktowerRoomPlaySurface(...)` directly for storyteller mode and inspect the returned `StorytellerGameSurface` element:

```ts
const onReload = vi.fn().mockResolvedValue(undefined)
const surface = ClocktowerRoomPlaySurface({
    room,
    gameView: {...view, viewerMode: 'STORYTELLER'},
    onReload,
})

expect(surface.type).toBe(StorytellerGameSurface)
expect(surface.props.onGameChanged).toBe(onReload)
```

- [ ] **Step 2: Run the two page tests and confirm RED**

```bash
cd fe
bun run test -- \
  src/modules/clocktower/StorytellerGrimoirePage.test.tsx \
  src/modules/clocktower/ClocktowerRoomPlayPage.test.tsx
```

Expected: FAIL because the flow tab still contains static text and the reload callback is not propagated. The mic phase assertion added in Task 4 should already pass.

- [ ] **Step 3: Wire the approved components into the storyteller surface**

In `StorytellerGrimoirePage.tsx`:

- Import `StorytellerGameFlowPanel`.
- Add `onGameChanged?: () => Promise<void>` to `StorytellerGameSurface` props.
- Replace only the static `flow` tab children with:

```tsx
<StorytellerGameFlowPanel
    gameId={view.gameId}
    onGameChanged={onGameChanged}
/>
```

In `ClocktowerRoomPlayPage.tsx`, preserve the existing reload implementation and forward it:

```tsx
return (
    <StorytellerGameSurface
        onGameChanged={onReload}
        roomName={room.name}
        view={gameView}
    />
)
```

Do not change the player surface or legacy standalone storyteller/grimoire route.

- [ ] **Step 4: Run the complete focused frontend slice**

```bash
cd fe
bun run test -- \
  src/modules/clocktower/clocktowerService.test.ts \
  src/modules/clocktower/components/StorytellerGameFlowPanel.test.tsx \
  src/modules/clocktower/components/StorytellerMicControlPanel.test.tsx \
  src/modules/clocktower/StorytellerGrimoirePage.test.tsx \
  src/modules/clocktower/ClocktowerRoomPlayPage.test.tsx
bun run typecheck
```

Expected: PASS.

- [ ] **Step 5: Commit only the integration task**

```bash
git commit --only \
  fe/src/modules/clocktower/StorytellerGrimoirePage.tsx \
  fe/src/modules/clocktower/StorytellerGrimoirePage.test.tsx \
  fe/src/modules/clocktower/ClocktowerRoomPlayPage.tsx \
  fe/src/modules/clocktower/ClocktowerRoomPlayPage.test.tsx \
  -m "feat(clocktower): wire storyteller game flow controls"
```

## Task 6: Final Regression Verification And Workspace Audit

**Files:** No production changes expected.

- [ ] **Step 1: Run the focused backend regression slice**

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false \
  -Dtest=ClocktowerRbacResourceProviderTests,ClocktowerGameFlowServiceTests,ClocktowerAgentControlServiceTests,ClocktowerGameNightTaskServiceTests,ClocktowerNightResolutionServiceTests,ClocktowerPublicMicServiceTests \
  test
```

Expected: PASS. This verifies matcher coverage plus the existing agent, night-task, flow, owner, and automatic mic lifecycle behavior.

- [ ] **Step 2: Run the full backend suite**

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false test
```

Expected: PASS. If an unrelated environment-dependent test fails, record the exact command, test, and failure rather than weakening the test or changing unrelated code.

- [ ] **Step 3: Run the full frontend suite and production checks**

```bash
cd fe
bun run test
bun run typecheck
bun run lint
bun run build
```

Expected: PASS. Do not run `bun run dev` or open a browser.

- [ ] **Step 4: Audit diff, commits, and preserved user changes**

```bash
git diff --check
git status --short --branch
git log --oneline --decorate -6
```

Confirm:

- only the approved Clocktower backend/frontend files changed in the five implementation commits;
- the user-owned staged `docs/clocktower_agent_tasks/**` files are still staged and were not included in those commits;
- `.gitignore` and unrelated untracked files retain their pre-task state;
- no Flyway file, dependency, global super-admin bypass, or force-advance UI was added;
- there is no uncommitted task code left.

- [ ] **Step 5: Report manual runtime checks without starting the project**

Ask the user to start the application and verify, in order:

1. `GET /api/clocktower/games/1/agents` no longer returns a pre-controller 403 for the owning storyteller.
2. `GET /api/clocktower/games/1/night-tasks` returns tasks or an empty list instead of 403.
3. In `FIRST_NIGHT`/`NIGHT`, the mic tab does not issue `GET /mic` and does not allow `POST /mic/start-day`.
4. With mandatory night tasks complete, “推进流程” changes the game to `DAY` and the refreshed page displays the day mic session.
5. A non-owner storyteller still receives `CLOCKTOWER_STORYTELLER_FORBIDDEN` from mutation services.

Do not create a verification-only commit.
