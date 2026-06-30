# Family AI Nutrition MVP Design

Date: 2026-06-30
Status: Approved design direction, pending written spec review
Author: design session

## 1. Background

This document designs a complete MVP for a family AI ordering, recipe, and
nutrition management system inside the existing CyberMario repository.

The source PRD defines a multi-family SaaS product for daily menu decisions,
AI menu recommendation, member meal confirmation, professional nutrition
management, recipe accumulation, shopping lists, and household food budgeting.
The confirmed implementation target is the current CyberMario codebase, not a
new repository.

The MVP is intentionally a complete product slice, but it must still be
implemented in smaller plans. This spec is the product and architecture
blueprint. Implementation should be split by domain and workflow after this
design is reviewed.

## 2. Goals

1. Add a new `nutrition` business domain to CyberMario.
2. Support users, clans, families, and family-scoped member profiles.
3. Treat family as the strong business isolation boundary for menus, ordering,
   shopping, budget, recipes, and nutrition records.
4. Support clan-family many-to-many organization relationships without granting
   family data access by default.
5. Use CyberMario RBAC for module/API entry permissions and nutrition-scoped
   role bindings for resource data access.
6. Support local standard food data, recipe data, health tags, CSV import, and
   platform/family correction workflows.
7. Generate daily AI menu recommendations by family on a schedule, then require
   cook review before publishing.
8. Run allergy, dislike, diet-goal, nutrition, and budget checks in backend
   rules. AI can suggest and explain, but cannot be the source of truth for
   safety decisions.
9. Support logged-in member meal confirmation, guardian/cook/admin proxy
   confirmation, shopping list generation, price recording, basic budget
   statistics, meal completion, nutrition record generation, and basic reports.

## 3. Non-Goals

- No standalone mobile app, WeChat mini program, or anonymous confirmation link
  in MVP.
- No external nutrition API integration in MVP. USDA, Open Food Facts, and
  other sources can be imported later through controlled pipelines.
- No medical diagnosis. Special population guidance must be marked as auxiliary
  nutrition advice.
- No complex rule engine such as Drools for MVP nutrition checks.
- No lowest-price shopping recommendation or procurement optimization.
- No refrigerator inventory, receipt OCR, barcode scanning, hardware device
  integration, or body-check report recognition.
- No direct coupling to Agent Chat, RAG, IM, or Clocktower business models.

## 4. Repository Integration

Backend module:

```text
be/src/main/java/top/egon/mario/nutrition
  config
  dto/request
  dto/response
  po
  po/enums
  repository
  service
  service/impl
  service/access
  service/ai
  service/bootstrap
  service/calculation
  service/importer
  service/rule
  web
```

Frontend module:

```text
fe/src/modules/nutrition
```

The module reuses:

- Spring Boot WebFlux backend and existing blocking isolation conventions.
- JPA repositories and Flyway schema management.
- `ApiResponse<T>` response envelope and existing frontend request helpers.
- CyberMario RBAC resource synchronization.
- Existing model factory and model-call audit infrastructure.
- React Router, TypeScript, Vite, Ant Design, `PageToolbar`, `StatusTag`, and
  shared frontend state/error patterns.

The module must not reuse Agent Chat as the business interface. Nutrition AI is
implemented as a dedicated service around the existing model infrastructure.

## 5. Domain Model

The organization model has four primary concepts:

```text
User login account
  can bind to
MemberProfile family-scoped eating profile
  belongs to one
Family strong business isolation unit
  many-to-many with
Clan organization container
```

### 5.1 User

`User` is the existing CyberMario login account from RBAC. It handles
authentication, platform-level RBAC, current user identity, and model-call audit
attribution.

### 5.2 Clan

`Clan` represents a broader organization, such as a family clan. A clan can
associate with multiple families. A clan member does not automatically gain
access to associated family business data.

Representative table: `nutrition_clan`

Important fields:

- `id`
- `name`
- `owner_user_id`
- `status`
- `created_at`
- `updated_at`

### 5.3 Family

`Family` is the core data boundary. Menus, confirmations, shopping lists,
budgets, recipes, and nutrition records bind to `family_id`.

Representative table: `nutrition_family`

Important fields:

- `id`
- `name`
- `owner_user_id`
- `region`
- `currency`
- `default_meal_types`
- `ai_enabled`
- `ai_generate_time`
- `health_alert_enabled`
- `budget_enabled`
- `status`
- `created_at`
- `updated_at`

### 5.4 Clan-Family Relationship

A family can join multiple clans, and a clan can contain multiple families.
This relationship is organizational only.

