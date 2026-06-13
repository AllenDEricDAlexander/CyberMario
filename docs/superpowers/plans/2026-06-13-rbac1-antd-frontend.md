# RBAC1 AntD Frontend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:
> executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a React Router and Ant Design based frontend for the backend RBAC1 APIs while preserving the existing
chat workflow.

**Architecture:** The frontend will use React Router route objects for authenticated and public routes, Ant Design for
the admin shell and resource pages, and a centralized request layer for API response handling and JWT refresh. RBAC
pages live under `modules/rbac` so users, roles, permissions, menus, buttons, and API permissions can grow
independently.

**Tech Stack:** React 19, Vite, TypeScript, React Router, Ant Design, `@ant-design/icons`, native fetch.

---

### Task 1: Dependencies And Routing Shell

**Files:**

- Modify: `fe/package.json`
- Modify: `fe/package-lock.json`
- Modify: `fe/vite.config.ts`
- Modify: `fe/src/App.tsx`
- Modify: `fe/src/app/providers.tsx`
- Modify: `fe/src/app/router.tsx`
- Create: `fe/src/app/routes.tsx`
- Create: `fe/src/layouts/AuthLayout/index.tsx`
- Create: `fe/src/layouts/AdminLayout/index.tsx`
- Create: `fe/src/layouts/AdminLayout/menu.tsx`

- [ ] Add `antd`, `@ant-design/icons`, and `react-router`.
- [ ] Add `/api` to the Vite proxy with the same backend target as `/demo`.
- [ ] Replace the pathname router with `createBrowserRouter` and `RouterProvider`.
- [ ] Add `AuthLayout` for `/login` and `AdminLayout` for authenticated pages.
- [ ] Move the existing chat page behind `/chat`.
- [ ] Run `npm run build` and `npm run lint`.

### Task 2: Shared Types And Request Layer

**Files:**

- Modify: `fe/src/services/request.ts`
- Create: `fe/src/services/tokenStorage.ts`
- Create: `fe/src/types/api.ts`
- Create: `fe/src/modules/auth/authTypes.ts`
- Create: `fe/src/modules/rbac/rbacTypes.ts`
- Create: `fe/src/modules/rbac/rbacEnums.ts`
- Create: `fe/src/utils/enum.ts`
- Create: `fe/src/utils/tree.ts`

- [ ] Define `ApiResponse<T>` and `PageResult<T>` to match the backend.
- [ ] Define RBAC DTO types matching backend request and response names.
- [ ] Centralize token storage for access and refresh tokens.
- [ ] Add JSON request helpers that unwrap `ApiResponse<T>`.
- [ ] Add bearer token injection and one refresh retry for authenticated requests.
- [ ] Keep the NDJSON chat stream helper working for `/demo/chat/stream`.
- [ ] Run `npm run build` and `npm run lint`.

### Task 3: Auth Store And Login

**Files:**

- Create: `fe/src/modules/auth/authService.ts`
- Create: `fe/src/modules/auth/authStore.tsx`
- Create: `fe/src/modules/auth/pages/LoginPage.tsx`
- Modify: `fe/src/app/providers.tsx`
- Modify: `fe/src/app/routes.tsx`
- Delete or replace: `fe/src/pages/Login/index.tsx`

- [ ] Implement login, refresh, logout, and current-user service calls.
- [ ] Implement `AuthProvider` with bootstrapping, login, logout, and permission helpers.
- [ ] Protect authenticated routes with `RequireAuth`.
- [ ] Build a real AntD login page with validation, loading, and error message states.
- [ ] Run `npm run build` and `npm run lint`.

### Task 4: Admin Layout And Chat Migration

**Files:**

- Create: `fe/src/modules/chat/pages/ChatPage.tsx`
- Create: `fe/src/modules/chat/chatService.ts`
- Create: `fe/src/modules/chat/chatTypes.ts`
- Modify: `fe/src/layouts/AdminLayout/index.tsx`
- Modify: `fe/src/layouts/AdminLayout/menu.tsx`
- Delete or replace: `fe/src/pages/Home/index.tsx`
- Delete or replace: `fe/src/services/userService.ts`
- Delete or replace: `fe/src/types/chat.ts`

