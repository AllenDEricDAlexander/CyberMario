# Family AI Nutrition Recovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore the approved Family AI Nutrition MVP as eleven complete vertical workflows so that real family data can move from administration and recipes through AI review, confirmation, shopping, budget, nutrition records, reports, and live frontend pages.

**Architecture:** Keep the existing modular-monolith package, V31 schema, WebFlux/JPA boundary, typed frontend service, and tested domain foundations. Complete missing handoffs with focused domain services for recommendation context, AI recipe materialization, deterministic meal validation, unit conversion, and home aggregation; every workflow remains family-scoped and backend-authoritative.

**Tech Stack:** Java 21, Spring Boot 3.5, WebFlux, Spring Data JPA, Flyway/PostgreSQL, JUnit 5, AssertJ, React 19, TypeScript 6, Vite 8, Ant Design 6, Vitest 4, Bun 1.3.

## Global Constraints

- Treat `docs/superpowers/specs/2026-06-30-family-ai-nutrition-mvp-design.md` and `docs/superpowers/specs/2026-07-13-family-ai-nutrition-recovery-design.md` as the requirements source of truth.
- Keep `Family` as the strong data boundary on every repository lookup and write.
- Platform RBAC controls entry only; scoped roles and explicit grants control nutrition data. Platform administrators receive no implicit family access.
- AI may propose content but may not publish, decide safety, or supply final nutrition calculations.
- Every published meal-plan item must reference a visible persisted recipe with calculable required ingredients.
- Dish-level confirmation items and their serving counts are the source for summary, shopping, budget, and records.
- Keep all existing compatible routes under `/api/nutrition`; extend DTOs instead of returning JPA entities.
- Do not edit, rename, delete, reformat, or replace `be/src/main/resources/db/migration/V31__create_nutrition_mvp_schema.sql` or any existing migration.
- V31 is sufficient for the planned work. If implementation proves a schema gap, stop that task, recheck the migration sequence, and add exactly one next-version backward-safe migration for that database change.
- Do not introduce a rule engine, state-machine framework, event bus, microservice, external queue, external nutrition-data API, or runtime frontend state-management dependency.
- The only planned dependency addition is test-only: Testing Library plus jsdom in Task 9, required to replace static markup assertions with user-interaction tests.
- Preserve unrelated worktree and index changes. Stage and commit only the paths listed by the active task.
- Use TDD for every task: add a focused failing test, run it and observe the expected failure, implement the smallest workflow, rerun focused tests, review the diff, then commit once.
- Do not start backend/frontend servers, open a browser, or perform manual runtime testing.
- Use stable `NUTRITION_*` errors and avoid logging health snapshots, model prompts, confirmation notes, or imported row bodies.

---

## Scope Check

The approved recovery is one connected product flow, not eleven independent products. Keep one master plan because later serving, shopping, budget, and record calculations consume contracts established earlier. The eleven tasks below match the eleven approved delivery slices, each ends with a user-visible backend or frontend workflow, and each produces exactly one commit.

The old plan at `docs/superpowers/plans/2026-06-30-family-ai-nutrition-mvp.md` is historical evidence only. Do not execute its technical-layer tasks or recreate V31.

Before Task 1 and before every commit, run:

```bash
git status --short --branch
git diff --check
```

Expected: existing user-owned changes may remain, but the active task's diff has no whitespace errors and no unrelated nutrition or Clocktower files.

## File Structure

### Existing backend seams to retain and extend

- `be/src/main/java/top/egon/mario/nutrition/service/access/NutritionAccessService.java` - all scoped read/write/manage and profile authorization contracts.
- `be/src/main/java/top/egon/mario/nutrition/service/ClanFamilyService.java` - clan/family settings, associations, roles, and grants.
- `be/src/main/java/top/egon/mario/nutrition/service/MemberHealthService.java` - member lifecycle, binding, guardians, and health profiles.
- `be/src/main/java/top/egon/mario/nutrition/service/RecipeService.java` - platform/family recipe and standard-food operations.
- `be/src/main/java/top/egon/mario/nutrition/service/importer/NutritionCsvImportTemplate.java` - the shared parse/validate/preview/confirm/write lifecycle.
- `be/src/main/java/top/egon/mario/nutrition/service/ai/NutritionAiServiceImpl.java` - job creation and orchestration only after focused services are extracted.
- `be/src/main/java/top/egon/mario/nutrition/service/MealPlanService.java` - meal editing and state transitions.
- `be/src/main/java/top/egon/mario/nutrition/service/MealConfirmationService.java` - dish-level confirmation and proxy authorization.
- `be/src/main/java/top/egon/mario/nutrition/service/ShoppingListService.java` - preview/final shopping lists, item state, and prices.
- `be/src/main/java/top/egon/mario/nutrition/service/BudgetService.java` - rules and cost-based summaries.
- `be/src/main/java/top/egon/mario/nutrition/service/NutritionRecordService.java` - idempotent records, corrections, extra foods, and reports.
- `be/src/main/java/top/egon/mario/nutrition/web/*.java` - thin WebFlux endpoints using `ReactiveNutritionSupport`.

### Focused backend files to create

- `be/src/main/java/top/egon/mario/nutrition/service/HealthTagService.java` - platform health-tag dictionaries.
- `be/src/main/java/top/egon/mario/nutrition/service/calculation/NutritionUnitConversionService.java` - mass and explicit unit-to-gram conversion.
- `be/src/main/java/top/egon/mario/nutrition/service/ai/NutritionRecommendationContext.java` - immutable typed model input.
- `be/src/main/java/top/egon/mario/nutrition/service/ai/NutritionRecommendationContextService.java` - family-scoped context assembly.
- `be/src/main/java/top/egon/mario/nutrition/service/ai/NutritionAiIngredientDraft.java` - structured generated ingredient.
- `be/src/main/java/top/egon/mario/nutrition/service/ai/NutritionAiRecipeMaterializationService.java` - existing recipe resolution and generated recipe persistence.
- `be/src/main/java/top/egon/mario/nutrition/service/ai/MaterializedNutritionRecipe.java` - real recipe output consumed by meal creation.
- `be/src/main/java/top/egon/mario/nutrition/service/NutritionMealValidationService.java` - nutrition, risk, cost snapshot, and publish-gate calculation.
- `be/src/main/java/top/egon/mario/nutrition/service/NutritionHomeQueryService.java` - read-only live home aggregation.
- `be/src/main/java/top/egon/mario/nutrition/web/HealthTagController.java` - platform tag CRUD.
- `be/src/main/java/top/egon/mario/nutrition/web/NutritionHomeController.java` - family overview query.
- `be/src/main/java/top/egon/mario/nutrition/service/importer/AbstractHealthTagCsvImporter.java` and seven concrete importer classes - the seven missing import types without duplicating lifecycle code.

### Backend test structure

- Extend the existing `be/src/test/java/top/egon/mario/nutrition/**/*Tests.java` tests beside their owning service.
- Create `be/src/test/java/top/egon/mario/nutrition/web/NutritionAdministrationControllerTests.java` for real controller wiring of settings, bindings, and grants.
- Create `be/src/test/java/top/egon/mario/nutrition/ai/NutritionRecommendationContextServiceTests.java`.
- Create `be/src/test/java/top/egon/mario/nutrition/ai/NutritionAiRecipeMaterializationServiceTests.java`.
- Create `be/src/test/java/top/egon/mario/nutrition/NutritionMealValidationServiceTests.java`.
- Create `be/src/test/java/top/egon/mario/nutrition/NutritionHomeQueryServiceTests.java`.
- Create `be/src/test/java/top/egon/mario/nutrition/NutritionVerticalFlowTests.java` for the mandatory end-to-end service workflows.

### Frontend structure

- Extend `fe/src/modules/nutrition/nutritionTypes.ts` and `nutritionService.ts` as the only typed HTTP boundary.
- Create `fe/src/modules/nutrition/useNutritionFamilySelection.ts` for accessible-family loading and current selection.
- Create `fe/src/modules/nutrition/components/NutritionAsyncState.tsx` for loading, error, empty, and permission-denied presentation.
- Convert the existing nutrition pages in place; do not add parallel replacement pages.
- Delete `fe/src/modules/nutrition/nutritionPageData.ts` only after Task 10 proves that no production import remains.
- Add `fe/src/modules/nutrition/test/nutritionTestSetup.ts` for jsdom/Ant Design test shims.
- Add `fe/src/modules/nutrition/test/renderNutritionPage.tsx` to render pages inside Ant Design `App` and React Router context.

## Cross-Task Contracts

The following signatures are fixed for later tasks. If implementation needs a signature change, update this plan and all consumers before code is committed.

```java
public interface NutritionAccessService {
    boolean canReadFamily(Long userId, Long familyId);
    boolean canReadFamilyScope(Long userId, Long familyId, NutritionGrantDataScope scope);
    boolean canWriteFamilyScope(Long userId, Long familyId, NutritionGrantDataScope scope);
    boolean canManageFamilyScope(Long userId, Long familyId, NutritionGrantDataScope scope);
    void requireReadFamily(Long userId, Long familyId);
    void requireWriteFamilyScope(Long userId, Long familyId, NutritionGrantDataScope scope);
    void requireManageFamily(Long userId, Long familyId);
    void requireManageFamilyScope(Long userId, Long familyId, NutritionGrantDataScope scope);
    void requireCookFamily(Long userId, Long familyId);
    void requireConfirmMemberProfile(Long userId, Long familyId, Long memberProfileId);
    void requireWriteMemberProfile(Long userId, Long familyId, Long memberProfileId);
}
```

```java
public record MealConfirmationItemRequest(
        @NotNull Long mealPlanItemId,
        @NotNull Boolean selected,
        @DecimalMin("0.001") BigDecimal servingCount,
        Boolean riskAcknowledged,
        @Size(max = 512) String adjustmentNote
) {}
```

```java
public record MealPlanItemRequest(
        Long id,
        @NotNull NutritionMealType mealType,
        @NotNull Long recipeId,
        @DecimalMin("0.001") BigDecimal servingCount,
        @Min(0) Integer sortOrder
) {}
```

```java
public record MealValidationResult(
        NutritionTotals totals,
        BigDecimal estimatedCost,
        List<RuleCheckResult> risks,
        boolean publishable
) {}
```

```typescript
export type NutritionLoadState = 'idle' | 'loading' | 'ready' | 'empty' | 'forbidden' | 'error'
```

## Spec Coverage Map

| Recovery design area | Owning implementation task |
| --- | --- |
| organization, roles, grants, family/member/health administration | Task 1 |
| standard foods, tag dictionaries, public recipes, all imports | Task 2 |
| recipe detail, mapping, steps, conversion, deterministic nutrition | Task 3 |
| AI job lifecycle, full context, materialization, rules | Task 4 |
| cook review, audit, versioning, state and publish gates | Task 5 |
| per-dish confirmation, proxy access, cutoff, exact summary | Task 6 |
| serving-derived shopping, prices, budget rules and cost semantics | Task 7 |
| item-derived records, effective corrections, reports and home overview | Task 8 |
| live administration frontend and interaction-test foundation | Task 9 |
| live AI-to-record frontend and removal of fixture production data | Task 10 |
| mandatory vertical acceptance, full verification, documentation | Task 11 |

---

## Task 1: Family Settings, Scoped Roles, Grants, Member Binding, and Guardians

**Files:**

- Create: `be/src/main/java/top/egon/mario/nutrition/dto/request/UpdateFamilySettingsRequest.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/dto/request/CreateScopedRoleBindingRequest.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/dto/request/CreateDataGrantRequest.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/dto/request/UpdateDataGrantRequest.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/dto/request/UpdateMemberProfileRequest.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/dto/request/BindMemberUserRequest.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/dto/request/AssignProfileGuardianRequest.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/dto/response/ScopedRoleBindingResponse.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/dto/response/DataGrantResponse.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/dto/response/ClanFamilyRelationResponse.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/service/access/NutritionAccessService.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/service/access/NutritionAccessServiceImpl.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/service/ClanFamilyService.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/service/MemberHealthService.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/repository/NutritionDataGrantRepository.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/repository/NutritionScopedRoleBindingRepository.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/repository/NutritionClanFamilyRepository.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/repository/NutritionMemberProfileRepository.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/web/ClanFamilyController.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/web/MemberHealthController.java`
- Test: `be/src/test/java/top/egon/mario/nutrition/access/NutritionAccessServiceTests.java`
- Test: `be/src/test/java/top/egon/mario/nutrition/ClanFamilyServiceTests.java`
- Test: `be/src/test/java/top/egon/mario/nutrition/MemberHealthServiceTests.java`
- Create test: `be/src/test/java/top/egon/mario/nutrition/web/NutritionAdministrationControllerTests.java`

