# Nutrition Workspace Navigation Design

Date: 2026-07-14
Status: Approved design, pending written spec review
Author: design session

## 1. Decision Summary

The Nutrition frontend already has live routes and pages for the complete family
workflow, but only Family Nutrition and Nutrition Platform are reachable from
the administration sidebar. The remaining family workflow pages have no
discoverable navigation.

The selected design keeps the sidebar coarse and introduces a Nutrition-owned
workspace layout for family pages:

- the sidebar keeps Family Nutrition and Nutrition Platform as its two entries
- Family Nutrition opens `/nutrition/home`
- a shared horizontal tab bar exposes all ten family workflow pages
- all family tabs share the existing `menu:nutrition:families` permission
- Nutrition Platform stays outside the family workspace and continues to
  require `menu:nutrition:platform`
- backend nutrition-scoped roles and explicit data grants remain the final
  authority for family data and operations

No existing API, persistence model, or family access rule changes as part of
this work.

## 2. Context and Problem

The protected admin router currently registers these live Nutrition pages:

1. `/nutrition/home`
2. `/nutrition/families`
3. `/nutrition/members`
4. `/nutrition/recipes`
5. `/nutrition/ai-menus`
6. `/nutrition/confirmations`
7. `/nutrition/meal-summary`
8. `/nutrition/shopping`
9. `/nutrition/budget`
10. `/nutrition/records`
11. `/nutrition/platform`

The sidebar and RBAC resource catalog declare only two routed Nutrition menu
entries:

- `/nutrition/families`
- `/nutrition/platform`

The other family paths are canonicalized to `/nutrition/families` for access
checks, but neither the sidebar nor a Nutrition page links to them. Users can
reach those pages only by entering URLs manually. This is a navigation gap, not
an absent-page or absent-backend problem.

## 3. Goals

1. Make every existing family Nutrition page discoverable from a consistent
   workspace navigation surface.
2. Make Nutrition Home the default Family Nutrition landing page.
3. Keep sidebar visibility, direct URL access, and selected-menu state aligned
   to the same RBAC permission.
4. Preserve the existing platform-RBAC plus nutrition-scoped-access model.
5. Preserve all existing page URLs and page-level loading, empty, forbidden,
   and error states.
6. Keep the change local to Nutrition routing, navigation, and its RBAC menu
   declaration.

## 4. Non-Goals

- No new Nutrition page or business workflow.
- No API, DTO, service, repository, or schema change.
- No new button permission catalog.
- No redesign of existing Nutrition page content.
- No new state-management or routing dependency.
- No global `SUPER_ADMIN` family-data bypass.
- No change to nutrition-scoped role or data-grant semantics.
- No modification of an existing Flyway migration.
- No new Flyway migration.
- No unrelated AdminLayout, RBAC, Agent, RAG, IM, or Clocktower refactor.

## 5. Approaches Considered

### 5.1 Nutrition route layout - selected

Add a pathless React Router layout owned by the Nutrition module. The layout
renders the shared family workspace tabs and an `Outlet`. The ten family routes
become its children, while the platform route remains a sibling.

Advantages:

- one source of truth for navigation order, labels, paths, and selected state
- no repeated navigation insertion across ten pages
- no Nutrition-specific condition in the global AdminLayout
- existing page modules and URLs remain intact
- future family pages have one explicit integration point

Trade-off: the route declaration gains one additional nesting level.

### 5.2 Import a shared navigation component into every page - rejected

This would preserve the flat route tree, but each family page would need to
render the component explicitly. It creates ten integration points, makes
omissions easy, and couples page content to workspace chrome.

### 5.3 Render Nutrition navigation from AdminLayout - rejected

This minimizes route changes but makes the global administration layout aware
of Nutrition-specific routes and labels. It weakens module ownership and makes
future domain navigation changes affect a shared shell.

## 6. Navigation Architecture

### 6.1 Shared workspace layout

Create:

`fe/src/modules/nutrition/NutritionWorkspaceLayout.tsx`

The layout owns a fixed navigation definition containing a label and route for
each family page. It derives the active tab from `location.pathname`, navigates
with React Router, and renders the active child through `Outlet`.

It does not:

- fetch families
- own current-family state
- duplicate page loading or error handling
- perform business authorization
- render the platform page

### 6.2 Tab order