- [ ] Rebuild the admin shell with AntD `Layout`, `Sider`, `Menu`, `Header`, `Dropdown`, and `Outlet`.
- [ ] Use React Router navigation and selected menu keys.
- [ ] Migrate chat to AntD visual primitives while preserving streaming behavior.
- [ ] Add logout and current user display.
- [ ] Run `npm run build` and `npm run lint`.

### Task 5: Shared RBAC Service And Components

**Files:**

- Create: `fe/src/modules/rbac/rbacService.ts`
- Create: `fe/src/components/PageToolbar/index.tsx`
- Create: `fe/src/components/StatusTag/index.tsx`
- Create: `fe/src/components/PermissionTypeTag/index.tsx`
- Create: `fe/src/components/ApiRiskTag/index.tsx`
- Create: `fe/src/components/ErrorState/index.tsx`

- [ ] Add service functions for all RBAC1 admin endpoints.
- [ ] Add shared tags for status, permission type, and API risk.
- [ ] Add a common page toolbar for title and actions.
- [ ] Add a reusable error state around AntD `Result`.
- [ ] Run `npm run build` and `npm run lint`.

### Task 6: Users And Roles

**Files:**

- Create: `fe/src/modules/rbac/users/UserListPage.tsx`
- Create: `fe/src/modules/rbac/users/UserEditorDrawer.tsx`
- Create: `fe/src/modules/rbac/users/UserRoleDrawer.tsx`
- Create: `fe/src/modules/rbac/users/UserPermissionDrawer.tsx`
- Create: `fe/src/modules/rbac/roles/RoleListPage.tsx`
- Create: `fe/src/modules/rbac/roles/RoleEditorDrawer.tsx`
- Create: `fe/src/modules/rbac/roles/RolePermissionDrawer.tsx`
- Create: `fe/src/modules/rbac/roles/RoleInheritanceDrawer.tsx`

- [ ] Implement user list, create, update, status, delete, password reset, role assignment, and effective permissions.
- [ ] Implement role list, create, update, delete, direct permission assignment, inheritance assignment, and effective
  permissions.
- [ ] Use AntD `Table`, `Drawer`, `Form`, `Transfer`, `Tree`, `Descriptions`, `Tag`, and `Popconfirm`.
- [ ] Run `npm run build` and `npm run lint`.

### Task 7: Permissions, Menus, Buttons, And API Rules

**Files:**

- Create: `fe/src/modules/rbac/permissions/PermissionListPage.tsx`
- Create: `fe/src/modules/rbac/permissions/PermissionEditorDrawer.tsx`
- Create: `fe/src/modules/rbac/menus/MenuTreePage.tsx`
- Create: `fe/src/modules/rbac/menus/MenuEditorDrawer.tsx`
- Create: `fe/src/modules/rbac/buttons/ButtonListPage.tsx`
- Create: `fe/src/modules/rbac/buttons/ButtonEditorDrawer.tsx`
- Create: `fe/src/modules/rbac/buttons/ButtonApiDrawer.tsx`
- Create: `fe/src/modules/rbac/apis/ApiPermissionListPage.tsx`
- Create: `fe/src/modules/rbac/apis/ApiPermissionEditorDrawer.tsx`

- [ ] Implement unified permission list and type-specific drawer editing.
- [ ] Implement menu tree management.
- [ ] Implement button management by selected menu and button-to-API binding.
- [ ] Implement API permission list and rule editing.
- [ ] Show the API scan action as disabled because backend RBAC1 returns not supported.
- [ ] Run `npm run build` and `npm run lint`.

### Task 8: Final Verification

**Files:**

- Inspect: `fe/src`
- Inspect: `fe/package.json`
- Inspect: `fe/vite.config.ts`

- [ ] Run `npm run build`.
- [ ] Run `npm run lint`.
- [ ] Start `npm run dev` and inspect the local app in the browser if a local server is available.
- [ ] Check `git status --short` to confirm only intended frontend, docs, and dependency files changed.
