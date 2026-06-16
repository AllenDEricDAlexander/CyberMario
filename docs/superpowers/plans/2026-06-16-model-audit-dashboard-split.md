# Model Audit Dashboard Split Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:
> executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Split the model audit dashboard into focused summary and paged recent-call APIs for both self and global
scopes, while fixing the dashboard ranking chart rendering bug.

**Architecture:** Keep the existing self/global authorization boundary and reuse the existing dashboard permission
codes. Move self/global API resources from exact controller annotations to `AgentDashboardRbacResourceProvider` as ANT
child-route rules, then expose `summary` and `recent-calls` child endpoints under each scope. Backend summary data stays
read-only and filter-compatible; recent calls use Spring `Page` and the existing frontend `PageResult` contract.
Frontend requests summary and recent calls independently so the table can paginate without refetching all chart data.

**Tech Stack:** Spring Boot WebFlux controllers, Spring Data JPA Criteria queries, existing RBAC resource
provider/synchronizer, Java records, React 19, Ant Design 6, `@ant-design/charts` 2.6.7, Vitest.

---

## File Structure

**Backend files to modify**

- `be/src/main/java/top/egon/mario/agent/model/dto/response/ModelAuditDashboardSummaryResponse.java`
    - New summary-only payload for overview, trends, and dimension rankings.
    - Leave the old `ModelAuditDashboardResponse` file untouched in this change. It becomes unused after call sites move
      to the split response and can be removed in a separate cleanup if desired.
- `be/src/main/java/top/egon/mario/agent/model/service/ModelAuditDashboardService.java`
    - Replace `self(...)` and `global(...)` with `selfSummary(...)`, `globalSummary(...)`, `selfRecentCalls(...)`, and
      `globalRecentCalls(...)`.
- `be/src/main/java/top/egon/mario/agent/model/service/impl/ModelAuditDashboardServiceImpl.java`
    - Keep permission and query normalization in one service.
    - Return summary data separately from paged recent calls.
    - Remove the fixed `RECENT_LIMIT`.
- `be/src/main/java/top/egon/mario/agent/model/repository/ModelAuditDashboardRepository.java`
    - Add `recentCalls(ModelAuditDashboardQuery, Pageable)`.
    - Add a private count query helper for the paged recent-call list.
- `be/src/main/java/top/egon/mario/agent/model/web/ModelAuditDashboardController.java`
    - Expose four child endpoints: self/global summary and self/global recent-calls.
    - Keep `user-options` unchanged.
    - Remove self/global `@RbacApi` annotations from controller methods after moving those resources into the provider.
- `be/src/main/java/top/egon/mario/agent/model/service/resource/AgentDashboardRbacResourceProvider.java`
    - Declare the self/global dashboard API permissions as `GET` ANT rules for `/self/**` and `/global/**`.

**Backend tests to modify**

- `be/src/test/java/top/egon/mario/agent/model/service/ModelAuditDashboardServiceTests.java`
    - Verify summary and paged recent calls separately.
    - Verify self scope rejects foreign user filters.
    - Verify global scope still requires global authority.
- `be/src/test/java/top/egon/mario/agent/model/service/resource/AgentDashboardRbacResourceProviderTests.java`
    - Verify provider-owned self/global API resources and role preset behavior.
- `be/src/test/java/top/egon/mario/rbac/service/resource/AnnotationRbacResourceProviderTests.java`
    - Stop expecting self/global dashboard API resources from annotations.
    - Keep user-options annotation assertion.
- `be/src/test/java/top/egon/mario/rbac/service/bootstrap/RbacAdminBootstrapTests.java`
    - Update dashboard route patterns to child-route ANT patterns.
- `be/src/test/java/top/egon/mario/rbac/service/resource/RbacResourceSynchronizerTests.java`
    - Update dashboard route patterns to child-route ANT patterns.

**Frontend files to modify**

- `fe/src/modules/dashboard/dashboardTypes.ts`
    - Add `ModelAuditDashboardSummaryResponse`.
    - Add `ModelAuditRecentCallPage`.
    - Keep existing filter and row types.
- `fe/src/modules/dashboard/dashboardService.ts`
    - Replace `getModelAuditDashboard` with `getModelAuditDashboardSummary`.
    - Add `getModelAuditRecentCalls`.
    - Keep `getModelAuditUserOptions`.
- `fe/src/modules/dashboard/dashboardService.test.ts`
    - Verify split endpoint URLs and paged recent-call query parameters.
- `fe/src/modules/dashboard/DashboardPage.tsx`
    - Load summary and recent calls independently.
    - Add table pagination.
    - Replace ranking `<Bar>` charts with `<Column>` charts to match the installed chart library's numeric `yField`
      behavior.

**No database migration is needed.** This change reads the existing `ai_model_call_audit`, `sys_permission`, and
`sys_api` tables and changes synchronized resource metadata through existing provider/synchronizer flows.

---

### Task 1: Backend Contract Tests for Split Summary and Recent Calls

**Files:**

- Modify: `be/src/test/java/top/egon/mario/agent/model/service/ModelAuditDashboardServiceTests.java`
- Later implementation target: `be/src/main/java/top/egon/mario/agent/model/service/ModelAuditDashboardService.java`
- Later implementation target:
  `be/src/main/java/top/egon/mario/agent/model/dto/response/ModelAuditDashboardSummaryResponse.java`

- [ ] **Step 1: Replace service test imports**

Update the imports in `ModelAuditDashboardServiceTests.java` so the test file can refer to Spring pagination and the
summary response type:

```java
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import top.egon.mario.agent.model.dto.response.ModelAuditDashboardSummaryResponse;
import top.egon.mario.agent.model.dto.response.ModelAuditRecentCallResponse;
```

Remove this import after the new summary type is used:

```java
import top.egon.mario.agent.model.dto.response.ModelAuditDashboardResponse;
```

- [ ] **Step 2: Write failing tests for split service behavior**

Replace the existing `selfDashboardOnlyAggregatesCurrentUser`, `globalDashboardAllowsAdministratorUserFilter`, and
permission tests with these methods. Keep the existing helper methods below them.

