# Backend Validation Global Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:
> executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enable consistent Jakarta Bean Validation across WebFlux controllers, application services, and business
services.

**Architecture:** Keep validation rules on request DTOs and public service boundaries. Use Spring method validation via
`@Validated` on proxied beans, and convert all validation failures to the existing `ApiResponse` envelope through the
global exception handler.

**Tech Stack:** Spring Boot 3.5.15, Spring WebFlux, Jakarta Validation, Hibernate Validator, JUnit 5, AssertJ.

---

## File Structure

- Modify `be/src/main/java/top/egon/mario/config/GlobalExceptionHandler.java`
    - Return `ApiResponse.fail("VALIDATION_ERROR", ...)` for body binding, controller method validation, and service
      method validation.
    - Preserve trace-id logging style through `TraceContext.withMdc`.
- Modify `be/src/main/java/top/egon/mario/config/ValidationConfiguration.java`
    - Add a focused Spring configuration class for method validation behavior.
- Modify request DTOs under `be/src/main/java/top/egon/mario/rbac/dto/request` and
  `be/src/main/java/top/egon/mario/rag/dto/request`
    - Add missing object, collection, and nested validation rules where the current API semantics are clear.
- Modify controller classes under `be/src/main/java/top/egon/mario/rbac/web`, `be/src/main/java/top/egon/mario/rag/web`,
  and `be/src/main/java/top/egon/mario/web`
    - Add `@Validated` at class level.
    - Add `@Valid` to request bodies that currently rely only on service-side validation.
    - Add simple parameter constraints for ids and pagination parameters.
- Modify application and service boundary classes
    - `be/src/main/java/top/egon/mario/rbac/application/RbacAuthApplication.java`
    - public service interfaces in `be/src/main/java/top/egon/mario/rbac/service`
    - public service interfaces in `be/src/main/java/top/egon/mario/rag/service`
    - public service interfaces in `be/src/main/java/top/egon/mario/agent/service`
    - service implementations in `be/src/main/java/top/egon/mario/**/service/impl`
    - Add `@Validated` to Spring-managed boundary beans and constraints to interface method parameters.
- Add `be/src/test/java/top/egon/mario/config/GlobalExceptionHandlerValidationTests.java`
    - Verify WebFlux validation errors use the standard API envelope.
- Add `be/src/test/java/top/egon/mario/rbac/application/RbacAuthApplicationValidationTests.java`
    - Verify application service direct bean calls trigger method validation.
- Add `be/src/test/java/top/egon/mario/rbac/service/RbacUserServiceValidationTests.java`
    - Verify business service proxy calls trigger method validation.
- Update `be/src/test/java/top/egon/mario/web/ChatControllerTests.java`
    - Verify legacy chat request body validation for `message`.

## Task 1: Add Failing Validation Tests

**Files:**

- Create: `be/src/test/java/top/egon/mario/config/GlobalExceptionHandlerValidationTests.java`
- Create: `be/src/test/java/top/egon/mario/rbac/application/RbacAuthApplicationValidationTests.java`
- Create: `be/src/test/java/top/egon/mario/rbac/service/RbacUserServiceValidationTests.java`
- Modify: `be/src/test/java/top/egon/mario/web/ChatControllerTests.java`

- [ ] **Step 1: Add WebFlux exception response test**

```java
@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
@AutoConfigureWebTestClient
class GlobalExceptionHandlerValidationTests {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void requestBodyValidationUsesStandardApiResponse() {
        webTestClient.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"\",\"password\":\"\"}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.message").value(containsString("username"))
                .jsonPath("$.traceId").isNotEmpty();
    }
}
```

- [ ] **Step 2: Add application-layer method validation test**

```java
@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
class RbacAuthApplicationValidationTests {

    @Autowired
    private RbacAuthApplication authApplication;

    @Test
    void loginRejectsInvalidRequestBeforeBusinessLogic() {
        assertThatThrownBy(() -> authApplication.login(new LoginRequest("", ""), "127.0.0.1", "test"))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("username")
                .hasMessageContaining("password");
    }
}
```

- [ ] **Step 3: Add service-layer method validation test**

```java
@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
class RbacUserServiceValidationTests {

    @Autowired
    private RbacUserService userService;

    @Test
    void createUserRejectsInvalidRequestBeforeBusinessLogic() {
        CreateUserRequest request = new CreateUserRequest();

        assertThatThrownBy(() -> userService.createUser(request, 1L))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("username")
                .hasMessageContaining("initialPassword");
    }

    @Test
    void getUserRejectsMissingIdBeforeBusinessLogic() {
        assertThatThrownBy(() -> userService.getUser(null))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("userId");
    }
}
```

- [ ] **Step 4: Add legacy chat request validation test**

Add a test to `ChatControllerTests` that posts `{"message":""}` to `/api/chat/stream` and expects `400` with
`code=VALIDATION_ERROR`.

- [ ] **Step 5: Run focused tests and verify they fail**

Run:

```bash
./mvnw -q -Dspring.ai.dashscope.api-key=test-api-key -Dtest=GlobalExceptionHandlerValidationTests,RbacAuthApplicationValidationTests,RbacUserServiceValidationTests,ChatControllerTests test
```

