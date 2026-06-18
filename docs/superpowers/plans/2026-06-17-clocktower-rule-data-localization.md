# Clocktower Rule Data Localization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:
> executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete Clocktower static rule data and make backend-owned Chinese enum/display fields drive the board, rule,
and night-order frontend views.

**Architecture:** Keep the current four Clocktower menu entries and improve the existing script, board, and grimoire
APIs behind them. Add a reviewed static data artifact, backend coded enum contracts, one new Flyway migration for
data/schema evolution, script-scoped board metadata, grouped night-order DTOs, and frontend rendering based on backend
display values. Existing machine-readable official role codes remain stable in API payloads.

**Tech Stack:** Java 21, Spring Boot 3.5, WebFlux, Spring Data JPA, Flyway, JUnit 5, AssertJ, React 19, TypeScript, Ant
Design 6, Bun, Vitest.

---

## Execution Preconditions

- Use an isolated branch or worktree at execution time.
- Do not edit existing Flyway migration files.
- Do not start the application automatically after implementation.
- The final rule-data migration task is data-gated. It cannot be completed until complete three-base-script role and
  night-order data has been supplied and reviewed.
- SQL and data population are intentionally deferred to the final gated phase. During the non-SQL phase, keep current
  `VARCHAR` persistence columns and map them to backend coded-enum DTO values in services instead of converting JPA
  fields to integer-coded converters.
- Role codes use official enum names such as `CHEF`, `EMPATH`, and `IMP`. Do not invent aliases such as `COOKER`.

## SQL-Last Execution Override

The latest execution instruction is to finish and validate every non-SQL task first, then request reviewed data and
generate SQL. Apply these overrides while executing this plan:

- Task 2 should add backend coded enum JSON contracts. Do not apply JPA converters to entities in the non-SQL phase.
- Task 3 should expose `roleName`, `roleType`, `alignment`, `orderNo`, and grouped `firstNight` / `otherNight` responses
  while preserving current entity field types where the database still stores text.
- Task 4 is deferred until the final SQL/data phase because changing JPA fields to integer-coded converters requires a
  matching Flyway migration.
- Task 6 remains the final data-gated phase and will include `ClocktowerRoleCode`, persistence converter application,
  and exactly one new Flyway migration after reviewed data is available.
- Tasks 5 and 7 through 11 should be executed now, using service-layer enum conversion so backend and frontend contracts
  are localized without requiring a schema change.

## Scope Check

The approved spec covers one coherent subsystem: Clocktower rule data localization. It touches backend enum contracts,
static rule data, script/board/grimoire DTOs, and existing frontend views. It does not include a new rule-data
maintenance CRUD page, RAG rule query, role automation, replay analytics, or new sidebar menu entries.

## File Structure

### Data Source

- Create: `docs/clocktower/rule-data/README.md`
    - Documents the accepted CSV schemas and the data gate.
- Create: `docs/clocktower/rule-data/clocktower-base-scripts-roles.csv`
    - Reviewable source of role rows before migration generation.
- Create: `docs/clocktower/rule-data/clocktower-base-scripts-night-order.csv`
    - Reviewable source of night-order rows before migration generation.

### Backend Enum And Persistence Contracts

- Modify: `be/src/main/java/top/egon/mario/clocktower/common/enums/ClocktowerRoleType.java`
    - Convert to coded enum with Chinese descriptions and JSON support.
- Create: `be/src/main/java/top/egon/mario/clocktower/common/enums/ClocktowerAlignment.java`
    - Good/evil alignment enum with Chinese descriptions.
- Create: `be/src/main/java/top/egon/mario/clocktower/common/enums/ClocktowerNightType.java`
    - First-night/other-night enum with Chinese descriptions.
- Create: `be/src/main/java/top/egon/mario/clocktower/common/enums/ClocktowerRoleCode.java`
    - Official role-code enum built from the reviewed role data.
- Create: `be/src/main/java/top/egon/mario/clocktower/common/enums/ClocktowerEnumJsonSupport.java`
    - Clocktower-local copy of the existing coded enum JSON pattern.
- Create: `be/src/main/java/top/egon/mario/clocktower/converter/jpa/AbstractClocktowerCodedEnumConverter.java`
    - Base JPA converter for Clocktower coded enums.
- Create: `be/src/main/java/top/egon/mario/clocktower/converter/jpa/ClocktowerRoleTypeConverter.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/converter/jpa/ClocktowerAlignmentConverter.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/converter/jpa/ClocktowerNightTypeConverter.java`
- Modify: Clocktower entity classes that store role type, alignment, or night type.

### Backend DTO And Services

- Create: `be/src/main/java/top/egon/mario/clocktower/script/dto/response/ClocktowerRoleSummaryResponse.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/script/dto/response/ClocktowerNightOrderGroupResponse.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/script/dto/response/ClocktowerRoleResponse.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/script/dto/response/ClocktowerNightOrderResponse.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/script/service/ClocktowerScriptService.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/script/service/impl/ClocktowerScriptServiceImpl.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/script/web/ClocktowerScriptController.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/board/service/RoleMetadataProvider.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/board/service/impl/RepositoryRoleMetadataProvider.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/board/service/impl/ClocktowerBoardServiceImpl.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/board/dto/response/ClocktowerBoardCandidateResponse.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/board/dto/response/ClocktowerBoardConfigResponse.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/grimoire/dto/response/NightStepResponse.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/grimoire/service/impl/ClocktowerGrimoireServiceImpl.java`

### Database Migration

- Create: `be/src/main/resources/db/migration/V20__complete_clocktower_rule_data.sql`
    - Exactly one migration for this rule-data/database change.
- Modify: `be/src/test/java/top/egon/mario/clocktower/ClocktowerSchemaMigrationTests.java`
    - Assert the new migration exists and contains complete base-script coverage markers.

### Frontend

- Modify: `fe/src/modules/clocktower/clocktowerTypes.ts`
- Modify: `fe/src/modules/clocktower/clocktowerService.ts`
- Modify: `fe/src/modules/clocktower/clocktowerService.test.ts`
- Modify: `fe/src/modules/clocktower/components/RoleTypeTag.tsx`
- Modify: `fe/src/modules/clocktower/components/BoardCandidateTable.tsx`
- Modify: `fe/src/modules/clocktower/RuleDataPage.tsx`
- Modify: `fe/src/modules/clocktower/RuleDataPage.test.tsx`
- Modify: `fe/src/modules/clocktower/BoardBuilderPage.tsx`
- Modify: `fe/src/modules/clocktower/BoardBuilderPage.test.tsx`
- Modify: `fe/src/modules/clocktower/components/NightChecklist.tsx`
- Modify: `fe/src/modules/clocktower/StorytellerGrimoirePage.test.tsx`

---

### Task 1: Add The Static Rule Data Intake Gate

**Files:**

- Create: `docs/clocktower/rule-data/README.md`
- Create: `docs/clocktower/rule-data/clocktower-base-scripts-roles.csv`
- Create: `docs/clocktower/rule-data/clocktower-base-scripts-night-order.csv`
- Modify: `be/src/test/java/top/egon/mario/clocktower/ClocktowerSchemaMigrationTests.java`

- [ ] **Step 1: Write the data-source README**

Create `docs/clocktower/rule-data/README.md` with this content:

~~~markdown
# Clocktower Base Script Rule Data

This directory stores the reviewed source data used to generate the Clocktower rule-data Flyway migration.

`clocktower-base-scripts-roles.csv` columns:

```csv
scriptCode,roleCode,name,roleType,alignment,abilityText,firstNightOrder,otherNightOrder,firstNightReminder,otherNightReminder,sourceUrl
```

`clocktower-base-scripts-night-order.csv` columns:

```csv
scriptCode,nightType,orderNo,roleCode,reminderText
```

Execution gate:

- The implementation must stop before creating `V20__complete_clocktower_rule_data.sql` if these files are missing complete reviewed data for all three base scripts.
- Role codes must use official enum names.
- Existing Flyway migrations must not be edited.
~~~

- [ ] **Step 2: Add CSV files with exact headers**

Create `docs/clocktower/rule-data/clocktower-base-scripts-roles.csv` with the exact first line below, then paste
reviewed role rows under it:

```csv
scriptCode,roleCode,name,roleType,alignment,abilityText,firstNightOrder,otherNightOrder,firstNightReminder,otherNightReminder,sourceUrl
```

Create `docs/clocktower/rule-data/clocktower-base-scripts-night-order.csv` with the exact first line below, then paste
reviewed night-order rows under it:

```csv
scriptCode,nightType,orderNo,roleCode,reminderText
```

If complete reviewed data has not been supplied, create header-only CSV files, commit the README, CSV headers, and
data-source test, then stop execution after Step 5 with a blocker report.

- [ ] **Step 3: Extend the schema migration test with data file checks**

Modify `be/src/test/java/top/egon/mario/clocktower/ClocktowerSchemaMigrationTests.java` by adding imports:

```java
import java.util.List;
```

Add this test method before `migrationRemovesOldBroadRoomPermissionGrant()`:

