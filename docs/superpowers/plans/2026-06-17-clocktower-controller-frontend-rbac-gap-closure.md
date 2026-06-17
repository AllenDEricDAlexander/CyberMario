# Clocktower Controller Frontend RBAC Gap Closure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:
> executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the current Clocktower gaps where implemented backend controllers under `top.egon.mario.clocktower` are
missing frontend pages, service wrappers, menu routes, or managed RBAC resources.

**Architecture:** Keep Clocktower inside the existing Phase 1 boundary: backend code stays under
`be/src/main/java/top/egon/mario/clocktower`, frontend code stays under `fe/src/modules/clocktower`, and RBAC
declarations stay centralized in `ClocktowerRbacResourceProvider`. This plan maps only to controllers that already exist
today; deferred Agent autoplay, RAG-backed rule query, admin maintenance, and replay analytics remain separate plans.

**Tech Stack:** Java 21, Spring Boot WebFlux, existing RBAC resource sync, JUnit 5, AssertJ, React 19, React Router 7,
Ant Design 6, Bun, Vitest, TypeScript.

---

## Evidence And Boundary

This plan was written from the current repository state and these durable Clocktower design docs:

- `docs/superpowers/specs/2026-06-17-clocktower-agent-platform-design.md`
- `docs/superpowers/specs/2026-06-17-clocktower-api-and-module-detail-design.md`
- `docs/superpowers/specs/2026-06-17-clocktower-drools-rule-engine-detail-design.md`
- `docs/superpowers/plans/2026-06-17-clocktower-phase1-core.md`

Current implementation evidence:

- Backend controllers exist under `be/src/main/java/top/egon/mario/clocktower/**/web`.
- Frontend module exists under `fe/src/modules/clocktower`.
- Current routes in `fe/src/app/routes.tsx` include boards, rooms, room lobby, player room, grimoire, and replay detail.
- Current RBAC provider is `be/src/main/java/top/egon/mario/clocktower/resource/ClocktowerRbacResourceProvider.java`.
- Current admin menu local fallback is `fe/src/layouts/AdminLayout/menu.tsx`.

### Path Boundary

The design and RBAC provider use plural menu paths:

- `menu:clocktower:rules` -> `/clocktower/rules`
- `menu:clocktower:replays` -> `/clocktower/replays`

The user-facing question mentioned `/clocktower/rule` and `/clocktower/replay`. Treat those singular paths as
compatibility aliases only. Canonical implementation must use the plural paths above so frontend routes, admin menu
keys, and provider-managed menu resources do not diverge.

### Rule Page Boundary

There is no implemented `ClocktowerRuleQueryController` today. Therefore the rule page in this plan is a structured rule
data page backed by existing endpoints:

- `GET /api/clocktower/scripts`
- `GET /api/clocktower/scripts/{scriptCode}`
- `GET /api/clocktower/scripts/{scriptCode}/roles`
- `GET /api/clocktower/scripts/{scriptCode}/night-order`
- `GET /api/clocktower/terms`
- `GET /api/clocktower/jinx-rules`

Do not build RAG question answering, citations, `POST /api/clocktower/rules/query`, or
`GET /api/clocktower/rules/roles/{roleCode}` in this plan.

### Replay Page Boundary

`ClocktowerReplayController` exists with:

- `GET /api/clocktower/replays/{roomId}`
- `GET /api/clocktower/replays/{roomId}/votes`

The existing frontend has `ReplayPage.tsx`, but it only uses replay events. This plan adds the missing replay list entry
page and vote replay section. Do not build Agent action audit, model audit, or replay analytics in this plan.

### Super Admin RBAC Boundary

Do not add `SUPER_ADMIN` to `ClocktowerRbacResourceProvider.rolePresets()`. `RbacResourceSynchronizer` rejects business
providers that declare `SUPER_ADMIN`, and current admin bootstrap already gives the built-in super admin all existing
permissions through `RbacAdminBootstrap.grantAllPermissions(role)`. Frontend menu authorization also bypasses menu
checks for `SUPER_ADMIN` through `hasAdminPermissionBypass`.

The required fix is to ensure Clocktower resources are declared and synchronized. Once declared, super admin can
see/access them by bypass and by all-permission bootstrap. Functional Clocktower roles such as `CLOCKTOWER_PLAYER` and
`CLOCKTOWER_STORYTELLER` still need explicit Clocktower permission codes.

## Current Controller Coverage Map

### Backend Controller Endpoints

- `ClocktowerScriptController`
    - `GET /api/clocktower/scripts`
    - `GET /api/clocktower/scripts/{scriptCode}`
    - `GET /api/clocktower/scripts/{scriptCode}/roles`
    - `GET /api/clocktower/scripts/{scriptCode}/night-order`
    - `GET /api/clocktower/terms`
    - `GET /api/clocktower/jinx-rules`
- `ClocktowerBoardController`
    - `POST /api/clocktower/boards/generate`
    - `POST /api/clocktower/boards/validate`
    - `POST /api/clocktower/boards/save`
    - `GET /api/clocktower/boards`
    - `DELETE /api/clocktower/boards/{boardId}`
- `ClocktowerRoomController`
    - `POST /api/clocktower/rooms`
    - `GET /api/clocktower/rooms`
    - `GET /api/clocktower/rooms/{roomId}`
    - `POST /api/clocktower/rooms/{roomId}/start`
    - `POST /api/clocktower/rooms/{roomId}/join`
    - `POST /api/clocktower/rooms/{roomId}/leave`
    - `PATCH /api/clocktower/rooms/{roomId}/seats/{seatId}`
- `ClocktowerViewController`
    - `GET /api/clocktower/rooms/{roomId}/view`
- `ClocktowerActionController`
    - `POST /api/clocktower/rooms/{roomId}/actions`
- `ClocktowerEventStreamController`
    - `GET /api/clocktower/rooms/{roomId}/events/stream`
- `ClocktowerGrimoireController`
    - `GET /api/clocktower/rooms/{roomId}/grimoire`
    - `GET /api/clocktower/rooms/{roomId}/night-checklist`
    - `POST /api/clocktower/rooms/{roomId}/storyteller/actions`
- `ClocktowerReplayController`
    - `GET /api/clocktower/replays/{roomId}`
    - `GET /api/clocktower/replays/{roomId}/votes`

### Confirmed Gaps

- `ClocktowerRbacResourceProvider` declares script wildcard `/api/clocktower/scripts/**`, but not root rule data
  endpoints `/api/clocktower/terms` or `/api/clocktower/jinx-rules`.
- `CLOCKTOWER_PLAYER` and `CLOCKTOWER_STORYTELLER` presets do not include the missing rule data API permissions.
- There is no frontend route/page for canonical `/clocktower/rules`.
- There is no frontend route/page for canonical `/clocktower/replays`.
- `ReplayPage.tsx` does not call or render `GET /api/clocktower/replays/{roomId}/votes`.
- `clocktowerService.ts` has no `getClocktowerTerms`, `getClocktowerJinxRules`, or `getClocktowerReplayVotes`.
- `clocktowerTypes.ts` has no `ClocktowerVoteReplayResponse`.
- `BoardBuilderPage.tsx` generates candidates but does not use existing saved-board list/save/delete/validate endpoints.
- `RoomLobbyPage.tsx` joins and starts rooms but does not use existing leave-room or update-seat endpoints.
- `fe/src/layouts/AdminLayout/menu.tsx` currently has local Clocktower fallback entries only for boards and rooms. Rules
  and replay list must be added to match provider-managed menus.

## File Structure

### Backend

- Modify: `be/src/main/java/top/egon/mario/clocktower/resource/ClocktowerRbacResourceProvider.java`
    - Add explicit API resources for terms and jinx rules.
    - Add those codes to Clocktower player/storyteller role presets.
    - Keep `SUPER_ADMIN` out of this provider.
