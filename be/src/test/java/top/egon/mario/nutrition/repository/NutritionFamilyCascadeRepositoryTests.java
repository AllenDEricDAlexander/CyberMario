package top.egon.mario.nutrition.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps code-level family cascade coverage aligned with the schema.
 */
@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
class NutritionFamilyCascadeRepositoryTests {

    @Autowired
    private NutritionFamilyCascadeRepository cascadeRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void cascadeCoversEveryNutritionTableWithAFamilyIdColumn() {
        Set<String> schemaTables = Set.copyOf(jdbcTemplate.queryForList("""
                SELECT table_name
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND column_name = 'family_id'
                  AND table_name LIKE 'nutrition_%'
                """, String.class));

        assertThat(cascadeRepository.familyScopedTables())
                .containsExactlyInAnyOrderElementsOf(schemaTables);
    }
}