```java
@Test
void clocktowerRuleDataSourceFilesUseReviewedCsvHeaders() throws IOException {
    Path rolesPath = Path.of("../docs/clocktower/rule-data/clocktower-base-scripts-roles.csv");
    Path nightOrderPath = Path.of("../docs/clocktower/rule-data/clocktower-base-scripts-night-order.csv");

    assertThat(Files.exists(rolesPath)).isTrue();
    assertThat(Files.exists(nightOrderPath)).isTrue();

    List<String> roleLines = Files.readAllLines(rolesPath);
    List<String> nightOrderLines = Files.readAllLines(nightOrderPath);

    assertThat(roleLines).isNotEmpty();
    assertThat(nightOrderLines).isNotEmpty();
    assertThat(roleLines.getFirst()).isEqualTo(
            "scriptCode,roleCode,name,roleType,alignment,abilityText,firstNightOrder,otherNightOrder,firstNightReminder,otherNightReminder,sourceUrl");
    assertThat(nightOrderLines.getFirst()).isEqualTo(
            "scriptCode,nightType,orderNo,roleCode,reminderText");
}
```

- [ ] **Step 4: Run the data-source header test**

Run:

```bash
mvn -q -f be/pom.xml -Dmaven.build.cache.enabled=false -Dtest=ClocktowerSchemaMigrationTests test
```

Expected:

- PASS if both CSV files exist with the exact headers.
- FAIL with an AssertJ existence/header assertion if Step 2 has not created the files.

- [ ] **Step 5: Commit the data intake gate**

```bash
git add docs/clocktower/rule-data/README.md \
  docs/clocktower/rule-data/clocktower-base-scripts-roles.csv \
  docs/clocktower/rule-data/clocktower-base-scripts-night-order.csv \
  be/src/test/java/top/egon/mario/clocktower/ClocktowerSchemaMigrationTests.java
git commit -m "test(clocktower): add rule data source gate"
```

### Task 2: Add Clocktower Coded Enum Contracts

**Files:**

- Modify: `be/src/main/java/top/egon/mario/clocktower/common/enums/ClocktowerRoleType.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/common/enums/ClocktowerAlignment.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/common/enums/ClocktowerNightType.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/common/enums/ClocktowerEnumJsonSupport.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/converter/jpa/AbstractClocktowerCodedEnumConverter.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/converter/jpa/ClocktowerRoleTypeConverter.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/converter/jpa/ClocktowerAlignmentConverter.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/converter/jpa/ClocktowerNightTypeConverter.java`
- Modify: `be/src/test/java/top/egon/mario/clocktower/ClocktowerJpaMappingTests.java`

- [ ] **Step 1: Write the failing enum JSON test**

Add imports to `ClocktowerJpaMappingTests.java`:

```java
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import top.egon.mario.clocktower.common.enums.ClocktowerAlignment;
import top.egon.mario.clocktower.common.enums.ClocktowerNightType;
import top.egon.mario.clocktower.common.enums.ClocktowerRoleType;
```

Add this test method:

```java
@Test
void clocktowerCodedEnumsExposeChineseDescriptions() throws Exception {
    ObjectMapper mapper = new ObjectMapper();

    JsonNode roleTypeJson = mapper.valueToTree(ClocktowerRoleType.TOWNSFOLK);
    JsonNode alignmentJson = mapper.valueToTree(ClocktowerAlignment.GOOD);
    JsonNode nightTypeJson = mapper.valueToTree(ClocktowerNightType.FIRST_NIGHT);

    assertThat(roleTypeJson.get("code").asInt()).isEqualTo(1);
    assertThat(roleTypeJson.get("desc").asText()).isEqualTo("镇民");
    assertThat(alignmentJson.get("code").asInt()).isEqualTo(1);
    assertThat(alignmentJson.get("desc").asText()).isEqualTo("善良");
    assertThat(nightTypeJson.get("code").asInt()).isEqualTo(1);
    assertThat(nightTypeJson.get("desc").asText()).isEqualTo("首夜");

    assertThat(mapper.convertValue(1, ClocktowerRoleType.class)).isEqualTo(ClocktowerRoleType.TOWNSFOLK);
    assertThat(mapper.convertValue("邪恶", ClocktowerAlignment.class)).isEqualTo(ClocktowerAlignment.EVIL);
    assertThat(mapper.convertValue("OTHER_NIGHT", ClocktowerNightType.class)).isEqualTo(ClocktowerNightType.OTHER_NIGHT);
}
```

- [ ] **Step 2: Run the enum test to verify it fails**

Run:

```bash
mvn -q -f be/pom.xml -Dmaven.build.cache.enabled=false -Dtest=ClocktowerJpaMappingTests#clocktowerCodedEnumsExposeChineseDescriptions test
```

Expected: FAIL during compilation because `ClocktowerAlignment` and `ClocktowerNightType` do not exist.

- [ ] **Step 3: Create the Clocktower enum JSON helper**

Create `be/src/main/java/top/egon/mario/clocktower/common/enums/ClocktowerEnumJsonSupport.java`:

```java
package top.egon.mario.clocktower.common.enums;

import top.egon.mario.common.enums.CodedEnum;

import java.util.Map;
import java.util.Objects;

/**
 * JSON helper for Clocktower coded enums.
 */
final class ClocktowerEnumJsonSupport {

    private ClocktowerEnumJsonSupport() {
    }

    static Map<String, Object> toJson(CodedEnum value) {
        return Map.of("code", value.getCode(), "desc", value.getDesc());
    }

    static <E extends Enum<E> & CodedEnum> E fromJson(Class<E> enumType, Object input) {
        if (input == null) {
            return null;
        }
        if (input instanceof Number number) {
            return CodedEnum.fromCode(enumType, number.intValue());
        }
        if (input instanceof Map<?, ?> map && map.get("code") instanceof Number number) {
            return CodedEnum.fromCode(enumType, number.intValue());
        }
        String text = String.valueOf(input).trim();
        for (E value : enumType.getEnumConstants()) {
            if (Objects.equals(value.name(), text) || Objects.equals(value.getDesc(), text)
                    || Objects.equals(String.valueOf(value.getCode()), text)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown " + enumType.getSimpleName() + " value: " + input);
    }
}
```

- [ ] **Step 4: Replace `ClocktowerRoleType` with a coded enum**

Replace `be/src/main/java/top/egon/mario/clocktower/common/enums/ClocktowerRoleType.java` with:

```java
package top.egon.mario.clocktower.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import top.egon.mario.common.enums.CodedEnum;

import java.util.Map;

/**
 * Blood on the Clocktower role category.
 */
@Getter
public enum ClocktowerRoleType implements CodedEnum {
    TOWNSFOLK(1, "镇民"),
    OUTSIDER(2, "外来者"),
    MINION(3, "爪牙"),
    DEMON(4, "恶魔"),
    TRAVELER(5, "旅行者"),
    FABLED(6, "传奇");

    private final int code;
    private final String desc;

    ClocktowerRoleType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    @JsonValue
    public Map<String, Object> toJson() {
        return ClocktowerEnumJsonSupport.toJson(this);
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static ClocktowerRoleType fromJson(Object input) {
        return ClocktowerEnumJsonSupport.fromJson(ClocktowerRoleType.class, input);
    }
}
```

- [ ] **Step 5: Add alignment and night-type enums**

Create `be/src/main/java/top/egon/mario/clocktower/common/enums/ClocktowerAlignment.java`:

```java
package top.egon.mario.clocktower.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import top.egon.mario.common.enums.CodedEnum;

import java.util.Map;

/**
 * Public alignment grouping used by Clocktower role and seat data.
 */
@Getter
public enum ClocktowerAlignment implements CodedEnum {
    GOOD(1, "善良"),
    EVIL(2, "邪恶"),
    NEUTRAL(3, "中立");

    private final int code;
    private final String desc;

    ClocktowerAlignment(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    @JsonValue
    public Map<String, Object> toJson() {
        return ClocktowerEnumJsonSupport.toJson(this);
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static ClocktowerAlignment fromJson(Object input) {
        return ClocktowerEnumJsonSupport.fromJson(ClocktowerAlignment.class, input);
    }
}
```

Create `be/src/main/java/top/egon/mario/clocktower/common/enums/ClocktowerNightType.java`:

```java
package top.egon.mario.clocktower.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import top.egon.mario.common.enums.CodedEnum;

import java.util.Map;

/**
 * Night-order group used by script data and storyteller checklist.
 */
@Getter
public enum ClocktowerNightType implements CodedEnum {
    FIRST_NIGHT(1, "首夜"),
    OTHER_NIGHT(2, "其他夜");

    private final int code;
    private final String desc;

    ClocktowerNightType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    @JsonValue
    public Map<String, Object> toJson() {
        return ClocktowerEnumJsonSupport.toJson(this);
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static ClocktowerNightType fromJson(Object input) {
        return ClocktowerEnumJsonSupport.fromJson(ClocktowerNightType.class, input);
    }
}
```

- [ ] **Step 6: Add JPA converters**

Create `be/src/main/java/top/egon/mario/clocktower/converter/jpa/AbstractClocktowerCodedEnumConverter.java`:

```java
package top.egon.mario.clocktower.converter.jpa;

import jakarta.persistence.AttributeConverter;
import top.egon.mario.common.enums.CodedEnum;

/**
 * Base JPA converter that persists Clocktower coded enums as integer codes.
 */
public abstract class AbstractClocktowerCodedEnumConverter<E extends Enum<E> & CodedEnum>
        implements AttributeConverter<E, Integer> {

    private final Class<E> enumType;

    protected AbstractClocktowerCodedEnumConverter(Class<E> enumType) {
        this.enumType = enumType;
    }

    @Override
    public Integer convertToDatabaseColumn(E attribute) {
        return attribute == null ? null : attribute.getCode();
    }

    @Override
    public E convertToEntityAttribute(Integer dbData) {
        return CodedEnum.fromCode(enumType, dbData);
    }
}
```

Create `ClocktowerRoleTypeConverter.java`:

```java
package top.egon.mario.clocktower.converter.jpa;

import jakarta.persistence.Converter;
import top.egon.mario.clocktower.common.enums.ClocktowerRoleType;

/**
 * Persists Clocktower role type as an integer code.
 */
@Converter(autoApply = false)
public class ClocktowerRoleTypeConverter extends AbstractClocktowerCodedEnumConverter<ClocktowerRoleType> {

    public ClocktowerRoleTypeConverter() {
        super(ClocktowerRoleType.class);
    }
}
```

Create `ClocktowerAlignmentConverter.java`:

```java
package top.egon.mario.clocktower.converter.jpa;

import jakarta.persistence.Converter;
import top.egon.mario.clocktower.common.enums.ClocktowerAlignment;

/**
 * Persists Clocktower alignment as an integer code.
 */
@Converter(autoApply = false)
public class ClocktowerAlignmentConverter extends AbstractClocktowerCodedEnumConverter<ClocktowerAlignment> {

    public ClocktowerAlignmentConverter() {
        super(ClocktowerAlignment.class);
    }
}
```

Create `ClocktowerNightTypeConverter.java`:

```java
package top.egon.mario.clocktower.converter.jpa;

import jakarta.persistence.Converter;
import top.egon.mario.clocktower.common.enums.ClocktowerNightType;

/**
 * Persists Clocktower night type as an integer code.
 */
@Converter(autoApply = false)
public class ClocktowerNightTypeConverter extends AbstractClocktowerCodedEnumConverter<ClocktowerNightType> {

    public ClocktowerNightTypeConverter() {
        super(ClocktowerNightType.class);
    }
}
```

- [ ] **Step 7: Run the enum test**

Run:

```bash
mvn -q -f be/pom.xml -Dmaven.build.cache.enabled=false -Dtest=ClocktowerJpaMappingTests#clocktowerCodedEnumsExposeChineseDescriptions test
```

Expected: PASS.

- [ ] **Step 8: Commit enum contracts**

```bash
git add be/src/main/java/top/egon/mario/clocktower/common/enums \
  be/src/main/java/top/egon/mario/clocktower/converter/jpa \
  be/src/test/java/top/egon/mario/clocktower/ClocktowerJpaMappingTests.java
git commit -m "feat(clocktower): add coded enum contracts"
```

### Task 3: Add Role Summary And Grouped Night-Order Backend DTOs

**Files:**

- Create: `be/src/main/java/top/egon/mario/clocktower/script/dto/response/ClocktowerRoleSummaryResponse.java`
- Create: `be/src/main/java/top/egon/mario/clocktower/script/dto/response/ClocktowerNightOrderGroupResponse.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/script/dto/response/ClocktowerRoleResponse.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/script/dto/response/ClocktowerNightOrderResponse.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/script/service/ClocktowerScriptService.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/script/service/impl/ClocktowerScriptServiceImpl.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/script/web/ClocktowerScriptController.java`
- Modify: `be/src/test/java/top/egon/mario/clocktower/script/ClocktowerScriptServiceTests.java`

- [ ] **Step 1: Write the failing script service tests**

Replace `ClocktowerScriptServiceTests.java` with:

```java
package top.egon.mario.clocktower.script;

import org.junit.jupiter.api.Test;
import top.egon.mario.clocktower.common.enums.ClocktowerAlignment;
import top.egon.mario.clocktower.common.enums.ClocktowerNightType;
import top.egon.mario.clocktower.common.enums.ClocktowerRoleType;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.script.dto.response.ClocktowerNightOrderGroupResponse;
import top.egon.mario.clocktower.script.dto.response.ClocktowerRoleResponse;
import top.egon.mario.clocktower.script.dto.response.ClocktowerScriptResponse;
import top.egon.mario.clocktower.script.po.ClocktowerNightOrderPo;
import top.egon.mario.clocktower.script.po.ClocktowerRolePo;
import top.egon.mario.clocktower.script.po.ClocktowerScriptPo;
import top.egon.mario.clocktower.script.repository.ClocktowerJinxRuleRepository;
import top.egon.mario.clocktower.script.repository.ClocktowerNightOrderRepository;
import top.egon.mario.clocktower.script.repository.ClocktowerRoleRepository;
import top.egon.mario.clocktower.script.repository.ClocktowerScriptRepository;
import top.egon.mario.clocktower.script.service.impl.ClocktowerScriptServiceImpl;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class ClocktowerScriptServiceTests {

    @Test
    void listScriptsReturnsEnabledScriptsInSortOrder() {
        ClocktowerScriptRepository scriptRepository = mock(ClocktowerScriptRepository.class);
        ClocktowerScriptPo trouble = script(ClocktowerScriptCode.TROUBLE_BREWING, "暗流涌动", 5, 15, 10);
        ClocktowerScriptPo bmr = script(ClocktowerScriptCode.BAD_MOON_RISING, "黯月初升", 7, 15, 20);
        given(scriptRepository.findByEnabledTrueAndDeletedFalseOrderBySortOrderAsc()).willReturn(List.of(trouble, bmr));

        ClocktowerScriptServiceImpl service = new ClocktowerScriptServiceImpl(scriptRepository, null, null, null);

        List<ClocktowerScriptResponse> scripts = service.listScripts();

        assertThat(scripts).extracting(ClocktowerScriptResponse::scriptCode)
                .containsExactly(ClocktowerScriptCode.TROUBLE_BREWING, ClocktowerScriptCode.BAD_MOON_RISING);
        assertThat(scripts.getFirst().minPlayers()).isEqualTo(5);
    }

    @Test
    void listRolesReturnsChineseDisplayFieldsFromBackend() {
        ClocktowerScriptRepository scriptRepository = mock(ClocktowerScriptRepository.class);
        ClocktowerRoleRepository roleRepository = mock(ClocktowerRoleRepository.class);
        ClocktowerScriptServiceImpl service = new ClocktowerScriptServiceImpl(scriptRepository, roleRepository, null, null);
        given(roleRepository.findByScriptCodeAndEnabledAndDeletedFalseOrderBySortOrderAsc(
                ClocktowerScriptCode.TROUBLE_BREWING, true))
                .willReturn(List.of(role("CHEF", "厨师", ClocktowerRoleType.TOWNSFOLK, ClocktowerAlignment.GOOD)));

        List<ClocktowerRoleResponse> roles = service.listRoles(ClocktowerScriptCode.TROUBLE_BREWING, null, true);

        assertThat(roles).hasSize(1);
        assertThat(roles.getFirst().roleCode()).isEqualTo("CHEF");
        assertThat(roles.getFirst().roleName()).isEqualTo("厨师");
        assertThat(roles.getFirst().roleType()).isEqualTo(ClocktowerRoleType.TOWNSFOLK);
        assertThat(roles.getFirst().alignment()).isEqualTo(ClocktowerAlignment.GOOD);
    }

    @Test
    void groupedNightOrderSplitsFirstNightAndOtherNight() {
        ClocktowerScriptRepository scriptRepository = mock(ClocktowerScriptRepository.class);
        ClocktowerRoleRepository roleRepository = mock(ClocktowerRoleRepository.class);
        ClocktowerNightOrderRepository nightOrderRepository = mock(ClocktowerNightOrderRepository.class);
        ClocktowerJinxRuleRepository jinxRuleRepository = mock(ClocktowerJinxRuleRepository.class);
        ClocktowerScriptServiceImpl service = new ClocktowerScriptServiceImpl(
                scriptRepository, roleRepository, nightOrderRepository, jinxRuleRepository);
        ClocktowerRolePo chef = role("CHEF", "厨师", ClocktowerRoleType.TOWNSFOLK, ClocktowerAlignment.GOOD);
        given(roleRepository.findByScriptCodeAndDeletedFalseOrderBySortOrderAsc(ClocktowerScriptCode.TROUBLE_BREWING))
                .willReturn(List.of(chef));
        given(nightOrderRepository.findByScriptCodeAndDeletedFalseOrderBySortOrderAsc(ClocktowerScriptCode.TROUBLE_BREWING))
                .willReturn(List.of(
                        nightOrder("CHEF", ClocktowerNightType.FIRST_NIGHT, 10),
                        nightOrder("CHEF", ClocktowerNightType.OTHER_NIGHT, 20)
                ));

        ClocktowerNightOrderGroupResponse response = service.groupedNightOrder(ClocktowerScriptCode.TROUBLE_BREWING);

        assertThat(response.firstNight()).hasSize(1);
        assertThat(response.otherNight()).hasSize(1);
        assertThat(response.firstNight().getFirst().nightType()).isEqualTo(ClocktowerNightType.FIRST_NIGHT);
        assertThat(response.firstNight().getFirst().roleName()).isEqualTo("厨师");
    }

    private static ClocktowerScriptPo script(ClocktowerScriptCode code, String name, int min, int max, int order) {
        ClocktowerScriptPo po = new ClocktowerScriptPo();
        po.setScriptCode(code);
        po.setName(name);
        po.setEdition("BASE_3");
        po.setMinPlayers(min);
        po.setMaxPlayers(max);
        po.setRoleCount(22);
        po.setEnabled(true);
        po.setSortOrder(order);
        return po;
    }

    private static ClocktowerRolePo role(String code, String name, ClocktowerRoleType roleType,
                                         ClocktowerAlignment alignment) {
        ClocktowerRolePo po = new ClocktowerRolePo();
        po.setScriptCode(ClocktowerScriptCode.TROUBLE_BREWING);
        po.setRoleCode(code);
        po.setName(name);
        po.setRoleType(roleType);
        po.setAlignment(alignment);
        po.setAbilityText("测试能力");
        po.setEnabled(true);
        po.setSortOrder(10);
        return po;
    }

    private static ClocktowerNightOrderPo nightOrder(String roleCode, ClocktowerNightType nightType, int orderNo) {
        ClocktowerNightOrderPo po = new ClocktowerNightOrderPo();
        po.setScriptCode(ClocktowerScriptCode.TROUBLE_BREWING);
        po.setRoleCode(roleCode);
        po.setNightType(nightType);
        po.setOrderNo(orderNo);
        po.setSortOrder(orderNo);
        po.setReminderText("提醒");
        return po;
    }
}
```