**Interfaces:**

- Consumes: existing family, clan-family, member, scoped-role, grant POs and `RbacPrincipal.userId()`.
- Produces: the `NutritionAccessService` contract above; CRUD routes for `/families/{familyId}/settings`, `/role-bindings`, `/data-grants`, `/members`, and clan-family relationships; profile-owner/guardian authorization used by Tasks 6 and 8.

- [ ] **Step 1: Add failing access and administration tests**

Add focused tests that distinguish read, write, and manage grants and reject implicit access:

```java
@Test
void writeGrantCanEditScopedDataButCannotManageFamily() {
    NutritionDataGrantPo grant = activeUserGrant(FAMILY_ID, USER_ID,
            NutritionGrantDataScope.HEALTH_PROFILE, NutritionGrantPermissionLevel.WRITE);
    dataGrantRepository.save(grant);

    assertThat(accessService.canWriteFamilyScope(USER_ID, FAMILY_ID,
            NutritionGrantDataScope.HEALTH_PROFILE)).isTrue();
    assertThat(accessService.canManageFamilyScope(USER_ID, FAMILY_ID,
            NutritionGrantDataScope.HEALTH_PROFILE)).isFalse();
    assertThatThrownBy(() -> accessService.requireManageFamily(USER_ID, FAMILY_ID))
            .isInstanceOf(NutritionException.class)
            .extracting("code").isEqualTo("NUTRITION_FORBIDDEN");
}

@Test
void clanAssociationWithoutGrantDoesNotExposeFamilyData() {
    associateClanAndFamily(CLAN_ID, FAMILY_ID);
    bindClanMember(USER_ID, CLAN_ID);

    assertThat(accessService.canReadFamily(USER_ID, FAMILY_ID)).isFalse();
}
```

Add service/controller tests for update settings, role revoke, expiring grant, last-admin protection, member update/deactivation, bind/unbind, and profile guardian assignment.

- [ ] **Step 2: Run the focused tests and observe failure**

Run:

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false \
  -Dtest='top.egon.mario.nutrition.access.NutritionAccessServiceTests,top.egon.mario.nutrition.ClanFamilyServiceTests,top.egon.mario.nutrition.MemberHealthServiceTests,top.egon.mario.nutrition.web.NutritionAdministrationControllerTests' test
```

Expected: FAIL at compilation because write/manage access methods and administration DTOs/routes do not exist.

- [ ] **Step 3: Add explicit request/response contracts**

Use records with validation; do not expose PO metadata directly:

```java
public record UpdateFamilySettingsRequest(
        @Size(max = 64) String region,
        @Size(min = 3, max = 3) String currency,
        List<@NotNull NutritionMealType> defaultMealTypes,
        @NotNull Boolean aiEnabled,
        LocalTime aiGenerateTime,
        @NotNull Boolean healthAlertEnabled,
        @NotNull Boolean budgetEnabled
) {}

public record CreateDataGrantRequest(
        Long memberProfileId,
        @Pattern(regexp = "USER|CLAN") String granteeType,
        @NotNull Long granteeId,
        @NotNull NutritionGrantDataScope dataScope,
        @NotNull NutritionGrantPermissionLevel permissionLevel,
        Instant expiresAt
) {}
```

`CreateScopedRoleBindingRequest` contains `subjectType`, `subjectId`, `roleCode`, `scopeType`, and `scopeId`; service validation rejects role/scope combinations outside the role table in the recovery design.

- [ ] **Step 4: Implement access levels and administration services**

Use explicit permission sets rather than enum ordinal comparison:

```java
private static final Set<NutritionGrantPermissionLevel> WRITE_GRANT_LEVELS = Set.of(
        NutritionGrantPermissionLevel.WRITE, NutritionGrantPermissionLevel.MANAGE);
private static final Set<NutritionGrantPermissionLevel> MANAGE_GRANT_LEVELS = Set.of(
        NutritionGrantPermissionLevel.MANAGE);

@Override
public boolean canWriteFamilyScope(Long userId, Long familyId, NutritionGrantDataScope scope) {
    return hasFamilyAdministrativeRole(userId, familyId)
            || hasActiveGrant(userId, familyId, scope, WRITE_GRANT_LEVELS);
}
```

Implement these service operations with family-scoped repository lookups:

```java
FamilyResponse updateFamilySettings(Long familyId, UpdateFamilySettingsRequest request, Long actorId);
FamilyResponse getFamilySettings(Long familyId, Long actorId);
List<ScopedRoleBindingResponse> listRoleBindings(Long familyId, Long actorId);
ScopedRoleBindingResponse createRoleBinding(Long familyId, CreateScopedRoleBindingRequest request, Long actorId);
ScopedRoleBindingResponse updateRoleBinding(Long familyId, Long bindingId,
        CreateScopedRoleBindingRequest request, Long actorId);
void revokeRoleBinding(Long familyId, Long bindingId, Long actorId);
List<DataGrantResponse> listDataGrants(Long familyId, Long actorId);
DataGrantResponse createDataGrant(Long familyId, CreateDataGrantRequest request, Long actorId);
DataGrantResponse updateDataGrant(Long familyId, Long grantId, UpdateDataGrantRequest request, Long actorId);
void revokeDataGrant(Long familyId, Long grantId, Long actorId);
List<ClanFamilyRelationResponse> listClanFamilyRelations(Long familyId, Long actorId);
void removeClanFamilyRelation(Long familyId, Long relationId, Long actorId);

MemberProfileResponse updateMemberProfile(Long familyId, Long memberProfileId,
        UpdateMemberProfileRequest request, Long actorId);
MemberProfileResponse deactivateMemberProfile(Long familyId, Long memberProfileId, Long actorId);
MemberProfileResponse bindMemberUser(Long familyId, Long memberProfileId,
        BindMemberUserRequest request, Long actorId);
MemberProfileResponse unbindMemberUser(Long familyId, Long memberProfileId, Long actorId);
ScopedRoleBindingResponse assignProfileGuardian(Long familyId, Long memberProfileId,
        AssignProfileGuardianRequest request, Long actorId);
void revokeProfileGuardian(Long familyId, Long memberProfileId, Long bindingId, Long actorId);
```

Deactivate rather than delete role/grant/member rows. Reject revoking the final active `FAMILY_ADMIN`. Validate bound user existence through the existing user repository before writing `bound_user_id`.

- [ ] **Step 5: Expose thin WebFlux routes**

Add controller mappings that delegate through `ReactiveNutritionSupport.blocking(...)`:

```java
@PutMapping("/families/{familyId}/settings")
@GetMapping("/families/{familyId}/settings")
@GetMapping("/families/{familyId}/role-bindings")
@PostMapping("/families/{familyId}/role-bindings")
@PutMapping("/families/{familyId}/role-bindings/{bindingId}")
@DeleteMapping("/families/{familyId}/role-bindings/{bindingId}")
@GetMapping("/families/{familyId}/data-grants")
@PostMapping("/families/{familyId}/data-grants")
@PutMapping("/families/{familyId}/data-grants/{grantId}")
@DeleteMapping("/families/{familyId}/data-grants/{grantId}")
@GetMapping("/families/{familyId}/clan-relations")
@DeleteMapping("/families/{familyId}/clan-relations/{relationId}")
@PutMapping("/families/{familyId}/members/{memberProfileId}")
@DeleteMapping("/families/{familyId}/members/{memberProfileId}")
@PostMapping("/families/{familyId}/members/{memberProfileId}/bind-user")
@DeleteMapping("/families/{familyId}/members/{memberProfileId}/bind-user")
@PostMapping("/families/{familyId}/members/{memberProfileId}/guardians")
@DeleteMapping("/families/{familyId}/members/{memberProfileId}/guardians/{bindingId}")
```

- [ ] **Step 6: Run focused and nutrition tests**

Run the Step 2 command, then:

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest='top.egon.mario.nutrition.**' test
```

Expected: all focused tests PASS; the nutrition suite reports zero failures and zero errors.

- [ ] **Step 7: Review and commit Task 1**

```bash
git diff --check
git add be/src/main/java/top/egon/mario/nutrition \
        be/src/test/java/top/egon/mario/nutrition
git commit -m "feat: complete nutrition family administration"
```

Confirm with `git show --stat --oneline HEAD` that no frontend, migration, Clocktower, or unrelated file entered the commit.

---

## Task 2: Platform Foods, Tags, Public Recipes, and All Import Types

**Files:**

- Create: `be/src/main/java/top/egon/mario/nutrition/dto/request/UpsertHealthTagRequest.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/dto/response/HealthTagResponse.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/dto/request/CreateStandardFoodRequest.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/dto/response/StandardFoodResponse.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/service/HealthTagService.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/service/RecipeService.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/service/MemberHealthService.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/repository/NutritionHealthTagRepository.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/repository/NutritionStandardFoodRepository.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/repository/NutritionRecipeRepository.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/web/HealthTagController.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/web/RecipeController.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/service/importer/AbstractHealthTagCsvImporter.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/service/importer/HealthTagCsvImporter.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/service/importer/AllergyTagCsvImporter.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/service/importer/DislikeTagCsvImporter.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/service/importer/DietGoalCsvImporter.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/service/importer/PublicRecipeCsvImporter.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/service/importer/FamilyIngredientMappingCsvImporter.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/service/importer/HistoricalPriceCsvImporter.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/service/importer/NutritionImportService.java`
- Test: `be/src/test/java/top/egon/mario/nutrition/RecipeServiceTests.java`
- Test: `be/src/test/java/top/egon/mario/nutrition/MemberHealthServiceTests.java`
- Test: `be/src/test/java/top/egon/mario/nutrition/importer/NutritionImportServiceTests.java`
- Test: `be/src/test/java/top/egon/mario/nutrition/web/NutritionControllerSmokeTests.java`

**Interfaces:**

- Consumes: Task 1 manage/read checks and the existing `NutritionCsvImportTemplate<R>` lifecycle.
- Produces: complete V31 standard-food/tag responses, combined public/family recipe visibility, active-family standard-food/tag search, and one registered importer for each `NutritionImportType`.

- [ ] **Step 1: Add failing catalog and import coverage tests**

```java
@Test
void everyDeclaredImportTypeHasExactlyOneImporter() {
    assertThat(importers)
            .extracting(NutritionCsvImportTemplate::importType)
            .containsExactlyInAnyOrder(NutritionImportType.values());
}

@Test
void standardFoodResponseContainsTheFullPersistedNutrientShape() {
    StandardFoodResponse response = recipeService.createStandardFood(fullFoodRequest(), platformAdmin());
    assertThat(response.sugarPer100g()).isEqualByComparingTo("3.500");
    assertThat(response.sodiumPer100g()).isEqualByComparingTo("12.000");
    assertThat(response.allergenTags()).containsExactly("MILK");
    assertThat(response.aliases()).containsExactly("whole milk");
}
```

Also assert: non-platform users cannot mutate catalog data; ordinary family readers can search active foods; tag codes are unique by type; public recipes have `familyId == null`; family importers reject another family id; confirmation is idempotent after completion.

- [ ] **Step 2: Run tests and observe the missing importer/full-shape failures**

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false \
  -Dtest='top.egon.mario.nutrition.RecipeServiceTests,top.egon.mario.nutrition.importer.NutritionImportServiceTests,top.egon.mario.nutrition.web.NutritionControllerSmokeTests' test
