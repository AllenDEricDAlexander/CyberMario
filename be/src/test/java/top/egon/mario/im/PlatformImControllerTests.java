package top.egon.mario.im;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import top.egon.mario.common.api.TraceContext;
import top.egon.mario.im.platform.PlatformImFacade;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformImControllerTests {

    private final Scheduler scheduler = Schedulers.fromExecutorService(
            Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("platform-im-controller-test-", 0).factory()),
            "platform-im-controller-test"
    );
    private final PlatformImFacade platformImFacade = mock(PlatformImFacade.class);
    private final PlatformImController controller = controller();

    @AfterEach
    void tearDown() {
        scheduler.dispose();
    }

    @Test
    void bootstrapAndConversationEndpointsUseAuthenticatedBoundaryPrincipal() {
        PlatformUserView currentUser = new PlatformUserView(1L, "mario", "Mario", null);
        PlatformConversationView conversation = new PlatformConversationView(
                11L, "CHANNEL_MAIN", "PUBLIC_CHANNEL", "公共频道", null, null,
                "CHANNEL", 12L, "general", null, null, true, false,
                0L, null, null, null, null, null, "ACTIVE", 0L);
        PlatformBootstrapView bootstrap = new PlatformBootstrapView(
                currentUser, conversation, List.of(conversation), 0L, 2L);
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

    private PlatformImController controller() {
        PlatformImController controller = new PlatformImController(platformImFacade);
        ReflectionTestUtils.invokeMethod(controller, "setBlockingScheduler", scheduler);
        return controller;
    }

    private RbacPrincipal principal() {
        return new RbacPrincipal(1L, "mario", Set.of("IM_USER"), Set.of("api:im:read"), "v1");
    }
}