- Modify: `be/src/test/java/top/egon/mario/clocktower/resource/ClocktowerRbacResourceProviderTests.java`
    - Assert every currently implemented controller route has a provider API rule.
    - Assert role presets include the new read-only rule data APIs.
    - Assert no provider tries to declare `SUPER_ADMIN`.

### Frontend Shared Clocktower Module

- Modify: `fe/src/modules/clocktower/clocktowerTypes.ts`
    - Add `ClocktowerVoteReplayResponse`.
- Modify: `fe/src/modules/clocktower/clocktowerService.ts`
    - Add `getClocktowerTerms`.
    - Add `getClocktowerJinxRules`.
    - Add `getClocktowerReplayVotes`.
- Modify: `fe/src/modules/clocktower/clocktowerService.test.ts`
    - Add wrapper request-construction tests.

### Frontend Pages

- Create: `fe/src/modules/clocktower/RuleDataPage.tsx`
    - Structured rule data page for scripts, roles, night order, terms, and jinx rules.
- Create: `fe/src/modules/clocktower/RuleDataPage.test.tsx`
    - Static page-shell test.
- Create: `fe/src/modules/clocktower/ReplayListPage.tsx`
    - Existing-room-backed replay entry page linking to `/clocktower/replays/:roomId`.
- Create: `fe/src/modules/clocktower/ReplayListPage.test.tsx`
    - Static page-shell test.
- Modify: `fe/src/modules/clocktower/ReplayPage.tsx`
    - Load and render vote replay data.
- Create: `fe/src/modules/clocktower/ReplayPage.test.tsx`
    - Static page-shell test for timeline and vote section.
- Modify: `fe/src/modules/clocktower/BoardBuilderPage.tsx`
    - Add manual validation.
    - Add save generated candidate.
    - Add saved board list.
    - Add delete saved board.
- Modify: `fe/src/modules/clocktower/BoardBuilderPage.test.tsx`
    - Extend static page-shell assertions.
- Modify: `fe/src/modules/clocktower/components/BoardCandidateTable.tsx`
    - Add optional save action column.
- Modify: `fe/src/modules/clocktower/RoomLobbyPage.tsx`
    - Add leave-room action.
    - Add seat update modal.
- Create: `fe/src/modules/clocktower/RoomLobbyPage.test.tsx`
    - Static page-shell test for leave and seat update controls.

### Frontend Routing And Menu

- Modify: `fe/src/app/routes.tsx`
    - Add canonical `/clocktower/rules`.
    - Add canonical `/clocktower/replays`.
    - Add compatibility redirects from `/clocktower/rule` and `/clocktower/replay`.
- Modify: `fe/src/layouts/AdminLayout/menu.tsx`
    - Add local fallback menu keys/items for `/clocktower/rules` and `/clocktower/replays`.
- Modify: `fe/src/layouts/AdminLayout/menu.test.tsx`
    - Assert rules/replays menu visibility and nested replay detail authorization.

---

### Task 1: Close Clocktower RBAC API Gaps

**Files:**

- Modify: `be/src/main/java/top/egon/mario/clocktower/resource/ClocktowerRbacResourceProvider.java`
- Modify: `be/src/test/java/top/egon/mario/clocktower/resource/ClocktowerRbacResourceProviderTests.java`

- [ ] **Step 1: Write the failing provider test**

Modify `be/src/test/java/top/egon/mario/clocktower/resource/ClocktowerRbacResourceProviderTests.java` to assert the full
current controller-backed resource set:

```java
assertThat(resourceCodes)
        .contains("menu:clocktower:boards",
                "menu:clocktower:rooms",
                "menu:clocktower:rules",
                "menu:clocktower:replays",
                "api:clocktower:scripts:*",
                "api:clocktower:terms:read",
                "api:clocktower:jinx-rules:read",
                "api:clocktower:boards:*",
                "api:clocktower:rooms:read:list",
                "api:clocktower:rooms:read:detail",
                "api:clocktower:rooms:player:join",
                "api:clocktower:rooms:player:leave",
                "api:clocktower:rooms:player:view",
                "api:clocktower:rooms:player:action",
                "api:clocktower:rooms:storyteller:create",
                "api:clocktower:rooms:storyteller:start",
                "api:clocktower:rooms:storyteller:seat",
                "api:clocktower:rooms:storyteller:night",
                "api:clocktower:rooms:storyteller:action",
                "api:clocktower:events:stream",
                "api:clocktower:grimoire:*",
                "api:clocktower:replays:*")
        .doesNotContain("api:clocktower:rooms:*");
```

Add role preset assertions:

```java
assertThat(provider.rolePresets()).extracting(role -> role.roleCode())
        .contains("CLOCKTOWER_PLAYER", "CLOCKTOWER_STORYTELLER")
        .doesNotContain("SUPER_ADMIN");

assertThat(provider.rolePresets())
        .filteredOn(role -> role.roleCode().equals("CLOCKTOWER_PLAYER"))
        .singleElement()
        .satisfies(role -> {
            assertThat(role.permissionCodes()).contains(
                    "api:clocktower:scripts:*",
                    "api:clocktower:terms:read",
                    "api:clocktower:jinx-rules:read",
                    "api:clocktower:rooms:player:view");
            assertThat(role.permissionCodes())
                    .noneMatch(code -> code.startsWith("api:clocktower:rooms:storyteller:"));
        });

assertThat(provider.rolePresets())
        .filteredOn(role -> role.roleCode().equals("CLOCKTOWER_STORYTELLER"))
        .singleElement()
        .satisfies(role -> assertThat(role.permissionCodes()).contains(
                "menu:clocktower:rules",
                "menu:clocktower:replays",
                "api:clocktower:terms:read",
                "api:clocktower:jinx-rules:read"));
```

- [ ] **Step 2: Run the test and verify it fails**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/be
./mvnw -Dmaven.build.cache.enabled=false -Djava.version=21 -Dtest=top.egon.mario.clocktower.resource.ClocktowerRbacResourceProviderTests test
```

Expected: FAIL because `api:clocktower:terms:read` and `api:clocktower:jinx-rules:read` do not exist yet.

- [ ] **Step 3: Add missing provider resources**

In `be/src/main/java/top/egon/mario/clocktower/resource/ClocktowerRbacResourceProvider.java`, add these API seeds
immediately after the scripts seed:

```java
resources.add(api("api:clocktower:terms:read", "Clocktower terms", "GET",
        "/api/clocktower/terms", ApiRiskLevel.LOW));
resources.add(api("api:clocktower:jinx-rules:read", "Clocktower jinx rules", "GET",
        "/api/clocktower/jinx-rules", ApiRiskLevel.LOW));
```

Add both codes to `CLOCKTOWER_PLAYER` and `CLOCKTOWER_STORYTELLER` immediately after `"api:clocktower:scripts:*"`:

```java
"api:clocktower:terms:read", "api:clocktower:jinx-rules:read",
```

- [ ] **Step 4: Run the provider test and verify it passes**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/be
./mvnw -Dmaven.build.cache.enabled=false -Djava.version=21 -Dtest=top.egon.mario.clocktower.resource.ClocktowerRbacResourceProviderTests test
```

Expected: PASS.

- [ ] **Step 5: Commit**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario
git add be/src/main/java/top/egon/mario/clocktower/resource/ClocktowerRbacResourceProvider.java \
  be/src/test/java/top/egon/mario/clocktower/resource/ClocktowerRbacResourceProviderTests.java
git commit -m "fix(clocktower): cover rule data rbac resources"
```

### Task 2: Add Missing Frontend Service Wrappers

**Files:**

- Modify: `fe/src/modules/clocktower/clocktowerTypes.ts`
- Modify: `fe/src/modules/clocktower/clocktowerService.ts`
- Modify: `fe/src/modules/clocktower/clocktowerService.test.ts`

- [ ] **Step 1: Write failing service tests**

Add these names to the existing grouped import from `./clocktowerService` in
`fe/src/modules/clocktower/clocktowerService.test.ts`:

```ts
getClocktowerJinxRules,
getClocktowerReplayVotes,
getClocktowerTerms,
```

Add these tests inside `describe('clocktowerService', () => { ... })`:

```ts
it('loads terms with optional filters', async () => {
    const {requestJson} = await import('../../services/request')

    await getClocktowerTerms({keyword: 'poison', category: 'status'})

    expect(requestJson).toHaveBeenCalledWith('/api/clocktower/terms?keyword=poison&category=status')
})

