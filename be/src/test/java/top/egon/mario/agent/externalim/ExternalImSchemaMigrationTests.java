package top.egon.mario.agent.externalim;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalImSchemaMigrationTests {

    @Test
    void migrationCreatesDurableExternalChatAndDirectionalMemorySchema() throws IOException {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V52__create_external_im_chat_guard.sql"));

        assertThat(sql).contains("CREATE TABLE agent_memory_space");
        assertThat(sql).contains("CREATE TABLE agent_external_chat_binding");
        assertThat(sql).contains("CREATE TABLE agent_external_chat_event");
        assertThat(sql).contains("CREATE TABLE agent_chat_guard_audit");
        assertThat(sql).contains("CONSTRAINT uk_agent_memory_space_space_id UNIQUE (space_id)");
        assertThat(sql).contains("CONSTRAINT uk_agent_external_binding_conversation UNIQUE");
        assertThat(sql).contains("CONSTRAINT uk_agent_external_event_source UNIQUE");
        assertThat(sql).contains("ALTER TABLE agent_memory_session ADD COLUMN memory_domain");
        assertThat(sql).contains("ALTER TABLE agent_memory_message ADD COLUMN external_event_id");
        assertThat(sql).contains("ALTER TABLE agent_long_term_memory ADD COLUMN scope_key");
        assertThat(sql).contains("DROP INDEX idx_agent_long_term_memory_user_scope");
        assertThat(sql).contains("idx_agent_long_term_memory_owner_scope_key");
        assertThat(sql).doesNotContain("TOKEN", "SECRET", "AUTHORIZATION");
    }
}
