# Clocktower Board Editor Library Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the redesigned `/clocktower/boards` editor and personal board library, including role tree selection, backend revalidation, paged personal board queries, and valid-board-only room creation.

**Architecture:** Keep the existing Clocktower module boundaries. Backend board APIs remain under `/api/clocktower/boards`; board save always revalidates and persists a validation snapshot plus a denormalized `valid` flag. Frontend keeps `/clocktower/boards` as the main editor/library page, with a small focused `RoleTreeSelect` component and paged board library queries.

**Tech Stack:** Spring Boot WebFlux controller facade, Spring Data JPA, Flyway, Java records, React 19, TypeScript, Ant Design, Vitest, JUnit 5, AssertJ, Mockito.

---

## File Structure

Backend files:

- Modify `be/src/main/resources/db/migration/V22__add_clocktower_board_valid.sql`: new Flyway migration for `clocktower_board_config.valid`.
- Modify `be/src/test/java/top/egon/mario/clocktower/ClocktowerSchemaMigrationTests.java`: migration coverage for the new column.
- Modify `be/src/main/java/top/egon/mario/clocktower/board/po/ClocktowerBoardConfigPo.java`: add `valid`.
- Modify `be/src/main/java/top/egon/mario/clocktower/board/dto/response/ClocktowerBoardConfigResponse.java`: expose `valid` and `createdAt`.
- Create `be/src/main/java/top/egon/mario/clocktower/board/dto/request/ClocktowerBoardQuery.java`: board list filters.
- Modify `be/src/main/java/top/egon/mario/clocktower/board/repository/ClocktowerBoardConfigRepository.java`: add JPA specification support and owner-aware lookup.
- Modify `be/src/main/java/top/egon/mario/clocktower/script/repository/ClocktowerRoleRepository.java`: add enabled role lookup by codes.
- Modify `be/src/main/java/top/egon/mario/clocktower/board/service/RoleMetadataProvider.java`: expose enabled role summaries by role code collection.
- Modify `be/src/main/java/top/egon/mario/clocktower/board/service/impl/RepositoryRoleMetadataProvider.java`: implement the new role-code lookup.
- Modify `be/src/main/java/top/egon/mario/clocktower/board/service/ClocktowerBoardService.java`: update list contract and add valid-board lookup for room creation.
- Modify `be/src/main/java/top/egon/mario/clocktower/board/service/impl/ClocktowerBoardServiceImpl.java`: Java validation issues, save revalidation, valid flag, paged personal queries, owner checks.
- Modify `be/src/main/java/top/egon/mario/clocktower/board/web/ClocktowerBoardController.java`: paged list endpoint with filters.
- Modify `be/src/main/java/top/egon/mario/clocktower/room/service/impl/ClocktowerRoomServiceImpl.java`: resolve `boardConfigId` / `boardCode` through board service and reject invalid boards.
- Modify backend tests under `be/src/test/java/top/egon/mario/clocktower/board/` and `be/src/test/java/top/egon/mario/clocktower/room/`.

Frontend files:

- Modify `fe/src/modules/clocktower/clocktowerTypes.ts`: board query/page fields, `valid`, `createdAt`, save request without trusted validation.
- Modify `fe/src/modules/clocktower/clocktowerService.ts`: paged board list query and save body.
- Modify `fe/src/modules/clocktower/clocktowerService.test.ts`: URL/body tests.
- Create `fe/src/modules/clocktower/components/RoleTreeSelect.tsx`: role-type grouped TreeSelect and helper functions.
- Create `fe/src/modules/clocktower/components/RoleTreeSelect.test.tsx`: tree data tests.
- Modify `fe/src/modules/clocktower/BoardBuilderPage.tsx`: editor state, role tree, save current board, paged filters, edit/copy to editor.
- Modify `fe/src/modules/clocktower/BoardBuilderPage.test.tsx`: render and helper behavior tests.
- Modify `fe/src/modules/clocktower/components/BoardCandidateTable.tsx`: add copy-to-editor action.
- Modify `fe/src/modules/clocktower/RoomListPage.tsx`: valid board selector in create modal.
- Modify `fe/src/modules/clocktower/RoomLobbyPage.test.tsx` only if type changes ripple into mocks.

Validation commands:

- Backend targeted: `cd be && ./mvnw -Dtest=ClocktowerSchemaMigrationTests,ClocktowerBoardServiceTests,ClocktowerBoardControllerTests,ClocktowerRoomServiceTests test`
- Frontend targeted: `cd fe && bun test src/modules/clocktower/clocktowerService.test.ts src/modules/clocktower/components/RoleTreeSelect.test.tsx src/modules/clocktower/BoardBuilderPage.test.tsx src/modules/clocktower/RoomLobbyPage.test.tsx`
- Final backend: `cd be && ./mvnw test`
- Final frontend: `cd fe && bun test`

---

### Task 1: Add Board Valid Persistence Shape

**Files:**
- Create: `be/src/main/resources/db/migration/V22__add_clocktower_board_valid.sql`
- Modify: `be/src/test/java/top/egon/mario/clocktower/ClocktowerSchemaMigrationTests.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/board/po/ClocktowerBoardConfigPo.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/board/dto/response/ClocktowerBoardConfigResponse.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/board/service/impl/ClocktowerBoardServiceImpl.java`
- Test: `be/src/test/java/top/egon/mario/clocktower/ClocktowerSchemaMigrationTests.java`
- Test: `be/src/test/java/top/egon/mario/clocktower/board/ClocktowerBoardServiceTests.java`

- [ ] **Step 1: Write the failing migration test**

Add this test method to `be/src/test/java/top/egon/mario/clocktower/ClocktowerSchemaMigrationTests.java`:

```java
@Test
void boardValidMigrationAddsQueryableValidFlag() throws IOException {
    Path migration = Path.of("src/main/resources/db/migration/V22__add_clocktower_board_valid.sql");

    String sql = Files.readString(migration);

    assertThat(sql).contains("ALTER TABLE clocktower_board_config");
    assertThat(sql).contains("ADD COLUMN valid BOOLEAN NOT NULL DEFAULT FALSE");
}
```

- [ ] **Step 2: Run the migration test to verify it fails**

Run:

```bash
cd be
./mvnw -Dtest=ClocktowerSchemaMigrationTests#boardValidMigrationAddsQueryableValidFlag test
```

Expected: FAIL because `V22__add_clocktower_board_valid.sql` does not exist.

- [ ] **Step 3: Create the Flyway migration**

Create `be/src/main/resources/db/migration/V22__add_clocktower_board_valid.sql`:

```sql
ALTER TABLE clocktower_board_config
    ADD COLUMN valid BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_clocktower_board_config_owner_valid
    ON clocktower_board_config (created_by, valid, id);
```

- [ ] **Step 4: Run the migration test to verify it passes**

Run:

```bash
cd be
./mvnw -Dtest=ClocktowerSchemaMigrationTests#boardValidMigrationAddsQueryableValidFlag test
```

Expected: PASS.

- [ ] **Step 5: Add the entity and response fields**

In `be/src/main/java/top/egon/mario/clocktower/board/po/ClocktowerBoardConfigPo.java`, add this field after `playerCount`:

```java
@Column(name = "valid", nullable = false)
private boolean valid;
```

Replace `be/src/main/java/top/egon/mario/clocktower/board/dto/response/ClocktowerBoardConfigResponse.java` with:

```java
package top.egon.mario.clocktower.board.dto.response;

import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.script.dto.response.ClocktowerRoleSummaryResponse;

import java.time.Instant;
import java.util.List;

public record ClocktowerBoardConfigResponse(
        Long boardId,
        String boardCode,
        ClocktowerScriptCode scriptCode,
        int playerCount,
        boolean valid,
        Instant createdAt,
        List<String> roleCodes,
        List<ClocktowerRoleSummaryResponse> roles,
        ClocktowerBoardValidationResponse validation
) {
}
```

- [ ] **Step 6: Update the response mapper call site**

In `be/src/main/java/top/egon/mario/clocktower/board/service/impl/ClocktowerBoardServiceImpl.java`, replace the `toResponse` constructor call with:

```java
return new ClocktowerBoardConfigResponse(config.getId(), config.getBoardCode(), config.getScriptCode(),
        config.getPlayerCount(), config.isValid(), config.getCreatedAt(), roleCodes,
        roleMetadataProvider.roleSummaries(config.getScriptCode(), roleCodes), validation);
```

- [ ] **Step 7: Update existing board tests for the new response fields**

In `be/src/test/java/top/egon/mario/clocktower/board/ClocktowerBoardServiceTests.java`, where `ClocktowerBoardConfigPo config` is saved in `saveBoardConfigResponsePreservesRoleCodesAndAddsRoleSummaries`, set the new entity fields:

```java
config.setId(42L);
config.setValid(true);
config.setCreatedAt(java.time.Instant.parse("2026-06-19T00:00:00Z"));
return config;
```

Keep the existing assertions and add:

```java
assertThat(response.valid()).isTrue();
assertThat(response.createdAt()).isEqualTo(java.time.Instant.parse("2026-06-19T00:00:00Z"));
```

- [ ] **Step 8: Run the affected backend tests**

Run:

```bash
cd be
./mvnw -Dtest=ClocktowerSchemaMigrationTests,ClocktowerBoardServiceTests test
```

Expected: PASS for migration and existing board service tests.

- [ ] **Step 9: Commit**

```bash
git add be/src/main/resources/db/migration/V22__add_clocktower_board_valid.sql \
  be/src/test/java/top/egon/mario/clocktower/ClocktowerSchemaMigrationTests.java \
  be/src/main/java/top/egon/mario/clocktower/board/po/ClocktowerBoardConfigPo.java \
  be/src/main/java/top/egon/mario/clocktower/board/dto/response/ClocktowerBoardConfigResponse.java \
  be/src/main/java/top/egon/mario/clocktower/board/service/impl/ClocktowerBoardServiceImpl.java \
  be/src/test/java/top/egon/mario/clocktower/board/ClocktowerBoardServiceTests.java
git commit -m "feat(clocktower): persist board validation status"
```

---

### Task 2: Revalidate Boards And Add Script-Role Alignment Issues

