# Nutrition Workspace Navigation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every existing family Nutrition page discoverable through a shared workspace tab bar while preserving the current coarse RBAC menu boundary and nutrition-scoped business authorization.

**Architecture:** Keep `AdminLayout` as the global authorization shell, change the stable Family Nutrition menu landing path to `/nutrition/home`, and canonicalize every family Nutrition route to that path. Add a Nutrition-owned pathless React Router layout that renders Ant Design Tabs above the ten existing family pages; keep Nutrition Platform outside that layout.

**Tech Stack:** Java 21, Spring Boot 3.5, JUnit 5, AssertJ, React 19, TypeScript 6, React Router 7, Ant Design 6, Vitest 4, Testing Library, Bun 1.3.

## Global Constraints

- Source of truth: `docs/superpowers/specs/2026-07-14-nutrition-workspace-navigation-design.md`.
- Execute in an isolated worktree on branch `codex/nutrition-workspace-navigation`.
- Preserve unrelated `.gitignore` and `docs/clocktower_agent_tasks` changes in the current `main` worktree.
- Do not add dependencies, state-management libraries, routing libraries, or build steps.
- Do not add or modify any Flyway migration.
- Keep permission codes `menu:nutrition`, `menu:nutrition:families`, and `menu:nutrition:platform` unchanged.
- Keep all existing Nutrition page URLs and business API behavior unchanged.
- Do not introduce a global `SUPER_ADMIN` family-data bypass.
- Do not start the backend, frontend dev server, or a browser.
- Complete each implementation task with exactly one commit.

## File Responsibility Map

- `be/src/main/java/top/egon/mario/nutrition/service/bootstrap/NutritionPermissionCatalog.java`: declares the provider-owned RBAC menu landing path.
- `be/src/test/java/top/egon/mario/nutrition/NutritionRbacResourceProviderTests.java`: proves stable Nutrition menu codes, role presets, and the `/nutrition/home` landing route.
- `fe/src/layouts/AdminLayout/menu.tsx`: maps the static sidebar item and all family Nutrition URLs to the same canonical authorized path.
- `fe/src/layouts/AdminLayout/menu.test.tsx`: proves family/platform menu separation, direct URL authorization, selected sidebar state, and super-admin behavior.
- `fe/src/modules/nutrition/NutritionWorkspaceLayout.tsx`: owns the ten family workspace tabs and renders the selected child route through `Outlet`.
- `fe/src/modules/nutrition/NutritionWorkspaceLayout.test.tsx`: proves tab order, active state, nested-path selection, and navigation.
- `fe/src/app/routes.tsx`: nests the ten family pages under the workspace layout and leaves Nutrition Platform outside it.
- `fe/src/app/routes.test.ts`: proves route registration and fixes source lookup under the configured Vitest runtime.

---

### Task 1: Align the Family Nutrition RBAC landing route and authorization boundary

**Files:**
- Modify: `be/src/test/java/top/egon/mario/nutrition/NutritionRbacResourceProviderTests.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/service/bootstrap/NutritionPermissionCatalog.java`
- Modify: `fe/src/layouts/AdminLayout/menu.test.tsx`
- Modify: `fe/src/layouts/AdminLayout/menu.tsx`

**Interfaces:**
- Consumes: `NutritionRbacResourceProvider.resources()`, `NutritionRbacResourceProvider.rolePresets()`, `buildAuthorizedAdminMenuItems(...)`, `canAccessAdminPath(...)`, and `selectedAdminMenuKey(...)`.
- Produces: the unchanged permission code `menu:nutrition:families` with route path `/nutrition/home`; a static sidebar key `/nutrition/home`; and canonical family-route authorization through `/nutrition/home`.

- [ ] **Step 1: Change the backend RBAC assertions to require the approved landing route and stable role boundary**

Replace the family menu assertion and extend the role-preset assertion in `NutritionRbacResourceProviderTests.java` with:

```java
@Test
void providerDeclaresMenusAndApisWithExpectedShape() {
    NutritionRbacResourceProvider provider = new NutritionRbacResourceProvider();

    assertThat(provider.resources())
            .filteredOn(seed -> "menu:nutrition:families".equals(seed.code()))
            .singleElement()
            .satisfies(seed -> {
                assertThat(seed.type()).isEqualTo(PermissionType.MENU);
                assertThat(seed.parentCode()).isEqualTo("menu:nutrition");
                assertThat(seed.menu().routePath()).isEqualTo("/nutrition/home");
            });
    assertThat(provider.resources())
            .filteredOn(seed -> "api:nutrition:family:*".equals(seed.code()))
            .singleElement()
            .satisfies(seed -> {
                assertThat(seed.type()).isEqualTo(PermissionType.API);
                assertThat(seed.api().publicFlag()).isFalse();
                assertThat(seed.api().urlPattern()).isEqualTo("/api/nutrition/families/**");
            });
    assertThat(provider.resources())
            .filteredOn(seed -> "api:nutrition:import-job:*".equals(seed.code()))
            .singleElement()
            .satisfies(seed -> {
                assertThat(seed.type()).isEqualTo(PermissionType.API);
                assertThat(seed.api().publicFlag()).isFalse();
                assertThat(seed.api().urlPattern()).isEqualTo("/api/nutrition/platform/import-jobs/**");
            });
}

@Test
void rolePresetsIncludeUserAndPlatformAdminPermissions() {
    NutritionRbacResourceProvider provider = new NutritionRbacResourceProvider();

    assertThat(provider.rolePresets())
            .extracting("roleCode")
            .contains("NUTRITION_USER", "NUTRITION_PLATFORM_ADMIN")
            .doesNotContain("SUPER_ADMIN");
    assertThat(provider.rolePresets())
            .filteredOn(seed -> "NUTRITION_USER".equals(seed.roleCode()))
            .singleElement()
            .satisfies(seed -> assertThat(seed.permissionCodes())
                    .contains("menu:nutrition", "menu:nutrition:families",
                            "api:nutrition:clan:*", "api:nutrition:family:*",
                            "api:nutrition:import-job:*")
                    .doesNotContain("menu:nutrition:platform", "api:nutrition:platform:*"));
    assertThat(provider.rolePresets())
            .filteredOn(seed -> "NUTRITION_PLATFORM_ADMIN".equals(seed.roleCode()))
            .singleElement()
            .satisfies(seed -> assertThat(seed.permissionCodes())
                    .contains("menu:nutrition", "menu:nutrition:families",
                            "menu:nutrition:platform", "api:nutrition:clan:*",
                            "api:nutrition:family:*", "api:nutrition:platform:*",
                            "api:nutrition:import-job:*"));
}
```

- [ ] **Step 2: Replace the frontend Nutrition menu test with separate family, platform, and bypass cases**

Replace the existing Nutrition test at the bottom of `menu.test.tsx` with:

```tsx
test('authorizes every family nutrition path through the family home menu', () => {
    const familyMenuTree: MenuTreeResponse[] = [
        ...menuTree,
        {
            permissionId: 30,
            permCode: 'menu:nutrition:families',
            permName: '家庭营养',
            routePath: '/nutrition/home',
            hidden: false,
            cacheable: true,
            sortNo: 30,
            children: [],
        },
    ]
    const familyPaths = [
        '/nutrition/home',
        '/nutrition/families',
        '/nutrition/members',
        '/nutrition/recipes',
        '/nutrition/ai-menus',
        '/nutrition/confirmations',
        '/nutrition/meal-summary',
        '/nutrition/shopping',
        '/nutrition/budget',
        '/nutrition/records',
    ]

    const keys = flattenMenuKeys(buildAuthorizedAdminMenuItems(familyMenuTree, false, ['NUTRITION_USER']))

    expect(keys).toContain('/nutrition/home')
    expect(keys).not.toContain('/nutrition/platform')
    familyPaths.forEach((path) => {
        expect(canAccessAdminPath(path, familyMenuTree, false, ['NUTRITION_USER'])).toBe(true)
    })
    expect(canAccessAdminPath('/nutrition/recipes/7', familyMenuTree, false, ['NUTRITION_USER']))
        .toBe(true)
    expect(canAccessAdminPath('/nutrition/platform', familyMenuTree, false, ['NUTRITION_USER']))
        .toBe(false)
    expect(canAccessAdminPath('/nutrition/unknown', familyMenuTree, false, ['NUTRITION_USER']))
        .toBe(false)
    expect(selectedAdminMenuKey('/nutrition/records', keys)).toBe('/nutrition/home')
})

test('keeps nutrition platform separate from the family workspace permission', () => {
    const platformMenuTree: MenuTreeResponse[] = [
        ...menuTree,
        {
            permissionId: 31,
            permCode: 'menu:nutrition:platform',
            permName: '营养平台',
            routePath: '/nutrition/platform',
            hidden: false,
            cacheable: true,
            sortNo: 31,
            children: [],
        },
    ]

    const keys = flattenMenuKeys(buildAuthorizedAdminMenuItems(
        platformMenuTree,
        false,
        ['NUTRITION_PLATFORM_ADMIN'],
    ))

    expect(keys).toContain('/nutrition/platform')
    expect(keys).not.toContain('/nutrition/home')
    expect(canAccessAdminPath('/nutrition/platform', platformMenuTree, false, ['NUTRITION_PLATFORM_ADMIN']))
        .toBe(true)
    expect(canAccessAdminPath('/nutrition/members', platformMenuTree, false, ['NUTRITION_PLATFORM_ADMIN']))
        .toBe(false)
})

test('keeps both nutrition entries in the super admin bypass menu', () => {
    const keys = flattenMenuKeys(buildAuthorizedAdminMenuItems(menuTree, true, ['SUPER_ADMIN']))

    expect(keys).toContain('/nutrition/home')
    expect(keys).toContain('/nutrition/platform')
    expect(canAccessAdminPath('/nutrition/budget', menuTree, true, ['SUPER_ADMIN'])).toBe(true)
    expect(canAccessAdminPath('/nutrition/platform', menuTree, true, ['SUPER_ADMIN'])).toBe(true)
})
```

- [ ] **Step 3: Run the focused tests and verify the new expectations fail**

Run:

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false -Dtest=top.egon.mario.nutrition.NutritionRbacResourceProviderTests test
```

Expected: FAIL because the provider still declares `/nutrition/families`.

Run:

```bash
cd ../fe
bun run test -- src/layouts/AdminLayout/menu.test.tsx
```

Expected: FAIL because the static menu still uses `/nutrition/families` and family paths still canonicalize there.

- [ ] **Step 4: Implement the stable `/nutrition/home` landing path**

In `NutritionPermissionCatalog.java`, change only the family menu route:

```java
new MenuPermissionSeed("menu:nutrition:families", "家庭营养", "menu:nutrition",
        "/nutrition/home", "nutrition-families", 71),
```

In `menu.tsx`, replace the family entry in `menuPathByKey` exactly as follows:

```diff
-    '/nutrition/families': '/nutrition/families',
+    '/nutrition/home': '/nutrition/home',
```

Replace the Nutrition item in `adminMenuItems` with:

```tsx
{
    key: 'nutrition',
    icon: <HeartOutlined/>,
    label: '营养管理',
    children: [
        {
            key: '/nutrition/home',
            icon: <TeamOutlined/>,
            label: '家庭营养',
        },
        {
            key: '/nutrition/platform',
            icon: <DatabaseOutlined/>,
            label: '营养平台',
        },
    ],
},
```

Remove the Nutrition entries from `compatibilityMenuPathAliases`; Nutrition canonicalization owns those routes. Replace `canonicalNutritionPath` with:

```tsx
function canonicalNutritionPath(pathname: string) {
    if (pathname === '/nutrition/platform' || pathname.startsWith('/nutrition/platform/')) {
        return '/nutrition/platform'
    }
    const familyMenuPaths = [
        '/nutrition/home',
        '/nutrition/families',
        '/nutrition/members',
        '/nutrition/recipes',
        '/nutrition/ai-menus',
        '/nutrition/confirmations',
        '/nutrition/meal-summary',
        '/nutrition/shopping',
        '/nutrition/budget',
        '/nutrition/records',
    ]
    return familyMenuPaths.some((path) => pathname === path || pathname.startsWith(`${path}/`))
        ? '/nutrition/home'
        : undefined
}
```

- [ ] **Step 5: Run the focused tests and verify they pass**

Run:

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false -Dtest=top.egon.mario.nutrition.NutritionRbacResourceProviderTests test
```

