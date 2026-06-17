# Clocktower Rule Data Localization Design

## Goal

Confirm why the UI only shows four Clocktower menu entries, then fix the current rule-data gaps where board generation can only use five Trouble Brewing roles and frontend pages receive too many English-only role/type values.

The work should keep official Blood on the Clocktower role codes, add Chinese display names through backend-owned enum and DTO contracts, complete the three base scripts' role and night-order data, and make rule/board/night-order pages display Chinese names without losing stable machine codes.

## Current Findings

- The four Clocktower sidebar entries are not caused by missing `superadmin` permissions. The current Phase 1 design and implementation only register four entries: boards, rooms, rules, and replays.
- `superadmin` already bypasses normal menu filtering, and the Clocktower RBAC provider declares those four menu resources.
- `clocktower_script` seeds all three base scripts, but `clocktower_role` only seeds five Trouble Brewing roles: `CHEF`, `EMPATH`, `MONK`, `POISONER`, and `IMP`.
- `clocktower_night_order` only has seven rows for those five roles.
- `ClocktowerRoleType` is currently a plain enum, so API responses expose English names like `TOWNSFOLK` unless the frontend maps them locally.
- Board APIs return `roleCodes` only, so board pages cannot display role Chinese names without another lookup.
- `RepositoryRoleMetadataProvider` reads roles across all scripts, so once multiple scripts are complete, board generation must filter by `scriptCode` to avoid mixing roles from different scripts.

## Scope

In scope:

- Keep official role enum names such as `CHEF`, `EMPATH`, and `IMP`.
- Add backend-owned coded enums with integer code and Chinese description for Clocktower role, role type, alignment, and night type where needed.
- Add exactly one new Flyway migration for the rule-data/database change. Existing migrations must not be edited.
- Complete structured static data for the three base scripts: Trouble Brewing, Bad Moon Rising, and Sects and Violets.
- Update DTOs so frontend receives Chinese display data from backend.
- Return night order as two lists: first night and other nights.
- Keep short-term compatibility for existing fields and service calls where practical.
- Update board generation and validation to use role metadata scoped by script.

Out of scope for this change:

- Full Clocktower rule-data maintenance CRUD page.
- RAG-backed rule question answering.
- Automatic implementation of every role ability.
- Replay analytics or agent autoplay improvements.
- New sidebar entries beyond the current four unless a later design explicitly adds a rule-data maintenance page.

## Data Ownership

The source of truth for static Clocktower role data should be a structured repository artifact that can be reviewed before it becomes a migration. The accepted input formats are:

- Excel, CSV, or Markdown table.
- Plain pasted role lists that can be normalized into a structured table.
- Later, a dedicated admin maintenance page, but that should be a separate design and implementation.

Recommended role data columns:

| Column | Meaning |
|---|---|
| `scriptCode` | `TROUBLE_BREWING`, `BAD_MOON_RISING`, or `SECTS_AND_VIOLETS` |
| `roleCode` | Official role code, for example `CHEF` |
| `name` | Chinese role name |
| `roleType` | Townsfolk, Outsider, Minion, Demon, Traveler, or Fabled |
| `alignment` | Good, Evil, or neutral/system where applicable |
| `abilityText` | Chinese ability text |
| `firstNightOrder` | Nullable integer order |
| `otherNightOrder` | Nullable integer order |
| `firstNightReminder` | Nullable Chinese reminder text |
| `otherNightReminder` | Nullable Chinese reminder text |
| `sourceUrl` | Source page for auditability |

Recommended night-order columns:

| Column | Meaning |
|---|---|
| `scriptCode` | Script code |
| `nightType` | First night or other night |
| `orderNo` | Numeric order |
| `roleCode` | Official role code |
| `reminderText` | Chinese reminder text |

## Backend Design

Add Clocktower coded enums following the existing project pattern used by RBAC enums:

