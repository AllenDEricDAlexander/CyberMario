package top.egon.mario.rbac.activation;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves V53 is unique, contains the required constraints, and backfills a V52 user.
 */
class RbacAccountActivationSchemaMigrationTests {

    private static final Path MIGRATION_DIRECTORY = Path.of("src/main/resources/db/migration");
    private static final Path MIGRATION = MIGRATION_DIRECTORY.resolve(
            "V53__add_rbac_account_activation_ott.sql");

    @Test
    void v53IsTheOnlyAccountActivationMigrationAndDefinesTheRequiredSchema() throws Exception {
        try (var migrations = Files.list(MIGRATION_DIRECTORY)) {
            assertThat(migrations
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith("V53__")))
                    .containsExactly("V53__add_rbac_account_activation_ott.sql");
        }
        String sql = Files.readString(MIGRATION);
        assertThat(sql).contains(
                "ADD COLUMN activated_at TIMESTAMP WITH TIME ZONE",
                "SET activated_at = created_at",
                "CREATE TABLE sys_one_time_token",
                "CONSTRAINT uk_one_time_token_hash UNIQUE (token_hash)",
                "CONSTRAINT uk_one_time_token_user_purpose UNIQUE (user_id, purpose)",
                "CONSTRAINT fk_one_time_token_user FOREIGN KEY (user_id) REFERENCES sys_user (id)",
                "CREATE INDEX idx_one_time_token_expires ON sys_one_time_token (expires_at)"
        );
    }

    @Test
    void v53BackfillsUsersThatExistAtV52() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:rbac_activation_migration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("52")).load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("""
                INSERT INTO sys_user
                    (account_no, username, password_hash, status, locked, password_expired,
                     created_at, updated_at, version, deleted)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, "legacy", "legacy", "hash", 1, false, false,
                java.time.OffsetDateTime.parse("2026-01-02T03:04:05Z"),
                java.time.OffsetDateTime.parse("2026-01-02T03:04:05Z"), 0L, false);

        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("53")).load().migrate();

        assertThat(jdbc.queryForObject(
                "SELECT activated_at = created_at FROM sys_user WHERE account_no = 'legacy'", Boolean.class))
                .isTrue();
    }
}
