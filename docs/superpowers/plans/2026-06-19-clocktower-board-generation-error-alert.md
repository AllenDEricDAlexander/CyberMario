# Clocktower Board Generation Error Alert Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix `/clocktower/boards` so candidate generation does not require manual role selection, and move request/async failure feedback from transient error toasts to a global closable Ant Design Alert.

**Architecture:** Add a small provider-level global error channel under `fe/src/app`, exposed through helper functions that non-React utilities and pages can call. Keep successful operation feedback as `message.success`, keep domain-specific inline result Alerts, and route operation failures to the global Alert. Make the board generator validate only generation fields, leaving editor role validation for manual validation/save.

**Tech Stack:** React 19, TypeScript, Vite/Vitest, Ant Design 6.4.4, existing `voidify` async reporter, existing `resolveErrorMessage`.

---

## File Structure

- Create `fe/src/app/globalError.tsx`
  - Owns a tiny global error event channel.
  - Exports `reportGlobalError(error)`, `clearGlobalError()`, `registerGlobalErrorHandler(handler)`, `GlobalErrorProvider`, and `GlobalErrorAlert`.
  - Keeps error message rendering in one focused file instead of spreading `Alert` state through pages.
- Create `fe/src/app/globalError.test.tsx`
  - Verifies the Alert renders a resolved error message.
  - Verifies `reportGlobalError` and `clearGlobalError` notify registered handlers.
- Modify `fe/src/app/providers.tsx`
  - Wrap app content with `GlobalErrorProvider`.
  - Change `AsyncErrorReporter` to report through `reportGlobalError` instead of `message.error`.
- Modify `fe/src/modules/clocktower/BoardBuilderPage.tsx`
  - Export a generation field list.
  - Use that field list in `generate()`.
  - Route request failures through `reportGlobalError`; retain the board validation result `Alert`.
- Modify `fe/src/modules/clocktower/BoardBuilderPage.test.tsx`
  - Assert the generation field list excludes `roleCodes`.
- Modify request/operation error toast callers:
  - `fe/src/modules/clocktower/ReplayListPage.tsx`
  - `fe/src/modules/clocktower/ReplayPage.tsx`
  - `fe/src/modules/clocktower/RuleDataPage.tsx`
  - `fe/src/modules/clocktower/RoomLobbyPage.tsx`
  - `fe/src/modules/clocktower/GameRoomPage.tsx`
  - `fe/src/modules/clocktower/RoomListPage.tsx`
  - `fe/src/modules/clocktower/StorytellerGrimoirePage.tsx`
  - `fe/src/modules/agent/AgentMemoryPage.tsx`
  - `fe/src/modules/agent/AgentMemoryArchivePage.tsx`
  - `fe/src/modules/agent/AgentDebugPage.tsx`
  - `fe/src/modules/agent/mcp/McpServerListPage.tsx`
  - `fe/src/modules/agent/mcp/McpToolListPage.tsx`
  - `fe/src/modules/dashboard/DashboardPage.tsx`
  - `fe/src/modules/rag/RetrievalLabPage.tsx`
  - `fe/src/modules/rag/RagChatPage.tsx`
  - `fe/src/modules/chat/pages/ChatPage.tsx`
- Leave these local error surfaces in place:
  - `fe/src/modules/rbac/users/UserListPage.tsx` local `message.error('请输入新密码')`, because it is a modal form prompt.
  - Existing inline `setError(...)` surfaces that render persistent page or message content, such as auth forms, chat message error content, audit detail panes, and RAG/agent stream message state.

## Task 1: Add Global Error Alert Channel

**Files:**
- Create: `fe/src/app/globalError.tsx`
- Create: `fe/src/app/globalError.test.tsx`
- Modify: `fe/src/app/providers.tsx`

- [ ] **Step 1: Write the failing global error tests**

Create `fe/src/app/globalError.test.tsx`:

```tsx
import {renderToStaticMarkup} from 'react-dom/server'
import {describe, expect, test, vi} from 'vitest'
import {
    clearGlobalError,
    GlobalErrorAlert,
    registerGlobalErrorHandler,
    reportGlobalError,
} from './globalError'

describe('global error reporting', () => {
    test('renders a closable error alert with the reported message', () => {
        const markup = renderToStaticMarkup(
            <GlobalErrorAlert message="Network Error" onClose={vi.fn()}/>,
        )

        expect(markup).toContain('Network Error')
        expect(markup).toContain('ant-alert-error')
    })

    test('reports and clears resolved error messages', () => {
        const handler = vi.fn()
        const dispose = registerGlobalErrorHandler(handler)

        reportGlobalError(new Error('Network Error'))
        expect(handler).toHaveBeenCalledWith('Network Error')

        clearGlobalError()
        expect(handler).toHaveBeenLastCalledWith(undefined)

        dispose()
        reportGlobalError(new Error('Ignored'))
        expect(handler).toHaveBeenCalledTimes(2)
    })
})
```

- [ ] **Step 2: Run the failing global error tests**

Run:

```bash
cd fe
./node_modules/.bin/vitest run src/app/globalError.test.tsx
```

Expected: FAIL because `./globalError` does not exist.

- [ ] **Step 3: Implement the global error channel**

Create `fe/src/app/globalError.tsx`:

```tsx
import {Alert} from 'antd'
import {type ReactNode, useEffect, useState} from 'react'
import {resolveErrorMessage} from '../services/request'

type GlobalErrorHandler = (message: string | undefined) => void

const globalErrorHandlers = new Set<GlobalErrorHandler>()

type GlobalErrorProviderProps = {
    children: ReactNode
}

type GlobalErrorAlertProps = {
    message?: string
    onClose: () => void
}

export function reportGlobalError(error: unknown) {
    const message = typeof error === 'string' ? error : resolveErrorMessage(error)
    globalErrorHandlers.forEach((handler) => handler(message))
}

export function clearGlobalError() {
    globalErrorHandlers.forEach((handler) => handler(undefined))
}

export function registerGlobalErrorHandler(handler: GlobalErrorHandler) {
    globalErrorHandlers.add(handler)
    return () => {
        globalErrorHandlers.delete(handler)
    }
}

export function GlobalErrorProvider({children}: GlobalErrorProviderProps) {
    const [message, setMessage] = useState<string>()

    useEffect(() => registerGlobalErrorHandler(setMessage), [])

    return (
        <>
            <GlobalErrorAlert message={message} onClose={clearGlobalError}/>
            {children}
        </>
    )
}

export function GlobalErrorAlert({message, onClose}: GlobalErrorAlertProps) {
    if (!message) {
        return null
    }

    return (
        <Alert
            closable
            message={message}
            onClose={onClose}
            showIcon
            style={{margin: '16px 24px 0'}}
            type="error"
        />
    )
}
```

- [ ] **Step 4: Wire the provider and async reporter**

Modify `fe/src/app/providers.tsx`.

Replace the import block:

```tsx
import {type ReactNode, useEffect} from 'react'
import {App, ConfigProvider} from 'antd'
import {XProvider} from '@ant-design/x'
import zhCN from 'antd/locale/zh_CN'
import zhCNX from '@ant-design/x/locale/zh_CN'
import {AuthProvider} from '../modules/auth/authStore'
import {GlobalErrorProvider, reportGlobalError} from './globalError'
import {registerAsyncErrorHandler, registerUnhandledRejectionReporter} from '../utils/async'
```

Update the `<App>` content to wrap children with `GlobalErrorProvider`:

```tsx
                <App>
                    <GlobalErrorProvider>
                        <AsyncErrorReporter>
                            <AuthProvider>{children}</AuthProvider>
                        </AsyncErrorReporter>
                    </GlobalErrorProvider>
                </App>
```

Replace `AsyncErrorReporter` with:

```tsx
function AsyncErrorReporter({children}: AppProvidersProps) {
    useEffect(() => {
        const disposeRejectedHandler = registerAsyncErrorHandler(reportGlobalError)
        const disposeUnhandledRejectionHandler = registerUnhandledRejectionReporter(reportGlobalError)
        return () => {
            disposeRejectedHandler()
            disposeUnhandledRejectionHandler()
        }
    }, [])

    return <>{children}</>
}
```

- [ ] **Step 5: Run the global error tests**

Run:

```bash
cd fe
./node_modules/.bin/vitest run src/app/globalError.test.tsx
```

Expected: PASS. Both global error tests pass.

- [ ] **Step 6: Commit Task 1**

Run:

```bash
git add fe/src/app/globalError.tsx fe/src/app/globalError.test.tsx fe/src/app/providers.tsx
git commit -m "feat(frontend): add global error alert"
```

## Task 2: Decouple Board Generation from Manual Role Selection

**Files:**
- Modify: `fe/src/modules/clocktower/BoardBuilderPage.tsx`
- Modify: `fe/src/modules/clocktower/BoardBuilderPage.test.tsx`

- [ ] **Step 1: Write the failing field-list test**

Modify the import in `fe/src/modules/clocktower/BoardBuilderPage.test.tsx`:

```tsx
import {
    boardGenerateFieldNames,
    Component as BoardBuilderPage,
    savedBoardColumns,
} from './BoardBuilderPage'
```

Add this test inside `describe('BoardBuilderPage', () => { ... })`:

```tsx
    test('does not require manual editor roles when generating candidates', () => {
        expect(boardGenerateFieldNames).toEqual([
            'scriptCode',
            'playerCount',
            'difficulty',
            'chaos',
            'evilPressure',
            'newbieFriendly',
            'candidateCount',
            'bannedRoleCodes',
            'lockedRoleCodes',
            'seed',
        ])
        expect(boardGenerateFieldNames).not.toContain('roleCodes')
    })
```

- [ ] **Step 2: Run the failing board test**

Run:

```bash
cd fe
./node_modules/.bin/vitest run src/modules/clocktower/BoardBuilderPage.test.tsx
```

Expected: FAIL because `boardGenerateFieldNames` is not exported.

- [ ] **Step 3: Add the generation field list**

In `fe/src/modules/clocktower/BoardBuilderPage.tsx`, add this constant after `const defaultScriptCode = ...`:

```tsx
export const boardGenerateFieldNames: Array<keyof BoardEditorFormValues> = [
    'scriptCode',
    'playerCount',
    'difficulty',
    'chaos',
    'evilPressure',
    'newbieFriendly',
    'candidateCount',
    'bannedRoleCodes',
    'lockedRoleCodes',
    'seed',
]
```

- [ ] **Step 4: Use the field list in `generate()`**

In `fe/src/modules/clocktower/BoardBuilderPage.tsx`, replace:

```tsx
        const values = await form.validateFields()
```

inside `async function generate()` with:

```tsx
        const values = await form.validateFields(boardGenerateFieldNames)
```

Do not change `validateCurrentBoard()` or `saveCurrentBoard()`. Those workflows must still validate `roleCodes`.

- [ ] **Step 5: Run the board test**

Run:

```bash
cd fe
./node_modules/.bin/vitest run src/modules/clocktower/BoardBuilderPage.test.tsx
```

Expected: PASS. The field-list test confirms `roleCodes` is not required for generation.

- [ ] **Step 6: Commit Task 2**

Run:

```bash
git add fe/src/modules/clocktower/BoardBuilderPage.tsx fe/src/modules/clocktower/BoardBuilderPage.test.tsx
git commit -m "fix(clocktower): generate boards without editor roles"
```

## Task 3: Route Request and Operation Errors to the Global Alert

