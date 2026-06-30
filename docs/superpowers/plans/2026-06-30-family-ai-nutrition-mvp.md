# Family AI Nutrition MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the complete family AI nutrition MVP inside CyberMario: clan/family/member health management, scoped authorization, standard food and recipe data, CSV import, scheduled AI menu recommendations, cook review, member confirmation, shopping lists, budget statistics, nutrition records, reports, and frontend admin pages.

**Architecture:** Add a new modular-monolith domain under `top.egon.mario.nutrition` and `fe/src/modules/nutrition`. CyberMario RBAC controls module/API entry, while nutrition services enforce family/clan/member scoped data access. AI is isolated behind `NutritionAiService`; backend rule checks and nutrition calculations remain the source of truth.

**Tech Stack:** Java 21, Spring Boot 3.5, WebFlux, Spring Data JPA, Flyway/PostgreSQL, JUnit 5, AssertJ, React 19, TypeScript 6, Vite, Ant Design 6, Vitest, Bun.

---

## Scope Check

The approved spec is a full MVP blueprint and spans several subsystems. Keep this as one master implementation plan because the first release must produce one coherent product slice, but execute it as independent commits. If a task is still too large during execution, split only that task into a child plan without changing the boundaries below.

Do not start backend or frontend dev servers during implementation. The user will run runtime testing.

Do not modify existing Flyway migrations. The initial nutrition schema is one new versioned migration. Re-check the current migration sequence before Task 1; at planning time the latest observed backend migration is `be/src/main/resources/db/migration/V30__create_im_core_schema.sql`, so the expected next file is `V31__create_nutrition_mvp_schema.sql`.

## File Structure

### Backend Files To Create

- `be/src/main/resources/db/migration/V31__create_nutrition_mvp_schema.sql` - initial nutrition MVP schema.
- `be/src/main/java/top/egon/mario/nutrition/po/enums/*.java` - nutrition status, role, scope, import, risk, meal, shopping, budget, and record enums.
- `be/src/main/java/top/egon/mario/nutrition/po/*.java` - JPA entities for clan, family, member profile, health profile, scoped role binding, data grant, standard food, tag, recipe, import job, AI recommendation, meal plan, confirmation, shopping, budget, and nutrition record tables.
- `be/src/main/java/top/egon/mario/nutrition/repository/*.java` - Spring Data repositories.
- `be/src/main/java/top/egon/mario/nutrition/dto/request/*.java` - request DTOs.
- `be/src/main/java/top/egon/mario/nutrition/dto/response/*.java` - response DTOs.
- `be/src/main/java/top/egon/mario/nutrition/service/NutritionException.java` - domain exception with stable error codes.
- `be/src/main/java/top/egon/mario/nutrition/service/access/NutritionAccessService.java` - scoped data authorization.
- `be/src/main/java/top/egon/mario/nutrition/service/access/NutritionAccessServiceImpl.java` - access implementation.
- `be/src/main/java/top/egon/mario/nutrition/service/ClanFamilyService.java`
- `be/src/main/java/top/egon/mario/nutrition/service/MemberHealthService.java`
- `be/src/main/java/top/egon/mario/nutrition/service/RecipeService.java`
- `be/src/main/java/top/egon/mario/nutrition/service/importer/NutritionImportService.java`
- `be/src/main/java/top/egon/mario/nutrition/service/importer/*.java` - CSV import template and row validators.
- `be/src/main/java/top/egon/mario/nutrition/service/calculation/NutritionCalculationService.java`
- `be/src/main/java/top/egon/mario/nutrition/service/rule/NutritionRuleCheckService.java`
- `be/src/main/java/top/egon/mario/nutrition/service/rule/*.java` - allergy, dislike, diet-goal, budget, repetition, and population rule checkers.
- `be/src/main/java/top/egon/mario/nutrition/service/ai/NutritionAiService.java`
- `be/src/main/java/top/egon/mario/nutrition/service/ai/NutritionAiServiceImpl.java`
- `be/src/main/java/top/egon/mario/nutrition/service/MealPlanService.java`
- `be/src/main/java/top/egon/mario/nutrition/service/MealConfirmationService.java`
- `be/src/main/java/top/egon/mario/nutrition/service/ShoppingListService.java`
- `be/src/main/java/top/egon/mario/nutrition/service/BudgetService.java`
- `be/src/main/java/top/egon/mario/nutrition/service/NutritionRecordService.java`
- `be/src/main/java/top/egon/mario/nutrition/service/bootstrap/NutritionPermissionCatalog.java`
- `be/src/main/java/top/egon/mario/nutrition/service/bootstrap/NutritionRbacResourceProvider.java`
- `be/src/main/java/top/egon/mario/nutrition/web/ReactiveNutritionSupport.java`
- `be/src/main/java/top/egon/mario/nutrition/web/*.java` - thin WebFlux controllers.

### Backend Tests To Create

- `be/src/test/java/top/egon/mario/nutrition/NutritionSchemaMigrationTests.java`
- `be/src/test/java/top/egon/mario/nutrition/NutritionRbacResourceProviderTests.java`
- `be/src/test/java/top/egon/mario/nutrition/access/NutritionAccessServiceTests.java`
- `be/src/test/java/top/egon/mario/nutrition/ClanFamilyServiceTests.java`
- `be/src/test/java/top/egon/mario/nutrition/MemberHealthServiceTests.java`
- `be/src/test/java/top/egon/mario/nutrition/RecipeServiceTests.java`
- `be/src/test/java/top/egon/mario/nutrition/importer/NutritionImportServiceTests.java`
- `be/src/test/java/top/egon/mario/nutrition/rule/NutritionRuleCheckServiceTests.java`
- `be/src/test/java/top/egon/mario/nutrition/calculation/NutritionCalculationServiceTests.java`
- `be/src/test/java/top/egon/mario/nutrition/ai/NutritionAiServiceTests.java`
- `be/src/test/java/top/egon/mario/nutrition/MealPlanServiceTests.java`
- `be/src/test/java/top/egon/mario/nutrition/MealConfirmationServiceTests.java`
- `be/src/test/java/top/egon/mario/nutrition/ShoppingListServiceTests.java`
- `be/src/test/java/top/egon/mario/nutrition/BudgetServiceTests.java`
- `be/src/test/java/top/egon/mario/nutrition/NutritionRecordServiceTests.java`
- `be/src/test/java/top/egon/mario/nutrition/web/NutritionControllerSmokeTests.java`