it('loads jinx rules with optional filters', async () => {
    const {requestJson} = await import('../../services/request')

    await getClocktowerJinxRules({roleCode: 'washerwoman', severity: 'INFO'})

    expect(requestJson).toHaveBeenCalledWith('/api/clocktower/jinx-rules?roleCode=washerwoman&severity=INFO')
})

it('loads replay votes for a room', async () => {
    const {requestJson} = await import('../../services/request')

    await getClocktowerReplayVotes(7)

    expect(requestJson).toHaveBeenCalledWith('/api/clocktower/replays/7/votes')
})
```

- [ ] **Step 2: Run the service test and verify it fails**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/fe
bun test src/modules/clocktower/clocktowerService.test.ts
```

Expected: FAIL because the three service functions do not exist.

- [ ] **Step 3: Add the missing type**

Append this type after `ClocktowerReplayResponse` in `fe/src/modules/clocktower/clocktowerTypes.ts`:

```ts
export type ClocktowerVoteReplayResponse = {
    voteId: number
    nominationId: number
    voterSeatId: number
    voteValue: boolean
    usedDeadVote: boolean
    eventId?: number | null
}
```

- [ ] **Step 4: Add the service wrappers**

Add these type imports in `fe/src/modules/clocktower/clocktowerService.ts`:

```ts
ClocktowerJinxRuleResponse,
ClocktowerTermResponse,
ClocktowerVoteReplayResponse,
```

Add after `getClocktowerNightOrder`:

```ts
export function getClocktowerTerms(params: { keyword?: string; category?: string } = {}) {
    const search = buildSearchParams(params)
    return requestJson<ClocktowerTermResponse[]>(`/api/clocktower/terms${suffix(search)}`)
}

export function getClocktowerJinxRules(params: { roleCode?: string; severity?: string } = {}) {
    const search = buildSearchParams(params)
    return requestJson<ClocktowerJinxRuleResponse[]>(`/api/clocktower/jinx-rules${suffix(search)}`)
}
```

Add after `getClocktowerReplay`:

```ts
export function getClocktowerReplayVotes(roomId: number) {
    return requestJson<ClocktowerVoteReplayResponse[]>(`/api/clocktower/replays/${roomId}/votes`)
}
```

- [ ] **Step 5: Run service tests and typecheck**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/fe
bun test src/modules/clocktower/clocktowerService.test.ts
bun run typecheck
```

Expected: PASS.

- [ ] **Step 6: Commit**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario
git add fe/src/modules/clocktower/clocktowerTypes.ts \
  fe/src/modules/clocktower/clocktowerService.ts \
  fe/src/modules/clocktower/clocktowerService.test.ts
git commit -m "feat(clocktower): add rule data and replay vote clients"
```

### Task 3: Add Structured Rule Data Page

**Files:**

- Create: `fe/src/modules/clocktower/RuleDataPage.tsx`
- Create: `fe/src/modules/clocktower/RuleDataPage.test.tsx`

- [ ] **Step 1: Write the failing page test**

Create `fe/src/modules/clocktower/RuleDataPage.test.tsx`:

```tsx
import {renderToStaticMarkup} from 'react-dom/server'
import {describe, expect, test, vi} from 'vitest'
import {Component as RuleDataPage} from './RuleDataPage'

vi.mock('./clocktowerService', () => ({
    getClocktowerScripts: vi.fn().mockResolvedValue([
        {scriptCode: 'TROUBLE_BREWING', name: '暗流涌动', edition: 'BASE_3', minPlayers: 5, maxPlayers: 15, roleCount: 22, enabled: true},
    ]),
    getClocktowerRoles: vi.fn().mockResolvedValue([]),
    getClocktowerNightOrder: vi.fn().mockResolvedValue([]),
    getClocktowerTerms: vi.fn().mockResolvedValue([]),
    getClocktowerJinxRules: vi.fn().mockResolvedValue([]),
}))

describe('RuleDataPage', () => {
    test('renders structured rule data controls', () => {
        const markup = renderToStaticMarkup(<RuleDataPage/>)

        expect(markup).toContain('钟楼规则')
        expect(markup).toContain('剧本规则')
        expect(markup).toContain('术语')
        expect(markup).toContain('相克规则')
    })
})
```

- [ ] **Step 2: Run the page test and verify it fails**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/fe
bun test src/modules/clocktower/RuleDataPage.test.tsx
```

Expected: FAIL because `RuleDataPage.tsx` does not exist.

- [ ] **Step 3: Create the page implementation**

Create `fe/src/modules/clocktower/RuleDataPage.tsx`:

```tsx
import {ReloadOutlined, SearchOutlined} from '@ant-design/icons'
import {App, Button, Card, Form, Input, Select, Space, Table, Tabs, Tag} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {useEffect, useState} from 'react'
import {PageToolbar} from '../../components/PageToolbar'
import {resolveErrorMessage} from '../../services/request'
import {voidify} from '../../utils/async'
import {
    getClocktowerJinxRules,
    getClocktowerNightOrder,
    getClocktowerRoles,
    getClocktowerScripts,
    getClocktowerTerms,
} from './clocktowerService'
import type {
    ClocktowerJinxRuleResponse,
    ClocktowerNightOrderResponse,
    ClocktowerRoleResponse,
    ClocktowerRoleType,
    ClocktowerScriptCode,
    ClocktowerScriptResponse,
    ClocktowerTermResponse,
} from './clocktowerTypes'
import {RoleTypeTag} from './components/RoleTypeTag'

type TermFilters = {
    keyword?: string
    category?: string
}

type JinxFilters = {
    roleCode?: string
    severity?: string
}

const roleTypeOptions: Array<{ label: string; value: ClocktowerRoleType }> = [
    {label: '镇民', value: 'TOWNSFOLK'},
    {label: '外来者', value: 'OUTSIDER'},
    {label: '爪牙', value: 'MINION'},
    {label: '恶魔', value: 'DEMON'},
    {label: '旅行者', value: 'TRAVELER'},
    {label: '传奇角色', value: 'FABLED'},
]