**Files:**
- Modify: `fe/src/modules/clocktower/BoardBuilderPage.tsx`
- Modify: `fe/src/modules/clocktower/ReplayListPage.tsx`
- Modify: `fe/src/modules/clocktower/ReplayPage.tsx`
- Modify: `fe/src/modules/clocktower/RuleDataPage.tsx`
- Modify: `fe/src/modules/clocktower/RoomLobbyPage.tsx`
- Modify: `fe/src/modules/clocktower/GameRoomPage.tsx`
- Modify: `fe/src/modules/clocktower/RoomListPage.tsx`
- Modify: `fe/src/modules/clocktower/StorytellerGrimoirePage.tsx`
- Modify: `fe/src/modules/agent/AgentMemoryPage.tsx`
- Modify: `fe/src/modules/agent/AgentMemoryArchivePage.tsx`
- Modify: `fe/src/modules/agent/AgentDebugPage.tsx`
- Modify: `fe/src/modules/agent/mcp/McpServerListPage.tsx`
- Modify: `fe/src/modules/agent/mcp/McpToolListPage.tsx`
- Modify: `fe/src/modules/dashboard/DashboardPage.tsx`
- Modify: `fe/src/modules/rag/RetrievalLabPage.tsx`
- Modify: `fe/src/modules/rag/RagChatPage.tsx`
- Modify: `fe/src/modules/chat/pages/ChatPage.tsx`

- [ ] **Step 1: Confirm the current error-toast inventory**

Run:

```bash
rg -n "message\\.error\\(|appMessage\\.error\\(" fe/src/modules fe/src/app fe/src/components -S
```

Expected: output includes request/operation error toasts in Clocktower, Agent, RAG, dashboard, chat, and MCP files. `providers.tsx` should no longer appear because Task 1 already moved `AsyncErrorReporter` to `reportGlobalError`.

- [ ] **Step 2: Convert `BoardBuilderPage` local request errors to the global alert**

Modify `fe/src/modules/clocktower/BoardBuilderPage.tsx`.

Keep `Alert` imported because board validation still renders an inline `Alert`. Add:

```tsx
import {clearGlobalError, reportGlobalError} from '../../app/globalError'
```

Remove:

```tsx
import {resolveErrorMessage} from '../../services/request'
```

Remove:

```tsx
    const [error, setError] = useState<string>()
```

Remove this render line:

```tsx
            {error && <Alert showIcon style={{marginBottom: 16}} title={error} type="error"/>}
```

Replace each `setError(undefined)` in request operations with:

```tsx
        clearGlobalError()
```

Replace each `setError(resolveErrorMessage(caught))` with:

```tsx
            reportGlobalError(caught)
```

In the roles-loading `.catch`, replace:

```tsx
                    setError(resolveErrorMessage(caught))
```

with:

```tsx
                    reportGlobalError(caught)
```

- [ ] **Step 3: Convert Clocktower pages from error toast to global alert**

In each listed Clocktower file, add the shown import and replace request error toasts:

```tsx
import {reportGlobalError} from '../../app/globalError'
```

Use this path for:

- `fe/src/modules/clocktower/ReplayListPage.tsx`
- `fe/src/modules/clocktower/ReplayPage.tsx`
- `fe/src/modules/clocktower/RuleDataPage.tsx`
- `fe/src/modules/clocktower/RoomLobbyPage.tsx`
- `fe/src/modules/clocktower/GameRoomPage.tsx`
- `fe/src/modules/clocktower/RoomListPage.tsx`
- `fe/src/modules/clocktower/StorytellerGrimoirePage.tsx`

Replace:

```tsx
message.error(resolveErrorMessage(caught))
```

with:

```tsx
reportGlobalError(caught)
```

Do not replace `message.success(...)` or `message.warning(...)`.

If a file no longer uses `resolveErrorMessage`, remove its `resolveErrorMessage` import. `StorytellerGrimoirePage.tsx` still uses `resolveErrorMessage` inside warning messages such as:

```tsx
message.warning(`裁定已生效，裁定历史刷新失败：${resolveErrorMessage(caught)}`)
```

so keep the `resolveErrorMessage` import in that file.

- [ ] **Step 4: Convert Agent, dashboard, RAG, chat, and MCP error toasts**