Expected: PASS with `3` tests and no failures.

Run:

```bash
cd ../fe
bun run test -- src/layouts/AdminLayout/menu.test.tsx
```

Expected: PASS with the existing menu suite plus the three explicit Nutrition boundary tests.

- [ ] **Step 6: Commit Task 1 once**

```bash
git add \
  be/src/main/java/top/egon/mario/nutrition/service/bootstrap/NutritionPermissionCatalog.java \
  be/src/test/java/top/egon/mario/nutrition/NutritionRbacResourceProviderTests.java \
  fe/src/layouts/AdminLayout/menu.tsx \
  fe/src/layouts/AdminLayout/menu.test.tsx
git commit -m "fix: align nutrition workspace permissions"
```

---

### Task 2: Add the shared family workspace tabs and nested route layout

**Files:**
- Create: `fe/src/modules/nutrition/NutritionWorkspaceLayout.test.tsx`
- Create: `fe/src/modules/nutrition/NutritionWorkspaceLayout.tsx`
- Modify: `fe/src/app/routes.test.ts`
- Modify: `fe/src/app/routes.tsx`

**Interfaces:**
- Consumes: the canonical `/nutrition/home` permission boundary from Task 1, React Router `Outlet`, `useLocation`, and `useNavigate`, and Ant Design `Tabs`.
- Produces: `NutritionWorkspaceLayout(): JSX.Element`, a shared ten-tab family navigation surface, and a pathless route parent for all family Nutrition pages.

- [ ] **Step 1: Add the failing workspace layout test**

Create `NutritionWorkspaceLayout.test.tsx`:

```tsx
import {render, screen, within} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {App} from 'antd'
import {describe, expect, test} from 'vitest'
import {MemoryRouter, Route, Routes, useLocation} from 'react-router'
import {NutritionWorkspaceLayout} from './NutritionWorkspaceLayout'

const expectedLabels = [
    '营养首页',
    '家庭管理',
    '成员健康',
    '家庭菜谱',
    'AI 菜单',
    '用餐确认',
    '餐食汇总',
    '采购清单',
    '预算分析',
    '营养记录',
]

function PathProbe() {
    const location = useLocation()
    return <div data-testid="current-path">{location.pathname}</div>
}

function renderWorkspace(initialEntry: string) {
    return render(
        <App>
            <MemoryRouter initialEntries={[initialEntry]}>
                <Routes>
                    <Route element={<NutritionWorkspaceLayout/>}>
                        <Route element={<PathProbe/>} path="nutrition/*"/>
                    </Route>
                </Routes>
            </MemoryRouter>
        </App>,
    )
}

describe('NutritionWorkspaceLayout', () => {
    test('renders every family workflow tab in the approved order and navigates between pages', async () => {
        const user = userEvent.setup()
        renderWorkspace('/nutrition/home')
        const navigation = screen.getByRole('navigation', {name: '家庭营养导航'})

        expect(within(navigation).getAllByRole('tab').map((tab) => tab.textContent)).toEqual(expectedLabels)
        expect(within(navigation).queryByRole('tab', {name: '营养平台'})).toBeNull()

        await user.click(within(navigation).getByRole('tab', {name: '预算分析'}))

        expect(screen.getByTestId('current-path').textContent).toBe('/nutrition/budget')
    })

    test('selects the owning tab for a nested family route', () => {
        renderWorkspace('/nutrition/recipes/42')
        const navigation = screen.getByRole('navigation', {name: '家庭营养导航'})

        expect(within(navigation).getByRole('tab', {name: '家庭菜谱'}).getAttribute('aria-selected'))
            .toBe('true')
    })
})
```

