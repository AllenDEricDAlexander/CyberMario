package top.egon.mario.agent.observability;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the agent run audit migration stores full plaintext payload columns.
 */
class AgentRunAuditSchemaMigrationTests {

    @Test
    void migrationCreatesRunAndEventAuditTablesWithTextPayloadColumns() throws IOException {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V16__create_agent_run_audit.sql"));

        assertThat(sql).contains("CREATE TABLE agent_run_audit");
        assertThat(sql).contains("CREATE TABLE agent_run_event_audit");
        assertThat(sql).contains("effective_config_json TEXT");
        assertThat(sql).containsPattern("user_message\\s+TEXT");
        assertThat(sql).containsPattern("final_message\\s+TEXT");
        assertThat(sql).containsPattern("final_thinking\\s+TEXT");
        assertThat(sql).containsPattern("prompt_text\\s+TEXT");
        assertThat(sql).containsPattern("request_messages_json\\s+TEXT");
        assertThat(sql).containsPattern("request_options_json\\s+TEXT");
        assertThat(sql).containsPattern("available_tools_json\\s+TEXT");
        assertThat(sql).containsPattern("response_text\\s+TEXT");
        assertThat(sql).containsPattern("tool_arguments\\s+TEXT");
        assertThat(sql).containsPattern("tool_result\\s+TEXT");
        assertThat(sql).containsPattern("metadata_json\\s+TEXT");
        assertThat(sql).containsPattern("error_message\\s+TEXT");
        assertThat(sql).contains("mcp_server_code");
        assertThat(sql).contains("CONSTRAINT fk_agent_run_event_run");
        assertThat(sql).contains("idx_agent_run_audit_status_created");
        assertThat(sql).contains("idx_agent_run_event_run_seq");
    }
}
