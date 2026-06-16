package top.egon.mario.rag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import top.egon.mario.agent.memory.po.AgentMemorySessionPo;
import top.egon.mario.agent.memory.po.enums.AgentMemoryEntryType;
import top.egon.mario.agent.memory.po.enums.AgentMemoryMessageRole;
import top.egon.mario.agent.memory.po.enums.AgentMemoryMessageType;
import top.egon.mario.agent.memory.po.enums.AgentMemorySessionStatus;
import top.egon.mario.agent.memory.service.AgentMemoryContextService;
import top.egon.mario.agent.memory.service.AgentMemoryExtractionService;
import top.egon.mario.agent.memory.service.AgentMemoryMessageService;
import top.egon.mario.agent.memory.service.AgentMemorySessionService;
import top.egon.mario.agent.memory.service.model.AgentMemoryContext;
import top.egon.mario.agent.memory.service.model.AgentMemoryExtractionRequest;
import top.egon.mario.agent.memory.service.model.AgentMemoryMessageRecord;
import top.egon.mario.rag.config.RagProperties;
import top.egon.mario.rag.dto.request.RagChatRequest;
import top.egon.mario.rag.dto.response.SourceReferenceResponse;
import top.egon.mario.rag.service.impl.RagChatServiceImpl;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RagChatMemoryServiceTests {

    private final RagRetrievalService retrievalService = mock(RagRetrievalService.class);
    private final org.springframework.ai.chat.model.ChatModel chatModel = mock(org.springframework.ai.chat.model.ChatModel.class);
    private final AgentMemorySessionService memorySessionService = mock(AgentMemorySessionService.class);
    private final AgentMemoryMessageService memoryMessageService = mock(AgentMemoryMessageService.class);
    private final AgentMemoryContextService memoryContextService = mock(AgentMemoryContextService.class);
    private final AgentMemoryExtractionService memoryExtractionService = mock(AgentMemoryExtractionService.class);
    private final RagChatServiceImpl service = new RagChatServiceImpl(new RagProperties(null, null, null),
            retrievalService, chatModel, new ObjectMapper(), memorySessionService, memoryMessageService,
            memoryContextService, memoryExtractionService);
    private final RbacPrincipal principal = new RbacPrincipal(8L, "luigi", Set.of("RAG_USER"), Set.of(), "v1");

    @Test
    void ragChatAddsOnlySessionShortTermMemoryBeforeSources() {
        given(memorySessionService.resolveOrCreate(eq(AgentMemoryEntryType.RAG_CHAT), any(), eq(true), eq(true), any()))
                .willReturn(session("rag-session-1", true, true));
        given(memoryContextService.contextFor(any(), any()))
                .willReturn(new AgentMemoryContext("用户: 上一个问题\n助手: 上一个回答", ""));
        given(memoryMessageService.nextTurnNo("rag-session-1")).willReturn(3);
        given(retrievalService.searchSources(any(), any(), any(), any(), any(), any(), any(), any()))
                .willReturn(List.of(source("S1", "source content")));
        given(chatModel.stream(any(Prompt.class))).willReturn(Flux.just(response("回答")));

        StepVerifier.create(service.stream(new RagChatRequest(
                        null, true, true, "继续说", List.of(1L), null, null, true), principal))
                .expectNextMatches(event -> "metadata".equals(event.type())
                        && "rag-session-1".equals(event.data().get("sessionId"))
                        && Boolean.TRUE.equals(event.data().get("memoryEnabled"))
                        && Boolean.TRUE.equals(event.data().get("longTermExtractionEnabled")))
                .expectNextMatches(event -> "retrieval".equals(event.type()))
                .expectNextMatches(event -> "delta".equals(event.type())
                        && "回答".equals(event.data().get("content")))
                .expectNextMatches(event -> "done".equals(event.type()))
                .verifyComplete();

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).stream(promptCaptor.capture());
        String userPrompt = promptCaptor.getValue().getInstructions().get(1).getText();
        assertThat(userPrompt).contains("当前 RAG 会话最近对话：\n用户: 上一个问题\n助手: 上一个回答");
        assertThat(userPrompt).contains("知识库上下文：\n[来源 S1] source content");
        assertThat(userPrompt.indexOf("当前 RAG 会话最近对话："))
                .isLessThan(userPrompt.indexOf("知识库上下文："));

        verify(memoryMessageService).appendAll(org.mockito.ArgumentMatchers.argThat(records ->
                records.size() == 2
                        && records.stream().allMatch(record -> record.entryType() == AgentMemoryEntryType.RAG_CHAT)
                        && records.get(0).role() == AgentMemoryMessageRole.USER
                        && records.get(0).messageType() == AgentMemoryMessageType.MESSAGE
                        && records.get(0).content().equals("继续说")
                        && records.get(1).role() == AgentMemoryMessageRole.ASSISTANT
                        && records.get(1).content().equals("回答")
                        && records.get(1).sourceRefsJson().contains("\"sourceId\":\"S1\"")));
        verify(memoryExtractionService).extractAfterTurn(any(AgentMemoryExtractionRequest.class));
    }

    @Test
    void ragChatPersistsNoContextAnswerWithoutUsingMemoryAsKnowledge() {
        given(memorySessionService.resolveOrCreate(eq(AgentMemoryEntryType.RAG_CHAT), eq("rag-session-2"), eq(false), eq(false), any()))
                .willReturn(session("rag-session-2", false, false));
        given(memoryContextService.contextFor(any(), any()))
                .willReturn(new AgentMemoryContext("用户: 历史事实", ""));
        given(memoryMessageService.nextTurnNo("rag-session-2")).willReturn(1);
        given(retrievalService.searchSources(any(), any(), any(), any(), any(), any(), any(), any()))
                .willReturn(List.of());

        StepVerifier.create(service.stream(new RagChatRequest(
                        "rag-session-2", false, false, "没有来源的问题", null, null, null, true), principal))
                .expectNextMatches(event -> "metadata".equals(event.type())
                        && "rag-session-2".equals(event.data().get("sessionId"))
                        && Boolean.FALSE.equals(event.data().get("memoryEnabled")))
                .expectNextMatches(event -> "retrieval".equals(event.type()))
                .expectNextMatches(event -> "delta".equals(event.type())
                        && "知识库中没有找到明确依据。".equals(event.data().get("content")))
                .expectNextMatches(event -> "done".equals(event.type())
                        && "NO_CONTEXT".equals(event.data().get("finishReason")))
                .verifyComplete();

        verify(chatModel, never()).stream(any(Prompt.class));
        verify(memoryMessageService).appendAll(org.mockito.ArgumentMatchers.argThat(records ->
                records.size() == 2
                        && records.get(1).content().equals("知识库中没有找到明确依据。")
                        && "[]".equals(records.get(1).sourceRefsJson())));
        verify(memoryExtractionService, never()).extractAfterTurn(any());
    }

    private SourceReferenceResponse source(String sourceId, String content) {
        return new SourceReferenceResponse(sourceId, 1L, "KB", 2L, "doc.md",
                3L, 0, 0.9D, content, Map.of("matched_by", "VECTOR"));
    }

    private ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private AgentMemorySessionPo session(String sessionId, boolean memoryEnabled, boolean extractionEnabled) {
        AgentMemorySessionPo session = new AgentMemorySessionPo();
        session.setSessionId(sessionId);
        session.setEntryType(AgentMemoryEntryType.RAG_CHAT);
        session.setUserId(8L);
        session.setUsername("luigi");
        session.setStatus(AgentMemorySessionStatus.ACTIVE);
        session.setMemoryEnabled(memoryEnabled);
        session.setLongTermExtractionEnabled(extractionEnabled);
        session.setShortTermWindowTurns(10);
        return session;
    }
}
