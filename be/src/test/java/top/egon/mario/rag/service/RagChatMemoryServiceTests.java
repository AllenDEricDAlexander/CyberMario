package top.egon.mario.rag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
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
import top.egon.mario.agent.memory.po.enums.AgentMemoryMessageStatus;
import top.egon.mario.agent.memory.po.enums.AgentMemoryMessageType;
import top.egon.mario.agent.memory.po.enums.AgentMemorySessionStatus;
import top.egon.mario.agent.memory.service.AgentMemoryContextService;
import top.egon.mario.agent.memory.service.AgentMemoryExtractionService;
import top.egon.mario.agent.memory.service.AgentMemoryMessageService;
import top.egon.mario.agent.memory.service.AgentMemorySessionService;
import top.egon.mario.agent.memory.service.model.AgentMemoryContext;
import top.egon.mario.agent.memory.service.model.AgentMemoryExtractionRequest;
import top.egon.mario.agent.memory.service.model.AgentMemoryMessageRecord;
import top.egon.mario.common.api.TraceContext;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

        InOrder memoryOrder = inOrder(memoryMessageService, retrievalService);
        memoryOrder.verify(memoryMessageService).appendAll(org.mockito.ArgumentMatchers.argThat(records ->
                records.size() == 1
                        && records.get(0).entryType() == AgentMemoryEntryType.RAG_CHAT
                        && records.get(0).turnNo() == 3
                        && records.get(0).role() == AgentMemoryMessageRole.USER
                        && records.get(0).messageType() == AgentMemoryMessageType.MESSAGE
                        && records.get(0).messageStatus() == AgentMemoryMessageStatus.SUCCEEDED
                        && records.get(0).content().equals("继续说")));
        memoryOrder.verify(retrievalService).searchSources(any(), any(), any(), any(), any(), any(), any(), any());
        memoryOrder.verify(memoryMessageService).appendAll(org.mockito.ArgumentMatchers.argThat(records ->
                records.size() == 1
                        && records.get(0).entryType() == AgentMemoryEntryType.RAG_CHAT
                        && records.get(0).turnNo() == 3
                        && records.get(0).role() == AgentMemoryMessageRole.ASSISTANT
                        && records.get(0).messageType() == AgentMemoryMessageType.MESSAGE
                        && records.get(0).messageStatus() == AgentMemoryMessageStatus.SUCCEEDED
                        && records.get(0).content().equals("回答")
                        && records.get(0).sourceRefsJson().contains("\"sourceId\":\"S1\"")));
        verify(memoryMessageService, times(2)).appendAll(any());
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
        InOrder memoryOrder = inOrder(memoryMessageService, retrievalService);
        memoryOrder.verify(memoryMessageService).appendAll(org.mockito.ArgumentMatchers.argThat(records ->
                records.size() == 1
                        && records.get(0).turnNo() == 1
                        && records.get(0).role() == AgentMemoryMessageRole.USER
                        && records.get(0).messageType() == AgentMemoryMessageType.MESSAGE
                        && records.get(0).messageStatus() == AgentMemoryMessageStatus.SUCCEEDED
                        && records.get(0).content().equals("没有来源的问题")));
        memoryOrder.verify(retrievalService).searchSources(any(), any(), any(), any(), any(), any(), any(), any());
        memoryOrder.verify(memoryMessageService).appendAll(org.mockito.ArgumentMatchers.argThat(records ->
                records.size() == 1
                        && records.get(0).turnNo() == 1
                        && records.get(0).role() == AgentMemoryMessageRole.ASSISTANT
                        && records.get(0).messageType() == AgentMemoryMessageType.MESSAGE
                        && records.get(0).messageStatus() == AgentMemoryMessageStatus.SUCCEEDED
                        && records.get(0).content().equals("知识库中没有找到明确依据。")
                        && "[]".equals(records.get(0).sourceRefsJson())));
        verify(memoryMessageService, times(2)).appendAll(any());
        verify(memoryExtractionService, never()).extractAfterTurn(any());
    }

    @Test
    void ragChatPersistsOnlyFinalCumulativeAssistantMessageWhileStreamingDeltas() {
        given(memorySessionService.resolveOrCreate(eq(AgentMemoryEntryType.RAG_CHAT), any(), eq(true), eq(false), any()))
                .willReturn(session("rag-session-3", true, false));
        given(memoryContextService.contextFor(any(), any()))
                .willReturn(new AgentMemoryContext("", ""));
        given(memoryMessageService.nextTurnNo("rag-session-3")).willReturn(5);
        given(retrievalService.searchSources(any(), any(), any(), any(), any(), any(), any(), any()))
                .willReturn(List.of(source("S1", "source content")));
        given(chatModel.stream(any(Prompt.class))).willReturn(Flux.just(
                response("你"),
                response("你好"),
                response("你好，Mario")));

        StepVerifier.create(service.stream(new RagChatRequest(
                        null, true, false, "继续说", List.of(1L), null, null, true), principal))
                .expectNextMatches(event -> "metadata".equals(event.type()))
                .expectNextMatches(event -> "retrieval".equals(event.type()))
                .expectNextMatches(event -> "delta".equals(event.type())
                        && "你".equals(event.data().get("content")))
                .expectNextMatches(event -> "delta".equals(event.type())
                        && "你好".equals(event.data().get("content")))
                .expectNextMatches(event -> "delta".equals(event.type())
                        && "你好，Mario".equals(event.data().get("content")))
                .expectNextMatches(event -> "done".equals(event.type()))
                .verifyComplete();

        InOrder memoryOrder = inOrder(memoryMessageService);
        memoryOrder.verify(memoryMessageService).appendAll(org.mockito.ArgumentMatchers.argThat(records ->
                records.size() == 1
                        && records.get(0).turnNo() == 5
                        && records.get(0).role() == AgentMemoryMessageRole.USER
                        && records.get(0).content().equals("继续说")));
        memoryOrder.verify(memoryMessageService).appendAll(org.mockito.ArgumentMatchers.argThat(records ->
                records.size() == 1
                        && records.get(0).turnNo() == 5
                        && records.get(0).role() == AgentMemoryMessageRole.ASSISTANT
                        && records.get(0).messageType() == AgentMemoryMessageType.MESSAGE
                        && records.get(0).messageStatus() == AgentMemoryMessageStatus.SUCCEEDED
                        && records.get(0).content().equals("你好，Mario")));
        verify(memoryMessageService, times(2)).appendAll(any());
        verify(memoryExtractionService, never()).extractAfterTurn(any());
    }

    @Test
    void ragChatStillEmitsDoneWhenLongTermExtractionFailsAfterAssistantPersistence() {
        given(memorySessionService.resolveOrCreate(eq(AgentMemoryEntryType.RAG_CHAT), any(), eq(true), eq(true), any()))
                .willReturn(session("rag-session-extract-fails", true, true));
        given(memoryContextService.contextFor(any(), any()))
                .willReturn(new AgentMemoryContext("", ""));
        given(memoryMessageService.nextTurnNo("rag-session-extract-fails")).willReturn(8);
        given(retrievalService.searchSources(any(), any(), any(), any(), any(), any(), any(), any()))
                .willReturn(List.of(source("S1", "source content")));
        given(chatModel.stream(any(Prompt.class))).willReturn(Flux.just(response("回答")));
        doThrow(new IllegalStateException("extract failed"))
                .when(memoryExtractionService).extractAfterTurn(any(AgentMemoryExtractionRequest.class));

        StepVerifier.create(service.stream(new RagChatRequest(
                        null, true, true, "需要抽取", List.of(1L), null, null, true), principal))
                .expectNextMatches(event -> "metadata".equals(event.type()))
                .expectNextMatches(event -> "retrieval".equals(event.type()))
                .expectNextMatches(event -> "delta".equals(event.type())
                        && "回答".equals(event.data().get("content")))
                .expectNextMatches(event -> "done".equals(event.type())
                        && "STOP".equals(event.data().get("finishReason")))
                .verifyComplete();

        InOrder memoryOrder = inOrder(memoryMessageService, retrievalService, chatModel, memoryExtractionService);
        memoryOrder.verify(memoryMessageService).appendAll(org.mockito.ArgumentMatchers.argThat(records ->
                records.size() == 1
                        && records.get(0).turnNo() == 8
                        && records.get(0).role() == AgentMemoryMessageRole.USER
                        && records.get(0).content().equals("需要抽取")));
        memoryOrder.verify(retrievalService).searchSources(any(), any(), any(), any(), any(), any(), any(), any());
        memoryOrder.verify(chatModel).stream(any(Prompt.class));
        memoryOrder.verify(memoryMessageService).appendAll(org.mockito.ArgumentMatchers.argThat(records ->
                records.size() == 1
                        && records.get(0).turnNo() == 8
                        && records.get(0).role() == AgentMemoryMessageRole.ASSISTANT
                        && records.get(0).messageType() == AgentMemoryMessageType.MESSAGE
                        && records.get(0).messageStatus() == AgentMemoryMessageStatus.SUCCEEDED
                        && records.get(0).content().equals("回答")));
        memoryOrder.verify(memoryExtractionService).extractAfterTurn(any(AgentMemoryExtractionRequest.class));
    }

    @Test
    void ragChatSourceRefsJsonAllowsNullableSourceFields() {
        given(memorySessionService.resolveOrCreate(eq(AgentMemoryEntryType.RAG_CHAT), any(), eq(true), eq(false), any()))
                .willReturn(session("rag-session-null-source", true, false));
        given(memoryContextService.contextFor(any(), any()))
                .willReturn(new AgentMemoryContext("", ""));
        given(memoryMessageService.nextTurnNo("rag-session-null-source")).willReturn(11);
        given(retrievalService.searchSources(any(), any(), any(), any(), any(), any(), any(), any()))
                .willReturn(List.of(nullableSource("nullable source content")));
        given(chatModel.stream(any(Prompt.class))).willReturn(Flux.just(response("回答")));

        StepVerifier.create(service.stream(new RagChatRequest(
                        null, true, false, "可空来源", List.of(1L), null, null, true), principal))
                .expectNextMatches(event -> "metadata".equals(event.type()))
                .expectNextMatches(event -> "retrieval".equals(event.type()))
                .expectNextMatches(event -> "delta".equals(event.type())
                        && "回答".equals(event.data().get("content")))
                .expectNextMatches(event -> "done".equals(event.type())
                        && "STOP".equals(event.data().get("finishReason")))
                .verifyComplete();

        verify(memoryMessageService).appendAll(org.mockito.ArgumentMatchers.argThat(records ->
                records.size() == 1
                        && records.get(0).role() == AgentMemoryMessageRole.ASSISTANT
                        && records.get(0).sourceRefsJson().contains("\"sourceId\":null")
                        && records.get(0).sourceRefsJson().contains("\"knowledgeBaseId\":null")
                        && records.get(0).sourceRefsJson().contains("\"documentId\":null")
                        && records.get(0).sourceRefsJson().contains("\"chunkId\":null")));
    }

    @Test
    void ragChatSkipsAssistantMemoryAndExtractionWhenModelCompletesWithBlankContent() {
        given(memorySessionService.resolveOrCreate(eq(AgentMemoryEntryType.RAG_CHAT), any(), eq(true), eq(true), any()))
                .willReturn(session("rag-session-blank", true, true));
        given(memoryContextService.contextFor(any(), any()))
                .willReturn(new AgentMemoryContext("", ""));
        given(memoryMessageService.nextTurnNo("rag-session-blank")).willReturn(6);
        given(retrievalService.searchSources(any(), any(), any(), any(), any(), any(), any(), any()))
                .willReturn(List.of(source("S1", "source content")));
        given(chatModel.stream(any(Prompt.class))).willReturn(Flux.just(response(" "), response("\n\t")));

        StepVerifier.create(service.stream(new RagChatRequest(
                        null, true, true, "空回答问题", List.of(1L), null, null, true), principal))
                .expectNextMatches(event -> "metadata".equals(event.type()))
                .expectNextMatches(event -> "retrieval".equals(event.type()))
                .expectNextMatches(event -> "done".equals(event.type())
                        && "STOP".equals(event.data().get("finishReason")))
                .verifyComplete();

        InOrder memoryOrder = inOrder(memoryMessageService, retrievalService, chatModel);
        memoryOrder.verify(memoryMessageService).appendAll(org.mockito.ArgumentMatchers.argThat(records ->
                records.size() == 1
                        && records.get(0).turnNo() == 6
                        && records.get(0).role() == AgentMemoryMessageRole.USER
                        && records.get(0).messageStatus() == AgentMemoryMessageStatus.SUCCEEDED
                        && records.get(0).content().equals("空回答问题")));
        memoryOrder.verify(retrievalService).searchSources(any(), any(), any(), any(), any(), any(), any(), any());
        memoryOrder.verify(chatModel).stream(any(Prompt.class));
        verify(memoryMessageService, times(1)).appendAll(any());
        verify(memoryExtractionService, never()).extractAfterTurn(any());
    }

    @Test
    void ragChatPersistsAssistantErrorAndEmitsErrorEventWhenModelStreamFails() {
        given(memorySessionService.resolveOrCreate(eq(AgentMemoryEntryType.RAG_CHAT), any(), eq(true), eq(true), any()))
                .willReturn(session("rag-session-4", true, true));
        given(memoryContextService.contextFor(any(), any()))
                .willReturn(new AgentMemoryContext("", ""));
        given(memoryMessageService.nextTurnNo("rag-session-4")).willReturn(7);
        given(retrievalService.searchSources(any(), any(), any(), any(), any(), any(), any(), any()))
                .willReturn(List.of(source("S1", "source content")));
        IllegalStateException failure = new IllegalStateException("provider down");
        given(chatModel.stream(any(Prompt.class))).willReturn(Flux.error(failure));

        StepVerifier.create(service.stream(new RagChatRequest(
                                null, true, true, "查一下", List.of(1L), null, null, true), principal)
                        .contextWrite(context -> context.put(TraceContext.CONTEXT_KEY, "trace-rag-model")))
                .expectNextMatches(event -> "metadata".equals(event.type()))
                .expectNextMatches(event -> "retrieval".equals(event.type()))
                .assertNext(event -> {
                    assertThat(event.type()).isEqualTo("error");
                    assertThat(event.data()).containsEntry("code", IllegalStateException.class.getName())
                            .containsEntry("message", "模型调用失败：provider down")
                            .containsEntry("traceId", "trace-rag-model")
                            .containsEntry("sessionId", "rag-session-4");
                })
                .verifyComplete();

        InOrder memoryOrder = inOrder(memoryMessageService);
        memoryOrder.verify(memoryMessageService).appendAll(org.mockito.ArgumentMatchers.argThat(records ->
                records.size() == 1
                        && records.get(0).turnNo() == 7
                        && records.get(0).role() == AgentMemoryMessageRole.USER
                        && records.get(0).messageStatus() == AgentMemoryMessageStatus.SUCCEEDED
                        && records.get(0).content().equals("查一下")));
        memoryOrder.verify(memoryMessageService).appendAll(org.mockito.ArgumentMatchers.argThat(records ->
                records.size() == 1
                        && records.get(0).turnNo() == 7
                        && records.get(0).role() == AgentMemoryMessageRole.ASSISTANT
                        && records.get(0).messageType() == AgentMemoryMessageType.ERROR
                        && records.get(0).messageStatus() == AgentMemoryMessageStatus.FAILED
                        && records.get(0).content().equals("模型调用失败：provider down")
                        && records.get(0).errorCode().equals(IllegalStateException.class.getName())
                        && records.get(0).errorMessage().equals("provider down")));
        verify(memoryExtractionService, never()).extractAfterTurn(any());
    }

    @Test
    void ragChatPersistsAssistantErrorAndCompletesWhenRetrievalFails() {
        given(memorySessionService.resolveOrCreate(eq(AgentMemoryEntryType.RAG_CHAT), any(), eq(true), eq(true), any()))
                .willReturn(session("rag-session-5", true, true));
        given(memoryContextService.contextFor(any(), any()))
                .willReturn(new AgentMemoryContext("", ""));
        given(memoryMessageService.nextTurnNo("rag-session-5")).willReturn(9);
        IllegalArgumentException failure = new IllegalArgumentException("retrieval down");
        given(retrievalService.searchSources(any(), any(), any(), any(), any(), any(), any(), any()))
                .willThrow(failure);

        StepVerifier.create(service.stream(new RagChatRequest(
                                null, true, true, "查知识库", List.of(1L), null, null, true), principal)
                        .contextWrite(context -> context.put(TraceContext.CONTEXT_KEY, "trace-rag-retrieval")))
                .expectNextMatches(event -> "metadata".equals(event.type())
                        && "rag-session-5".equals(event.data().get("sessionId")))
                .assertNext(event -> {
                    assertThat(event.type()).isEqualTo("error");
                    assertThat(event.data()).containsEntry("code", IllegalArgumentException.class.getName())
                            .containsEntry("message", "模型调用失败：retrieval down")
                            .containsEntry("traceId", "trace-rag-retrieval")
                            .containsEntry("sessionId", "rag-session-5");
                })
                .verifyComplete();

        InOrder memoryOrder = inOrder(memoryMessageService, retrievalService);
        memoryOrder.verify(memoryMessageService).appendAll(org.mockito.ArgumentMatchers.argThat(records ->
                records.size() == 1
                        && records.get(0).turnNo() == 9
                        && records.get(0).role() == AgentMemoryMessageRole.USER
                        && records.get(0).messageStatus() == AgentMemoryMessageStatus.SUCCEEDED
                        && records.get(0).content().equals("查知识库")));
        memoryOrder.verify(retrievalService).searchSources(any(), any(), any(), any(), any(), any(), any(), any());
        memoryOrder.verify(memoryMessageService).appendAll(org.mockito.ArgumentMatchers.argThat(records ->
                records.size() == 1
                        && records.get(0).turnNo() == 9
                        && records.get(0).role() == AgentMemoryMessageRole.ASSISTANT
                        && records.get(0).messageType() == AgentMemoryMessageType.ERROR
                        && records.get(0).messageStatus() == AgentMemoryMessageStatus.FAILED
                        && records.get(0).content().equals("模型调用失败：retrieval down")
                        && records.get(0).errorCode().equals(IllegalArgumentException.class.getName())
                        && records.get(0).errorMessage().equals("retrieval down")));
        verify(chatModel, never()).stream(any(Prompt.class));
        verify(memoryExtractionService, never()).extractAfterTurn(any());
    }

    private SourceReferenceResponse source(String sourceId, String content) {
        return new SourceReferenceResponse(sourceId, 1L, "KB", 2L, "doc.md",
                3L, 0, 0.9D, content, Map.of("matched_by", "VECTOR"));
    }

    private SourceReferenceResponse nullableSource(String content) {
        return new SourceReferenceResponse(null, null, null, null, null,
                null, 0, 0.9D, content, Map.of());
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
