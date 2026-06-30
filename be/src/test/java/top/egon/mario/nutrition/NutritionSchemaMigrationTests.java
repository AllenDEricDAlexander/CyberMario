package top.egon.mario.nutrition;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class NutritionSchemaMigrationTests {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V31__create_nutrition_mvp_schema.sql");

    private static final List<String> NUTRITION_TABLES = List.of(
            "nutrition_clan",
            "nutrition_family",
            "nutrition_clan_family",
            "nutrition_member_profile",
            "nutrition_health_profile",
            "nutrition_scoped_role_binding",
            "nutrition_data_grant",
            "nutrition_standard_food",
            "nutrition_health_tag",
            "nutrition_recipe",
            "nutrition_recipe_ingredient",
            "nutrition_recipe_step",
            "nutrition_import_job",
            "nutrition_import_error",
            "nutrition_ai_recommendation_job",
            "nutrition_ai_recommendation",
            "nutrition_risk_check_result",
            "nutrition_meal_plan",
            "nutrition_meal_plan_item",
            "nutrition_meal_confirmation",
            "nutrition_meal_confirmation_item",
            "nutrition_meal_operation_log",
            "nutrition_shopping_list",
            "nutrition_shopping_list_item",
            "nutrition_food_price_record",
            "nutrition_budget_rule",
            "nutrition_budget_snapshot",
            "nutrition_record",
            "nutrition_record_adjustment",
            "nutrition_extra_food_record",
            "nutrition_report_snapshot"
    );

    private static final List<String> AUDIT_COLUMNS = List.of(
            "id",
            "created_at",
            "updated_at",
            "created_by",
            "updated_by",
            "version",
            "deleted"
    );

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

    @Test
    void migrationCreatesAllNutritionTablesWithAuditing() throws IOException {
        String sql = Files.readString(MIGRATION);

        for (String table : NUTRITION_TABLES) {
            String tableDefinition = tableDefinition(sql, table);
            assertThat(tableDefinition).as(table + " definition").isNotBlank();

            for (String column : AUDIT_COLUMNS) {
                assertTableContains(sql, table, column);
            }
        }
    }

    @Test
    void migrationDefinesCoreNutritionIndexes() throws IOException {
        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains("CREATE INDEX idx_nutrition_member_profile_family");
        assertThat(sql).contains("CREATE INDEX idx_nutrition_health_profile_member");
        assertThat(sql).contains("CREATE INDEX idx_nutrition_data_grant_family");
        assertThat(sql).contains("CREATE INDEX idx_nutrition_recipe_family");
        assertThat(sql).contains("CREATE INDEX idx_nutrition_import_job_status");
        assertThat(sql).contains("CREATE INDEX idx_nutrition_ai_recommendation_job_status");
        assertThat(sql).contains("CREATE INDEX idx_nutrition_meal_plan_family_date");
        assertThat(sql).contains("CREATE INDEX idx_nutrition_meal_confirmation_member");
        assertThat(sql).contains("CREATE INDEX idx_nutrition_shopping_list_family_date");
        assertThat(sql).contains("CREATE INDEX idx_nutrition_food_price_record_family_date");
        assertThat(sql).contains("CREATE INDEX idx_nutrition_record_member_date");
    }

    @Test
    void migrationClasspathRunsOnH2AndCreatesNutritionTables() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:nutrition_schema_%s;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1"
                .formatted(UUID.randomUUID()));
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        for (String table : NUTRITION_TABLES) {
            Integer tableCount = jdbcTemplate.queryForObject("""
                    select count(*)
                    from information_schema.tables
                    where table_schema = 'public'
                      and table_name = ?
                    """, Integer.class, table);
            assertThat(tableCount).as(table + " exists after Flyway migration").isEqualTo(1);
        }
    }

    private static void assertTableContains(String sql, String table, String column) {
        assertThat(tableDefinition(sql, table))
                .as(table + "." + column)
                .containsPattern("\\n\\s+" + column + "\\s");
    }

    private static String tableDefinition(String sql, String table) {
        Pattern pattern = Pattern.compile("CREATE TABLE " + table + " \\((.*?)\\);", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(sql);
        assertThat(matcher.find()).as(table + " create statement").isTrue();
        return matcher.group(1);
    }
}