| Order | Label | Path |
| --- | --- | --- |
| 1 | Nutrition Home | `/nutrition/home` |
| 2 | Family Management | `/nutrition/families` |
| 3 | Member Health | `/nutrition/members` |
| 4 | Family Recipes | `/nutrition/recipes` |
| 5 | AI Menus | `/nutrition/ai-menus` |
| 6 | Meal Confirmation | `/nutrition/confirmations` |
| 7 | Meal Summary | `/nutrition/meal-summary` |
| 8 | Shopping List | `/nutrition/shopping` |
| 9 | Budget Analysis | `/nutrition/budget` |
| 10 | Nutrition Records | `/nutrition/records` |

The rendered labels remain Chinese to match the existing application UI:

`营养首页`, `家庭管理`, `成员健康`, `家庭菜谱`, `AI 菜单`, `用餐确认`,
`餐食汇总`, `采购清单`, `预算分析`, and `营养记录`.

### 6.3 Responsive behavior

Use Ant Design Tabs as an unframed workspace navigation band. The active tab
must be visible, tab content must not wrap into overlapping rows, and narrow
viewports must use the component's overflow behavior rather than shrinking
labels until they become unreadable.

The tab bar must not be placed inside a card. Existing pages retain their own
toolbars and content hierarchy below it.

### 6.4 Route structure

The admin route tree becomes conceptually:

```text
AdminLayout
|- NutritionWorkspaceLayout
|  |- /nutrition/home
|  |- /nutrition/families
|  |- /nutrition/members
|  |- /nutrition/recipes
|  |- /nutrition/ai-menus
|  |- /nutrition/confirmations
|  |- /nutrition/meal-summary
|  |- /nutrition/shopping
|  |- /nutrition/budget
|  `- /nutrition/records
`- /nutrition/platform
```

The parent route is pathless. All existing child URLs remain compatible.

## 7. RBAC and Access Design

### 7.1 Stable permission codes

Keep the existing permission codes:

- `menu:nutrition`
- `menu:nutrition:families`
- `menu:nutrition:platform`

Do not create page-level menu permissions for the ten family tabs. The tabs are
views inside one authorized family workspace rather than independent platform
modules.

### 7.2 Family menu landing route

Change the route path declared for `menu:nutrition:families` from
`/nutrition/families` to `/nutrition/home` in the provider-owned Nutrition RBAC
catalog.

The resource synchronizer updates the existing managed menu resource by
permission code. The permission identity and current role-permission relations
remain stable, so no migration or manual grant rewrite is required.

The static admin menu key and path mapping also change to `/nutrition/home`.
The visible label remains `家庭营养`.

### 7.3 Canonical route authorization

All ten family paths canonicalize to `/nutrition/home` for AdminLayout access
checks and selected-menu state. `/nutrition/platform` remains its own canonical
path.

This produces these boundaries:

- a user without `menu:nutrition:families` cannot see Family Nutrition and
  cannot open any family tab by direct URL
- a user with `menu:nutrition:families` sees all family tabs
- a user without `menu:nutrition:platform` cannot see or directly open the
  platform page
- `SUPER_ADMIN` retains the existing frontend administration bypass

### 7.4 Empty family state

Family workspace navigation is controlled by platform RBAC, not by whether the
current user already has an accessible family. A user with the family menu
permission sees every tab even when the accessible-family list is empty.

Each page continues to render its existing empty state. Family Management
remains available so an authorized user can create a family or inspect later
authorization changes.

### 7.5 Business authorization remains unchanged

Navigation access does not grant family data. Existing backend checks continue
to enforce family ownership, nutrition-scoped roles, and explicit data grants.
Existing frontend API/button checks remain usability hints; the backend remains
the final authority.

`SUPER_ADMIN` does not become an implicit owner, cook, guardian, or member of
every family through this change.

## 8. Data and Interaction Flow

```text
authenticated session
-> effective RBAC menus and API permissions
-> AdminLayout sidebar and direct-route guard
-> Family Nutrition at /nutrition/home
-> NutritionWorkspaceLayout tabs
-> selected existing page
-> existing page family loading and API calls
-> backend nutrition-scoped authorization
```

The workspace layout introduces no asynchronous data flow. Navigation failures
therefore remain routing or authorization failures; page data failures continue
to use the existing `NutritionAsyncState` handling.

## 9. Error and Compatibility Handling

- Existing family URLs remain valid and render their current pages.
- `/nutrition/families` remains Family Management; it is not redirected.
- The workspace active-tab resolver must support exact family paths and future
  detail suffixes beneath a known tab path.
- An unknown path is not silently treated as an authorized Nutrition tab.
- Platform routing does not render family tabs.
- Existing page empty, forbidden, stale-version, and request-error behavior is
  unchanged.