```java
@Test
void selfSummaryOnlyAggregatesCurrentUserAndDoesNotExposeUserStats() {
    audit(mario.getId(), "qwen-plus", ModelScenario.AGENT_CHAT, ModelAuditStatus.SUCCESS, 12, 8, 20, 120);
    audit(mario.getId(), "qwen-plus", ModelScenario.AGENT_CHAT, ModelAuditStatus.FAILED, 3, 0, 3, 40);
    audit(luigi.getId(), "qwen-max", ModelScenario.RAG_CHAT, ModelAuditStatus.SUCCESS, 30, 10, 40, 90);

    ModelAuditDashboardSummaryResponse response = dashboardService.selfSummary(query(null), selfPrincipal(mario.getId()));

    assertThat(response.scope()).isEqualTo(ModelAuditDashboardScope.SELF);
    assertThat(response.overview().callCount()).isEqualTo(2L);
    assertThat(response.overview().successCount()).isEqualTo(1L);
    assertThat(response.overview().failedCount()).isEqualTo(1L);
    assertThat(response.overview().totalTokens()).isEqualTo(23L);
    assertThat(response.userStats()).isEmpty();
    assertThat(response.modelStats()).extracting(ModelAuditDimensionStatResponse::name).containsExactly("qwen-plus");
}

@Test
void selfRecentCallsArePagedAndScopedToCurrentUser() {
    audit(mario.getId(), "qwen-plus", ModelScenario.AGENT_CHAT, ModelAuditStatus.SUCCESS, 1, 1, 2, 20);
    audit(mario.getId(), "qwen-max", ModelScenario.RAG_CHAT, ModelAuditStatus.SUCCESS, 2, 2, 4, 40);
    audit(mario.getId(), "qwen-turbo", ModelScenario.BACKGROUND_TASK, ModelAuditStatus.FAILED, 3, 3, 6, 60);
    audit(luigi.getId(), "qwen-leaked", ModelScenario.RAG_CHAT, ModelAuditStatus.SUCCESS, 20, 20, 40, 90);

    Page<ModelAuditRecentCallResponse> page = dashboardService.selfRecentCalls(
            query(null),
            PageRequest.of(0, 2),
            selfPrincipal(mario.getId()));

    assertThat(page.getTotalElements()).isEqualTo(3L);
    assertThat(page.getContent()).hasSize(2);
    assertThat(page.getContent())
            .extracting(ModelAuditRecentCallResponse::model)
            .containsExactly("qwen-turbo", "qwen-max");
    assertThat(page.getContent())
            .extracting(ModelAuditRecentCallResponse::userId)
            .containsOnly(mario.getId());
}

@Test
void selfRecentCallsRejectForeignUserFilter() {
    assertThatThrownBy(() -> dashboardService.selfRecentCalls(
            query(luigi.getId()),
            PageRequest.of(0, 20),
            selfPrincipal(mario.getId())))
            .isInstanceOf(RbacException.class)
            .hasMessageContaining("MODEL_AUDIT_DASHBOARD_FORBIDDEN");
}

@Test
void globalSummaryRequiresAdministratorAuthority() {
    assertThatThrownBy(() -> dashboardService.globalSummary(query(null), selfPrincipal(mario.getId())))
            .isInstanceOf(RbacException.class)
            .hasMessageContaining("MODEL_AUDIT_DASHBOARD_FORBIDDEN");
}

@Test
void globalRecentCallsRequiresAdministratorAuthority() {
    assertThatThrownBy(() -> dashboardService.globalRecentCalls(
            query(null),
            PageRequest.of(0, 20),
            selfPrincipal(mario.getId())))
            .isInstanceOf(RbacException.class)
            .hasMessageContaining("MODEL_AUDIT_DASHBOARD_FORBIDDEN");
}

@Test
void globalSummaryAllowsAdministratorUserFilter() {
    audit(mario.getId(), "qwen-plus", ModelScenario.AGENT_CHAT, ModelAuditStatus.SUCCESS, 12, 8, 20, 120);
    audit(luigi.getId(), "qwen-max", ModelScenario.RAG_CHAT, ModelAuditStatus.SUCCESS, 30, 10, 40, 90);

    ModelAuditDashboardSummaryResponse response = dashboardService.globalSummary(query(luigi.getId()), adminPrincipal());

    assertThat(response.scope()).isEqualTo(ModelAuditDashboardScope.GLOBAL);
    assertThat(response.overview().callCount()).isEqualTo(1L);
    assertThat(response.overview().totalTokens()).isEqualTo(40L);
    assertThat(response.userStats()).hasSize(1);
    assertThat(response.userStats().getFirst().userId()).isEqualTo(luigi.getId());
    assertThat(response.userStats().getFirst().username()).isEqualTo("luigi");
}

@Test
void globalRecentCallsReturnRequestedUserPage() {
    audit(mario.getId(), "qwen-plus", ModelScenario.AGENT_CHAT, ModelAuditStatus.SUCCESS, 12, 8, 20, 120);
    audit(luigi.getId(), "qwen-max", ModelScenario.RAG_CHAT, ModelAuditStatus.SUCCESS, 30, 10, 40, 90);
    audit(luigi.getId(), "qwen-turbo", ModelScenario.RAG_SUMMARY, ModelAuditStatus.FAILED, 3, 1, 4, 35);

    Page<ModelAuditRecentCallResponse> page = dashboardService.globalRecentCalls(
            query(luigi.getId()),
            PageRequest.of(0, 10),
            adminPrincipal());

    assertThat(page.getTotalElements()).isEqualTo(2L);
    assertThat(page.getContent())
            .extracting(ModelAuditRecentCallResponse::model)
            .containsExactly("qwen-turbo", "qwen-max");
    assertThat(page.getContent())
            .extracting(ModelAuditRecentCallResponse::username)
            .containsOnly("luigi");
}
```

- [ ] **Step 3: Run the focused service test and verify it fails for missing APIs**

Run:

```bash
mvn -q -f be/pom.xml -Dmaven.build.cache.enabled=false -Dtest=ModelAuditDashboardServiceTests test
```

Expected: FAIL during test compilation with errors that include `cannot find symbol` for
`ModelAuditDashboardSummaryResponse`, `selfSummary`, `globalSummary`, `selfRecentCalls`, or `globalRecentCalls`.

- [ ] **Step 4: Commit the failing-test checkpoint only if the team allows red commits**

Default for this repo is to avoid committing red checkpoints unless explicitly asked. If red commits are allowed, run:

```bash
git add be/src/test/java/top/egon/mario/agent/model/service/ModelAuditDashboardServiceTests.java
git commit -m "test(agent): define model audit dashboard split contract"
```

Expected: commit succeeds and contains only the service test file.

---

### Task 2: Backend DTO and Service Interface Split

**Files:**

- Create: `be/src/main/java/top/egon/mario/agent/model/dto/response/ModelAuditDashboardSummaryResponse.java`
- Modify: `be/src/main/java/top/egon/mario/agent/model/service/ModelAuditDashboardService.java`
- Modify: `be/src/main/java/top/egon/mario/agent/model/service/impl/ModelAuditDashboardServiceImpl.java`
- Test: `be/src/test/java/top/egon/mario/agent/model/service/ModelAuditDashboardServiceTests.java`

- [ ] **Step 1: Create the summary response DTO**