function RuleDataPage() {
    const {message} = App.useApp()
    const [termForm] = Form.useForm<TermFilters>()
    const [jinxForm] = Form.useForm<JinxFilters>()
    const [scripts, setScripts] = useState<ClocktowerScriptResponse[]>([])
    const [scriptCode, setScriptCode] = useState<ClocktowerScriptCode>('TROUBLE_BREWING')
    const [roleType, setRoleType] = useState<ClocktowerRoleType>()
    const [roles, setRoles] = useState<ClocktowerRoleResponse[]>([])
    const [nightOrder, setNightOrder] = useState<ClocktowerNightOrderResponse[]>([])
    const [terms, setTerms] = useState<ClocktowerTermResponse[]>([])
    const [jinxRules, setJinxRules] = useState<ClocktowerJinxRuleResponse[]>([])
    const [loading, setLoading] = useState(false)

    useEffect(() => {
        void loadInitialData()
    }, [])

    useEffect(() => {
        void loadScriptData()
    }, [scriptCode, roleType])

    async function loadInitialData() {
        setLoading(true)
        try {
            const [scriptRows, termRows, jinxRows] = await Promise.all([
                getClocktowerScripts(),
                getClocktowerTerms(),
                getClocktowerJinxRules(),
            ])
            setScripts(scriptRows)
            setTerms(termRows)
            setJinxRules(jinxRows)
            if (scriptRows[0]?.scriptCode) {
                setScriptCode(scriptRows[0].scriptCode)
            }
        } catch (caught) {
            message.error(resolveErrorMessage(caught))
        } finally {
            setLoading(false)
        }
    }

    async function loadScriptData() {
        setLoading(true)
        try {
            const [roleRows, nightRows] = await Promise.all([
                getClocktowerRoles(scriptCode, {roleType, enabled: true}),
                getClocktowerNightOrder(scriptCode),
            ])
            setRoles(roleRows)
            setNightOrder(nightRows)
        } catch (caught) {
            message.error(resolveErrorMessage(caught))
        } finally {
            setLoading(false)
        }
    }

    async function searchTerms() {
        setLoading(true)
        try {
            setTerms(await getClocktowerTerms(termForm.getFieldsValue()))
        } catch (caught) {
            message.error(resolveErrorMessage(caught))
        } finally {
            setLoading(false)
        }
    }

    async function searchJinxRules() {
        setLoading(true)
        try {
            setJinxRules(await getClocktowerJinxRules(jinxForm.getFieldsValue()))
        } catch (caught) {
            message.error(resolveErrorMessage(caught))
        } finally {
            setLoading(false)
        }
    }

    return (
        <>
            <PageToolbar
                actions={<Button icon={<ReloadOutlined/>} loading={loading} onClick={voidify(loadInitialData)}>刷新</Button>}
                description="查看当前已结构化落库的剧本、角色、夜晚顺序、术语和相克规则。"
                title="钟楼规则"
            />
            <Tabs
                items={[
                    {
                        key: 'script',
                        label: '剧本规则',
                        children: (
                            <Space direction="vertical" style={{width: '100%'}}>
                                <Card>
                                    <Space wrap>
                                        <Select
                                            options={scripts.map((script) => ({label: script.name, value: script.scriptCode}))}
                                            onChange={setScriptCode}
                                            style={{width: 220}}
                                            value={scriptCode}
                                        />
                                        <Select
                                            allowClear
                                            options={roleTypeOptions}
                                            onChange={setRoleType}
                                            placeholder="角色类型"
                                            style={{width: 160}}
                                            value={roleType}
                                        />
                                    </Space>
                                </Card>
                                <RoleTable loading={loading} roles={roles}/>
                                <NightOrderTable loading={loading} rows={nightOrder}/>
                            </Space>
                        ),
                    },
                    {
                        key: 'terms',
                        label: '术语',
                        children: (
                            <Space direction="vertical" style={{width: '100%'}}>
                                <Card>
                                    <Form form={termForm} layout="inline">
                                        <Form.Item label="关键词" name="keyword">
                                            <Input allowClear/>
                                        </Form.Item>
                                        <Form.Item label="分类" name="category">
                                            <Input allowClear/>
                                        </Form.Item>
                                        <Button icon={<SearchOutlined/>} loading={loading} onClick={voidify(searchTerms)} type="primary">
                                            查询
                                        </Button>
                                    </Form>
                                </Card>
                                <TermTable loading={loading} rows={terms}/>
                            </Space>
                        ),
                    },
                    {
                        key: 'jinx',
                        label: '相克规则',
                        children: (
                            <Space direction="vertical" style={{width: '100%'}}>
                                <Card>
                                    <Form form={jinxForm} layout="inline">
                                        <Form.Item label="角色代码" name="roleCode">
                                            <Input allowClear/>
                                        </Form.Item>
                                        <Form.Item label="严重级别" name="severity">
                                            <Input allowClear/>
                                        </Form.Item>
                                        <Button icon={<SearchOutlined/>} loading={loading} onClick={voidify(searchJinxRules)} type="primary">
                                            查询
                                        </Button>
                                    </Form>
                                </Card>
                                <JinxRuleTable loading={loading} rows={jinxRules}/>
                            </Space>
                        ),
                    },
                ]}
            />
        </>
    )
}

function RoleTable({roles, loading}: { roles: ClocktowerRoleResponse[]; loading: boolean }) {
    const columns: ColumnsType<ClocktowerRoleResponse> = [
        {title: '角色', dataIndex: 'name', width: 160},
        {title: '代码', dataIndex: 'roleCode', width: 160, render: value => <Tag>{value}</Tag>},
        {title: '类型', dataIndex: 'roleType', width: 130, render: value => <RoleTypeTag value={value}/>},
        {title: '能力', dataIndex: 'abilityText'},
    ]
    return <Table columns={columns} dataSource={roles} loading={loading} rowKey="roleCode" scroll={{x: 900}}/>
}

function NightOrderTable({rows, loading}: { rows: ClocktowerNightOrderResponse[]; loading: boolean }) {
    const columns: ColumnsType<ClocktowerNightOrderResponse> = [
        {title: '顺序', dataIndex: 'sortOrder', width: 90},
        {title: '夜晚', dataIndex: 'nightType', width: 130, render: value => <Tag>{value}</Tag>},
        {title: '角色', dataIndex: 'roleName', width: 160},
        {title: '提醒', dataIndex: 'reminderText'},
    ]
    return <Table columns={columns} dataSource={rows} loading={loading} rowKey={row => `${row.nightType}-${row.roleCode}-${row.sortOrder}`} scroll={{x: 800}}/>
}

function TermTable({rows, loading}: { rows: ClocktowerTermResponse[]; loading: boolean }) {
    const columns: ColumnsType<ClocktowerTermResponse> = [
        {title: '术语', dataIndex: 'term', width: 160},
        {title: '分类', dataIndex: 'category', width: 140, render: value => <Tag>{value}</Tag>},
        {title: '说明', dataIndex: 'description'},
    ]
    return <Table columns={columns} dataSource={rows} loading={loading} rowKey={row => `${row.category}-${row.term}`} scroll={{x: 800}}/>
}

function JinxRuleTable({rows, loading}: { rows: ClocktowerJinxRuleResponse[]; loading: boolean }) {
    const columns: ColumnsType<ClocktowerJinxRuleResponse> = [
        {title: '角色 A', dataIndex: 'roleACode', width: 140, render: value => <Tag>{value}</Tag>},
        {title: '角色 B', dataIndex: 'roleBCode', width: 140, render: value => <Tag>{value}</Tag>},
        {title: '范围', dataIndex: 'scope', width: 120, render: value => <Tag>{value}</Tag>},
        {title: '级别', dataIndex: 'severity', width: 120, render: value => <Tag>{value}</Tag>},
        {title: '效果', dataIndex: 'effectType', width: 140},
        {title: '规则', dataIndex: 'ruleText'},
    ]
    return <Table columns={columns} dataSource={rows} loading={loading} rowKey={row => `${row.roleACode}-${row.roleBCode}-${row.severity}`} scroll={{x: 1000}}/>
}

export const Component = RuleDataPage
```

- [ ] **Step 4: Run the page test and typecheck**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/fe
bun test src/modules/clocktower/RuleDataPage.test.tsx
bun run typecheck
```

Expected: PASS.

- [ ] **Step 5: Commit**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario
git add fe/src/modules/clocktower/RuleDataPage.tsx \
  fe/src/modules/clocktower/RuleDataPage.test.tsx
git commit -m "feat(clocktower): add structured rule data page"
```

### Task 4: Add Replay List And Vote Replay Detail

**Files:**

- Create: `fe/src/modules/clocktower/ReplayListPage.tsx`
- Create: `fe/src/modules/clocktower/ReplayListPage.test.tsx`
- Modify: `fe/src/modules/clocktower/ReplayPage.tsx`
- Create: `fe/src/modules/clocktower/ReplayPage.test.tsx`

- [ ] **Step 1: Write failing replay list page test**

Create `fe/src/modules/clocktower/ReplayListPage.test.tsx`:

```tsx
import {renderToStaticMarkup} from 'react-dom/server'
import {describe, expect, test, vi} from 'vitest'
import {Component as ReplayListPage} from './ReplayListPage'

vi.mock('react-router', async () => {
    const actual = await vi.importActual<typeof import('react-router')>('react-router')
    return {...actual, useNavigate: () => vi.fn()}
})

vi.mock('./clocktowerService', () => ({
    listClocktowerRooms: vi.fn().mockResolvedValue([]),
}))

