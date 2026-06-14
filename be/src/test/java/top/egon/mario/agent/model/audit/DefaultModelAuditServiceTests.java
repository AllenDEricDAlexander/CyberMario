package top.egon.mario.agent.model.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import top.egon.mario.agent.model.api.ModelOptions;
import top.egon.mario.agent.model.api.ModelProviderType;
import top.egon.mario.agent.model.api.ModelScenario;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Verifies model audit persistence mapping.
 */
class DefaultModelAuditServiceTests {

    @Test
    void recordPersistsAuditEvent() {
        ModelAuditRepository repository = mock(ModelAuditRepository.class);
        DefaultModelAuditService service = new DefaultModelAuditService(repository, new ObjectMapper());
        ModelOptions options = new ModelOptions(new BigDecimal("0.7"), 2048, new BigDecimal("0.9"),
                50, true, 512, true, true, Map.of("seed", 1));
        Instant startedAt = Instant.parse("2026-06-14T01:00:00Z");
        Instant finishedAt = Instant.parse("2026-06-14T01:00:01Z");
        ModelAuditEvent event = new ModelAuditEvent(
                "request-1",
                "trace-1",
                8L,
                "session-1",
                "thread-1",
                ModelScenario.AGENT_CHAT,
                ModelProviderType.DASHSCOPE,
                "qwen-plus",
                options,
                new ModelTokenUsage(10, 5, 15, TokenUsageSource.PROVIDER),
                true,
                ModelAuditStatus.SUCCESS,
                startedAt,
                finishedAt,
                1000L,
                null,
                null,
                20,
                15,
                "127.0.0.1",
                "JUnit"
        );

        service.record(event);

        ArgumentCaptor<ModelAuditPo> captor = ArgumentCaptor.forClass(ModelAuditPo.class);
        verify(repository).save(captor.capture());
        ModelAuditPo po = captor.getValue();
        assertThat(po.getRequestId()).isEqualTo("request-1");
        assertThat(po.getTraceId()).isEqualTo("trace-1");
        assertThat(po.getUserId()).isEqualTo(8L);
        assertThat(po.getSessionId()).isEqualTo("session-1");
        assertThat(po.getThreadId()).isEqualTo("thread-1");
        assertThat(po.getScenario()).isEqualTo(ModelScenario.AGENT_CHAT);
        assertThat(po.getProvider()).isEqualTo(ModelProviderType.DASHSCOPE);
        assertThat(po.getModel()).isEqualTo("qwen-plus");
        assertThat(po.getOptionsJson()).contains("\"maxTokens\":2048");
        assertThat(po.getOptionsJson()).contains("\"seed\":1");
        assertThat(po.getPromptTokens()).isEqualTo(10);
        assertThat(po.getCompletionTokens()).isEqualTo(5);
        assertThat(po.getTotalTokens()).isEqualTo(15);
        assertThat(po.getTokenUsageSource()).isEqualTo(TokenUsageSource.PROVIDER);
        assertThat(po.isStreaming()).isTrue();
        assertThat(po.getStatus()).isEqualTo(ModelAuditStatus.SUCCESS);
        assertThat(po.getStartedAt()).isEqualTo(startedAt);
        assertThat(po.getFinishedAt()).isEqualTo(finishedAt);
        assertThat(po.getDurationMs()).isEqualTo(1000L);
        assertThat(po.getPromptChars()).isEqualTo(20);
        assertThat(po.getCompletionChars()).isEqualTo(15);
        assertThat(po.getIp()).isEqualTo("127.0.0.1");
        assertThat(po.getUserAgent()).isEqualTo("JUnit");
        assertThat(po.getCreatedAt()).isNotNull();
    }

    @Test
    void recordTruncatesLongErrorMessage() {
        ModelAuditRepository repository = mock(ModelAuditRepository.class);
        DefaultModelAuditService service = new DefaultModelAuditService(repository, new ObjectMapper());
        ModelAuditEvent event = new ModelAuditEvent(
                "request-1",
                "trace-1",
                8L,
                null,
                null,
                ModelScenario.AGENT_CHAT,
                ModelProviderType.DASHSCOPE,
                "qwen-plus",
                null,
                ModelTokenUsage.unavailable(),
                false,
                ModelAuditStatus.FAILED,
                Instant.parse("2026-06-14T01:00:00Z"),
                Instant.parse("2026-06-14T01:00:01Z"),
                1000L,
                IllegalStateException.class.getName(),
                "x".repeat(1200),
                20,
                0,
                null,
                null
        );

        service.record(event);

        ArgumentCaptor<ModelAuditPo> captor = ArgumentCaptor.forClass(ModelAuditPo.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getErrorMessage()).hasSize(1024);
    }

}