Use these import paths:

```tsx
// From fe/src/modules/agent/*.tsx, fe/src/modules/dashboard/*.tsx, fe/src/modules/rag/*.tsx
import {reportGlobalError} from '../../app/globalError'

// From fe/src/modules/agent/mcp/*.tsx and fe/src/modules/chat/pages/*.tsx
import {reportGlobalError} from '../../../app/globalError'
```

Apply these replacements:

```tsx
message.error(resolveErrorMessage(requestError))
```

becomes:

```tsx
reportGlobalError(requestError)
```

```tsx
appMessage.error(resolveErrorMessage(requestError))
```

becomes:

```tsx
reportGlobalError(requestError)
```

```tsx
message.error((error as Error).message)
```

becomes:

```tsx
reportGlobalError(error)
```

```tsx
message.error(result.errorMessage || '连接测试失败')
```

becomes:

```tsx
reportGlobalError(result.errorMessage || '连接测试失败')
```

```tsx
appMessage.error('复制失败')
```

becomes:

```tsx
reportGlobalError('复制失败')
```

In `ChatPage.tsx`, `AgentDebugPage.tsx`, and `RagChatPage.tsx`, also report streaming request failures globally while preserving the local chat message error state. In the stream catch blocks, after:

```tsx
            const errorMessage = resolveErrorMessage(requestError)
            setError(errorMessage)
```

add:

```tsx
            reportGlobalError(requestError)
```

Keep `appMessage.success(...)` and `appMessage.warning(...)` as-is.

- [ ] **Step 5: Remove unused Ant Design `App` bindings where possible**

After replacing error toasts, clean up each file:

- If a file no longer calls `message.success(...)`, `message.warning(...)`, or another `message` method, remove `App` from its `antd` import and remove `const {message} = App.useApp()`.
- If a file still uses success or warning toasts, keep `App.useApp()`.
- If a file no longer uses `resolveErrorMessage`, remove that import.

Do not remove `App.useApp()` from files that still use success or warning messages.

- [ ] **Step 6: Verify no request/operation error toasts remain**

Run:

```bash
rg -n "message\\.error\\(|appMessage\\.error\\(" fe/src/modules fe/src/app fe/src/components -S
```

Expected: the only remaining result is the local password prompt:

```text
fe/src/modules/rbac/users/UserListPage.tsx:192:                    message.error('请输入新密码')
```

If line numbers shift, the same file and text are acceptable.

- [ ] **Step 7: Run affected frontend tests**

Run:

```bash
cd fe
./node_modules/.bin/vitest run \
  src/app/globalError.test.tsx \
  src/modules/clocktower/BoardBuilderPage.test.tsx \
  src/modules/clocktower/ReplayListPage.test.tsx \
  src/modules/clocktower/ReplayPage.test.tsx \
  src/modules/clocktower/RuleDataPage.test.tsx \
  src/modules/clocktower/RoomLobbyPage.test.tsx \
  src/modules/clocktower/GameRoomPage.test.tsx \
  src/modules/clocktower/StorytellerGrimoirePage.test.tsx
```

Expected: PASS. Existing Ant Design deprecation warnings may appear in Clocktower tests; they are acceptable if the test run exits 0.

- [ ] **Step 8: Commit Task 3**

Run:

```bash
git add fe/src
git commit -m "refactor(frontend): show request errors in global alert"
```

## Task 4: Final Frontend Verification

**Files:**
- Verify all files changed in Tasks 1-3.

- [ ] **Step 1: Run typecheck**

Run:

```bash
cd fe
bun run typecheck
```

Expected: PASS with `tsc -b --noEmit` exiting 0.

- [ ] **Step 2: Run full frontend Vitest**

Run:

```bash
cd fe
./node_modules/.bin/vitest run --reporter=dot
```

Expected: PASS. Current baseline is 34 test files and 136 tests; the count will increase by one file and two tests after adding `globalError.test.tsx`.

