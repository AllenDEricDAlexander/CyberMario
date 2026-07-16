package top.egon.mario.im;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.HandshakeInfo;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;
import top.egon.mario.im.facade.ImFacade;
import top.egon.mario.im.facade.dto.command.MarkReadCommand;
import top.egon.mario.im.facade.dto.command.MintWsTicketCommand;
import top.egon.mario.im.facade.dto.command.SendMessageCommand;
import top.egon.mario.im.facade.dto.view.MessageView;
import top.egon.mario.im.facade.dto.view.UnreadView;
import top.egon.mario.im.facade.dto.view.WsTicketView;
import top.egon.mario.im.po.ImWsTicketPo;
import top.egon.mario.im.po.enums.ImWsTicketStatus;
import top.egon.mario.im.policy.ImPrincipal;
import top.egon.mario.im.platform.PlatformImFacade;
import top.egon.mario.im.realtime.ImConnectionRegistry;
import top.egon.mario.im.realtime.ImFrame;
import top.egon.mario.im.repository.ImWsTicketRepository;
import top.egon.mario.im.service.ImException;
import top.egon.mario.im.service.ImTicketService;
import top.egon.mario.im.web.ImController;
import top.egon.mario.im.web.ImWebSocketHandler;
import top.egon.mario.rbac.application.RbacAuthApplication;
import top.egon.mario.rbac.service.resource.annotation.RbacApi;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.ai.dashscope.api-key=test-api-key",
        "mario.rbac.resource-sync.enabled=true"
})
@AutoConfigureWebTestClient
class ImWebSocketGatewayTests {

    @Autowired
    private ImTicketService ticketService;

    @Autowired
    private ImWsTicketRepository ticketRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private RbacAuthApplication authApplication;

    @BeforeEach
    void setUp() {
        ticketRepository.deleteAllInBatch();
    }

    @Test
    void mintTicketStoresOnlyHashAndConsumeMarksTicketConsumed() {
        ImPrincipal principal = principal(11001L);

        WsTicketView ticket = ticketService.mint(new MintWsTicketCommand(principal, 9001L));

        ImWsTicketPo row = onlyTicketFor(11001L);
        assertThat(ticket.ticket()).isNotBlank();
        assertThat(ticket.expiresAt()).isAfter(Instant.now());
        assertThat(row.getTokenHash()).isNotEqualTo(ticket.ticket());
        assertThat(row.getTokenHash()).doesNotContain(ticket.ticket());
        assertThat(row.getRolesJson()).contains("im-user", "IM_TEST", "roomId");
        assertThat(row.getMetadataJson()).isEqualTo("{}");

        ImPrincipal consumed = ticketService.consume(ticket.ticket());

        assertThat(consumed.userId()).isEqualTo(principal.userId());
        assertThat(consumed.roleCodes()).containsExactlyInAnyOrderElementsOf(principal.roleCodes());
        assertThat(consumed.contextType()).isEqualTo(principal.contextType());
        assertThat(consumed.attributes()).containsEntry("roomId", "42");
        assertThat(ticketRepository.findById(row.getId())).get()
                .satisfies(consumedRow -> {
                    assertThat(consumedRow.getStatus()).isEqualTo(ImWsTicketStatus.CONSUMED);
                    assertThat(consumedRow.getConsumedAt()).isNotNull();
                });
        assertThatThrownBy(() -> ticketService.consume(ticket.ticket()))
                .isInstanceOf(ImException.class)
                .extracting("code")
                .isEqualTo("IM_WS_TICKET_CONSUMED");
    }

    @Test
    void expiredAndUnknownTicketsAreRejected() {
        ImPrincipal principal = principal(11002L);
        WsTicketView ticket = ticketService.mint(new MintWsTicketCommand(principal, null));
        ImWsTicketPo row = onlyTicketFor(11002L);
        row.setExpiresAt(Instant.now().minusSeconds(1));
        ticketRepository.saveAndFlush(row);

        assertThatThrownBy(() -> ticketService.consume(ticket.ticket()))
                .isInstanceOf(ImException.class)
                .extracting("code")
                .isEqualTo("IM_WS_TICKET_EXPIRED");
        assertThat(ticketRepository.findById(row.getId())).get()
                .extracting(ImWsTicketPo::getStatus)
                .isEqualTo(ImWsTicketStatus.EXPIRED);
        assertThatThrownBy(() -> ticketService.consume("unknown-ticket"))
                .isInstanceOf(ImException.class)
                .extracting("code")
                .isEqualTo("IM_WS_TICKET_INVALID");
    }