```

Expected: FAIL because seven importers, tag endpoints, and the full food fields are absent.

- [ ] **Step 3: Complete standard-food and health-tag CRUD**

Map every existing `NutritionStandardFoodPo` field in create/update/response code: aliases, external ids, eight nutrient values, purine, GI, allergen/suitable tags, quality, and status. Add platform CRUD plus family read-only search.

Use this service boundary for tags:

```java
List<HealthTagResponse> listTags(String tagType, boolean activeOnly, RbacPrincipal principal);
HealthTagResponse createTag(UpsertHealthTagRequest request, RbacPrincipal principal);
HealthTagResponse updateTag(Long tagId, UpsertHealthTagRequest request, RbacPrincipal principal);
HealthTagResponse deactivateTag(Long tagId, RbacPrincipal principal);
```

New health-profile writes in `MemberHealthService` must resolve every submitted tag against an active dictionary row. Existing stored unknown strings remain readable.

Expose family read-only catalog routes at `GET /api/nutrition/families/{familyId}/standard-foods` and `GET /api/nutrition/families/{familyId}/health-tags?tagType=...`; both require family read access. Platform create/update/deactivate remains under `/api/nutrition/platform/**` and requires a platform administrator.

- [ ] **Step 4: Complete platform-public recipe operations**

Extend `RecipeService` with `listPlatformRecipes`, `createPlatformRecipe`, `updatePlatformRecipe`, and `deactivateRecipe`. Reuse the recipe DTO from Task 3 only for fields already present here; Task 3 adds steps and deterministic calculation. Every public recipe sets `familyId = null` and `sourceType = PLATFORM_PUBLIC`.

Change `listFamilyRecipes(familyId, actorId)` to return the union of active `PLATFORM_PUBLIC` recipes and active recipes owned by that family; never include another family's private or AI-generated rows.

- [ ] **Step 5: Register the seven missing import strategies**

Keep the Template Method contract; concrete classes define only type, row parsing, validation, and transactional write. Share tag-row behavior through:

```java
public abstract class AbstractHealthTagCsvImporter
        extends NutritionCsvImportTemplate<AbstractHealthTagCsvImporter.TagRow> {

    protected abstract String tagType();

    public record TagRow(String tagCode, String name, String description, Integer sortOrder) {}
}
```

Importer mapping is exact:

```text
STANDARD_FOOD              -> StandardFoodCsvImporter
PUBLIC_RECIPE              -> PublicRecipeCsvImporter
HEALTH_TAG                 -> HealthTagCsvImporter
ALLERGY_TAG                -> AllergyTagCsvImporter
DISLIKE_TAG                -> DislikeTagCsvImporter
DIET_GOAL                  -> DietGoalCsvImporter
PRIVATE_RECIPE             -> FamilyRecipeCsvImporter
FAMILY_INGREDIENT_MAPPING  -> FamilyIngredientMappingCsvImporter
HISTORICAL_PRICE           -> HistoricalPriceCsvImporter
```

Family ingredient mapping updates only a recipe ingredient already owned by the job family. Historical price import writes `NutritionFoodPriceRecordPo` with that same family id. Preview never mutates target tables; confirm remains locked and idempotent.

- [ ] **Step 6: Run focused and nutrition tests**

Run the Step 2 command, then:

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest='top.egon.mario.nutrition.**' test
```

Expected: every import enum is represented exactly once and all nutrition tests PASS.

- [ ] **Step 7: Review and commit Task 2**

```bash
git diff --check
git add be/src/main/java/top/egon/mario/nutrition \
        be/src/test/java/top/egon/mario/nutrition
git commit -m "feat: complete nutrition catalog imports"
```

---

## Task 3: Recipe Detail, Ingredient Mapping, Unit Conversion, and Calculation

**Files:**

- Create: `be/src/main/java/top/egon/mario/nutrition/dto/request/RecipeStepRequest.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/dto/request/UpdateRecipeIngredientMappingRequest.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/dto/response/RecipeStepResponse.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/dto/response/RecipeValidationResponse.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/dto/request/CreateRecipeRequest.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/dto/request/RecipeIngredientRequest.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/dto/response/RecipeResponse.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/dto/response/RecipeIngredientResponse.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/service/calculation/NutritionUnitConversionService.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/service/calculation/NutritionCalculationService.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/service/RecipeService.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/repository/NutritionRecipeIngredientRepository.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/repository/NutritionRecipeStepRepository.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/repository/NutritionFoodPriceRecordRepository.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/web/RecipeController.java`
- Test: `be/src/test/java/top/egon/mario/nutrition/calculation/NutritionCalculationServiceTests.java`
- Test: `be/src/test/java/top/egon/mario/nutrition/RecipeServiceTests.java`

**Interfaces:**

- Consumes: full standard-food values and scoped recipe visibility from Task 2.
- Produces: `NutritionUnitConversionService.toGrams(...)`, complete recipe detail with steps and snapshots, explicit mapping/update APIs, and publish-blocking validation consumed by Tasks 4 and 5.

- [ ] **Step 1: Write failing unit and recipe validation tests**

```java
@Test
void convertsMassAndExplicitHouseholdUnitsToGrams() {
    assertThat(conversionService.toGrams(new BigDecimal("250"), "mg", null))
            .isEqualByComparingTo("0.250");
    assertThat(conversionService.toGrams(new BigDecimal("1.5"), "kg", null))
            .isEqualByComparingTo("1500.0");
    assertThat(conversionService.toGrams(new BigDecimal("2"), "piece", new BigDecimal("55")))
            .isEqualByComparingTo("110");
}

@Test
void requiredUnmappedIngredientBlocksRecipeValidation() {
    Long recipeId = createRecipeWithUnmappedRequiredFood();
    RecipeValidationResponse result = recipeService.validateRecipe(FAMILY_ID, recipeId, OWNER_ID);
    assertThat(result.publishable()).isFalse();
    assertThat(result.errors()).contains("NUTRITION_RECIPE_INGREDIENT_UNMAPPED");
}
```

Add assertions for all eight nutrients, public recipe visibility, cross-family recipe rejection, optional-unmapped warning, step ordering, and update replacing children transactionally.

Add a price-backed assertion that a family recipe response uses the latest compatible family normalized unit price to calculate ingredient and recipe estimated cost.

- [ ] **Step 2: Run tests and observe failure**

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false \
  -Dtest='top.egon.mario.nutrition.calculation.NutritionCalculationServiceTests,top.egon.mario.nutrition.RecipeServiceTests' test
```

Expected: FAIL because non-gram conversion, steps, mapping APIs, and full nutrition snapshots are missing.

- [ ] **Step 3: Implement deterministic unit conversion**

```java
public BigDecimal toGrams(BigDecimal amount, String unit, BigDecimal gramsPerUnit) {
    if (amount == null || amount.signum() <= 0 || !StringUtils.hasText(unit)) {
        throw new NutritionException("NUTRITION_UNIT_INVALID", "nutrition ingredient amount and unit are required");
    }
    return switch (unit.trim().toLowerCase(Locale.ROOT)) {
        case "mg" -> amount.divide(new BigDecimal("1000"), 6, RoundingMode.HALF_UP);
        case "g" -> amount;
        case "kg" -> amount.multiply(new BigDecimal("1000"));
        default -> {
            if (gramsPerUnit == null || gramsPerUnit.signum() <= 0) {
                throw new NutritionException("NUTRITION_UNIT_CONVERSION_MISSING",
                        "nutrition ingredient grams-per-unit conversion is required");
            }
            yield amount.multiply(gramsPerUnit);
        }
    };
}
```

Persist `gramsPerUnit` in the ingredient `metadataJson` so V31 remains unchanged. Do not infer piece/cup/spoon weights.

- [ ] **Step 4: Complete recipe write/read models**

Add `standardFoodId` and `gramsPerUnit` to ingredient requests; add cooking minutes, difficulty, tags, and ordered `RecipeStepRequest` values to recipe requests. Responses include steps, nutrition snapshot, estimated cost, mapping state, and per-ingredient grams-per-unit.

Implement these operations:

```java
RecipeResponse getRecipe(Long familyId, Long recipeId, Long actorId);
RecipeResponse updateFamilyRecipe(Long familyId, Long recipeId, CreateRecipeRequest request, Long actorId);
RecipeIngredientResponse updateIngredientMapping(Long familyId, Long recipeId, Long ingredientId,
        UpdateRecipeIngredientMappingRequest request, Long actorId);
RecipeValidationResponse validateRecipe(Long familyId, Long recipeId, Long actorId);
RecipeResponse deactivateFamilyRecipe(Long familyId, Long recipeId, Long actorId);
```

Visibility accepts active `PLATFORM_PUBLIC` recipes and active family-owned private/AI recipes only.

- [ ] **Step 5: Calculate and persist full nutrition snapshots**

For each mapped ingredient, convert to grams and scale every per-100g nutrient by `grams / 100`. Sum calories, protein, fat, carbs, sugar, sodium, fiber, and cholesterol in `NutritionTotals`. Persist explainable ingredient and recipe snapshots after create/update/mapping.

```java
BigDecimal factor = grams.divide(new BigDecimal("100"), 9, RoundingMode.HALF_UP);
NutritionTotals contribution = NutritionTotals.fromFood(food).multiply(factor);
totals = totals.plus(contribution);
```

Required mapping/conversion failures enter `RecipeValidationResponse.errors`; optional failures enter warnings and contribute zero only with the warning retained.

When family price history exists, scale the latest compatible `normalizedUnitPrice` by converted ingredient grams and persist the summed `estimatedCost`; keep the value null rather than inventing a price when no compatible record exists.

- [ ] **Step 6: Expose recipe detail and validation routes**

```java
@GetMapping("/families/{familyId}/recipes/{recipeId}")
@PutMapping("/families/{familyId}/recipes/{recipeId}")
@DeleteMapping("/families/{familyId}/recipes/{recipeId}")
@PutMapping("/families/{familyId}/recipes/{recipeId}/ingredients/{ingredientId}/mapping")
@GetMapping("/families/{familyId}/recipes/{recipeId}/validation")
```

- [ ] **Step 7: Run focused and nutrition tests**

Run the Step 2 command, then:

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest='top.egon.mario.nutrition.**' test
```

Expected: mass/conversion/calculation/recipe tests and the full nutrition suite PASS.

- [ ] **Step 8: Review and commit Task 3**

```bash
git diff --check
git add be/src/main/java/top/egon/mario/nutrition \
        be/src/test/java/top/egon/mario/nutrition
git commit -m "feat: complete nutrition recipe calculation"
```

---

## Task 4: Asynchronous AI Jobs, Complete Context, Recipe Materialization, and Validation

**Files:**

- Create: `be/src/main/java/top/egon/mario/nutrition/service/ai/NutritionRecommendationContext.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/service/ai/NutritionRecommendationContextService.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/service/ai/NutritionAiIngredientDraft.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/service/ai/NutritionAiRecipeDraft.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/service/ai/NutritionAiMenuDraft.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/service/ai/MaterializedNutritionRecipe.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/service/ai/NutritionAiRecipeMaterializationService.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/service/MealValidationResult.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/service/NutritionMealValidationService.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/service/rule/RepetitionRuleChecker.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/service/rule/PopulationRuleChecker.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/service/rule/RuleCheckRequest.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/service/rule/MemberRuleProfile.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/service/rule/RuleIngredient.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/service/ai/NutritionAiModelRequest.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/service/ai/DefaultNutritionAiModelClient.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/service/ai/NutritionAiService.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/service/ai/NutritionAiServiceImpl.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/service/ai/NutritionAiRecommendationRunner.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/service/ai/NutritionAiRecommendationScheduler.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/repository/NutritionAiRecommendationJobRepository.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/repository/NutritionMealPlanRepository.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/repository/NutritionMealPlanItemRepository.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/repository/NutritionRiskCheckResultRepository.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/web/AiRecommendationController.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/web/AiRecommendationJobController.java`
- Create test: `be/src/test/java/top/egon/mario/nutrition/ai/NutritionRecommendationContextServiceTests.java`
- Create test: `be/src/test/java/top/egon/mario/nutrition/ai/NutritionAiRecipeMaterializationServiceTests.java`
- Create test: `be/src/test/java/top/egon/mario/nutrition/NutritionMealValidationServiceTests.java`
- Test: `be/src/test/java/top/egon/mario/nutrition/ai/NutritionAiServiceTests.java`
- Test: `be/src/test/java/top/egon/mario/nutrition/rule/NutritionRuleCheckServiceTests.java`

**Interfaces:**

- Consumes: Tasks 1-3 access, health, tag, visible recipe, mapping, conversion, calculation, and rule services.
- Produces: immutable input snapshots; structured drafts containing an existing recipe id or generated recipe; persisted real recipe ids; `MealValidationResult`; promptly returned `PENDING` jobs and a locked local runner used by Task 5.

- [ ] **Step 1: Write failing context/materialization/job tests**

```java
@Test
void contextContainsHealthRecipesBudgetAndRecentMeals() {
    NutritionRecommendationContext context = contextService.build(
            FAMILY_ID, LocalDate.of(2026, 7, 20), List.of(NutritionMealType.DINNER), OWNER_ID,
            NutritionAiTriggerType.MANUAL);

    assertThat(context.members()).extracting(NutritionRecommendationContext.MemberContext::allergyTags)
            .contains(List.of("PEANUT"));
    assertThat(context.recipes()).extracting(NutritionRecommendationContext.RecipeContext::recipeId)
            .contains(PUBLIC_RECIPE_ID, FAMILY_RECIPE_ID);
    assertThat(context.standardFoods()).extracting(NutritionRecommendationContext.StandardFoodContext::foodId)
            .contains(TOMATO_ID);
    assertThat(context.healthTags()).extracting(NutritionRecommendationContext.HealthTagContext::tagCode)
            .contains("PEANUT");
    assertThat(context.budgetRules()).isNotEmpty();
    assertThat(context.recentPrices()).isNotEmpty();
    assertThat(context.recentMeals()).extracting(NutritionRecommendationContext.RecentMealContext::dishName)
            .contains("Tomato Pasta");
}

@Test
void generatedDishIsMaterializedAndMealItemReferencesRecipe() {
    modelClient.addResponse(generatedRecipeJson());
    NutritionAiRecommendationJobResponse queued = aiService.generateManualRecommendation(
            FAMILY_ID, PLAN_DATE, List.of(NutritionMealType.DINNER), OWNER_ID);

    assertThat(queued.status()).isEqualTo(NutritionAiJobStatus.PENDING);
    assertThat(modelClient.callCount()).isZero();
    assertThat(aiService.runPendingJobs(1)).isEqualTo(1);
    assertThat(mealPlanItemRepository.findAll()).singleElement()
            .extracting(NutritionMealPlanItemPo::getRecipeId).isNotNull();
}
```

Add tests for existing public/family recipe resolution, cross-family id rejection, unmapped required ingredient job failure, persisted risks, high-risk publishable false, scheduled deduplication, locked claim preventing double execution, and bounded failed-job retry metadata.

- [ ] **Step 2: Run tests and observe failure**

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false \
  -Dtest='top.egon.mario.nutrition.ai.NutritionRecommendationContextServiceTests,top.egon.mario.nutrition.ai.NutritionAiRecipeMaterializationServiceTests,top.egon.mario.nutrition.NutritionMealValidationServiceTests,top.egon.mario.nutrition.ai.NutritionAiServiceTests,top.egon.mario.nutrition.rule.NutritionRuleCheckServiceTests' test
```

Expected: FAIL because context/materialization/validation services and asynchronous claim APIs do not exist.

- [ ] **Step 3: Define the immutable recommendation context**

Use one typed record serialized to `input_snapshot`:

```java
public record NutritionRecommendationContext(
        Long familyId,
        String familyName,
        FamilySettingsContext settings,
        LocalDate plannedDate,
        List<NutritionMealType> mealTypes,
        List<MemberContext> members,
        List<RecipeContext> recipes,
        List<StandardFoodContext> standardFoods,
        List<HealthTagContext> healthTags,
        List<RecentMealContext> recentMeals,
        List<BudgetRuleContext> budgetRules,
        List<PriceContext> recentPrices,
        Long actorId,
        NutritionAiTriggerType triggerType,
        String auditCorrelationId
) {
    public record FamilySettingsContext(String region, String currency,
            List<NutritionMealType> defaultMealTypes, boolean aiEnabled, LocalTime aiGenerateTime,
            boolean healthAlertEnabled, boolean budgetEnabled) {}
    public record MemberContext(Long memberProfileId, NutritionMemberType memberType, String activityLevel,
            List<String> dietGoals, List<String> allergyTags, List<String> dislikeTags,
            List<String> restrictionTags, NutritionTotals targets) {}
    public record RecipeContext(Long recipeId, NutritionRecipeSourceType sourceType, String name,
            int servingCount, NutritionTotals nutrients, BigDecimal estimatedCost) {}
    public record StandardFoodContext(Long foodId, String name, String category,
            NutritionTotals nutrients, List<String> allergenTags, List<String> suitableTags) {}
    public record HealthTagContext(String tagType, String tagCode, String name) {}
    public record RecentMealContext(LocalDate planDate, NutritionMealType mealType, Long recipeId, String dishName) {}
    public record BudgetRuleContext(String periodType, BigDecimal amountLimit, String currency) {}
    public record PriceContext(Long standardFoodId, LocalDate priceDate, BigDecimal normalizedUnitPrice,
            String channel, String brand) {}
}
```

`build(...)` must scope every query by `familyId`, while including `PLATFORM_PUBLIC` recipes. Store the same serialized context on the job and pass it unchanged through `NutritionAiModelRequest.inputSnapshot()`.

- [ ] **Step 4: Extend the untrusted AI output contract**

```java
public record NutritionAiIngredientDraft(
        String foodName,
        String category,
        Long standardFoodId,
        BigDecimal amount,
        String unit,
        BigDecimal gramsPerUnit,
        boolean optional
) {}

public record NutritionAiRecipeDraft(
        NutritionMealType mealType,
        Long existingRecipeId,
        String name,
        BigDecimal servingCount,
        List<NutritionAiIngredientDraft> ingredients,
        List<RecipeStepRequest> steps,
        String reason
) {}
```

Exactly one of `existingRecipeId` or a valid generated recipe body is required. Reject invalid lengths, non-positive values, meal-type mismatches, invisible ids, unknown standard-food ids, and missing explicit conversions before persistence.

- [ ] **Step 5: Materialize every candidate to a real recipe**

```java
public record MaterializedNutritionRecipe(
        Long recipeId,
        NutritionMealType mealType,
        String dishName,
        BigDecimal servingCount,
        NutritionTotals nutrients,
        BigDecimal estimatedCost
) {}

List<MaterializedNutritionRecipe> materialize(
        Long familyId, NutritionAiMenuDraft draft, Long actorId);
```

Existing ids are revalidated for visibility. Generated bodies create `AI_GENERATED` family recipes, ingredients, and steps using Task 3 mapping/calculation. If any required ingredient is unmapped or unconvertible, roll back recipe/recommendation/meal creation and mark only the job failed in its separate transaction.

- [ ] **Step 6: Implement deterministic meal validation**

`NutritionMealValidationService.validateAndPersist(familyId, mealPlanId, actorId)` loads real recipes, calculates plan totals and ingredient cost, builds per-member `MemberRuleProfile`, executes every `NutritionRuleChecker`, replaces active risk rows, and writes plan/item snapshots. Return `publishable = false` for high/blocking risks or required recipe validation errors.

Persist AI explanatory text separately in recommendation metadata. Never convert it into `NutritionRiskCheckResultPo`.

- [ ] **Step 7: Make job creation asynchronous and claiming locked**

Keep the public `generateManualRecommendation(...)` signature but return immediately after the PENDING row commits. Add:

```java
int runPendingJobs(int limit);
```

The repository claims the oldest PENDING job with `PESSIMISTIC_WRITE`, changes it to RUNNING inside a short transaction, invokes the model outside that transaction, and completes success/failure in another transaction. `NutritionAiRecommendationRunner` performs both scheduled scans and `runPendingJobs(10)` on each cycle. Do not add an external queue.

Add `getJob(familyId, jobId, actorId)` to `NutritionAiService` and expose it at `GET /api/nutrition/families/{familyId}/ai-recommendation-jobs/{jobId}` for bounded frontend polling.

Scheduled scans create at most one PENDING/RUNNING/SUCCEEDED scheduled job per family/date. A failed row is retried only when metadata `retryCount < maxRetries` and `nextRetryAt <= now`, or by an explicit regeneration request.

Manual generation and regeneration require `requireCookFamily`, not family-manage permission, so assigned cooks can operate the approved workflow without receiving family administration.

- [ ] **Step 8: Persist recommendation, meal, items, validation, and job outcome atomically**

On success, create recommendation and `PENDING_REVIEW` plan, set every item `recipeId` from `MaterializedNutritionRecipe`, run validation, and store ids in job metadata. Any exception leaves no partial recommendation/meal/items; the separate failure transaction stores `FAILED`, a stable error code/message, and bounded retry metadata.

- [ ] **Step 9: Run focused and nutrition tests**

Run the Step 2 command, then:

```bash
./mvnw -Dmaven.build.cache.enabled=false -Dtest='top.egon.mario.nutrition.**' test
```

Expected: asynchronous, locking, context, materialization, rules, and full nutrition tests PASS.

- [ ] **Step 10: Review and commit Task 4**

```bash
git diff --check
git add be/src/main/java/top/egon/mario/nutrition \
        be/src/test/java/top/egon/mario/nutrition
git commit -m "feat: complete nutrition ai recommendation flow"
```

---

## Task 5: Cook Editing, Operation Logs, Concurrency, and Publish Gate

**Files:**

- Create: `be/src/main/java/top/egon/mario/nutrition/dto/request/MealPlanItemRequest.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/dto/request/UpdateMealPlanRequest.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/dto/request/AcknowledgeMealRiskRequest.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/dto/response/MealRiskResponse.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/dto/response/MealPlanItemResponse.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/dto/response/MealPlanResponse.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/service/MealPlanService.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/service/NutritionMealValidationService.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/repository/NutritionMealPlanRepository.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/repository/NutritionMealPlanItemRepository.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/repository/NutritionMealOperationLogRepository.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/repository/NutritionRiskCheckResultRepository.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/web/MealPlanController.java`
- Test: `be/src/test/java/top/egon/mario/nutrition/MealPlanServiceTests.java`
- Test: `be/src/test/java/top/egon/mario/nutrition/NutritionMealValidationServiceTests.java`

**Interfaces:**

- Consumes: real recipes and `MealValidationResult` from Tasks 3-4.
- Produces: versioned editable plans, operation snapshots, risk acknowledgements, and a deterministic publish gate for Task 6.

- [ ] **Step 1: Write failing edit, audit, and concurrency tests**

```java
@Test
void replacingDishRecalculatesSnapshotsAndWritesOperationLog() {
    MealPlanResponse updated = mealPlanService.updateMealPlan(FAMILY_ID, PLAN_ID,
            new UpdateMealPlanRequest(0L, CUTOFF, List.of(
                    new MealPlanItemRequest(ITEM_ID, NutritionMealType.DINNER,
                            REPLACEMENT_RECIPE_ID, new BigDecimal("3"), 0))), COOK_ID);

    assertThat(updated.status()).isEqualTo(NutritionMealPlanStatus.ADJUSTED);
    assertThat(updated.items()).singleElement().extracting(MealPlanItemResponse::recipeId)
            .isEqualTo(REPLACEMENT_RECIPE_ID);
    assertThat(updated.nutritionSnapshot()).contains("calories");
    assertThat(operationLogRepository.findAll()).singleElement()
            .satisfies(log -> assertThat(log.getBeforeSnapshot()).isNotEqualTo(log.getAfterSnapshot()));
}

@Test
void staleExpectedVersionIsRejected() {
    assertThatThrownBy(() -> mealPlanService.updateMealPlan(FAMILY_ID, PLAN_ID,
            requestWithExpectedVersion(0L), COOK_ID))
            .isInstanceOf(NutritionException.class)
            .extracting("code").isEqualTo("NUTRITION_MEAL_VERSION_CONFLICT");
}
```

Add tests for add/remove/reorder, invisible recipe, required-unmapped recipe, high-risk publish rejection, medium-risk acknowledgement/note, future cutoff, immutable published plans, regeneration preserving prior recommendation, and every state-transition audit row.

- [ ] **Step 2: Run tests and observe failure**

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false \
  -Dtest='top.egon.mario.nutrition.MealPlanServiceTests,top.egon.mario.nutrition.NutritionMealValidationServiceTests' test
```

Expected: FAIL because update DTOs, version checks, operation logging, and publish validation are absent.

- [ ] **Step 3: Add versioned meal-plan responses and edit request validation**

```java
public record UpdateMealPlanRequest(
        @NotNull Long expectedVersion,
        Instant confirmationCutoffAt,
        @NotEmpty List<@Valid MealPlanItemRequest> items
) {}
```

Add `version`, `nutritionSnapshot`, `risks`, and `publishable` to `MealPlanResponse`. Validate unique item ids, unique sort order, positive servings, visible recipe ids, and requested meal types before replacing children.

- [ ] **Step 4: Implement edits as one locked workflow**

Load the family plan with a write lock, compare `expectedVersion` to `BaseAuditablePo.getVersion()`, capture the before snapshot, update children, set `ADJUSTED`, invoke `validateAndPersist`, capture the after snapshot, then save one `NutritionMealOperationLogPo` with operation type `EDIT`.

Do not split the transaction into nested private service chains. `MealPlanService` orchestrates repositories plus the focused validation service directly.

- [ ] **Step 5: Implement risk acknowledgement and publish gates**

`acknowledgeRisks` accepts only risk ids belonging to the same family/plan, rejects high/blocking acknowledgements, and records actor/note/time in each risk row metadata. Publishing reruns validation and requires:

```java
boolean publishable = !items.isEmpty()
        && items.stream().allMatch(item -> item.recipeId() != null)
        && validation.publishable()
        && mediumRisksAcknowledged
        && confirmationCutoffIsValid;
```

Store `PUBLISH`, `ACKNOWLEDGE_RISK`, `CLOSE_CONFIRMATION`, `START_PREPARING`, `COMPLETE`, and `CANCEL` operation logs with before/after snapshots. Use existing locked reads for transitions.

- [ ] **Step 6: Add edit and acknowledgement routes**

```java
@PutMapping("/{mealPlanId}")
@PostMapping("/{mealPlanId}/risks/acknowledge")
@PostMapping("/{mealPlanId}/regenerate")
@PostMapping("/{mealPlanId}/start-preparing")
@PostMapping("/{mealPlanId}/cancel")
```

- [ ] **Step 7: Run focused and nutrition tests**

Run the Step 2 command, then the nutrition suite. Expected: edit/audit/concurrency/gate and all nutrition tests PASS.

- [ ] **Step 8: Review and commit Task 5**

```bash
git diff --check
git add be/src/main/java/top/egon/mario/nutrition \
        be/src/test/java/top/egon/mario/nutrition
git commit -m "feat: complete nutrition meal review"
```

---

## Task 6: Dish-Level Confirmation, Proxy Rules, Cutoff, and Exact Summary

**Files:**

- Create: `be/src/main/java/top/egon/mario/nutrition/dto/request/MealConfirmationItemRequest.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/dto/response/MealConfirmationItemResponse.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/dto/request/MealConfirmationRequest.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/dto/response/MealConfirmationResponse.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/dto/response/MealPlanSummaryResponse.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/service/MealConfirmationService.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/service/MealPlanService.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/repository/NutritionMealConfirmationRepository.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/repository/NutritionMealConfirmationItemRepository.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/repository/NutritionMemberProfileRepository.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/repository/NutritionRiskCheckResultRepository.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/web/MealConfirmationController.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/web/MealPlanController.java`
- Test: `be/src/test/java/top/egon/mario/nutrition/MealConfirmationServiceTests.java`
- Test: `be/src/test/java/top/egon/mario/nutrition/MealPlanServiceTests.java`

**Interfaces:**

- Consumes: Task 1 profile authorization and Task 5 published, versioned meal items/risks.
- Produces: confirmation headers plus item rows, exact serving summaries, and a locked `CONFIRM_CLOSED` state consumed by Task 7.

- [ ] **Step 1: Write failing dish-selection tests**

```java
@Test
void twoMembersSelectingDifferentServingsProduceExactDishTotals() {
    confirmationService.confirmMeal(FAMILY_ID, PLAN_ID,
            request(FIRST_MEMBER_ID, item(FIRST_DISH_ID, true, "1.5")), FIRST_USER_ID);
    confirmationService.confirmMeal(FAMILY_ID, PLAN_ID,
            request(SECOND_MEMBER_ID, item(SECOND_DISH_ID, true, "2")), SECOND_USER_ID);

    MealPlanSummaryResponse summary = mealPlanService.summary(FAMILY_ID, PLAN_ID, COOK_ID);

    assertThat(summary.confirmedMemberCount()).isEqualTo(2);
    assertThat(summary.awayMemberCount()).isZero();
    assertThat(summary.unconfirmedMemberCount()).isEqualTo(ACTIVE_MEMBER_COUNT - 2);
    assertThat(summary.dishes()).extracting(MealPlanSummaryResponse.DishSummary::confirmedServingTotal)
            .containsExactly(new BigDecimal("1.500"), new BigDecimal("2.000"));
}
```

Add tests for away confirmation with no items, item/plan mismatch, cross-family item id, own profile, family/profile guardian, unrelated profile rejection, high item risk block, medium item acknowledgement, update before cutoff, rejection at/after cutoff, and locked close expiring pending members.

- [ ] **Step 2: Run tests and observe failure**

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false \
  -Dtest='top.egon.mario.nutrition.MealConfirmationServiceTests,top.egon.mario.nutrition.MealPlanServiceTests' test
```

Expected: FAIL because request/response item contracts and exact summary counters are absent.

- [ ] **Step 3: Make confirmation items the write source of truth**

Replace meal-type selection input with:

```java
public record MealConfirmationRequest(
        @NotNull Long memberProfileId,
        @NotNull Boolean eatAtHome,
        List<@Valid MealConfirmationItemRequest> items,
        @Size(max = 512) String remark
) {}
```

For `eatAtHome=true`, require at least one selected valid plan item. For away confirmation, require an empty item list. Replace existing item rows transactionally on update; keep the confirmation header id stable.

Add `listConfirmations(familyId, mealPlanId, actorId)` and `getConfirmation(familyId, confirmationId, actorId)` so cooks can view family summaries and members can reload their persisted selection. Expose them at `GET /meal-plans/{mealPlanId}/confirmations` and `GET /confirmations/{confirmationId}` with the same profile/family read rules.

- [ ] **Step 4: Apply risk and proxy checks per selected item**

Use `requireConfirmMemberProfile` first. Query unresolved member-specific plan/item risks. High/blocking rejects only the affected selected item; medium requires `riskAcknowledged=true` and a nonblank adjustment note when the rule requests a note. Persist the acknowledgement on `NutritionMealConfirmationItemPo`.

- [ ] **Step 5: Derive summary and close behavior from persisted rows**

Summary aggregates selected item servings, not meal-plan servings multiplied by member count. Return confirmed, away, unconfirmed, risk counts, remarks, selected servings per dish, and `readyForShopping`.

Closing confirmation uses a locked plan read, rejects a future cutoff unless the cook explicitly closes early, marks remaining active members unconfirmed/expired in summary semantics, sets `CONFIRM_CLOSED`, and prevents further confirmation updates.

- [ ] **Step 6: Run focused and nutrition tests**

Run the Step 2 command, then the nutrition suite. Expected: all confirmation, summary, cutoff, proxy, and nutrition tests PASS.

- [ ] **Step 7: Review and commit Task 6**

```bash
git diff --check
git add be/src/main/java/top/egon/mario/nutrition \
        be/src/test/java/top/egon/mario/nutrition
git commit -m "feat: complete dish level meal confirmation"
```

---

## Task 7: Shopping Generation, Prices, Budget Rules, and Correct Cost Semantics

**Files:**

- Create: `be/src/main/java/top/egon/mario/nutrition/dto/request/UpsertBudgetRuleRequest.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/dto/request/TransitionShoppingListRequest.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/dto/response/BudgetRuleResponse.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/dto/response/BudgetSummaryResponse.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/dto/response/ShoppingListResponse.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/service/ShoppingListService.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/service/BudgetService.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/repository/NutritionShoppingListRepository.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/repository/NutritionShoppingListItemRepository.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/repository/NutritionFoodPriceRecordRepository.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/repository/NutritionBudgetRuleRepository.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/repository/NutritionBudgetSnapshotRepository.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/web/ShoppingListController.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/web/BudgetController.java`
- Test: `be/src/test/java/top/egon/mario/nutrition/ShoppingListServiceTests.java`
- Test: `be/src/test/java/top/egon/mario/nutrition/BudgetServiceTests.java`

**Interfaces:**

- Consumes: Task 6 closed confirmation item servings and Task 3 converted recipe ingredients.
- Produces: preview/final shopping data, price history, budget rules, cost-based `usageRate`, and source snapshots consumed by Task 8 reports.

- [ ] **Step 1: Write failing serving-derived shopping and cost tests**

```java
@Test
void finalShoppingAmountsUseConfirmedDishServings() {
    ShoppingListResponse list = shoppingListService.generateFinalShoppingList(FAMILY_ID, PLAN_ID, COOK_ID);
    assertThat(list.items()).singleElement().satisfies(item -> {
        assertThat(item.standardFoodId()).isEqualTo(TOMATO_ID);
        assertThat(item.plannedAmount()).isEqualByComparingTo("450.000");
        assertThat(item.plannedUnit()).isEqualTo("g");
    });
}

@Test
void usageRateIsCostDividedByLimitAndNotPurchaseCompletion() {
    BudgetSummaryResponse summary = budgetService.weeklyBudget(FAMILY_ID, WEEK_START, OWNER_ID);
    assertThat(summary.budgetLimit()).isEqualByComparingTo("500.00");
    assertThat(summary.totalAmount()).isEqualByComparingTo("125.00");
    assertThat(summary.usageRate()).isEqualByComparingTo("0.2500");
    assertThat(summary.shoppingCompletionRate()).isEqualByComparingTo("0.5000");
}
```

Add tests for preview before close, final rejection before close, idempotency by plan version/confirmation snapshot, selected=false exclusion, price normalization, actual/estimate precedence, per-dish ingredient cost, daily/weekly/monthly/per-meal rule selection, and shopping state guards.

- [ ] **Step 2: Run tests and observe failure**

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false \
  -Dtest='top.egon.mario.nutrition.ShoppingListServiceTests,top.egon.mario.nutrition.BudgetServiceTests' test
```

Expected: FAIL because current generation ignores confirmation items and current `usageRate` represents item completion.

- [ ] **Step 3: Separate preview from final generation**

Add:

```java
ShoppingListResponse previewShoppingList(Long familyId, Long mealPlanId, Long actorId);
ShoppingListResponse generateFinalShoppingList(Long familyId, Long mealPlanId, Long actorId);
```

Also add `listShoppingLists(familyId, mealPlanId, actorId)` and `listPriceRecords(familyId, standardFoodId, actorId)` for live page reload and price history. Expose them through `GET /shopping-lists?mealPlanId=...` and `GET /price-records?standardFoodId=...`.

Preview returns a calculated DTO without persisting final list rows. Final requires `CONFIRM_CLOSED`, locks by family/plan, aggregates selected serving counts, scales recipe ingredients by `confirmedServing / recipeServingCount`, converts to grams, and stores source plan version plus confirmation ids/versions in `generatedSnapshot`.

Add `generatedSnapshot` to `ShoppingListResponse` so clients and acceptance tests can display and verify the source plan/confirmation version without reading the PO.

- [ ] **Step 4: Complete shopping item and price transitions**

Expose explicit `activate`, `start-purchasing`, `mark-purchased`, `close`, and `cancel` transitions. Preserve channel, brand, original spec, purchase quantity, total price, and normalized unit price. Creating a price record from an item updates list actual total in the same transaction.

- [ ] **Step 5: Add budget-rule CRUD and corrected summaries**

```java
List<BudgetRuleResponse> listBudgetRules(Long familyId, Long actorId);
BudgetRuleResponse createBudgetRule(Long familyId, UpsertBudgetRuleRequest request, Long actorId);
BudgetRuleResponse updateBudgetRule(Long familyId, Long ruleId, UpsertBudgetRuleRequest request, Long actorId);
void deactivateBudgetRule(Long familyId, Long ruleId, Long actorId);
```

Validate `periodType` against `DAILY`, `WEEKLY`, `MONTHLY`, `PER_MEAL`; amount must be positive and warning threshold in `(0, 1]`.

Calculate summary values with this exact precedence:

```text
shopping item total price
-> shopping list actual total
-> latest compatible family price record
-> meal/recipe estimated cost marked estimated
```

Set `usageRate = periodCost / budgetLimit` with scale 4; return null when no enabled applicable limit. Return item completion separately as `shoppingCompletionRate`. Compute dish cost from its ingredients and serving scale; never split a meal total evenly.

- [ ] **Step 6: Add routes**

```java
@GetMapping("/meal-plans/{mealPlanId}/shopping-list/preview")
@PostMapping("/meal-plans/{mealPlanId}/shopping-list/generate")
@PostMapping("/shopping-lists/{shoppingListId}/transition")
@GetMapping("/budget-rules")
@PostMapping("/budget-rules")
@PutMapping("/budget-rules/{ruleId}")
@DeleteMapping("/budget-rules/{ruleId}")
```

- [ ] **Step 7: Run focused and nutrition tests**

Run the Step 2 command, then the nutrition suite. Expected: serving aggregation, idempotency, price, budget rule, and all nutrition tests PASS.

- [ ] **Step 8: Review and commit Task 7**

```bash
git diff --check
git add be/src/main/java/top/egon/mario/nutrition \
        be/src/test/java/top/egon/mario/nutrition
git commit -m "feat: complete nutrition shopping budget flow"
```

---

## Task 8: Completed-Meal Records, Corrections, Extra Foods, and Reports

**Files:**

- Create: `be/src/main/java/top/egon/mario/nutrition/dto/response/NutritionTrendPointResponse.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/dto/response/NutritionHomeOverviewResponse.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/dto/response/NutritionRecordResponse.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/dto/response/NutritionDailyOverviewResponse.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/dto/response/NutritionReportResponse.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/service/NutritionRecordService.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/service/NutritionHomeQueryService.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/service/MealPlanService.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/repository/NutritionRecordRepository.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/repository/NutritionRecordAdjustmentRepository.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/repository/NutritionExtraFoodRecordRepository.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/repository/NutritionReportSnapshotRepository.java`
- Modify: `be/src/main/java/top/egon/mario/nutrition/web/NutritionRecordController.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/web/NutritionHomeController.java`
- Test: `be/src/test/java/top/egon/mario/nutrition/NutritionRecordServiceTests.java`
- Test: `be/src/test/java/top/egon/mario/nutrition/MealPlanServiceTests.java`
- Create test: `be/src/test/java/top/egon/mario/nutrition/NutritionHomeQueryServiceTests.java`

**Interfaces:**

- Consumes: Task 6 selected confirmation items/servings, Task 3 recipe calculations, Task 7 cost sources, and Task 1 profile write authorization.
- Produces: idempotent item-derived records, immutable original snapshots plus effective corrections, live daily/trend/home queries, and explicit report snapshots for Tasks 9-10.

- [ ] **Step 1: Write failing record and report tests**

```java
@Test
void completionCreatesNonZeroRecordPerSelectedDishAndIsIdempotent() {
    List<NutritionRecordResponse> first = recordService.generateForCompletedMealPlan(FAMILY_ID, PLAN_ID, COOK_ID);
    List<NutritionRecordResponse> second = recordService.generateForCompletedMealPlan(FAMILY_ID, PLAN_ID, COOK_ID);

    assertThat(first).hasSize(SELECTED_CONFIRMATION_ITEM_COUNT)
            .allSatisfy(record -> assertThat(record.nutrients().calories()).isPositive());
    assertThat(second).extracting(NutritionRecordResponse::id)
            .containsExactlyElementsOf(first.stream().map(NutritionRecordResponse::id).toList());
    assertThat(recordRepository.count()).isEqualTo(SELECTED_CONFIRMATION_ITEM_COUNT);
}

@Test
void correctionPreservesOriginalAndChangesEffectiveOverview() {
    NutritionRecordPo original = recordRepository.save(generatedRecord("450"));
    recordService.adjustRecord(FAMILY_ID, original.getId(), adjustment("400", "left food"), OWNER_ID);

    assertThat(recordRepository.findById(original.getId()).orElseThrow().getCalories())
            .isEqualByComparingTo("450");
    assertThat(recordService.dailyOverview(FAMILY_ID, DATE, OWNER_ID).totalNutrients().calories())
            .isEqualByComparingTo("400");
    assertThat(adjustmentRepository.findAll()).singleElement();
}
```

Add tests for selected=false exclusion, serving scaling, metadata `sourceMealPlanItemId`, family/profile guardian authorization, unrelated profile rejection, extra food calculation, target comparison, risk counts, cost breakdowns, common dishes, nutrient reminders, trend points, query-without-snapshot, explicit report generation creating one snapshot, and a home overview that returns today's plan/confirmation/risk/shopping/cost/budget state from live tables.

- [ ] **Step 2: Run tests and observe failure**

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false \
  -Dtest='top.egon.mario.nutrition.NutritionRecordServiceTests,top.egon.mario.nutrition.MealPlanServiceTests' test
```

Expected: FAIL because generation does not consume confirmation items, reports lack effective corrections/trends, and the home query service is absent.

- [ ] **Step 3: Generate records from selected dish servings**

Lock the completed meal plan before generation. For each selected confirmation item, calculate `recipeTotals * confirmedServing / recipeServingCount`, create one `NutritionRecordPo`, and write `sourceMealPlanItemId`, recipe id, serving count, and calculation inputs into `metadataJson`/`calculationSnapshot`.

Idempotency key is `(familyId, mealPlanId, memberProfileId, sourceMealPlanItemId)` read from existing records while holding the meal-plan lock. Existing matching records are returned unchanged.

Add `sourceMealPlanItemId` to `NutritionRecordResponse`; derive it from metadata so no V31 column is required.

- [ ] **Step 4: Preserve original rows and derive effective values**

Corrections append `NutritionRecordAdjustmentPo` with before/after snapshots. Do not update original nutrient columns. A shared `effectiveNutrients(record, latestAdjustment)` function feeds daily/report queries. Profile owners/guardians use `requireWriteMemberProfile`; family admins/cooks may correct all family records.

Extra-food creation validates the authorized member and either calculates from a standard food plus Task 3 conversion or accepts the explicit nutrients already supported by the request, recording which source was used.

- [ ] **Step 5: Complete live queries and explicit snapshots**

Daily overview returns member targets and actual comparison. Weekly/monthly reports aggregate effective records plus extra foods, risk counts, actual/estimated/per-person cost, common dishes, sodium/sugar/fat reminders, and daily `NutritionTrendPointResponse` values.

Keep ordinary query routes read-only and snapshot-free. Add explicit generation routes:

```java
NutritionReportResponse generateFamilyWeeklyReport(Long familyId, LocalDate weekStart, Long actorId);
NutritionReportResponse generateFamilyMonthlyReport(Long familyId, LocalDate month, Long actorId);
```

These two methods append `NutritionReportSnapshotPo`; repeated ordinary GET requests do not.

Implement `NutritionHomeQueryService.overview(familyId, date, actorId)` as a read-only aggregator over today's meal plans, confirmation summary, unresolved risks, shopping state, actual/estimated cost, applicable budget usage, and nutrition-record readiness. Expose it at `GET /api/nutrition/families/{familyId}/overview?date=YYYY-MM-DD`; it must not call write services or create snapshots.

Use this response contract:

```java
public record NutritionHomeOverviewResponse(
        Long familyId,
        LocalDate date,
        List<MealPlanResponse> mealPlans,
        int confirmedMemberCount,
        int awayMemberCount,
        int unconfirmedMemberCount,
        Map<NutritionRiskLevel, Long> riskCounts,
        NutritionShoppingListStatus shoppingState,
        BigDecimal actualCost,
        BigDecimal estimatedCost,
        BigDecimal budgetUsageRate,
        boolean nutritionRecordReady
) {}
```

- [ ] **Step 6: Run focused and nutrition tests**

Run the Step 2 command, then the nutrition suite. Expected: record idempotency, correction authorization, report details, and all nutrition tests PASS.

- [ ] **Step 7: Review and commit Task 8**

```bash
git diff --check
git add be/src/main/java/top/egon/mario/nutrition \
        be/src/test/java/top/egon/mario/nutrition
git commit -m "feat: complete nutrition records reports"
```

---

## Task 9: Live Frontend Foundation, Family, Health, Recipe, and Platform Pages

**Files:**

- Modify: `fe/package.json`
- Modify: `fe/bun.lock`
- Create: `fe/vitest.config.ts`
- Create: `fe/src/modules/nutrition/test/nutritionTestSetup.ts`
- Create: `fe/src/modules/nutrition/test/renderNutritionPage.tsx`
- Create: `fe/src/modules/nutrition/useNutritionFamilySelection.ts`
- Create: `fe/src/modules/nutrition/useNutritionFamilySelection.test.tsx`
- Create: `fe/src/modules/nutrition/components/NutritionAsyncState.tsx`
- Create: `fe/src/modules/nutrition/components/NutritionAsyncState.test.tsx`
- Modify: `fe/src/modules/nutrition/nutritionTypes.ts`
- Modify: `fe/src/modules/nutrition/nutritionService.ts`
- Modify: `fe/src/modules/nutrition/nutritionService.test.ts`
- Modify: `fe/src/modules/nutrition/components/CurrentFamilySelect.test.tsx`
- Modify: `fe/src/modules/nutrition/NutritionHomePage.tsx`
- Modify: `fe/src/modules/nutrition/NutritionHomePage.test.tsx`
- Modify: `fe/src/modules/nutrition/ClanFamilyPage.tsx`
- Modify: `fe/src/modules/nutrition/ClanFamilyPage.test.tsx`
- Modify: `fe/src/modules/nutrition/MemberHealthPage.tsx`
- Modify: `fe/src/modules/nutrition/MemberHealthPage.test.tsx`
- Modify: `fe/src/modules/nutrition/RecipeLibraryPage.tsx`
- Modify: `fe/src/modules/nutrition/RecipeLibraryPage.test.tsx`
- Modify: `fe/src/modules/nutrition/PlatformNutritionConfigPage.tsx`
- Modify: `fe/src/modules/nutrition/PlatformNutritionConfigPage.test.tsx`

**Interfaces:**

- Consumes: Tasks 1-3 and Task 8 family/health/recipe/catalog/home/report APIs plus existing auth/RBAC stores.
- Produces: live family selection, consistent async states, typed administration editors, and an interaction-test harness used by Task 10.

- [ ] **Step 1: Add the required test-only interaction dependencies**

Run from `fe`:

```bash
bun add --dev @testing-library/react@16.3.0 @testing-library/user-event@14.6.1 jsdom@26.1.0
```

Expected: only `package.json` and `bun.lock` change. These dependencies are test-only and do not enter the production bundle.

Create `vitest.config.ts` so the production Vite configuration remains unchanged:

```typescript
import {defineConfig} from 'vitest/config'

export default defineConfig({
    test: {
        environment: 'jsdom',
        setupFiles: ['./src/modules/nutrition/test/nutritionTestSetup.ts'],
    },
})
```

The setup file defines only the browser capabilities required by Ant Design tests; it must not mock nutrition APIs globally:

```typescript
import {cleanup} from '@testing-library/react'
import {afterEach, vi} from 'vitest'

afterEach(cleanup)

Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: vi.fn().mockImplementation((query: string) => ({
        matches: false,
        media: query,
        onchange: null,
        addListener: vi.fn(),
        removeListener: vi.fn(),
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        dispatchEvent: vi.fn(),
    })),
})

class ResizeObserverStub {
    observe() {}
    unobserve() {}
    disconnect() {}
}

Object.defineProperty(globalThis, 'ResizeObserver', {value: ResizeObserverStub})
Object.defineProperty(window, 'scrollTo', {value: vi.fn(), writable: true})
```

The render helper supplies required runtime contexts:

```typescript
import {App} from 'antd'
import type {ReactElement} from 'react'
import {render} from '@testing-library/react'
import {MemoryRouter} from 'react-router'

export function renderNutritionPage(ui: ReactElement) {
    return render(<App><MemoryRouter>{ui}</MemoryRouter></App>)
}
```

- [ ] **Step 2: Replace static page tests with failing interaction tests**

Mock `nutritionService` and assert loading-to-ready behavior and mutations:

```typescript
test('loads accessible families and saves family settings', async () => {
    vi.mocked(listNutritionFamilies).mockResolvedValue([family])
    vi.mocked(updateNutritionFamilySettings).mockResolvedValue({...family, aiEnabled: true})
    const user = userEvent.setup()

    renderNutritionPage(<ClanFamilyPage/>)
    expect(screen.getByText('加载中')).toBeTruthy()
    await screen.findByText('Mario Family')
    await user.click(screen.getByRole('button', {name: '编辑设置'}))
    await user.click(screen.getByRole('switch', {name: '启用 AI'}))
    await user.click(screen.getByRole('button', {name: '保存'}))

    expect(updateNutritionFamilySettings).toHaveBeenCalledWith(family.id, expect.objectContaining({aiEnabled: true}))
})
```

Add page tests for empty/error/forbidden, role/grant revoke, member bind/guardian, tag validation error, recipe mapping warning, platform import preview/confirm, and RBAC-disabled mutation buttons.

- [ ] **Step 3: Run frontend tests and observe failure**

```bash
cd fe
bun run test -- src/modules/nutrition
```

Expected: FAIL because pages still import fixtures and live service functions/types are missing.

- [ ] **Step 4: Extend typed service contracts**

Add exact Task 1-3/8 request and response types to `nutritionTypes.ts`, including role/grant, settings, member update/bind/guardian, full food/tag/recipe, recipe validation, home overview, and report details.

Add service methods whose names mirror backend operations, for example:

```typescript
export function updateNutritionFamilySettings(familyId: number, request: NutritionUpdateFamilySettingsRequest) {
    return requestJson<NutritionFamilyResponse>(familyPath(familyId, '/settings'), {method: 'PUT', body: request})
}

export function listNutritionRoleBindings(familyId: number) {
    return requestJson<NutritionScopedRoleBindingResponse[]>(familyPath(familyId, '/role-bindings'))
}

export function getNutritionRecipe(familyId: number, recipeId: number) {
    return requestJson<NutritionRecipeResponse>(familyPath(familyId, `/recipes/${recipeId}`))
}
```

Keep all URL construction in `nutritionService.ts`; pages never call `requestJson` directly.

- [ ] **Step 5: Implement family selection and async state**

`useNutritionFamilySelection` calls `listNutritionFamilies`, preserves a still-accessible stored id, selects the first family when necessary, and exposes:

```typescript
{
    families,
    currentFamily,
    currentFamilyId,
    setCurrentFamilyId,
    state,
    error,
    reload,
}
```

`NutritionAsyncState` renders Spin, Empty, Alert, or children from `NutritionLoadState`. Map HTTP 403 to `forbidden`; do not hide other failures as empty results.

- [ ] **Step 6: Convert the administration pages to live APIs**

Implement:

- `NutritionHomePage`: live current family, today's pending/confirmation/shopping/cost/budget overview.
- `ClanFamilyPage`: clan/family create/association, settings, roles, grants, relation removal.
- `MemberHealthPage`: member create/update/deactivate/bind, guardian, health profile form with tag dictionaries.
- `RecipeLibraryPage`: public/family recipe list, full editor, steps, mapping state, validation.
- `PlatformNutritionConfigPage`: full foods/tags/public recipes and all nine import types with preview/confirm.

Use existing Ant Design form/table/drawer patterns and `canUseRbacButton` for visibility/enabled state. Backend 403 remains authoritative and is displayed.

Use Ant Design 6 APIs verified by the local CLI: `Form.List` for recipe ingredients/steps and meal items, controlled `Table` values with stable `rowKey` plus responsive/scroll columns, `Drawer open/loading/destroyOnHidden`, and `App.useApp()` for feedback. Do not introduce deprecated `destroyOnClose` or static message APIs.

- [ ] **Step 7: Run focused frontend verification**

```bash
bun run typecheck
bun run test -- src/modules/nutrition
bun run lint -- src/modules/nutrition
antd lint ./src/modules/nutrition --format json
```

Expected: typecheck passes; all converted-page interaction tests pass; ESLint and Ant Design lint have zero errors. Fixture imports may remain only in the six workflow pages reserved for Task 10.

- [ ] **Step 8: Review and commit Task 9**

```bash
git diff --check
git add fe/package.json fe/bun.lock fe/vitest.config.ts fe/src/modules/nutrition
git commit -m "feat: connect nutrition administration pages"
```

Before committing, use `git diff --cached --name-only` to confirm no backend, migration, or unrelated frontend path is included.

---

## Task 10: Live AI, Meal, Confirmation, Shopping, Budget, and Record Pages

**Files:**

- Modify: `fe/src/modules/nutrition/nutritionTypes.ts`
- Modify: `fe/src/modules/nutrition/nutritionService.ts`
- Modify: `fe/src/modules/nutrition/nutritionService.test.ts`
- Modify: `fe/src/modules/nutrition/AiMenuPage.tsx`
- Modify: `fe/src/modules/nutrition/AiMenuPage.test.tsx`
- Modify: `fe/src/modules/nutrition/MealConfirmationPage.tsx`
- Modify: `fe/src/modules/nutrition/MealConfirmationPage.test.tsx`
- Modify: `fe/src/modules/nutrition/MealSummaryPage.tsx`
- Modify: `fe/src/modules/nutrition/MealSummaryPage.test.tsx`
- Modify: `fe/src/modules/nutrition/ShoppingListPage.tsx`
- Modify: `fe/src/modules/nutrition/ShoppingListPage.test.tsx`
- Modify: `fe/src/modules/nutrition/BudgetPage.tsx`
- Modify: `fe/src/modules/nutrition/BudgetPage.test.tsx`
- Modify: `fe/src/modules/nutrition/NutritionRecordPage.tsx`
- Modify: `fe/src/modules/nutrition/NutritionRecordPage.test.tsx`
- Delete: `fe/src/modules/nutrition/nutritionPageData.ts`

**Interfaces:**

- Consumes: Tasks 4-8 backend workflows and Task 9 live frontend foundation.
- Produces: complete live operational UI, no production fixtures, and interaction tests for all user-visible nutrition flows.

- [ ] **Step 1: Write failing end-user interaction tests**

At minimum, add these test cases:

```typescript
test('cook edits a generated dish, acknowledges medium risk, and publishes', async () => {
    vi.mocked(listNutritionMealPlans).mockResolvedValue([pendingPlan])
    vi.mocked(updateNutritionMealPlan).mockResolvedValue(adjustedPlan)
    vi.mocked(acknowledgeNutritionMealRisks).mockResolvedValue(acknowledgedPlan)
    vi.mocked(publishNutritionMealPlan).mockResolvedValue(publishedPlan)
    const user = userEvent.setup()

    renderNutritionPage(<AiMenuPage/>)
    await screen.findByText(pendingPlan.title)
    await user.click(screen.getByRole('button', {name: '替换菜品'}))
    await user.click(screen.getByRole('button', {name: '保存调整'}))
    await user.click(screen.getByRole('checkbox', {name: /确认中风险/}))
    await user.click(screen.getByRole('button', {name: '发布菜单'}))

    expect(updateNutritionMealPlan).toHaveBeenCalled()
    expect(acknowledgeNutritionMealRisks).toHaveBeenCalled()
    expect(publishNutritionMealPlan).toHaveBeenCalled()
})

test('member submits dish-level serving selections', async () => {
    renderNutritionPage(<MealConfirmationPage/>)
    await screen.findByText('番茄意面')
    await userEvent.type(screen.getByLabelText('番茄意面份数'), '1.5')
    await userEvent.click(screen.getByRole('button', {name: '提交确认'}))
    expect(createNutritionMealConfirmation).toHaveBeenCalledWith(
            FAMILY_ID, PLAN_ID, expect.objectContaining({items: [expect.objectContaining({servingCount: '1.5'})]}))
})
```

Also test AI PENDING polling/error/regeneration, high-risk disabled publish/selection, summary counts, close-confirmation, shopping preview/final/state/price entry, budget rule CRUD and distinct ratios, record correction/extra food/report/trend, stale-version reload prompt, and permission-denied mutation controls.

- [ ] **Step 2: Run workflow-page tests and observe failure**

```bash
cd fe
bun run test -- src/modules/nutrition/AiMenuPage.test.tsx \
  src/modules/nutrition/MealConfirmationPage.test.tsx \
  src/modules/nutrition/MealSummaryPage.test.tsx \
  src/modules/nutrition/ShoppingListPage.test.tsx \
  src/modules/nutrition/BudgetPage.test.tsx \
  src/modules/nutrition/NutritionRecordPage.test.tsx
```

Expected: FAIL because the pages still use fixture constants and disabled buttons.

- [ ] **Step 3: Extend service calls for Tasks 4-8**

Add typed calls for AI job status/run/regeneration, plan update/risk acknowledgement/transitions, dish confirmations, shopping preview/final/transitions/prices, budget-rule CRUD, record correction/extra foods, and report generation. Update `nutritionService.test.ts` to assert every new URL, method, body, and omitted empty query parameter.

- [ ] **Step 4: Convert AI and meal pages**

`AiMenuPage` loads recommendations and plans, polls a PENDING/RUNNING job with a bounded timer cleared on unmount, separates AI explanation from backend risks, edits items with expected version, and enables publish only when backend `publishable` and RBAC allow it.

`MealConfirmationPage` loads the current published plan/member profile, renders a responsive row/card per dish, captures selected/serving/risk acknowledgement, and submits confirmation items. `MealSummaryPage` displays exact confirmed/away/unconfirmed counts and per-dish servings, then allows an authorized cook to close confirmation.

- [ ] **Step 5: Convert shopping, budget, and record pages**

`ShoppingListPage` displays preview before close and persisted final data after close, supports item state/price entry, and labels estimated versus actual cost. `BudgetPage` manages budget rules and labels `预算使用率` separately from `采购完成率`. `NutritionRecordPage` loads daily/member views, target comparison, corrections, extra foods, weekly/monthly reports, reminders, and trends.

Every mutation sets a saving state, reports API errors through `App.useApp().message`, refreshes authoritative server data on success, and handles `NUTRITION_MEAL_VERSION_CONFLICT` by prompting reload rather than retrying stale input.

- [ ] **Step 6: Remove fixture production data**

Run:

```bash
rg -n "nutritionPageData" fe/src/modules/nutrition --glob '!*.test.ts' --glob '!*.test.tsx'
```

Expected before deletion: no output. Delete `nutritionPageData.ts`, then run:

```bash
test ! -e src/modules/nutrition/nutritionPageData.ts
```

Expected: exit 0.

- [ ] **Step 7: Run focused frontend verification**

```bash
bun run typecheck
bun run test -- src/modules/nutrition
bun run lint -- src/modules/nutrition
antd lint ./src/modules/nutrition --format json
bun run build
```

Expected: all commands exit 0; production nutrition code has zero fixture imports.

- [ ] **Step 8: Review and commit Task 10**

```bash
git diff --check
git add fe/src/modules/nutrition
git commit -m "feat: connect nutrition workflow pages"
```

---

## Task 11: Vertical Acceptance, Full Verification, and Documentation Reconciliation

**Files:**

- Create: `be/src/test/java/top/egon/mario/nutrition/NutritionVerticalFlowTests.java`
- Modify: `be/src/test/java/top/egon/mario/nutrition/web/NutritionControllerSmokeTests.java`
- Modify: `fe/src/modules/nutrition/nutritionService.test.ts`
- Modify: `fe/src/modules/nutrition/NutritionHomePage.test.tsx`
- Modify: `fe/src/modules/nutrition/ClanFamilyPage.test.tsx`
- Modify: `fe/src/modules/nutrition/MemberHealthPage.test.tsx`
- Modify: `fe/src/modules/nutrition/RecipeLibraryPage.test.tsx`
- Modify: `fe/src/modules/nutrition/PlatformNutritionConfigPage.test.tsx`
- Modify: `fe/src/modules/nutrition/AiMenuPage.test.tsx`
- Modify: `fe/src/modules/nutrition/MealConfirmationPage.test.tsx`
- Modify: `fe/src/modules/nutrition/MealSummaryPage.test.tsx`
- Modify: `fe/src/modules/nutrition/ShoppingListPage.test.tsx`
- Modify: `fe/src/modules/nutrition/BudgetPage.test.tsx`
- Modify: `fe/src/modules/nutrition/NutritionRecordPage.test.tsx`
- Modify: `FEATURE_CHECKLIST.md`
- Modify: `README.md`

**Interfaces:**

- Consumes: every contract from Tasks 1-10.
- Produces: executable proof for the ten mandatory vertical scenarios, accurate project documentation, and a fully verified recovery commit.

- [ ] **Step 1: Add the ten vertical acceptance tests**

Create one descriptive test for each approved scenario:

```java
@Test
void ownerConfiguresFamilyAndCookWhileUnrelatedUserIsDenied() {
    FamilyResponse family = createConfiguredFamily(OWNER_ID);
    clanFamilyService.createRoleBinding(family.id(), cookBinding(COOK_ID, family.id()), OWNER_ID);

    assertThat(accessService.canReadFamily(COOK_ID, family.id())).isTrue();
    assertThat(accessService.canReadFamily(UNRELATED_ID, family.id())).isFalse();
    assertThat(clanFamilyService.getFamilySettings(family.id(), COOK_ID).aiEnabled()).isTrue();
}

@Test
void clanNeedsExplicitScopedGrantAndRevocationRemovesAccess() {
    FamilyResponse family = createConfiguredFamily(OWNER_ID);
    ClanResponse clan = createClanWithMember(CLAN_OWNER_ID, CLAN_MEMBER_ID);
    clanFamilyService.associateClanFamily(clan.id(), family.id(), OWNER_ID);
    assertThat(accessService.canReadFamily(CLAN_MEMBER_ID, family.id())).isFalse();

    DataGrantResponse grant = clanFamilyService.createDataGrant(
            family.id(), clanHealthReadGrant(clan.id()), OWNER_ID);
    assertThat(accessService.canReadFamilyScope(CLAN_MEMBER_ID, family.id(),
            NutritionGrantDataScope.HEALTH_PROFILE)).isTrue();
    clanFamilyService.revokeDataGrant(family.id(), grant.id(), OWNER_ID);
    assertThat(accessService.canReadFamilyScope(CLAN_MEMBER_ID, family.id(),
            NutritionGrantDataScope.HEALTH_PROFILE)).isFalse();
}

@Test
void scheduledAiUsesCompleteContextAndCreatesValidatedRealRecipeItems() {
    FamilyResponse family = createAiReadyFamilyWithHealthRecipeBudgetAndPrice(OWNER_ID);
    aiService.generateScheduledRecommendation(family.id(), PLAN_DATE);
    assertThat(aiService.runPendingJobs(10)).isEqualTo(1);

    assertThat(aiJobRepository.findAll()).singleElement()
            .satisfies(job -> assertThat(job.getInputSnapshot())
                    .contains("members", "recipes", "budgetRules", "recentPrices", "recentMeals"));
    assertThat(mealPlanItemRepository.findAll()).allSatisfy(item -> assertThat(item.getRecipeId()).isNotNull());
    assertThat(mealPlanRepository.findAll()).singleElement()
            .extracting(NutritionMealPlanPo::getStatus).isEqualTo(NutritionMealPlanStatus.PENDING_REVIEW);
}

@Test
void allergyBlocksPublishAndMediumRiskRequiresAcknowledgement() {
    NutritionMealPlanPo plan = createValidatedPlanWithHighAndMediumRisk();
    assertThatThrownBy(() -> mealPlanService.publishMealPlan(FAMILY_ID, plan.getId(), COOK_ID))
            .isInstanceOf(NutritionException.class)
            .extracting("code").isEqualTo("NUTRITION_MEAL_RISK_BLOCKED");

    resolveHighRiskByReplacingDish(plan.getId());
    assertThatThrownBy(() -> mealPlanService.publishMealPlan(FAMILY_ID, plan.getId(), COOK_ID))
            .isInstanceOf(NutritionException.class)
            .extracting("code").isEqualTo("NUTRITION_MEAL_RISK_ACKNOWLEDGEMENT_REQUIRED");
    acknowledgeMediumRisk(plan.getId(), COOK_ID);
    assertThat(mealPlanService.publishMealPlan(FAMILY_ID, plan.getId(), COOK_ID).status())
            .isEqualTo(NutritionMealPlanStatus.PUBLISHED);
}

@Test
void cookEditRecalculatesAndAuditsBeforePublish() {
    NutritionMealPlanPo plan = createPendingReviewPlan();
    MealPlanResponse edited = mealPlanService.updateMealPlan(
            FAMILY_ID, plan.getId(), replacementRequest(plan.getVersion()), COOK_ID);

    assertThat(edited.nutritionSnapshot()).contains("calories");
    assertThat(edited.estimatedCost()).isPositive();
    assertThat(operationLogRepository.findAll()).extracting(NutritionMealOperationLogPo::getOperationType)
            .contains("EDIT");
}

@Test
void memberDishSelectionsProduceExactSummaryServings() {
    PublishedMealScenario scenario = createPublishedMealForTwoMembers();
    confirmDish(scenario.firstMember(), scenario.firstDish(), "1.5");
    confirmDish(scenario.secondMember(), scenario.secondDish(), "2.0");

    MealPlanSummaryResponse summary = mealPlanService.summary(FAMILY_ID, scenario.planId(), COOK_ID);
    assertThat(summary.dishes()).extracting(MealPlanSummaryResponse.DishSummary::confirmedServingTotal)
            .containsExactly(new BigDecimal("1.500"), new BigDecimal("2.000"));
}

@Test
void closedConfirmationProducesServingDerivedShoppingList() {
    PublishedMealScenario scenario = createPublishedMealForTwoMembers();
    confirmDish(scenario.firstMember(), scenario.firstDish(), "1.5");
    mealPlanService.closeConfirmation(FAMILY_ID, scenario.planId(), COOK_ID);

    ShoppingListResponse list = shoppingListService.generateFinalShoppingList(
            FAMILY_ID, scenario.planId(), COOK_ID);
    assertThat(list.items()).allSatisfy(item -> assertThat(item.plannedAmount()).isPositive());
    assertThat(list.generatedSnapshot()).contains("confirmationIds", "planVersion");
}

@Test
void pricesDriveBudgetUsageSeparatelyFromShoppingCompletion() {
    createHalfPurchasedListWithCost("125.00");
    createWeeklyBudgetRule("500.00");
    BudgetSummaryResponse summary = budgetService.weeklyBudget(FAMILY_ID, WEEK_START, OWNER_ID);

    assertThat(summary.usageRate()).isEqualByComparingTo("0.2500");
    assertThat(summary.shoppingCompletionRate()).isEqualByComparingTo("0.5000");
}

@Test
void completionIsIdempotentAndCorrectionPreservesOriginalRecord() {
    Long planId = createCompletedConfirmedMeal();
    List<NutritionRecordResponse> first = recordService.generateForCompletedMealPlan(FAMILY_ID, planId, COOK_ID);
    List<NutritionRecordResponse> second = recordService.generateForCompletedMealPlan(FAMILY_ID, planId, COOK_ID);
    BigDecimal originalCalories = first.getFirst().nutrients().calories();
    recordService.adjustRecord(FAMILY_ID, first.getFirst().id(), adjustment("400"), OWNER_ID);

    assertThat(second).extracting(NutritionRecordResponse::id)
            .containsExactlyElementsOf(first.stream().map(NutritionRecordResponse::id).toList());
    assertThat(recordRepository.findById(first.getFirst().id()).orElseThrow().getCalories())
            .isEqualByComparingTo(originalCalories);
}

@Test
void overviewUsesTheSamePersistedFamilyWorkflowData() {
    Long planId = createCompletedConfirmedMeal();
    NutritionHomeOverviewResponse overview = homeQueryService.overview(FAMILY_ID, PLAN_DATE, OWNER_ID);

    assertThat(overview.mealPlans()).extracting(MealPlanResponse::id).contains(planId);
    assertThat(overview.confirmedMemberCount()).isPositive();
    assertThat(overview.shoppingState()).isNotNull();
    assertThat(overview.nutritionRecordReady()).isTrue();
}
```

Use `@SpringBootTest` with the AI runner disabled and a deterministic primary fake model client. Assertions must cross repositories/services; do not reproduce implementation calculations inside the test.

Implement every named private scenario builder in the same test class. Builders may compose public nutrition services and repositories to establish prerequisite state, but they must not invoke the method being asserted or calculate the expected result themselves.

- [ ] **Step 2: Run the vertical suite**

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false -Dtest=NutritionVerticalFlowTests test
```

Expected: 10 tests PASS. If a test fails, return to the owning Task 1-10 contract and correct that nutrition-scoped implementation before continuing; do not weaken the acceptance assertion.

- [ ] **Step 3: Strengthen controller and frontend acceptance assertions**

Controller tests must call representative real endpoints for settings/grants, recipe validation, AI enqueue, plan edit/publish, confirmation items, shopping finalization, budget rules, and records; reflection-only path assertions remain supplementary.

Frontend tests must prove mocked HTTP load/mutation/error states for every production page. Run:

```bash
cd ../fe
rg -n "nutritionPageData" src/modules/nutrition --glob '!*.test.ts' --glob '!*.test.tsx'
```

Expected: no output.

- [ ] **Step 4: Reconcile documentation to operable behavior**

Update the Nutrition section in `FEATURE_CHECKLIST.md` from broad “implemented” claims to checked workflow statements proven by the acceptance suite. Update the README nutrition bullet to mention cook review, dish-level confirmations, serving-derived shopping/budget, and effective records. Do not claim browser/manual runtime validation.

- [ ] **Step 5: Run full backend verification**

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false -Dtest='top.egon.mario.nutrition.**' test
./mvnw -Dmaven.build.cache.enabled=false test
```

Expected: both commands exit 0 with zero failures and zero errors.

- [ ] **Step 6: Run full frontend verification**

```bash
cd ../fe
bun run typecheck
bun run test
bun run lint
antd lint ./src/modules/nutrition --format json
bun run build
```

Expected: typecheck, tests, lint, and production build exit 0. Report any pre-existing unrelated warnings separately; do not call a command successful if it exits nonzero.

- [ ] **Step 7: Verify migration and scope invariants**

```bash
cd ..
git diff --exit-code 47fc35c4 -- be/src/main/resources/db/migration/V31__create_nutrition_mvp_schema.sql
test "$(git diff --name-only 47fc35c4 -- be/src/main/resources/db/migration | wc -l | tr -d ' ')" = "0"
rg -n "nutritionPageData" fe/src/modules/nutrition --glob '!*.test.ts' --glob '!*.test.tsx' && exit 1 || true
git diff --check
```

Expected: V31 and the migration directory are unchanged, production fixture search returns no match, and the final diff has no whitespace errors.

- [ ] **Step 8: Review and commit Task 11**

```bash
git add be/src/test/java/top/egon/mario/nutrition \
        fe/src/modules/nutrition \
        FEATURE_CHECKLIST.md README.md
git diff --cached --check
git commit -m "test: verify nutrition recovery workflows"
```

Confirm the commit contains only acceptance tests and documentation unless a named acceptance failure required a small nutrition-scoped correction. Do not start the project.

---

## Completion Audit

Before declaring the recovery complete, verify this exact checklist against test output and committed diffs:

- Family settings, roles, grants, member bindings, guardians, expiration, revocation, and last-admin protection are operable through APIs and live UI.
- All nine import types have parse/validate/preview/confirm/write coverage.
- Standard foods expose all V31 nutrients/tags and recipes expose steps, mappings, conversion, calculation, and cost.
- AI jobs return PENDING, are claimed safely, use complete family context, create real recipe-linked items, and remain review-only.
- Cook edits recalculate nutrition/cost/risks, write operation logs, reject stale versions, and pass a deterministic publish gate.
- Confirmations persist dish selection and serving rows with correct proxy/risk/cutoff rules.
- Shopping amounts come from selected confirmed servings; budget usage means cost divided by limit.
- Completion creates non-zero idempotent records; corrections preserve originals and reports use effective values.
- Every production nutrition page loads and mutates through `nutritionService.ts`; `nutritionPageData.ts` is absent.
- V31 and all pre-existing Flyway migrations remain byte-for-byte unchanged.
- Focused and full backend/frontend verification commands all pass.

## Execution Handoff

Plan complete. Two execution options:

1. **Subagent-Driven (recommended)** - use `superpowers:subagent-driven-development`, dispatch a fresh implementation subagent per task, perform specification and code-quality review after each, and keep one commit per task.
2. **Inline Execution** - use `superpowers:executing-plans`, execute Tasks 1-11 in batches with review checkpoints, and keep one commit per task.

Whichever option is selected, create an isolated worktree with `superpowers:using-git-worktrees` before Task 1 because the current main worktree contains unrelated user-owned staged and unstaged changes.

For subagent-driven execution, clean up each finished subagent after its task and create a new subagent for the next task, as required by the repository instructions.