**Files:**
- Modify: `be/src/main/java/top/egon/mario/clocktower/script/repository/ClocktowerRoleRepository.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/board/service/RoleMetadataProvider.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/board/service/impl/RepositoryRoleMetadataProvider.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/board/service/impl/ClocktowerBoardServiceImpl.java`
- Modify: `be/src/test/java/top/egon/mario/clocktower/board/ClocktowerBoardTestFactory.java`
- Modify: `be/src/test/java/top/egon/mario/clocktower/board/ClocktowerBoardServiceTests.java`
- Test: `be/src/test/java/top/egon/mario/clocktower/board/ClocktowerBoardServiceTests.java`

- [ ] **Step 1: Write failing tests for Java validation issues and save revalidation**

Add these tests to `be/src/test/java/top/egon/mario/clocktower/board/ClocktowerBoardServiceTests.java`:

```java
@Test
void validateRejectsUnknownRoleCode() {
    BoardValidationResponse response = boardService.validate(new ClocktowerBoardValidateRequest(
            ClocktowerScriptCode.TROUBLE_BREWING,
            5,
            List.of("EMPATH", "CHEF", "MONK", "POISONER", "NO_SUCH_ROLE")
    ));

    assertThat(response.valid()).isFalse();
    assertThat(response.issues()).extracting(ClocktowerRuleViolationResponse::code)
            .contains("BOARD_ROLE_NOT_FOUND");
}

@Test
void validateRejectsRoleFromAnotherScript() {
    BoardValidationResponse response = boardService.validate(new ClocktowerBoardValidateRequest(
            ClocktowerScriptCode.TROUBLE_BREWING,
            5,
            List.of("EMPATH", "CHEF", "MONK", "POISONER", "BMR_DEMON")
    ));

    assertThat(response.valid()).isFalse();
    assertThat(response.issues()).extracting(ClocktowerRuleViolationResponse::code)
            .contains("BOARD_ROLE_SCRIPT_MISMATCH");
}

@Test
void saveRevalidatesAndPersistsBackendValidationResult() {
    RoleMetadataProvider provider = ClocktowerBoardTestFactory.roleMetadataProvider();
    ClocktowerBoardConfigRepository configRepository = mock(ClocktowerBoardConfigRepository.class);
    ClocktowerBoardRoleRepository roleRepository = mock(ClocktowerBoardRoleRepository.class);
    when(configRepository.save(any(ClocktowerBoardConfigPo.class))).thenAnswer(invocation -> {
        ClocktowerBoardConfigPo config = invocation.getArgument(0);
        config.setId(99L);
        config.setCreatedAt(java.time.Instant.parse("2026-06-19T01:00:00Z"));
        return config;
    });
    ClocktowerBoardService service = new ClocktowerBoardServiceImpl(provider,
            ClocktowerBoardTestFactory.ruleEngine(), configRepository, roleRepository, new ObjectMapper());
    ClocktowerBoardValidationResponse trustedFrontendValidation = new ClocktowerBoardValidationResponse(true, Map.of(),
            List.of(), List.of());
    ClocktowerBoardSaveRequest request = new ClocktowerBoardSaveRequest(ClocktowerScriptCode.TROUBLE_BREWING,
            5, 1, 1, 1, true, "seed", List.of("EMPATH", "CHEF"), trustedFrontendValidation);

    ClocktowerBoardConfigResponse response = service.save(request, principal(1L));

    assertThat(response.valid()).isFalse();
    assertThat(response.validation().valid()).isFalse();
    assertThat(response.validation().violations()).extracting(ClocktowerRuleViolationResponse::code)
            .contains("BOARD_ROLE_COUNT_MISMATCH");
}
```

Add these imports if missing:

```java
import top.egon.mario.clocktower.board.dto.response.ClocktowerRuleViolationResponse;
```

- [ ] **Step 2: Run the board service tests to verify they fail**

Run:

```bash
cd be
./mvnw -Dtest=ClocktowerBoardServiceTests test
```

Expected: FAIL because role-code lookup and save revalidation are not implemented.

- [ ] **Step 3: Extend role metadata lookup by role codes**

In `be/src/main/java/top/egon/mario/clocktower/script/repository/ClocktowerRoleRepository.java`, add:

```java
List<ClocktowerRolePo> findByRoleCodeInAndEnabledAndDeletedFalse(Collection<String> roleCodes, boolean enabled);
```

In `be/src/main/java/top/egon/mario/clocktower/board/service/RoleMetadataProvider.java`, add this default method below `roles`:

```java
default List<ClocktowerRoleSummaryResponse> enabledRoles(Collection<String> roleCodes) {
    return List.of();
}
```

In `be/src/main/java/top/egon/mario/clocktower/board/service/impl/RepositoryRoleMetadataProvider.java`, add:

```java
@Override
public List<ClocktowerRoleSummaryResponse> enabledRoles(Collection<String> roleCodes) {
    if (roleCodes == null || roleCodes.isEmpty()) {
        return List.of();
    }
    return roleRepository.findByRoleCodeInAndEnabledAndDeletedFalse(roleCodes, true).stream()
            .map(ClocktowerRoleSummaryResponse::from)
            .toList();
}
```

Add import:

```java
import java.util.Collection;
```

- [ ] **Step 4: Convert the test factory provider from lambda to reusable provider**

In `be/src/test/java/top/egon/mario/clocktower/board/ClocktowerBoardTestFactory.java`, replace the local lambda in `service()` with a call to a new static method:

```java
static RoleMetadataProvider roleMetadataProvider() {
    return new RoleMetadataProvider() {
        @Override
        public List<ClocktowerRoleSummaryResponse> roles(ClocktowerScriptCode scriptCode) {
            return roleSummaries(scriptCode);
        }

        @Override
        public List<ClocktowerRoleSummaryResponse> enabledRoles(java.util.Collection<String> roleCodes) {
            return java.util.stream.Stream.of(
                            roleSummaries(ClocktowerScriptCode.TROUBLE_BREWING),
                            roleSummaries(ClocktowerScriptCode.BAD_MOON_RISING),
                            roleSummaries(ClocktowerScriptCode.SECTS_AND_VIOLETS)
                    )
                    .flatMap(List::stream)
                    .filter(role -> roleCodes.contains(role.roleCode()))
                    .toList();
        }
    };
}
```

Move the current lambda body into:

```java
private static List<ClocktowerRoleSummaryResponse> roleSummaries(ClocktowerScriptCode scriptCode) {
    if (scriptCode == ClocktowerScriptCode.TROUBLE_BREWING) {
        return List.of(
                summary(scriptCode, "WASHERWOMAN", "洗衣妇", ClocktowerRoleType.TOWNSFOLK,
                        ClocktowerAlignment.GOOD, "得知两名玩家中有一名是某个镇民。", 1, true, false, false),
                summary(scriptCode, "LIBRARIAN", "图书管理员", ClocktowerRoleType.TOWNSFOLK,
                        ClocktowerAlignment.GOOD, "得知两名玩家中有一名是某个外来者，或者没有外来者。", 2, true, false, false),
                summary(scriptCode, "INVESTIGATOR", "调查员", ClocktowerRoleType.TOWNSFOLK,
                        ClocktowerAlignment.GOOD, "得知两名玩家中有一名是某个爪牙。", 2, true, false, false),
                summary(scriptCode, "FORTUNETELLER", "占卜师", ClocktowerRoleType.TOWNSFOLK,
                        ClocktowerAlignment.GOOD, "每晚选择两名玩家，得知其中是否有恶魔，且有干扰项。", 3, true, true, false),
                summary(scriptCode, "UNDERTAKER", "送葬者", ClocktowerRoleType.TOWNSFOLK,
                        ClocktowerAlignment.GOOD, "每晚得知今天白天死于处决的角色。", 2, false, true, false),
                summary(scriptCode, "RAVENKEEPER", "守鸦人", ClocktowerRoleType.TOWNSFOLK,
                        ClocktowerAlignment.GOOD, "如果你在夜晚死亡，你会被唤醒并得知一名玩家的角色。", 3, false, true, false),
                summary(scriptCode, "VIRGIN", "贞洁者", ClocktowerRoleType.TOWNSFOLK,
                        ClocktowerAlignment.GOOD, "第一次被镇民提名时，提名者立刻被处决。", 2, false, false, false),
                summary(scriptCode, "SLAYER", "猎手", ClocktowerRoleType.TOWNSFOLK,
                        ClocktowerAlignment.GOOD, "白天限一次选择玩家，如果他是恶魔，他死亡。", 2, false, false, false),
                summary(scriptCode, "SOLDIER", "士兵", ClocktowerRoleType.TOWNSFOLK,
                        ClocktowerAlignment.GOOD, "你免受恶魔伤害。", 1, false, false, false),
                summary(scriptCode, "MAYOR", "镇长", ClocktowerRoleType.TOWNSFOLK,
                        ClocktowerAlignment.GOOD, "三人存活且无人被处决时善良获胜，夜晚死亡可能转移。", 3, false, false, false),
                summary(scriptCode, "EMPATH", "共情者", ClocktowerRoleType.TOWNSFOLK, ClocktowerAlignment.GOOD,
                        "每晚得知邻近存活玩家中有几名邪恶玩家。", 2, true, true, false),
                summary(scriptCode, "CHEF", "厨师", ClocktowerRoleType.TOWNSFOLK, ClocktowerAlignment.GOOD,
                        "首夜得知相邻邪恶玩家有多少对。", 1, true, false, false),
                summary(scriptCode, "MONK", "僧侣", ClocktowerRoleType.TOWNSFOLK, ClocktowerAlignment.GOOD,
                        "每晚保护一名玩家免受恶魔伤害。", 1, false, true, false),
                summary(scriptCode, "BUTLER", "管家", ClocktowerRoleType.OUTSIDER, ClocktowerAlignment.GOOD,
                        "每晚选择主人，明天只能在主人投票时投票。", 1, true, true, false),
                summary(scriptCode, "DRUNK", "酒鬼", ClocktowerRoleType.OUTSIDER, ClocktowerAlignment.GOOD,
                        "你不知道自己是酒鬼，以为自己是镇民。", 2, false, false, false),
                summary(scriptCode, "RECLUSE", "陌客", ClocktowerRoleType.OUTSIDER, ClocktowerAlignment.GOOD,
                        "你可能被当作邪恶阵营、爪牙或恶魔。", 2, false, false, false),
                summary(scriptCode, "SAINT", "圣徒", ClocktowerRoleType.OUTSIDER, ClocktowerAlignment.GOOD,
                        "如果你死于处决，你的阵营落败。", 1, false, false, false),
                summary(scriptCode, "POISONER", "投毒者", ClocktowerRoleType.MINION, ClocktowerAlignment.EVIL,
                        "每晚选择一名玩家：他中毒。", 2, true, true, false),
                summary(scriptCode, "SPY", "间谍", ClocktowerRoleType.MINION, ClocktowerAlignment.EVIL,
                        "每晚看到魔典，且可能被当作善良、镇民或外来者。", 3, true, true, false),
                summary(scriptCode, "SCARLETWOMAN", "红唇女郎", ClocktowerRoleType.MINION,
                        ClocktowerAlignment.EVIL, "恶魔死亡时，如果存活玩家够多，你变成恶魔。", 2, false, true, false),
                summary(scriptCode, "BARON", "男爵", ClocktowerRoleType.MINION, ClocktowerAlignment.EVIL,
                        "有额外的外来者在场。", 3, false, false, true),
                summary(scriptCode, "IMP", "小恶魔", ClocktowerRoleType.DEMON, ClocktowerAlignment.EVIL,
                        "每晚选择一名玩家：他死亡。如果杀死自己，一名爪牙变成小恶魔。", 2, false, true, false));
    }
    return List.of(
            summary(scriptCode, "BMR_TOWNSFOLK", "黯月镇民", ClocktowerRoleType.TOWNSFOLK,
                    ClocktowerAlignment.GOOD),
            summary(scriptCode, "BMR_MINION", "黯月爪牙", ClocktowerRoleType.MINION,
                    ClocktowerAlignment.EVIL),
            summary(scriptCode, "BMR_DEMON", "黯月恶魔", ClocktowerRoleType.DEMON, ClocktowerAlignment.EVIL));
}
```

