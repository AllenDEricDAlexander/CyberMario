package top.egon.mario.agent.mcp;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the MCP management schema is available after Flyway migration.
 */
@SpringJUnitConfig(McpSchemaMigrationTests.MigrationTestConfiguration.class)
class McpSchemaMigrationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void mcpTablesExist() {
        Integer tableCount = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.tables
                where table_name in (
                    'agent_mcp_server_config',
                    'agent_mcp_tool_config',
                    'agent_mcp_tool_call_log'
                )
                """, Integer.class);

        assertThat(tableCount).isEqualTo(3);
    }

    /**
     * Provides a minimal migration context so schema smoke tests avoid full application startup.
     */
    @Configuration(proxyBeanMethods = false)
    static class MigrationTestConfiguration {

        @Bean
        DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:mcp_schema_migration_%s;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1"
                    .formatted(UUID.randomUUID()));
            dataSource.setUsername("sa");
            dataSource.setPassword("");
            return dataSource;
        }

        @Bean
        Flyway flyway(DataSource dataSource) {
            Flyway flyway = Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .load();
            flyway.migrate();
            return flyway;
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource, Flyway flyway) {
            return new JdbcTemplate(dataSource);
        }
    }
}