    @Test
    void ticketMintEndpointDoesNotDeclareFineGrainedRbacApi() throws Exception {
        Method method = ImController.class.getMethod(
                "mintWsTicket", RbacPrincipal.class, ImController.MintWsTicketRequest.class);

        assertThat(method.getAnnotation(RbacApi.class)).isNull();
    }

    @Test
    void ticketMintEndpointIsReachableForCallerWithCoarseWriteAuthority() {
        when(authApplication.authenticateAccessToken("ticket-access"))
                .thenReturn(new UsernamePasswordAuthenticationToken(
                        new RbacPrincipal(13001L, "mario", Set.of("im-user"), Set.of("api:im:write"), "permission-v1"),
                        "ticket-access",
                        List.of(new SimpleGrantedAuthority("api:im:write"))
                ));

        webTestClient.post()
                .uri("/api/im/ws-ticket")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ticket-access")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "conversationId": 9701
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo("0")
                .jsonPath("$.data.ticket").isNotEmpty()
                .jsonPath("$.data.expiresAt").isNotEmpty();

        assertThat(onlyTicketFor(13001L).getTokenHash()).isNotBlank();
    }

    @Test
    void websocketHandlesFramesAndTreatsDeprecatedSubscribeAsNoOp() throws Exception {
        ImTicketService tickets = mock(ImTicketService.class);
        ImFacade facade = mock(ImFacade.class);
        PlatformImFacade platformFacade = mock(PlatformImFacade.class);
        ImConnectionRegistry registry = new ImConnectionRegistry();
        ImPrincipal principal = principal(12001L);
        MessageView message = new MessageView(
                8801L, 7701L, 12001L, 1L, "client-1", "TEXT", "hello", "{}",
                "VISIBLE", Instant.parse("2026-06-28T01:00:00Z"), null, null, "{}");
        UnreadView unread = new UnreadView(7701L, 12001L, 1L, 0L);
        when(tickets.consume("valid-ticket")).thenReturn(principal);
        when(platformFacade.send(argThat(command -> command != null && command.conversationId().equals(7701L))))
                .thenReturn(message);
        when(facade.markRead(argThat(command -> command != null && command.conversationId().equals(7701L))))
                .thenReturn(unread);
        ImWebSocketHandler handler = new ImWebSocketHandler(
                tickets, facade, platformFacade, registry, objectMapper, Schedulers.immediate());
        TestWebSocketSession session = TestWebSocketSession.withMessages(
                "ws://localhost/ws/im?ticket=valid-ticket",
                frame("PING", "r-ping", Map.of()),
                frame("SUBSCRIBE", "r-sub", Map.of("conversationId", 7701L, "lastSeq", 0L)),
                frame("SEND_MESSAGE", "r-send", Map.of(
                        "conversationId", 7701L,
                        "clientMsgId", "client-1",
                        "messageType", "TEXT",
                        "content", "hello",
                        "payloadJson", "{}",
                        "metadataJson", "{}"
                )),
                frame("MARK_READ", "r-read", Map.of("conversationId", 7701L, "messageSeq", 1L))
        );

        handler.handle(session).block(Duration.ofSeconds(3));

        List<JsonNode> frames = session.sentJson(objectMapper);
        assertThat(frames).extracting(frame -> frame.get("type").asText())
                .containsExactly("PONG", "SEND_ACK", "READ_UPDATED");
        assertThat(frames.get(0).get("requestId").asText()).isEqualTo("r-ping");
        assertThat(frames.get(1).get("payload").get("message").get("id").asLong()).isEqualTo(8801L);
        assertThat(frames.get(2).get("payload").get("unread").get("lastReadSeq").asLong()).isEqualTo(1L);
        verify(platformFacade).send(argThat((SendMessageCommand command) ->
                command.principal().equals(principal)
                        && command.conversationId().equals(7701L)
                        && command.clientMsgId().equals("client-1")
                        && command.content().equals("hello")));
        verify(facade).markRead(argThat((MarkReadCommand command) ->
                command.principal().equals(principal)
                        && command.conversationId().equals(7701L)
                        && command.messageSeq().equals(1L)));
    }

