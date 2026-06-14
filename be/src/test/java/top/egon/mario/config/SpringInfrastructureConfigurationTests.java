package top.egon.mario.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import reactor.core.scheduler.Scheduler;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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

    @Autowired
    private Environment environment;

    @Autowired
    private Scheduler blockingScheduler;

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

    @Test
    void objectMapperUsesConfiguredJsonDeserialization() throws Exception {
        JsonPayload payload = objectMapper.readValue(
                "{\"name\":\"visible\",\"extra\":\"ignored\"}",
                JsonPayload.class
        );
        JsonNode singleQuoteNode = objectMapper.readTree("{'name':'single-quote'}");
        JsonPayload controlCharPayload = objectMapper.readValue(
                "{\"name\":\"line\nbreak\"}",
                JsonPayload.class
        );

        assertThat(payload.name()).isEqualTo("visible");
        assertThat(singleQuoteNode.get("name").asText()).isEqualTo("single-quote");
        assertThat(controlCharPayload.name()).isEqualTo("line\nbreak");
    }

    @Test
    void shutdownSettingsEnableGracefulStopAndTaskDrain() {
        assertThat(environment.getProperty("server.shutdown")).isEqualTo("graceful");
        assertThat(environment.getProperty("spring.lifecycle.timeout-per-shutdown-phase", Duration.class))
                .isEqualTo(Duration.ofSeconds(30));
        assertThat(environment.getProperty("spring.task.execution.shutdown.await-termination", Boolean.class))
                .isTrue();
        assertThat(environment.getProperty("spring.task.execution.shutdown.await-termination-period", Duration.class))
                .isEqualTo(Duration.ofSeconds(30));
        assertThat(environment.getProperty("spring.task.scheduling.shutdown.await-termination", Boolean.class))
                .isTrue();
        assertThat(environment.getProperty("spring.task.scheduling.shutdown.await-termination-period", Duration.class))
                .isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void blockingSchedulerUsesVirtualThreads() throws InterruptedException {
        AtomicReference<String> workerThreadName = new AtomicReference<>();
        AtomicReference<Boolean> workerThreadVirtual = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        blockingScheduler.schedule(() -> {
            workerThreadName.set(Thread.currentThread().getName());
            workerThreadVirtual.set(Thread.currentThread().isVirtual());
            latch.countDown();
        });

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(workerThreadVirtual).hasValue(true);
        assertThat(workerThreadName).hasValueSatisfying(threadName ->
                assertThat(threadName).contains("blocking-virtual"));
    }

    private record JsonPayload(String name, String emptyValue, LocalDateTime createdAt) {
    }

}
