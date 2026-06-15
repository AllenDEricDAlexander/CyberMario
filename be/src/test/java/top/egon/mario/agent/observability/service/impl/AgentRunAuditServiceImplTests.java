package top.egon.mario.agent.observability.service.impl;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import top.egon.mario.agent.observability.dto.request.AgentRunAuditQuery;
import top.egon.mario.agent.observability.po.AgentRunAuditPo;
import top.egon.mario.agent.observability.po.AgentRunEventAuditPo;
import top.egon.mario.agent.observability.po.enums.AgentRunAuditStatus;
import top.egon.mario.agent.observability.po.enums.AgentRunEventStatus;
import top.egon.mario.agent.observability.po.enums.AgentRunEventType;
import top.egon.mario.agent.observability.po.enums.AgentRunToolType;
import top.egon.mario.agent.observability.repository.AgentRunAuditRepository;
import top.egon.mario.agent.observability.repository.AgentRunEventAuditRepository;
import top.egon.mario.agent.observability.service.model.AgentRunAuditContext;
import top.egon.mario.agent.observability.service.model.AgentRunAuditStart;
import top.egon.mario.agent.observability.service.model.AgentRunEventRecord;
import top.egon.mario.agent.service.AgentException;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies agent run audit persistence mapping and super-admin access rules.
 */
class AgentRunAuditServiceImplTests {

    @Test
    void startEventAndCompletePersistPlaintextAuditData() {
        AgentRunAuditRepository runRepository = mock(AgentRunAuditRepository.class);
        AgentRunEventAuditRepository eventRepository = mock(AgentRunEventAuditRepository.class);
        AtomicReference<AgentRunAuditPo> savedRun = new AtomicReference<>();
        List<AgentRunEventAuditPo> savedEvents = new ArrayList<>();
        when(runRepository.save(any(AgentRunAuditPo.class))).thenAnswer(invocation -> {
            AgentRunAuditPo po = invocation.getArgument(0);
            if (po.getId() == null) {
                po.setId(100L);
            }
            savedRun.set(po);
            return po;
        });
        when(runRepository.findById(100L)).thenAnswer(invocation -> Optional.of(savedRun.get()));
        when(runRepository.incrementModelCallCount(100L)).thenAnswer(invocation -> {
            savedRun.get().setModelCallCount(savedRun.get().getModelCallCount() + 1);
            return 1;
        });
        when(runRepository.incrementToolCallCount(100L)).thenAnswer(invocation -> {
            savedRun.get().setToolCallCount(savedRun.get().getToolCallCount() + 1);
            return 1;
        });
        when(runRepository.incrementMcpToolCallCount(100L)).thenAnswer(invocation -> {
            savedRun.get().setMcpToolCallCount(savedRun.get().getMcpToolCallCount() + 1);
            return 1;
        });
        when(eventRepository.save(any(AgentRunEventAuditPo.class))).thenAnswer(invocation -> {
            AgentRunEventAuditPo po = invocation.getArgument(0);
            if (po.getId() == null) {
                po.setId((long) savedEvents.size() + 1);
            }
            savedEvents.add(po);
            return po;
        });
        AgentRunAuditServiceImpl service = new AgentRunAuditServiceImpl(runRepository, eventRepository,
                new ObjectMapper());

        AgentRunAuditContext context = service.start(new AgentRunAuditStart(
                "request-1",
                "trace-1",
                8L,
                "luigi",
                "thread-1",
                9L,
                "fingerprint-1",
                "{\"systemPrompt\":\"full prompt\"}",
                "用户明文输入",
                java.util.Map.of(),
                Instant.parse("2026-06-15T01:00:00Z")
        ));
        service.record(context, AgentRunEventRecord.builder(AgentRunEventType.MODEL_REQUEST)
                .reactRound(1)
                .status(AgentRunEventStatus.SUCCESS)
                .promptText("system prompt\nuser prompt")
                .requestMessagesJson("[{\"role\":\"user\",\"content\":\"用户明文输入\"}]")
                .availableToolsJson("[\"searchWikipedia\"]")
                .startedAt(Instant.parse("2026-06-15T01:00:01Z"))
                .finishedAt(Instant.parse("2026-06-15T01:00:02Z"))
                .durationMs(1000L)
                .build());
        service.record(context, AgentRunEventRecord.builder(AgentRunEventType.TOOL_RESPONSE)
                .reactRound(1)
                .toolName("searchWikipedia")
                .toolCallId("call-1")
                .toolType(AgentRunToolType.LOCAL)
                .status(AgentRunEventStatus.SUCCESS)
                .toolResult("完整工具返回")
                .startedAt(Instant.parse("2026-06-15T01:00:02Z"))
                .finishedAt(Instant.parse("2026-06-15T01:00:03Z"))
                .durationMs(1000L)
                .build());
        service.complete(context, "最终回答", "思考内容", Instant.parse("2026-06-15T01:00:04Z"));

        AgentRunAuditPo run = savedRun.get();
        assertThat(run.getStatus()).isEqualTo(AgentRunAuditStatus.SUCCESS);
        assertThat(run.getUserMessage()).isEqualTo("用户明文输入");
        assertThat(run.getFinalMessage()).isEqualTo("最终回答");
        assertThat(run.getFinalThinking()).isEqualTo("思考内容");
        assertThat(run.getModelCallCount()).isEqualTo(1);
        assertThat(run.getToolCallCount()).isEqualTo(1);
        assertThat(run.getMcpToolCallCount()).isZero();
        assertThat(savedEvents).extracting(AgentRunEventAuditPo::getSeqNo).containsExactly(0, 1, 2, 3, 4, 5, 6);
        assertThat(savedEvents).extracting(AgentRunEventAuditPo::getEventType)
                .containsExactly(AgentRunEventType.RUN_STARTED, AgentRunEventType.USER_MESSAGE,
                        AgentRunEventType.MODEL_REQUEST, AgentRunEventType.TOOL_RESPONSE,
                        AgentRunEventType.ASSISTANT_THINK, AgentRunEventType.ASSISTANT_MESSAGE,
                        AgentRunEventType.RUN_COMPLETED);
    }

