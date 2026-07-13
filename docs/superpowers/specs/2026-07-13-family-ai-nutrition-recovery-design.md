# Family AI Nutrition Recovery Design

Date: 2026-07-13
Status: Proposed recovery design, pending written review
Author: recovery design session

## 1. Decision Summary

The approved Family AI Nutrition design remains the product source of truth.
The current implementation is not replaced wholesale. Its schema, entities,
repositories, access foundation, calculation strategies, CSV import template,
and service-layer state transitions are retained where they already match the
approved design.

Recovery work is organized as vertical business slices. Every slice must end in
a usable workflow with backend integration tests and a live frontend path. A
task is not complete merely because a table, repository, endpoint, or page
exists.

The recovery must restore these product invariants:

1. `Family` is the strong data boundary.
2. Platform RBAC controls entry and coarse capabilities; nutrition-scoped roles
   and explicit grants control business data.
3. AI proposes menus but never decides safety or publishes automatically.
4. Backend nutrition calculations and deterministic rules are the source of
   truth.
5. AI-generated menu items must become real, calculable recipes before publish.
6. Member confirmation is per dish and per serving, not only per meal type.
7. Shopping, budget, and nutrition records consume the same reviewed and
   confirmed meal data.
8. Frontend pages use live APIs; fixture data is test-only.

## 2. Baseline and Source of Truth

The recovery is governed by:

- `docs/superpowers/specs/2026-06-30-family-ai-nutrition-mvp-design.md`
- this recovery design, which narrows implementation choices and closes gaps
  discovered in the current code
- the current repository conventions for WebFlux controllers, blocking JPA
  isolation, `ApiResponse<T>`, RBAC providers, Flyway, React, TypeScript, and
  Ant Design

The existing implementation plan at
`docs/superpowers/plans/2026-06-30-family-ai-nutrition-mvp.md` is historical
evidence, not an execution plan for the recovery. It decomposed work by
technical layer and omitted several integration responsibilities. A new plan
must be written after this design is approved.

## 3. Goals

1. Restore the complete approved nutrition MVP without discarding valid current
   code.
2. Make family, role, grant, health, recipe, AI, meal, confirmation, shopping,
   budget, record, and report capabilities operable through APIs and frontend
   pages.
3. Make an AI-generated meal plan flow all the way to a non-empty shopping list
   and non-zero nutrition records when its recipes contain mapped ingredients.
4. Enforce safety, family isolation, role scope, confirmation cutoff, and state
   transitions at the backend.
5. Replace presence-based acceptance with workflow-based acceptance.
6. Preserve existing nutrition data and compatibility wherever possible.

## 4. Non-Goals

- No microservice split.
- No new frontend framework or state-management dependency.
- No Drools or external rule-engine dependency.
- No formal state-machine framework.
- No external nutrition-data API.
- No inventory, OCR, barcode, payment, procurement optimization, or medical
  diagnosis.
- No anonymous meal confirmation.
- No unrelated refactoring of RBAC, Agent, RAG, IM, or Clocktower modules.
- No modification of `V31__create_nutrition_mvp_schema.sql` or any other
  existing Flyway migration.

## 5. Approaches Considered

### 5.1 Vertical business-flow recovery - selected

Each delivery slice completes a user-visible workflow and its data handoffs.
For example, the AI slice is not complete until health context, recipe
materialization, deterministic rules, cook review, and publish gates work
together.

Advantages:

- directly prevents another collection of disconnected components
- produces useful software after each slice
- creates meaningful integration-test boundaries
- reuses most of the current module

Trade-off: some existing services must be extended across package boundaries in
the same task, so task scopes must be defined by workflow rather than by layer.

### 5.2 Complete every backend domain before connecting workflows - rejected

This would fill every repository and endpoint first, then integrate later. It
looks orderly but repeats the failure mode of the current implementation:
individual domains can pass tests while their data contracts remain
incompatible.

### 5.3 Rewrite the nutrition module - rejected

The existing schema and several service foundations are usable. Rewriting would
increase migration risk, discard tested concurrency and access behavior, and
create unnecessary churn without solving the acceptance problem.

## 6. Recovery Architecture

The module remains a modular-monolith domain under
`top.egon.mario.nutrition`. Controllers stay thin. JPA work stays on the
existing blocking scheduler. Services enforce family scope before accessing
business data.

The main runtime flow becomes:

```text
family settings + members + health + recipes + budget rules
-> recommendation context assembly
-> AI structured candidate generation
-> recipe validation/materialization
-> backend nutrition calculation
-> deterministic risk and budget checks
-> pending-review meal plan
-> cook edits and revalidation
-> publish gate
-> per-member dish and serving confirmation
-> confirmed meal summary
-> shopping list and price records
-> meal completion
-> member nutrition records and reports
```

### 6.1 Existing components to retain

- `NutritionAccessService` as the central scoped-access boundary
- `NutritionCsvImportTemplate` as the import lifecycle Template Method
- `NutritionRuleChecker` strategies and `NutritionRuleCheckService`
- `NutritionCalculationService` as the only final nutrition calculator
- `MealPlanService` as the owner of meal-plan state transitions
- existing PO and repository types backed by V31
- `DefaultNutritionAiModelClient` as the model-infrastructure adapter
- existing WebFlux blocking support and response envelope

### 6.2 Focused components to add or separate

The current `NutritionAiServiceImpl`, `ShoppingListService`, and
`NutritionRecordService` are already large. New responsibilities must not be
added as long private-method chains.

Add focused services only where there is a real independent responsibility:

- `NutritionRecommendationContextService`: loads and serializes the complete,
  family-scoped recommendation input.
- `NutritionAiRecipeMaterializationService`: validates AI output, resolves
  existing recipes, creates AI-generated recipe drafts, maps standard foods,
  and returns real recipe ids.
- `NutritionMealValidationService`: calculates menu nutrition, executes rule
  strategies, persists risk results, and produces publish-gate status.
- `NutritionUnitConversionService`: converts supported mass units and explicit
  per-food unit conversions into grams.
- `NutritionHomeQueryService`: returns today's live pending menus,
  confirmations, risks, shopping state, cost, and budget usage without putting
  dashboard aggregation into write services.

These are domain/application services, not new architectural layers. A generic
workflow framework, event bus, factory hierarchy, or handler chain is not
introduced.

## 7. Organization, Roles, and Grants

### 7.1 Role responsibilities

| Role | Allowed scope |
| --- | --- |
| `FAMILY_ADMIN` | family settings, members, health, recipes, roles, grants, menus, budget, records |
| `COOK` | menu review, validation, publish, confirmation close, shopping, prices, preparation, completion |
| `MEMBER` | family menu read, own confirmation, own records, permitted family-health read |
| `GUARDIAN` | confirmation and record actions for explicitly authorized profiles |
| `PROFILE_OWNER` | own profile confirmation and own nutrition-record correction |
| `PROFILE_GUARDIAN` | authorized profile confirmation and correction |
| `CLAN_ADMIN` | clan membership and organization; family data only through an explicit grant |
| `CLAN_MEMBER` | clan read; no implicit family data |

The family owner remains an implicit family administrator but is also stored as
an active `FAMILY_ADMIN` binding. The last active family administrator cannot be
removed.

### 7.2 Operational APIs

The recovery adds family-scoped APIs for:

- reading and updating family AI, meal, health-alert, and budget settings
- listing, creating, updating, and revoking scoped role bindings
- listing, creating, updating, expiring, and revoking data grants
- listing clan-family relationships and removing an association
- binding and unbinding login users from member profiles
- establishing and revoking guardian relationships

Role bindings express operational responsibility. Data grants express
cross-scope access. A grant must not silently create a cook, administrator, or
guardian role.

### 7.3 Grant semantics

- `READ` permits scoped query operations only.
- `WRITE` permits scoped content changes but not role or grant administration.
- `MANAGE` permits scoped administration for the granted data scope, not family
  ownership transfer.
- `expires_at` is enforced on every grant check.
- revoked and expired grants are excluded from access checks and visible in
  audit history.
- platform administrators do not receive family-sensitive access by default.

The access service must expose matching read, write, and manage checks instead
of treating all grant levels as read-only.

## 8. Family, Member, and Health Management

Family settings must expose and persist:

- region and currency
- default meal types
- AI enabled flag and generation time
- health-alert enabled flag
- budget enabled flag

Member management must support create, read, update, deactivate, bind user,
unbind user, and guardian assignment. User binding must validate that the
target user exists and that duplicate active bindings inside one family are not
created accidentally.

Health profiles retain the existing target nutrients and tag collections.
Health tag values must reference active platform dictionaries. Existing raw
values remain readable for compatibility, but new writes reject unknown tag
codes.