Create `ModelAuditDashboardSummaryResponse.java` with this complete content:

```java
package top.egon.mario.agent.model.dto.response;

import top.egon.mario.agent.model.dto.enums.ModelAuditDashboardScope;

import java.time.Instant;
import java.util.List;

/**
 * Summary payload for model audit dashboard metrics and charts.
 */
public record ModelAuditDashboardSummaryResponse(
        ModelAuditDashboardScope scope,
        Instant startAt,
        Instant endAt,
        ModelAuditOverviewResponse overview,
        List<ModelAuditTrendPointResponse> tokenTrend,
        List<ModelAuditTrendPointResponse> callTrend,
        List<ModelAuditDimensionStatResponse> providerStats,
        List<ModelAuditDimensionStatResponse> modelStats,
        List<ModelAuditDimensionStatResponse> scenarioStats,
        List<ModelAuditDimensionStatResponse> statusStats,
        List<ModelAuditUserStatResponse> userStats
) {

    public ModelAuditDashboardSummaryResponse {
        tokenTrend = tokenTrend == null ? List.of() : List.copyOf(tokenTrend);
        callTrend = callTrend == null ? List.of() : List.copyOf(callTrend);
        providerStats = providerStats == null ? List.of() : List.copyOf(providerStats);
        modelStats = modelStats == null ? List.of() : List.copyOf(modelStats);
        scenarioStats = scenarioStats == null ? List.of() : List.copyOf(scenarioStats);
        statusStats = statusStats == null ? List.of() : List.copyOf(statusStats);
        userStats = userStats == null ? List.of() : List.copyOf(userStats);
    }

}
```

- [ ] **Step 2: Replace the service interface with split methods**

Update `ModelAuditDashboardService.java` imports:

```java
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import top.egon.mario.agent.model.dto.response.ModelAuditDashboardSummaryResponse;
import top.egon.mario.agent.model.dto.response.ModelAuditRecentCallResponse;
```

Replace the method section with:

```java
ModelAuditDashboardSummaryResponse selfSummary(ModelAuditDashboardQuery query, RbacPrincipal principal);

ModelAuditDashboardSummaryResponse globalSummary(ModelAuditDashboardQuery query, RbacPrincipal principal);

Page<ModelAuditRecentCallResponse> selfRecentCalls(ModelAuditDashboardQuery query, Pageable pageable,
                                                    RbacPrincipal principal);

Page<ModelAuditRecentCallResponse> globalRecentCalls(ModelAuditDashboardQuery query, Pageable pageable,
                                                      RbacPrincipal principal);

List<ModelAuditUserOptionResponse> userOptions(String keyword, int size, RbacPrincipal principal);
```

Remove the old import:

```java
import top.egon.mario.agent.model.dto.response.ModelAuditDashboardResponse;
```

- [ ] **Step 3: Update service implementation imports and constants**

In `ModelAuditDashboardServiceImpl.java`, add:

```java
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import top.egon.mario.agent.model.dto.response.ModelAuditDashboardSummaryResponse;
```

Remove:

```java
import top.egon.mario.agent.model.dto.response.ModelAuditDashboardResponse;
```

Delete this constant:

```java
private static final int RECENT_LIMIT = 20;
```

- [ ] **Step 4: Replace the public self/global methods**

Replace the current `self(...)` and `global(...)` methods in `ModelAuditDashboardServiceImpl.java` with:

```java
@Override
@Transactional(readOnly = true)
public ModelAuditDashboardSummaryResponse selfSummary(ModelAuditDashboardQuery query, RbacPrincipal principal) {
    ModelAuditDashboardQuery normalized = normalizeSelfQuery(query, principal);
    return summary(ModelAuditDashboardScope.SELF, normalized, false);
}

@Override
@Transactional(readOnly = true)
public ModelAuditDashboardSummaryResponse globalSummary(ModelAuditDashboardQuery query, RbacPrincipal principal) {
    ModelAuditDashboardQuery normalized = normalizeGlobalQuery(query, principal);
    return summary(ModelAuditDashboardScope.GLOBAL, normalized, true);
}

@Override
@Transactional(readOnly = true)
public Page<ModelAuditRecentCallResponse> selfRecentCalls(ModelAuditDashboardQuery query, Pageable pageable,
                                                          RbacPrincipal principal) {
    ModelAuditDashboardQuery normalized = normalizeSelfQuery(query, principal);
    return recentCalls(normalized, pageable);
}

@Override
@Transactional(readOnly = true)
public Page<ModelAuditRecentCallResponse> globalRecentCalls(ModelAuditDashboardQuery query, Pageable pageable,
                                                            RbacPrincipal principal) {
    ModelAuditDashboardQuery normalized = normalizeGlobalQuery(query, principal);
    return recentCalls(normalized, pageable);
}
```

- [ ] **Step 5: Add shared query normalization helpers**

Add these private methods below `userOptions(...)`:

```java
private ModelAuditDashboardQuery normalizeSelfQuery(ModelAuditDashboardQuery query, RbacPrincipal principal) {
    requirePermission(principal, SELF_PERMISSION);
    if (query != null && query.userId() != null && !Objects.equals(query.userId(), principal.userId())) {
        throw forbidden();
    }
    return normalize(query, principal.userId());
}

private ModelAuditDashboardQuery normalizeGlobalQuery(ModelAuditDashboardQuery query, RbacPrincipal principal) {
    requireGlobalPermission(principal);
    return normalize(query, query == null ? null : query.userId());
}
```

- [ ] **Step 6: Replace the old dashboard aggregation method with summary**

Replace the current `dashboard(...)` method with:

```java
private ModelAuditDashboardSummaryResponse summary(ModelAuditDashboardScope scope, ModelAuditDashboardQuery query,
                                                   boolean includeUserStats) {
    List<ModelAuditPo> audits = dashboardRepository.findAudits(query, AUDIT_LIMIT);
    return new ModelAuditDashboardSummaryResponse(
            scope,
            query.startAt(),
            query.endAt(),
            overview(audits),
            tokenTrend(audits),
            callTrend(audits),
            dimensionStats(query, "provider"),
            dimensionStats(query, "model"),
            dimensionStats(query, "scenario"),
            dimensionStats(query, "status"),
            includeUserStats ? userStats(query) : List.of()
    );
}
```

Add this paged recent-call method near `summary(...)`:

```java
private Page<ModelAuditRecentCallResponse> recentCalls(ModelAuditDashboardQuery query, Pageable pageable) {
    Page<ModelAuditPo> page = dashboardRepository.recentCalls(query, pageable);
    Map<Long, UserPo> usersById = usersById(page.getContent().stream()
            .map(ModelAuditPo::getUserId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet()));
    return new PageImpl<>(recentCalls(page.getContent(), usersById), pageable, page.getTotalElements());
}
```

