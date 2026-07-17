package top.egon.mario.im;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import top.egon.mario.config.GlobalExceptionHandler;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.common.api.TraceContext;
import top.egon.mario.im.facade.DmFacade;
import top.egon.mario.im.facade.GovFacade;
import top.egon.mario.im.facade.ImFacade;
import top.egon.mario.im.facade.RoomFacade;
import top.egon.mario.im.facade.dto.command.MintWsTicketCommand;
import top.egon.mario.im.facade.dto.command.OpenDmCommand;
import top.egon.mario.im.facade.dto.command.SendMessageCommand;
import top.egon.mario.im.facade.dto.query.ListConversationsQuery;
import top.egon.mario.im.facade.dto.view.ConversationView;
import top.egon.mario.im.facade.dto.view.MessageView;
import top.egon.mario.im.facade.dto.view.SurfaceMemberView;
import top.egon.mario.im.facade.dto.view.WsTicketView;
import top.egon.mario.im.policy.ImPrincipal;
import top.egon.mario.im.platform.PlatformImFacade;
import top.egon.mario.im.service.ImException;
import top.egon.mario.im.web.ImController;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ImControllerTests {

    private final Scheduler scheduler = Schedulers.fromExecutorService(
            Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("im-controller-test-", 0).factory()),
            "im-controller-test"
    );
    private final ImFacade imFacade = mock(ImFacade.class);
    private final PlatformImFacade platformImFacade = mock(PlatformImFacade.class);
    private final RoomFacade roomFacade = mock(RoomFacade.class);
    private final DmFacade dmFacade = mock(DmFacade.class);
    private final GovFacade govFacade = mock(GovFacade.class);
    private final ImController controller = controller();

    @AfterEach
    void tearDown() {
        scheduler.dispose();
    }

    @Test
    void listConversationsDelegatesToFacadeOnBlockingSchedulerAndWrapsResponse() {
        AtomicReference<String> workerThreadName = new AtomicReference<>();
        ConversationView view = new ConversationView(
                7701L, "DM", "DM_PAIR", 3301L, "clocktower", 42L, 3L,
                null, null, null, Instant.parse("2026-06-28T01:00:00Z"), "ACTIVE", 2L);
        when(imFacade.listConversations(argThat(query -> query != null && query.contextId().equals(42L))))
                .thenAnswer(invocation -> {
                    workerThreadName.set(Thread.currentThread().getName());
                    return List.of(view);
                });

        Mono<ApiResponse<List<ConversationView>>> response = controller
                .listConversations(principal(), "clocktower", 42L)
                .contextWrite(context -> context.put(TraceContext.CONTEXT_KEY, "trace-im-controller"));

        StepVerifier.create(response)
                .assertNext(body -> {
                    assertThat(body.code()).isEqualTo("0");
                    assertThat(body.traceId()).isEqualTo("trace-im-controller");
                    assertThat(body.data()).containsExactly(view);
                })
                .verifyComplete();
        assertThat(workerThreadName.get()).contains("im-controller-test-");
        verify(imFacade).listConversations(argThat((ListConversationsQuery query) ->
                query.contextType().equals("clocktower")
                        && query.contextId().equals(42L)
                        && isBoundaryPrincipal(query.principal())));
    }

    @Test
    void sendMessageDelegatesToFacadeAndPreservesClientMessageId() {
        MessageView message = new MessageView(
                8801L, 7701L, 12001L, 4L, "client-abc", "TEXT", "hello", "{}",
                "VISIBLE", Instant.parse("2026-06-28T01:01:00Z"), null, null, "{}");
        when(platformImFacade.send(argThat(command -> command != null && "client-abc".equals(command.clientMsgId()))))
                .thenReturn(message);

        StepVerifier.create(controller.sendMessage(principal(), new ImController.SendMessageRequest(
                                7701L, "client-abc", "TEXT", "hello", "{}", "{}"))
                        .contextWrite(context -> context.put(TraceContext.CONTEXT_KEY, "trace-send")))
                .assertNext(body -> {
                    assertThat(body.traceId()).isEqualTo("trace-send");
                    assertThat(body.data().clientMsgId()).isEqualTo("client-abc");
                })
                .verifyComplete();
        verify(platformImFacade).send(argThat((SendMessageCommand command) ->
                command.conversationId().equals(7701L)
                        && command.clientMsgId().equals("client-abc")
                        && command.messageType().equals("TEXT")
                        && command.content().equals("hello")
                        && isBoundaryPrincipal(command.principal())));
    }

    @Test
    void historyConvertsRestPageNumberToServicePageIndex() {
        MessageView message = new MessageView(
                8802L, 7701L, 12001L, 5L, "client-page", "TEXT", "paged", "{}",
                "VISIBLE", Instant.parse("2026-06-28T01:01:30Z"), null, null, "{}");
        when(imFacade.history(any())).thenReturn(new PageImpl<>(List.of(message)));

        StepVerifier.create(controller.history(principal(), 7701L, 1, 20, null, null)
                        .contextWrite(context -> context.put(TraceContext.CONTEXT_KEY, "trace-history")))
                .assertNext(body -> {
                    assertThat(body.traceId()).isEqualTo("trace-history");
                    assertThat(body.data().records()).containsExactly(message);
                })
                .verifyComplete();
        verify(imFacade).history(argThat(query ->
                query.conversationId().equals(7701L)
                        && query.page() == 0
                        && query.size() == 20
                        && isBoundaryPrincipal(query.principal())));
    }

    @Test
    void openDmDelegatesToPlatformFriendshipGate() {
        ConversationView conversation = new ConversationView(
                7702L, "DM", "DM_PAIR", 3302L, "PLATFORM", null, 0L,
                null, null, null, Instant.parse("2026-07-16T01:00:00Z"), "ACTIVE", 0L);
        when(platformImFacade.openDm(argThat(command -> command != null
                && Long.valueOf(12002L).equals(command.targetUserId()))))
                .thenReturn(conversation);

        StepVerifier.create(controller.openDm(principal(), new ImController.OpenDmRequest(12002L)))
                .assertNext(body -> assertThat(body.data()).isSameAs(conversation))
                .verifyComplete();
        verify(platformImFacade).openDm(argThat((OpenDmCommand command) ->
                command.targetUserId().equals(12002L) && isBoundaryPrincipal(command.principal())));
    }

    @Test
    void ticketEndpointReturnsTicketViewForAuthenticatedPrincipal() {
        WsTicketView ticket = new WsTicketView("raw-ticket", Instant.parse("2026-06-28T01:02:00Z"));
        when(imFacade.mintWsTicket(argThat(command -> command != null && command.conversationId().equals(7701L))))
                .thenReturn(ticket);

        StepVerifier.create(controller.mintWsTicket(principal(), new ImController.MintWsTicketRequest(7701L))
                        .contextWrite(context -> context.put(TraceContext.CONTEXT_KEY, "trace-ticket")))
                .assertNext(body -> {
                    assertThat(body.traceId()).isEqualTo("trace-ticket");
                    assertThat(body.data()).isSameAs(ticket);
                })
                .verifyComplete();
        verify(imFacade).mintWsTicket(argThat((MintWsTicketCommand command) ->
                command.conversationId().equals(7701L) && isBoundaryPrincipal(command.principal())));
    }

    @Test
    void surfaceMemberEndpointReturnsBoundedPage() {
        SurfaceMemberView member = new SurfaceMemberView(
                9901L, 12001L, "mario", "Mario", null, true,
                "OWNER", "ACTIVE", null, Instant.parse("2026-07-16T01:00:00Z"));
        when(roomFacade.listMembers(argThat(query -> query != null
                && "GROUP".equals(query.surfaceType())
                && Long.valueOf(8801L).equals(query.surfaceId())
                && query.page() == 0
                && query.size() == 50)))
                .thenReturn(new PageImpl<>(List.of(member)));

        StepVerifier.create(controller.listSurfaceMembers(principal(), "GROUP", 8801L, 1, 50))
                .assertNext(body -> {
                    assertThat(body.data().records()).containsExactly(member);
                    assertThat(body.data().total()).isEqualTo(1);
                })
                .verifyComplete();
    }

    @Test
    void missingAuthenticatedPrincipalFailsBeforeFacadeAccess() {
        StepVerifier.create(controller.sendMessage(null, new ImController.SendMessageRequest(
                                7701L, "client-abc", "TEXT", "hello", "{}", "{}")))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ImException.class)
                        .extracting("code")
                        .isEqualTo("IM_PRINCIPAL_REQUIRED"))
                .verify();
        verifyNoInteractions(imFacade, platformImFacade, roomFacade, dmFacade, govFacade);
    }

    @Test
    void imExceptionHandlerReturnsApiResponseFailure() {
        StepVerifier.create(new GlobalExceptionHandler()
                        .handleImException(new ImException("IM_PRINCIPAL_REQUIRED"))
                        .contextWrite(context -> context.put(TraceContext.CONTEXT_KEY, "trace-im-error")))
                .assertNext(response -> assertImErrorResponse(response, "trace-im-error"))
                .verifyComplete();
    }

    private ImController controller() {
        ImController controller = new ImController(
                imFacade, platformImFacade, roomFacade, dmFacade, govFacade);
        ReflectionTestUtils.invokeMethod(controller, "setBlockingScheduler", scheduler);
        return controller;
    }

    private RbacPrincipal principal() {
        return new RbacPrincipal(12001L, "mario", Set.of("im-user"), Set.of("api:im:write"), "permission-v1");
    }

    private boolean isBoundaryPrincipal(ImPrincipal principal) {
        return principal != null
                && principal.userId().equals(12001L)
                && principal.roleCodes().contains("im-user")
                && principal.contextType().equals("RBAC")
                && principal.attributes().equals(Map.of(
                "username", "mario",
                "permissionVersion", "permission-v1"
        ));
    }

    private void assertImErrorResponse(ResponseEntity<ApiResponse<Void>> response, String traceId) {
        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("IM_PRINCIPAL_REQUIRED");
        assertThat(response.getBody().traceId()).isEqualTo(traceId);
    }
}
