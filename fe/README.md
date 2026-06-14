# CyberMario Frontend

React + TypeScript + Vite frontend for CyberMario. The UI is built with Ant Design and talks to the backend through
`/api` and `/demo` routes.

## Scripts

```bash
npm run dev
npm run lint
npm run typecheck
npm run test
npm run test:coverage
npm run build
npm run analyze
```

- `dev` starts Vite locally.
- `lint` runs ESLint.
- `typecheck` runs TypeScript project checks without emitting files.
- `test` runs Vitest unit tests.
- `test:coverage` runs Vitest with coverage output.
- `build` runs typecheck and Vite production build.
- `analyze` builds with bundle analysis output at `dist/stats.html`.

## Backend Target

Vite proxies `/api` and `/demo` to the backend. The target is resolved in this order:

1. `VITE_BACKEND_TARGET`
2. `VITE_API_BASE_URL`
3. `http://localhost:${VITE_BACKEND_PORT || BACKEND_PORT || 28080}`

When `VITE_API_BASE_URL` is set in browser builds, frontend requests use it as the absolute API base URL.

## Response Contracts

The frontend keeps normal JSON responses and streaming responses separate.

- Spring MVC JSON responses and WebFlux `Mono<ApiResponse<T>>` responses use `requestJson<T>()`.
- File uploads use `requestFormData<T>()`, and their response body is still parsed as normal JSON.
- True NDJSON HTTP streams use `streamJsonLines<T>()`.

`Mono` is not treated as a frontend stream because the browser receives one complete HTTP response body. Only endpoints
that continuously write newline-delimited JSON events should use `streamJsonLines<T>()`.

## Request Helpers

Core request utilities live in `src/services/request.ts`.

- `requestJson<T>()` unwraps backend `ApiResponse<T>` and throws `ApiRequestError` for HTTP or business errors.
- `requestFormData<T>()` sends `FormData` and unwraps the same response envelope.
- `streamJsonLines<T>()` reads NDJSON events and uses the same backend error envelope when the stream request fails.
- `buildSearchParams()` in `src/services/urlSearch.ts` builds encoded query strings for service APIs.

## Validation

Before handing frontend changes back, run:

```bash
npm run lint
npm run typecheck
npm run test
npm run build
```