### Frontend Files To Create

- `fe/src/modules/nutrition/nutritionTypes.ts` - DTO and enum types.
- `fe/src/modules/nutrition/nutritionPermissionCodes.ts` - menu and button permission constants.
- `fe/src/modules/nutrition/nutritionService.ts` - REST helpers.
- `fe/src/modules/nutrition/currentFamilyStore.tsx` - current family selector state.
- `fe/src/modules/nutrition/components/CurrentFamilySelect.tsx`
- `fe/src/modules/nutrition/components/RiskTag.tsx`
- `fe/src/modules/nutrition/components/MoneyText.tsx`
- `fe/src/modules/nutrition/components/ImportJobPanel.tsx`
- `fe/src/modules/nutrition/NutritionHomePage.tsx`
- `fe/src/modules/nutrition/ClanFamilyPage.tsx`
- `fe/src/modules/nutrition/MemberHealthPage.tsx`
- `fe/src/modules/nutrition/RecipeLibraryPage.tsx`
- `fe/src/modules/nutrition/AiMenuPage.tsx`
- `fe/src/modules/nutrition/MealConfirmationPage.tsx`
- `fe/src/modules/nutrition/MealSummaryPage.tsx`
- `fe/src/modules/nutrition/ShoppingListPage.tsx`
- `fe/src/modules/nutrition/BudgetPage.tsx`
- `fe/src/modules/nutrition/NutritionRecordPage.tsx`
- `fe/src/modules/nutrition/PlatformNutritionConfigPage.tsx`

### Frontend Tests To Create

- `fe/src/modules/nutrition/nutritionService.test.ts`
- `fe/src/modules/nutrition/currentFamilyStore.test.tsx`
- `fe/src/modules/nutrition/components/CurrentFamilySelect.test.tsx`
- `fe/src/modules/nutrition/components/RiskTag.test.tsx`
- `fe/src/modules/nutrition/components/MoneyText.test.tsx`
- `fe/src/modules/nutrition/components/ImportJobPanel.test.tsx`
- `fe/src/modules/nutrition/NutritionHomePage.test.tsx`
- `fe/src/modules/nutrition/ClanFamilyPage.test.tsx`
- `fe/src/modules/nutrition/MemberHealthPage.test.tsx`
- `fe/src/modules/nutrition/RecipeLibraryPage.test.tsx`
- `fe/src/modules/nutrition/AiMenuPage.test.tsx`
- `fe/src/modules/nutrition/MealConfirmationPage.test.tsx`
- `fe/src/modules/nutrition/ShoppingListPage.test.tsx`
- `fe/src/modules/nutrition/BudgetPage.test.tsx`
- `fe/src/modules/nutrition/NutritionRecordPage.test.tsx`
- `fe/src/modules/nutrition/PlatformNutritionConfigPage.test.tsx`

### Existing Files To Modify

- `fe/src/app/routes.tsx` - add nutrition routes.
- `fe/src/app/routes.test.ts` - assert route registration.
- No existing backend package should be modified except imports/configuration required by normal component scanning.
- No existing migration file should be modified.

## Task 1: Schema, Enums, and Module Boundary

**Files:**

- Create: `be/src/main/resources/db/migration/V31__create_nutrition_mvp_schema.sql`
- Create: `be/src/main/java/top/egon/mario/nutrition/po/enums/*.java`
- Create: `be/src/test/java/top/egon/mario/nutrition/NutritionSchemaMigrationTests.java`

- [ ] **Step 1: Write the failing migration tests**

Create `NutritionSchemaMigrationTests` with assertions matching this shape:

```java
class NutritionSchemaMigrationTests {
    private static final Path MIGRATION = Path.of("src/main/resources/db/migration/V31__create_nutrition_mvp_schema.sql");

    @Test
    void migrationCreatesCoreNutritionTables() throws IOException {
        String sql = Files.readString(MIGRATION);
        assertThat(sql).contains("CREATE TABLE nutrition_clan");
        assertThat(sql).contains("CREATE TABLE nutrition_family");
        assertThat(sql).contains("CREATE TABLE nutrition_clan_family");
        assertThat(sql).contains("CREATE TABLE nutrition_member_profile");
        assertThat(sql).contains("CREATE TABLE nutrition_health_profile");
        assertThat(sql).contains("CREATE TABLE nutrition_scoped_role_binding");
        assertThat(sql).contains("CREATE TABLE nutrition_data_grant");
        assertThat(sql).contains("CREATE TABLE nutrition_standard_food");
        assertThat(sql).contains("CREATE TABLE nutrition_recipe");
        assertThat(sql).contains("CREATE TABLE nutrition_import_job");
        assertThat(sql).contains("CREATE TABLE nutrition_ai_recommendation_job");
        assertThat(sql).contains("CREATE TABLE nutrition_meal_plan");
        assertThat(sql).contains("CREATE TABLE nutrition_meal_confirmation");
        assertThat(sql).contains("CREATE TABLE nutrition_shopping_list");
        assertThat(sql).contains("CREATE TABLE nutrition_food_price_record");
        assertThat(sql).contains("CREATE TABLE nutrition_record");
    }

    @Test
    void migrationUsesFamilyIdOnBusinessTables() throws IOException {
        String sql = Files.readString(MIGRATION);
        assertTableContains(sql, "nutrition_recipe", "family_id");
        assertTableContains(sql, "nutrition_meal_plan", "family_id");
        assertTableContains(sql, "nutrition_meal_confirmation", "family_id");
        assertTableContains(sql, "nutrition_shopping_list", "family_id");
        assertTableContains(sql, "nutrition_food_price_record", "family_id");
        assertTableContains(sql, "nutrition_record", "family_id");
    }
}
```

