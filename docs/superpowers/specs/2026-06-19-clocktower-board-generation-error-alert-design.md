# Clocktower Board Generation and Global Error Alert Design

## Context

The Clocktower board page currently uses one Ant Design `Form` for both generated-board parameters and the manual board editor. The `generate` action calls `form.validateFields()` without a field list, so the editor-only `roleCodes` rule blocks candidate generation when no manual roles are selected.

The frontend also still shows many request failures through `message.error(...)`. A network failure such as `Network Error` should be persistent enough for users to notice and dismiss, so request errors should use an Ant Design `Alert` instead of a transient toast.

## Goals

- Allow `/clocktower/boards` to generate candidate boards without requiring any manual editor role selection.
- Show request and async errors through an application-level closable `Alert`.
- Keep success feedback such as saved/deleted notifications as `message.success`.
- Avoid changing backend validation rules or room creation behavior.

## Non-Goals

- Do not change the board generation API contract.
- Do not redesign the board page layout.
- Do not replace inline domain result alerts such as board validation success/warning details.
- Do not convert every non-request local validation hint in the app to global alerts.

## Board Generation Fix

`BoardBuilderPage.generate` will validate only the fields used by `ClocktowerBoardGenerateRequest`:

- `scriptCode`
- `playerCount`
- `difficulty`
- `chaos`
- `evilPressure`
- `newbieFriendly`
- `candidateCount`
- `bannedRoleCodes`
- `lockedRoleCodes`
- `seed`

The editor-only `roleCodes` field remains required for manual validation and saving, but it will no longer participate in candidate generation. This keeps the top generation workflow independent from the manual editor workflow.

## Global Error Alert

Add a small global error channel in the frontend provider layer:

- A provider-owned error state stores the latest resolved error message.
- A hook or helper exposes `showGlobalError(error)` and `clearGlobalError()`.
- `AppProviders` renders a closable Ant Design `Alert type="error"` near the top of the application chrome, inside the existing Ant Design provider tree.
- `AsyncErrorReporter` reports errors through the global Alert channel instead of `message.error`.

The global Alert will use `resolveErrorMessage(error)` so existing `ApiRequestError`, ordinary `Error`, and unknown values keep the same message resolution. `Network Error` will therefore appear as a dismissible page-level Alert.

## Request Error Usage

Convert frontend failure feedback that currently uses transient error toasts to the global Alert channel when it represents a request, async, or operation failure. This includes Clocktower, agent, RAG, RBAC, dashboard, and provider-level error reporting.

Keep non-error toasts as-is:

- Success feedback such as save/delete/create completion.
- Local form validation prompts such as "请输入新密码".
- Domain state alerts already rendered inline, such as board validation pass/fail details and chat message error content.

If a component already renders an inline `Alert` for a persistent page-local error, it can either keep that local Alert or route to the global Alert. The implementation should avoid showing the same error in both places.

## Testing

- Add or update a board page test proving `generateClocktowerBoard` can be called when `roleCodes` is empty.
- Add or update provider-level tests proving reported async errors render an Ant Design `Alert` instead of calling the toast error API.
- Run targeted Clocktower frontend tests, request/provider tests if touched, typecheck, and scoped lint for changed files.

## Risks

- If multiple requests fail at once, the latest error will replace the previous one. This is acceptable for this small fix and avoids introducing a notification queue.
- Some direct `message.error` calls may intentionally remain when they are local form prompts rather than request or async failures.