- [ ] **Step 7: Run the focused service test and verify repository method is missing**

Run:

```bash
mvn -q -f be/pom.xml -Dmaven.build.cache.enabled=false -Dtest=ModelAuditDashboardServiceTests test
```

Expected: FAIL during compilation with `cannot find symbol` for `dashboardRepository.recentCalls`.

- [ ] **Step 8: Commit DTO and service contract when compilation reaches the repository failure**

Run:

```bash
git add be/src/main/java/top/egon/mario/agent/model/dto/response/ModelAuditDashboardSummaryResponse.java \
  be/src/main/java/top/egon/mario/agent/model/service/ModelAuditDashboardService.java \
  be/src/main/java/top/egon/mario/agent/model/service/impl/ModelAuditDashboardServiceImpl.java
git commit -m "refactor(agent): split model audit dashboard service contract"
```

Expected: commit succeeds if red intermediate commits are allowed. If not, leave files unstaged and continue to Task 3.

---

### Task 3: Backend Repository Pagination for Recent Calls

**Files:**

- Modify: `be/src/main/java/top/egon/mario/agent/model/repository/ModelAuditDashboardRepository.java`
- Test: `be/src/test/java/top/egon/mario/agent/model/service/ModelAuditDashboardServiceTests.java`

- [ ] **Step 1: Add Spring Data pagination imports**

Add these imports to `ModelAuditDashboardRepository.java`:

```java
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
```

- [ ] **Step 2: Add the paged recent-call query**

Add this method below `findAudits(...)`:

```java
public Page<ModelAuditPo> recentCalls(ModelAuditDashboardQuery query, Pageable pageable) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<ModelAuditPo> cq = cb.createQuery(ModelAuditPo.class);
    Root<ModelAuditPo> root = cq.from(ModelAuditPo.class);
    cq.select(root)
            .where(predicates(cb, root, query).toArray(new Predicate[0]))
            .orderBy(cb.desc(root.get("createdAt")), cb.desc(root.get("id")));
    List<ModelAuditPo> content = entityManager.createQuery(cq)
            .setFirstResult((int) pageable.getOffset())
            .setMaxResults(pageable.getPageSize())
            .getResultList();
    return new PageImpl<>(content, pageable, countAudits(query));
}
```

- [ ] **Step 3: Add the count helper**

Add this private method below `recentCalls(...)`:

```java
private long countAudits(ModelAuditDashboardQuery query) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<Long> cq = cb.createQuery(Long.class);
    Root<ModelAuditPo> root = cq.from(ModelAuditPo.class);
    cq.select(cb.count(root))
            .where(predicates(cb, root, query).toArray(new Predicate[0]));
    return entityManager.createQuery(cq).getSingleResult();
}
```

- [ ] **Step 4: Run the service tests**

Run:

```bash
mvn -q -f be/pom.xml -Dmaven.build.cache.enabled=false -Dtest=ModelAuditDashboardServiceTests test
```

Expected: PASS for all tests in `ModelAuditDashboardServiceTests`.

- [ ] **Step 5: Commit repository pagination**

Run:

```bash
git add be/src/main/java/top/egon/mario/agent/model/repository/ModelAuditDashboardRepository.java \
  be/src/test/java/top/egon/mario/agent/model/service/ModelAuditDashboardServiceTests.java
git commit -m "feat(agent): paginate model audit recent calls"
```

Expected: commit succeeds and contains repository pagination plus service tests.

---

### Task 4: Backend Controller Split Endpoints

**Files:**

- Modify: `be/src/main/java/top/egon/mario/agent/model/web/ModelAuditDashboardController.java`
- Test indirectly: `be/src/test/java/top/egon/mario/agent/model/service/ModelAuditDashboardServiceTests.java`

- [ ] **Step 1: Update controller imports**

Add:

```java
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import top.egon.mario.agent.model.dto.response.ModelAuditDashboardSummaryResponse;
import top.egon.mario.agent.model.dto.response.ModelAuditRecentCallResponse;
import top.egon.mario.common.api.PageResult;
```

Remove:

```java
import top.egon.mario.agent.model.dto.response.ModelAuditDashboardResponse;
```

- [ ] **Step 2: Replace self/global endpoints with child endpoints**

Replace the current `self(...)` and `global(...)` methods with these four methods:

```java
@GetMapping("/self/summary")
public Mono<ApiResponse<ModelAuditDashboardSummaryResponse>> selfSummary(@RequestParam(required = false) Instant startAt,
                                                                         @RequestParam(required = false) Instant endAt,
                                                                         @RequestParam(required = false) ModelProviderType provider,
                                                                         @RequestParam(required = false) String model,
                                                                         @RequestParam(required = false) ModelScenario scenario,
                                                                         @RequestParam(required = false) ModelAuditStatus status,
                                                                         @AuthenticationPrincipal RbacPrincipal principal) {
    ModelAuditDashboardQuery query = new ModelAuditDashboardQuery(startAt, endAt, null, provider, model, scenario, status);
    return blocking(() -> dashboardService.selfSummary(query, principal));
}

@GetMapping("/self/recent-calls")
public Mono<ApiResponse<PageResult<ModelAuditRecentCallResponse>>> selfRecentCalls(@RequestParam(required = false) Instant startAt,
                                                                                   @RequestParam(required = false) Instant endAt,
                                                                                   @RequestParam(required = false) ModelProviderType provider,
                                                                                   @RequestParam(required = false) String model,
                                                                                   @RequestParam(required = false) ModelScenario scenario,
                                                                                   @RequestParam(required = false) ModelAuditStatus status,
                                                                                   @RequestParam(defaultValue = "1") @Min(1) int page,
                                                                                   @RequestParam(defaultValue = "20") @Min(1) int size,
                                                                                   @AuthenticationPrincipal RbacPrincipal principal) {
    ModelAuditDashboardQuery query = new ModelAuditDashboardQuery(startAt, endAt, null, provider, model, scenario, status);
    return blocking(() -> pageResult(dashboardService.selfRecentCalls(query,
            PageRequest.of(Math.max(page - 1, 0), size, Sort.by("createdAt").descending().and(Sort.by("id").descending())),
            principal)));
}

@GetMapping("/global/summary")
public Mono<ApiResponse<ModelAuditDashboardSummaryResponse>> globalSummary(@RequestParam(required = false) Instant startAt,
                                                                           @RequestParam(required = false) Instant endAt,
                                                                           @RequestParam(required = false) Long userId,
                                                                           @RequestParam(required = false) ModelProviderType provider,
                                                                           @RequestParam(required = false) String model,
                                                                           @RequestParam(required = false) ModelScenario scenario,
                                                                           @RequestParam(required = false) ModelAuditStatus status,
                                                                           @AuthenticationPrincipal RbacPrincipal principal) {
    ModelAuditDashboardQuery query = new ModelAuditDashboardQuery(startAt, endAt, userId, provider, model, scenario, status);
    return blocking(() -> dashboardService.globalSummary(query, principal));
}

@GetMapping("/global/recent-calls")
public Mono<ApiResponse<PageResult<ModelAuditRecentCallResponse>>> globalRecentCalls(@RequestParam(required = false) Instant startAt,
                                                                                     @RequestParam(required = false) Instant endAt,
                                                                                     @RequestParam(required = false) Long userId,
                                                                                     @RequestParam(required = false) ModelProviderType provider,
                                                                                     @RequestParam(required = false) String model,
                                                                                     @RequestParam(required = false) ModelScenario scenario,
                                                                                     @RequestParam(required = false) ModelAuditStatus status,
                                                                                     @RequestParam(defaultValue = "1") @Min(1) int page,
                                                                                     @RequestParam(defaultValue = "20") @Min(1) int size,
                                                                                     @AuthenticationPrincipal RbacPrincipal principal) {
    ModelAuditDashboardQuery query = new ModelAuditDashboardQuery(startAt, endAt, userId, provider, model, scenario, status);
    return blocking(() -> pageResult(dashboardService.globalRecentCalls(query,
            PageRequest.of(Math.max(page - 1, 0), size, Sort.by("createdAt").descending().and(Sort.by("id").descending())),
            principal)));
}
```

