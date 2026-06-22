package top.egon.mario.agent.memory;

import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import top.egon.mario.agent.memory.checkpoint.AgentMemoryCheckpointerProvider;
import top.egon.mario.agent.memory.checkpoint.PostgresSaverProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies checkpoint saver selection without requiring a live PostgreSQL database.
 */
class AgentMemoryCheckpointerProviderTests {

    @Test
    void disabledProviderFallsBackToMemorySaver() {
        PostgresSaverProperties properties = new PostgresSaverProperties();
        properties.setEnabled(false);
        DataSourceProperties dataSourceProperties = new DataSourceProperties();
        dataSourceProperties.setUrl("jdbc:h2:mem:test");

        AgentMemoryCheckpointerProvider provider =
                new AgentMemoryCheckpointerProvider(dataSourceProperties, properties);

        assertThat(provider.saver()).isInstanceOf(MemorySaver.class);
    }

    @Test
    void createTablesIsDisabledByDefaultBecauseFlywayOwnsCheckpointSchema() {
        PostgresSaverProperties properties = new PostgresSaverProperties();

        assertThat(properties.isCreateTables()).isFalse();
    }

    @Test
    void applicationDefaultDoesNotEnableRuntimeCheckpointDdl() throws IOException {
        String yaml = Files.readString(Path.of("src/main/resources/application.yaml"));

        assertThat(yaml).contains("create-tables: ${AGENT_MEMORY_CHECKPOINTER_CREATE_TABLES:false}");
    }
}