Representative table: `nutrition_clan_family`

Important fields:

- `id`
- `clan_id`
- `family_id`
- `relation_status`
- `created_by`
- `created_at`

The relationship must not be used as a shortcut for reading family menus,
health data, budget data, or reports.

### 5.5 Member Profile

`MemberProfile` is the actual eating member in a family. It is family-scoped and
can optionally bind to an existing login user. Children, elders, and managed
members can exist without login accounts.

Representative table: `nutrition_member_profile`

Important fields:

- `id`
- `family_id`
- `bound_user_id`
- `nickname`
- `gender`
- `birth_date`
- `height`
- `weight`
- `member_type`
- `login_enabled`
- `guardian_member_id`
- `status`
- `created_at`
- `updated_at`

The same login user can have separate member profiles in different families.
Health data does not automatically sync across families.

### 5.6 Health Profile

`HealthProfile` stores member-specific nutrition and risk data.

Representative table: `nutrition_health_profile`

Important fields:

- `id`
- `family_id`
- `member_profile_id`
- `activity_level`
- `diet_goals`
- `allergy_tags`
- `dislike_tags`
- `restriction_tags`
- `target_calories`
- `target_protein`
- `target_fat`
- `target_carbs`
- `target_sodium`
- `target_sugar`
- `visibility_config`
- `created_at`
- `updated_at`

MVP visibility rule: family members can view all health profiles in the same
family. The data model keeps `visibility_config` so future field-level privacy
can be added without rewriting the core schema.

## 6. Permissions

Permissions are split into two layers.

### 6.1 CyberMario RBAC

CyberMario RBAC controls module entry, menus, buttons, and API-level access.
The nutrition module should provide a `NutritionRbacResourceProvider`.

Suggested platform roles:

- `NUTRITION_USER`
- `NUTRITION_PLATFORM_ADMIN`

Suggested menu permissions:

- `menu:nutrition`
- `menu:nutrition:home`
- `menu:nutrition:families`
- `menu:nutrition:members`
- `menu:nutrition:recipes`
- `menu:nutrition:ai-menus`
- `menu:nutrition:confirmations`
- `menu:nutrition:shopping`
- `menu:nutrition:budget`
- `menu:nutrition:records`
- `menu:nutrition:platform`

Suggested API namespace:

- `api:nutrition:*`

Platform admin permissions do not imply unrestricted access to family health,
budget, menu, or nutrition-record data. Platform admins manage platform
standard data, import jobs, public recipes, dictionaries, AI strategy settings,
and aggregated/desensitized operations views. Any support/debug access to
family-sensitive data must require an explicit audited path and must not be the
default platform-admin behavior.

### 6.2 Nutrition Scoped Permissions

Nutrition data access is enforced by business-scoped role bindings and explicit
grants.

Representative tables:

- `nutrition_scoped_role_binding`
- `nutrition_data_grant`

Suggested scoped roles:

- Clan: `CLAN_ADMIN`, `CLAN_MEMBER`
- Family: `FAMILY_ADMIN`, `COOK`, `MEMBER`, `GUARDIAN`
- Member profile: `PROFILE_OWNER`, `PROFILE_GUARDIAN`

`nutrition_scoped_role_binding` fields:

- `id`
- `subject_type`
- `subject_id`
- `role_code`
- `scope_type`
- `scope_id`
- `created_by`
- `created_at`

`nutrition_data_grant` fields:

- `id`
- `family_id`
- `grantee_type`
- `grantee_id`
- `data_scope`
- `permission_level`
- `created_by`
- `created_at`
- `expires_at`

Access rules:

1. A user must pass CyberMario RBAC before calling a nutrition API.
2. Any request scoped to `family_id`, `clan_id`, or `member_profile_id` must
   pass `NutritionAccessService`.
3. Family admins can manage members, health profiles, family recipes, menus,
   budgets, and grants.
4. Cooks can review and publish menus, view summaries, manage shopping lists,
   record prices, and complete meals.
5. Members can view family menus and health profiles, confirm their bound
   profile, and view their nutrition records.
6. Guardians can manage and confirm authorized member profiles.
7. Clan-family relationships do not grant data access. Access requires explicit
   grants.

This follows enterprise RBAC practice: global RBAC for capabilities, scoped role
bindings for organization/resource roles, and data grants for cross-scope
access.

## 7. Standard Data, Recipes, and Import

MVP standard data is local and importable.

### 7.1 Standard Food

Representative table: `nutrition_standard_food`

Important fields:

- `id`
- `name_cn`
- `name_en`
- `aliases`
- `category`
- `external_source`
- `external_food_id`
- `calories_per_100g`
- `protein_per_100g`
- `fat_per_100g`
- `carbs_per_100g`
- `sugar_per_100g`
- `sodium_per_100g`
- `fiber_per_100g`
- `cholesterol_per_100g`
- `purine_level`
- `gi_value`
- `allergen_tags`
- `data_quality`
- `updated_at`

### 7.2 Tags and Dictionaries

Health and recipe tags should be platform-managed dictionaries:

- diet goals
- allergy tags
- dislike tags
- restriction tags
- chronic-condition nutrition tags
- suitable population tags
- meal types
- food categories

### 7.3 Recipes

Representative tables:

- `nutrition_recipe`
- `nutrition_recipe_ingredient`
- `nutrition_recipe_step`

Recipe source types:

- platform public recipe
- family private recipe
- AI generated recipe

Recipe ingredients should reference `nutrition_standard_food` when possible.
When a standard food cannot be matched, the ingredient keeps the raw name and a
mapping status.

### 7.4 CSV Import

Both platform admins and family admins can import data.

Platform import types:

- standard foods
- public recipes
- health tags
- allergy tags
- dislike tags
- diet goals

Family import types:

- private recipes
- family ingredient mappings
- historical purchase prices

Representative tables:

- `nutrition_import_job`
- `nutrition_import_error`

Import flow:

```text
upload CSV
-> parse
-> field validation
-> business validation
-> duplicate detection
-> error/warning preview
-> confirm import
-> write data
-> record import result
```

Duplicate rules:

- Standard food: `name_cn + category` or external source id.
- Public recipe: `source_type + name + category`.
- Family private recipe: `family_id + name`.
- Price records: no hard dedupe, but warn on same day, same channel, same food.

Design pattern: CSV import uses a lightweight Template Method. The parse,
validate, preview, commit, and job-recording lifecycle is shared, while each
import type owns its row mapping and business validation.

## 8. AI Recommendation and Rule Checks

AI menu generation runs per family.

### 8.1 Scheduling

Each family can enable AI recommendation and configure a default generation
time. A backend scheduler scans enabled families and creates recommendation
jobs. Manual regeneration is also supported.

AI output never publishes directly to members. It creates a recommendation and
a pending-review meal plan.

### 8.2 Recommendation Flow

```text
scheduled/manual trigger
-> create recommendation job
-> build family input snapshot
-> apply candidate constraints
-> call NutritionAiService
-> normalize structured output
-> run nutrition and risk checks
-> persist recommendation and pending meal plan
-> cook reviews, adjusts, and publishes
```

Representative tables:

- `nutrition_ai_recommendation_job`
- `nutrition_ai_recommendation`
- `nutrition_meal_plan`
- `nutrition_meal_plan_item`
- `nutrition_risk_check_result`

### 8.3 Nutrition AI Service

`NutritionAiService` wraps existing model infrastructure for nutrition-specific
tasks:

- AI recipe generation
- AI menu candidate generation
- recommendation reasons
- replacement suggestions
- cook-facing adjustment suggestions
- structured output normalization
- model-call audit context

The service stores input and output snapshots for audit and replay. It does not
own final safety decisions.

### 8.4 Rules

MVP rule checks:

- Allergy: high risk, hard filter, blocks member confirmation.
- Dislike: medium risk, avoid when possible, allow with confirmation.
- Diet goal: medium or low risk, warn and suggest alternatives.
- Budget: warn or downgrade ranking when above configured limit.
- Recent repetition: lower score, not a hard block.
- Children, elders, and restricted populations: fixed MVP rule set.

Design pattern: rule checks use a lightweight Strategy plus Specification
structure. Each checker is independently testable and returns a normalized risk
result. The orchestration service combines results. This avoids a large
conditional method while avoiding a heavy external rule engine.

## 9. Meal Plan, Confirmation, and Summary

### 9.1 Meal Plan Status

Suggested status model:

```text
DRAFT_AI
PENDING_REVIEW
ADJUSTED
PUBLISHED
CONFIRMING
CONFIRM_CLOSED
PREPARING
COMPLETED
CANCELLED
```

`PUBLISHED` and `CONFIRMING` can be represented by timestamps in the
implementation, but the business states should remain explicit in the design
and tests.

### 9.2 Cook Review

Cooks can:

- view AI recommendation details
- view nutrition analysis
- view risk results
- view budget estimates
- replace dishes
- remove dishes
- add dishes
- adjust serving count
- publish the menu

### 9.3 Member Confirmation

Confirmation requires login.