    @Test
    void websocketRejectsInvalidTicketBeforeRegisteringConnection() {
        ImTicketService tickets = mock(ImTicketService.class);
        ImFacade facade = mock(ImFacade.class);
        PlatformImFacade platformFacade = mock(PlatformImFacade.class);
        ImConnectionRegistry registry = new ImConnectionRegistry();
        when(tickets.consume("bad-ticket")).thenThrow(new ImException("IM_WS_TICKET_INVALID"));
        ImWebSocketHandler handler = new ImWebSocketHandler(
                tickets, facade, platformFacade, registry, objectMapper, Schedulers.immediate());
        TestWebSocketSession session = TestWebSocketSession.withMessages("ws://localhost/ws/im?ticket=bad-ticket");

        handler.handle(session).block(Duration.ofSeconds(3));

        assertThat(session.closedWith()).isEqualTo(CloseStatus.POLICY_VIOLATION);
        assertThat(registry.deliverToUser(12001L, Map.of("eventType", "MESSAGE_CREATED"))).isZero();
    }

    @Test
    void websocketSerializesLocalRealtimePushesAsServerFrames() throws Exception {
        ImTicketService tickets = mock(ImTicketService.class);
        ImFacade facade = mock(ImFacade.class);
        PlatformImFacade platformFacade = mock(PlatformImFacade.class);
        ImConnectionRegistry registry = new ImConnectionRegistry();
        when(tickets.consume("push-ticket")).thenReturn(principal(12002L));
        ImWebSocketHandler handler = new ImWebSocketHandler(
                tickets, facade, platformFacade, registry, objectMapper, Schedulers.immediate());
        TestWebSocketSession session = TestWebSocketSession.streaming("ws://localhost/ws/im?ticket=push-ticket");

        Disposable subscription = handler.handle(session).subscribe();
        try {
            assertThat(deliverWhenRegistered(registry, 12002L, Map.of(
                    "eventType", "MESSAGE_CREATED",
                    "conversationId", 7702L,
                    "messageSeq", 9L
            ))).isEqualTo(1);
            assertThat(session.awaitSentCount(1)).isTrue();

            JsonNode pushed = session.sentJson(objectMapper).getFirst();
            assertThat(pushed.get("type").asText()).isEqualTo("MESSAGE_PUSH");
            assertThat(pushed.get("payload").get("conversationId").asLong()).isEqualTo(7702L);
            assertThat(pushed.get("payload").get("messageSeq").asLong()).isEqualTo(9L);
        } finally {
            session.completeReceive();
            subscription.dispose();
        }
    }

    @Test
    void outboundOverflowDropsBufferedFramesAndEmitsResync() throws Exception {
        ImTicketService tickets = mock(ImTicketService.class);
        ImFacade facade = mock(ImFacade.class);
        ImConnectionRegistry registry = new ImConnectionRegistry();
        when(tickets.consume("overflow-ticket")).thenReturn(principal(12003L));
        ImWebSocketHandler handler = websocketHandler(tickets, facade, registry, 1);
        TestWebSocketSession session = TestWebSocketSession.deferredSend("ws://localhost/ws/im?ticket=overflow-ticket");

        Disposable subscription = handler.handle(session).subscribe();
        try {
            assertThat(deliverWhenRegistered(registry, 12003L, Map.of(
                    "eventType", "MESSAGE_CREATED",
                    "conversationId", 7703L,
                    "messageSeq", 1L
            ))).isEqualTo(1);
            assertThat(registry.deliverToUser(12003L, Map.of(
                    "eventType", "MESSAGE_CREATED",
                    "conversationId", 7703L,
                    "messageSeq", 2L
            ))).isEqualTo(1);

            JsonNode resync = session.deferredSentJson(objectMapper, 1).getFirst();
            assertThat(resync.get("type").asText()).isEqualTo("RESYNC");
            assertThat(resync.get("payload").get("reason").asText()).isEqualTo("OUTBOUND_OVERFLOW");
            assertThat(resync.get("payload").get("conversationId").asLong()).isEqualTo(7703L);
            assertThat(resync.get("payload").get("messageSeq").asLong()).isEqualTo(2L);
        } finally {
            session.completeReceive();
            subscription.dispose();
        }
    }

