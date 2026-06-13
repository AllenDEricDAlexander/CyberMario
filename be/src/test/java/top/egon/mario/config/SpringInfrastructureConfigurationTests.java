package top.egon.mario.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import javax.sql.DataSource;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies shared Spring infrastructure settings used by persistence and API JSON responses.
 */
@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
class SpringInfrastructureConfigurationTests {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ChatModel chatModel;

    @Test
    void datasourceUsesConfiguredHikariPool() {
        assertThat(dataSource).isInstanceOf(HikariDataSource.class);

        HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
        assertThat(hikariDataSource.getPoolName()).isEqualTo("CyberMarioTestHikariPool");
        assertThat(hikariDataSource.getMaximumPoolSize()).isEqualTo(4);
        assertThat(hikariDataSource.getMinimumIdle()).isEqualTo(1);
    }

    @Test
    void objectMapperUsesConfiguredJsonSerialization() throws Exception {
        JsonNode jsonNode = objectMapper.readTree(objectMapper.writeValueAsString(
                new JsonPayload("visible", null, LocalDateTime.of(2026, 6, 13, 10, 30, 5))
        ));

        assertThat(jsonNode.get("name").asText()).isEqualTo("visible");
        assertThat(jsonNode.has("emptyValue")).isFalse();
        assertThat(jsonNode.get("createdAt").asText()).isEqualTo("2026-06-13T10:30:05");
    }

    private record JsonPayload(String name, String emptyValue, LocalDateTime createdAt) {
    }

}
