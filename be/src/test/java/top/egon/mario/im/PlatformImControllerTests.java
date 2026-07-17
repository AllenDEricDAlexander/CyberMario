package top.egon.mario.im;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import top.egon.mario.common.api.TraceContext;
import top.egon.mario.im.platform.PlatformImFacade;
import top.egon.mario.im.platform.PlatformRoomFacade;
import top.egon.mario.im.facade.dto.view.ChannelView;
import top.egon.mario.im.facade.dto.view.GroupView;
import top.egon.mario.im.platform.dto.PlatformBootstrapView;
import top.egon.mario.im.platform.dto.PlatformConversationView;
import top.egon.mario.im.platform.dto.PlatformUserView;
import top.egon.mario.im.web.PlatformImController;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformImControllerTests {

    private final Scheduler scheduler = Schedulers.fromExecutorService(
            Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("platform-im-controller-test-", 0).factory()),
            "platform-im-controller-test"
    );
    private final PlatformImFacade platformImFacade = mock(PlatformImFacade.class);
    private final PlatformRoomFacade platformRoomFacade = mock(PlatformRoomFacade.class);
    private final PlatformImController controller = controller();

    @AfterEach
    void tearDown() {
        scheduler.dispose();
    }

    @Test
    void bootstrapAndConversationEndpointsUseAuthenticatedBoundaryPrincipal() {
        PlatformUserView currentUser = new PlatformUserView(1L, "mario", "Mario", null);
        PlatformConversationView conversation = new PlatformConversationView(
                11L, "CHANNEL_MAIN", "CHANNEL", "产品频道", null, null,
                "CHANNEL", 12L, "channel-12", null, null, true, false,
                0L, null, null, null, null, null, "ACTIVE", 0L);
        PlatformBootstrapView bootstrap = new PlatformBootstrapView(
                currentUser, List.of(conversation), 0L, 2L);
        when(platformImFacade.bootstrap(argThat(principal -> principal.userId().equals(1L))))
                .thenReturn(bootstrap);
        when(platformImFacade.listConversations(argThat(principal -> principal.userId().equals(1L))))
                .thenReturn(List.of(conversation));

        StepVerifier.create(controller.bootstrap(principal())
                        .contextWrite(context -> context.put(TraceContext.CONTEXT_KEY, "trace-platform-bootstrap")))
                .assertNext(response -> {
                    assertThat(response.traceId()).isEqualTo("trace-platform-bootstrap");
                    assertThat(response.data()).isSameAs(bootstrap);
                })
                .verifyComplete();
        StepVerifier.create(controller.listConversations(principal()))
                .assertNext(response -> assertThat(response.data()).containsExactly(conversation))
                .verifyComplete();
        verify(platformImFacade).bootstrap(argThat(principal -> principal.userId().equals(1L)
                && "RBAC".equals(principal.contextType())));
    }

    @Test
    void roomEndpointsUsePlatformAdapterAndDoNotExposeDiscoveryParameters() {
        ChannelView channel = new ChannelView(
                12L, "PLATFORM", null, "channel-12", "产品频道", 1L,
                "PUBLIC", "APPROVAL", "ACTIVE", "", 11L, 1, null);
        GroupView group = new GroupView(
                21L, 12L, "PLATFORM", null, "group-21", "研发群", 1L,
                "OPEN", "ACTIVE", "", 22L, 1, null);
        when(platformRoomFacade.createChannel(argThat(principal -> principal.userId().equals(1L)),
                eq("产品频道"), eq("{}"))).thenReturn(channel);
        when(platformRoomFacade.createChannelGroup(argThat(principal -> principal.userId().equals(1L)),
                eq(12L), eq("研发群"), eq("OPEN"), eq("{}"))).thenReturn(group);

        StepVerifier.create(controller.createChannel(
                        principal(), new PlatformImController.SurfaceCreateRequest("产品频道", "{}")))
                .assertNext(response -> assertThat(response.data()).isSameAs(channel))
                .verifyComplete();
        StepVerifier.create(controller.createChannelGroup(
                        principal(), 12L,
                        new PlatformImController.ChannelGroupCreateRequest("研发群", "OPEN", "{}")))
                .assertNext(response -> assertThat(response.data()).isSameAs(group))
                .verifyComplete();
    }

    private PlatformImController controller() {
        PlatformImController controller = new PlatformImController(platformImFacade, platformRoomFacade);
        ReflectionTestUtils.invokeMethod(controller, "setBlockingScheduler", scheduler);
        return controller;
    }

    private RbacPrincipal principal() {
        return new RbacPrincipal(1L, "mario", Set.of("IM_USER"), Set.of("api:im:read"), "v1");
    }
}