- [ ] **Step 2: Run the migration tests and verify they fail**

Run:

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false -Dtest=NutritionSchemaMigrationTests test
```

Expected: FAIL because `V31__create_nutrition_mvp_schema.sql` and enum classes do not exist yet.

- [ ] **Step 3: Add the migration and enum classes**

Create `V31__create_nutrition_mvp_schema.sql` with these tables and indexes:

```text
nutrition_clan
nutrition_family
nutrition_clan_family
nutrition_member_profile
nutrition_health_profile
nutrition_scoped_role_binding
nutrition_data_grant
nutrition_standard_food
nutrition_health_tag
nutrition_recipe
nutrition_recipe_ingredient
nutrition_recipe_step
nutrition_import_job
nutrition_import_error
nutrition_ai_recommendation_job
nutrition_ai_recommendation
nutrition_risk_check_result
nutrition_meal_plan
nutrition_meal_plan_item
nutrition_meal_confirmation
nutrition_meal_confirmation_item
nutrition_meal_operation_log
nutrition_shopping_list
nutrition_shopping_list_item
nutrition_food_price_record
nutrition_budget_rule
nutrition_budget_snapshot
nutrition_record
nutrition_record_adjustment
nutrition_extra_food_record
nutrition_report_snapshot
```

Every table must include `id BIGSERIAL PRIMARY KEY`, `created_at`, `updated_at`, `created_by`, `updated_by`, `version`, and `deleted` unless the table is pure append-only error/detail data. Use `JSONB` for snapshots and tag arrays where the design calls for flexible metadata. Add indexes for `family_id`, `member_profile_id`, date/status fields, import job status, AI job status, and meal plan date.

Create enums under `be/src/main/java/top/egon/mario/nutrition/po/enums`:

```text
NutritionStatus
NutritionScopeType
NutritionSubjectType
NutritionRoleCode
NutritionGrantDataScope
NutritionGrantPermissionLevel
NutritionMemberType
NutritionRecipeSourceType
NutritionImportType
NutritionImportStatus
NutritionRiskLevel
NutritionMealPlanStatus
NutritionMealType
NutritionConfirmationStatus
NutritionShoppingListStatus
NutritionAiJobStatus
NutritionAiTriggerType
```

- [ ] **Step 4: Run the targeted tests and compile**

Run:

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false -Dtest=NutritionSchemaMigrationTests test
./mvnw -Dmaven.build.cache.enabled=false -DskipTests compile
```

Expected: both commands PASS.

- [ ] **Step 5: Commit**

```bash
git add be/src/main/resources/db/migration/V31__create_nutrition_mvp_schema.sql \
  be/src/main/java/top/egon/mario/nutrition/po/enums \
  be/src/test/java/top/egon/mario/nutrition/NutritionSchemaMigrationTests.java
git commit -m "feat: add nutrition mvp schema"
```

## Task 2: JPA Entities, Repositories, RBAC Provider, and Access Service

**Files:**

- Create: `be/src/main/java/top/egon/mario/nutrition/po/*.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/repository/*.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/service/NutritionException.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/service/access/NutritionAccessService.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/service/access/NutritionAccessServiceImpl.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/service/bootstrap/NutritionPermissionCatalog.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/service/bootstrap/NutritionRbacResourceProvider.java`
- Test: `be/src/test/java/top/egon/mario/nutrition/NutritionRbacResourceProviderTests.java`
- Test: `be/src/test/java/top/egon/mario/nutrition/access/NutritionAccessServiceTests.java`

- [ ] **Step 1: Write failing RBAC and access tests**

Create RBAC provider tests asserting:

```java
class NutritionRbacResourceProviderTests {
    @Test
    void providesNutritionMenusApisAndPresetRoles() {
        NutritionRbacResourceProvider provider = new NutritionRbacResourceProvider();
        assertThat(provider.appCode()).isEqualTo("nutrition");
        assertThat(provider.resources()).extracting("permCode")
                .contains("menu:nutrition", "menu:nutrition:families", "menu:nutrition:platform",
                        "api:nutrition:family:*", "api:nutrition:platform:*");
        assertThat(provider.rolePresets()).extracting("roleCode")
                .contains("NUTRITION_USER", "NUTRITION_PLATFORM_ADMIN");
    }
}
```

Create access tests asserting:

```java
class NutritionAccessServiceTests {
    @Test
    void familyMembershipGrantsFamilyReadButClanRelationDoesNot() {
        // user 10 has MEMBER binding on family 100
        // family 100 belongs to clan 200
        // user 11 has CLAN_MEMBER binding on clan 200 only
        assertThat(access.canReadFamily(10L, 100L)).isTrue();
        assertThat(access.canReadFamily(11L, 100L)).isFalse();
    }

    @Test
    void explicitFamilyGrantAllowsClanRoleToReadGrantedScope() {
        // grant family 100 HEALTH to CLAN role CLAN_ADMIN in clan 200
        assertThat(access.canReadFamilyScope(20L, 100L, NutritionGrantDataScope.HEALTH)).isTrue();
    }
}
```

- [ ] **Step 2: Run tests and verify they fail**

Run:

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false -Dtest=NutritionRbacResourceProviderTests,NutritionAccessServiceTests test
```

Expected: FAIL because provider, entities, repositories, and access service do not exist.

- [ ] **Step 3: Add entities, repositories, provider, and access logic**

Create one JPA entity per migration table with names ending in `Po`. Repositories should be package-public interfaces under `nutrition.repository` where possible and expose only methods needed by services.

Implement `NutritionPermissionCatalog` with menu, button, and API seeds matching the plan. Implement `NutritionRbacResourceProvider` like existing `RagRbacResourceProvider`: convert catalog seeds into `RbacResourceSeed` and preset roles.

Implement `NutritionAccessService` methods:

```java
boolean canReadFamily(Long userId, Long familyId);
void requireReadFamily(Long userId, Long familyId);
void requireManageFamily(Long userId, Long familyId);
void requireCookFamily(Long userId, Long familyId);
void requireConfirmMemberProfile(Long userId, Long familyId, Long memberProfileId);
boolean canReadFamilyScope(Long userId, Long familyId, NutritionGrantDataScope scope);
```

Access must honor family scoped roles first, then explicit `nutrition_data_grant`. Clan-family membership alone must not pass.

- [ ] **Step 4: Run targeted tests and compile**

Run:

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false -Dtest=NutritionRbacResourceProviderTests,NutritionAccessServiceTests test
./mvnw -Dmaven.build.cache.enabled=false -DskipTests compile
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add be/src/main/java/top/egon/mario/nutrition/po \
  be/src/main/java/top/egon/mario/nutrition/repository \
  be/src/main/java/top/egon/mario/nutrition/service \
  be/src/test/java/top/egon/mario/nutrition/NutritionRbacResourceProviderTests.java \
  be/src/test/java/top/egon/mario/nutrition/access/NutritionAccessServiceTests.java
git commit -m "feat: add nutrition access foundation"
```

