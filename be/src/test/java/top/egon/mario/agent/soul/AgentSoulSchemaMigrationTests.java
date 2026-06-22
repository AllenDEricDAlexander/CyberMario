package top.egon.mario.agent.soul;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the Agent SoulMD migration stores current user SoulMD and version snapshots.
 */
class AgentSoulSchemaMigrationTests {

    @Test
    void migrationAddsUserSoulFieldsAndVersionTable() throws IOException {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V25__add_agent_soulmd.sql"));

        assertThat(sql).contains("ALTER TABLE sys_user ADD COLUMN soul_md TEXT");
        assertThat(sql).contains("ALTER TABLE sys_user ADD COLUMN soul_md_enabled BOOLEAN NOT NULL DEFAULT TRUE");
        assertThat(sql).contains("ALTER TABLE sys_user ADD COLUMN soul_md_chars INTEGER NOT NULL DEFAULT 0");
        assertThat(sql).contains("ALTER TABLE sys_user ADD COLUMN soul_md_version_no INTEGER NOT NULL DEFAULT 1");
        assertThat(sql).contains("ALTER TABLE sys_user ADD COLUMN soul_md_updated_at TIMESTAMP WITH TIME ZONE");
        assertThat(sql).contains("CREATE TABLE agent_soul_md_version");
        assertThat(sql).contains("change_type");
        assertThat(sql).contains("change_summary");
        assertThat(sql).contains("source_type");
        assertThat(sql).contains("source_session_id");
        assertThat(sql).contains("source_message_ids");
        assertThat(sql).contains("model_provider");
        assertThat(sql).contains("model_name");
        assertThat(sql).contains("request_id");
        assertThat(sql).contains("trace_id");
        assertThat(sql).contains("created_at");
        assertThat(sql).contains("idx_agent_soul_md_version_user_version");
        assertThat(sql).contains("idx_agent_soul_md_version_user_created");
    }
}