Do not add `@RbacApi` to these four methods. Task 5 moves self/global dashboard API authorization to the resource
provider as child-route rules.

- [ ] **Step 3: Add a pageResult helper to the controller**

Add this method above `blocking(...)`:

```java
private <T> PageResult<T> pageResult(org.springframework.data.domain.Page<T> page) {
    return new PageResult<>(page.getContent(), page.getNumber() + 1, page.getSize(),
            page.getTotalElements(), page.getTotalPages());
}
```

- [ ] **Step 4: Run backend compilation for the changed controller**

Run:

```bash
mvn -q -f be/pom.xml -Dmaven.build.cache.enabled=false -DskipTests compile
```

Expected: PASS. If compilation fails in unrelated test sources, rerun with `compile` rather than `testCompile` and
report the unrelated source path.

- [ ] **Step 5: Commit controller split**

Run:

```bash
git add be/src/main/java/top/egon/mario/agent/model/web/ModelAuditDashboardController.java
git commit -m "feat(agent): split model audit dashboard endpoints"
```

Expected: commit succeeds and contains only the controller change.

---

### Task 5: RBAC Resource Ownership for Split Dashboard Routes

**Files:**

- Modify: `be/src/main/java/top/egon/mario/agent/model/service/resource/AgentDashboardRbacResourceProvider.java`
- Modify: `be/src/test/java/top/egon/mario/agent/model/service/resource/AgentDashboardRbacResourceProviderTests.java`
- Modify: `be/src/test/java/top/egon/mario/rbac/service/resource/AnnotationRbacResourceProviderTests.java`
- Modify: `be/src/test/java/top/egon/mario/rbac/service/bootstrap/RbacAdminBootstrapTests.java`
- Modify: `be/src/test/java/top/egon/mario/rbac/service/resource/RbacResourceSynchronizerTests.java`

- [ ] **Step 1: Add API imports to the dashboard provider if missing**

Ensure these imports exist in `AgentDashboardRbacResourceProvider.java`:

```java
import top.egon.mario.rbac.po.enums.ApiMatcherType;
import top.egon.mario.rbac.po.enums.ApiRiskLevel;
```

- [ ] **Step 2: Add self/global dashboard API resources**

Change `resources()` to include the new helper methods:

```java
@Override
public List<RbacResourceSeed> resources() {
    return List.of(dashboardMenu(), dashboardSelfApi(), dashboardGlobalApi(), arxivLogCollectionApi(), arxivLogApi());
}
```

Add these helper methods below `dashboardMenu()`:

```java
private RbacResourceSeed dashboardSelfApi() {
    return RbacResourceSeed.api(
            APP_CODE,
            APP_CODE,
            "api:agent:model-audit:dashboard:self",
            "AI 用量个人控制台",
            PermissionStatus.ENABLED,
            0,
            "Personal AI model usage dashboard APIs",
            new RbacApiSeed("GET", "/api/agent/model-audit/dashboard/self/**", ApiMatcherType.ANT, false, ApiRiskLevel.MEDIUM),
            RbacResourceSource.PROVIDER
    );
}

private RbacResourceSeed dashboardGlobalApi() {
    return RbacResourceSeed.api(
            APP_CODE,
            APP_CODE,
            "api:agent:model-audit:dashboard:global",
            "AI 用量全局控制台",
            PermissionStatus.ENABLED,
            0,
            "Global AI model usage dashboard APIs",
            new RbacApiSeed("GET", "/api/agent/model-audit/dashboard/global/**", ApiMatcherType.ANT, false, ApiRiskLevel.HIGH),
            RbacResourceSource.PROVIDER
    );
}
```

- [ ] **Step 3: Update provider tests for dashboard APIs**

In `AgentDashboardRbacResourceProviderTests.java`, replace `resourcesContainArxivLogApi()` with:

```java
@Test
void resourcesContainDashboardAndArxivApis() {
    AgentDashboardRbacResourceProvider provider = new AgentDashboardRbacResourceProvider();

    assertThat(provider.resources())
            .filteredOn(seed -> seed.type() == PermissionType.API)
            .extracting(seed -> seed.code())
            .contains("api:agent:model-audit:dashboard:self",
                    "api:agent:model-audit:dashboard:global",
                    "api:agent:arxiv-log:collection",
                    "api:agent:arxiv-log:*");
    assertThat(provider.resources())
            .filteredOn(seed -> "api:agent:model-audit:dashboard:self".equals(seed.code()))
            .singleElement()
            .satisfies(seed -> {
                assertThat(seed.api().httpMethod()).isEqualTo("GET");
                assertThat(seed.api().urlPattern()).isEqualTo("/api/agent/model-audit/dashboard/self/**");
                assertThat(seed.api().matcherType()).isEqualTo(top.egon.mario.rbac.po.enums.ApiMatcherType.ANT);
            });
    assertThat(provider.resources())
            .filteredOn(seed -> "api:agent:model-audit:dashboard:global".equals(seed.code()))
            .singleElement()
            .satisfies(seed -> {
                assertThat(seed.api().httpMethod()).isEqualTo("GET");
                assertThat(seed.api().urlPattern()).isEqualTo("/api/agent/model-audit/dashboard/global/**");
                assertThat(seed.api().matcherType()).isEqualTo(top.egon.mario.rbac.po.enums.ApiMatcherType.ANT);
            });
}
```