## Task 3: Clan, Family, Member, and Health APIs

**Files:**

- Create: `be/src/main/java/top/egon/mario/nutrition/dto/request/CreateClanRequest.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/dto/request/CreateFamilyRequest.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/dto/request/CreateMemberProfileRequest.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/dto/request/UpdateHealthProfileRequest.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/dto/response/ClanResponse.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/dto/response/FamilyResponse.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/dto/response/MemberProfileResponse.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/dto/response/HealthProfileResponse.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/service/ClanFamilyService.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/service/MemberHealthService.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/web/ReactiveNutritionSupport.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/web/ClanFamilyController.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/web/MemberHealthController.java`
- Test: `be/src/test/java/top/egon/mario/nutrition/ClanFamilyServiceTests.java`
- Test: `be/src/test/java/top/egon/mario/nutrition/MemberHealthServiceTests.java`

- [ ] **Step 1: Write failing service tests**

Write tests for:

```java
@Test
void creatingFamilyBindsOwnerAsFamilyAdminAndMemberProfileOwner()

@Test
void clanFamilyAssociationDoesNotCreateFamilyReadBinding()

@Test
void familyMemberCanReadAllFamilyHealthProfiles()

@Test
void unrelatedUserCannotReadFamilyHealthProfile()
```

- [ ] **Step 2: Run tests and verify they fail**

Run:

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClanFamilyServiceTests,MemberHealthServiceTests test
```

Expected: FAIL because services and DTOs do not exist.

- [ ] **Step 3: Implement services and controllers**

`ClanFamilyService` must create clans, create families, associate clans and families, list current user's accessible families, and manage data grants. `MemberHealthService` must create member profiles, bind optional users, save health profiles, and list family health profiles after access checks.

`ReactiveNutritionSupport` must mirror `ReactiveRagSupport`: wrap blocking service calls with `blockingScheduler`, preserve `TraceContext`, return `ApiResponse`, and resolve `actorId(RbacPrincipal)`.

Expose APIs:

```text
POST /api/nutrition/clans
GET /api/nutrition/clans
POST /api/nutrition/families
GET /api/nutrition/families
POST /api/nutrition/clans/{clanId}/families/{familyId}
POST /api/nutrition/families/{familyId}/members
GET /api/nutrition/families/{familyId}/members
GET /api/nutrition/families/{familyId}/health-profiles
PUT /api/nutrition/families/{familyId}/members/{memberProfileId}/health-profile
```

- [ ] **Step 4: Run targeted tests and controller smoke compile**

Run:

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ClanFamilyServiceTests,MemberHealthServiceTests test
./mvnw -Dmaven.build.cache.enabled=false -DskipTests compile
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add be/src/main/java/top/egon/mario/nutrition/dto \
  be/src/main/java/top/egon/mario/nutrition/service/ClanFamilyService.java \
  be/src/main/java/top/egon/mario/nutrition/service/MemberHealthService.java \
  be/src/main/java/top/egon/mario/nutrition/web \
  be/src/test/java/top/egon/mario/nutrition/ClanFamilyServiceTests.java \
  be/src/test/java/top/egon/mario/nutrition/MemberHealthServiceTests.java
git commit -m "feat: add nutrition family and health services"
```

## Task 4: Standard Data, Recipes, and CSV Import

**Files:**

- Create: `be/src/main/java/top/egon/mario/nutrition/service/RecipeService.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/service/importer/NutritionImportService.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/service/importer/NutritionCsvImportTemplate.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/service/importer/StandardFoodCsvImporter.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/service/importer/FamilyRecipeCsvImporter.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/web/RecipeController.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/web/NutritionImportController.java`
- Test: `be/src/test/java/top/egon/mario/nutrition/RecipeServiceTests.java`
- Test: `be/src/test/java/top/egon/mario/nutrition/importer/NutritionImportServiceTests.java`

- [ ] **Step 1: Write failing recipe and import tests**

Write tests for:

```java
@Test
void standardFoodImportRejectsMissingNameAndReportsRowNumber()

@Test
void standardFoodImportWarnsDuplicateNameWithinCategory()

@Test
void familyRecipeImportStoresRecipesUnderFamilyOnly()

@Test
void recipeIngredientKeepsRawNameWhenStandardFoodIsMissing()
```

- [ ] **Step 2: Run tests and verify they fail**