    @Test
    void clientResponseOverflowDropsBufferedFramesAndEmitsResync() throws Exception {
        ImTicketService tickets = mock(ImTicketService.class);
        ImFacade facade = mock(ImFacade.class);
        ImConnectionRegistry registry = new ImConnectionRegistry();
        when(tickets.consume("client-overflow-ticket")).thenReturn(principal(12004L));
        ImWebSocketHandler handler = websocketHandler(tickets, facade, registry, 1);
        TestWebSocketSession session = TestWebSocketSession.deferredSend("ws://localhost/ws/im?ticket=client-overflow-ticket");

        Disposable subscription = handler.handle(session).subscribe();
        try {
            assertThat(session.awaitDeferredSend()).isTrue();

            session.emitReceive(frame("PING", "r-ping-1", Map.of()));
            session.emitReceive(frame("PING", "r-ping-2", Map.of()));

            JsonNode resync = session.deferredSentJson(objectMapper, 1).getFirst();
            assertThat(resync.get("type").asText()).isEqualTo("RESYNC");
            assertThat(resync.get("requestId").asText()).isEqualTo("r-ping-2");
            assertThat(resync.get("payload").get("reason").asText()).isEqualTo("OUTBOUND_OVERFLOW");
        } finally {
            session.completeReceive();
            subscription.dispose();
        }
    }

    @Test
    void malformedAuthenticatedFrameReturnsResyncWithoutServerErrorClose() {
        ImTicketService tickets = mock(ImTicketService.class);
        ImFacade facade = mock(ImFacade.class);
        PlatformImFacade platformFacade = mock(PlatformImFacade.class);
        ImConnectionRegistry registry = new ImConnectionRegistry();
        when(tickets.consume("malformed-ticket")).thenReturn(principal(12005L));
        ImWebSocketHandler handler = new ImWebSocketHandler(
                tickets, facade, platformFacade, registry, objectMapper, Schedulers.immediate());
        TestWebSocketSession session = TestWebSocketSession.withMessages(
                "ws://localhost/ws/im?ticket=malformed-ticket",
                "{not-json"
        );

        handler.handle(session).block(Duration.ofSeconds(3));

        JsonNode resync = session.sentJson(objectMapper).getFirst();
        assertThat(resync.get("type").asText()).isEqualTo("RESYNC");
        assertThat(resync.get("payload").get("reason").asText()).isEqualTo("IM_WS_FRAME_INVALID");
        assertThat(session.closedWith()).isNull();
    }

    @Test
    void nullAuthenticatedFrameReturnsResyncWithoutServerErrorClose() {
        ImTicketService tickets = mock(ImTicketService.class);
        ImFacade facade = mock(ImFacade.class);
        PlatformImFacade platformFacade = mock(PlatformImFacade.class);
        ImConnectionRegistry registry = new ImConnectionRegistry();
        when(tickets.consume("null-frame-ticket")).thenReturn(principal(12007L));
        ImWebSocketHandler handler = new ImWebSocketHandler(
                tickets, facade, platformFacade, registry, objectMapper, Schedulers.immediate());
        TestWebSocketSession session = TestWebSocketSession.withMessages(
                "ws://localhost/ws/im?ticket=null-frame-ticket",
                "null"
        );

        handler.handle(session).block(Duration.ofSeconds(3));

        JsonNode resync = session.sentJson(objectMapper).getFirst();
        assertThat(resync.get("type").asText()).isEqualTo("RESYNC");
        assertThat(resync.get("payload").get("reason").asText()).isEqualTo("IM_WS_FRAME_INVALID");
        assertThat(session.closedWith()).isNull();
    }