Family members may read the family-wide health view defined by the approved MVP.
Only family administrators and authorized profile owners/guardians may update a
profile. Profile owners and guardians may not edit another unrelated profile.

## 9. Standard Foods, Health Tags, Recipes, and Import

### 9.1 Standard food data

Create, update, query, deactivate, and import operations must cover the full V31
shape:

- names, aliases, category, and external source identifiers
- calories, protein, fat, carbs, sugar, sodium, fiber, and cholesterol
- purine level and GI value
- allergen and suitable-population tags
- data quality and status

Family users receive a read-only search endpoint for active standard foods so
recipe editors can choose deterministic food ids. Platform mutation remains
restricted to platform administrators.

### 9.2 Recipes

The recipe library exposes platform-public, family-private, and AI-generated
recipes visible to the current family. Recipe create/update responses include:

- base information and serving count
- ingredients and mapping state
- cooking steps
- calculated nutrition snapshot
- allergen and suitable-population tags
- estimated cost when price data exists

Public recipes have `family_id = null`. Family-private and AI-generated recipes
must belong to the current family. Every recipe id accepted by another service
is revalidated against these visibility rules.

Required ingredients may remain unmapped while a recipe is being edited, but a
meal plan containing that recipe cannot be published until every required
ingredient has a valid standard-food mapping and a supported conversion to
grams. Optional unmapped ingredients produce a warning and remain in the audit
snapshot.

### 9.3 Unit conversion

The deterministic calculator supports `mg`, `g`, and `kg` directly. Piece,
spoon, cup, and volume units require an explicit grams-per-unit conversion in
the ingredient or standard-food metadata. Missing conversions never default to
zero silently; they create a validation result that blocks publish for required
ingredients.

### 9.4 Import coverage

The existing import Template Method is extended to all declared MVP types:

- standard foods
- public recipes
- health tags
- allergy tags
- dislike tags
- diet goals
- private family recipes
- family ingredient mappings
- historical purchase prices

Every importer supports parse, validation, duplicate detection, preview,
confirmation, transactional write, failure recording, and idempotent status
handling. Platform imports require platform administration. Family imports
require the correct family scope and never use the platform role as an implicit
family-data bypass.

## 10. AI Recommendation Workflow

### 10.1 Job lifecycle

Manual and scheduled generation use the same persisted job lifecycle:

```text
PENDING -> RUNNING -> SUCCEEDED
                   -> FAILED
```

Creating a manual job returns the persisted job promptly. A local database
runner claims pending jobs with locking and invokes the model outside the claim
transaction. Scheduled scans create at most one active scheduled job per family
and planned date. A failed job is not retried every minute; retry is explicit or
uses a bounded retry policy recorded in metadata.

No external queue dependency is added for the MVP.

### 10.2 Recommendation input snapshot

`NutritionRecommendationContextService` builds an immutable snapshot containing:

- family settings, region, currency, date, and requested meal types
- active members and member types
- activity level, diet goals, allergies, dislikes, restrictions, and nutrient
  targets
- visible public and family recipes with recipe ids and nutrition summaries
- active standard-food and health-tag codes needed by the prompt
- recent meal-plan repetition data
- applicable budget rules and recent price-derived estimates
- actor, trigger type, and model audit correlation data

The snapshot is family-scoped before model invocation and stored with the job.
Health and access decisions are never delegated to the model.

### 10.3 Structured AI output

Each AI dish candidate must either:

1. reference a visible existing recipe id, or
2. provide an AI-generated recipe draft with name, meal type, servings,
   ingredients, amounts, units, and cooking steps.

Model-supplied ids, units, amounts, and tags are untrusted input. The backend
validates recipe visibility, positive amounts, meal-type membership, unit
convertibility, and maximum lengths before persistence.

AI-generated recipes are materialized as `AI_GENERATED` family recipes in
review state. Their ingredients are deterministically mapped to standard foods.
The resulting real recipe ids are stored on every meal-plan item.

### 10.4 Backend validation and safety

After materialization, the backend:

1. calculates recipe and meal nutrition
2. builds per-member rule profiles
3. runs allergy, dislike, diet-goal, budget, repetition, and fixed population
   rules
4. persists risk rows against the meal plan
5. writes nutrition, cost, and risk snapshots
6. creates a `PENDING_REVIEW` meal plan

High or blocking risks prevent publish. Medium risks require a cook
acknowledgement with a note. Low risks remain visible. The AI explanation is
stored separately from deterministic rule results so the UI cannot present AI
text as a safety decision.

