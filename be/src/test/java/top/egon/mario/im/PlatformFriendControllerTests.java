package top.egon.mario.im;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import top.egon.mario.common.api.TraceContext;
import top.egon.mario.im.facade.FriendFacade;
import top.egon.mario.im.facade.dto.command.RemoveFriendCommand;
import top.egon.mario.im.facade.dto.command.RequestFriendCommand;
import top.egon.mario.im.facade.dto.command.UpdateFriendRemarkCommand;
import top.egon.mario.im.facade.dto.view.FriendRequestView;
import top.egon.mario.im.facade.dto.view.FriendView;
import top.egon.mario.im.web.PlatformFriendController;
import top.egon.mario.rbac.dto.response.UserDirectoryItemResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformFriendControllerTests {

    private final Scheduler scheduler = Schedulers.fromExecutorService(
            Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("friend-controller-test-", 0).factory()),
            "friend-controller-test"
    );
    private final FriendFacade friendFacade = mock(FriendFacade.class);
    private final PlatformFriendController controller = controller();

    @AfterEach
    void tearDown() {
        scheduler.dispose();
    }

    @Test
    void searchUsersConvertsPageAndReturnsSafeProjection() {
        UserDirectoryItemResponse user = new UserDirectoryItemResponse(
                2L, "U0002", "Luigi", "https://example.com/luigi.png");
        when(friendFacade.searchUsers(argThat(principal -> principal.userId().equals(1L)),
                eq("Luigi"), eq(0), eq(20)))
                .thenReturn(new PageImpl<>(List.of(user)));

        StepVerifier.create(controller.searchUsers(principal(), "Luigi", 1, 20)
                        .contextWrite(context -> context.put(TraceContext.CONTEXT_KEY, "trace-user-search")))
                .assertNext(response -> {
                    assertThat(response.traceId()).isEqualTo("trace-user-search");
                    assertThat(response.data().records()).containsExactly(user);
                })
                .verifyComplete();
    }

    @Test
    void friendCommandsDelegateWithAuthenticatedPrincipal() {
        FriendRequestView requestView = new FriendRequestView(
                11L, 1L, 2L, 2L, "U0002", "Luigi", null, true,
                "PENDING", "hello", Instant.now(), null, null);
        when(friendFacade.request(argThat(command -> command.targetUserId().equals(2L))))
                .thenReturn(requestView);
        FriendView friendView = new FriendView(11L, 2L, "U0002", "Luigi", null,
                "Brother", true, Instant.now());
        when(friendFacade.updateRemark(argThat(command -> command.friendUserId().equals(2L))))
                .thenReturn(friendView);

        StepVerifier.create(controller.requestFriend(
                        principal(), new PlatformFriendController.FriendRequest(2L, "hello")))
                .assertNext(response -> assertThat(response.data()).isSameAs(requestView))
                .verifyComplete();
        StepVerifier.create(controller.updateFriendRemark(
                        principal(), 2L, new PlatformFriendController.FriendRemarkRequest("Brother")))
                .assertNext(response -> assertThat(response.data()).isSameAs(friendView))
                .verifyComplete();
        StepVerifier.create(controller.removeFriend(principal(), 2L))
                .assertNext(response -> assertThat(response.data()).isNull())
                .verifyComplete();

        verify(friendFacade).request(argThat((RequestFriendCommand command) ->
                command.principal().userId().equals(1L)
                        && command.targetUserId().equals(2L)
                        && command.message().equals("hello")));
        verify(friendFacade).updateRemark(argThat((UpdateFriendRemarkCommand command) ->
                command.principal().userId().equals(1L)
                        && command.friendUserId().equals(2L)
                        && command.remark().equals("Brother")));
        verify(friendFacade).remove(argThat((RemoveFriendCommand command) ->
                command.principal().userId().equals(1L) && command.friendUserId().equals(2L)));
    }

    private PlatformFriendController controller() {
        PlatformFriendController controller = new PlatformFriendController(friendFacade);
        ReflectionTestUtils.invokeMethod(controller, "setBlockingScheduler", scheduler);
        return controller;
    }

    private RbacPrincipal principal() {
        return new RbacPrincipal(1L, "mario", Set.of("IM_USER"), Set.of("api:im:write"), "v1");
    }
}