- [ ] **Step 2: Run the script service tests to verify they fail**

Run:

```bash
mvn -q -f be/pom.xml -Dmaven.build.cache.enabled=false -Dtest=ClocktowerScriptServiceTests test
```

Expected: FAIL during compilation because `ClocktowerAlignment`, `ClocktowerNightType`,
`ClocktowerRoleResponse.roleName`, and `groupedNightOrder` are not wired through all target classes yet.

- [ ] **Step 3: Add role summary and grouped night-order DTOs**

Create `ClocktowerRoleSummaryResponse.java`:

```java
package top.egon.mario.clocktower.script.dto.response;

import top.egon.mario.clocktower.common.enums.ClocktowerAlignment;
import top.egon.mario.clocktower.common.enums.ClocktowerRoleType;
import top.egon.mario.clocktower.script.po.ClocktowerRolePo;

public record ClocktowerRoleSummaryResponse(
        String roleCode,
        String roleName,
        ClocktowerRoleType roleType,
        ClocktowerAlignment alignment
) {

    public static ClocktowerRoleSummaryResponse from(ClocktowerRolePo role) {
        return new ClocktowerRoleSummaryResponse(role.getRoleCode(), role.getName(), role.getRoleType(),
                role.getAlignment());
    }
}
```

Create `ClocktowerNightOrderGroupResponse.java`:

```java
package top.egon.mario.clocktower.script.dto.response;

import java.util.List;

public record ClocktowerNightOrderGroupResponse(
        List<ClocktowerNightOrderResponse> firstNight,
        List<ClocktowerNightOrderResponse> otherNight
) {
}
```

- [ ] **Step 4: Update role and night-order response records**

Replace `ClocktowerRoleResponse.java` with:

```java
package top.egon.mario.clocktower.script.dto.response;

import top.egon.mario.clocktower.common.enums.ClocktowerAlignment;
import top.egon.mario.clocktower.common.enums.ClocktowerRoleType;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.script.po.ClocktowerRolePo;

public record ClocktowerRoleResponse(
        ClocktowerScriptCode scriptCode,
        String roleCode,
        String roleName,
        ClocktowerRoleType roleType,
        ClocktowerAlignment alignment,
        String abilityText,
        Integer firstNightOrder,
        Integer otherNightOrder,
        String firstNightReminder,
        String otherNightReminder,
        boolean enabled,
        String sourceUrl
) {

    public static ClocktowerRoleResponse from(ClocktowerRolePo role) {
        return new ClocktowerRoleResponse(role.getScriptCode(), role.getRoleCode(), role.getName(),
                role.getRoleType(), role.getAlignment(), role.getAbilityText(), role.getFirstNightOrder(),
                role.getOtherNightOrder(), role.getFirstNightReminder(), role.getOtherNightReminder(),
                role.isEnabled(), role.getSourceUrl());
    }
}
```

Replace `ClocktowerNightOrderResponse.java` with:

```java
package top.egon.mario.clocktower.script.dto.response;

import top.egon.mario.clocktower.common.enums.ClocktowerNightType;
import top.egon.mario.clocktower.common.enums.ClocktowerRoleType;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;

public record ClocktowerNightOrderResponse(
        ClocktowerScriptCode scriptCode,
        String roleCode,
        String roleName,
        ClocktowerRoleType roleType,
        ClocktowerNightType nightType,
        int orderNo,
        String reminderText
) {
}
```

- [ ] **Step 5: Update service and controller contracts**

In `ClocktowerScriptService.java`, add the import:

```java
import top.egon.mario.clocktower.script.dto.response.ClocktowerNightOrderGroupResponse;
```

Add the method:

```java
ClocktowerNightOrderGroupResponse groupedNightOrder(ClocktowerScriptCode scriptCode);
```

In `ClocktowerScriptController.java`, import `ClocktowerNightOrderGroupResponse` and add this endpoint after
`nightOrder(...)`:

```java
@GetMapping("/scripts/{scriptCode}/night-order/grouped")
public Mono<ApiResponse<ClocktowerNightOrderGroupResponse>> groupedNightOrder(
        @PathVariable ClocktowerScriptCode scriptCode) {
    return blocking(() -> scriptService.groupedNightOrder(scriptCode));
}
```

- [ ] **Step 6: Update `ClocktowerScriptServiceImpl` night-order mapping**

In `ClocktowerScriptServiceImpl.java`, import:

```java
import top.egon.mario.clocktower.common.enums.ClocktowerNightType;
import top.egon.mario.clocktower.script.dto.response.ClocktowerNightOrderGroupResponse;
```

Replace the `nightOrder(...)` method body and add `groupedNightOrder(...)`:

```java
@Override
public List<ClocktowerNightOrderResponse> nightOrder(ClocktowerScriptCode scriptCode, String nightType) {
    ClocktowerNightType parsedNightType = StringUtils.hasText(nightType)
            ? ClocktowerNightType.fromJson(nightType)
            : null;
    List<ClocktowerNightOrderPo> orders = parsedNightType != null
            ? nightOrderRepository.findByScriptCodeAndNightTypeAndDeletedFalseOrderBySortOrderAsc(scriptCode, parsedNightType)
            : nightOrderRepository.findByScriptCodeAndDeletedFalseOrderBySortOrderAsc(scriptCode);
    return toNightOrderResponses(scriptCode, orders);
}

@Override
public ClocktowerNightOrderGroupResponse groupedNightOrder(ClocktowerScriptCode scriptCode) {
    List<ClocktowerNightOrderResponse> orders = nightOrder(scriptCode, null);
    return new ClocktowerNightOrderGroupResponse(
            orders.stream().filter(order -> order.nightType() == ClocktowerNightType.FIRST_NIGHT).toList(),
            orders.stream().filter(order -> order.nightType() == ClocktowerNightType.OTHER_NIGHT).toList()
    );
}

private List<ClocktowerNightOrderResponse> toNightOrderResponses(ClocktowerScriptCode scriptCode,
                                                                 List<ClocktowerNightOrderPo> orders) {
    Map<String, ClocktowerRolePo> roles = roleRepository.findByScriptCodeAndDeletedFalseOrderBySortOrderAsc(scriptCode)
            .stream()
            .collect(Collectors.toMap(ClocktowerRolePo::getRoleCode, Function.identity(), (left, right) -> left));
    return orders.stream()
            .map(order -> {
                ClocktowerRolePo role = roles.get(order.getRoleCode());
                return new ClocktowerNightOrderResponse(order.getScriptCode(), order.getRoleCode(),
                        role == null ? order.getRoleCode() : role.getName(),
                        role == null ? null : role.getRoleType(), order.getNightType(), order.getOrderNo(),
                        order.getReminderText());
            })
            .toList();
}
```

- [ ] **Step 7: Run script service tests as the cross-task failure point**

Run:

```bash
mvn -q -f be/pom.xml -Dmaven.build.cache.enabled=false -Dtest=ClocktowerScriptServiceTests test
```

Expected: FAIL if `ClocktowerNightOrderPo.getNightType()` still returns `String` or if repository methods still accept
`String nightType`. Keep the Task 3 edits in the working tree and proceed to Task 4.

- [ ] **Step 8: Leave Task 3 edits uncommitted until Task 4 passes**

Do not commit a compiling-broken intermediate state. Task 4 will commit the DTO and entity changes together after the
affected backend tests pass.

### Task 4: Convert Entity Fields And Repositories To Coded Enums

**Files:**

- Modify: `be/src/main/java/top/egon/mario/clocktower/script/po/ClocktowerRolePo.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/script/po/ClocktowerNightOrderPo.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/room/po/ClocktowerSeatPo.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/grimoire/po/ClocktowerGrimoireEntryPo.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/board/po/ClocktowerBoardRolePo.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/script/repository/ClocktowerNightOrderRepository.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/script/service/impl/ClocktowerScriptServiceImpl.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/grimoire/service/impl/ClocktowerGrimoireServiceImpl.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/room/service/impl/ClocktowerRoomServiceImpl.java`

- [ ] **Step 1: Update role and night-order entity fields**

In `ClocktowerRolePo.java`:

- Remove imports for `EnumType` and `Enumerated`.
- Add imports:

```java
import jakarta.persistence.Convert;
import top.egon.mario.clocktower.common.enums.ClocktowerAlignment;
import top.egon.mario.clocktower.converter.jpa.ClocktowerAlignmentConverter;
import top.egon.mario.clocktower.converter.jpa.ClocktowerRoleTypeConverter;
```

- Replace the `roleType` and `alignment` fields with:

```java
@Convert(converter = ClocktowerRoleTypeConverter.class)
@Column(name = "role_type", nullable = false)
private ClocktowerRoleType roleType;

@Convert(converter = ClocktowerAlignmentConverter.class)
@Column(name = "alignment", nullable = false)
private ClocktowerAlignment alignment;
```

In `ClocktowerNightOrderPo.java`:

- Add imports:

```java
import jakarta.persistence.Convert;
import top.egon.mario.clocktower.common.enums.ClocktowerNightType;
import top.egon.mario.clocktower.converter.jpa.ClocktowerNightTypeConverter;
```