- [ ] **Step 4: Update annotation-provider test expectations**

In `AnnotationRbacResourceProviderTests.java`, replace `providersIncludeAgentDashboardApis()` with:

```java
@Test
void providersIncludeAgentDashboardUserOptionsApi() {
    assertThat(annotationProvider.providers())
            .filteredOn(provider -> provider.appCode().equals("agent"))
            .flatExtracting(RbacResourceProvider::resources)
            .anySatisfy(seed -> {
                assertThat(seed.code()).isEqualTo("api:agent:model-audit:dashboard:user-options");
                assertThat(seed.type()).isEqualTo(PermissionType.API);
                assertThat(seed.api().urlPattern()).isEqualTo("/api/agent/model-audit/dashboard/user-options");
                assertThat(seed.api().matcherType()).isEqualTo(ApiMatcherType.EXACT);
                assertThat(seed.api().riskLevel()).isEqualTo(ApiRiskLevel.HIGH);
            });
}
```

- [ ] **Step 5: Update dashboard API seed helper in RBAC tests**

In `RbacAdminBootstrapTests.java` and `RbacResourceSynchronizerTests.java`, update the local helper usage so self/global
are ANT child routes.

Use these seed calls where the tests currently pass exact `/self` and `/global` paths:

```java
dashboardApiSeed("api:agent:model-audit:dashboard:self",
        "/api/agent/model-audit/dashboard/self/**",
        ApiMatcherType.ANT),
dashboardApiSeed("api:agent:model-audit:dashboard:global",
        "/api/agent/model-audit/dashboard/global/**",
        ApiMatcherType.ANT),
dashboardApiSeed("api:agent:model-audit:dashboard:user-options",
        "/api/agent/model-audit/dashboard/user-options",
        ApiMatcherType.EXACT)
```

If the helper currently accepts only `(String code, String pattern)`, replace it with:

```java
private RbacResourceSeed dashboardApiSeed(String code, String pattern, ApiMatcherType matcherType) {
    return RbacResourceSeed.api(
            "agent",
            "agent",
            code,
            code,
            PermissionStatus.ENABLED,
            0,
            null,
            new RbacApiSeed("GET", pattern, matcherType, false, ApiRiskLevel.MEDIUM),
            RbacResourceSource.ANNOTATION
    );
}
```

Keep any existing test-local imports for `PermissionStatus`, `RbacApiSeed`, `RbacResourceSeed`, and
`RbacResourceSource`. Add this import if missing:

```java
import top.egon.mario.rbac.po.enums.ApiMatcherType;
```

- [ ] **Step 6: Update exact URL assertions**

In `RbacAdminBootstrapTests.java`, replace the global exact assertion with:

```java
assertThat(apiRepository.findById(dashboardGlobal.getId()).orElseThrow().getUrlPattern())
        .isEqualTo("/api/agent/model-audit/dashboard/global/**");
assertThat(apiRepository.findById(dashboardGlobal.getId()).orElseThrow().getMatcherType())
        .isEqualTo(ApiMatcherType.ANT);
```

- [ ] **Step 7: Run RBAC-focused tests**

Run:

```bash
mvn -q -f be/pom.xml -Dmaven.build.cache.enabled=false -Dtest=AgentDashboardRbacResourceProviderTests,AnnotationRbacResourceProviderTests,RbacAdminBootstrapTests,RbacResourceSynchronizerTests test
```

Expected: PASS. If unrelated test context failures occur, capture the first unrelated class name and rerun the smallest
failing test class alone.

- [ ] **Step 8: Commit RBAC route ownership**

Run:

```bash
git add be/src/main/java/top/egon/mario/agent/model/service/resource/AgentDashboardRbacResourceProvider.java \
  be/src/test/java/top/egon/mario/agent/model/service/resource/AgentDashboardRbacResourceProviderTests.java \
  be/src/test/java/top/egon/mario/rbac/service/resource/AnnotationRbacResourceProviderTests.java \
  be/src/test/java/top/egon/mario/rbac/service/bootstrap/RbacAdminBootstrapTests.java \
  be/src/test/java/top/egon/mario/rbac/service/resource/RbacResourceSynchronizerTests.java
git commit -m "refactor(rbac): cover split dashboard child routes"
```

Expected: commit succeeds and does not include unrelated RBAC files.

---

### Task 6: Frontend Service and Type Split

**Files:**

- Modify: `fe/src/modules/dashboard/dashboardTypes.ts`
- Modify: `fe/src/modules/dashboard/dashboardService.ts`
- Modify: `fe/src/modules/dashboard/dashboardService.test.ts`

- [ ] **Step 1: Update dashboard frontend types**

In `dashboardTypes.ts`, import `PageResult`:

```ts
import type {PageResult} from '../../types/api'
```

Rename the response type at the bottom from `ModelAuditDashboardResponse` to `ModelAuditDashboardSummaryResponse` and
remove `recentCalls` from that type:

```ts
export type ModelAuditDashboardSummaryResponse = {
    scope: ModelAuditDashboardScope
    startAt: string
    endAt: string
    overview: ModelAuditOverview
    tokenTrend: ModelAuditTrendPoint[]
    callTrend: ModelAuditTrendPoint[]
    providerStats: ModelAuditDimensionStat[]
    modelStats: ModelAuditDimensionStat[]
    scenarioStats: ModelAuditDimensionStat[]
    statusStats: ModelAuditDimensionStat[]
    userStats: ModelAuditUserStat[]
}

export type ModelAuditRecentCallPage = PageResult<ModelAuditRecentCall>
```

- [ ] **Step 2: Replace dashboard service functions**

In `dashboardService.ts`, replace the import line with:

```ts
import type {
    ModelAuditDashboardQuery,
    ModelAuditDashboardSummaryResponse,
    ModelAuditRecentCallPage,
    ModelAuditUserOption,
} from './dashboardTypes'
```

Add this helper above exported functions:

```ts
function dashboardBasePath(query: ModelAuditDashboardQuery) {
    return query.scope === 'GLOBAL'
        ? '/api/agent/model-audit/dashboard/global'
        : '/api/agent/model-audit/dashboard/self'
}
```

Replace `getModelAuditDashboard(...)` with:

```ts
export function getModelAuditDashboardSummary(query: ModelAuditDashboardQuery) {
    const search = buildSearchParams({
        startAt: query.startAt,
        endAt: query.endAt,
        userId: query.scope === 'GLOBAL' ? query.userId : undefined,
        provider: query.provider,
        model: query.model,
        scenario: query.scenario,
        status: query.status,
    })
    const path = `${dashboardBasePath(query)}/summary`
    return requestJson<ModelAuditDashboardSummaryResponse>(search ? `${path}?${search}` : path)
}
```

