# Browser Cookie CSRF Auth Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move browser authentication away from `localStorage` tokens to HttpOnly Cookie + CSRF while keeping the same API usable by non-browser Bearer clients.

**Architecture:** The backend will support two credential channels: `Authorization: Bearer` for native/desktop/script clients and HttpOnly access/refresh cookies for browser clients marked by `X-Client-Type: browser`. Browser unsafe requests will require `X-XSRF-TOKEN`; Bearer and non-browser requests remain compatible. The frontend will stop storing tokens, send browser mode headers and credentials, initialize CSRF tokens, and refresh through cookies.

**Tech Stack:** Spring Boot WebFlux, Spring Security WebFlux CSRF, JWT, JPA/Redis token state, React 19, Vite, axios, fetch, Vitest, Maven, Bun.

---

## Implementation Notes

- This plan intentionally uses pseudocode snippets instead of large implementation blocks, per user request.
- Keep commits small. Each task ends with one commit.
- Do not start the project. Use tests and compile checks only.
- Do not modify existing Flyway migrations. This change does not need a database migration.
- Preserve non-browser `Authorization: Bearer` behavior unless a task explicitly scopes a browser-only change.

---

## File Structure

Backend files:

- Create `be/src/main/java/top/egon/mario/rbac/service/security/BrowserAuthCookieProperties.java`: configuration for browser auth cookie names, paths, SameSite, and Secure behavior.
- Create `be/src/main/java/top/egon/mario/rbac/service/security/BrowserAuthCookieService.java`: writes and clears auth cookies, detects browser mode, reads token cookies, and redacts browser response tokens.
- Create `be/src/main/java/top/egon/mario/rbac/dto/response/CsrfTokenResponse.java`: small response DTO for `GET /api/auth/csrf`.
- Modify `be/src/main/java/top/egon/mario/rbac/config/RbacSecurityConfig.java`: enable CSRF, configure token repository, register browser cookie properties, and wire CSRF access denied handling.
- Modify `be/src/main/java/top/egon/mario/rbac/service/security/RbacSecurityExceptionHandler.java`: return a stable CSRF error code for CSRF failures.
- Modify `be/src/main/java/top/egon/mario/rbac/service/security/JwtAuthenticationWebFilter.java`: resolve access token from Bearer first, then browser access cookie.
- Modify `be/src/main/java/top/egon/mario/rbac/service/security/RbacPublicApiPolicy.java`: explicitly allow `GET /api/auth/csrf`.
- Modify `be/src/main/java/top/egon/mario/rbac/web/AuthController.java`: browser cookie login/register/refresh/logout/csrf handling while preserving non-browser body token behavior.
- Modify `be/src/main/resources/application.yaml` and `be/src/test/resources/application.yaml`: add browser cookie auth properties.
- Add or update backend tests under `be/src/test/java/top/egon/mario/rbac/...`.

Frontend files:

- Create `fe/src/services/csrfToken.ts`: reads `XSRF-TOKEN` from `document.cookie`, initializes CSRF, and determines unsafe methods.
- Create `fe/src/services/csrfToken.test.ts`: tests cookie parsing and unsafe method logic.
- Modify `fe/src/services/tokenStorage.ts`: remove token persistence behavior and expose legacy token cleanup/session hint helpers.
- Modify `fe/src/services/tokenStorage.test.ts`: assert old sensitive localStorage keys are cleared and never saved.
- Modify `fe/src/services/request.ts`: set browser mode header, credentials, CSRF headers, cookie refresh retry, and remove Authorization header from browser requests.
- Modify `fe/src/services/request.test.ts`: cover CSRF, credentials, browser header, cookie refresh, and streaming POST.
- Modify `fe/src/modules/auth/authStore.tsx`: bootstrap from `/api/auth/me`, clear legacy token keys, and stop saving token strings.
- Modify `fe/src/modules/auth/authService.ts`: logout/refresh-compatible request bodies for cookie mode if needed.
- Modify `fe/src/modules/auth/authTypes.ts`: keep token fields optional/nullable for non-browser API compatibility.