In `service()`, instantiate the service with `roleMetadataProvider()`:

```java
return new ClocktowerBoardServiceImpl(roleMetadataProvider(), new TestClocktowerRuleEngine(),
        mock(top.egon.mario.clocktower.board.repository.ClocktowerBoardConfigRepository.class),
        mock(top.egon.mario.clocktower.board.repository.ClocktowerBoardRoleRepository.class),
        new ObjectMapper());
```

- [ ] **Step 5: Add Java validation issue helpers**

In `be/src/main/java/top/egon/mario/clocktower/board/service/impl/ClocktowerBoardServiceImpl.java`, add imports:

```java
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
```

Replace `validate` with:

```java
@Override
public BoardValidationResponse validate(ClocktowerBoardValidateRequest request) {
    List<String> roleCodes = normalizeRoleCodes(request.roleCodes());
    Map<String, ClocktowerRoleSummaryResponse> scriptRoles = roleMetadataProvider.roles(request.scriptCode()).stream()
            .collect(java.util.stream.Collectors.toMap(ClocktowerRoleSummaryResponse::roleCode,
                    java.util.function.Function.identity(), (left, right) -> left));
    Map<String, ClocktowerRoleSummaryResponse> enabledRoles = roleMetadataProvider.enabledRoles(roleCodes).stream()
            .collect(java.util.stream.Collectors.toMap(ClocktowerRoleSummaryResponse::roleCode,
                    java.util.function.Function.identity(), (left, right) -> left));

    List<ClocktowerRuleViolationResponse> javaIssues = boardInputIssues(request, roleCodes, scriptRoles, enabledRoles);
    Map<String, ClocktowerRoleType> roleTypes = scriptRoles.entrySet().stream()
            .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().roleType()));
    ClocktowerRoleTypeCountResponse typeCounts = countRoleTypes(roleCodes, roleTypes);
    BoardCandidateFact fact = new BoardCandidateFact(request.scriptCode(), request.playerCount(), roleCodes,
            typeCounts.townsfolk(), typeCounts.outsider(), typeCounts.minion(), typeCounts.demon());
    RuleDecisionCollector collector = ruleEngine.validateBoard(fact);
    List<ClocktowerRuleViolationResponse> ruleIssues = collector.violations().stream()
            .map(ClocktowerRuleViolationResponse::from)
            .toList();
    List<ClocktowerRuleViolationResponse> issues = mergeIssues(javaIssues, ruleIssues);
    List<ClocktowerScoreResponse> scores = collector.scores().stream()
            .map(ClocktowerScoreResponse::from)
            .toList();
    boolean valid = issues.stream().noneMatch(issue -> "ERROR".equalsIgnoreCase(issue.severity()));
    return new BoardValidationResponse(valid, typeCounts, issues, scores);
}
```

Add these helper methods near `countRoleTypes`:

```java
private List<String> normalizeRoleCodes(Collection<String> roleCodes) {
    if (roleCodes == null || roleCodes.isEmpty()) {
        return List.of();
    }
    return roleCodes.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(roleCode -> !roleCode.isBlank())
            .toList();
}

private List<ClocktowerRuleViolationResponse> boardInputIssues(
        ClocktowerBoardValidateRequest request,
        List<String> roleCodes,
        Map<String, ClocktowerRoleSummaryResponse> scriptRoles,
        Map<String, ClocktowerRoleSummaryResponse> enabledRoles) {
    List<ClocktowerRuleViolationResponse> issues = new ArrayList<>();
    if (roleCodes.size() != request.playerCount()) {
        issues.add(new ClocktowerRuleViolationResponse("BOARD_ROLE_COUNT_MISMATCH",
                "角色数量必须和玩家人数一致。", "ERROR"));
    }
    Set<String> checked = new LinkedHashSet<>(roleCodes);
    for (String roleCode : checked) {
        ClocktowerRoleSummaryResponse enabledRole = enabledRoles.get(roleCode);
        if (enabledRole == null) {
            issues.add(new ClocktowerRuleViolationResponse("BOARD_ROLE_NOT_FOUND",
                    "角色不存在或未启用：" + roleCode, "ERROR"));
            continue;
        }
        if (!scriptRoles.containsKey(roleCode)) {
            issues.add(new ClocktowerRuleViolationResponse("BOARD_ROLE_SCRIPT_MISMATCH",
                    "角色不属于所选剧本：" + roleCode, "ERROR"));
        }
    }
    return issues;
}

private List<ClocktowerRuleViolationResponse> mergeIssues(List<ClocktowerRuleViolationResponse> first,
                                                          List<ClocktowerRuleViolationResponse> second) {
    Map<String, ClocktowerRuleViolationResponse> issues = new LinkedHashMap<>();
    first.forEach(issue -> issues.putIfAbsent(issue.code(), issue));
    second.forEach(issue -> issues.putIfAbsent(issue.code(), issue));
    return List.copyOf(issues.values());
}
```

- [ ] **Step 6: Revalidate on save and persist backend validation**

In `save`, compute validation before saving config:

```java
List<String> roleCodes = normalizeRoleCodes(request.roleCodes());
BoardValidationResponse validation = validate(new ClocktowerBoardValidateRequest(
        request.scriptCode(), request.playerCount(), roleCodes));
ClocktowerBoardValidationResponse boardValidation = ClocktowerBoardValidationResponse.from(
        validation, roleTypeCountMap(validation.typeCounts()));
```

Set valid and write backend validation:

```java
config.setValid(boardValidation.valid());
config.setValidationJson(writeJson(boardValidation));
```

Iterate over `roleCodes` instead of `request.roleCodes()`:

```java
for (int i = 0; i < roleCodes.size(); i++) {
    ClocktowerBoardRolePo role = new ClocktowerBoardRolePo();
    role.setBoardConfigId(saved.getId());
    role.setRoleCode(roleCodes.get(i));
    role.setRoleType(roleTypes.getOrDefault(role.getRoleCode(), ClocktowerRoleType.TOWNSFOLK));
    role.setSortOrder(i + 1);
    boardRoleRepository.save(role);
}
return toResponse(saved, roleCodes, boardValidation);
```

- [ ] **Step 7: Update the lambda provider in `ClocktowerBoardServiceTests`**

In `saveBoardConfigResponsePreservesRoleCodesAndAddsRoleSummaries`, replace the lambda provider with:

```java
RoleMetadataProvider provider = new RoleMetadataProvider() {
    private final List<ClocktowerRoleSummaryResponse> roles = List.of(
            new ClocktowerRoleSummaryResponse(ClocktowerScriptCode.TROUBLE_BREWING, "CHEF", "厨师",
                    ClocktowerRoleType.TOWNSFOLK, ClocktowerAlignment.GOOD),
            new ClocktowerRoleSummaryResponse(ClocktowerScriptCode.TROUBLE_BREWING, "POISONER", "投毒者",
                    ClocktowerRoleType.MINION, ClocktowerAlignment.EVIL));

    @Override
    public List<ClocktowerRoleSummaryResponse> roles(ClocktowerScriptCode scriptCode) {
        return roles.stream().filter(role -> role.scriptCode() == scriptCode).toList();
    }

    @Override
    public List<ClocktowerRoleSummaryResponse> enabledRoles(java.util.Collection<String> roleCodes) {
        return roles.stream().filter(role -> roleCodes.contains(role.roleCode())).toList();
    }
};
```

- [ ] **Step 8: Run board service tests**

Run:

```bash
cd be
./mvnw -Dtest=ClocktowerBoardServiceTests test
```

Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add be/src/main/java/top/egon/mario/clocktower/script/repository/ClocktowerRoleRepository.java \
  be/src/main/java/top/egon/mario/clocktower/board/service/RoleMetadataProvider.java \
  be/src/main/java/top/egon/mario/clocktower/board/service/impl/RepositoryRoleMetadataProvider.java \
  be/src/main/java/top/egon/mario/clocktower/board/service/impl/ClocktowerBoardServiceImpl.java \
  be/src/test/java/top/egon/mario/clocktower/board/ClocktowerBoardTestFactory.java \
  be/src/test/java/top/egon/mario/clocktower/board/ClocktowerBoardServiceTests.java