- [ ] **Step 2: Make the route source test runtime-stable and require the shared layout**

Replace `routes.test.ts` with:

```ts
import {readFileSync} from 'node:fs'
import {resolve} from 'node:path'
import {describe, expect, test} from 'vitest'

const routesSource = readFileSync(resolve(process.cwd(), 'src/app/routes.tsx'), 'utf8')

describe('admin routes', () => {
    test('does not register the legacy global MCP tool policy page', () => {
        expect(routesSource).not.toContain("path: 'agent/mcp/tools'")
        expect(routesSource).not.toContain('McpToolListPage')
    })

    test('registers family nutrition routes under the workspace layout and keeps platform separate', () => {
        const familyRoutes = {
            'nutrition/home': 'NutritionHomePage',
            'nutrition/families': 'ClanFamilyPage',
            'nutrition/members': 'MemberHealthPage',
            'nutrition/recipes': 'RecipeLibraryPage',
            'nutrition/ai-menus': 'AiMenuPage',
            'nutrition/confirmations': 'MealConfirmationPage',
            'nutrition/meal-summary': 'MealSummaryPage',
            'nutrition/shopping': 'ShoppingListPage',
            'nutrition/budget': 'BudgetPage',
            'nutrition/records': 'NutritionRecordPage',
        }

        expect(routesSource).toContain("import {NutritionWorkspaceLayout} from '../modules/nutrition/NutritionWorkspaceLayout'")
        const layoutStart = routesSource.indexOf('element: <NutritionWorkspaceLayout/>')
        const platformStart = routesSource.indexOf("path: 'nutrition/platform'")
        expect(layoutStart).toBeGreaterThan(-1)
        expect(platformStart).toBeGreaterThan(layoutStart)
        const familyLayoutSource = routesSource.slice(layoutStart, platformStart)
        Object.entries(familyRoutes).forEach(([path, moduleName]) => {
            expect(familyLayoutSource).toContain(`path: '${path}'`)
            expect(familyLayoutSource).toContain(`lazy: () => import('../modules/nutrition/${moduleName}')`)
        })
        expect(familyLayoutSource).not.toContain('PlatformNutritionConfigPage')
        expect(routesSource).toContain("lazy: () => import('../modules/nutrition/PlatformNutritionConfigPage')")
        expect(routesSource).not.toContain("import('../modules/nutrition/pages/NutritionPlaceholderPage')")
    })
})
```

- [ ] **Step 3: Run the new frontend tests and verify they fail before implementation**

Run:

```bash
cd fe
bun run test -- src/modules/nutrition/NutritionWorkspaceLayout.test.tsx src/app/routes.test.ts
```

Expected: FAIL because `NutritionWorkspaceLayout.tsx` does not exist and `routes.tsx` does not declare the shared layout.

- [ ] **Step 4: Implement the Nutrition-owned workspace layout**

Create `NutritionWorkspaceLayout.tsx`:

```tsx
import {Tabs} from 'antd'
import {Outlet, useLocation, useNavigate} from 'react-router'

const workspaceTabs = [
    {key: '/nutrition/home', label: '营养首页'},
    {key: '/nutrition/families', label: '家庭管理'},
    {key: '/nutrition/members', label: '成员健康'},
    {key: '/nutrition/recipes', label: '家庭菜谱'},
    {key: '/nutrition/ai-menus', label: 'AI 菜单'},
    {key: '/nutrition/confirmations', label: '用餐确认'},
    {key: '/nutrition/meal-summary', label: '餐食汇总'},
    {key: '/nutrition/shopping', label: '采购清单'},
    {key: '/nutrition/budget', label: '预算分析'},
    {key: '/nutrition/records', label: '营养记录'},
]

export function NutritionWorkspaceLayout() {
    const location = useLocation()
    const navigate = useNavigate()
    const activeKey = workspaceTabs.find(({key}) => (
        location.pathname === key || location.pathname.startsWith(`${key}/`)
    ))?.key

    return (
        <>
            <nav aria-label="家庭营养导航">
                <Tabs
                    activeKey={activeKey}
                    items={workspaceTabs}
                    onChange={(path) => void navigate(path)}
                    tabBarStyle={{marginBottom: 16}}
                />
            </nav>
            <Outlet/>
        </>
    )
}
```