Add the recent-call page function:

```ts
export function getModelAuditRecentCalls(query: ModelAuditDashboardQuery, page = 1, size = 20) {
    const search = buildSearchParams({
        startAt: query.startAt,
        endAt: query.endAt,
        userId: query.scope === 'GLOBAL' ? query.userId : undefined,
        provider: query.provider,
        model: query.model,
        scenario: query.scenario,
        status: query.status,
        page,
        size,
    })
    return requestJson<ModelAuditRecentCallPage>(`${dashboardBasePath(query)}/recent-calls?${search}`)
}
```

Keep `getModelAuditUserOptions(...)` unchanged.

- [ ] **Step 3: Update frontend service tests**

Replace the import in `dashboardService.test.ts` with:

```ts
import {getModelAuditDashboardSummary, getModelAuditRecentCalls, getModelAuditUserOptions} from './dashboardService'
```

Replace the first test with:

```ts
test('builds encoded query strings for split dashboard summary APIs', async () => {
    const {requestJson} = await import('../../services/request')

    void getModelAuditDashboardSummary({
        scope: 'SELF',
        startAt: '2026-06-01T00:00:00Z',
        endAt: '2026-06-14T00:00:00Z',
        provider: 'DASHSCOPE',
        model: 'qwen3.6-max-preview',
        scenario: 'AGENT_CHAT',
        status: 'SUCCESS',
    })
    void getModelAuditDashboardSummary({
        scope: 'GLOBAL',
        userId: 7,
    })

    expect(requestJson).toHaveBeenNthCalledWith(1,
        '/api/agent/model-audit/dashboard/self/summary?startAt=2026-06-01T00%3A00%3A00Z&endAt=2026-06-14T00%3A00%3A00Z&provider=DASHSCOPE&model=qwen3.6-max-preview&scenario=AGENT_CHAT&status=SUCCESS')
    expect(requestJson).toHaveBeenNthCalledWith(2,
        '/api/agent/model-audit/dashboard/global/summary?userId=7')
})

test('builds encoded query strings for paged recent calls APIs', async () => {
    const {requestJson} = await import('../../services/request')

    void getModelAuditRecentCalls({
        scope: 'GLOBAL',
        userId: 7,
        model: 'qwen max',
    }, 3, 50)

    expect(requestJson).toHaveBeenCalledWith(
        '/api/agent/model-audit/dashboard/global/recent-calls?userId=7&model=qwen+max&page=3&size=50')
})
```

Keep the user option selector test unchanged.

- [ ] **Step 4: Run frontend service tests**

Run:

```bash
npm --prefix fe test -- dashboardService.test.ts
```

Expected: PASS for `dashboardService.test.ts`.

- [ ] **Step 5: Commit frontend service contract**

Run:

```bash
git add fe/src/modules/dashboard/dashboardTypes.ts \
  fe/src/modules/dashboard/dashboardService.ts \
  fe/src/modules/dashboard/dashboardService.test.ts
git commit -m "feat(fe): split model audit dashboard service calls"
```

Expected: commit succeeds and contains only dashboard type/service/test files.

---

### Task 7: Frontend Dashboard Page Pagination and Chart Fix

**Files:**

- Modify: `fe/src/modules/dashboard/DashboardPage.tsx`
- Test: `fe/src/modules/dashboard/dashboardService.test.ts`

- [ ] **Step 1: Update imports**

In `DashboardPage.tsx`, replace:

```ts
import {Bar, Column, Line, Pie} from '@ant-design/charts'
```

with:

```ts
import {Column, Line, Pie} from '@ant-design/charts'
```

Replace the service import with:

```ts
import {getModelAuditDashboardSummary, getModelAuditRecentCalls, getModelAuditUserOptions} from './dashboardService'
```

Replace `ModelAuditDashboardResponse` in the type import list with:

```ts
ModelAuditDashboardSummaryResponse,
```

- [ ] **Step 2: Replace state for summary and recent calls**

Replace:

```ts
const [loading, setLoading] = useState(false)
const [userLoading, setUserLoading] = useState(false)
const [data, setData] = useState<ModelAuditDashboardResponse>()
```

with:

```ts
const [summaryLoading, setSummaryLoading] = useState(false)
const [recentLoading, setRecentLoading] = useState(false)
const [userLoading, setUserLoading] = useState(false)
const [summary, setSummary] = useState<ModelAuditDashboardSummaryResponse>()
const [recentCalls, setRecentCalls] = useState<ModelAuditRecentCall[]>([])
const [recentPage, setRecentPage] = useState(1)
const [recentSize, setRecentSize] = useState(20)
const [recentTotal, setRecentTotal] = useState(0)
```

- [ ] **Step 3: Add a query builder function**

Add this function above `loadSummary()`:

```ts
function buildQuery(): ModelAuditDashboardQuery {
    return {
        scope: effectiveScope,
        startAt: range?.[0]?.toISOString(),
        endAt: range?.[1]?.toISOString(),
        userId: effectiveScope === 'GLOBAL' ? userId : undefined,
        provider: provider as ModelAuditDashboardQuery['provider'],
        model,
        scenario: scenario as ModelAuditDashboardQuery['scenario'],
        status: status as ModelAuditDashboardQuery['status'],
    }
}
```

- [ ] **Step 4: Replace the old load function with split loaders**

Replace the whole `load()` function with these functions:

```ts
async function loadSummary() {
    setSummaryLoading(true)
    try {
        setSummary(await getModelAuditDashboardSummary(buildQuery()))
    } catch (error) {
        message.error((error as Error).message)
    } finally {
        setSummaryLoading(false)
    }
}

async function loadRecent(nextPage = recentPage, nextSize = recentSize) {
    setRecentLoading(true)
    try {
        const page = await getModelAuditRecentCalls(buildQuery(), nextPage, nextSize)
        setRecentCalls(page.records)
        setRecentPage(page.page)
        setRecentSize(page.size)
        setRecentTotal(page.total)
    } catch (error) {
        message.error((error as Error).message)
    } finally {
        setRecentLoading(false)
    }
}

async function loadDashboard() {
    await Promise.all([
        loadSummary(),
        loadRecent(1, recentSize),
    ])
}
```

- [ ] **Step 5: Update effect and buttons**

Replace:

```ts
void load()
```

inside the `useEffect` with:

```ts
void loadDashboard()
```

Replace the refresh button:

```tsx
<Button icon={<ReloadOutlined/>} loading={loading} onClick={() => void load()} type="primary">
```