describe('ReplayListPage', () => {
    test('renders replay review entry surface', () => {
        const markup = renderToStaticMarkup(<ReplayListPage/>)

        expect(markup).toContain('钟楼回放复盘')
        expect(markup).toContain('房间')
        expect(markup).toContain('查看回放')
    })
})
```

- [ ] **Step 2: Write failing replay detail page test**

Create `fe/src/modules/clocktower/ReplayPage.test.tsx`:

```tsx
import {renderToStaticMarkup} from 'react-dom/server'
import {describe, expect, test, vi} from 'vitest'
import {Component as ReplayPage} from './ReplayPage'

vi.mock('react-router', async () => {
    const actual = await vi.importActual<typeof import('react-router')>('react-router')
    return {...actual, useParams: () => ({roomId: '7'})}
})

vi.mock('./clocktowerService', () => ({
    getClocktowerReplay: vi.fn().mockResolvedValue({roomId: 7, mode: 'PUBLIC', events: []}),
    getClocktowerReplayVotes: vi.fn().mockResolvedValue([]),
}))

describe('ReplayPage', () => {
    test('renders replay timeline and vote review sections', () => {
        const markup = renderToStaticMarkup(<ReplayPage/>)

        expect(markup).toContain('钟楼回放')
        expect(markup).toContain('事件时间线')
        expect(markup).toContain('投票复盘')
    })
})
```

- [ ] **Step 3: Run replay page tests and verify they fail**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/fe
bun test src/modules/clocktower/ReplayListPage.test.tsx src/modules/clocktower/ReplayPage.test.tsx
```

Expected: FAIL because `ReplayListPage.tsx` does not exist and `ReplayPage.tsx` does not render vote replay data.

- [ ] **Step 4: Add replay list page**

Create `fe/src/modules/clocktower/ReplayListPage.tsx`:

```tsx
import {EyeOutlined, ReloadOutlined} from '@ant-design/icons'
import {App, Button, Card, Space, Table, Tag} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {useEffect, useState} from 'react'
import {useNavigate} from 'react-router'
import {PageToolbar} from '../../components/PageToolbar'
import {resolveErrorMessage} from '../../services/request'
import {voidify} from '../../utils/async'
import {listClocktowerRooms} from './clocktowerService'
import type {ClocktowerRoomResponse, ClocktowerRoomStatus} from './clocktowerTypes'

const statusColors: Record<ClocktowerRoomStatus, string> = {
    LOBBY: 'default',
    SETUP: 'warning',
    RUNNING: 'processing',
    ENDED: 'success',
    ARCHIVED: 'default',
}

function ReplayListPage() {
    const navigate = useNavigate()
    const {message} = App.useApp()
    const [rooms, setRooms] = useState<ClocktowerRoomResponse[]>([])
    const [loading, setLoading] = useState(false)

    useEffect(() => {
        void loadRooms()
    }, [])

    async function loadRooms() {
        setLoading(true)
        try {
            setRooms(await listClocktowerRooms())
        } catch (caught) {
            message.error(resolveErrorMessage(caught))
        } finally {
            setLoading(false)
        }
    }

    const columns: ColumnsType<ClocktowerRoomResponse> = [
        {
            title: '房间',
            dataIndex: 'name',
            width: 220,
            render: (_, record) => (
                <Space direction="vertical" size={0}>
                    <span>{record.name}</span>
                    <Tag>{record.roomCode}</Tag>
                </Space>
            ),
        },
        {title: '剧本', dataIndex: 'scriptCode', width: 180},
        {
            title: '状态',
            dataIndex: 'status',
            width: 120,
            render: (value: ClocktowerRoomStatus) => <Tag color={statusColors[value]}>{value}</Tag>,
        },
        {title: '阶段', dataIndex: 'phase', width: 130, render: value => <Tag color="blue">{value}</Tag>},
        {
            title: '操作',
            fixed: 'right',
            width: 140,
            render: (_, record) => (
                <Button
                    icon={<EyeOutlined/>}
                    onClick={() => navigate(`/clocktower/replays/${record.roomId}`)}
                    size="small"
                    type="primary"
                >
                    查看回放
                </Button>
            ),
        },
    ]

    return (
        <>
            <PageToolbar
                actions={<Button icon={<ReloadOutlined/>} loading={loading} onClick={voidify(loadRooms)}>刷新</Button>}
                description="从已有房间进入事件回放与投票复盘。"
                title="钟楼回放复盘"
            />
            <Card>
                <Table columns={columns} dataSource={rooms} loading={loading} rowKey="roomId" scroll={{x: 900}}/>
            </Card>
        </>
    )
}

export const Component = ReplayListPage
```

- [ ] **Step 5: Extend replay detail with votes**

Modify `fe/src/modules/clocktower/ReplayPage.tsx`.

Update imports:

```tsx
import {App, Button, Card, Col, Form, InputNumber, Row, Select, Space, Table, Tag, Typography} from 'antd'
import {getClocktowerReplay, getClocktowerReplayVotes} from './clocktowerService'
import type {ClocktowerEventResponse, ClocktowerReplayResponse, ClocktowerVoteReplayResponse} from './clocktowerTypes'
```

Add state:

```tsx
const [votes, setVotes] = useState<ClocktowerVoteReplayResponse[]>([])
```

Replace the replay load body after `const values = form.getFieldsValue()`:

```tsx
const [response, voteRows] = await Promise.all([
    getClocktowerReplay(numericRoomId, values),
    getClocktowerReplayVotes(numericRoomId),
])
setReplay(response)
setVotes(voteRows)
setSelected(response.events[0] ?? null)
```

Add a vote table card after the existing `Row`:

```tsx
<Card style={{marginTop: 16}} title="投票复盘">
    <Table
        columns={[
            {title: '投票 ID', dataIndex: 'voteId', width: 100},
            {title: '提名 ID', dataIndex: 'nominationId', width: 110},
            {title: '投票座位', dataIndex: 'voterSeatId', width: 110},
            {title: '投票', dataIndex: 'voteValue', width: 100, render: (value: boolean) => value ? <Tag color="success">赞成</Tag> : <Tag>反对</Tag>},
            {title: '死票', dataIndex: 'usedDeadVote', width: 100, render: (value: boolean) => value ? <Tag color="warning">已使用</Tag> : <Tag>未使用</Tag>},
            {title: '事件 ID', dataIndex: 'eventId', width: 100, render: value => value ?? '-'},
        ]}
        dataSource={votes}
        loading={loading}
        pagination={false}
        rowKey="voteId"
        scroll={{x: 720}}
    />
</Card>
```

- [ ] **Step 6: Run replay tests and typecheck**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/fe
bun test src/modules/clocktower/ReplayListPage.test.tsx src/modules/clocktower/ReplayPage.test.tsx
bun run typecheck
```

Expected: PASS.

- [ ] **Step 7: Commit**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario
git add fe/src/modules/clocktower/ReplayListPage.tsx \
  fe/src/modules/clocktower/ReplayListPage.test.tsx \
  fe/src/modules/clocktower/ReplayPage.tsx \
  fe/src/modules/clocktower/ReplayPage.test.tsx
git commit -m "feat(clocktower): add replay review surfaces"
```

### Task 5: Add Save Action To Board Candidate Table

**Files:**

- Modify: `fe/src/modules/clocktower/components/BoardCandidateTable.tsx`

- [ ] **Step 1: Update the component props and columns**

Modify `fe/src/modules/clocktower/components/BoardCandidateTable.tsx`.

Change imports:

```tsx
import {Button, Table, Tag} from 'antd'
```

Change props:

```tsx
type BoardCandidateTableProps = {
    candidates: ClocktowerBoardCandidateResponse[]
    loading?: boolean
    savingCandidateId?: string
    onSave?: (candidate: ClocktowerBoardCandidateResponse) => Promise<void>
}
```

Change the function signature:

```tsx
export function BoardCandidateTable({candidates, loading, savingCandidateId, onSave}: BoardCandidateTableProps) {
```

Add this column at the end of the `columns` array:

```tsx
{
    title: '操作',
    fixed: 'right',
    width: 110,
    render: (_, record) => (
        <Button
            disabled={!onSave}
            loading={savingCandidateId === record.candidateId}
            onClick={() => void onSave?.(record)}
            size="small"
            type="primary"
        >
            保存
        </Button>
    ),
},
```

Change table scroll width:

```tsx
scroll={{x: 1020}}
```

- [ ] **Step 2: Run existing board page test**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/fe
bun test src/modules/clocktower/BoardBuilderPage.test.tsx
```

Expected: PASS because the new prop is optional and current board page has not used it yet.

- [ ] **Step 3: Commit**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario
git add fe/src/modules/clocktower/components/BoardCandidateTable.tsx
git commit -m "feat(clocktower): add candidate save action slot"
```

### Task 6: Finish Board Builder Saved Board Workflow

**Files:**

- Modify: `fe/src/modules/clocktower/BoardBuilderPage.tsx`
- Modify: `fe/src/modules/clocktower/BoardBuilderPage.test.tsx`

- [ ] **Step 1: Write failing page assertions**

Modify the service mock in `fe/src/modules/clocktower/BoardBuilderPage.test.tsx`:

```tsx
vi.mock('./clocktowerService', () => ({
    getClocktowerScripts: vi.fn().mockResolvedValue([
        {scriptCode: 'TROUBLE_BREWING', name: '暗流涌动', edition: 'BASE_3', minPlayers: 5, maxPlayers: 15, roleCount: 22, enabled: true},
    ]),
    generateClocktowerBoard: vi.fn(),
    validateClocktowerBoard: vi.fn(),
    saveClocktowerBoard: vi.fn(),
    listClocktowerBoards: vi.fn().mockResolvedValue([]),
    deleteClocktowerBoard: vi.fn(),
}))
```

Extend the assertions:

```tsx
expect(markup).toContain('钟楼配板')
expect(markup).toContain('剧本')
expect(markup).toContain('人数')
expect(markup).toContain('生成配板')
expect(markup).toContain('手动校验')
expect(markup).toContain('已保存配板')
```

- [ ] **Step 2: Run the board page test and verify it fails**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/fe
bun test src/modules/clocktower/BoardBuilderPage.test.tsx
```

Expected: FAIL because the page does not render manual validation or saved boards.

- [ ] **Step 3: Add imports, state, and initial loading**

Modify `fe/src/modules/clocktower/BoardBuilderPage.tsx`.

Change imports:

```tsx
import {ExperimentOutlined, ReloadOutlined} from '@ant-design/icons'
import {Alert, App, Button, Card, Form, Input, InputNumber, Popconfirm, Select, Space, Switch, Table, Tag} from 'antd'
import type {ColumnsType} from 'antd/es/table'
```

Change service imports:

```tsx
import {
    deleteClocktowerBoard,
    generateClocktowerBoard,
    getClocktowerScripts,
    listClocktowerBoards,
    saveClocktowerBoard,
    validateClocktowerBoard,
} from './clocktowerService'
```

Change type imports:

```tsx
BoardValidationResponse,
ClocktowerBoardCandidateResponse,
ClocktowerBoardConfigResponse,
ClocktowerBoardGenerateRequest,
ClocktowerScriptCode,
ClocktowerScriptResponse,
```

Add above the component:

```tsx
type ValidateFormValues = {
    scriptCode: ClocktowerScriptCode
    playerCount: number
    roleCodesText?: string
}
```

Add inside `BoardBuilderPage`:

```tsx
const {message} = App.useApp()
const [validateForm] = Form.useForm<ValidateFormValues>()
const [savedBoards, setSavedBoards] = useState<ClocktowerBoardConfigResponse[]>([])
const [validation, setValidation] = useState<BoardValidationResponse>()
const [savingCandidateId, setSavingCandidateId] = useState<string>()
```

Replace the first effect:

```tsx
useEffect(() => {
    void loadInitialData()
}, [])
```

Add:

```tsx
async function loadInitialData() {
    await Promise.all([loadScripts(), loadSavedBoards()])
}

async function loadSavedBoards() {
    try {
        setSavedBoards(await listClocktowerBoards())
    } catch (caught) {
        setError(resolveErrorMessage(caught))
    }
}
```

- [ ] **Step 4: Add validation, save, and delete handlers**

Add these functions inside `BoardBuilderPage`:

```tsx
async function validateManualBoard() {
    const values = await validateForm.validateFields()
    setLoading(true)
    setError(undefined)
    try {
        setValidation(await validateClocktowerBoard({
            scriptCode: values.scriptCode,
            playerCount: values.playerCount,
            roleCodes: parseRoleCodes(values.roleCodesText),
        }))
    } catch (caught) {
        setError(resolveErrorMessage(caught))
    } finally {
        setLoading(false)
    }
}

async function saveCandidate(candidate: ClocktowerBoardCandidateResponse) {
    const values = form.getFieldsValue()
    setSavingCandidateId(candidate.candidateId)
    setError(undefined)
    try {
        await saveClocktowerBoard({
            scriptCode: candidate.scriptCode,
            playerCount: candidate.playerCount,
            difficulty: values.difficulty,
            chaos: values.chaos,
            evilPressure: values.evilPressure,
            newbieFriendly: values.newbieFriendly,
            seed: values.seed,
            roleCodes: candidate.roleCodes,
            validation: candidate.validation,
        })
        message.success('配板已保存')
        await loadSavedBoards()
    } catch (caught) {
        setError(resolveErrorMessage(caught))
    } finally {
        setSavingCandidateId(undefined)
    }
}

async function deleteSavedBoard(boardId: number) {
    setError(undefined)
    try {
        await deleteClocktowerBoard(boardId)
        message.success('配板已删除')
        await loadSavedBoards()
    } catch (caught) {
        setError(resolveErrorMessage(caught))
    }
}
```

Add after `scriptOptions`:

```tsx
function parseRoleCodes(text?: string) {
    return (text ?? '')
        .split(/[,\n]/)
        .map(item => item.trim())
        .filter(Boolean)
}

function countSummary(counts: Record<string, number> | undefined) {
    if (!counts) {
        return '-'
    }
    return Object.entries(counts)
        .map(([key, value]) => `${key}:${value}`)
        .join(' ')
}

function savedBoardColumns(onDelete: (boardId: number) => Promise<void>): ColumnsType<ClocktowerBoardConfigResponse> {
    return [
        {title: '编号', dataIndex: 'boardCode', width: 160, render: value => <Tag>{value}</Tag>},
        {title: '剧本', dataIndex: 'scriptCode', width: 180},
        {title: '人数', dataIndex: 'playerCount', width: 90},
        {
            title: '角色',
            dataIndex: 'roleCodes',
            render: (roleCodes: string[]) => roleCodes.map(roleCode => <Tag key={roleCode}>{roleCode}</Tag>),
        },
        {
            title: '操作',
            fixed: 'right',
            width: 100,
            render: (_, record) => (
                <Popconfirm title="删除该配板？" onConfirm={() => void onDelete(record.boardId)}>
                    <Button danger size="small">删除</Button>
                </Popconfirm>
            ),
        },
    ]
}
```

- [ ] **Step 5: Render manual validation and saved boards**

Insert this card before the candidate table:

```tsx
<Card style={{marginTop: 16}} title="手动校验">
    <Form
        form={validateForm}
        initialValues={{scriptCode: 'TROUBLE_BREWING', playerCount: 5}}
        layout="vertical"
    >
        <Space align="end" wrap>
            <Form.Item label="剧本" name="scriptCode" rules={[{required: true, message: '请选择剧本'}]}>
                <Select options={scriptOptions(scripts)} style={{width: 220}}/>
            </Form.Item>
            <Form.Item label="人数" name="playerCount" rules={[{required: true}]}>
                <InputNumber min={5} max={15}/>
            </Form.Item>
            <Form.Item label="角色代码" name="roleCodesText">
                <Input.TextArea placeholder="用逗号或换行分隔角色代码" rows={2}/>
            </Form.Item>
            <Button loading={loading} onClick={voidify(validateManualBoard)} type="primary">手动校验</Button>
        </Space>
    </Form>
    {validation && (
        <Alert
            message={validation.valid ? '校验通过' : '校验存在问题'}
            description={validation.issues.map(issue => issue.message).join('；') || countSummary(validation.typeCounts)}
            showIcon
            style={{marginTop: 12}}
            type={validation.valid ? 'success' : 'warning'}
        />
    )}