    @Test
    void facadeValidationErrorReturnsResyncWithoutServerErrorClose() throws Exception {
        ImTicketService tickets = mock(ImTicketService.class);
        ImFacade facade = mock(ImFacade.class);
        PlatformImFacade platformFacade = mock(PlatformImFacade.class);
        ImConnectionRegistry registry = new ImConnectionRegistry();
        when(tickets.consume("validation-ticket")).thenReturn(principal(12006L));
        when(platformFacade.send(argThat(command -> command != null && command.conversationId().equals(7706L))))
                .thenThrow(new ImException("IM_MESSAGE_CONVERSATION_DENIED"));
        ImWebSocketHandler handler = new ImWebSocketHandler(
                tickets, facade, platformFacade, registry, objectMapper, Schedulers.immediate());
        TestWebSocketSession session = TestWebSocketSession.withMessages(
                "ws://localhost/ws/im?ticket=validation-ticket",
                frame("SEND_MESSAGE", "r-validation", Map.of(
                        "conversationId", 7706L,
                        "clientMsgId", "client-validation",
                        "messageType", "TEXT",
                        "content", "hello",
                        "payloadJson", "{}",
                        "metadataJson", "{}"
                ))
        );

        handler.handle(session).block(Duration.ofSeconds(3));

        JsonNode resync = session.sentJson(objectMapper).getFirst();
        assertThat(resync.get("type").asText()).isEqualTo("RESYNC");
        assertThat(resync.get("requestId").asText()).isEqualTo("r-validation");
        assertThat(resync.get("payload").get("reason").asText()).isEqualTo("IM_MESSAGE_CONVERSATION_DENIED");
        assertThat(session.closedWith()).isNull();
    }

    private ImWsTicketPo onlyTicketFor(Long userId) {
        return ticketRepository.findAll().stream()
                .filter(ticket -> userId.equals(ticket.getUserId()))
                .max(Comparator.comparing(ImWsTicketPo::getId))
                .orElseThrow();
    }

    private ImPrincipal principal(Long userId) {
        return new ImPrincipal(userId, Set.of("im-user", "ops"), "IM_TEST", Map.of("roomId", "42"));
    }

    private String frame(String type, String requestId, Map<String, Object> payload) throws JsonProcessingException {
        return objectMapper.writeValueAsString(new ImFrame(type, requestId, payload));
    }

    private ImWebSocketHandler websocketHandler(ImTicketService ticketService, ImFacade facade,
                                                ImConnectionRegistry registry, int outboundCapacity)
            throws ReflectiveOperationException {
        PlatformImFacade platformFacade = mock(PlatformImFacade.class);
        Constructor<ImWebSocketHandler> constructor = ImWebSocketHandler.class.getDeclaredConstructor(
                ImTicketService.class,
                ImFacade.class,
                PlatformImFacade.class,
                ImConnectionRegistry.class,
                ObjectMapper.class,
                reactor.core.scheduler.Scheduler.class,
                int.class
        );
        constructor.setAccessible(true);
        return constructor.newInstance(ticketService, facade, platformFacade, registry, objectMapper,
                Schedulers.immediate(), outboundCapacity);
    }

    private int deliverWhenRegistered(ImConnectionRegistry registry, Long userId, Map<String, Object> frame)
            throws InterruptedException {
        int delivered = 0;
        for (int i = 0; i < 50 && delivered == 0; i++) {
            delivered = registry.deliverToUser(userId, frame);
            if (delivered == 0) {
                TimeUnit.MILLISECONDS.sleep(10);
            }
        }
        return delivered;
    }

    private static final class TestWebSocketSession implements WebSocketSession {

        private final String id;
        private final HandshakeInfo handshakeInfo;
        private final DataBufferFactory bufferFactory = new DefaultDataBufferFactory();
        private final List<WebSocketMessage> inboundMessages;
        private final Sinks.Many<WebSocketMessage> inboundSink;
        private final List<WebSocketMessage> sentMessages = new CopyOnWriteArrayList<>();
        private final Map<String, Object> attributes = new ConcurrentHashMap<>();
        private final CountDownLatch sentLatch = new CountDownLatch(1);
        private final CountDownLatch deferredSendLatch = new CountDownLatch(1);
        private final boolean deferredSend;
        private volatile Publisher<WebSocketMessage> deferredMessages;
        private volatile boolean open = true;
        private volatile CloseStatus closeStatus;

        private TestWebSocketSession(String uri, List<String> inboundPayloads, boolean streaming) {
            this(uri, inboundPayloads, streaming, false);
        }

        private TestWebSocketSession(String uri, List<String> inboundPayloads, boolean streaming,
                                     boolean deferredSend) {
            this.id = "test-session";
            this.handshakeInfo = new HandshakeInfo(URI.create(uri), HttpHeaders.EMPTY, Mono.<Principal>empty(), null);
            this.inboundMessages = streaming ? List.of() : inboundPayloads.stream()
                    .map(this::textMessage)
                    .toList();
            this.inboundSink = streaming ? Sinks.many().unicast().onBackpressureBuffer() : null;
            this.deferredSend = deferredSend;
        }