Rules:

1. A bound user can confirm their own member profile.
2. Family admins, cooks, and guardians can proxy-confirm authorized profiles.
3. Members can confirm whether they eat at home, selected meal types, selected
   dishes, servings, and remarks.
4. Confirmation can be edited before the cutoff.
5. High-risk allergy conflicts block submission by default.
6. Medium and low risks allow submission only when risk confirmation is
   recorded.

Representative tables:

- `nutrition_meal_confirmation`
- `nutrition_meal_confirmation_item`
- `nutrition_meal_operation_log`

### 9.4 Meal Summary

The summary view outputs:

- confirmed member count
- away-from-home count
- dish serving totals
- member remarks
- high and medium risk counts
- expected ingredient entry point
- shopping list generation readiness

State transitions should be centralized in `MealPlanService`. MVP does not need
a separate state-machine framework.

## 10. Shopping List, Price Records, and Budget

Shopping lists are generated from published or confirmed meal plans. They use
menu items, recipe ingredients, member confirmations, servings, and cook
adjustments.

Representative tables:

- `nutrition_shopping_list`
- `nutrition_shopping_list_item`
- `nutrition_food_price_record`
- `nutrition_budget_rule`
- `nutrition_budget_snapshot`

Shopping list status:

```text
DRAFT
ACTIVE
PURCHASING
PURCHASED
CLOSED
CANCELLED
```

Price recording rules:

- Shopping list items can record channel, brand, spec amount, spec unit,
  purchase quantity, total price, and note.
- The system calculates normalized unit price while preserving the original
  spec.
- Recording actual price creates a family-level `FoodPriceRecord`.
- The same food can have multiple channel, brand, and date records.
- Budget statistics prefer actual price. If actual price is missing, the
  system may use recent price or estimate and mark the value as estimated.

MVP budget output:

- meal cost
- daily cost
- weekly cost
- monthly cost
- per-person cost
- dish and ingredient basic cost
- channel spending summary
- budget usage rate

Lowest-price channel recommendation and price optimization are outside MVP.

## 11. Nutrition Records and Reports

Nutrition records are generated when a cook marks a meal or meal plan completed.
They are not generated at confirmation time.

Inputs:

- meal plan items
- member confirmations
- servings
- recipe nutrition data
- actual adjustments
- risk check results

Representative tables:

- `nutrition_record`
- `nutrition_record_adjustment`
- `nutrition_extra_food_record`
- `nutrition_report_snapshot`

`nutrition_report_snapshot` is a reserved persistence shape for later
precomputed reports. MVP can generate reports through query aggregation first
and add snapshot writes only if the implementation plan needs them for
performance.

Nutrition metrics:

- calories
- protein
- fat
- carbs
- sugar
- sodium
- fiber
- cholesterol
- allergy/dislike/goal risk tags

MVP reports:

- personal daily intake overview
- family weekly and monthly basic statistics
- risk count statistics
- cost and per-person cost
- commonly eaten dishes
- high-salt, high-sugar, high-oil basic reminders
- simple trend data

Members can later correct actual intake and add extra foods. Corrections should
be recorded in adjustment records rather than overwriting the original generated
record silently.

`NutritionCalculationService` owns nutrition math and explainable calculation
details. AI can generate explanatory text, but it cannot create final nutrition
records directly.

## 12. Frontend Information Architecture

The frontend adds a top-level "Nutrition Management" menu.

Suggested pages:

1. Home: today's pending menus, pending confirmations, risks, shopping list,
   cost, and budget usage.
2. Clans and families: clan list, family list, clan-family association, grants.
3. Member health: member profiles, health profiles, diet goals, allergies,
   dislikes, restrictions, guardian relationships.
4. Recipe library: public recipes, family recipes, AI recipes, ingredient
   mapping, nutrition summaries.
5. AI menus: jobs, recommendations, pending review, regeneration, adjustment,
   publishing.
6. Member confirmation: today's menu, serving adjustment, risk confirmation,
   proxy confirmation.
7. Meal summary: confirmed counts, dish servings, remarks, risks, shopping list
   generation.
8. Shopping list: ingredient list, purchase status, price entry, channels.
9. Budget: budget rules, weekly/monthly cost, per-person cost, price history.
10. Nutrition records: daily records, corrections, extra foods, basic reports.
11. Platform configuration: standard foods, tags, public recipes, import jobs.

Frontend behavior:

- Business pages use a current-family selector.
- Platform configuration is visible only to platform admins.
- Member confirmation must work acceptably on mobile browsers, but remains a
  responsive web page.