Run:

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false -Dtest=RecipeServiceTests,NutritionImportServiceTests test
```

Expected: FAIL because recipe/import services do not exist.

- [ ] **Step 3: Implement recipe and import services**

Implement platform standard food and public recipe operations behind platform RBAC. Implement family recipe operations behind `requireReadFamily` and `requireManageFamily`.

Implement import lifecycle:

```text
create job
parse CSV rows
validate required columns
record row errors
record duplicate warnings
commit valid rows when confirmed
store job counts and final status
```

Use one shared template class for lifecycle and separate importer classes for standard food and family recipes.

Expose APIs:

```text
GET /api/nutrition/platform/standard-foods
POST /api/nutrition/platform/standard-foods
GET /api/nutrition/families/{familyId}/recipes
POST /api/nutrition/families/{familyId}/recipes
POST /api/nutrition/import-jobs
GET /api/nutrition/import-jobs/{jobId}
POST /api/nutrition/import-jobs/{jobId}/confirm
```

- [ ] **Step 4: Run targeted tests**

Run:

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false -Dtest=RecipeServiceTests,NutritionImportServiceTests test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add be/src/main/java/top/egon/mario/nutrition/service/RecipeService.java \
  be/src/main/java/top/egon/mario/nutrition/service/importer \
  be/src/main/java/top/egon/mario/nutrition/web/RecipeController.java \
  be/src/main/java/top/egon/mario/nutrition/web/NutritionImportController.java \
  be/src/test/java/top/egon/mario/nutrition/RecipeServiceTests.java \
  be/src/test/java/top/egon/mario/nutrition/importer/NutritionImportServiceTests.java
git commit -m "feat: add nutrition recipes and imports"
```

## Task 5: Nutrition Calculation and Risk Rules

**Files:**

- Create: `be/src/main/java/top/egon/mario/nutrition/service/calculation/NutritionCalculationService.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/service/rule/NutritionRuleCheckService.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/service/rule/NutritionRuleChecker.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/service/rule/AllergyRuleChecker.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/service/rule/DislikeRuleChecker.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/service/rule/DietGoalRuleChecker.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/service/rule/BudgetRuleChecker.java`
- Test: `be/src/test/java/top/egon/mario/nutrition/calculation/NutritionCalculationServiceTests.java`
- Test: `be/src/test/java/top/egon/mario/nutrition/rule/NutritionRuleCheckServiceTests.java`

- [ ] **Step 1: Write failing calculation and rule tests**

Write tests for:

```java
@Test
void calculatesNutritionByHundredGramFoodValuesAndServingAmount()

@Test
void allergyRuleCreatesHighRiskBlockingResult()

@Test
void dislikeRuleCreatesMediumRiskConfirmableResult()

@Test
void budgetRuleCreatesWarningWhenEstimatedCostExceedsMealLimit()
```

- [ ] **Step 2: Run tests and verify they fail**

Run:

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false -Dtest=NutritionCalculationServiceTests,NutritionRuleCheckServiceTests test
```

Expected: FAIL because calculation and rule services do not exist.

- [ ] **Step 3: Implement calculation and rule strategy classes**

`NutritionCalculationService` must calculate calories, protein, fat, carbs, sugar, sodium, fiber, and cholesterol from standard-food per-100g values and recipe ingredient amounts.

`NutritionRuleCheckService` must accept family, member profiles, recipes/menu items, budget context, and return normalized risk results. High allergy risk must set `blocking=true`. Dislike and diet-goal risks must set `requiresConfirmation=true`. Budget risks must never block in MVP.

- [ ] **Step 4: Run targeted tests**

Run:

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false -Dtest=NutritionCalculationServiceTests,NutritionRuleCheckServiceTests test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add be/src/main/java/top/egon/mario/nutrition/service/calculation \
  be/src/main/java/top/egon/mario/nutrition/service/rule \
  be/src/test/java/top/egon/mario/nutrition/calculation/NutritionCalculationServiceTests.java \
  be/src/test/java/top/egon/mario/nutrition/rule/NutritionRuleCheckServiceTests.java
git commit -m "feat: add nutrition calculation and risk checks"
```

## Task 6: AI Recommendation Jobs and Scheduled Generation

**Files:**

- Create: `be/src/main/java/top/egon/mario/nutrition/service/ai/NutritionAiService.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/service/ai/NutritionAiServiceImpl.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/service/ai/NutritionAiMenuDraft.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/service/ai/NutritionAiRecipeDraft.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/service/ai/NutritionAiRecommendationScheduler.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/web/AiRecommendationController.java`
- Test: `be/src/test/java/top/egon/mario/nutrition/ai/NutritionAiServiceTests.java`

- [ ] **Step 1: Write failing AI job tests**

Write tests for:

```java
@Test
void scheduledGenerationCreatesPendingReviewMealPlan()

@Test
void aiFailureStoresFailedJobWithoutPublishingMenu()

@Test
void manualRegenerationCreatesNewJobForFamilyAndDate()
```

- [ ] **Step 2: Run tests and verify they fail**

Run:

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false -Dtest=NutritionAiServiceTests test
```

Expected: FAIL because AI recommendation services do not exist.

- [ ] **Step 3: Implement AI service boundary and scheduler**

`NutritionAiServiceImpl` must reuse the existing model infrastructure only through an injectable boundary. Unit tests should use a fake model client to return deterministic JSON. Store input snapshot, raw output snapshot, normalized output, job status, and error text.

The scheduler must scan enabled families by configured generation time and create jobs. The job result must create `nutrition_ai_recommendation` and `nutrition_meal_plan` with status `PENDING_REVIEW`, never `PUBLISHED`.

Expose APIs:

```text
POST /api/nutrition/families/{familyId}/ai-recommendations/generate
GET /api/nutrition/families/{familyId}/ai-recommendations
GET /api/nutrition/families/{familyId}/ai-recommendations/{recommendationId}
```

- [ ] **Step 4: Run targeted tests**

Run:

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false -Dtest=NutritionAiServiceTests test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add be/src/main/java/top/egon/mario/nutrition/service/ai \
  be/src/main/java/top/egon/mario/nutrition/web/AiRecommendationController.java \
  be/src/test/java/top/egon/mario/nutrition/ai/NutritionAiServiceTests.java
git commit -m "feat: add nutrition ai recommendation jobs"
```

## Task 7: Meal Plan Review, Member Confirmation, and Summary

**Files:**

- Create: `be/src/main/java/top/egon/mario/nutrition/service/MealPlanService.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/service/MealConfirmationService.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/web/MealPlanController.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/web/MealConfirmationController.java`
- Test: `be/src/test/java/top/egon/mario/nutrition/MealPlanServiceTests.java`
- Test: `be/src/test/java/top/egon/mario/nutrition/MealConfirmationServiceTests.java`

- [ ] **Step 1: Write failing meal workflow tests**

Write tests for:

```java
@Test
void cookCanPublishPendingReviewMenu()

@Test
void ordinaryMemberCannotPublishMenu()

@Test
void boundUserCanConfirmOwnProfileBeforeCutoff()

@Test
void highRiskAllergyBlocksConfirmation()

@Test
void mediumRiskRequiresExplicitRiskConfirmation()

@Test
void summaryAggregatesConfirmedServingsByDish()
```

- [ ] **Step 2: Run tests and verify they fail**

Run:

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false -Dtest=MealPlanServiceTests,MealConfirmationServiceTests test
```

Expected: FAIL because meal services do not exist.

- [ ] **Step 3: Implement meal services and controllers**

`MealPlanService` must centralize state transitions:

```text
PENDING_REVIEW -> ADJUSTED
PENDING_REVIEW -> PUBLISHED
ADJUSTED -> PUBLISHED
PUBLISHED -> CONFIRMING
CONFIRMING -> CONFIRM_CLOSED
CONFIRM_CLOSED -> PREPARING
PREPARING -> COMPLETED
PUBLISHED/CONFIRMING/CONFIRM_CLOSED/PREPARING -> CANCELLED
```

`MealConfirmationService` must enforce login-bound confirmation, proxy confirmation for admin/cook/guardian, cutoff checks, and risk confirmation flags.

Expose APIs:

```text
GET /api/nutrition/families/{familyId}/meal-plans
GET /api/nutrition/families/{familyId}/meal-plans/today
POST /api/nutrition/families/{familyId}/meal-plans/{mealPlanId}/publish
POST /api/nutrition/families/{familyId}/meal-plans/{mealPlanId}/close-confirmation
POST /api/nutrition/families/{familyId}/meal-plans/{mealPlanId}/complete
POST /api/nutrition/families/{familyId}/meal-plans/{mealPlanId}/confirmations
PUT /api/nutrition/families/{familyId}/confirmations/{confirmationId}
GET /api/nutrition/families/{familyId}/meal-plans/{mealPlanId}/summary
```

- [ ] **Step 4: Run targeted tests**

Run:

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false -Dtest=MealPlanServiceTests,MealConfirmationServiceTests test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add be/src/main/java/top/egon/mario/nutrition/service/MealPlanService.java \
  be/src/main/java/top/egon/mario/nutrition/service/MealConfirmationService.java \
  be/src/main/java/top/egon/mario/nutrition/web/MealPlanController.java \
  be/src/main/java/top/egon/mario/nutrition/web/MealConfirmationController.java \
  be/src/test/java/top/egon/mario/nutrition/MealPlanServiceTests.java \
  be/src/test/java/top/egon/mario/nutrition/MealConfirmationServiceTests.java
git commit -m "feat: add nutrition meal workflow"
```

## Task 8: Shopping Lists, Price Records, and Budget Statistics

**Files:**

- Create: `be/src/main/java/top/egon/mario/nutrition/service/ShoppingListService.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/service/BudgetService.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/web/ShoppingListController.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/web/BudgetController.java`
- Test: `be/src/test/java/top/egon/mario/nutrition/ShoppingListServiceTests.java`
- Test: `be/src/test/java/top/egon/mario/nutrition/BudgetServiceTests.java`

- [ ] **Step 1: Write failing shopping and budget tests**

Write tests for:

```java
@Test
void shoppingListAggregatesIngredientsByStandardFoodAndUnit()

@Test
void priceEntryCreatesFamilyPriceRecordWithNormalizedUnitPrice()

@Test
void budgetUsesActualPriceBeforeEstimatedPrice()

@Test
void weeklyBudgetIncludesMealDailyAndPerPersonCosts()
```

- [ ] **Step 2: Run tests and verify they fail**

Run:

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ShoppingListServiceTests,BudgetServiceTests test
```

Expected: FAIL because shopping and budget services do not exist.

- [ ] **Step 3: Implement shopping and budget services**

Generate shopping lists from meal plan items, recipe ingredients, confirmation counts, and servings. Allow cook/admin updates to item amount, unit, checked flag, channel, brand, spec amount, spec unit, quantity, and total price. On price entry, create `nutrition_food_price_record` and recalculate actual total.

Budget service must return meal, daily, weekly, monthly, per-person, dish, ingredient, channel, and usage-rate summaries.

Expose APIs:

```text
POST /api/nutrition/families/{familyId}/meal-plans/{mealPlanId}/shopping-list/generate
GET /api/nutrition/families/{familyId}/shopping-lists/{shoppingListId}
PUT /api/nutrition/families/{familyId}/shopping-lists/{shoppingListId}/items/{itemId}
POST /api/nutrition/families/{familyId}/price-records
GET /api/nutrition/families/{familyId}/budget/weekly
GET /api/nutrition/families/{familyId}/budget/monthly
```

- [ ] **Step 4: Run targeted tests**

Run:

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false -Dtest=ShoppingListServiceTests,BudgetServiceTests test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add be/src/main/java/top/egon/mario/nutrition/service/ShoppingListService.java \
  be/src/main/java/top/egon/mario/nutrition/service/BudgetService.java \
  be/src/main/java/top/egon/mario/nutrition/web/ShoppingListController.java \
  be/src/main/java/top/egon/mario/nutrition/web/BudgetController.java \
  be/src/test/java/top/egon/mario/nutrition/ShoppingListServiceTests.java \
  be/src/test/java/top/egon/mario/nutrition/BudgetServiceTests.java
git commit -m "feat: add nutrition shopping and budget"
```

## Task 9: Nutrition Records and Basic Reports

**Files:**

- Create: `be/src/main/java/top/egon/mario/nutrition/service/NutritionRecordService.java`
- Create: `be/src/main/java/top/egon/mario/nutrition/web/NutritionRecordController.java`
- Test: `be/src/test/java/top/egon/mario/nutrition/NutritionRecordServiceTests.java`

- [ ] **Step 1: Write failing record/report tests**

Write tests for:

```java
@Test
void completingMealGeneratesMemberNutritionRecords()

@Test
void recordAdjustmentKeepsOriginalAndStoresCorrection()

@Test
void extraFoodRecordContributesToDailyOverview()

@Test
void familyWeeklyReportIncludesRiskCountsCostAndCommonDishes()
```

- [ ] **Step 2: Run tests and verify they fail**

Run:

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false -Dtest=NutritionRecordServiceTests test
```

Expected: FAIL because nutrition record service does not exist.

- [ ] **Step 3: Implement record generation and reports**

Generate records only when cook marks meal completed. Use menu items, confirmations, servings, recipe nutrition, and risk results. Store adjustment records instead of silently overwriting generated records. Include extra food in daily overview.

Expose APIs:

```text
GET /api/nutrition/families/{familyId}/nutrition-records/daily
POST /api/nutrition/families/{familyId}/nutrition-records/{recordId}/adjustments
POST /api/nutrition/families/{familyId}/nutrition-records/extra-foods
GET /api/nutrition/families/{familyId}/nutrition-records/reports/family-weekly
GET /api/nutrition/families/{familyId}/nutrition-records/reports/family-monthly
```

- [ ] **Step 4: Run targeted tests**

Run:

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false -Dtest=NutritionRecordServiceTests test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add be/src/main/java/top/egon/mario/nutrition/service/NutritionRecordService.java \
  be/src/main/java/top/egon/mario/nutrition/web/NutritionRecordController.java \
  be/src/test/java/top/egon/mario/nutrition/NutritionRecordServiceTests.java
git commit -m "feat: add nutrition records and reports"
```

## Task 10: Backend Controller Smoke and Module Verification

**Files:**

- Create: `be/src/test/java/top/egon/mario/nutrition/web/NutritionControllerSmokeTests.java`
- Modify: backend files from Tasks 1-9 only if tests expose integration gaps.

- [ ] **Step 1: Write controller smoke tests**

Create smoke tests that load controller methods with fake services and assert:

```java
@Test
void controllersWrapResponsesWithApiResponseAndTraceId()

@Test
void blockingSupportUsesActorIdFromRbacPrincipal()

@Test
void platformAdminApisUsePlatformNutritionPath()
```

- [ ] **Step 2: Run controller smoke tests and verify failures**

Run:

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false -Dtest=NutritionControllerSmokeTests test
```

Expected: FAIL if any controller lacks response wrapping, actor id handling, or expected paths.

- [ ] **Step 3: Fix controller integration gaps**

Apply only the changes needed for controller tests:

- controllers extend `ReactiveNutritionSupport`
- all blocking calls return `Mono<ApiResponse<T>>`
- actor id uses `RbacPrincipal.userId()`
- platform endpoints stay under `/api/nutrition/platform`
- family endpoints include `familyId`

- [ ] **Step 4: Run backend nutrition test slice**

Run:

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false -Dtest='top.egon.mario.nutrition.**' test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add be/src/main/java/top/egon/mario/nutrition \
  be/src/test/java/top/egon/mario/nutrition
git commit -m "test: verify nutrition backend module"
```

## Task 11: Frontend Foundation, Routes, Service, and Shared Components

**Files:**

- Create: `fe/src/modules/nutrition/nutritionTypes.ts`
- Create: `fe/src/modules/nutrition/nutritionPermissionCodes.ts`
- Create: `fe/src/modules/nutrition/nutritionService.ts`
- Create: `fe/src/modules/nutrition/currentFamilyStore.tsx`
- Create: `fe/src/modules/nutrition/components/CurrentFamilySelect.tsx`
- Create: `fe/src/modules/nutrition/components/RiskTag.tsx`
- Create: `fe/src/modules/nutrition/components/MoneyText.tsx`
- Create: `fe/src/modules/nutrition/components/ImportJobPanel.tsx`
- Modify: `fe/src/app/routes.tsx`
- Modify: `fe/src/app/routes.test.ts`
- Test: `fe/src/modules/nutrition/nutritionService.test.ts`
- Test: `fe/src/modules/nutrition/currentFamilyStore.test.tsx`
- Test: `fe/src/modules/nutrition/components/*.test.tsx`

- [ ] **Step 1: Write failing frontend foundation tests**

Write tests asserting:

```tsx
test('nutrition routes are registered under the protected admin layout')
test('nutritionService builds family scoped URLs')
test('CurrentFamilySelect calls onChange with the selected family id')
test('RiskTag renders blocking high risk as error')
test('MoneyText renders CNY values with two fraction digits')
test('ImportJobPanel renders failed row counts and confirm action')
```

- [ ] **Step 2: Run tests and verify they fail**

Run:

```bash
cd fe
bun run test -- routes.test.ts nutritionService.test.ts currentFamilyStore.test.tsx CurrentFamilySelect.test.tsx RiskTag.test.tsx MoneyText.test.tsx ImportJobPanel.test.tsx
```

Expected: FAIL because nutrition frontend files and routes do not exist.

- [ ] **Step 3: Implement frontend foundation**

Add typed service helpers for all backend API groups. Use existing `requestJson`, `requestFormData`, and `buildSearchParams` helpers. Add routes:

```text
nutrition/home
nutrition/families
nutrition/members
nutrition/recipes
nutrition/ai-menus
nutrition/confirmations
nutrition/meal-summary
nutrition/shopping
nutrition/budget
nutrition/records
nutrition/platform
```

Use Ant Design components already present in the repo. Do not add dependencies.

- [ ] **Step 4: Run targeted frontend tests and typecheck**

Run:

```bash
cd fe
bun run test -- routes.test.ts nutritionService.test.ts currentFamilyStore.test.tsx CurrentFamilySelect.test.tsx RiskTag.test.tsx MoneyText.test.tsx ImportJobPanel.test.tsx
bun run typecheck
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add fe/src/app/routes.tsx fe/src/app/routes.test.ts \
  fe/src/modules/nutrition
git commit -m "feat: add nutrition frontend foundation"
```

## Task 12: Frontend Nutrition Pages

**Files:**