- [ ] **Step 3: Run scoped ESLint on changed frontend files**

Run:

```bash
cd fe
./node_modules/.bin/eslint \
  src/app/globalError.tsx \
  src/app/globalError.test.tsx \
  src/app/providers.tsx \
  src/modules/clocktower/BoardBuilderPage.tsx \
  src/modules/clocktower/BoardBuilderPage.test.tsx \
  src/modules/clocktower/ReplayListPage.tsx \
  src/modules/clocktower/ReplayPage.tsx \
  src/modules/clocktower/RuleDataPage.tsx \
  src/modules/clocktower/RoomLobbyPage.tsx \
  src/modules/clocktower/GameRoomPage.tsx \
  src/modules/clocktower/RoomListPage.tsx \
  src/modules/clocktower/StorytellerGrimoirePage.tsx \
  src/modules/agent/AgentMemoryPage.tsx \
  src/modules/agent/AgentMemoryArchivePage.tsx \
  src/modules/agent/AgentDebugPage.tsx \
  src/modules/agent/mcp/McpServerListPage.tsx \
  src/modules/agent/mcp/McpToolListPage.tsx \
  src/modules/dashboard/DashboardPage.tsx \
  src/modules/rag/RetrievalLabPage.tsx \
  src/modules/rag/RagChatPage.tsx \
  src/modules/chat/pages/ChatPage.tsx
```

Expected: PASS with no errors.

- [ ] **Step 4: Run Ant Design lint on changed Ant Design component files**

Run:

```bash
cd fe
antd lint \
  src/app/globalError.tsx \
  src/app/providers.tsx \
  src/modules/clocktower/BoardBuilderPage.tsx \
  src/modules/clocktower/ReplayListPage.tsx \
  src/modules/clocktower/ReplayPage.tsx \
  src/modules/clocktower/RuleDataPage.tsx \
  src/modules/clocktower/RoomLobbyPage.tsx \
  src/modules/clocktower/GameRoomPage.tsx \
  src/modules/clocktower/RoomListPage.tsx \
  src/modules/clocktower/StorytellerGrimoirePage.tsx \
  src/modules/agent/AgentMemoryPage.tsx \
  src/modules/agent/AgentMemoryArchivePage.tsx \
  src/modules/agent/AgentDebugPage.tsx \
  src/modules/agent/mcp/McpServerListPage.tsx \
  src/modules/agent/mcp/McpToolListPage.tsx \
  src/modules/dashboard/DashboardPage.tsx \
  src/modules/rag/RetrievalLabPage.tsx \
  src/modules/rag/RagChatPage.tsx \
  src/modules/chat/pages/ChatPage.tsx \
  --format json
```

Expected: PASS with no Ant Design lint violations for changed files.

- [ ] **Step 5: Run final source checks**

Run:

```bash
git diff --check HEAD
git status --short
```

Expected: `git diff --check HEAD` exits 0. `git status --short` shows only intended modified files before the final commit.

- [ ] **Step 6: Commit verification cleanup if needed**

If Task 4 required code or lint fixes, commit them:

```bash
git add fe/src
git commit -m "fix(frontend): clean global alert integration"
```

If Task 4 required no file changes, do not create an empty commit.

## Self-Review Checklist

- Spec coverage:
  - Board generation no longer validates `roleCodes`: Task 2.
  - Global request/async errors render through Alert: Tasks 1 and 3.
  - Success toasts remain: Task 3 instructions explicitly keep `message.success`.
  - Backend behavior unchanged: no backend files in this plan.
- Red-flag scan:
  - No task contains forbidden unfinished-work markers or an unspecified implementation slot.
  - Every code-changing step includes exact code or exact replacement patterns.
- Type consistency:
  - Global helper names are consistent across all tasks: `reportGlobalError`, `clearGlobalError`, `registerGlobalErrorHandler`, `GlobalErrorProvider`, `GlobalErrorAlert`.
  - Board field-list name is consistent across tests and implementation: `boardGenerateFieldNames`.