- Replace the `nightType` field with:

```java
@Convert(converter = ClocktowerNightTypeConverter.class)
@Column(name = "night_type", nullable = false)
private ClocktowerNightType nightType;
```

- [ ] **Step 2: Update board, seat, and grimoire entity fields**

In `ClocktowerBoardRolePo.java`, replace `@Enumerated(EnumType.STRING)` on `roleType` with:

```java
@Convert(converter = ClocktowerRoleTypeConverter.class)
@Column(name = "role_type", nullable = false)
private ClocktowerRoleType roleType;
```

In `ClocktowerSeatPo.java`, replace `roleType` and `alignment` with:

```java
@Convert(converter = ClocktowerRoleTypeConverter.class)
@Column(name = "role_type")
private ClocktowerRoleType roleType;

@Convert(converter = ClocktowerAlignmentConverter.class)
@Column(name = "alignment")
private ClocktowerAlignment alignment;
```

In `ClocktowerGrimoireEntryPo.java`, replace `roleType` and `alignment` with:

```java
@Convert(converter = ClocktowerRoleTypeConverter.class)
@Column(name = "role_type", nullable = false)
private ClocktowerRoleType roleType;

@Convert(converter = ClocktowerAlignmentConverter.class)
@Column(name = "alignment", nullable = false)
private ClocktowerAlignment alignment;
```

For these three files, add the needed `jakarta.persistence.Convert`, converter, and `ClocktowerAlignment` imports, and
remove unused `EnumType` or `Enumerated` imports.

- [ ] **Step 3: Update night-order repository signatures**

In `ClocktowerNightOrderRepository.java`, import:

```java
import top.egon.mario.clocktower.common.enums.ClocktowerNightType;
```

Replace the two method signatures that currently accept `String nightType` with:

```java
List<ClocktowerNightOrderPo> findByScriptCodeAndNightTypeAndDeletedFalseOrderBySortOrderAsc(ClocktowerScriptCode scriptCode,
                                                                                            ClocktowerNightType nightType);

List<ClocktowerNightOrderPo> findByScriptCodeAndNightTypeAndRoleCodeInAndDeletedFalseOrderBySortOrderAsc(ClocktowerScriptCode scriptCode,
                                                                                                         ClocktowerNightType nightType,
                                                                                                         Collection<String> roleCodes);
```

- [ ] **Step 4: Update grimoire night checklist to use `ClocktowerNightType`**

In `ClocktowerGrimoireServiceImpl.java`, import `ClocktowerNightType` and replace:

```java
String nightType = room.getCurrentNightNo() <= 1 ? "FIRST_NIGHT" : "OTHER_NIGHT";
```

with:

```java
ClocktowerNightType nightType = room.getCurrentNightNo() <= 1
        ? ClocktowerNightType.FIRST_NIGHT
        : ClocktowerNightType.OTHER_NIGHT;
```

The existing `new NightChecklistResponse(room.getCurrentNightNo(), nightType, steps, ...)` will be corrected in Task 8
when `NightChecklistResponse.nightType` becomes `ClocktowerNightType`.

- [ ] **Step 5: Run backend compile for affected tests**

Run:

```bash
mvn -q -f be/pom.xml -Dmaven.build.cache.enabled=false -Dtest=ClocktowerScriptServiceTests,ClocktowerJpaMappingTests,ClocktowerNightChecklistServiceTests test
```

Expected: PASS after the Task 3 DTO edits and Task 4 entity/repository edits are both present.

- [ ] **Step 6: Commit entity enum conversion**

```bash
git add be/src/main/java/top/egon/mario/clocktower \
  be/src/test/java/top/egon/mario/clocktower
git commit -m "feat(clocktower): expose localized coded rule data"
```

### Task 5: Scope Board Metadata By Script And Add Role Summaries

**Files:**

- Modify: `be/src/main/java/top/egon/mario/clocktower/board/service/RoleMetadataProvider.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/board/service/impl/RepositoryRoleMetadataProvider.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/board/service/impl/ClocktowerBoardServiceImpl.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/board/dto/response/ClocktowerBoardCandidateResponse.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/board/dto/response/ClocktowerBoardConfigResponse.java`
- Modify: `be/src/test/java/top/egon/mario/clocktower/board/ClocktowerBoardTestFactory.java`
- Modify: `be/src/test/java/top/egon/mario/clocktower/board/ClocktowerBoardServiceTests.java`
- Modify: `be/src/test/java/top/egon/mario/clocktower/board/ClocktowerBoardControllerTests.java`

- [ ] **Step 1: Write failing board metadata tests**

Add this test to `ClocktowerBoardServiceTests.java`:

```java
@Test
void generateUsesOnlyRolesFromRequestedScriptAndReturnsRoleSummaries() {
    ClocktowerBoardGenerateResponse response = boardService.generate(new ClocktowerBoardGenerateRequest(
            ClocktowerScriptCode.TROUBLE_BREWING, 5, 2, 2, 2, true, 1, List.of(), List.of(), "seed-1"
    ), ClocktowerBoardTestFactory.principal(1L));

    assertThat(response.candidates()).hasSize(1);
    assertThat(response.candidates().getFirst().roleCodes()).contains("CHEF", "IMP");
    assertThat(response.candidates().getFirst().roleCodes()).doesNotContain("GRANDMOTHER");
    assertThat(response.candidates().getFirst().roles())
            .extracting(role -> role.roleName())
            .contains("厨师", "小恶魔");
}
```

Update `ClocktowerBoardControllerTests.generateBoardReturnsRequestedCandidateCount()` assertion block:

```java
assertThat(response.candidates()).allSatisfy(candidate -> {
    assertThat(candidate.playerCount()).isEqualTo(5);
    assertThat(candidate.validation().valid()).isTrue();
    assertThat(candidate.roles()).hasSameSizeAs(candidate.roleCodes());
});
```

- [ ] **Step 2: Run board tests to verify they fail**

Run:

```bash
mvn -q -f be/pom.xml -Dmaven.build.cache.enabled=false -Dtest=ClocktowerBoardServiceTests,ClocktowerBoardControllerTests test
```

Expected: FAIL during compilation because `roles()` is not present on board responses and `RoleMetadataProvider` does
not accept `scriptCode`.

- [ ] **Step 3: Update `RoleMetadataProvider`**

Replace `RoleMetadataProvider.java` with:

```java
package top.egon.mario.clocktower.board.service;

import top.egon.mario.clocktower.common.enums.ClocktowerRoleType;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.script.dto.response.ClocktowerRoleSummaryResponse;

import java.util.List;
import java.util.Map;

public interface RoleMetadataProvider {

    Map<String, ClocktowerRoleType> roleTypes(ClocktowerScriptCode scriptCode);

    Map<String, ClocktowerRoleSummaryResponse> roleSummaries(ClocktowerScriptCode scriptCode);

    default List<String> roleCodes(ClocktowerScriptCode scriptCode, ClocktowerRoleType roleType) {
        return roleTypes(scriptCode).entrySet().stream()
                .filter(entry -> entry.getValue() == roleType)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }
}
```

- [ ] **Step 4: Update repository-backed role metadata**

Replace `RepositoryRoleMetadataProvider.java` with:

```java
package top.egon.mario.clocktower.board.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import top.egon.mario.clocktower.board.service.RoleMetadataProvider;
import top.egon.mario.clocktower.common.enums.ClocktowerRoleType;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.script.dto.response.ClocktowerRoleSummaryResponse;
import top.egon.mario.clocktower.script.po.ClocktowerRolePo;
import top.egon.mario.clocktower.script.repository.ClocktowerRoleRepository;

import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class RepositoryRoleMetadataProvider implements RoleMetadataProvider {

    private final ClocktowerRoleRepository roleRepository;

    @Override
    public Map<String, ClocktowerRoleType> roleTypes(ClocktowerScriptCode scriptCode) {
        return roles(scriptCode).stream()
                .collect(Collectors.toMap(ClocktowerRolePo::getRoleCode, ClocktowerRolePo::getRoleType,
                        (left, right) -> left));
    }

    @Override
    public Map<String, ClocktowerRoleSummaryResponse> roleSummaries(ClocktowerScriptCode scriptCode) {
        return roles(scriptCode).stream()
                .collect(Collectors.toMap(ClocktowerRolePo::getRoleCode, ClocktowerRoleSummaryResponse::from,
                        (left, right) -> left));
    }

    private java.util.List<ClocktowerRolePo> roles(ClocktowerScriptCode scriptCode) {
        return roleRepository.findByScriptCodeAndEnabledAndDeletedFalseOrderBySortOrderAsc(scriptCode, true);
    }
}
```

- [ ] **Step 5: Update board response records**

Replace `ClocktowerBoardCandidateResponse.java` with:

```java
package top.egon.mario.clocktower.board.dto.response;

import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.script.dto.response.ClocktowerRoleSummaryResponse;

import java.util.List;

public record ClocktowerBoardCandidateResponse(
        String candidateId,
        ClocktowerScriptCode scriptCode,
        int playerCount,
        List<String> roleCodes,
        List<ClocktowerRoleSummaryResponse> roles,
        ClocktowerBoardValidationResponse validation,
        List<ClocktowerScoreResponse> scores
) {
}
```

Replace `ClocktowerBoardConfigResponse.java` with:

```java
package top.egon.mario.clocktower.board.dto.response;

import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.script.dto.response.ClocktowerRoleSummaryResponse;

import java.util.List;

public record ClocktowerBoardConfigResponse(
        Long boardId,
        String boardCode,
        ClocktowerScriptCode scriptCode,
        int playerCount,
        List<String> roleCodes,
        List<ClocktowerRoleSummaryResponse> roles,
        ClocktowerBoardValidationResponse validation
) {
}
```