---

### Task 1: Backend Browser Cookie Configuration and Helper

**Files:**
- Create: `be/src/main/java/top/egon/mario/rbac/service/security/BrowserAuthCookieProperties.java`
- Create: `be/src/main/java/top/egon/mario/rbac/service/security/BrowserAuthCookieService.java`
- Modify: `be/src/main/java/top/egon/mario/rbac/config/RbacSecurityConfig.java`
- Modify: `be/src/main/resources/application.yaml`
- Modify: `be/src/test/resources/application.yaml`
- Test: `be/src/test/java/top/egon/mario/rbac/service/security/BrowserAuthCookieServiceTests.java`

- [ ] **Step 1: Write failing tests for cookie writing, clearing, browser detection, and redaction**

Pseudocode:

```pseudocode
test browser request detected by X-Client-Type:
  exchange = request with header X-Client-Type=browser
  expect cookieService.isBrowserClient(exchange) is true

test token cookies are httpOnly and path scoped:
  response = fake LoginResponse with access and refresh token
  cookieService.writeTokenCookies(exchange, response)
  expect CM_ACCESS_TOKEN Set-Cookie has HttpOnly, Path=/api, Max-Age access ttl
  expect CM_REFRESH_TOKEN Set-Cookie has HttpOnly, Path=/api/auth, Max-Age refresh ttl

test browser response redacts tokens:
  redacted = cookieService.withoutBodyTokens(loginResponse)
  expect redacted.accessToken is null
  expect redacted.refreshToken is null
  expect redacted.user and permissions remain present

test clear cookies expires both auth cookies:
  cookieService.clearTokenCookies(exchange)
  expect CM_ACCESS_TOKEN Max-Age is zero
  expect CM_REFRESH_TOKEN Max-Age is zero
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
cd be && ./mvnw -Dmaven.build.cache.enabled=false -Dtest=BrowserAuthCookieServiceTests test
```

Expected: FAIL because `BrowserAuthCookieService` and its properties do not exist.

- [ ] **Step 3: Add browser cookie properties and helper**

Implementation pseudocode:

```pseudocode
BrowserAuthCookieProperties:
  prefix = mario.security.browser-cookie
  fields:
    enabled = true
    secure = true
    sameSite = Lax
    accessCookieName = CM_ACCESS_TOKEN
    refreshCookieName = CM_REFRESH_TOKEN
    accessCookiePath = /api
    refreshCookiePath = /api/auth
    browserHeaderName = X-Client-Type
    browserHeaderValue = browser

BrowserAuthCookieService:
  isBrowserClient(exchange):
    compare configured browser header ignoring case

  writeTokenCookies(exchange, LoginResponse):
    write access cookie using response.accessToken and response.accessTokenExpiresInSeconds
    write refresh cookie using response.refreshToken and response.refreshTokenExpiresInSeconds

  readAccessToken(exchange):
    return CM_ACCESS_TOKEN cookie value if present

  readRefreshToken(exchange):
    return CM_REFRESH_TOKEN cookie value if present

  clearTokenCookies(exchange):
    write expired access and refresh cookies

  withoutBodyTokens(response):
    rebuild LoginResponse with token fields null and all non-token fields copied
```

Configuration notes:

- Add `BrowserAuthCookieProperties.class` to `@EnableConfigurationProperties`.
- Add `mario.security.browser-cookie.secure: ${BROWSER_AUTH_COOKIE_SECURE:true}` to main config.
- Add test override `secure: false` in test config for easier assertions.

- [ ] **Step 4: Run tests to verify they pass**

Run:

```bash
cd be && ./mvnw -Dmaven.build.cache.enabled=false -Dtest=BrowserAuthCookieServiceTests test
```

Expected: PASS.

- [ ] **Step 5: Commit**

Run:

```bash
git add be/src/main/java/top/egon/mario/rbac/service/security/BrowserAuthCookieProperties.java \
  be/src/main/java/top/egon/mario/rbac/service/security/BrowserAuthCookieService.java \
  be/src/main/java/top/egon/mario/rbac/config/RbacSecurityConfig.java \
  be/src/main/resources/application.yaml \
  be/src/test/resources/application.yaml \
  be/src/test/java/top/egon/mario/rbac/service/security/BrowserAuthCookieServiceTests.java
git commit -m "Add browser auth cookie support"
```

---

### Task 2: Backend Access Token Resolution from Cookie

**Files:**
- Modify: `be/src/main/java/top/egon/mario/rbac/service/security/JwtAuthenticationWebFilter.java`
- Test: `be/src/test/java/top/egon/mario/rbac/service/security/JwtAuthenticationWebFilterTests.java`

- [ ] **Step 1: Add failing tests for cookie auth and Bearer priority**

Pseudocode:

```pseudocode
test authenticates access token from browser cookie:
  request has CM_ACCESS_TOKEN=valid-cookie-access
  authApplication.authenticateAccessToken receives valid-cookie-access
  chain is called
  permission version header is written

test bearer token wins over cookie token:
  request has Authorization=Bearer bearer-access
  request also has CM_ACCESS_TOKEN=cookie-access
  authApplication.authenticateAccessToken receives bearer-access
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
cd be && ./mvnw -Dmaven.build.cache.enabled=false -Dtest=JwtAuthenticationWebFilterTests test
```

Expected: FAIL because the filter only reads Bearer headers.

- [ ] **Step 3: Update the filter to use the cookie service**

Implementation pseudocode:

```pseudocode
resolveAccessToken(exchange):
  bearer = parse Authorization header
  if bearer exists:
    return bearer
  return browserAuthCookieService.readAccessToken(exchange).orElse(null)

filter(exchange, chain):
  token = resolveAccessToken(exchange)
  existing authentication flow remains unchanged
```

Constructor impact:

- Inject `BrowserAuthCookieService`.
- Update tests to construct the filter with a real or mocked cookie service.

- [ ] **Step 4: Run tests to verify they pass**

Run:

```bash
cd be && ./mvnw -Dmaven.build.cache.enabled=false -Dtest=JwtAuthenticationWebFilterTests test
```

Expected: PASS.

- [ ] **Step 5: Commit**

Run:

```bash
git add be/src/main/java/top/egon/mario/rbac/service/security/JwtAuthenticationWebFilter.java \
  be/src/test/java/top/egon/mario/rbac/service/security/JwtAuthenticationWebFilterTests.java
git commit -m "Support browser access token cookies"
```

---

### Task 3: Backend CSRF Configuration and CSRF Endpoint

**Files:**
- Create: `be/src/main/java/top/egon/mario/rbac/dto/response/CsrfTokenResponse.java`
- Modify: `be/src/main/java/top/egon/mario/rbac/config/RbacSecurityConfig.java`
- Modify: `be/src/main/java/top/egon/mario/rbac/service/security/RbacSecurityExceptionHandler.java`
- Modify: `be/src/main/java/top/egon/mario/rbac/service/security/RbacPublicApiPolicy.java`
- Modify: `be/src/main/java/top/egon/mario/rbac/web/AuthController.java`
- Test: `be/src/test/java/top/egon/mario/config/RbacSecurityConfigCsrfTests.java`
- Test: `be/src/test/java/top/egon/mario/rbac/service/security/RbacSecurityExceptionHandlerTests.java`

- [ ] **Step 1: Add failing tests for CSRF endpoint and CSRF error response**

Pseudocode:

```pseudocode
test csrf endpoint returns token metadata:
  GET /api/auth/csrf
  expect status 200
  expect body.data.headerName is X-XSRF-TOKEN
  expect body.data.parameterName is present
  expect Set-Cookie contains XSRF-TOKEN

test csrf exception maps to AUTH_CSRF_INVALID:
  call securityExceptionHandler.handle(exchange, CsrfException)
  expect status 403
  expect body.code is AUTH_CSRF_INVALID

test unsafe browser request without csrf is forbidden:
  POST /api/auth/login with X-Client-Type=browser and no csrf
  expect status 403
  expect body.code is AUTH_CSRF_INVALID
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
cd be && ./mvnw -Dmaven.build.cache.enabled=false -Dtest=RbacSecurityConfigCsrfTests,RbacSecurityExceptionHandlerTests test
```

Expected: FAIL because CSRF is disabled and `/api/auth/csrf` is missing.

- [ ] **Step 3: Enable CSRF and add the endpoint**

Implementation pseudocode:

```pseudocode
RbacSecurityConfig.csrf:
  repository = CookieServerCsrfTokenRepository.withHttpOnlyFalse()
  repository cookie path = /
  repository cookie secure = browserCookieProperties.secure
  request matcher:
    if method is safe: no csrf
    if Authorization starts with Bearer: no csrf
    if request has browser header: csrf required
    if request has CM_ACCESS_TOKEN or CM_REFRESH_TOKEN cookie: csrf required
    otherwise: no csrf
  accessDeniedHandler = securityExceptionHandler

AuthController.csrf(exchange):
  csrfMono = exchange attribute CsrfToken class name
  subscribe token
  return CsrfTokenResponse(headerName, parameterName, token)

RbacSecurityExceptionHandler:
  if denied is CsrfException:
    code = AUTH_CSRF_INVALID
    message = csrf token is invalid
  else:
    existing forbidden response
```

Public API policy:

- Add `GET /api/auth/csrf` to explicit public path matchers.
- Keep existing POST auth public endpoints unchanged.

- [ ] **Step 4: Run tests to verify they pass**

Run:

```bash
cd be && ./mvnw -Dmaven.build.cache.enabled=false -Dtest=RbacSecurityConfigCsrfTests,RbacSecurityExceptionHandlerTests test
```

Expected: PASS.

- [ ] **Step 5: Commit**

Run:

```bash
git add be/src/main/java/top/egon/mario/rbac/dto/response/CsrfTokenResponse.java \
  be/src/main/java/top/egon/mario/rbac/config/RbacSecurityConfig.java \
  be/src/main/java/top/egon/mario/rbac/service/security/RbacSecurityExceptionHandler.java \
  be/src/main/java/top/egon/mario/rbac/service/security/RbacPublicApiPolicy.java \
  be/src/main/java/top/egon/mario/rbac/web/AuthController.java \
  be/src/test/java/top/egon/mario/config/RbacSecurityConfigCsrfTests.java \
  be/src/test/java/top/egon/mario/rbac/service/security/RbacSecurityExceptionHandlerTests.java
git commit -m "Enable browser CSRF protection"
```

---

### Task 4: Backend Browser Auth Controller Compatibility

**Files:**
- Modify: `be/src/main/java/top/egon/mario/rbac/web/AuthController.java`
- Modify: `be/src/main/java/top/egon/mario/rbac/dto/request/RefreshTokenRequest.java` only if null-safe request binding requires it
- Test: `be/src/test/java/top/egon/mario/rbac/web/AuthControllerBrowserCookieTests.java`
- Test: `be/src/test/java/top/egon/mario/rbac/application/RbacAuthApplicationTests.java` if application behavior requires a focused assertion update

- [ ] **Step 1: Add failing controller tests for browser and non-browser modes**

Pseudocode:

```pseudocode
test browser login writes cookies and hides body tokens:
  POST /api/auth/login with X-Client-Type=browser and valid csrf
  mock authApplication.login returns LoginResponse with tokens
  expect Set-Cookie has CM_ACCESS_TOKEN and CM_REFRESH_TOKEN
  expect response body has no accessToken or refreshToken

test non-browser login still returns body tokens:
  POST /api/auth/login without X-Client-Type
  mock authApplication.login returns LoginResponse with tokens
  expect no auth Set-Cookie
  expect response body includes accessToken and refreshToken

test browser refresh reads refresh cookie:
  POST /api/auth/refresh with X-Client-Type=browser, csrf, and CM_REFRESH_TOKEN cookie
  expect authApplication.refresh receives cookie refresh token
  expect new cookies are written

test non-browser refresh reads body:
  POST /api/auth/refresh with body refreshToken
  expect authApplication.refresh receives body refresh token
  expect response body includes token fields

test browser logout clears cookies:
  POST /api/auth/logout with browser header, csrf, and refresh cookie
  expect authApplication.logout called with cookie token
  expect expired auth cookies are written
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
cd be && ./mvnw -Dmaven.build.cache.enabled=false -Dtest=AuthControllerBrowserCookieTests test
```

Expected: FAIL because controller methods do not write/read cookies and refresh/logout require a body.

- [ ] **Step 3: Update AuthController for dual-mode behavior**

Implementation pseudocode:

```pseudocode
login(request, exchange):
  response = authApplication.login(...)
  if browserCookieService.isBrowserClient(exchange):
    browserCookieService.writeTokenCookies(exchange, response)
    return ApiResponse.ok(browserCookieService.withoutBodyTokens(response), traceId)
  return ApiResponse.ok(response, traceId)

refresh(optionalRequest, exchange):
  refreshToken = browser cookie token when browser mode, else optionalRequest.refreshToken
  response = authApplication.refresh(refreshToken, ...)
  if browser mode:
    write cookies and redact body tokens
  else:
    return body tokens

logout(optionalRequest, exchange):
  refreshToken = browser cookie token or optionalRequest.refreshToken
  if refreshToken exists:
    authApplication.logout(refreshToken)
  clear browser cookies when browser mode
```

Controller style:

- Preserve `ReactiveRbacSupport.blocking(...)` patterns.
- Use `@RequestBody(required = false)` for refresh/logout so browser cookie calls can send an empty JSON body or no body.
- Keep public RBAC annotations consistent with existing auth endpoints.

- [ ] **Step 4: Run tests to verify they pass**

Run:

```bash
cd be && ./mvnw -Dmaven.build.cache.enabled=false -Dtest=AuthControllerBrowserCookieTests test
```

Expected: PASS.

- [ ] **Step 5: Run nearby auth tests**

Run:

```bash
cd be && ./mvnw -Dmaven.build.cache.enabled=false -Dtest=RbacAuthApplicationTests,RbacAuthApplicationValidationTests,AuthControllerBrowserCookieTests test
```

Expected: PASS.

- [ ] **Step 6: Commit**

Run:

```bash
git add be/src/main/java/top/egon/mario/rbac/web/AuthController.java \
  be/src/main/java/top/egon/mario/rbac/dto/request/RefreshTokenRequest.java \
  be/src/test/java/top/egon/mario/rbac/web/AuthControllerBrowserCookieTests.java \
  be/src/test/java/top/egon/mario/rbac/application/RbacAuthApplicationTests.java
git commit -m "Support browser cookie auth endpoints"
```

If `RefreshTokenRequest.java` and `RbacAuthApplicationTests.java` were not changed, omit them from `git add`.

---

### Task 5: Frontend Legacy Token Storage Cleanup

**Files:**
- Modify: `fe/src/services/tokenStorage.ts`
- Modify: `fe/src/services/tokenStorage.test.ts`
- Modify: `fe/src/modules/auth/authStore.tsx`

- [ ] **Step 1: Write failing tests for no token persistence and legacy cleanup**

Pseudocode:

```pseudocode
test saveTokens does not write sensitive tokens:
  call saveTokens({ accessToken, refreshToken })
  expect localStorage cyber-mario-access-token is null
  expect localStorage cyber-mario-refresh-token is null

test clearTokens removes legacy keys:
  seed localStorage with old token keys
  call clearTokens()
  expect old access, refresh, expiry keys are removed

test hasStoredToken no longer treats legacy tokens as active browser session:
  seed localStorage old tokens
  expect hasStoredToken() is false
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
cd fe && bun run test -- tokenStorage.test.ts
```

Expected: FAIL because the current module stores and reads token strings.

- [ ] **Step 3: Update tokenStorage to cleanup-only behavior**

Implementation pseudocode:

```pseudocode
legacyKeys = old access, refresh, access expiry, refresh expiry keys

saveTokens(tokens):
  do not store tokens
  optionally store non-sensitive session hint only if needed

getAccessToken():
  return null

getRefreshToken():
  return null

shouldRefreshAccessToken():
  return false

clearTokens():
  remove every legacy token key

hasStoredToken():
  return false
```

AuthProvider bootstrap adjustment pseudocode:

```pseudocode
on mount:
  clear legacy tokens
  call reload()
  if reload fails:
    clear local session
  finish bootstrapping
```

- [ ] **Step 4: Run tests to verify they pass**

Run:

```bash
cd fe && bun run test -- tokenStorage.test.ts
```

Expected: PASS.

- [ ] **Step 5: Commit**

Run:

```bash
git add fe/src/services/tokenStorage.ts \
  fe/src/services/tokenStorage.test.ts \
  fe/src/modules/auth/authStore.tsx
git commit -m "Remove browser token persistence"
```

---

### Task 6: Frontend CSRF Helper

**Files:**
- Create: `fe/src/services/csrfToken.ts`
- Create: `fe/src/services/csrfToken.test.ts`

- [ ] **Step 1: Write failing tests for cookie parsing and unsafe method detection**

Pseudocode:

```pseudocode
test reads XSRF token from document cookie:
  document.cookie contains XSRF-TOKEN=abc%20123
  expect readCsrfToken() is "abc 123"

test unsafe method detection:
  expect POST, PUT, PATCH, DELETE are unsafe
  expect GET, HEAD, OPTIONS, TRACE are safe

test csrf header is only returned for unsafe requests with token:
  method POST and token exists -> header X-XSRF-TOKEN
  method GET and token exists -> no csrf header
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
cd fe && bun run test -- csrfToken.test.ts
```

Expected: FAIL because `csrfToken.ts` does not exist.

- [ ] **Step 3: Add CSRF helper**

Implementation pseudocode:

```pseudocode
readCookie(name):
  split document.cookie by semicolon
  find matching name
  decode value

isUnsafeMethod(method):
  normalize method or default GET
  return method in POST, PUT, PATCH, DELETE

csrfHeaderFor(method):
  if method is safe: return empty headers
  token = readCookie(XSRF-TOKEN)
  if token missing: return empty headers
  return { X-XSRF-TOKEN: token }
```

- [ ] **Step 4: Run tests to verify they pass**

Run:

```bash
cd fe && bun run test -- csrfToken.test.ts
```

Expected: PASS.

- [ ] **Step 5: Commit**

Run:

```bash
git add fe/src/services/csrfToken.ts fe/src/services/csrfToken.test.ts
git commit -m "Add frontend CSRF token helper"
```

---

### Task 7: Frontend Request Pipeline for Cookie Auth and CSRF

**Files:**
- Modify: `fe/src/services/request.ts`
- Modify: `fe/src/services/request.test.ts`
- Modify: `fe/src/modules/auth/authService.ts`

- [ ] **Step 1: Write failing tests for browser headers, credentials, CSRF init, refresh, and streaming POST**

Pseudocode:

```pseudocode
test requestJson includes browser client header and axios credentials:
  requestJson("/api/me/profile")
  expect axios request headers has X-Client-Type=browser
  expect axios request withCredentials is true
  expect no Authorization header

test unsafe request initializes csrf before POST:
  document.cookie has no XSRF-TOKEN
  requestJson("/api/auth/login", method POST)
  expect first axios call is GET /api/auth/csrf
  expect second axios call is POST /api/auth/login

test unsafe request sends csrf header when cookie exists:
  document.cookie has XSRF-TOKEN=csrf-1
  requestJson("/api/auth/logout", method POST)
  expect POST headers has X-XSRF-TOKEN=csrf-1

test 401 refresh uses cookie and no body refresh token:
  first protected request returns 401
  refresh call is POST /api/auth/refresh with browser header and csrf
  retry original request after refresh success

test streamJsonLines POST includes credentials and csrf:
  document.cookie has XSRF-TOKEN=csrf-stream
  streamJsonLines(...)
  expect fetch options.credentials is same-origin
  expect fetch headers has X-XSRF-TOKEN=csrf-stream
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
cd fe && bun run test -- request.test.ts
```

Expected: FAIL because request.ts still reads localStorage tokens and does not initialize CSRF.

- [ ] **Step 3: Update request.ts for browser cookie auth**

Implementation pseudocode:

```pseudocode
constants:
  CLIENT_TYPE_HEADER = X-Client-Type
  CLIENT_TYPE_VALUE = browser

buildHeaders(options, includeJson):
  existing Accept, Trace, Content-Type behavior
  set X-Client-Type=browser unless caller provided it
  do not set Authorization from localStorage
  merge csrfHeaderFor(method)

ensureCsrfFor(method):
  if method is safe: return
  if csrf cookie exists: return
  call raw axios GET /api/auth/csrf with browser header and credentials

axiosWithAuthRetry(requester, options, allowRefresh):
  ensure csrf before unsafe request
  execute request
  if response is AUTH_CSRF_INVALID:
    refresh csrf once and retry original request
  if response is 401:
    refreshAccessToken via POST /api/auth/refresh with csrf and credentials
    retry original request once

fetchWithAuthRetry:
  same flow for fetch, including credentials same-origin
```

Auth service notes:

- `logout()` no longer needs to read refresh token.
- `logout()` can send an empty object or no body; choose one and keep tests aligned.
- `login()` and `register()` continue using `requestJson` with `auth: false`; CSRF still applies because method is unsafe and browser mode is active.

- [ ] **Step 4: Run request tests to verify they pass**

Run:

```bash
cd fe && bun run test -- request.test.ts csrfToken.test.ts tokenStorage.test.ts
```

Expected: PASS.

- [ ] **Step 5: Commit**

Run:

```bash
git add fe/src/services/request.ts \
  fe/src/services/request.test.ts \
  fe/src/modules/auth/authService.ts
git commit -m "Use cookie auth in frontend requests"
```

---

### Task 8: Frontend Auth Store Session Recovery

**Files:**
- Modify: `fe/src/modules/auth/authStore.tsx`
- Test: `fe/src/app/globalError.test.tsx` only if existing auth-provider coverage lives there
- Test: add focused auth store test if the existing suite has a suitable pattern

- [ ] **Step 1: Write failing tests or update existing auth provider tests for cookie session recovery**

Pseudocode:

```pseudocode
test auth provider checks current user on startup:
  mock fetchCurrentUser resolves session
  render AuthProvider
  expect authenticated eventually true

test auth provider clears legacy tokens on startup:
  seed legacy localStorage token keys
  render AuthProvider
  expect legacy token keys removed

test failed current user leaves unauthenticated:
  mock fetchCurrentUser rejects 401 ApiRequestError
  render AuthProvider
  expect authenticated false after bootstrap
```

- [ ] **Step 2: Run targeted frontend tests to verify they fail**

Run the most specific existing auth-related test first. If no auth-provider test exists, add one and run:

```bash
cd fe && bun run test -- authStore.test.tsx
```

Expected: FAIL because startup still depends on `hasStoredToken()`.

- [ ] **Step 3: Update authStore startup flow**

Implementation pseudocode:

```pseudocode
AuthProvider startup:
  clearTokens()
  reload()
    success -> applySession
    failure -> clear in-memory session
    finally -> bootstrapping false

applySession:
  do not save token strings
  update sessionRef and React state only
```

Preserve existing permission version reload behavior.

- [ ] **Step 4: Run tests to verify they pass**

Run:

```bash
cd fe && bun run test -- authStore.test.tsx tokenStorage.test.ts request.test.ts
```

If `authStore.test.tsx` is not added, run the existing test file that covers auth provider behavior plus:

```bash
cd fe && bun run test -- tokenStorage.test.ts request.test.ts
```

Expected: PASS.

- [ ] **Step 5: Commit**

Run:

```bash
git add fe/src/modules/auth/authStore.tsx \
  fe/src/services/tokenStorage.ts \
  fe/src/services/tokenStorage.test.ts \
  fe/src/modules/auth/authStore.test.tsx
git commit -m "Recover browser sessions from cookies"
```

If `authStore.test.tsx` was not created, omit it from `git add`.

---

### Task 9: Cross-Cut Verification and Documentation Touchups

**Files:**
- Modify: `README.md` only if local cookie secure configuration or browser/non-browser auth mode needs a short operator note
- Modify: `fe/README.md` only if frontend request behavior documentation needs updating

- [ ] **Step 1: Run backend targeted verification**

Run:

```bash
cd be && ./mvnw -Dmaven.build.cache.enabled=false -Dtest='*Auth*Tests,*Security*Tests,RbacSecurityConfigCsrfTests,AuthControllerBrowserCookieTests' test
```

Expected: PASS with zero failures and zero errors.

- [ ] **Step 2: Run frontend targeted verification**

Run:

```bash
cd fe && bun run test -- request.test.ts csrfToken.test.ts tokenStorage.test.ts
```

Expected: PASS with zero failures.

- [ ] **Step 3: Run compile/type checks**

Run:

```bash
cd be && ./mvnw -Dmaven.build.cache.enabled=false -DskipTests compile
cd fe && bun run typecheck
```

Expected: both commands exit 0.

- [ ] **Step 4: Update brief docs if needed**

Documentation pseudocode:

```pseudocode
README auth note:
  Browser clients send X-Client-Type: browser and use HttpOnly cookies plus CSRF.
  Non-browser clients continue using Authorization: Bearer.
  Local HTTP development may set BROWSER_AUTH_COOKIE_SECURE=false.
```

Keep the documentation short. Do not duplicate the full design spec.

- [ ] **Step 5: Run final changed-area verification**

Run:

```bash
cd be && ./mvnw -Dmaven.build.cache.enabled=false -Dtest='*Auth*Tests,*Security*Tests,RbacSecurityConfigCsrfTests,AuthControllerBrowserCookieTests' test
cd fe && bun run test -- request.test.ts csrfToken.test.ts tokenStorage.test.ts
cd fe && bun run typecheck
```

Expected: all commands exit 0.

- [ ] **Step 6: Commit**

Run:

```bash
git add README.md fe/README.md
git commit -m "Document browser cookie auth mode"
```

If neither README changed, skip this commit and record in the task notes that no documentation update was needed beyond the existing spec.

---

## Self-Review Notes

- Spec coverage: Browser cookies are covered by Tasks 1 and 4; token resolution by Task 2; CSRF by Task 3 and Task 7; frontend localStorage removal by Tasks 5 and 8; non-browser compatibility by Tasks 2, 3, 4, and 7; follow-up optimization scope is preserved in the design spec and not implemented here.
- Placeholder scan: this plan uses concrete file paths, commands, expected outcomes, and pseudocode examples. It intentionally avoids large code blocks.
- Type consistency: Cookie names are consistently `CM_ACCESS_TOKEN`, `CM_REFRESH_TOKEN`, and `XSRF-TOKEN`; browser mode header is consistently `X-Client-Type: browser`; CSRF header is consistently `X-XSRF-TOKEN`.
