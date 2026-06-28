package top.egon.mario.im.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Scheduler;
import top.egon.mario.im.facade.ImFacade;
import top.egon.mario.im.service.ImException;
import top.egon.mario.im.facade.dto.command.MarkReadCommand;
import top.egon.mario.im.facade.dto.command.SendMessageCommand;
import top.egon.mario.im.facade.dto.view.MessageView;
import top.egon.mario.im.facade.dto.view.UnreadView;
import top.egon.mario.im.policy.ImPrincipal;
import top.egon.mario.im.realtime.ImConnectionRegistry;
import top.egon.mario.im.realtime.ImFrame;
import top.egon.mario.im.service.ImTicketService;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class ImWebSocketHandler implements WebSocketHandler {

    public static final String SUBSCRIPTIONS_ATTRIBUTE = ImWebSocketHandler.class.getName() + ".SUBSCRIPTIONS";

    private static final int DEFAULT_OUTBOUND_CAPACITY = 256;

    private final ImTicketService ticketService;
    private final ImFacade imFacade;
    private final ImConnectionRegistry connectionRegistry;
    private final ObjectMapper objectMapper;
    private final Scheduler blockingScheduler;
    private final int outboundCapacity;

    @Autowired
    public ImWebSocketHandler(ImTicketService ticketService, ImFacade imFacade,
                              ImConnectionRegistry connectionRegistry, ObjectMapper objectMapper,
                              Scheduler blockingScheduler) {
        this(ticketService, imFacade, connectionRegistry, objectMapper, blockingScheduler, DEFAULT_OUTBOUND_CAPACITY);
    }

    ImWebSocketHandler(ImTicketService ticketService, ImFacade imFacade,
                       ImConnectionRegistry connectionRegistry, ObjectMapper objectMapper,
                       Scheduler blockingScheduler, int outboundCapacity) {
        this.ticketService = ticketService;
        this.imFacade = imFacade;
        this.connectionRegistry = connectionRegistry;
        this.objectMapper = objectMapper;
        this.blockingScheduler = blockingScheduler;
        this.outboundCapacity = outboundCapacity;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        return Mono.fromCallable(() -> ticketService.consume(ticket(session.getHandshakeInfo().getUri())))
                .subscribeOn(blockingScheduler)
                .flatMap(principal -> handleAuthenticated(session, principal))
                .onErrorResume(ImException.class, ignored -> session.close(CloseStatus.POLICY_VIOLATION))
                .onErrorResume(IllegalArgumentException.class, ignored -> session.close(CloseStatus.POLICY_VIOLATION));
    }

    private Mono<Void> handleAuthenticated(WebSocketSession session, ImPrincipal principal) {
        Set<Long> subscriptions = ConcurrentHashMap.newKeySet();
        session.getAttributes().put(SUBSCRIPTIONS_ATTRIBUTE, subscriptions);

        BlockingQueue<ImFrame> outboundQueue = new ArrayBlockingQueue<>(outboundCapacity);
        Sinks.Many<ImFrame> outbound = Sinks.many().unicast().onBackpressureBuffer(outboundQueue);
        AtomicReference<Map<String, Object>> resyncHint =
                new AtomicReference<>(Map.of("reason", "OUTBOUND_OVERFLOW"));
        AtomicBoolean closed = new AtomicBoolean(false);
        ImConnectionRegistry.Registration registration = connectionRegistry.register(principal.userId(),
                frame -> emitRealtimeFrame(outbound, outboundQueue, resyncHint, frame));

        Flux<WebSocketMessage> output = outbound.asFlux()
                .map(frame -> session.textMessage(write(frame)));
        Mono<Void> receive = session.receive()
                .filter(message -> WebSocketMessage.Type.TEXT.equals(message.getType()))
                .concatMap(message -> handleClientFrame(principal, subscriptions, outbound, outboundQueue, resyncHint,
                        message.getPayloadAsText()))
                .then()
                .doFinally(ignored -> outbound.tryEmitComplete());
        return Mono.when(session.send(output), receive)
                .onErrorResume(ignored -> session.close(CloseStatus.SERVER_ERROR))
                .doFinally(ignored -> close(registration, outbound, closed));
    }

    private Mono<Void> handleClientFrame(ImPrincipal principal, Set<Long> subscriptions,
                                         Sinks.Many<ImFrame> outbound, BlockingQueue<ImFrame> outboundQueue,
                                         AtomicReference<Map<String, Object>> resyncHint, String payload) {
        ImFrame frame;
        try {
            frame = read(payload);
        } catch (ImException | IllegalArgumentException ex) {
            return recoverClientFrame(outbound, outboundQueue, resyncHint, null, errorCode(ex), Map.of());
        }
        if (frame.type() == null) {
            return recoverClientFrame(outbound, outboundQueue, resyncHint, frame.requestId(), "UNKNOWN_FRAME_TYPE",
                    frame.payload());
        }
        Mono<Void> handled;
        try {
            handled = switch (frame.type()) {
                case "PING" -> {
                    emitServerFrame(outbound, outboundQueue, resyncHint,
                            ImFrame.server("PONG", frame.requestId(), Map.of("time", Instant.now().toString())),
                            frame.payload());
                    yield Mono.empty();
                }
                case "SEND_MESSAGE" -> sendMessage(principal, outbound, outboundQueue, resyncHint, frame);
                case "MARK_READ" -> markRead(principal, outbound, outboundQueue, resyncHint, frame);
                case "SUBSCRIBE" -> {
                    SubscribePayload subscribe = payload(frame, SubscribePayload.class);
                    if (subscribe.conversationId() != null) {
                        subscriptions.add(subscribe.conversationId());
                    }
                    yield Mono.empty();
                }
                default -> recoverClientFrame(outbound, outboundQueue, resyncHint, frame.requestId(),
                        "UNKNOWN_FRAME_TYPE", frame.payload());
            };
        } catch (ImException | IllegalArgumentException ex) {
            return recoverClientFrame(outbound, outboundQueue, resyncHint, frame.requestId(), errorCode(ex),
                    frame.payload());
        }
        return handled
                .onErrorResume(ImException.class, ex -> recoverClientFrame(outbound, outboundQueue, resyncHint,
                        frame.requestId(), ex.getCode(), frame.payload()))
                .onErrorResume(IllegalArgumentException.class, ex -> recoverClientFrame(outbound, outboundQueue,
                        resyncHint, frame.requestId(), "IM_WS_FRAME_INVALID", frame.payload()));
    }

    private Mono<Void> sendMessage(ImPrincipal principal, Sinks.Many<ImFrame> outbound,
                                   BlockingQueue<ImFrame> outboundQueue,
                                   AtomicReference<Map<String, Object>> resyncHint, ImFrame frame) {
        SendMessagePayload payload = payload(frame, SendMessagePayload.class);
        return Mono.fromCallable(() -> imFacade.send(new SendMessageCommand(
                        principal,
                        payload.conversationId(),
                        payload.clientMsgId(),
                        payload.messageType(),
                        payload.content(),
                        payload.payloadJson(),
                        payload.metadataJson()
                )))
                .subscribeOn(blockingScheduler)
                .doOnNext(message -> emitServerFrame(outbound, outboundQueue, resyncHint,
                        sendAck(frame.requestId(), message),
                        hint(message.conversationId(), message.messageSeq())))
                .then();
    }

    private Mono<Void> markRead(ImPrincipal principal, Sinks.Many<ImFrame> outbound,
                                BlockingQueue<ImFrame> outboundQueue,
                                AtomicReference<Map<String, Object>> resyncHint, ImFrame frame) {
        MarkReadPayload payload = payload(frame, MarkReadPayload.class);
        return Mono.fromCallable(() -> imFacade.markRead(new MarkReadCommand(
                        principal,
                        payload.conversationId(),
                        payload.messageSeq()
                )))
                .subscribeOn(blockingScheduler)
                .doOnNext(unread -> emitServerFrame(outbound, outboundQueue, resyncHint,
                        readUpdated(frame.requestId(), unread),
                        hint(unread.conversationId(), unread.lastReadSeq())))
                .then();
    }

    private ImFrame sendAck(String requestId, MessageView message) {
        return ImFrame.server("SEND_ACK", requestId, Map.of("message", message));
    }

    private ImFrame readUpdated(String requestId, UnreadView unread) {
        return ImFrame.server("READ_UPDATED", requestId, Map.of("unread", unread));
    }

    private void emitRealtimeFrame(Sinks.Many<ImFrame> outbound, BlockingQueue<ImFrame> outboundQueue,
                                   AtomicReference<Map<String, Object>> resyncHint,
                                   Map<String, Object> realtimeFrame) {
        ImFrame serverFrame = ImFrame.server(serverType(realtimeFrame), null, realtimeFrame);
        emitServerFrame(outbound, outboundQueue, resyncHint, serverFrame, realtimeFrame);
    }

    private void emitServerFrame(Sinks.Many<ImFrame> outbound, BlockingQueue<ImFrame> outboundQueue,
                                 AtomicReference<Map<String, Object>> resyncHint, ImFrame frame,
                                 Object hintSource) {
        updateResyncHint(resyncHint, hintSource);
        if (outboundQueue.remainingCapacity() == 0) {
            outboundQueue.clear();
            emit(outbound, ImFrame.server("RESYNC", frame.requestId(), resyncHint.get()));
            return;
        }
        Sinks.EmitResult result = emit(outbound, frame);
        if (Sinks.EmitResult.FAIL_OVERFLOW.equals(result)) {
            outboundQueue.clear();
            emit(outbound, ImFrame.server("RESYNC", frame.requestId(), resyncHint.get()));
        }
    }

    private String serverType(Map<String, Object> realtimeFrame) {
        Object eventType = realtimeFrame.get("eventType");
        if ("READ_UPDATED".equals(eventType)) {
            return "READ_UPDATED";
        }
        return "MESSAGE_PUSH";
    }

    private void updateResyncHint(AtomicReference<Map<String, Object>> resyncHint, Object hintSource) {
        Map<String, Object> hint = overflowHint(hintSource);
        if (hint.size() > 1) {
            resyncHint.set(hint);
        }
    }

    private Map<String, Object> overflowHint(Object hintSource) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reason", "OUTBOUND_OVERFLOW");
        if (hintSource instanceof Map<?, ?> hint) {
            Object conversationId = hint.get("conversationId");
            Object messageSeq = hint.get("messageSeq");
            if (conversationId != null) {
                payload.put("conversationId", conversationId);
            }
            if (messageSeq != null) {
                payload.put("messageSeq", messageSeq);
            }
        }
        return payload;
    }

    private Map<String, Object> hint(Long conversationId, Long messageSeq) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (conversationId != null) {
            payload.put("conversationId", conversationId);
        }
        if (messageSeq != null) {
            payload.put("messageSeq", messageSeq);
        }
        return payload;
    }

    private Mono<Void> recoverClientFrame(Sinks.Many<ImFrame> outbound, BlockingQueue<ImFrame> outboundQueue,
                                          AtomicReference<Map<String, Object>> resyncHint, String requestId,
                                          String reason, Object hintSource) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reason", reason);
        emitServerFrame(outbound, outboundQueue, resyncHint, ImFrame.server("RESYNC", requestId, payload), hintSource);
        return Mono.empty();
    }

    private String errorCode(RuntimeException ex) {
        if (ex instanceof ImException imException) {
            return imException.getCode();
        }
        return "IM_WS_FRAME_INVALID";
    }

    private Sinks.EmitResult emit(Sinks.Many<ImFrame> outbound, ImFrame frame) {
        synchronized (outbound) {
            return outbound.tryEmitNext(frame);
        }
    }

    private void close(ImConnectionRegistry.Registration registration, Sinks.Many<ImFrame> outbound,
                       AtomicBoolean closed) {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        registration.close();
        outbound.tryEmitComplete();
    }

    private String ticket(URI uri) {
        String ticket = UriComponentsBuilder.fromUri(uri).build().getQueryParams().getFirst("ticket");
        if (ticket == null || ticket.isBlank()) {
            throw new ImException("IM_WS_TICKET_REQUIRED");
        }
        return ticket;
    }

    private ImFrame read(String payload) {
        try {
            ImFrame frame = objectMapper.readValue(payload, ImFrame.class);
            if (frame == null) {
                throw new ImException("IM_WS_FRAME_INVALID");
            }
            return frame;
        } catch (JsonProcessingException ex) {
            throw new ImException("IM_WS_FRAME_INVALID");
        }
    }

    private String write(ImFrame frame) {
        try {
            return objectMapper.writeValueAsString(frame);
        } catch (JsonProcessingException ex) {
            throw new ImException("IM_WS_FRAME_INVALID");
        }
    }

    private <T> T payload(ImFrame frame, Class<T> type) {
        Object payload = frame.payload() == null ? Map.of() : frame.payload();
        return objectMapper.convertValue(payload, type);
    }

    private record SendMessagePayload(Long conversationId, String clientMsgId, String messageType,
                                      String content, String payloadJson, String metadataJson) {
    }

    private record MarkReadPayload(Long conversationId, Long messageSeq) {
    }

    private record SubscribePayload(Long conversationId, Long lastSeq) {
    }
}