- The current route source test must stop depending on an unstable
  `import.meta.url` value under the configured Vitest environment. It should
  resolve `src/app/routes.tsx` from the frontend working directory.

## 10. Testing Strategy

### 10.1 Backend RBAC provider tests

Update `NutritionRbacResourceProviderTests` to prove:

- `menu:nutrition:families` declares `/nutrition/home`
- `NUTRITION_USER` receives the family menu but not the platform menu
- `NUTRITION_PLATFORM_ADMIN` receives both routed Nutrition menus
- provider presets do not declare `SUPER_ADMIN`
- existing family and platform API resource declarations remain unchanged

### 10.2 Frontend workspace tests

Add `NutritionWorkspaceLayout.test.tsx` to prove:

- all ten family tabs render in the approved order
- the current pathname selects the correct tab
- selecting a tab navigates to its existing route
- a nested path under a known family route selects the owning tab
- the platform path is not represented in the family tab list

### 10.3 Frontend menu authorization tests

Update `menu.test.tsx` to prove:

- Family Nutrition uses `/nutrition/home` as its static menu path
- every family route is authorized by `menu:nutrition:families`
- family authorization does not imply platform authorization
- platform authorization does not replace family authorization
- `SUPER_ADMIN` retains both entries through the existing bypass
- the sidebar selects Family Nutrition while any family tab is active

### 10.4 Route registration tests

Update `routes.test.ts` to prove:

- all ten family pages are children of the shared workspace layout
- the platform page stays outside that layout
- no production route imports the placeholder page
- route source lookup works under the repository's configured test runtime

### 10.5 Verification commands

Run, at minimum:

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false -Dtest=top.egon.mario.nutrition.NutritionRbacResourceProviderTests test
./mvnw -Dmaven.build.cache.enabled=false -Dtest='top.egon.mario.nutrition.**' test

cd ../fe
bun run test -- src/modules/nutrition src/layouts/AdminLayout/menu.test.tsx src/app/routes.test.ts
bun run typecheck
bun run --bun eslint src/modules/nutrition src/layouts/AdminLayout/menu.tsx src/app/routes.tsx
bun run build
```

The project must not be started automatically, and no browser-based verification
is performed under the current project instructions.

## 11. Expected File Scope

Create:

- `fe/src/modules/nutrition/NutritionWorkspaceLayout.tsx`
- `fe/src/modules/nutrition/NutritionWorkspaceLayout.test.tsx`

Modify:

- `fe/src/app/routes.tsx`
- `fe/src/app/routes.test.ts`
- `fe/src/layouts/AdminLayout/menu.tsx`
- `fe/src/layouts/AdminLayout/menu.test.tsx`
- `be/src/main/java/top/egon/mario/nutrition/service/bootstrap/NutritionPermissionCatalog.java`
- `be/src/test/java/top/egon/mario/nutrition/NutritionRbacResourceProviderTests.java`

No other production module is expected to change.

## 12. Worktree and Commit Strategy

After written spec review and implementation-plan approval:

1. create an isolated worktree on branch
   `codex/nutrition-workspace-navigation`
2. preserve all unrelated staged and unstaged state in the current `main`
   worktree
3. implement RBAC landing-route and authorization tests as one task and one
   commit
4. implement the workspace layout, route nesting, tabs, and frontend tests as
   one task and one commit
5. run the complete verification set in the worktree
6. do not start the project automatically

The design spec and implementation plan are committed separately as workflow
artifacts before implementation. Unrelated `.gitignore` and
`docs/clocktower_agent_tasks` changes in the current worktree must not enter any
Nutrition commit.

## 13. Acceptance Criteria

1. Clicking Family Nutrition opens `/nutrition/home`.
2. Every family workflow page is reachable from the shared tab bar.
3. The selected tab and sidebar entry remain correct on every family route.
4. `NUTRITION_USER` can access all family tabs but cannot access Nutrition
   Platform without its platform permission.
5. `NUTRITION_PLATFORM_ADMIN` and `SUPER_ADMIN` retain their approved coarse
   menu access.
6. Direct URL access follows the same permission boundary as visible
   navigation.
7. Users without accessible families still see the complete workspace
   navigation and existing empty states.
8. Existing family data authorization remains unchanged.
9. Existing Nutrition page URLs, API calls, and page behavior remain compatible.
10. No Flyway migration is added or modified.
11. Targeted backend and frontend tests, typecheck, lint, and build pass.
12. The implementation is isolated in the requested worktree and excludes all
    unrelated current-worktree changes.