</Card>
```

Replace the candidate table with:

```tsx
<Card style={{marginTop: 16}} title="候选配板">
    <BoardCandidateTable
        candidates={candidates}
        loading={loading}
        onSave={saveCandidate}
        savingCandidateId={savingCandidateId}
    />
</Card>
```

Add after the candidate card:

```tsx
<Card style={{marginTop: 16}} title="已保存配板">
    <Table
        columns={savedBoardColumns(deleteSavedBoard)}
        dataSource={savedBoards}
        loading={loading}
        rowKey="boardId"
        scroll={{x: 900}}
    />
</Card>
```

- [ ] **Step 6: Run board tests and typecheck**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/fe
bun test src/modules/clocktower/BoardBuilderPage.test.tsx
bun run typecheck
```

Expected: PASS.

- [ ] **Step 7: Commit**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario
git add fe/src/modules/clocktower/BoardBuilderPage.tsx \
  fe/src/modules/clocktower/BoardBuilderPage.test.tsx
git commit -m "feat(clocktower): complete board management workflow"
```

### Task 7: Finish Room Lobby Leave And Seat Update Workflow

**Files:**

- Modify: `fe/src/modules/clocktower/RoomLobbyPage.tsx`
- Create: `fe/src/modules/clocktower/RoomLobbyPage.test.tsx`

- [ ] **Step 1: Write failing lobby page test**

Create `fe/src/modules/clocktower/RoomLobbyPage.test.tsx`:

```tsx
import {renderToStaticMarkup} from 'react-dom/server'
import {describe, expect, test, vi} from 'vitest'
import {Component as RoomLobbyPage} from './RoomLobbyPage'

vi.mock('react-router', async () => {
    const actual = await vi.importActual<typeof import('react-router')>('react-router')
    return {...actual, useParams: () => ({roomId: '7'}), useNavigate: () => vi.fn()}
})

vi.mock('./clocktowerService', () => ({
    getClocktowerRoom: vi.fn().mockResolvedValue({
        roomId: 7,
        roomCode: 'ABC123',
        name: '测试房间',
        scriptCode: 'TROUBLE_BREWING',
        status: 'LOBBY',
        phase: 'LOBBY',
        playerCount: 5,
        seats: [],
    }),
    joinClocktowerRoom: vi.fn(),
    leaveClocktowerRoom: vi.fn(),
    startClocktowerGame: vi.fn(),
    updateClocktowerSeat: vi.fn(),
}))

describe('RoomLobbyPage', () => {
    test('renders leave and seat update controls', () => {
        const markup = renderToStaticMarkup(<RoomLobbyPage/>)

        expect(markup).toContain('房间大厅')
        expect(markup).toContain('离开房间')
        expect(markup).toContain('调整座位')
    })
})
```

- [ ] **Step 2: Run lobby page test and verify it fails**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/fe
bun test src/modules/clocktower/RoomLobbyPage.test.tsx
```

Expected: FAIL because the page does not render `离开房间` or `调整座位`.

- [ ] **Step 3: Add imports and state**

Modify `fe/src/modules/clocktower/RoomLobbyPage.tsx`.

Update icon import:

```tsx
import {EditOutlined, LoginOutlined, LogoutOutlined, PlayCircleOutlined, ReloadOutlined} from '@ant-design/icons'
```

Update Ant Design import:

```tsx
import {App, Button, Card, Col, Empty, Form, Input, InputNumber, List, Modal, Row, Space, Tag, Typography} from 'antd'
```

Update service imports:

```tsx
import {
    getClocktowerRoom,
    joinClocktowerRoom,
    leaveClocktowerRoom,
    startClocktowerGame,
    updateClocktowerSeat,
} from './clocktowerService'
```

Update type import:

```tsx
import type {ClocktowerRoomResponse, ClocktowerSeatResponse, ClocktowerUpdateSeatRequest} from './clocktowerTypes'
```

Add inside `RoomLobbyPage`:

```tsx
const [seatForm] = Form.useForm<ClocktowerUpdateSeatRequest>()
const [selectedSeat, setSelectedSeat] = useState<ClocktowerSeatResponse>()
const [seatEditorOpen, setSeatEditorOpen] = useState(false)
const [updatingSeat, setUpdatingSeat] = useState(false)
const [leaving, setLeaving] = useState(false)
```

- [ ] **Step 4: Add leave and update handlers**

Add inside `RoomLobbyPage`:

```tsx
async function leaveRoom() {
    if (!Number.isFinite(numericRoomId)) {
        return
    }
    setLeaving(true)
    try {
        await leaveClocktowerRoom(numericRoomId)
        message.success('已离开房间')
        await loadRoom()
    } catch (caught) {
        message.error(resolveErrorMessage(caught))
    } finally {
        setLeaving(false)
    }
}

function openSeatEditor(seat: ClocktowerSeatResponse) {
    setSelectedSeat(seat)
    seatForm.setFieldsValue({
        displayName: seat.displayName,
        seatNo: seat.seatNo,
    })
    setSeatEditorOpen(true)
}

async function saveSeatUpdate() {
    if (!selectedSeat || !Number.isFinite(numericRoomId)) {
        return
    }
    const values = await seatForm.validateFields()
    setUpdatingSeat(true)
    try {
        await updateClocktowerSeat(numericRoomId, selectedSeat.seatId, values)
        message.success('座位已更新')
        setSeatEditorOpen(false)
        await loadRoom()
    } catch (caught) {
        message.error(resolveErrorMessage(caught))
    } finally {
        setUpdatingSeat(false)
    }
}
```

- [ ] **Step 5: Render controls and modal**

Add this button to `PageToolbar` actions:

```tsx
<Button
    danger
    icon={<LogoutOutlined/>}
    loading={leaving}
    onClick={voidify(leaveRoom)}
>
    离开房间
</Button>
```

Change the `SeatList` usage:

```tsx
{room ? <SeatList onEdit={openSeatEditor} seats={room.seats}/> : <Empty description="暂无房间数据"/>}
```

Add this modal before the closing fragment:

```tsx
<Modal
    confirmLoading={updatingSeat}
    onCancel={() => setSeatEditorOpen(false)}
    onOk={voidify(saveSeatUpdate)}
    open={seatEditorOpen}
    title="调整座位"
>
    <Form form={seatForm} layout="vertical">
        <Form.Item label="显示名称" name="displayName">
            <Input/>
        </Form.Item>
        <Form.Item label="座位号" name="seatNo">
            <InputNumber min={1} max={room?.playerCount ?? 15} style={{width: '100%'}}/>
        </Form.Item>
    </Form>
</Modal>
```

Change `SeatList` signature:

```tsx
function SeatList({seats, onEdit}: { seats: ClocktowerSeatResponse[]; onEdit: (seat: ClocktowerSeatResponse) => void }) {
```

Add this button in the seat row `Space`:

```tsx
<Button icon={<EditOutlined/>} onClick={() => onEdit(seat)} size="small">调整座位</Button>
```

- [ ] **Step 6: Run lobby test and typecheck**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/fe
bun test src/modules/clocktower/RoomLobbyPage.test.tsx
bun run typecheck
```

Expected: PASS.

- [ ] **Step 7: Commit**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario
git add fe/src/modules/clocktower/RoomLobbyPage.tsx \
  fe/src/modules/clocktower/RoomLobbyPage.test.tsx
git commit -m "feat(clocktower): complete room lobby controls"
```