- Create: `fe/src/modules/nutrition/NutritionHomePage.tsx`
- Create: `fe/src/modules/nutrition/ClanFamilyPage.tsx`
- Create: `fe/src/modules/nutrition/MemberHealthPage.tsx`
- Create: `fe/src/modules/nutrition/RecipeLibraryPage.tsx`
- Create: `fe/src/modules/nutrition/AiMenuPage.tsx`
- Create: `fe/src/modules/nutrition/MealConfirmationPage.tsx`
- Create: `fe/src/modules/nutrition/MealSummaryPage.tsx`
- Create: `fe/src/modules/nutrition/ShoppingListPage.tsx`
- Create: `fe/src/modules/nutrition/BudgetPage.tsx`
- Create: `fe/src/modules/nutrition/NutritionRecordPage.tsx`
- Create: `fe/src/modules/nutrition/PlatformNutritionConfigPage.tsx`
- Test: page tests listed in the frontend test section.

- [ ] **Step 1: Write failing page tests**

Write page tests asserting:

```tsx
test('home page shows pending AI menus and budget usage')
test('clan family page exposes explicit grant controls')
test('member health page lists family health profiles')
test('recipe page renders public and family recipes')
test('AI menu page separates AI suggestion, risk check, and cook adjusted menu')
test('confirmation page blocks high risk and requires confirmation for medium risk')
test('shopping page records channel price details')
test('budget page renders weekly and monthly summaries')
test('record page renders daily intake and adjustment actions')
test('platform config page is marked platform admin only')
```

- [ ] **Step 2: Run tests and verify they fail**

Run:

```bash
cd fe
bun run test -- NutritionHomePage.test.tsx ClanFamilyPage.test.tsx MemberHealthPage.test.tsx RecipeLibraryPage.test.tsx AiMenuPage.test.tsx MealConfirmationPage.test.tsx ShoppingListPage.test.tsx BudgetPage.test.tsx NutritionRecordPage.test.tsx PlatformNutritionConfigPage.test.tsx
```

Expected: FAIL because pages do not exist.

- [ ] **Step 3: Implement pages**

Implement pages with dense admin-tool layouts, not marketing layouts:

- `NutritionHomePage`: summary cards and table of pending actions.
- `ClanFamilyPage`: clan list, family list, association table, grant drawer.
- `MemberHealthPage`: member table, health profile drawer, guardian controls.
- `RecipeLibraryPage`: recipe table, ingredient mapping, import panel.
- `AiMenuPage`: job list, recommendation detail, risk panel, publish controls.
- `MealConfirmationPage`: current menu, profile selector, serving controls, risk confirmation.
- `MealSummaryPage`: dish serving summary and shopping list generation.
- `ShoppingListPage`: shopping item table, check state, price entry form.
- `BudgetPage`: budget rules and weekly/monthly cost tables.
- `NutritionRecordPage`: daily intake, adjustments, extra food, family reports.
- `PlatformNutritionConfigPage`: standard foods, tags, public recipes, import jobs.

- [ ] **Step 4: Run targeted frontend tests, typecheck, and lint**

Run:

```bash
cd fe
bun run test -- NutritionHomePage.test.tsx ClanFamilyPage.test.tsx MemberHealthPage.test.tsx RecipeLibraryPage.test.tsx AiMenuPage.test.tsx MealConfirmationPage.test.tsx ShoppingListPage.test.tsx BudgetPage.test.tsx NutritionRecordPage.test.tsx PlatformNutritionConfigPage.test.tsx
bun run typecheck
bun run lint
```

Expected: PASS or report only unrelated pre-existing lint failures with exact file names.

- [ ] **Step 5: Commit**

```bash
git add fe/src/modules/nutrition fe/src/app/routes.tsx fe/src/app/routes.test.ts
git commit -m "feat: add nutrition frontend pages"
```

## Task 13: End-to-End Verification and Documentation

**Files:**

- Modify: `FEATURE_CHECKLIST.md`
- Modify: `README.md` only if the top-level feature summary needs one nutrition bullet.
- Modify: `docs/superpowers/specs/2026-06-30-family-ai-nutrition-mvp-design.md` only if implementation exposes a design correction approved by the user.
- Test/verification only: backend and frontend commands below.

- [ ] **Step 1: Run backend nutrition slice**

Run:

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false -Dtest='top.egon.mario.nutrition.**' test
```

Expected: PASS.

- [ ] **Step 2: Run backend full tests**

Run:

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false test
```

Expected: PASS. If it fails outside `top.egon.mario.nutrition`, record the failing classes and rerun the nutrition slice to prove this feature's boundary.

- [ ] **Step 3: Run frontend checks**

Run:

```bash
cd fe
bun run typecheck
bun run test
bun run lint
bun run build
```

Expected: PASS. If lint has pre-existing unrelated failures, record exact files and keep nutrition checks green.

- [ ] **Step 4: Update documentation**

Update `FEATURE_CHECKLIST.md` with a concise nutrition MVP section covering:

```text
Nutrition Management
- clan/family/member profile model
- scoped authorization and explicit grants
- health profiles and family visibility
- recipe and standard food data
- CSV import jobs
- AI menu recommendations with cook review
- meal confirmation and summary
- shopping list and price records
- budget statistics
- nutrition records and basic reports
```

Add one README feature bullet only if the README feature list remains accurate and concise.

- [ ] **Step 5: Final status and commit**

Run:

```bash
git status --short
git diff --check
```

Expected: only intended docs/test/source changes before commit, and whitespace check PASS.

Commit:

```bash
git add FEATURE_CHECKLIST.md
git diff --quiet -- README.md || git add README.md
git diff --quiet -- docs/superpowers/specs/2026-06-30-family-ai-nutrition-mvp-design.md || git add docs/superpowers/specs/2026-06-30-family-ai-nutrition-mvp-design.md
git commit -m "docs: document nutrition mvp"
```

## Execution Handoff

Recommended execution is subagent-driven with a fresh worker per task and a main-agent review after each returned result. Do not reuse subagents across tasks. Each task has a commit boundary. Do not start the project automatically.

If execution starts from this plan, first create an isolated worktree or branch using the required execution skill for the chosen mode, then run Task 1.
