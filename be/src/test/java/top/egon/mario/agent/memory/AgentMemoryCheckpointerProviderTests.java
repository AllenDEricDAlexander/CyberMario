package top.egon.mario.agent.memory;

import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import top.egon.mario.agent.memory.checkpoint.AgentMemoryCheckpointerProvider;
import top.egon.mario.agent.memory.checkpoint.PostgresSaverProperties;

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
}
