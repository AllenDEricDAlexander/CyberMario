package top.egon.mario.agent.memory.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveUserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import top.egon.mario.agent.memory.po.AgentLongTermMemoryPo;
import top.egon.mario.agent.memory.po.AgentLongTermMemoryVersionPo;
import top.egon.mario.agent.memory.po.AgentMemoryExtractionAuditPo;
import top.egon.mario.agent.memory.po.AgentMemoryMessagePo;
import top.egon.mario.agent.memory.po.AgentMemorySessionPo;
import top.egon.mario.agent.memory.po.enums.AgentLongTermMemoryScopeType;
import top.egon.mario.agent.memory.po.enums.AgentLongTermMemoryStatus;
import top.egon.mario.agent.memory.po.enums.AgentMemoryEntryType;
import top.egon.mario.agent.memory.po.enums.AgentMemoryExtractionStatus;
import top.egon.mario.agent.memory.po.enums.AgentMemoryMessageRole;
import top.egon.mario.agent.memory.po.enums.AgentMemoryMessageType;
import top.egon.mario.agent.memory.po.enums.AgentMemorySessionStatus;
import top.egon.mario.agent.memory.service.AgentLongTermMemoryService;
import top.egon.mario.agent.memory.service.AgentMemoryExtractionService;
import top.egon.mario.agent.memory.service.AgentMemoryMessageService;
import top.egon.mario.agent.memory.service.AgentMemorySessionService;
import top.egon.mario.agent.web.AgentMemoryController;
import top.egon.mario.rbac.application.RbacAuthApplication;
import top.egon.mario.rbac.service.security.BrowserAuthCookieService;
import top.egon.mario.rbac.service.security.RbacApiRuleCache;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@WebFluxTest(controllers = AgentMemoryController.class,
        excludeAutoConfiguration = {ReactiveSecurityAutoConfiguration.class, ReactiveUserDetailsServiceAutoConfiguration.class})
class AgentMemoryControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private AgentMemorySessionService sessionService;
    @MockitoBean
    private AgentMemoryMessageService messageService;
    @MockitoBean
    private AgentLongTermMemoryService longTermMemoryService;
    @MockitoBean
    private AgentMemoryExtractionService extractionService;
    @MockitoBean
    private RbacAuthApplication rbacAuthApplication;
    @MockitoBean
    private RbacApiRuleCache rbacApiRuleCache;
    @MockitoBean
    private BrowserAuthCookieService browserAuthCookieService;
    @MockitoBean
    private Scheduler blockingScheduler;

    @Test
    void sessionsReturnCurrentUserPageAndDoNotAcceptUserId() {
        useImmediateScheduler();
        given(sessionService.page(eq(AgentMemoryEntryType.AGENT_CHAT), eq(null),
                any(), any())).willReturn(new PageImpl<>(List.of(session()), PageRequest.of(0, 20), 1));

        webTestClient.get()
                .uri("/api/agent/memory/sessions?entryType=AGENT_CHAT&userId=999&page=1&size=20")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.records[0].sessionId").isEqualTo("session-1")
                .jsonPath("$.data.records[0].entryType").isEqualTo("AGENT_CHAT");

        verify(sessionService).page(eq(AgentMemoryEntryType.AGENT_CHAT), eq(null),
                any(), any());
    }

    @Test
    void sessionLifecycleEndpointsDelegateToServices() {
        useImmediateScheduler();
        given(sessionService.create(any(), any())).willReturn(session());
        given(sessionService.update(eq("session-1"), any(), any())).willReturn(session());
        given(sessionService.release(eq("session-1"), any())).willReturn(session());
        given(sessionService.restore(eq("session-1"), any())).willReturn(session());
        given(sessionService.archive(eq("session-1"), any())).willReturn(session());

        webTestClient.post()
                .uri("/api/agent/memory/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"entryType":"AGENT_CHAT","title":"Chat","memoryEnabled":true}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.sessionId").isEqualTo("session-1");

        webTestClient.patch()
                .uri("/api/agent/memory/sessions/session-1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"title":"Chat v2","memoryEnabled":false}
                        """)
                .exchange()
                .expectStatus().isOk();

        webTestClient.post().uri("/api/agent/memory/sessions/session-1/release")
                .exchange().expectStatus().isOk();
        webTestClient.post().uri("/api/agent/memory/sessions/session-1/restore")
                .exchange().expectStatus().isOk();
        webTestClient.post().uri("/api/agent/memory/sessions/session-1/archive")
                .exchange().expectStatus().isOk();
        webTestClient.delete().uri("/api/agent/memory/sessions/session-1")
                .exchange().expectStatus().isOk();

        verify(sessionService).deleteArchived(eq("session-1"), any());
    }

    @Test
    void messagesLongTermVersionsAndExtractionsAreReadable() {
        useImmediateScheduler();
        given(messageService.messages(eq("session-1"), any())).willReturn(List.of(message()));
        given(longTermMemoryService.getOrCreateUserAgentMemory(any())).willReturn(memory());
        given(longTermMemoryService.userAgentVersions(any())).willReturn(List.of(version()));
        given(extractionService.userAudits(any())).willReturn(List.of(extraction()));

        webTestClient.get().uri("/api/agent/memory/sessions/session-1/messages")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].content").isEqualTo("hello");

        webTestClient.get().uri("/api/agent/memory/long-term")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.contentMarkdown").isEqualTo("# User Memory");

        webTestClient.get().uri("/api/agent/memory/long-term/versions")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].versionNo").isEqualTo(1);

        webTestClient.get().uri("/api/agent/memory/extractions")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].status").isEqualTo("SUCCESS");
    }

    private void useImmediateScheduler() {
        given(blockingScheduler.schedule(any())).willAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return (reactor.core.Disposable) () -> {
            };
        });
        given(blockingScheduler.createWorker()).willAnswer(invocation -> Schedulers.immediate().createWorker());
    }

    private AgentMemorySessionPo session() {
        Instant now = Instant.parse("2026-06-16T01:00:00Z");
        AgentMemorySessionPo session = new AgentMemorySessionPo();
        session.setSessionId("session-1");
        session.setEntryType(AgentMemoryEntryType.AGENT_CHAT);
        session.setTitle("Chat");
        session.setUserId(8L);
        session.setUsername("luigi");
        session.setStatus(AgentMemorySessionStatus.ACTIVE);
        session.setMemoryEnabled(true);
        session.setLongTermExtractionEnabled(true);
        session.setShortTermWindowTurns(10);
        session.setLastActiveAt(now);
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        return session;
    }

    private AgentMemoryMessagePo message() {
        AgentMemoryMessagePo message = new AgentMemoryMessagePo();
        message.setId(1L);
        message.setSessionId("session-1");
        message.setEntryType(AgentMemoryEntryType.AGENT_CHAT);
        message.setSeqNo(1);
        message.setTurnNo(1);
        message.setRole(AgentMemoryMessageRole.USER);
        message.setMessageType(AgentMemoryMessageType.MESSAGE);
        message.setContent("hello");
        message.setContentChars(5);
        message.setCreatedAt(Instant.parse("2026-06-16T01:00:00Z"));
        return message;
    }

    private AgentLongTermMemoryPo memory() {
        Instant now = Instant.parse("2026-06-16T01:00:00Z");
        AgentLongTermMemoryPo memory = new AgentLongTermMemoryPo();
        memory.setScopeType(AgentLongTermMemoryScopeType.USER_AGENT);
        memory.setContentMarkdown("# User Memory");
        memory.setContentChars(13);
        memory.setActiveVersionId(1L);
        memory.setStatus(AgentLongTermMemoryStatus.ACTIVE);
        memory.setCreatedAt(now);
        memory.setUpdatedAt(now);
        return memory;
    }

    private AgentLongTermMemoryVersionPo version() {
        AgentLongTermMemoryVersionPo version = new AgentLongTermMemoryVersionPo();
        version.setId(1L);
        version.setMemoryId(1L);
        version.setVersionNo(1);
        version.setContentMarkdown("# User Memory");
        version.setContentChars(13);
        version.setChangeSummary("create");
        version.setCreatedAt(Instant.parse("2026-06-16T01:00:00Z"));
        return version;
    }

    private AgentMemoryExtractionAuditPo extraction() {
        AgentMemoryExtractionAuditPo audit = new AgentMemoryExtractionAuditPo();
        audit.setId(1L);
        audit.setSessionId("session-1");
        audit.setEntryType(AgentMemoryEntryType.AGENT_CHAT);
        audit.setStatus(AgentMemoryExtractionStatus.SUCCESS);
        audit.setStartedAt(Instant.parse("2026-06-16T01:00:00Z"));
        audit.setFinishedAt(Instant.parse("2026-06-16T01:00:01Z"));
        audit.setCreatedAt(Instant.parse("2026-06-16T01:00:00Z"));
        return audit;
    }
}