## 11. Cook Review and Meal-Plan State

The existing service-layer state model is retained:

```text
PENDING_REVIEW -> ADJUSTED -> PUBLISHED -> CONFIRMING
PENDING_REVIEW ------------> PUBLISHED
PUBLISHED/CONFIRMING -------> CONFIRM_CLOSED
CONFIRM_CLOSED -------------> PREPARING -> COMPLETED
PUBLISHED/CONFIRMING/CONFIRM_CLOSED/PREPARING -> CANCELLED
```

Only `PENDING_REVIEW` and `ADJUSTED` plans are editable. Cook operations include:

- add, remove, replace, and reorder dishes
- change meal type and serving count
- choose a visible recipe
- edit an AI-generated recipe before publish
- set confirmation cutoff
- acknowledge medium/low risks
- request regeneration without overwriting the previous recommendation

Every content edit recalculates nutrition, cost, and risks before returning.
Every edit and state transition appends `nutrition_meal_operation_log` with
before and after snapshots.

Publish requires:

- at least one active meal-plan item
- a real, family-visible recipe id on every item
- valid required-ingredient mappings and unit conversions
- no unresolved high or blocking risks
- explicit acknowledgement of required medium risks
- a future confirmation cutoff when confirmation is enabled

Meal-plan edits use the existing version column for optimistic concurrency.
State transitions and confirmation close use locked reads. A stale edit returns
a stable nutrition version-conflict error instead of silently overwriting
another cook's change.

## 12. Member Confirmation and Summary

`nutrition_meal_confirmation` remains the confirmation header.
`nutrition_meal_confirmation_item` becomes the source of truth for dish-level
selection.

Each submitted item contains:

- meal-plan item id
- selected flag
- serving count
- risk acknowledgement
- optional adjustment note

The backend validates that every submitted item belongs to the same family and
meal plan. A member may submit only for their bound profile. Family admins,
cooks, and explicit profile guardians may proxy-confirm. Confirmation remains
editable only before the cutoff and while the plan is `PUBLISHED` or
`CONFIRMING`.

High or blocking member-specific risk rejects the affected dish selection.
Medium risk requires acknowledgement on that confirmation item. Choosing
`eat_at_home = false` stores an away confirmation with no selected items.

The meal summary derives dish servings from confirmation items, not from member
count multiplication. It returns confirmed, away, and unconfirmed member
counts; selected servings per dish; remarks; risk counts; and readiness for
shopping-list generation.

## 13. Shopping, Prices, and Budget

### 13.1 Shopping list

A final shopping list is generated after confirmation closes. A read-only
preview may be generated before close but is not persisted as the final list.

Final ingredient amounts use:

- selected confirmation items
- confirmed serving counts
- recipe serving counts
- mapped recipe ingredient quantities
- explicit unit conversion
- cook adjustments recorded before completion

Generation is idempotent for one meal-plan version. If the plan or
confirmations change before close, the preview is recalculated. After close,
the persisted generated snapshot identifies the source plan version and
confirmation set.

Shopping-list states and item states are exposed through explicit transition
operations. Price entry preserves channel, brand, original specification,
quantity, total price, and normalized unit price.

### 13.2 Budget rules and summaries

Family administrators can configure enabled daily, weekly, monthly, and
per-meal budget rules using `nutrition_budget_rule`.

Budget calculation follows this precedence:

```text
actual shopping-item price
-> persisted shopping-list actual total
-> latest compatible family price record
-> meal/recipe estimate marked as estimated
```

`usageRate` means `periodCost / applicableBudgetLimit`. It is not the purchased
item completion ratio. Shopping completion is returned as a separately named
metric.

Dish cost is derived from ingredient cost when available. It is never produced
by dividing the whole meal cost equally across dishes. Weekly and monthly
responses retain daily, dish, ingredient, channel, actual, estimated, and
per-person breakdowns.

## 14. Nutrition Records and Reports

Completing a meal plan generates one effective nutrition record per member,
meal type, and selected confirmation item. Calculation uses the confirmed
serving count and the reviewed recipe, not the original AI text.

Generation is idempotent. Repeated completion calls return existing records
without creating duplicates. Original calculation snapshots remain immutable.

Profile owners and profile guardians may append corrections and extra foods for
authorized profiles. Family administrators and cooks may do so across the
family. Corrections append adjustment records and effective correction records;
they do not overwrite generated records.

Reports include:

- personal daily intake and target comparison
- family weekly and monthly totals
- risk counts
- actual, estimated, and per-person cost
- commonly eaten dishes
- high-sodium, high-sugar, and high-fat reminders
- simple daily trend series

Report snapshots are written only for explicit report-generation requests.
Ordinary daily queries aggregate current records without producing duplicate
snapshots.

## 15. Frontend Integration

All nutrition pages must remove production imports from `nutritionPageData.ts`.
Fixture builders may remain under tests only.

The live frontend behavior includes:

- accessible-family loading and a current-family selector
- loading, empty, error, permission-denied, and stale-version states
- family settings, role, grant, member, guardian, and health editors
- platform standard-food, tag, public-recipe, and import management
- family recipe and ingredient-mapping editors
- AI job progress, recommendation comparison, risk separation, cook editing,
  acknowledgement, and publish actions
- responsive dish-level member confirmation
- live meal summary and shopping-list generation
- item purchase and price entry
- budget-rule configuration and live weekly/monthly summaries
- nutrition record correction, extra food, and report views

Buttons are enabled only when the current RBAC and scoped-role state allows the
operation. Frontend checks improve usability but never replace backend access
checks.

The existing `nutritionService.ts` remains the typed HTTP boundary and is
extended rather than bypassed. Pages must not construct nutrition API URLs
directly.

## 16. API Organization

All APIs remain under `/api/nutrition`.

Primary family groups:

- `/families` and `/families/{familyId}/settings`
- `/families/{familyId}/role-bindings`
- `/families/{familyId}/data-grants`
- `/families/{familyId}/members`
- `/families/{familyId}/health-profiles`
- `/families/{familyId}/recipes`
- `/families/{familyId}/ai-recommendation-jobs`
- `/families/{familyId}/ai-recommendations`
- `/families/{familyId}/meal-plans`
- `/families/{familyId}/confirmations`
- `/families/{familyId}/shopping-lists`
- `/families/{familyId}/price-records`
- `/families/{familyId}/budget-rules`
- `/families/{familyId}/budget`
- `/families/{familyId}/nutrition-records`
- `/families/{familyId}/overview`

Primary platform groups:

- `/platform/standard-foods`
- `/platform/health-tags`
- `/platform/recipes`
- `/platform/import-jobs`

Existing compatible routes remain available unless a route is demonstrably
incorrect. New DTOs provide the missing detail without returning JPA entities.

## 17. Error Handling, Audit, and Concurrency

Use stable `NUTRITION_*` domain error codes for:

- access denial and invalid scoped role/grant operations
- missing or invisible family data
- invalid recipe mapping or unit conversion
- invalid AI output and failed jobs
- blocked or unacknowledged risks
- invalid meal and shopping state transitions
- stale versions
- closed confirmation windows
- duplicate import confirmation

The existing global nutrition exception handler and `ApiResponse<T>` envelope
remain in place. This recovery does not introduce nutrition-only HTTP semantics
that conflict with the rest of CyberMario.

Sensitive logs contain ids and error codes, not health snapshots, model prompts,
confirmation notes, or full imported rows. AI input/output snapshots, operation
logs, import errors, price history, adjustments, and report snapshots provide
the business audit trail.

The existing locking approach is retained where duplicate writes are possible:

- AI scheduled-job creation and job claim
- import confirmation and failure recording
- meal-plan transitions
- confirmation upsert and confirmation close
- final shopping-list generation
- completed-meal record generation

## 18. Persistence and Migration

V31 already contains the required tables and fields for the recovery, including
role bindings, grants, health tags, recipe steps, confirmation items, operation
logs, budget rules, budget snapshots, record adjustments, and report snapshots.
No schema migration is planned for the initial recovery.

Implementation must never edit an existing Flyway migration. If a verified
schema gap is discovered, the task must add exactly one next-version migration
for that database change after rechecking the current migration sequence. The
change must preserve existing nutrition rows and use backward-safe defaults.

Current fixture or test data is not migrated into production tables.

## 19. Testing and Acceptance Strategy

### 19.1 Required test layers

1. Unit tests for deterministic calculation, unit conversion, rule strategies,
   DTO validation, and state-transition guards.
2. Repository-backed service tests for family isolation, role/grant semantics,
   imports, job claims, confirmation upserts, and idempotency.
3. Controller tests using real service wiring for representative family and
   platform operations; path-reflection smoke tests alone are insufficient.