- [ ] **Step 6: Update `ClocktowerBoardServiceImpl`**

In `ClocktowerBoardServiceImpl`, replace all `roleMetadataProvider.roleTypes()` calls with
`roleMetadataProvider.roleTypes(request.scriptCode())` or `roleMetadataProvider.roleTypes(config.getScriptCode())`.

Replace `candidate(...)` return construction with:

```java
Map<String, ClocktowerRoleSummaryResponse> summaries = roleMetadataProvider.roleSummaries(request.scriptCode());
return new ClocktowerBoardCandidateResponse(
        "candidate-" + (index + 1),
        request.scriptCode(),
        request.playerCount(),
        roles,
        roleSummaries(roles, summaries),
        boardValidation,
        validation.scores()
);
```

Add imports:

```java
import top.egon.mario.clocktower.script.dto.response.ClocktowerRoleSummaryResponse;
```

Add helper:

```java
private List<ClocktowerRoleSummaryResponse> roleSummaries(List<String> roleCodes,
                                                          Map<String, ClocktowerRoleSummaryResponse> summaries) {
    return roleCodes.stream()
            .map(roleCode -> summaries.getOrDefault(roleCode,
                    new ClocktowerRoleSummaryResponse(roleCode, roleCode, null, null)))
            .toList();
}
```

Replace `roleMetadataProvider.roleCodes(entry.getKey())` with:

```java
roleMetadataProvider.roleCodes(request.scriptCode(), entry.getKey())
```

Replace `toResponse(...)` with:

```java
private ClocktowerBoardConfigResponse toResponse(ClocktowerBoardConfigPo config, List<String> roleCodes,
                                                 ClocktowerBoardValidationResponse validation) {
    Map<String, ClocktowerRoleSummaryResponse> summaries = roleMetadataProvider.roleSummaries(config.getScriptCode());
    return new ClocktowerBoardConfigResponse(config.getId(), config.getBoardCode(), config.getScriptCode(),
            config.getPlayerCount(), roleCodes, roleSummaries(roleCodes, summaries), validation);
}
```

- [ ] **Step 7: Update board test factory**

Replace the provider implementation in `ClocktowerBoardTestFactory.service()` with a small class:

```java
RoleMetadataProvider provider = new RoleMetadataProvider() {
    private final Map<String, ClocktowerRoleSummaryResponse> troubleBrewing = Map.of(
            "EMPATH", new ClocktowerRoleSummaryResponse("EMPATH", "共情者", ClocktowerRoleType.TOWNSFOLK, ClocktowerAlignment.GOOD),
            "CHEF", new ClocktowerRoleSummaryResponse("CHEF", "厨师", ClocktowerRoleType.TOWNSFOLK, ClocktowerAlignment.GOOD),
            "MONK", new ClocktowerRoleSummaryResponse("MONK", "僧侣", ClocktowerRoleType.TOWNSFOLK, ClocktowerAlignment.GOOD),
            "POISONER", new ClocktowerRoleSummaryResponse("POISONER", "投毒者", ClocktowerRoleType.MINION, ClocktowerAlignment.EVIL),
            "IMP", new ClocktowerRoleSummaryResponse("IMP", "小恶魔", ClocktowerRoleType.DEMON, ClocktowerAlignment.EVIL)
    );

    private final Map<String, ClocktowerRoleSummaryResponse> badMoonRising = Map.of(
            "GRANDMOTHER", new ClocktowerRoleSummaryResponse("GRANDMOTHER", "祖母", ClocktowerRoleType.TOWNSFOLK, ClocktowerAlignment.GOOD)
    );

    @Override
    public Map<String, ClocktowerRoleType> roleTypes(ClocktowerScriptCode scriptCode) {
        return roleSummaries(scriptCode).entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().roleType()));
    }

    @Override
    public Map<String, ClocktowerRoleSummaryResponse> roleSummaries(ClocktowerScriptCode scriptCode) {
        return scriptCode == ClocktowerScriptCode.BAD_MOON_RISING ? badMoonRising : troubleBrewing;
    }
};
```

Add imports for `ClocktowerAlignment`, `ClocktowerScriptCode`, and `ClocktowerRoleSummaryResponse`.

Expose the principal helper by changing it from private in `ClocktowerBoardControllerTests` style to this method in
`ClocktowerBoardTestFactory`:

```java
static RbacPrincipal principal(Long userId) {
    return new RbacPrincipal(userId, "user-" + userId, java.util.Set.of("CLOCKTOWER_STORYTELLER"),
            java.util.Set.of(), "v1");
}
```

- [ ] **Step 8: Run board tests**

Run:

```bash
mvn -q -f be/pom.xml -Dmaven.build.cache.enabled=false -Dtest=ClocktowerBoardServiceTests,ClocktowerBoardControllerTests test
```

Expected: PASS.

- [ ] **Step 9: Commit board metadata changes**

```bash
git add be/src/main/java/top/egon/mario/clocktower/board \
  be/src/main/java/top/egon/mario/clocktower/script/dto/response/ClocktowerRoleSummaryResponse.java \
  be/src/test/java/top/egon/mario/clocktower/board
git commit -m "feat(clocktower): localize board role summaries"
```

### Task 6: Add The Rule Data Migration After Data Is Reviewed

**Files:**

- Create: `be/src/main/java/top/egon/mario/clocktower/common/enums/ClocktowerRoleCode.java`
- Create: `be/src/main/resources/db/migration/V20__complete_clocktower_rule_data.sql`
- Modify: `be/src/test/java/top/egon/mario/clocktower/ClocktowerSchemaMigrationTests.java`

- [ ] **Step 1: Confirm the data gate**

Before creating the migration, verify:

```bash
test -s docs/clocktower/rule-data/clocktower-base-scripts-roles.csv
test -s docs/clocktower/rule-data/clocktower-base-scripts-night-order.csv
```

Expected: both commands exit with status `0`.

If either command exits with status `1`, stop this task and report that the complete reviewed data files are required.

- [ ] **Step 2: Create the official role-code enum from the reviewed role CSV**

Create `be/src/main/java/top/egon/mario/clocktower/common/enums/ClocktowerRoleCode.java` from
`clocktower-base-scripts-roles.csv`.

Rules:

- There must be one enum constant per distinct `roleCode` in the reviewed role CSV.
- Constants must use the exact official role code text.
- The integer code must be stable and unique.
- The Chinese description must equal the `name` column from the reviewed CSV.
- The first five existing rows must remain:

```java
CHEF(1, "厨师"),
EMPATH(2, "共情者"),
MONK(3, "僧侣"),
POISONER(4, "投毒者"),
IMP(5, "小恶魔")
```

Use this class shape:

```java
package top.egon.mario.clocktower.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import top.egon.mario.common.enums.CodedEnum;

import java.util.Map;

/**
 * Official Blood on the Clocktower role code with Chinese display name.
 */
@Getter
public enum ClocktowerRoleCode implements CodedEnum {
    CHEF(1, "厨师"),
    EMPATH(2, "共情者"),
    MONK(3, "僧侣"),
    POISONER(4, "投毒者"),
    IMP(5, "小恶魔");

    private final int code;
    private final String desc;

    ClocktowerRoleCode(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    @JsonValue
    public Map<String, Object> toJson() {
        return ClocktowerEnumJsonSupport.toJson(this);
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static ClocktowerRoleCode fromJson(Object input) {
        return ClocktowerEnumJsonSupport.fromJson(ClocktowerRoleCode.class, input);
    }
}
```

When complete reviewed data is available, replace the five-constant body with all reviewed constants in a single edit
before running Step 3.

- [ ] **Step 3: Write the failing migration coverage test**

Add this test to `ClocktowerSchemaMigrationTests.java`:

```java
@Test
void completeClocktowerRuleDataMigrationCoversThreeBaseScripts() throws IOException {
    String sql = Files.readString(Path.of(
            "src/main/resources/db/migration/V20__complete_clocktower_rule_data.sql"));

    assertThat(sql).contains("TROUBLE_BREWING");
    assertThat(sql).contains("BAD_MOON_RISING");
    assertThat(sql).contains("SECTS_AND_VIOLETS");
    assertThat(sql).contains("CHEF");
    assertThat(sql).contains("IMP");
    assertThat(sql).contains("FIRST_NIGHT");
    assertThat(sql).contains("OTHER_NIGHT");
    assertThat(sql).contains("clocktower_role");
    assertThat(sql).contains("clocktower_night_order");
    assertThat(sql).doesNotContain("DROP TABLE");
    assertThat(sql).doesNotContain("TRUNCATE");
}
```

- [ ] **Step 4: Run the migration coverage test to verify it fails**

Run:

```bash
mvn -q -f be/pom.xml -Dmaven.build.cache.enabled=false -Dtest=ClocktowerSchemaMigrationTests#completeClocktowerRuleDataMigrationCoversThreeBaseScripts test
```

Expected: FAIL with `NoSuchFileException` for `V20__complete_clocktower_rule_data.sql`.

- [ ] **Step 5: Create exactly one new Flyway migration**

Create `be/src/main/resources/db/migration/V20__complete_clocktower_rule_data.sql`.

Required structure:

```sql
-- Convert existing textual enum data only if Task 4 changed the corresponding columns to integer-coded enums.
-- Keep this migration backward-safe: update existing rows first, then insert missing rows.

-- clocktower_role: upsert all reviewed role rows for TROUBLE_BREWING, BAD_MOON_RISING, and SECTS_AND_VIOLETS.
-- clocktower_night_order: delete and reinsert managed static night-order rows for the three base scripts only.
```