- Persistence-layer enums implement `CodedEnum`.
- DTO-layer enums serialize as `{code, desc}`.
- JPA converters persist integer codes for new or migrated enum columns.
- Frontend can use the existing `enumDesc` helper instead of local string mapping.

Role enum naming uses official codes. For example:

```java
CHEF(1, "厨师")
```

Role type uses Chinese descriptions:

```java
TOWNSFOLK(1, "镇民")
OUTSIDER(2, "外来者")
MINION(3, "爪牙")
DEMON(4, "恶魔")
TRAVELER(5, "旅行者")
FABLED(6, "传奇")
```

The migration should not modify `V18__create_clocktower_core_schema.sql`. It should add one new versioned Flyway migration that safely evolves the current data. If enum columns are moved from text to integer codes, the migration must convert existing rows deterministically.

## API Design

Role and board APIs should preserve machine-readable codes and add backend-owned display fields.

`ClocktowerRoleResponse` should expose:

- `scriptCode`
- `roleCode`
- `roleName`
- `roleType`
- `alignment`
- `abilityText`
- `firstNightOrder`
- `otherNightOrder`
- `firstNightReminder`
- `otherNightReminder`
- `enabled`
- `sourceUrl`

`roleCode` remains the stable official role code in API payloads, such as `CHEF`, even if the persistence layer stores a coded enum value. `roleName`, `roleType.desc`, and `alignment.desc` are for UI display.

Board candidate and saved board responses should keep `roleCodes` for compatibility and add:

- `roles: ClocktowerRoleSummaryResponse[]`

`ClocktowerRoleSummaryResponse` should include role code, role name, role type, and alignment. Board pages should render Chinese names from `roles`, with the official code available as a secondary tag or tooltip.

Night-order APIs should add an aggregate response:

```ts
type ClocktowerNightOrderGroupResponse = {
  firstNight: ClocktowerNightOrderResponse[]
  otherNight: ClocktowerNightOrderResponse[]
}
```

Each night-order row should include role code, role name, role type, order, and reminder text. Existing filtered list behavior can remain during transition, but new UI should use the grouped response.

## Frontend Design

The Clocktower frontend should stop hardcoding Chinese labels for role types once backend DTO enums provide `{code, desc}`.

Rule page changes:

- Script role table displays role Chinese name, official code, type Chinese description, alignment, ability text, and night orders.
- Night order displays two sections: first night and other nights.
- Empty states should distinguish "no night action" from "data missing" where possible.

Board page changes:

- Candidate and saved board tables display role Chinese names from `roles`.
- Official role codes remain visible as compact secondary text or tag content where useful for debugging and data entry.
- Manual validation still accepts official role codes.

Grimoire/night checklist changes:

- Night checklist should use the same Chinese role/type data shape as the rule page.
- Existing room and player workflows continue to use role codes internally.

## Validation

Backend validation:

- Unit tests for coded enum JSON shape and parsing where new DTO enums are introduced.
- Script service tests for role list, role type filtering, and grouped night order.
- Board service tests proving candidate generation only uses roles from the requested script.
- Migration/schema test verifying the new migration applies and contains complete expected static data counts.

Frontend validation:

- Type updates for coded enum DTOs.
- Rule data page test for first-night and other-night sections.
- Board page or table test proving Chinese role names render when `roles` is present.
- Service tests for the new grouped night-order endpoint.

## Rollout

1. User provides static role and night-order data in one of the accepted formats.
2. Normalize data into a reviewable structured artifact.
3. Add backend enum and DTO contracts.
4. Add one Flyway migration to convert/complete data.
5. Update service and board metadata lookup logic.
6. Update frontend types and displays.
7. Run targeted backend and frontend validation.

## Risks

- Complete role and night-order data must be accurate. If source data is incomplete, the migration should not guess missing rows.
- Changing persisted enum columns from strings to integer codes affects existing data and requires careful migration tests.
- Keeping compatibility fields temporarily reduces frontend churn but leaves a future cleanup task.
- Full rule-data maintenance UI remains unresolved and should be planned separately if manual long-term editing is required.