4. Frontend interaction tests that mock HTTP responses and assert request,
   mutation, loading, error, and disabled states; static text rendering alone is
   insufficient.
5. Vertical backend acceptance tests for complete business flows.

### 19.2 Mandatory vertical acceptance scenarios

The recovery is not complete unless tests prove all of these:

1. A family owner enables AI, assigns a cook, creates members and health
   profiles, and the cook can access the family while an unrelated user cannot.
2. Clan-family association alone grants no family data; an explicit scoped grant
   enables only the granted data scope and permission level; revocation removes
   access.
3. A scheduled AI job receives member health, recipe, budget, and recent-menu
   context, creates real recipe-linked meal items, runs backend rules, and
   remains `PENDING_REVIEW`.
4. A high allergy risk blocks publish and confirmation. A medium risk requires a
   recorded acknowledgement.
5. A cook replaces a dish, changes servings, and publishes; nutrition, risk, and
   cost snapshots are recalculated and an operation log is written.
6. Two members select different dishes and servings; summary totals match the
   confirmation items exactly.
7. Closing confirmation and generating the shopping list produces ingredient
   amounts derived from those exact servings.
8. Price entry updates actual cost; budget usage equals cost divided by the
   configured limit and is distinct from shopping completion.
9. Completing the meal produces non-zero member nutrition records; a retry does
   not duplicate them; an authorized correction changes effective reports while
   preserving the original record.
10. Live frontend pages load the same family flow and call the expected APIs;
    production components contain no fixture imports.

### 19.3 Verification commands

Every implementation task starts with its focused tests. Recovery completion
requires, at minimum:

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false -Dtest='top.egon.mario.nutrition.**' test
./mvnw -Dmaven.build.cache.enabled=false test

cd ../fe
bun run typecheck
bun run test
bun run lint
bun run build
```

Do not start the application or open a browser as part of automated recovery
verification.

## 20. Delivery Slices

The implementation plan must use separate reviewable slices and one commit per
task:

1. Family settings, scoped roles, grants, member binding, and guardian workflow.
2. Full standard-food/tag/public-recipe operations and remaining import types.
3. Family and AI-generated recipe detail, steps, mapping, calculation, and unit
   conversion.
4. Asynchronous AI job claim, complete context, recipe materialization, and
   deterministic validation.
5. Cook meal editing, operation logs, concurrency, validation, and publish gate.
6. Dish-level confirmations, proxy rules, cutoff behavior, and exact summary.
7. Shopping generation, price history, budget rules, and corrected cost
   semantics.
8. Completed-meal records, authorized corrections, extra foods, and expanded
   reports.
9. Live frontend foundation and family/health/recipe pages.
10. Live AI/meal/confirmation/shopping/budget/record pages.
11. Vertical acceptance suite, fixture removal check, full verification, and
    documentation reconciliation.

Each slice must include failing tests, the smallest complete implementation,
focused verification, main-agent review, and an intentional commit. A later
slice may depend on explicit interfaces from an earlier slice, but no slice may
claim a workflow that still requires direct database edits or fixture data.

## 21. Design Pattern Decisions

Patterns retained and completed:

- scoped role binding plus explicit data grants for multi-family access
- Template Method for all CSV import lifecycles
- Strategy plus Specification-style results for deterministic nutrition rules
- service-layer state transition guards for meal plans and shopping lists
- focused Domain Services for recommendation context, recipe materialization,
  validation, and unit conversion

Patterns intentionally rejected:

- repository-only or controller-only completion as a substitute for workflows
- global RBAC-only family access
- full policy engines or Drools
- formal state-machine frameworks
- microservices
- generic event buses or handler pipelines
- rewriting the current module

The added Domain Services are justified because context assembly, untrusted AI
materialization, deterministic validation, and unit conversion are independent,
testable responsibilities. They prevent `NutritionAiServiceImpl` from becoming
the owner of every domain rule. Simpler direct service calls remain preferable
for CRUD and state transitions.

## 22. Completion Criteria

The recovery is complete only when:

- every mandatory vertical acceptance scenario passes
- all production nutrition pages use live typed services
- no required operational role or grant needs direct database manipulation
- AI recommendations contain real recipe-linked items
- publish always runs deterministic validation
- confirmation, shopping, budget, and records use the same serving data
- current nutrition data remains readable
- no existing Flyway migration was changed
- focused and full backend/frontend verification passes
- the feature checklist describes actual operable behavior, not schema or page
  presence
