package top.egon.mario.agent.memory;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the agent memory migration creates the expected persistence tables.
 */
class AgentMemorySchemaMigrationTests {

    @Test
    void migrationCreatesAllMemoryTablesWithSessionAndUserIndexes() throws IOException {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V17__create_agent_memory_schema.sql"));

        assertThat(sql).contains("CREATE TABLE agent_memory_session");
        assertThat(sql).contains("CREATE TABLE agent_memory_message");
        assertThat(sql).contains("CREATE TABLE agent_long_term_memory");
        assertThat(sql).contains("CREATE TABLE agent_long_term_memory_version");
        assertThat(sql).contains("CREATE TABLE agent_memory_extraction_audit");
        assertThat(sql).contains("CONSTRAINT uk_agent_memory_session_id UNIQUE (session_id)");
        assertThat(sql).contains("idx_agent_memory_session_user_status");
        assertThat(sql).contains("idx_agent_memory_message_session_seq");
        assertThat(sql).contains("idx_agent_long_term_memory_user_scope");
        assertThat(sql).contains("idx_agent_memory_extraction_user_created");
        assertThat(sql).contains("long_term_extraction_enabled BOOLEAN                  NOT NULL DEFAULT TRUE");
        assertThat(sql).contains("short_term_window_turns      INTEGER                  NOT NULL DEFAULT 10");
    }

    @Test
    void postgresCheckpointMigrationCreatesIdempotentGraphSchema() throws IOException {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/postgresql/V23__create_agent_checkpoint_schema.sql"));

        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS GraphThread");
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS GraphCheckpoint");
        assertThat(sql).containsPattern("state_data\\s+JSONB\\s+NOT NULL");
        assertThat(sql).contains("CREATE INDEX IF NOT EXISTS idx_lg4jcheckpoint_thread_id");
        assertThat(sql).contains("CREATE INDEX IF NOT EXISTS idx_lg4jcheckpoint_thread_id_saved_at_desc");
        assertThat(sql).contains("CREATE UNIQUE INDEX IF NOT EXISTS idx_unique_lg4jthread_thread_name_unreleased");
        assertThat(sql).contains("WHERE is_released = FALSE");
    }
}