- [ ] **Step 5: Nest the ten family routes under the workspace layout**

Add the static import near the other layout imports in `routes.tsx`:

```tsx
import {NutritionWorkspaceLayout} from '../modules/nutrition/NutritionWorkspaceLayout'
```

Replace the flat Nutrition route block with:

```tsx
{
    element: <NutritionWorkspaceLayout/>,
    children: [
        {path: 'nutrition/home', lazy: () => import('../modules/nutrition/NutritionHomePage')},
        {path: 'nutrition/families', lazy: () => import('../modules/nutrition/ClanFamilyPage')},
        {path: 'nutrition/members', lazy: () => import('../modules/nutrition/MemberHealthPage')},
        {path: 'nutrition/recipes', lazy: () => import('../modules/nutrition/RecipeLibraryPage')},
        {path: 'nutrition/ai-menus', lazy: () => import('../modules/nutrition/AiMenuPage')},
        {path: 'nutrition/confirmations', lazy: () => import('../modules/nutrition/MealConfirmationPage')},
        {path: 'nutrition/meal-summary', lazy: () => import('../modules/nutrition/MealSummaryPage')},
        {path: 'nutrition/shopping', lazy: () => import('../modules/nutrition/ShoppingListPage')},
        {path: 'nutrition/budget', lazy: () => import('../modules/nutrition/BudgetPage')},
        {path: 'nutrition/records', lazy: () => import('../modules/nutrition/NutritionRecordPage')},
    ],
},
{path: 'nutrition/platform', lazy: () => import('../modules/nutrition/PlatformNutritionConfigPage')},
```

- [ ] **Step 6: Run the focused workspace, route, menu, and Nutrition tests**

Run:

```bash
cd fe
bun run test -- src/modules/nutrition src/layouts/AdminLayout/menu.test.tsx src/app/routes.test.ts
```

Expected: PASS for all Nutrition test files, the AdminLayout menu suite, and the route source suite.

- [ ] **Step 7: Run frontend typecheck and focused lint before committing**

Run:

```bash
bun run typecheck
bun run --bun eslint \
  src/modules/nutrition \
  src/layouts/AdminLayout/menu.tsx \
  src/layouts/AdminLayout/menu.test.tsx \
  src/app/routes.tsx \
  src/app/routes.test.ts
```

Expected: both commands exit `0` with no errors in the changed scope.

- [ ] **Step 8: Commit Task 2 once**

```bash
git add \
  fe/src/modules/nutrition/NutritionWorkspaceLayout.tsx \
  fe/src/modules/nutrition/NutritionWorkspaceLayout.test.tsx \
  fe/src/app/routes.tsx \
  fe/src/app/routes.test.ts
git commit -m "feat: add nutrition workspace navigation"
```

---

## Final Verification

- [ ] Run the complete backend Nutrition slice:

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false -Dtest='top.egon.mario.nutrition.**' test
```

Expected: BUILD SUCCESS with no failed or errored Nutrition tests.

- [ ] Run the complete focused frontend verification:

```bash
cd ../fe
bun run test -- src/modules/nutrition src/layouts/AdminLayout/menu.test.tsx src/app/routes.test.ts
bun run typecheck
bun run --bun eslint \
  src/modules/nutrition \
  src/layouts/AdminLayout/menu.tsx \
  src/layouts/AdminLayout/menu.test.tsx \
  src/app/routes.tsx \
  src/app/routes.test.ts
bun run build
```

Expected: all tests pass; typecheck, focused lint, and production build exit `0`.

- [ ] Confirm worktree scope and commit history:

```bash
git status --short
git diff --check HEAD~2..HEAD
git log -2 --oneline
git diff --name-only HEAD~2..HEAD
```

Expected:

- clean worktree
- exactly two implementation commits
- only the eight files listed in the File Responsibility Map changed
- no Flyway migration, dependency manifest, `.gitignore`, or Clocktower task document changed
- no backend or frontend server was started