    @Test
    void pageRequiresSuperAdminAndMapsRunFields() {
        AgentRunAuditRepository runRepository = mock(AgentRunAuditRepository.class);
        AgentRunEventAuditRepository eventRepository = mock(AgentRunEventAuditRepository.class);
        AgentRunAuditPo run = runPo();
        when(runRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class),
                any(PageRequest.class))).thenReturn(new PageImpl<>(List.of(run), PageRequest.of(0, 20), 1));
        AgentRunAuditServiceImpl service = new AgentRunAuditServiceImpl(runRepository, eventRepository,
                new ObjectMapper());

        var page = service.page(new AgentRunAuditQuery(null, null, null, null, null, null, null,
                null, "searchWikipedia", null, null), PageRequest.of(0, 20), principal("SUPER_ADMIN"));

        assertThat(page.getContent()).singleElement().satisfies(response -> {
            assertThat(response.id()).isEqualTo(100L);
            assertThat(response.effectiveConfigJson()).isNull();
            assertThat(response.userMessage()).isNull();
            assertThat(response.finalMessage()).isNull();
            assertThat(response.finalThinking()).isNull();
            assertThat(response.modelCallCount()).isEqualTo(1);
        });
        assertThatThrownBy(() -> service.page(null, PageRequest.of(0, 20), principal("CHAT_BASIC")))
                .isInstanceOf(AgentException.class)
                .hasMessageContaining("only available to super administrators");
    }

    @Test
    void detailRequiresSuperAdminAndMapsPlaintextRunPayload() {
        AgentRunAuditRepository runRepository = mock(AgentRunAuditRepository.class);
        AgentRunEventAuditRepository eventRepository = mock(AgentRunEventAuditRepository.class);
        when(runRepository.findById(100L)).thenReturn(Optional.of(runPo()));
        AgentRunAuditServiceImpl service = new AgentRunAuditServiceImpl(runRepository, eventRepository,
                new ObjectMapper());

        var response = service.detail(100L, principal("SUPER_ADMIN"));

        assertThat(response.effectiveConfigJson()).isEqualTo("{\"systemPrompt\":\"full prompt\"}");
        assertThat(response.userMessage()).isEqualTo("用户明文输入");
        assertThat(response.finalMessage()).isEqualTo("最终回答");
        assertThat(response.finalThinking()).isEqualTo("思考内容");
        assertThatThrownBy(() -> service.detail(100L, principal("CHAT_BASIC")))
                .isInstanceOf(AgentException.class)
                .hasMessageContaining("only available to super administrators");
    }

    @Test
    void eventsRequireSuperAdminAndReturnPlaintextTimeline() {
        AgentRunAuditRepository runRepository = mock(AgentRunAuditRepository.class);
        AgentRunEventAuditRepository eventRepository = mock(AgentRunEventAuditRepository.class);
        AgentRunEventAuditPo event = new AgentRunEventAuditPo();
        event.setId(1L);
        event.setRunId(100L);
        event.setSeqNo(2);
        event.setEventType(AgentRunEventType.TOOL_RESPONSE);
        event.setStatus(AgentRunEventStatus.SUCCESS);
        event.setToolName("searchWikipedia");
        event.setToolResult("完整工具返回");
        event.setStartedAt(Instant.parse("2026-06-15T01:00:02Z"));
        event.setCreatedAt(Instant.parse("2026-06-15T01:00:02Z"));
        when(runRepository.existsById(100L)).thenReturn(true);
        when(eventRepository.findByRunIdOrderBySeqNoAsc(100L)).thenReturn(List.of(event));
        AgentRunAuditServiceImpl service = new AgentRunAuditServiceImpl(runRepository, eventRepository,
                new ObjectMapper());

        var events = service.events(100L, principal("SUPER_ADMIN"));

        assertThat(events).singleElement().satisfies(response -> {
            assertThat(response.toolName()).isEqualTo("searchWikipedia");
            assertThat(response.toolResult()).isEqualTo("完整工具返回");
        });
        assertThatThrownBy(() -> service.events(100L, principal("CHAT_BASIC")))
                .isInstanceOf(AgentException.class)
                .hasMessageContaining("only available to super administrators");
    }

    @Test
    void eventCountersUseAtomicRepositoryUpdates() {
        AgentRunAuditRepository runRepository = mock(AgentRunAuditRepository.class);
        AgentRunEventAuditRepository eventRepository = mock(AgentRunEventAuditRepository.class);
        when(eventRepository.save(any(AgentRunEventAuditPo.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AgentRunAuditServiceImpl service = new AgentRunAuditServiceImpl(runRepository, eventRepository,
                new ObjectMapper());
        AgentRunAuditContext context = new AgentRunAuditContext(100L, "request-1", "trace-1",
                8L, "luigi", "thread-1", 9L, "fingerprint-1", new java.util.concurrent.atomic.AtomicInteger(-1),
                new java.util.concurrent.atomic.AtomicInteger(0), java.util.Map.of());

        service.record(context, AgentRunEventRecord.builder(AgentRunEventType.MODEL_REQUEST).build());
        service.record(context, AgentRunEventRecord.builder(AgentRunEventType.TOOL_RESPONSE)
                .toolType(AgentRunToolType.LOCAL)
                .build());
        service.record(context, AgentRunEventRecord.builder(AgentRunEventType.TOOL_RESPONSE)
                .toolType(AgentRunToolType.MCP)
                .build());

        verify(runRepository).incrementModelCallCount(100L);
        verify(runRepository, org.mockito.Mockito.times(2)).incrementToolCallCount(100L);
        verify(runRepository).incrementMcpToolCallCount(100L);
    }

    @Test
    void debugLogIncludesCompleteEventPayload() {
        AgentRunAuditRepository runRepository = mock(AgentRunAuditRepository.class);
        AgentRunEventAuditRepository eventRepository = mock(AgentRunEventAuditRepository.class);
        when(eventRepository.save(any(AgentRunEventAuditPo.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AgentRunAuditServiceImpl service = new AgentRunAuditServiceImpl(runRepository, eventRepository,
                new ObjectMapper());
        AgentRunAuditContext context = new AgentRunAuditContext(100L, "request-1", "trace-1",
                8L, "luigi", "thread-1", 9L, "fingerprint-1", new java.util.concurrent.atomic.AtomicInteger(-1),
                new java.util.concurrent.atomic.AtomicInteger(0), java.util.Map.of());
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(AgentRunAuditServiceImpl.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        Level previousLevel = logger.getLevel();
        boolean previousAdditive = logger.isAdditive();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);
        logger.setAdditive(false);
        try {
            service.record(context, AgentRunEventRecord.builder(AgentRunEventType.MODEL_RESPONSE)
                    .promptText("prompt")
                    .requestMessagesJson("[{\"role\":\"user\"}]")
                    .requestOptionsJson("{\"temperature\":0.7}")
                    .availableToolsJson("[{\"name\":\"docs_search\"}]")
                    .responseText("model output")
                    .toolArguments("{\"query\":\"raw\"}")
                    .toolResult("tool output")
                    .metadataJson("{\"meta\":\"value\"}")
                    .errorCode("MODEL_ERROR")
                    .errorMessage("model failed")
                    .build());
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
            logger.setAdditive(previousAdditive);
            appender.stop();
        }

        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(message -> assertThat(message)
                        .contains("promptText")
                        .contains("requestMessagesJson")
                        .contains("requestOptionsJson")
                        .contains("availableToolsJson")
                        .contains("responseText")
                        .contains("toolArguments")
                        .contains("toolResult")
                        .contains("metadataJson")
                        .contains("errorCode")
                        .contains("errorMessage"));
    }

    private AgentRunAuditPo runPo() {
        AgentRunAuditPo run = new AgentRunAuditPo();
        run.setId(100L);
        run.setRequestId("request-1");
        run.setTraceId("trace-1");
        run.setThreadId("thread-1");
        run.setUserId(8L);
        run.setUsername("luigi");
        run.setPresetId(9L);
        run.setRuntimeFingerprint("fingerprint-1");
        run.setEffectiveConfigJson("{\"systemPrompt\":\"full prompt\"}");
        run.setUserMessage("用户明文输入");
        run.setFinalMessage("最终回答");
        run.setFinalThinking("思考内容");
        run.setStatus(AgentRunAuditStatus.SUCCESS);
        run.setModelCallCount(1);
        run.setToolCallCount(1);
        run.setMcpToolCallCount(0);
        run.setStartedAt(Instant.parse("2026-06-15T01:00:00Z"));
        run.setFinishedAt(Instant.parse("2026-06-15T01:00:04Z"));
        run.setDurationMs(4000L);
        run.setCreatedAt(Instant.parse("2026-06-15T01:00:00Z"));
        return run;
    }

    private RbacPrincipal principal(String roleCode) {
        return new RbacPrincipal(1L, "user", Set.of(roleCode), Set.of(), "v1");
    }
}