git commit -m "feat(clocktower): revalidate saved boards"
```

---

### Task 3: Add Personal Paged Board Library API

**Files:**
- Create: `be/src/main/java/top/egon/mario/clocktower/board/dto/request/ClocktowerBoardQuery.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/board/repository/ClocktowerBoardConfigRepository.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/board/service/ClocktowerBoardService.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/board/service/impl/ClocktowerBoardServiceImpl.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/board/web/ClocktowerBoardController.java`
- Modify: `be/src/test/java/top/egon/mario/clocktower/board/ClocktowerBoardServiceTests.java`
- Test: `be/src/test/java/top/egon/mario/clocktower/board/ClocktowerBoardServiceTests.java`

- [ ] **Step 1: Write failing service tests for owner filtering and delete ownership**

Add imports:

```java
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import top.egon.mario.clocktower.board.dto.request.ClocktowerBoardQuery;
import top.egon.mario.clocktower.common.ClocktowerException;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
```

Add these tests to `ClocktowerBoardServiceTests`:

```java
@Test
void listBoardsFiltersByCurrentUserAndQuery() {
    RoleMetadataProvider provider = ClocktowerBoardTestFactory.roleMetadataProvider();
    ClocktowerBoardConfigRepository configRepository = mock(ClocktowerBoardConfigRepository.class);
    ClocktowerBoardRoleRepository roleRepository = mock(ClocktowerBoardRoleRepository.class);
    ClocktowerBoardConfigPo config = new ClocktowerBoardConfigPo();
    config.setId(7L);
    config.setBoardCode("CTB-TEST");
    config.setScriptCode(ClocktowerScriptCode.TROUBLE_BREWING);
    config.setPlayerCount(5);
    config.setValid(true);
    config.setCreatedBy(1L);
    config.setCreatedAt(java.time.Instant.parse("2026-06-19T02:00:00Z"));
    config.setValidationJson("""
            {"valid":true,"roleTypeCounts":{"TOWNSFOLK":3,"MINION":1,"DEMON":1},"violations":[],"scores":[]}
            """);
    when(configRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(PageRequest.class)))
            .thenReturn(new PageImpl<>(List.of(config), PageRequest.of(0, 20), 1));
    when(roleRepository.findByBoardConfigIdAndDeletedFalseOrderBySortOrderAsc(7L)).thenReturn(List.of());
    ClocktowerBoardService service = new ClocktowerBoardServiceImpl(provider,
            ClocktowerBoardTestFactory.ruleEngine(), configRepository, roleRepository, new ObjectMapper());

    Page<ClocktowerBoardConfigResponse> page = service.list(new ClocktowerBoardQuery(
            ClocktowerScriptCode.TROUBLE_BREWING, 5, true), PageRequest.of(0, 20), principal(1L));

    assertThat(page.getTotalElements()).isEqualTo(1);
    assertThat(page.getContent().getFirst().boardCode()).isEqualTo("CTB-TEST");
    assertThat(page.getContent().getFirst().valid()).isTrue();
}

@Test
void deleteRejectsBoardOwnedByAnotherUser() {
    RoleMetadataProvider provider = ClocktowerBoardTestFactory.roleMetadataProvider();
    ClocktowerBoardConfigRepository configRepository = mock(ClocktowerBoardConfigRepository.class);
    ClocktowerBoardRoleRepository roleRepository = mock(ClocktowerBoardRoleRepository.class);
    ClocktowerBoardConfigPo config = new ClocktowerBoardConfigPo();
    config.setId(7L);
    config.setCreatedBy(2L);
    when(configRepository.findByIdAndDeletedFalse(7L)).thenReturn(java.util.Optional.of(config));
    ClocktowerBoardService service = new ClocktowerBoardServiceImpl(provider,
            ClocktowerBoardTestFactory.ruleEngine(), configRepository, roleRepository, new ObjectMapper());

    assertThatThrownBy(() -> service.delete(7L, principal(1L)))
            .isInstanceOf(ClocktowerException.class)
            .hasMessageContaining("CLOCKTOWER_BOARD_FORBIDDEN");
}
```

- [ ] **Step 2: Run board service tests to verify they fail**

Run:

```bash
cd be
./mvnw -Dtest=ClocktowerBoardServiceTests test
```

Expected: FAIL because the service interface still returns `List` and delete does not check owner.

- [ ] **Step 3: Create board query DTO**

Create `be/src/main/java/top/egon/mario/clocktower/board/dto/request/ClocktowerBoardQuery.java`:

```java
package top.egon.mario.clocktower.board.dto.request;

import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;

public record ClocktowerBoardQuery(
        ClocktowerScriptCode scriptCode,
        Integer playerCount,
        Boolean valid
) {
}
```

- [ ] **Step 4: Update repository capabilities**

Replace `ClocktowerBoardConfigRepository` with:

```java
package top.egon.mario.clocktower.board.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import top.egon.mario.clocktower.board.po.ClocktowerBoardConfigPo;

import java.util.List;
import java.util.Optional;

public interface ClocktowerBoardConfigRepository extends JpaRepository<ClocktowerBoardConfigPo, Long>,
        JpaSpecificationExecutor<ClocktowerBoardConfigPo> {

    Optional<ClocktowerBoardConfigPo> findByIdAndDeletedFalse(Long id);

    Optional<ClocktowerBoardConfigPo> findByIdAndCreatedByAndDeletedFalse(Long id, Long createdBy);

    List<ClocktowerBoardConfigPo> findByDeletedFalseOrderByIdDesc();

    Optional<ClocktowerBoardConfigPo> findByBoardCodeAndDeletedFalse(String boardCode);

    Optional<ClocktowerBoardConfigPo> findByBoardCodeAndCreatedByAndDeletedFalse(String boardCode, Long createdBy);
}
```

- [ ] **Step 5: Update service contract**

Replace `ClocktowerBoardService` with:

```java
package top.egon.mario.clocktower.board.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import top.egon.mario.clocktower.board.dto.request.ClocktowerBoardGenerateRequest;
import top.egon.mario.clocktower.board.dto.request.ClocktowerBoardQuery;
import top.egon.mario.clocktower.board.dto.request.ClocktowerBoardSaveRequest;
import top.egon.mario.clocktower.board.dto.request.ClocktowerBoardValidateRequest;
import top.egon.mario.clocktower.board.dto.response.BoardValidationResponse;
import top.egon.mario.clocktower.board.dto.response.ClocktowerBoardConfigResponse;
import top.egon.mario.clocktower.board.dto.response.ClocktowerBoardGenerateResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

public interface ClocktowerBoardService {

    BoardValidationResponse validate(ClocktowerBoardValidateRequest request);

    ClocktowerBoardGenerateResponse generate(ClocktowerBoardGenerateRequest request, RbacPrincipal principal);

    ClocktowerBoardConfigResponse save(ClocktowerBoardSaveRequest request, RbacPrincipal principal);

    Page<ClocktowerBoardConfigResponse> list(ClocktowerBoardQuery query, Pageable pageable, RbacPrincipal principal);

    ClocktowerBoardConfigResponse usableBoard(Long boardConfigId, String boardCode, RbacPrincipal principal);