### Task 8: Align Frontend Routes And Menu With RBAC Menus

**Files:**

- Modify: `fe/src/app/routes.tsx`
- Modify: `fe/src/layouts/AdminLayout/menu.tsx`
- Modify: `fe/src/layouts/AdminLayout/menu.test.tsx`

- [ ] **Step 1: Write failing menu assertions**

Modify the `shows clocktower pages when clocktower menu permissions are present` test in
`fe/src/layouts/AdminLayout/menu.test.tsx`.

Add these menu entries to `clocktowerMenuTree`:

```ts
{
    permissionId: 22,
    permCode: 'menu:clocktower:rules',
    permName: '钟楼规则',
    routePath: '/clocktower/rules',
    hidden: false,
    cacheable: true,
    sortNo: 22,
    children: [],
},
{
    permissionId: 23,
    permCode: 'menu:clocktower:replays',
    permName: '钟楼回放',
    routePath: '/clocktower/replays',
    hidden: false,
    cacheable: true,
    sortNo: 23,
    children: [],
},
```

Add expectations:

```ts
expect(clocktowerKeys).toContain('/clocktower/rules')
expect(clocktowerKeys).toContain('/clocktower/replays')
expect(canAccessAdminPath('/clocktower/replays/7', clocktowerMenuTree, false, ['CLOCKTOWER_STORYTELLER']))
    .toBe(true)
```

- [ ] **Step 2: Run menu test and verify it fails**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/fe
bun test src/layouts/AdminLayout/menu.test.tsx --testNamePattern "shows clocktower pages"
```

Expected: FAIL because local fallback menu keys/items are missing for rules and replay list.

- [ ] **Step 3: Add canonical and compatibility routes**

Modify `fe/src/app/routes.tsx`.

Add canonical routes next to the existing Clocktower routes:

```tsx
{path: 'clocktower/rules', lazy: () => import('../modules/clocktower/RuleDataPage')},
{path: 'clocktower/replays', lazy: () => import('../modules/clocktower/ReplayListPage')},
```

Add compatibility redirects for singular paths:

```tsx
{path: 'clocktower/rule', element: <Navigate replace to="/clocktower/rules"/>},
{path: 'clocktower/replay', element: <Navigate replace to="/clocktower/replays"/>},
```

The Clocktower block should contain:

```tsx
{path: 'clocktower/boards', lazy: () => import('../modules/clocktower/BoardBuilderPage')},
{path: 'clocktower/rooms', lazy: () => import('../modules/clocktower/RoomListPage')},
{path: 'clocktower/rooms/:roomId/lobby', lazy: () => import('../modules/clocktower/RoomLobbyPage')},
{path: 'clocktower/rooms/:roomId/play', lazy: () => import('../modules/clocktower/GameRoomPage')},
{path: 'clocktower/rooms/:roomId/grimoire', lazy: () => import('../modules/clocktower/StorytellerGrimoirePage')},
{path: 'clocktower/rule', element: <Navigate replace to="/clocktower/rules"/>},
{path: 'clocktower/rules', lazy: () => import('../modules/clocktower/RuleDataPage')},
{path: 'clocktower/replay', element: <Navigate replace to="/clocktower/replays"/>},
{path: 'clocktower/replays', lazy: () => import('../modules/clocktower/ReplayListPage')},
{path: 'clocktower/replays/:roomId', lazy: () => import('../modules/clocktower/ReplayPage')},
```

- [ ] **Step 4: Add menu keys and items**

Modify `fe/src/layouts/AdminLayout/menu.tsx`.

Add icon import:

```tsx
ReadOutlined,
```

`FileSearchOutlined` is already imported in the current file; reuse it.

Add paths to `menuPathByKey`:

```tsx
'/clocktower/rules': '/clocktower/rules',
'/clocktower/replays': '/clocktower/replays',
```

Add children under the existing Clocktower group after rooms:

```tsx
{
    key: '/clocktower/rules',
    icon: <ReadOutlined/>,
    label: '钟楼规则',
},
{
    key: '/clocktower/replays',
    icon: <FileSearchOutlined/>,
    label: '钟楼回放',
},
```

- [ ] **Step 5: Run menu tests and typecheck**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/fe
bun test src/layouts/AdminLayout/menu.test.tsx
bun run typecheck
```

Expected: PASS.

- [ ] **Step 6: Commit**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario
git add fe/src/app/routes.tsx \
  fe/src/layouts/AdminLayout/menu.tsx \
  fe/src/layouts/AdminLayout/menu.test.tsx
git commit -m "feat(clocktower): align routes with clocktower menus"
```

### Task 9: Final Targeted Verification

**Files:**

- No source edits expected.

- [ ] **Step 1: Run frontend Clocktower tests**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/fe
bun test src/modules/clocktower src/layouts/AdminLayout/menu.test.tsx
```

Expected: PASS.

- [ ] **Step 2: Run frontend typecheck**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/fe
bun run typecheck
```

Expected: PASS.

- [ ] **Step 3: Run backend Clocktower RBAC test**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/be
./mvnw -Dmaven.build.cache.enabled=false -Djava.version=21 -Dtest=top.egon.mario.clocktower.resource.ClocktowerRbacResourceProviderTests test
```

Expected: PASS.

- [ ] **Step 4: Run a narrow backend Clocktower safety set**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/be
./mvnw -Dmaven.build.cache.enabled=false -Djava.version=21 \
  -Dtest=top.egon.mario.clocktower.ClocktowerCoreFlowTests,top.egon.mario.clocktower.replay.ClocktowerReplayServiceTests,top.egon.mario.clocktower.room.ClocktowerRoomServiceTests,top.egon.mario.clocktower.board.ClocktowerBoardControllerTests \
  test
```

Expected: PASS. If this fails because unrelated Spring context or local environment drift blocks test startup, rerun the
most specific failing class and report the first failing stack trace.

- [ ] **Step 5: Review changed files**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario
git status --short
git diff --stat
```

Expected: only files from this plan are changed. Do not revert unrelated user changes.

---

## Self-Review

### Spec Coverage

- Existing backend controllers under `top.egon.mario.clocktower` are covered by RBAC resource assertions in Task 1.
- Existing script/rule-data endpoints are covered by Task 2 and Task 3.
- Existing replay controller endpoints are covered by Task 2 and Task 4.
- Existing board endpoints are covered by Task 5 and Task 6.
- Existing room leave/update endpoints are covered by Task 7.
- Existing route/menu gaps are covered by Task 8.
- Super admin behavior is covered by boundary notes and by explicitly not declaring `SUPER_ADMIN` in Clocktower
  provider.

### Out Of Scope Requirements

- `ClocktowerRuleQueryController` and RAG-backed rule answering are not included because no backend controller exists
  yet and Phase 1 docs defer rule RAG.
- Agent assign, Agent step, and autoplay are not included because no backend controllers exist yet and Phase 1 docs
  defer advanced Agent autoplay.
- Admin rule maintenance, admin audit, replay analytics, and new DB migrations are not included because the current gaps
  can be closed with existing schema, controllers, services, and provider declarations.

### Placeholder Scan

This plan avoids placeholder-only work items and vague error-handling instructions. Every implementation task names
exact files, concrete code, commands, expected test state, and commit scope.

### Type Consistency

- `ClocktowerVoteReplayResponse` matches
  `be/src/main/java/top/egon/mario/clocktower/replay/dto/ClocktowerVoteReplayResponse.java`.
- `ClocktowerTermResponse` and `ClocktowerJinxRuleResponse` already exist in `clocktowerTypes.ts`; Task 2 only imports
  and uses them.
- `BoardValidationResponse` uses `issues` and `typeCounts`, matching the existing frontend type and board validate
  endpoint.
- Canonical menu and route paths use `/clocktower/rules` and `/clocktower/replays`, matching
  `ClocktowerRbacResourceProvider`.