Use `UPDATE` statements for the five existing Trouble Brewing rows, then `INSERT` statements for missing role and
night-order rows. Do not edit `V18__create_clocktower_core_schema.sql`.

- [ ] **Step 6: Run schema migration tests**

Run:

```bash
mvn -q -f be/pom.xml -Dmaven.build.cache.enabled=false -Dtest=ClocktowerSchemaMigrationTests test
```

Expected: PASS.

- [ ] **Step 7: Commit migration**

```bash
git add be/src/main/java/top/egon/mario/clocktower/common/enums/ClocktowerRoleCode.java \
  be/src/main/resources/db/migration/V20__complete_clocktower_rule_data.sql \
  be/src/test/java/top/egon/mario/clocktower/ClocktowerSchemaMigrationTests.java
git commit -m "feat(clocktower): complete base script rule data"
```

### Task 7: Update Frontend Types And Service Wrappers

**Files:**

- Modify: `fe/src/modules/clocktower/clocktowerTypes.ts`
- Modify: `fe/src/modules/clocktower/clocktowerService.ts`
- Modify: `fe/src/modules/clocktower/clocktowerService.test.ts`

- [ ] **Step 1: Write the failing service wrapper test**

Update imports in `clocktowerService.test.ts` to include:

```ts
getClocktowerGroupedNightOrder,
```

Add this test:

```ts
it('loads grouped night order for a script', async () => {
    const {requestJson} = await import('../../services/request')

    await getClocktowerGroupedNightOrder('TROUBLE_BREWING')

    expect(requestJson).toHaveBeenCalledWith('/api/clocktower/scripts/TROUBLE_BREWING/night-order/grouped')
})
```

- [ ] **Step 2: Run frontend service test to verify it fails**

Run:

```bash
cd fe && bun run test -- clocktowerService.test.ts
```

Expected: FAIL because `getClocktowerGroupedNightOrder` is not exported.

- [ ] **Step 3: Update frontend enum and response types**

In `clocktowerTypes.ts`, replace `ClocktowerRoleType` with:

```ts
export type CodedEnum = {
    code: number
    desc: string
}

export type ClocktowerRoleTypeCode = 'TOWNSFOLK' | 'OUTSIDER' | 'MINION' | 'DEMON' | 'TRAVELER' | 'FABLED'
export type ClocktowerRoleType = ClocktowerRoleTypeCode | CodedEnum
export type ClocktowerAlignment = 'GOOD' | 'EVIL' | 'NEUTRAL' | CodedEnum
export type ClocktowerNightType = 'FIRST_NIGHT' | 'OTHER_NIGHT' | CodedEnum
```

Update `ClocktowerRoleResponse` fields:

```ts
roleName: string
roleType: ClocktowerRoleType
alignment: ClocktowerAlignment
```

Add:

```ts
export type ClocktowerRoleSummaryResponse = {
    roleCode: string
    roleName: string
    roleType?: ClocktowerRoleType | null
    alignment?: ClocktowerAlignment | null
}

export type ClocktowerNightOrderGroupResponse = {
    firstNight: ClocktowerNightOrderResponse[]
    otherNight: ClocktowerNightOrderResponse[]
}
```

Update `ClocktowerNightOrderResponse.nightType`:

```ts
nightType: ClocktowerNightType
orderNo: number
```

Update board response types:

```ts
roles: ClocktowerRoleSummaryResponse[]
```

- [ ] **Step 4: Add grouped night-order service wrapper**

In `clocktowerService.ts`, import `ClocktowerNightOrderGroupResponse` and add:

```ts
export function getClocktowerGroupedNightOrder(scriptCode: ClocktowerScriptCode) {
    return requestJson<ClocktowerNightOrderGroupResponse>(`/api/clocktower/scripts/${scriptCode}/night-order/grouped`)
}
```

- [ ] **Step 5: Run frontend service test**

Run:

```bash
cd fe && bun run test -- clocktowerService.test.ts
```

Expected: PASS.

- [ ] **Step 6: Commit frontend type/service changes**

```bash
git add fe/src/modules/clocktower/clocktowerTypes.ts \
  fe/src/modules/clocktower/clocktowerService.ts \
  fe/src/modules/clocktower/clocktowerService.test.ts
git commit -m "feat(clocktower): add localized frontend rule data types"
```

### Task 8: Render Grouped Night Order And Backend Enum Descriptions In Rule Page

**Files:**

- Modify: `fe/src/modules/clocktower/components/RoleTypeTag.tsx`
- Modify: `fe/src/modules/clocktower/RuleDataPage.tsx`
- Modify: `fe/src/modules/clocktower/RuleDataPage.test.tsx`

- [ ] **Step 1: Write failing rule page test**

In `RuleDataPage.test.tsx`, update the service mock:

```ts
getClocktowerGroupedNightOrder: vi.fn().mockResolvedValue({
    firstNight: [{scriptCode: 'TROUBLE_BREWING', roleCode: 'CHEF', roleName: '厨师', roleType: {code: 1, desc: '镇民'}, nightType: {code: 1, desc: '首夜'}, orderNo: 10, reminderText: '厨师得知邻座邪恶玩家对数。'}],
    otherNight: [],
}),
```

Add expectations:

```ts
expect(markup).toContain('首夜')
expect(markup).toContain('其他夜晚')
expect(markup).toContain('角色名称')
```

- [ ] **Step 2: Run rule page test to verify it fails**

Run:

```bash
cd fe && bun run test -- RuleDataPage.test.tsx
```

Expected: FAIL because the page still renders a single night-order table and uses `name` instead of `roleName`.

- [ ] **Step 3: Update `RoleTypeTag`**

Replace `RoleTypeTag.tsx` with:

```tsx
import {Tag} from 'antd'
import {enumCode, enumDesc} from '../../../utils/enum'
import type {ClocktowerRoleType} from '../clocktowerTypes'

const roleTypeColors: Record<string, string> = {
    TOWNSFOLK: 'blue',
    OUTSIDER: 'cyan',
    MINION: 'volcano',
    DEMON: 'red',
    TRAVELER: 'purple',
    FABLED: 'gold',
    '1': 'blue',
    '2': 'cyan',
    '3': 'volcano',
    '4': 'red',
    '5': 'purple',
    '6': 'gold',
}

type RoleTypeTagProps = {
    value?: ClocktowerRoleType | null
}

export function RoleTypeTag({value}: RoleTypeTagProps) {
    if (!value) {
        return <Tag>未定</Tag>
    }
    const code = String(enumCode(value))
    return <Tag color={roleTypeColors[code] ?? 'default'}>{enumDesc(value)}</Tag>
}
```

- [ ] **Step 4: Update RuleDataPage service usage and columns**

In `RuleDataPage.tsx`:

- Replace `getClocktowerNightOrder` import with `getClocktowerGroupedNightOrder`.
- Change night-order state to:

```ts
const [nightOrder, setNightOrder] = useState<ClocktowerNightOrderGroupResponse>({firstNight: [], otherNight: []})
```

- Update `loadNightOrderData`:

```ts
return getClocktowerGroupedNightOrder(selectedScriptCode)
```

- Change the role table columns:

```tsx
{title: '角色代码', dataIndex: 'roleCode', width: 150},
{title: '角色名称', dataIndex: 'roleName', width: 140},
```

- Replace `NightOrderTable` usage with:

```tsx
<Space direction="vertical" size="middle" style={{width: '100%'}}>
    <Card size="small" title="首夜">
        <NightOrderTable loading={nightOrderLoading} nightOrder={nightOrder.firstNight}/>
    </Card>
    <Card size="small" title="其他夜晚">
        <NightOrderTable loading={nightOrderLoading} nightOrder={nightOrder.otherNight}/>
    </Card>
</Space>
```

- In `NightOrderTable`, replace `sortOrder` with `orderNo`.

- [ ] **Step 5: Run rule page test**

Run:

```bash
cd fe && bun run test -- RuleDataPage.test.tsx
```

Expected: PASS.

- [ ] **Step 6: Commit rule page localization**

```bash
git add fe/src/modules/clocktower/components/RoleTypeTag.tsx \
  fe/src/modules/clocktower/RuleDataPage.tsx \
  fe/src/modules/clocktower/RuleDataPage.test.tsx
git commit -m "feat(clocktower): show localized grouped night order"
```

### Task 9: Render Role Summaries In Board Tables

**Files:**

- Modify: `fe/src/modules/clocktower/components/BoardCandidateTable.tsx`
- Modify: `fe/src/modules/clocktower/BoardBuilderPage.tsx`
- Modify: `fe/src/modules/clocktower/BoardBuilderPage.test.tsx`

- [ ] **Step 1: Write failing board table test coverage**

Update the `generateClocktowerBoard` mock in `BoardBuilderPage.test.tsx`:

```ts
generateClocktowerBoard: vi.fn().mockResolvedValue({
    candidates: [{
        candidateId: 'candidate-1',
        scriptCode: 'TROUBLE_BREWING',
        playerCount: 5,
        roleCodes: ['CHEF', 'IMP'],
        roles: [
            {roleCode: 'CHEF', roleName: '厨师', roleType: {code: 1, desc: '镇民'}, alignment: {code: 1, desc: '善良'}},
            {roleCode: 'IMP', roleName: '小恶魔', roleType: {code: 4, desc: '恶魔'}, alignment: {code: 2, desc: '邪恶'}},
        ],
        validation: {valid: true, roleTypeCounts: {}, violations: [], scores: []},
        scores: [],
    }],
}),
```