    void delete(Long boardId, RbacPrincipal principal);
}
```

- [ ] **Step 6: Implement paged owner filtering**

In `ClocktowerBoardServiceImpl`, add imports:

```java
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import top.egon.mario.clocktower.board.dto.request.ClocktowerBoardQuery;
import top.egon.mario.clocktower.common.ClocktowerException;
```

Replace `list()` with:

```java
@Override
public Page<ClocktowerBoardConfigResponse> list(ClocktowerBoardQuery query, Pageable pageable,
                                                RbacPrincipal principal) {
    return boardConfigRepository.findAll(boardSpecification(query, principal), pageable)
            .map(config -> {
                List<String> roleCodes = boardRoleRepository
                        .findByBoardConfigIdAndDeletedFalseOrderBySortOrderAsc(config.getId())
                        .stream()
                        .map(ClocktowerBoardRolePo::getRoleCode)
                        .toList();
                return toResponse(config, roleCodes, readValidation(config.getValidationJson()));
            });
}
```

Add:

```java
private Specification<ClocktowerBoardConfigPo> boardSpecification(ClocktowerBoardQuery query,
                                                                  RbacPrincipal principal) {
    Long userId = principal == null ? null : principal.userId();
    return (root, criteriaQuery, criteriaBuilder) -> {
        List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
        predicates.add(criteriaBuilder.isFalse(root.get("deleted")));
        predicates.add(userId == null
                ? criteriaBuilder.isNull(root.get("createdBy"))
                : criteriaBuilder.equal(root.get("createdBy"), userId));
        if (query != null && query.scriptCode() != null) {
            predicates.add(criteriaBuilder.equal(root.get("scriptCode"), query.scriptCode()));
        }
        if (query != null && query.playerCount() != null) {
            predicates.add(criteriaBuilder.equal(root.get("playerCount"), query.playerCount()));
        }
        if (query != null && query.valid() != null) {
            predicates.add(criteriaBuilder.equal(root.get("valid"), query.valid()));
        }
        return criteriaBuilder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
    };
}
```

- [ ] **Step 7: Implement valid-board lookup and owner-protected delete**

Add to `ClocktowerBoardServiceImpl`:

```java
@Override
public ClocktowerBoardConfigResponse usableBoard(Long boardConfigId, String boardCode, RbacPrincipal principal) {
    ClocktowerBoardConfigPo config = findOwnedBoard(boardConfigId, boardCode, principal);
    if (!config.isValid()) {
        throw new ClocktowerException("CLOCKTOWER_BOARD_INVALID");
    }
    List<String> roleCodes = boardRoleRepository.findByBoardConfigIdAndDeletedFalseOrderBySortOrderAsc(config.getId())
            .stream()
            .map(ClocktowerBoardRolePo::getRoleCode)
            .toList();
    return toResponse(config, roleCodes, readValidation(config.getValidationJson()));
}
```

Replace `delete` with:

```java
@Override
@Transactional
public void delete(Long boardId, RbacPrincipal principal) {
    ClocktowerBoardConfigPo config = boardConfigRepository.findByIdAndDeletedFalse(boardId)
            .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_BOARD_NOT_FOUND"));
    requireBoardOwner(config, principal);
    config.setDeleted(true);
    boardConfigRepository.save(config);
}
```

Add helper methods:

```java
private ClocktowerBoardConfigPo findOwnedBoard(Long boardConfigId, String boardCode, RbacPrincipal principal) {
    ClocktowerBoardConfigPo config;
    if (boardConfigId != null) {
        config = boardConfigRepository.findByIdAndDeletedFalse(boardConfigId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_BOARD_NOT_FOUND"));
    } else if (boardCode != null && !boardCode.isBlank()) {
        config = boardConfigRepository.findByBoardCodeAndDeletedFalse(boardCode)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_BOARD_NOT_FOUND"));
    } else {
        throw new ClocktowerException("CLOCKTOWER_BOARD_NOT_FOUND");
    }
    requireBoardOwner(config, principal);
    return config;
}

private void requireBoardOwner(ClocktowerBoardConfigPo config, RbacPrincipal principal) {
    Long userId = principal == null ? null : principal.userId();
    if (!Objects.equals(config.getCreatedBy(), userId)) {
        throw new ClocktowerException("CLOCKTOWER_BOARD_FORBIDDEN");
    }
}
```

- [ ] **Step 8: Update controller list endpoint**

In `ClocktowerBoardController`, add imports:

```java
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.RequestParam;
import top.egon.mario.clocktower.board.dto.request.ClocktowerBoardQuery;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.common.api.PageResult;
```

Replace `list()` with:

```java
@GetMapping
public Mono<ApiResponse<PageResult<ClocktowerBoardConfigResponse>>> list(
        @RequestParam(required = false) ClocktowerScriptCode scriptCode,
        @RequestParam(required = false) Integer playerCount,
        @RequestParam(required = false) Boolean valid,
        @RequestParam(defaultValue = "1") @Min(1) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size,
        @AuthenticationPrincipal RbacPrincipal principal) {
    ClocktowerBoardQuery query = new ClocktowerBoardQuery(scriptCode, playerCount, valid);
    PageRequest pageRequest = PageRequest.of(Math.max(page - 1, 0), size,
            Sort.by("createdAt").descending().and(Sort.by("id").descending()));
    return blocking(() -> pageResult(boardService.list(query, pageRequest, principal)));
}
```

Add private helper:

```java
private <T> PageResult<T> pageResult(org.springframework.data.domain.Page<T> page) {
    return new PageResult<>(page.getContent(), page.getNumber() + 1, page.getSize(),
            page.getTotalElements(), page.getTotalPages());
}
```

- [ ] **Step 9: Update room test fake service signatures**

In `ClocktowerRoomServiceTests.RejectEmptyBoardService`, replace `list()` with:

```java
@Override
public org.springframework.data.domain.Page<ClocktowerBoardConfigResponse> list(
        ClocktowerBoardQuery query, org.springframework.data.domain.Pageable pageable, RbacPrincipal principal) {
    return org.springframework.data.domain.Page.empty(pageable);
}

@Override
public ClocktowerBoardConfigResponse usableBoard(Long boardConfigId, String boardCode, RbacPrincipal principal) {
    throw new UnsupportedOperationException();
}
```

Add import:

```java
import top.egon.mario.clocktower.board.dto.request.ClocktowerBoardQuery;
```

- [ ] **Step 10: Run backend tests**

Run:

```bash
cd be
./mvnw -Dtest=ClocktowerBoardServiceTests,ClocktowerBoardControllerTests,ClocktowerRoomServiceTests test
```

Expected: PASS.

- [ ] **Step 11: Commit**

```bash
git add be/src/main/java/top/egon/mario/clocktower/board/dto/request/ClocktowerBoardQuery.java \
  be/src/main/java/top/egon/mario/clocktower/board/repository/ClocktowerBoardConfigRepository.java \
  be/src/main/java/top/egon/mario/clocktower/board/service/ClocktowerBoardService.java \
  be/src/main/java/top/egon/mario/clocktower/board/service/impl/ClocktowerBoardServiceImpl.java \
  be/src/main/java/top/egon/mario/clocktower/board/web/ClocktowerBoardController.java \
  be/src/test/java/top/egon/mario/clocktower/board/ClocktowerBoardServiceTests.java \
  be/src/test/java/top/egon/mario/clocktower/room/ClocktowerRoomServiceTests.java
git commit -m "feat(clocktower): page personal board library"
```

---

### Task 4: Enforce Valid Board Usage When Creating Rooms

**Files:**
- Modify: `be/src/main/java/top/egon/mario/clocktower/room/service/impl/ClocktowerRoomServiceImpl.java`
- Modify: `be/src/test/java/top/egon/mario/clocktower/room/ClocktowerRoomServiceTests.java`
- Test: `be/src/test/java/top/egon/mario/clocktower/room/ClocktowerRoomServiceTests.java`

- [ ] **Step 1: Write failing room creation tests**

Add this test to `ClocktowerRoomServiceTests`:

```java
@Test
void createRoomUsesValidSavedBoardRoles() {
    ClocktowerRoomService service = ClocktowerRoomTestFactory.context(new SavedBoardService(true)).roomService();
    ClocktowerRoomCreateRequest request = new ClocktowerRoomCreateRequest(
            "周五暗流", ClocktowerScriptCode.TROUBLE_BREWING, 5, 42L, null,
            List.of(), "HUMAN", false, true, 0);

    ClocktowerRoomResponse room = service.create(request, principal(1L, "mario"));

    assertThat(room.status()).isEqualTo(ClocktowerRoomStatus.LOBBY);
    assertThat(room.seats()).hasSize(5);
}

@Test
void createRoomRejectsInvalidSavedBoard() {
    ClocktowerRoomService service = ClocktowerRoomTestFactory.context(new SavedBoardService(false)).roomService();
    ClocktowerRoomCreateRequest request = new ClocktowerRoomCreateRequest(
            "周五暗流", ClocktowerScriptCode.TROUBLE_BREWING, 5, 42L, null,
            List.of(), "HUMAN", false, true, 0);

    assertThatThrownBy(() -> service.create(request, principal(1L, "mario")))
            .isInstanceOf(ClocktowerException.class)
            .hasMessageContaining("CLOCKTOWER_BOARD_INVALID");
}
```

Add this fake service inside the test class:

```java
private static final class SavedBoardService implements ClocktowerBoardService {

    private final boolean valid;

    private SavedBoardService(boolean valid) {
        this.valid = valid;
    }

    @Override
    public BoardValidationResponse validate(ClocktowerBoardValidateRequest request) {
        return new BoardValidationResponse(valid, new ClocktowerRoleTypeCountResponse(3, 0, 1, 1, 0, 0),
                valid ? List.of() : List.of(new top.egon.mario.clocktower.board.dto.response.ClocktowerRuleViolationResponse(
                        "BOARD_ROLE_COUNT_MISMATCH", "角色数量必须和玩家人数一致。", "ERROR")), List.of());
    }

    @Override
    public ClocktowerBoardGenerateResponse generate(ClocktowerBoardGenerateRequest request, RbacPrincipal principal) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ClocktowerBoardConfigResponse save(ClocktowerBoardSaveRequest request, RbacPrincipal principal) {
        throw new UnsupportedOperationException();
    }

    @Override
    public org.springframework.data.domain.Page<ClocktowerBoardConfigResponse> list(
            ClocktowerBoardQuery query, org.springframework.data.domain.Pageable pageable, RbacPrincipal principal) {
        return org.springframework.data.domain.Page.empty(pageable);
    }

    @Override
    public ClocktowerBoardConfigResponse usableBoard(Long boardConfigId, String boardCode, RbacPrincipal principal) {
        if (!valid) {
            throw new ClocktowerException("CLOCKTOWER_BOARD_INVALID");
        }
        return new ClocktowerBoardConfigResponse(boardConfigId, "CTB-TEST", ClocktowerScriptCode.TROUBLE_BREWING,
                5, true, java.time.Instant.parse("2026-06-19T03:00:00Z"),
                List.of("EMPATH", "CHEF", "MONK", "POISONER", "IMP"), List.of(),
                new top.egon.mario.clocktower.board.dto.response.ClocktowerBoardValidationResponse(true,
                        java.util.Map.of("TOWNSFOLK", 3, "MINION", 1, "DEMON", 1), List.of(), List.of()));
    }

    @Override
    public void delete(Long boardId, RbacPrincipal principal) {
    }
}
```

Add import:

```java
import top.egon.mario.clocktower.board.dto.request.ClocktowerBoardQuery;
```

- [ ] **Step 2: Run room service tests to verify failure**

Run:

```bash
cd be
./mvnw -Dtest=ClocktowerRoomServiceTests test
```

Expected: FAIL because create room ignores `boardConfigId`.

- [ ] **Step 3: Resolve saved board roles before direct roleCodes**

In `ClocktowerRoomServiceImpl.create`, replace:

```java
List<String> roleCodes = request.roleCodes() == null ? List.of() : request.roleCodes();
```

with:

```java
List<String> roleCodes = resolveCreateRoomRoleCodes(request, principal);
```

Add this helper near `create`:

```java
private List<String> resolveCreateRoomRoleCodes(ClocktowerRoomCreateRequest request, RbacPrincipal principal) {
    if (request.boardConfigId() != null || StringUtils.hasText(request.boardCode())) {
        ClocktowerBoardConfigResponse board = boardService.usableBoard(request.boardConfigId(), request.boardCode(),
                principal);
        if (board.scriptCode() != request.scriptCode() || board.playerCount() != request.playerCount()) {
            throw new ClocktowerException("CLOCKTOWER_BOARD_INVALID");
        }
        return board.roleCodes();
    }
    return request.roleCodes() == null ? List.of() : request.roleCodes();
}
```

Add import:

```java
import top.egon.mario.clocktower.board.dto.response.ClocktowerBoardConfigResponse;
```

- [ ] **Step 4: Run room service tests**

Run:

```bash
cd be
./mvnw -Dtest=ClocktowerRoomServiceTests test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add be/src/main/java/top/egon/mario/clocktower/room/service/impl/ClocktowerRoomServiceImpl.java \
  be/src/test/java/top/egon/mario/clocktower/room/ClocktowerRoomServiceTests.java
git commit -m "feat(clocktower): require valid boards for rooms"
```

---

### Task 5: Update Frontend Board API Types And Service

**Files:**
- Modify: `fe/src/modules/clocktower/clocktowerTypes.ts`
- Modify: `fe/src/modules/clocktower/clocktowerService.ts`
- Modify: `fe/src/modules/clocktower/clocktowerService.test.ts`
- Test: `fe/src/modules/clocktower/clocktowerService.test.ts`

- [ ] **Step 1: Write failing service tests**

In `fe/src/modules/clocktower/clocktowerService.test.ts`, add imports:

```ts
import {
    listClocktowerBoards,
    validateClocktowerBoard,
} from './clocktowerService'
```

If the file already imports from `clocktowerService`, add these names to the existing import list.

Add tests:

```ts
it('validates boards with POST body', async () => {
    const {requestJson} = await import('../../services/request')
    const request = {
        scriptCode: 'TROUBLE_BREWING' as const,
        playerCount: 5,
        roleCodes: ['EMPATH', 'CHEF', 'MONK', 'POISONER', 'IMP'],
    }

    await validateClocktowerBoard(request)

    expect(requestJson).toHaveBeenCalledWith('/api/clocktower/boards/validate', {
        method: 'POST',
        body: request,
    })
})

it('lists boards with page and filters', async () => {
    const {requestJson} = await import('../../services/request')

    await listClocktowerBoards({page: 3, size: 40, scriptCode: 'TROUBLE_BREWING', playerCount: 5, valid: true})

    expect(requestJson).toHaveBeenCalledWith(
        '/api/clocktower/boards?page=3&size=40&scriptCode=TROUBLE_BREWING&playerCount=5&valid=true',
    )
})
```

Update the existing save test request by removing `validation`:

```ts
const request = {
    scriptCode: 'TROUBLE_BREWING' as const,
    playerCount: 5,
    difficulty: 2,
    chaos: 2,
    evilPressure: 2,
    newbieFriendly: true,
    seed: 'seed-1',
    roleCodes: ['CHEF', 'IMP'],
}
```

- [ ] **Step 2: Run service tests to verify failure**

Run:

```bash
cd fe
bun test src/modules/clocktower/clocktowerService.test.ts
```

Expected: FAIL because `listClocktowerBoards` has no query params and save types still accept trusted validation.

- [ ] **Step 3: Update TypeScript types**

In `fe/src/modules/clocktower/clocktowerTypes.ts`, add:

```ts
import type {PageResult} from '../../types/api'
```

Near the board types, add:

```ts
export type ClocktowerPage<T> = PageResult<T>

export type ClocktowerBoardQuery = {
    scriptCode?: ClocktowerScriptCode
    playerCount?: number
    valid?: boolean
    page?: number
    size?: number
}
```

Replace `ClocktowerBoardSaveRequest` with:

```ts
export type ClocktowerBoardSaveRequest = {
    scriptCode: ClocktowerScriptCode
    playerCount: number
    difficulty: number
    chaos: number
    evilPressure: number
    newbieFriendly: boolean
    seed?: string | null
    roleCodes: string[]
}
```

Replace `ClocktowerBoardConfigResponse` with:

```ts
export type ClocktowerBoardConfigResponse = {
    boardId: number
    boardCode: string
    scriptCode: ClocktowerScriptCode
    playerCount: number
    valid: boolean
    createdAt?: string | null
    roleCodes: string[]
    roles?: ClocktowerRoleSummaryResponse[]
    validation: ClocktowerBoardValidationResponse
}
```

- [ ] **Step 4: Update board service list query**

In `fe/src/modules/clocktower/clocktowerService.ts`, import the new types:

```ts
ClocktowerBoardQuery,
ClocktowerPage,
```

Replace `listClocktowerBoards` with:

```ts
export function listClocktowerBoards(params: ClocktowerBoardQuery = {}) {
    const search = buildSearchParams({
        page: params.page ?? 1,
        size: params.size ?? 20,
        scriptCode: params.scriptCode,
        playerCount: params.playerCount,
        valid: params.valid,
    })
    return requestJson<ClocktowerPage<ClocktowerBoardConfigResponse>>(`/api/clocktower/boards${suffix(search)}`)
}
```

- [ ] **Step 5: Run service tests**

Run:

```bash
cd fe
bun test src/modules/clocktower/clocktowerService.test.ts
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add fe/src/modules/clocktower/clocktowerTypes.ts \
  fe/src/modules/clocktower/clocktowerService.ts \
  fe/src/modules/clocktower/clocktowerService.test.ts
git commit -m "feat(clocktower): page board library api client"
```

---

### Task 6: Add Role TreeSelect And Board Editor Library UI

**Files:**
- Create: `fe/src/modules/clocktower/components/RoleTreeSelect.tsx`
- Create: `fe/src/modules/clocktower/components/RoleTreeSelect.test.tsx`
- Modify: `fe/src/modules/clocktower/components/BoardCandidateTable.tsx`
- Modify: `fe/src/modules/clocktower/BoardBuilderPage.tsx`
- Modify: `fe/src/modules/clocktower/BoardBuilderPage.test.tsx`
- Test: `fe/src/modules/clocktower/components/RoleTreeSelect.test.tsx`
- Test: `fe/src/modules/clocktower/BoardBuilderPage.test.tsx`

- [ ] **Step 1: Write failing role tree helper tests**

Create `fe/src/modules/clocktower/components/RoleTreeSelect.test.tsx`:

```tsx
import {describe, expect, test} from 'vitest'
import {buildRoleTreeData, selectedRoleCountText} from './RoleTreeSelect'
import type {ClocktowerRoleResponse} from '../clocktowerTypes'

const roles: ClocktowerRoleResponse[] = [
    {
        scriptCode: 'TROUBLE_BREWING',
        roleCode: 'CHEF',
        roleName: '厨师',
        name: '厨师',
        roleType: 'TOWNSFOLK',
        alignment: 'GOOD',
        abilityText: '首夜得知相邻邪恶玩家有多少对。',
        enabled: true,
    },
    {
        scriptCode: 'TROUBLE_BREWING',
        roleCode: 'IMP',
        roleName: '小恶魔',
        name: '小恶魔',
        roleType: 'DEMON',
        alignment: 'EVIL',
        abilityText: '每晚选择一名玩家死亡。',
        enabled: true,
    },
]

describe('RoleTreeSelect helpers', () => {
    test('groups roles by role type with selectable leaf roles', () => {
        const tree = buildRoleTreeData(roles)

        expect(tree).toEqual([
            {
                title: '镇民',
                value: 'group-TOWNSFOLK',
                key: 'group-TOWNSFOLK',
                selectable: false,
                disableCheckbox: true,
                children: [{
                    title: '厨师 (CHEF)',
                    value: 'CHEF',
                    key: 'CHEF',
                    searchText: '厨师 CHEF',
                }],
            },
            {
                title: '恶魔',
                value: 'group-DEMON',
                key: 'group-DEMON',
                selectable: false,
                disableCheckbox: true,
                children: [{
                    title: '小恶魔 (IMP)',
                    value: 'IMP',
                    key: 'IMP',
                    searchText: '小恶魔 IMP',
                }],
            },
        ])
    })

    test('formats selected role count', () => {
        expect(selectedRoleCountText(['CHEF', 'IMP'], 5)).toBe('已选 2 / 目标 5')
    })
})
```

- [ ] **Step 2: Run role tree tests to verify failure**

Run:

```bash
cd fe
bun test src/modules/clocktower/components/RoleTreeSelect.test.tsx
```

Expected: FAIL because `RoleTreeSelect.tsx` does not exist.

- [ ] **Step 3: Create RoleTreeSelect component**

Create `fe/src/modules/clocktower/components/RoleTreeSelect.tsx`:

```tsx
import {TreeSelect} from 'antd'
import {enumCode, enumDesc} from '../../../utils/enum'
import type {ClocktowerRoleResponse, ClocktowerRoleTypeCode} from '../clocktowerTypes'

const roleTypeOrder: ClocktowerRoleTypeCode[] = ['TOWNSFOLK', 'OUTSIDER', 'MINION', 'DEMON', 'TRAVELER', 'FABLED']

const roleTypeLabels: Record<ClocktowerRoleTypeCode, string> = {
    TOWNSFOLK: '镇民',
    OUTSIDER: '外来者',
    MINION: '爪牙',
    DEMON: '恶魔',
    TRAVELER: '旅行者',
    FABLED: '传奇',
}

type RoleTreeNode = {
    title: string
    value: string
    key: string
    selectable?: boolean
    disableCheckbox?: boolean
    searchText?: string
    children?: RoleTreeNode[]
}

type RoleTreeSelectProps = {
    value?: string[]
    onChange?: (value: string[]) => void
    roles: ClocktowerRoleResponse[]
    loading?: boolean
    disabled?: boolean
    placeholder?: string
}

export function RoleTreeSelect({
    value = [],
    onChange,
    roles,
    loading = false,
    disabled = false,
    placeholder = '选择角色',
}: RoleTreeSelectProps) {
    return (
        <TreeSelect
            allowClear
            disabled={disabled}
            filterTreeNode={(input, node) => String(node.searchText ?? node.title)
                .toLowerCase()
                .includes(input.toLowerCase())}
            loading={loading}
            maxTagCount="responsive"
            onChange={(nextValue) => onChange?.(Array.isArray(nextValue) ? nextValue : [])}
            placeholder={placeholder}
            showCheckedStrategy={TreeSelect.SHOW_CHILD}
            showSearch
            style={{minWidth: 360, width: '100%'}}
            treeCheckable
            treeData={buildRoleTreeData(roles)}
            value={value}
        />
    )
}

export function buildRoleTreeData(roles: ClocktowerRoleResponse[]): RoleTreeNode[] {
    return roleTypeOrder
        .map((roleType) => {
            const children = roles
                .filter((role) => normalizeRoleType(role.roleType) === roleType)
                .map((role) => ({
                    title: `${role.roleName} (${role.roleCode})`,
                    value: role.roleCode,
                    key: role.roleCode,
                    searchText: `${role.roleName} ${role.roleCode}`,
                }))
            return {
                title: roleTypeLabels[roleType],
                value: `group-${roleType}`,
                key: `group-${roleType}`,
                selectable: false,
                disableCheckbox: true,
                children,
            }
        })
        .filter((node) => node.children.length > 0)
}

export function selectedRoleCountText(roleCodes: string[] | undefined, playerCount: number | undefined) {
    return `已选 ${roleCodes?.length ?? 0} / 目标 ${playerCount ?? 0}`
}

function normalizeRoleType(roleType: ClocktowerRoleResponse['roleType']): ClocktowerRoleTypeCode | undefined {
    if (typeof roleType === 'string') {
        return roleType
    }
    const code = enumCode(roleType)
    if (typeof code === 'number') {
        return roleTypeOrder[code - 1]
    }
    const desc = enumDesc(roleType)
    return roleTypeOrder.find((type) => roleTypeLabels[type] === desc)
}
```

- [ ] **Step 4: Run role tree tests**

Run:

```bash
cd fe
bun test src/modules/clocktower/components/RoleTreeSelect.test.tsx
```

Expected: PASS.

- [ ] **Step 5: Add candidate copy action test**

In `BoardBuilderPage.test.tsx`, add an assertion to the existing shell test:

```ts
expect(markup).toContain('保存当前配板')
expect(markup).toContain('校验结果')
```

In the saved board localized test data, add the new fields:

```ts
valid: true,
createdAt: '2026-06-19T00:00:00Z',
```

- [ ] **Step 6: Update BoardCandidateTable with copy action**

In `fe/src/modules/clocktower/components/BoardCandidateTable.tsx`, update props:

```ts
type BoardCandidateTableProps = {
    candidates: ClocktowerBoardCandidateResponse[]
    loading?: boolean
    savingCandidateId?: string
    onCopyToEditor?: (candidate: ClocktowerBoardCandidateResponse) => void
    onSave?: (candidate: ClocktowerBoardCandidateResponse) => Promise<void>
}
```

Change the component signature:

```tsx
export function BoardCandidateTable({candidates, loading, savingCandidateId, onCopyToEditor, onSave}: BoardCandidateTableProps) {
```

Replace the operation render block with:

```tsx
render: (_, record) => (
    <Space>
        <Button
            disabled={!onCopyToEditor}
            onClick={() => onCopyToEditor?.(record)}
            size="small"
        >
            复制到编辑器
        </Button>
        <Button
            disabled={!onSave}
            loading={savingCandidateId === record.candidateId}
            onClick={() => void onSave?.(record)}
            size="small"
            type="primary"
        >
            保存
        </Button>
    </Space>
),
```

Add `Space` to the Ant Design import:

```ts
import {Button, Space, Table, Tag} from 'antd'
```

- [ ] **Step 7: Refactor BoardBuilderPage imports and state**

In `BoardBuilderPage.tsx`, update imports:

```tsx
import {ExperimentOutlined, ReloadOutlined, SaveOutlined} from '@ant-design/icons'
import {Alert, App, Button, Card, Form, Input, InputNumber, Popconfirm, Select, Space, Switch, Table, Tag, Typography} from 'antd'
import {useCallback, useEffect, useMemo, useState} from 'react'
import {usePageData} from '../../hooks/usePageData'
import {enumCode, enumDesc} from '../../utils/enum'
import {getClocktowerRoles} from './clocktowerService'
import type {
    BoardValidationResponse,
    ClocktowerBoardCandidateResponse,
    ClocktowerBoardConfigResponse,
    ClocktowerBoardGenerateRequest,
    ClocktowerBoardQuery,
    ClocktowerRoleResponse,
    ClocktowerRoleTypeCode,
    ClocktowerScriptCode,
    ClocktowerScriptResponse,
} from './clocktowerTypes'
import {RoleTreeSelect, selectedRoleCountText} from './components/RoleTreeSelect'
```

Replace `ValidateFormValues` with:

```ts
type EditorFormValues = ClocktowerBoardGenerateRequest & {
    roleCodes?: string[]
}

type BoardFilterValues = {
    scriptCode?: ClocktowerScriptCode
    playerCount?: number
    valid?: boolean
}
```

State and forms:

```tsx
const [form] = Form.useForm<EditorFormValues>()
const [filterForm] = Form.useForm<BoardFilterValues>()
const [scripts, setScripts] = useState<ClocktowerScriptResponse[]>([])
const [roles, setRoles] = useState<ClocktowerRoleResponse[]>([])
const [roleLoading, setRoleLoading] = useState(false)
const [candidates, setCandidates] = useState<ClocktowerBoardCandidateResponse[]>([])
const [validation, setValidation] = useState<BoardValidationResponse>()
const [loading, setLoading] = useState(false)
const [validating, setValidating] = useState(false)
const [savingCurrentBoard, setSavingCurrentBoard] = useState(false)
const [savingCandidateId, setSavingCandidateId] = useState<string>()
const [error, setError] = useState<string>()
const selectedScriptCode = Form.useWatch('scriptCode', form)
const selectedRoleCodes = Form.useWatch('roleCodes', form) ?? []
const selectedPlayerCount = Form.useWatch('playerCount', form)
```

- [ ] **Step 8: Use paged board library loader**

Inside `BoardBuilderPage`, add:

```tsx
const loadSavedBoardPage = useCallback((request: { page: number; size: number }) => {
    const filters = filterForm.getFieldsValue()
    const query: ClocktowerBoardQuery = {
        ...request,
        scriptCode: filters.scriptCode,
        playerCount: filters.playerCount,
        valid: filters.valid,
    }
    return listClocktowerBoards(query)
}, [filterForm])

const savedBoardPage = usePageData<ClocktowerBoardConfigResponse>(loadSavedBoardPage, {enabled: false})
```

Update `loadInitialData`:

```tsx
async function loadInitialData() {
    await Promise.all([loadScripts(), savedBoardPage.load(1, savedBoardPage.size)])
}
```

Add:

```tsx
async function loadRoles(scriptCode: ClocktowerScriptCode) {
    setRoleLoading(true)
    try {
        const response = await getClocktowerRoles(scriptCode, {enabled: true})
        setRoles(response)
        const allowed = new Set(response.map((role) => role.roleCode))
        const currentRoleCodes = form.getFieldValue('roleCodes') ?? []
        form.setFieldValue('roleCodes', currentRoleCodes.filter((roleCode: string) => allowed.has(roleCode)))
    } catch (caught) {
        setError(resolveErrorMessage(caught))
    } finally {
        setRoleLoading(false)
    }
}
```

Add effect:

```tsx
useEffect(() => {
    if (selectedScriptCode) {
        void loadRoles(selectedScriptCode)
        setValidation(undefined)
    }
}, [selectedScriptCode])
```

- [ ] **Step 9: Update generate, validate, save, copy handlers**

Replace `generate` body values type with `EditorFormValues` and keep existing request shape:

```tsx
const values = await form.validateFields()
```

Add:

```tsx
async function validateManualBoard() {
    const values = await form.validateFields(['scriptCode', 'playerCount', 'roleCodes'])
    setValidating(true)
    setError(undefined)
    setValidation(undefined)
    try {
        const response = await validateClocktowerBoard({
            scriptCode: values.scriptCode,
            playerCount: values.playerCount,
            roleCodes: values.roleCodes ?? [],
        })
        setValidation(response)
    } catch (caught) {
        setError(resolveErrorMessage(caught))
    } finally {
        setValidating(false)
    }
}

async function saveCurrentBoard() {
    const values = await form.validateFields()
    setSavingCurrentBoard(true)
    setError(undefined)
    try {
        await saveClocktowerBoard({
            scriptCode: values.scriptCode,
            playerCount: values.playerCount,
            difficulty: values.difficulty,
            chaos: values.chaos,
            evilPressure: values.evilPressure,
            newbieFriendly: values.newbieFriendly,
            seed: values.seed,
            roleCodes: values.roleCodes ?? [],
        })
        message.success('配板已保存')
        await savedBoardPage.reload()
    } catch (caught) {
        setError(resolveErrorMessage(caught))
    } finally {
        setSavingCurrentBoard(false)
    }
}

function copyCandidateToEditor(candidate: ClocktowerBoardCandidateResponse) {
    form.setFieldsValue({
        scriptCode: candidate.scriptCode,
        playerCount: candidate.playerCount,
        roleCodes: candidate.roleCodes,
    })
    setValidation(undefined)
}

function editSavedBoard(record: ClocktowerBoardConfigResponse) {
    form.setFieldsValue({
        scriptCode: record.scriptCode,
        playerCount: record.playerCount,
        roleCodes: record.roleCodes,
    })
    setValidation(undefined)
}
```

Update `saveCandidate` request by removing `validation`:

```tsx
await saveClocktowerBoard({
    scriptCode: candidate.scriptCode,
    playerCount: candidate.playerCount,
    difficulty: values.difficulty,
    chaos: values.chaos,
    evilPressure: values.evilPressure,
    newbieFriendly: values.newbieFriendly,
    seed: values.seed,
    roleCodes: candidate.roleCodes,
})
```

Update `deleteSavedBoard` reload:

```tsx
await savedBoardPage.reload()
```

Add filter submit:

```tsx
async function searchSavedBoards() {
    await savedBoardPage.load(1, savedBoardPage.size)
}
```

- [ ] **Step 10: Replace manual text area with role tree editor**

In the JSX, remove the separate “手动校验” card form. Add this card after the generate form card:

```tsx
<Card style={{marginTop: 16}} title="配板编辑器">
    <Space direction="vertical" size={12} style={{width: '100%'}}>
        <Space align="center" wrap>
            <Typography.Text type={selectedRoleCodes.length === selectedPlayerCount ? 'success' : 'warning'}>
                {selectedRoleCountText(selectedRoleCodes, selectedPlayerCount)}
            </Typography.Text>
            <Button loading={validating} onClick={voidify(validateManualBoard)} type="primary">
                手动校验
            </Button>
            <Button icon={<SaveOutlined/>} loading={savingCurrentBoard} onClick={voidify(saveCurrentBoard)}>
                保存当前配板
            </Button>
        </Space>
        <Form.Item label="角色选择" name="roleCodes">
            <RoleTreeSelect loading={roleLoading} roles={roles}/>
        </Form.Item>
        {validation && (
            <Alert
                showIcon
                title={validation.valid ? '校验通过' : '校验未通过'}
                type={validation.valid ? 'success' : 'warning'}
                description={
                    <Space direction="vertical" size={4}>
                        <span>{countSummary(validation.typeCounts)}</span>
                        {validation.issues.map((issue) => (
                            <span key={issue.code}>{issue.severity}：{issue.message}</span>
                        ))}
                    </Space>
                }
            />
        )}
    </Space>
</Card>
```

- [ ] **Step 11: Update saved board table filters and pagination**

Replace the saved board card with:

```tsx
<Card style={{marginTop: 16}} title="我的配板库">
    <Form form={filterForm} layout="vertical">
        <Space align="end" wrap>
            <Form.Item label="剧本" name="scriptCode">
                <Select allowClear options={scriptOptions(scripts)} style={{width: 220}}/>
            </Form.Item>
            <Form.Item label="人数" name="playerCount">
                <InputNumber min={5} max={15}/>
            </Form.Item>
            <Form.Item label="校验结果" name="valid">
                <Select
                    allowClear
                    options={[
                        {label: '通过', value: true},
                        {label: '未通过', value: false},
                    ]}
                    style={{width: 140}}
                />
            </Form.Item>
            <Button onClick={voidify(searchSavedBoards)} type="primary">查询</Button>
        </Space>
    </Form>
    <Table
        columns={savedBoardColumns(deleteSavedBoard, editSavedBoard)}
        dataSource={savedBoardPage.records}
        loading={savedBoardPage.loading}
        pagination={{
            current: savedBoardPage.page,
            pageSize: savedBoardPage.size,
            total: savedBoardPage.total,
            showSizeChanger: true,
            onChange: voidify(savedBoardPage.load),
        }}
        rowKey="boardId"
        scroll={{x: 1000}}
    />
</Card>
```

Update `BoardCandidateTable` usage:

```tsx
<BoardCandidateTable
    candidates={candidates}
    loading={loading}
    onCopyToEditor={copyCandidateToEditor}
    onSave={saveCandidate}
    savingCandidateId={savingCandidateId}
/>
```

- [ ] **Step 12: Update saved board columns**

Replace `savedBoardColumns` with:

```tsx
export function savedBoardColumns(
    onDelete: (boardId: number) => Promise<void>,
    onEdit?: (record: ClocktowerBoardConfigResponse) => void,
): ColumnsType<ClocktowerBoardConfigResponse> {
    return [
        {title: '编号', dataIndex: 'boardCode', width: 180, render: (value) => <Tag>{value}</Tag>},
        {title: '剧本', dataIndex: 'scriptCode', width: 160},
        {title: '人数', dataIndex: 'playerCount', width: 90},
        {
            title: '角色',
            dataIndex: 'roleCodes',
            render: (roleCodes: string[], record) => <RoleSummaryTags roleCodes={roleCodes} roles={record.roles}/>,
        },
        {
            title: '校验',
            dataIndex: 'valid',
            width: 120,
            render: (valid: boolean) => valid ? <Tag color="success">通过</Tag> : <Tag color="error">未通过</Tag>,
        },
        {title: '保存时间', dataIndex: 'createdAt', width: 190, render: (value?: string) => value ?? '-'},
        {
            title: '操作',
            fixed: 'right',
            width: 180,
            render: (_, record) => (
                <Space>
                    <Button onClick={() => onEdit?.(record)} size="small">编辑</Button>
                    <Popconfirm
                        cancelText="取消"
                        okText="删除"
                        okType="danger"
                        onConfirm={() => void onDelete(record.boardId)}
                        title="删除已保存配板？"
                    >
                        <Button danger size="small">删除</Button>
                    </Popconfirm>
                </Space>
            ),
        },
    ]
}
```

- [ ] **Step 13: Run frontend board tests**

Run:

```bash
cd fe
bun test src/modules/clocktower/components/RoleTreeSelect.test.tsx src/modules/clocktower/BoardBuilderPage.test.tsx
```

Expected: PASS.

- [ ] **Step 14: Commit**

```bash
git add fe/src/modules/clocktower/components/RoleTreeSelect.tsx \
  fe/src/modules/clocktower/components/RoleTreeSelect.test.tsx \
  fe/src/modules/clocktower/components/BoardCandidateTable.tsx \
  fe/src/modules/clocktower/BoardBuilderPage.tsx \
  fe/src/modules/clocktower/BoardBuilderPage.test.tsx
git commit -m "feat(clocktower): add board editor library ui"
```

---

### Task 7: Use Valid Saved Boards In Room Creation UI

**Files:**
- Modify: `fe/src/modules/clocktower/RoomListPage.tsx`
- Modify: `fe/src/modules/clocktower/clocktowerService.test.ts`
- Test: `fe/src/modules/clocktower/clocktowerService.test.ts`
- Test: `fe/src/modules/clocktower/RoomLobbyPage.test.tsx`

- [ ] **Step 1: Add service test for valid board query used by room creation**

If Task 5 already added the filtered list test, add this focused test to `clocktowerService.test.ts`:

```ts
it('loads valid boards for room creation', async () => {
    const {requestJson} = await import('../../services/request')

    await listClocktowerBoards({page: 1, size: 200, valid: true})

    expect(requestJson).toHaveBeenCalledWith('/api/clocktower/boards?page=1&size=200&valid=true')
})
```

- [ ] **Step 2: Run service test**

Run:

```bash
cd fe
bun test src/modules/clocktower/clocktowerService.test.ts
```

Expected: PASS if Task 5 is complete.

- [ ] **Step 3: Update RoomListPage imports and form values**

In `RoomListPage.tsx`, import board service and role tags:

```tsx
import {createClocktowerRoom, listClocktowerBoards, listClocktowerRooms} from './clocktowerService'
import type {
    ClocktowerBoardConfigResponse,
    ClocktowerRoomCreateRequest,
    ClocktowerRoomResponse,
    ClocktowerRoomStatus,
    ClocktowerScriptCode,
} from './clocktowerTypes'
import {RoleSummaryTags} from './components/RoleSummaryTags'
```

Replace `RoomFormValues` with:

```ts
type RoomFormValues = ClocktowerRoomCreateRequest
```

Add state:

```tsx
const [boards, setBoards] = useState<ClocktowerBoardConfigResponse[]>([])
const [boardLoading, setBoardLoading] = useState(false)
```

- [ ] **Step 4: Load valid boards for the modal**

Add:

```tsx
async function loadValidBoards() {
    setBoardLoading(true)
    try {
        const page = await listClocktowerBoards({page: 1, size: 200, valid: true})
        setBoards(page.records)
    } catch (caught) {
        message.error(resolveErrorMessage(caught))
    } finally {
        setBoardLoading(false)
    }
}
```

Update `openCreator`:

```tsx
function openCreator() {
    form.setFieldsValue({
        name: '钟楼房间',
        scriptCode: 'TROUBLE_BREWING',
        playerCount: 5,
        boardConfigId: null,
        boardCode: null,
        roleCodes: [],
        storytellerMode: 'HUMAN',
        allowSpectators: true,
        allowPrivateChat: true,
        agentSeatCount: 0,
    })
    setCreatorOpen(true)
    void loadValidBoards()
}
```

Add selection helper:

```tsx
function selectBoard(boardId?: number) {
    const board = boards.find((item) => item.boardId === boardId)
    if (!board) {
        form.setFieldsValue({boardConfigId: null, roleCodes: []})
        return
    }
    form.setFieldsValue({
        boardConfigId: board.boardId,
        boardCode: board.boardCode,
        scriptCode: board.scriptCode,
        playerCount: board.playerCount,
        roleCodes: [],
    })
}
```

- [ ] **Step 5: Send boardConfigId instead of roleCodesText**

Replace `saveRoom` body with:

```tsx
async function saveRoom() {
    const values = await form.validateFields()
    setSaving(true)
    try {
        const room = await createClocktowerRoom({
            ...values,
            roleCodes: values.roleCodes ?? [],
        })
        message.success('房间已创建')
        setCreatorOpen(false)
        await loadRooms()
        navigate(`/clocktower/rooms/${room.roomId}/lobby`)
    } catch (caught) {
        message.error(resolveErrorMessage(caught))
    } finally {
        setSaving(false)
    }
}
```

- [ ] **Step 6: Replace role code text area with valid board selector**

Replace the `预设角色代码` Form.Item with:

```tsx
<Form.Item label="通过配板" name="boardConfigId">
    <Select
        allowClear
        loading={boardLoading}
        onChange={selectBoard}
        optionLabelProp="label"
        options={boards.map((board) => ({
            label: `${board.boardCode} · ${board.scriptCode} · ${board.playerCount}人`,
            value: board.boardId,
        }))}
        placeholder="可选，只展示校验通过的配板"
    />
</Form.Item>
<Form.Item shouldUpdate noStyle>
    {() => {
        const board = boards.find((item) => item.boardId === form.getFieldValue('boardConfigId'))
        return board ? (
            <RoleSummaryTags roleCodes={board.roleCodes} roles={board.roles}/>
        ) : null
    }}
</Form.Item>
```

- [ ] **Step 7: Run frontend targeted tests**

Run:

```bash
cd fe
bun test src/modules/clocktower/clocktowerService.test.ts src/modules/clocktower/RoomLobbyPage.test.tsx
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add fe/src/modules/clocktower/RoomListPage.tsx \
  fe/src/modules/clocktower/clocktowerService.test.ts
git commit -m "feat(clocktower): select valid boards for rooms"
```

---

### Task 8: Final Validation And Cleanup

**Files:**
- Review all files changed by Tasks 1-7.

- [ ] **Step 1: Run backend targeted tests**

Run:

```bash
cd be
./mvnw -Dtest=ClocktowerSchemaMigrationTests,ClocktowerBoardServiceTests,ClocktowerBoardControllerTests,ClocktowerRoomServiceTests test
```

Expected: PASS.

- [ ] **Step 2: Run frontend targeted tests**

Run:

```bash
cd fe
bun test src/modules/clocktower/clocktowerService.test.ts src/modules/clocktower/components/RoleTreeSelect.test.tsx src/modules/clocktower/BoardBuilderPage.test.tsx src/modules/clocktower/RoomLobbyPage.test.tsx
```

Expected: PASS.

- [ ] **Step 3: Run full backend tests**

Run:

```bash
cd be
./mvnw test
```

Expected: PASS.

- [ ] **Step 4: Run full frontend tests**

Run:

```bash
cd fe
bun test
```

Expected: PASS.

- [ ] **Step 5: Inspect git diff for unrelated changes**

Run:

```bash
git status --short
git diff --stat
git diff --check
```

Expected:

- `git diff --check` prints no output.
- Diff only includes Clocktower board, room creation, migration, and test files from this plan.

- [ ] **Step 6: Commit final cleanup if any files changed after Task 7**

If Step 5 shows small cleanup changes, commit them:

```bash
git add <cleanup-files>
git commit -m "chore(clocktower): verify board editor library"
```

If Step 5 shows no remaining changes, do not create an empty commit.

---

## Self-Review Notes

Spec coverage:

- Role selector replaces manual role code input: Task 6.
- Tree-shaped role options by role type: Task 6.
- Frontend quantity prompt and backend quantity check: Tasks 2 and 6.
- Backend script-role alignment: Task 2.
- Manual boards can be saved even when invalid: Task 2.
- Saved boards are personal and paged with filters: Task 3 and Task 6.
- No draft/submitted status, only valid/invalid: Tasks 1, 3, 5, 6.
- Only valid boards can be used for rooms: Task 4 and Task 7.
- One Flyway migration and no existing migration edits: Task 1.

Placeholder scan:

- The plan contains no unresolved placeholder sections.

Type consistency:

- Backend valid flag is `valid` in entity, response, query filter, frontend type, and table filter.
- Board list returns `PageResult<ClocktowerBoardConfigResponse>` on the wire and `ClocktowerPage<ClocktowerBoardConfigResponse>` in TypeScript.
- Room creation uses existing `boardConfigId` and `boardCode` fields.