Expected: failures proving validation is not yet globally wired.

## Task 2: Implement Global Exception Handling

**Files:**

- Modify: `be/src/main/java/top/egon/mario/config/GlobalExceptionHandler.java`

- [ ] **Step 1: Replace ad hoc validation map response**

Return `ResponseEntity.badRequest().body(ApiResponse.fail("VALIDATION_ERROR", message, traceId))` for
`WebExchangeBindException`.

- [ ] **Step 2: Add method validation handlers**

Handle:

```java
jakarta.validation.ConstraintViolationException
org.springframework.web.method.annotation.HandlerMethodValidationException
```

Both handlers must produce the same `VALIDATION_ERROR` envelope and log a warning with violation count.

- [ ] **Step 3: Keep existing business exception handlers**

Do not change `RbacException` or `RagException` response semantics.

- [ ] **Step 4: Run global exception focused test**

Run:

```bash
./mvnw -q -Dspring.ai.dashscope.api-key=test-api-key -Dtest=GlobalExceptionHandlerValidationTests test
```

Expected: PASS.

## Task 3: Enable Method Validation on App and Service Beans

**Files:**

- Create: `be/src/main/java/top/egon/mario/config/ValidationConfiguration.java`
- Modify: `be/src/main/java/top/egon/mario/rbac/application/RbacAuthApplication.java`
- Modify: service implementation classes under `be/src/main/java/top/egon/mario/**/service/impl`
- Modify: component service classes `ApiRuleMatcher`, `RoleHierarchyResolver`, `RbacPermissionVersionService`

- [ ] **Step 1: Add configuration**

Create a `@Configuration` class with a `MethodValidationPostProcessor` bean. Keep default proxy style unless tests prove
CGLIB is required.

- [ ] **Step 2: Add `@Validated` to Spring-managed application/service beans**

Add `org.springframework.validation.annotation.Validated` to application service and service implementation classes.

- [ ] **Step 3: Run focused method validation tests**

Run:

```bash
./mvnw -q -Dspring.ai.dashscope.api-key=test-api-key -Dtest=RbacAuthApplicationValidationTests,RbacUserServiceValidationTests test
```

Expected: tests still fail until interface constraints are added.

## Task 4: Add Boundary Constraints

**Files:**

- Modify public service interfaces and application service methods.
- Modify request DTOs where missing rules are clear.

- [ ] **Step 1: Add service interface constraints**

Use Jakarta imports:

```java
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
```

Apply:

- ids: `@NotNull`
- positive page sizes and numbers at controller level: `@Min(1)`
- request DTOs: `@Valid @NotNull`
- optional search text that must not be blank if required by behavior: `@NotBlank`
- collection replacement payloads: nullable collections only where existing code already treats null as empty; otherwise
  use `@NotNull` or `@NotEmpty`.

- [ ] **Step 2: Add DTO nested validation**

Add `@Valid` to nested records/classes:

- `RagChatRequest.retrievalOptions`
- `RagChatRequest.modelOptions`
- `PermissionRequest.menu`
- `PermissionRequest.button`
- `PermissionRequest.api`

Add clear constraints:

- `ChatRequest.message`: `@NotBlank`
- `ReplaceIdsRequest.ids`: `@NotNull`
- `StatusRequest.rbacStatus` and `permissionStatus` stay optional because one DTO serves multiple endpoints; service
  method constraints enforce the expected one.
- `ReplaceKnowledgeBaseUsersRequest.users`: `@NotNull`

- [ ] **Step 3: Add controller validation**

Add class-level `@Validated` to all controllers and `@Valid` to missing request bodies. Add `@Min(1)` to path ids and
page/size request params where appropriate.

- [ ] **Step 4: Run focused validation tests**

Run:

```bash
./mvnw -q -Dspring.ai.dashscope.api-key=test-api-key -Dtest=GlobalExceptionHandlerValidationTests,RbacAuthApplicationValidationTests,RbacUserServiceValidationTests,ChatControllerTests test
```

Expected: PASS.

## Task 5: Full Backend Verification

**Files:**

- No new files expected.

- [ ] **Step 1: Run backend tests**

Run:

```bash
./mvnw -q -Dspring.ai.dashscope.api-key=test-api-key test
```

Expected: PASS.

- [ ] **Step 2: Run compile if tests are too slow or fail for external services**

Run:

```bash
./mvnw -q -Dspring.ai.dashscope.api-key=test-api-key -DskipTests compile
```

Expected: PASS.

- [ ] **Step 3: Inspect backend diff**

Run:

```bash
git diff -- be docs/superpowers/plans/2026-06-14-backend-validation-global.md
```

Expected: only validation-related code and tests changed.

## Self-Review

- Spec coverage: Covers controller request bodies, controller simple params, app layer, service layer, nested DTOs,
  global exception response, and verification.
- Placeholder scan: No TBD/TODO placeholders.
- Type consistency: Uses Jakarta Validation packages because the project is Spring Boot 3.5.15.