Add an assertion in the existing test:

```ts
expect(markup).toContain('候选配板')
```

Create a focused component test file `fe/src/modules/clocktower/components/BoardCandidateTable.test.tsx`:

```tsx
import {renderToStaticMarkup} from 'react-dom/server'
import {describe, expect, test, vi} from 'vitest'
import {BoardCandidateTable} from './BoardCandidateTable'

describe('BoardCandidateTable', () => {
    test('renders localized role names with official codes', () => {
        const markup = renderToStaticMarkup(
            <BoardCandidateTable
                candidates={[{
                    candidateId: 'candidate-1',
                    scriptCode: 'TROUBLE_BREWING',
                    playerCount: 5,
                    roleCodes: ['CHEF'],
                    roles: [{roleCode: 'CHEF', roleName: '厨师', roleType: {code: 1, desc: '镇民'}, alignment: {code: 1, desc: '善良'}}],
                    validation: {valid: true, roleTypeCounts: {}, violations: [], scores: []},
                    scores: [],
                }]}
                onSave={vi.fn()}
            />,
        )

        expect(markup).toContain('厨师')
        expect(markup).toContain('CHEF')
    })
})
```

- [ ] **Step 2: Run board frontend tests to verify they fail**

Run:

```bash
cd fe && bun run test -- BoardBuilderPage.test.tsx BoardCandidateTable.test.tsx
```

Expected: FAIL because `BoardCandidateTable` still renders only `roleCodes`.

- [ ] **Step 3: Update BoardCandidateTable role rendering**

In `BoardCandidateTable.tsx`, replace the role column render with:

```tsx
render: (_, record) => (
    <span>
        {(record.roles?.length ? record.roles : record.roleCodes.map((roleCode) => ({roleCode, roleName: roleCode}))).map((role) => (
            <Tag key={role.roleCode}>{role.roleName} ({role.roleCode})</Tag>
        ))}
    </span>
),
```

- [ ] **Step 4: Update saved board columns**

In `BoardBuilderPage.tsx`, change saved board role rendering:

```tsx
render: (_, record) => (
    <span>
        {(record.roles?.length ? record.roles : record.roleCodes.map((roleCode) => ({roleCode, roleName: roleCode}))).map((role) => (
            <Tag key={role.roleCode}>{role.roleName} ({role.roleCode})</Tag>
        ))}
    </span>
),
```

- [ ] **Step 5: Run board frontend tests**

Run:

```bash
cd fe && bun run test -- BoardBuilderPage.test.tsx BoardCandidateTable.test.tsx
```

Expected: PASS.

- [ ] **Step 6: Commit board frontend localization**

```bash
git add fe/src/modules/clocktower/components/BoardCandidateTable.tsx \
  fe/src/modules/clocktower/components/BoardCandidateTable.test.tsx \
  fe/src/modules/clocktower/BoardBuilderPage.tsx \
  fe/src/modules/clocktower/BoardBuilderPage.test.tsx
git commit -m "feat(clocktower): render localized board roles"
```

### Task 10: Localize Night Checklist DTOs And Frontend Rendering

**Files:**

- Modify: `be/src/main/java/top/egon/mario/clocktower/grimoire/dto/response/NightChecklistResponse.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/grimoire/dto/response/NightStepResponse.java`
- Modify: `be/src/main/java/top/egon/mario/clocktower/grimoire/service/impl/ClocktowerGrimoireServiceImpl.java`
- Modify: `be/src/test/java/top/egon/mario/clocktower/grimoire/ClocktowerNightChecklistServiceTests.java`
- Modify: `fe/src/modules/clocktower/clocktowerTypes.ts`
- Modify: `fe/src/modules/clocktower/components/NightChecklist.tsx`
- Modify: `fe/src/modules/clocktower/StorytellerGrimoirePage.test.tsx`

- [ ] **Step 1: Write failing backend checklist expectations**

In `ClocktowerNightChecklistServiceTests.java`, add assertions to
`firstNightChecklistContainsAliveRolesWithFirstNightOrder()`:

```java
assertThat(checklist.nightType()).isEqualTo(ClocktowerNightType.FIRST_NIGHT);
assertThat(checklist.steps()).extracting(NightStepResponse::roleName)
        .contains("共情者", "厨师", "投毒者");
assertThat(checklist.steps()).allSatisfy(step -> assertThat(step.roleType()).isNotNull());
```

Add imports for `ClocktowerNightType`.

- [ ] **Step 2: Update checklist response records**

Replace `NightChecklistResponse.java` with:

```java
package top.egon.mario.clocktower.grimoire.dto.response;

import top.egon.mario.clocktower.common.enums.ClocktowerNightType;

import java.util.List;

public record NightChecklistResponse(
        int nightNo,
        ClocktowerNightType nightType,
        List<NightStepResponse> steps,
        boolean completed
) {
}
```

Replace `NightStepResponse.java` with:

```java
package top.egon.mario.clocktower.grimoire.dto.response;

import top.egon.mario.clocktower.common.enums.ClocktowerRoleType;

public record NightStepResponse(
        int orderNo,
        Long seatId,
        String roleCode,
        String roleName,
        ClocktowerRoleType roleType,
        boolean wakeRequired,
        String skipReason,
        boolean completed
) {
}
```

- [ ] **Step 3: Update checklist mapping**

In `ClocktowerGrimoireServiceImpl.toNightStep(...)`, replace the constructor call with:

```java
return new NightStepResponse(order.getOrderNo(), seat == null ? null : seat.getId(), order.getRoleCode(),
        role == null ? order.getRoleCode() : role.getName(), role == null ? null : role.getRoleType(),
        true, null, false);
```

- [ ] **Step 4: Run backend checklist test**

Run:

```bash
mvn -q -f be/pom.xml -Dmaven.build.cache.enabled=false -Dtest=ClocktowerNightChecklistServiceTests test
```

Expected: PASS.

- [ ] **Step 5: Update frontend checklist types**

In `clocktowerTypes.ts`, update:

```ts
nightType: ClocktowerNightType
roleType?: ClocktowerRoleType | null
```

for `ClocktowerNightChecklistResponse` and `NightStepResponse`.

- [ ] **Step 6: Update NightChecklist rendering**

In `NightChecklist.tsx`, import `enumDesc` and `RoleTypeTag`, then render night type and role type:

```tsx
<Tag color="blue">{enumDesc(checklist.nightType)}</Tag>
```

In the columns, add:

```tsx
{
    title: '类型',
    dataIndex: 'roleType',
    width: 120,
    render: (value) => <RoleTypeTag value={value}/>,
},
```

- [ ] **Step 7: Run grimoire frontend test**

Run:

```bash
cd fe && bun run test -- StorytellerGrimoirePage.test.tsx
```

Expected: PASS.

- [ ] **Step 8: Commit checklist localization**

```bash
git add be/src/main/java/top/egon/mario/clocktower/grimoire \
  be/src/test/java/top/egon/mario/clocktower/grimoire/ClocktowerNightChecklistServiceTests.java \
  fe/src/modules/clocktower/clocktowerTypes.ts \
  fe/src/modules/clocktower/components/NightChecklist.tsx \
  fe/src/modules/clocktower/StorytellerGrimoirePage.test.tsx
git commit -m "feat(clocktower): localize night checklist"
```

### Task 11: Final Targeted Validation

**Files:**

- No source files should be changed in this task unless validation exposes a concrete defect.

- [ ] **Step 1: Run targeted backend tests**

Run:

```bash
mvn -q -f be/pom.xml -Dmaven.build.cache.enabled=false -Dtest=ClocktowerSchemaMigrationTests,ClocktowerJpaMappingTests,ClocktowerScriptServiceTests,ClocktowerBoardServiceTests,ClocktowerBoardControllerTests,ClocktowerNightChecklistServiceTests test
```

Expected: PASS.

- [ ] **Step 2: Run targeted frontend tests**

Run:

```bash
cd fe && bun run test -- clocktowerService.test.ts RuleDataPage.test.tsx BoardBuilderPage.test.tsx BoardCandidateTable.test.tsx StorytellerGrimoirePage.test.tsx
```

Expected: PASS.

- [ ] **Step 3: Inspect git diff**

Run:

```bash
git status --short
git diff --stat
```

Expected: only files listed in this plan are modified, unless a test-driven correction required a directly related file.

- [ ] **Step 4: Commit validation fixes if any were required**

If Step 1 or Step 2 exposed a concrete defect and a source fix was made:

```bash
git add be/src/main/java/top/egon/mario/clocktower be/src/test/java/top/egon/mario/clocktower fe/src/modules/clocktower
git commit -m "fix(clocktower): stabilize localized rule data flow"
```

If no source fix was made, do not create an empty commit.

## Self-Review Notes

- Spec coverage:
    - Four-menu confirmation is documented as scope and no new menu task is included.
    - Static data source and exactly one migration are covered by Tasks 1 and 6.
    - Backend coded enums are covered by Tasks 2 and 4.
    - Localized DTOs and grouped night order are covered by Task 3.
    - Script-scoped board generation and role summaries are covered by Task 5.
    - Frontend types, rules page, board page, and night checklist are covered by Tasks 7 through 10.
    - Validation is covered by Task 11.
- Data gap:
    - Complete role and night-order content is an execution precondition. The plan includes a stop condition rather than
      inventing data.
- Type consistency:
    - Backend uses `roleName`, `roleType`, `alignment`, `orderNo`, and grouped `firstNight`/`otherNight`.
    - Frontend mirrors those property names.