with:

```tsx
<Button icon={<ReloadOutlined/>} loading={summaryLoading || recentLoading} onClick={() => void loadDashboard()} type="primary">
```

Replace the query button:

```tsx
<Button onClick={() => void load()}>查询</Button>
```

with:

```tsx
<Button onClick={() => void loadDashboard()}>查询</Button>
```

- [ ] **Step 6: Replace summary data references**

In `DashboardPage.tsx`, replace all `data?.overview`, `data?.tokenTrend`, `data?.callTrend`, `data?.modelStats`,
`data?.scenarioStats`, `data?.statusStats`, and `data?.userStats` references with `summary?.overview`,
`summary?.tokenTrend`, `summary?.callTrend`, `summary?.modelStats`, `summary?.scenarioStats`, `summary?.statusStats`,
and `summary?.userStats`.

For metric cards, replace `loading={loading}` with:

```tsx
loading={summaryLoading}
```

For chart cards, keep the existing empty checks but use `summary`.

- [ ] **Step 7: Replace ranking Bar charts with Column charts**

Replace the model ranking chart:

```tsx
<Bar
    data={data?.modelStats ?? []}
    height={320}
    theme={chartTheme}
    xField="totalTokens"
    yField="name"
/>
```

with:

```tsx
<Column
    data={summary?.modelStats ?? []}
    height={320}
    theme={chartTheme}
    xField="name"
    yField="totalTokens"
/>
```

Replace the user ranking chart:

```tsx
<Bar
    data={(data?.userStats ?? []).map((item) => ({...item, name: userStatLabel(item)}))}
    height={320}
    theme={chartTheme}
    xField="totalTokens"
    yField="name"
/>
```

with:

```tsx
<Column
    data={(summary?.userStats ?? []).map((item) => ({...item, name: userStatLabel(item)}))}
    height={320}
    theme={chartTheme}
    xField="name"
    yField="totalTokens"
/>
```

- [ ] **Step 8: Update model options**

Replace:

```tsx
options={modelOptions(data)}
```

with:

```tsx
options={modelOptions(summary)}
```

Update the helper signature:

```ts
function modelOptions(data?: ModelAuditDashboardSummaryResponse) {
    return (data?.modelStats ?? []).map((item) => ({label: item.name, value: item.name}))
}
```

- [ ] **Step 9: Add table pagination**

Replace the table block:

```tsx
<Table<ModelAuditRecentCall>
    columns={recentColumns}
    dataSource={data?.recentCalls ?? []}
    loading={loading}
    pagination={false}
    rowKey="id"
    scroll={{x: 1350}}
/>
```

with:

```tsx
<Table<ModelAuditRecentCall>
    columns={recentColumns}
    dataSource={recentCalls}
    loading={recentLoading}
    pagination={{
        current: recentPage,
        pageSize: recentSize,
        total: recentTotal,
        showSizeChanger: true,
        onChange: (page, size) => void loadRecent(page, size),
    }}
    rowKey="id"
    scroll={{x: 1350}}
/>
```

- [ ] **Step 10: Run frontend tests and typecheck**

Run:

```bash
npm --prefix fe test -- dashboardService.test.ts
npm --prefix fe run typecheck
```

Expected: both commands PASS.

- [ ] **Step 11: Commit dashboard page update**

Run:

```bash
git add fe/src/modules/dashboard/DashboardPage.tsx
git commit -m "feat(fe): paginate model audit recent calls"
```

Expected: commit succeeds and contains only the page update.

---

### Task 8: Final Verification and Cleanup

**Files:**

- Verify: backend model audit, RBAC, and frontend dashboard files changed in prior tasks.

- [ ] **Step 1: Run backend model audit service tests**

Run:

```bash
mvn -q -f be/pom.xml -Dmaven.build.cache.enabled=false -Dtest=ModelAuditDashboardServiceTests test
```

Expected: PASS.

- [ ] **Step 2: Run RBAC resource tests**

Run:

```bash
mvn -q -f be/pom.xml -Dmaven.build.cache.enabled=false -Dtest=AgentDashboardRbacResourceProviderTests,AnnotationRbacResourceProviderTests,RbacAdminBootstrapTests,RbacResourceSynchronizerTests test
```

Expected: PASS. If a failure is unrelated to dashboard resources, include the failing class and first error line in the
handoff.

- [ ] **Step 3: Run frontend dashboard tests**

Run:

```bash
npm --prefix fe test -- dashboardService.test.ts
```

Expected: PASS.

- [ ] **Step 4: Run frontend typecheck**

Run:

```bash
npm --prefix fe run typecheck
```

Expected: PASS.

- [ ] **Step 5: Run whitespace check**

Run:

```bash
git diff --check
```

Expected: no output.

- [ ] **Step 6: Inspect final owned-file diff**

Run:

```bash
git diff -- be/src/main/java/top/egon/mario/agent/model/dto/response/ModelAuditDashboardSummaryResponse.java \
  be/src/main/java/top/egon/mario/agent/model/service/ModelAuditDashboardService.java \
  be/src/main/java/top/egon/mario/agent/model/service/impl/ModelAuditDashboardServiceImpl.java \
  be/src/main/java/top/egon/mario/agent/model/repository/ModelAuditDashboardRepository.java \
  be/src/main/java/top/egon/mario/agent/model/web/ModelAuditDashboardController.java \
  be/src/main/java/top/egon/mario/agent/model/service/resource/AgentDashboardRbacResourceProvider.java \
  fe/src/modules/dashboard/dashboardTypes.ts \
  fe/src/modules/dashboard/dashboardService.ts \
  fe/src/modules/dashboard/DashboardPage.tsx
```

Expected: diff only contains dashboard split, recent-call pagination, RBAC route ownership, and chart changes.

- [ ] **Step 7: Commit final verification note only if project convention requires it**

This repo usually keeps verification in the final response rather than a separate commit. If a verification-only commit
is requested, run:

```bash
git status --short
```

Expected: only owned dashboard/RBAC files are modified.

---

## Self-Review

**Spec coverage:** The plan covers both self and global dashboard split endpoints, table pagination, ranking chart
rendering, existing RBAC self/global boundaries, frontend service URL changes, and targeted backend/frontend tests.

**Placeholder scan:** This plan does not contain open placeholders, missing method names, or deferred implementation
sections.

**Type consistency:** Backend uses `ModelAuditDashboardSummaryResponse` for summary endpoints and
`Page<ModelAuditRecentCallResponse>` for service paging. Frontend uses `ModelAuditDashboardSummaryResponse` for
charts/metrics and `ModelAuditRecentCallPage` for the table. API paths are consistently `/summary` and `/recent-calls`
under `/self` and `/global`.