- AI pages must distinguish original AI suggestions, backend rule check results,
  and cook-adjusted final menu.
- Import pages support upload, validation preview, confirmation, and import job
  history.
- Risk levels should be visible and consistent. High risk blocks, medium/low
  risk allows explicit confirmation.

## 13. API Shape

All nutrition APIs should live under `/api/nutrition`.

Suggested groups:

- `/api/nutrition/clans`
- `/api/nutrition/families`
- `/api/nutrition/families/{familyId}/members`
- `/api/nutrition/families/{familyId}/health-profiles`
- `/api/nutrition/families/{familyId}/recipes`
- `/api/nutrition/import-jobs`
- `/api/nutrition/families/{familyId}/ai-recommendations`
- `/api/nutrition/families/{familyId}/meal-plans`
- `/api/nutrition/families/{familyId}/confirmations`
- `/api/nutrition/families/{familyId}/meal-summaries`
- `/api/nutrition/families/{familyId}/shopping-lists`
- `/api/nutrition/families/{familyId}/budget`
- `/api/nutrition/families/{familyId}/nutrition-records`
- `/api/nutrition/platform/standard-foods`
- `/api/nutrition/platform/tags`
- `/api/nutrition/platform/recipes`

Controllers should stay thin. They identify the current user, accept requests,
and delegate to services. Services must enforce data access through
`NutritionAccessService`.

## 14. Persistence and Migration

Flyway migration files are immutable. Implementation must add exactly one new
versioned migration for the initial nutrition MVP schema unless the user
explicitly requests a different migration split.

At design time, the latest observed migration is:

```text
be/src/main/resources/db/migration/V30__create_im_core_schema.sql
```

Before implementation, re-check the migration directory and choose the next
version according to the current repository state.

All nutrition business tables should include the current project's standard
audit shape where appropriate:

- `created_at`
- `updated_at`
- `created_by`
- `updated_by`
- `version`
- `deleted`

Large JSON snapshots are acceptable for AI input/output and risk summary audit
data, but core query fields such as `family_id`, status, date, member id, and
recipe id must remain relational and indexed.

## 15. Validation Plan

Backend validation should include:

- access tests for family, clan, member profile, and explicit grants
- role-binding tests for admin, cook, member, guardian, and clan roles
- rule tests for allergy hard block, dislike confirmation, diet-goal warning,
  and budget warning
- AI job state tests for success, failure, retry/manual regeneration, and
  pending-review result creation
- menu state tests for draft, review, publish, confirm, close, complete, cancel
- import tests for valid rows, invalid rows, duplicate rows, platform scope,
  and family scope
- shopping and budget tests for unit-price conversion and aggregation
- nutrition record tests for completion generation, adjustment, and extra food

Frontend validation should include:

- route and menu permission visibility
- current-family selector behavior
- AI menu review and publish form states
- member confirmation risk-block and risk-confirm states
- import upload/preview/confirm states
- shopping price entry and budget display states

Final implementation verification should prefer targeted tests first, then
broader backend/frontend checks. Do not start local runtime servers unless the
user explicitly asks.

## 16. Design Pattern Decisions

Patterns selected:

- Scoped Role Binding plus data grant service for enterprise-style multi-scope
  authorization.
- Template Method for CSV import lifecycle.
- Strategy plus Specification for nutrition risk checks.
- Service-layer state transition checks for meal plan and shopping list states.

Patterns intentionally not selected:

- Global RBAC-only authorization, because it cannot express one user's
  different roles across different families.
- Full policy-expression engine, because MVP scopes and grants are fixed enough
  for direct service checks.
- Drools or another external rule engine, because MVP nutrition rules are
  bounded and should stay easy to test.
- Microservices, because the current repository is a modular monolith and the
  product is not ready for distributed ownership.
- A formal state-machine framework, because menu and shopping states are clear
  enough for centralized service validation.

## 17. Implementation Decomposition Guidance

This design is a complete MVP blueprint, not a single implementation task.
The implementation plan should split the work into small, reviewable tasks such
as:

1. Nutrition module skeleton, migration, RBAC resources, and access service.
2. Clan, family, member profile, and health profile management.
3. Standard data, recipe model, and CSV import foundation.
4. Nutrition calculation and risk check services.
5. AI recommendation job model and `NutritionAiService`.
6. Meal plan review, publish, confirmation, and summary.
7. Shopping list, price records, and budget statistics.
8. Nutrition record generation and basic reports.
9. Frontend nutrition routes and page slices.
10. End-to-end validation and documentation updates.

Each implementation task should have its own tests and commit boundary.