        static TestWebSocketSession withMessages(String uri, String... inboundPayloads) {
            return new TestWebSocketSession(uri, List.of(inboundPayloads), false);
        }

        static TestWebSocketSession streaming(String uri) {
            return new TestWebSocketSession(uri, List.of(), true);
        }

        static TestWebSocketSession deferredSend(String uri) {
            return new TestWebSocketSession(uri, List.of(), true, true);
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public HandshakeInfo getHandshakeInfo() {
            return handshakeInfo;
        }

        @Override
        public DataBufferFactory bufferFactory() {
            return bufferFactory;
        }

        @Override
        public Map<String, Object> getAttributes() {
            return attributes;
        }

        @Override
        public Flux<WebSocketMessage> receive() {
            if (inboundSink != null) {
                return inboundSink.asFlux();
            }
            return Flux.fromIterable(inboundMessages);
        }

        @Override
        public Mono<Void> send(Publisher<WebSocketMessage> messages) {
            if (deferredSend) {
                this.deferredMessages = messages;
                deferredSendLatch.countDown();
                return Mono.never();
            }
            return Flux.from(messages)
                    .doOnNext(message -> {
                        sentMessages.add(message);
                        sentLatch.countDown();
                    })
                    .then();
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public Mono<Void> close(CloseStatus status) {
            this.closeStatus = status;
            this.open = false;
            completeReceive();
            return Mono.empty();
        }

        @Override
        public Mono<CloseStatus> closeStatus() {
            return closeStatus == null ? Mono.empty() : Mono.just(closeStatus);
        }

        void emitReceive(String payload) {
            if (inboundSink == null) {
                throw new IllegalStateException("session is not streaming");
            }
            inboundSink.tryEmitNext(textMessage(payload));
        }

        @Override
        public WebSocketMessage textMessage(String payload) {
            return new WebSocketMessage(
                    WebSocketMessage.Type.TEXT,
                    bufferFactory.wrap(payload.getBytes(StandardCharsets.UTF_8))
            );
        }

        @Override
        public WebSocketMessage binaryMessage(Function<DataBufferFactory, DataBuffer> payloadFactory) {
            return new WebSocketMessage(WebSocketMessage.Type.BINARY, payloadFactory.apply(bufferFactory));
        }

        @Override
        public WebSocketMessage pingMessage(Function<DataBufferFactory, DataBuffer> payloadFactory) {
            return new WebSocketMessage(WebSocketMessage.Type.PING, payloadFactory.apply(bufferFactory));
        }

        @Override
        public WebSocketMessage pongMessage(Function<DataBufferFactory, DataBuffer> payloadFactory) {
            return new WebSocketMessage(WebSocketMessage.Type.PONG, payloadFactory.apply(bufferFactory));
        }

        CloseStatus closedWith() {
            return closeStatus;
        }

        List<JsonNode> sentJson(ObjectMapper objectMapper) {
            return sentMessages.stream()
                    .map(WebSocketMessage::getPayloadAsText)
                    .map(payload -> readJson(objectMapper, payload))
                    .toList();
        }

        List<JsonNode> deferredSentJson(ObjectMapper objectMapper, int count) {
            Publisher<WebSocketMessage> publisher = deferredMessages;
            if (publisher == null) {
                throw new AssertionError("deferred send publisher was not registered");
            }
            return Flux.from(publisher)
                    .take(count)
                    .map(WebSocketMessage::getPayloadAsText)
                    .map(payload -> readJson(objectMapper, payload))
                    .collectList()
                    .block(Duration.ofSeconds(2));
        }

        boolean awaitDeferredSend() throws InterruptedException {
            return deferredSendLatch.await(2, TimeUnit.SECONDS);
        }

        boolean awaitSentCount(int count) throws InterruptedException {
            if (sentMessages.size() >= count) {
                return true;
            }
            return sentLatch.await(2, TimeUnit.SECONDS);
        }

        void completeReceive() {
            if (inboundSink != null) {
                inboundSink.tryEmitComplete();
            }
        }

        private JsonNode readJson(ObjectMapper objectMapper, String payload) {
            try {
                return objectMapper.readTree(payload);
            } catch (JsonProcessingException ex) {
                throw new AssertionError(ex);
            }
        }
    }
}
