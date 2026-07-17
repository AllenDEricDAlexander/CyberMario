package top.egon.mario.im;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockReset;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.im.facade.DmFacade;
import top.egon.mario.im.facade.FriendFacade;
import top.egon.mario.im.facade.ImFacade;
import top.egon.mario.im.facade.dto.command.BlockUserCommand;
import top.egon.mario.im.facade.dto.command.DecideFriendRequestCommand;
import top.egon.mario.im.facade.dto.command.OpenDmCommand;
import top.egon.mario.im.facade.dto.command.RemoveFriendCommand;
import top.egon.mario.im.facade.dto.command.RequestFriendCommand;
import top.egon.mario.im.facade.dto.command.SendMessageCommand;
import top.egon.mario.im.facade.dto.query.HistoryQuery;
import top.egon.mario.im.facade.dto.view.ChannelView;
import top.egon.mario.im.facade.dto.view.ConversationView;
import top.egon.mario.im.facade.dto.view.FriendRequestView;
import top.egon.mario.im.facade.dto.view.GroupView;
import top.egon.mario.im.platform.PlatformImFacade;
import top.egon.mario.im.platform.PlatformRoomFacade;
import top.egon.mario.im.platform.dto.PlatformBootstrapView;
import top.egon.mario.im.platform.dto.PlatformConversationView;
import top.egon.mario.im.policy.ImPrincipal;
import top.egon.mario.im.po.ImMembershipPo;
import top.egon.mario.im.po.enums.ImSurfaceType;
import top.egon.mario.im.repository.ImMembershipRepository;
import top.egon.mario.im.service.ImException;
import top.egon.mario.rbac.application.RbacUserDirectoryFacade;
import top.egon.mario.rbac.po.UserPo;
import top.egon.mario.rbac.po.enums.RbacStatus;
import top.egon.mario.rbac.repository.UserRepository;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = {
        "spring.ai.dashscope.api-key=test-api-key",
        "spring.datasource.url=jdbc:h2:mem:platform_im_facade_tests;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"
})
@Transactional
class PlatformImFacadeTests {

    @Autowired
    private PlatformImFacade platformImFacade;

    @Autowired
    private PlatformRoomFacade platformRoomFacade;

    @Autowired
    private FriendFacade friendFacade;

    @Autowired
    private DmFacade dmFacade;

    @Autowired
    private ImFacade imFacade;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ImMembershipRepository membershipRepository;

    @MockitoSpyBean(reset = MockReset.AFTER)
    private RbacUserDirectoryFacade userDirectoryFacade;

    @Test
    void readModelBuildsDmGroupAndChannelWithOneBatchedIdentityLookup() {
        UserPo alice = user("platform-alice", "Alice", "/avatars/alice.png");
        UserPo bob = user("platform-bob", "Bob", null);
        UserPo charlie = user("platform-charlie", "Charlie", null);
        activateFriendship(alice.getId(), bob.getId());

        ImPrincipal alicePrincipal = principal(alice.getId());
        ChannelView channel = platformRoomFacade.createChannel(alicePrincipal, "产品频道", "{}");
        GroupView group = platformRoomFacade.createGroup(
                alicePrincipal, "platform-team", "Platform Team", "OPEN", "{}");
        ConversationView dm = platformImFacade.openDm(new OpenDmCommand(alicePrincipal, bob.getId()));
        platformImFacade.send(message(alicePrincipal, channel.mainConversationId(), "platform-channel-1", "channel"));
        platformImFacade.send(message(alicePrincipal, group.conversationId(), "platform-group-1", "group"));
        platformImFacade.send(message(alicePrincipal, dm.id(), "platform-dm-1", "dm"));
        friendFacade.request(new RequestFriendCommand(principal(charlie.getId()), alice.getId(), "hello"));

        clearInvocations(userDirectoryFacade);
        var conversations = platformImFacade.listConversations(alicePrincipal);

        assertThat(conversations)
                .extracting(PlatformConversationView::displayType)
                .containsExactly("DM", "GROUP", "CHANNEL");
        assertThat(conversations).filteredOn(view -> "CHANNEL".equals(view.displayType()))
                .singleElement()
                .satisfies(view -> {
                    assertThat(view.title()).isEqualTo("产品频道");
                    assertThat(view.surfaceKey()).startsWith("channel-");
                    assertThat(view.canRead()).isTrue();
                    assertThat(view.canPost()).isTrue();
                });
        assertThat(conversations).filteredOn(view -> "GROUP".equals(view.displayType()))
                .singleElement()
                .satisfies(view -> {
                    assertThat(view.title()).isEqualTo("Platform Team");
                    assertThat(view.surfaceKey()).isEqualTo("platform-team");
                    assertThat(view.membershipStatus()).isEqualTo("ACTIVE");
                    assertThat(view.canPost()).isTrue();
                });
        assertThat(conversations).filteredOn(view -> "DM".equals(view.displayType()))
                .singleElement()
                .satisfies(view -> {
                    assertThat(view.title()).isEqualTo("Bob");
                    assertThat(view.avatarUrl()).isNull();
                    assertThat(view.peerUserId()).isEqualTo(bob.getId());
                    assertThat(view.canRead()).isTrue();
                    assertThat(view.canPost()).isTrue();
                });
        assertThat(conversations)
                .allSatisfy(view -> {
                    assertThat(view.lastMessage()).isNotNull();
                    assertThat(view.lastMessageSender()).isNotNull();
                    assertThat(view.lastMessageSender().displayName()).isEqualTo("Alice");
                });
        verify(userDirectoryFacade, times(1)).findEnabledByIds(any());

        PlatformBootstrapView bootstrap = platformImFacade.bootstrap(alicePrincipal);
        assertThat(bootstrap.currentUser().displayName()).isEqualTo("Alice");
        assertThat(bootstrap.pendingFriendRequestCount()).isEqualTo(1);
        assertThat(bootstrap.unreadTotal()).isEqualTo(bootstrap.conversations().stream()
                .mapToLong(PlatformConversationView::unreadCount)
                .sum());
    }

    @Test
    void removedFriendKeepsDmHistoryReadableButBlocksRestAndGenericPlatformSend() {
        UserPo alice = user("platform-remove-alice", "Remove Alice", null);
        UserPo bob = user("platform-remove-bob", "Remove Bob", "/avatars/bob.png");
        activateFriendship(alice.getId(), bob.getId());
        ImPrincipal alicePrincipal = principal(alice.getId());
        ConversationView dm = platformImFacade.openDm(new OpenDmCommand(alicePrincipal, bob.getId()));
        platformImFacade.send(message(alicePrincipal, dm.id(), "platform-before-remove", "retained"));

        friendFacade.remove(new RemoveFriendCommand(alicePrincipal, bob.getId()));

        assertThat(imFacade.history(new HistoryQuery(
                alicePrincipal, dm.id(), 0, 20, null, null)).getContent())
                .extracting(message -> message.content())
                .containsExactly("retained");
        assertThat(platformImFacade.listConversations(alicePrincipal))
                .filteredOn(view -> dm.id().equals(view.conversationId()))
                .singleElement()
                .satisfies(view -> {
                    assertThat(view.canRead()).isTrue();
                    assertThat(view.canPost()).isFalse();
                });
        assertThatThrownBy(() -> platformImFacade.send(message(
                alicePrincipal, dm.id(), "platform-after-remove", "denied")))
                .isInstanceOf(ImException.class)
                .extracting("code")
                .isEqualTo("IM_DM_FRIENDSHIP_REQUIRED");
        assertThatThrownBy(() -> platformImFacade.openDm(
                new OpenDmCommand(alicePrincipal, bob.getId())))
                .isInstanceOf(ImException.class)
                .extracting("code")
                .isEqualTo("IM_DM_FRIENDSHIP_REQUIRED");
    }

    @Test
    void activeFriendshipDoesNotBypassExistingDmBlockPolicy() {
        UserPo alice = user("platform-block-alice", "Block Alice", null);
        UserPo bob = user("platform-block-bob", "Block Bob", null);
        activateFriendship(alice.getId(), bob.getId());
        ImPrincipal alicePrincipal = principal(alice.getId());
        ConversationView dm = platformImFacade.openDm(new OpenDmCommand(alicePrincipal, bob.getId()));

        dmFacade.block(new BlockUserCommand(alicePrincipal, bob.getId(), "blocked"));

        assertThatThrownBy(() -> platformImFacade.send(message(
                alicePrincipal, dm.id(), "platform-blocked", "denied")))
                .isInstanceOf(ImException.class)
                .extracting("code")
                .isEqualTo("IM_SEND_DENIED");
        assertThat(platformImFacade.listConversations(alicePrincipal))
                .filteredOn(view -> dm.id().equals(view.conversationId()))
                .singleElement()
                .extracting(PlatformConversationView::canPost)
                .isEqualTo(false);
    }

    @Test
    void mutedGroupMembershipRemainsReadableButCannotPost() {
        UserPo alice = user("platform-muted-alice", "Muted Alice", null);
        ImPrincipal alicePrincipal = principal(alice.getId());
        GroupView group = platformRoomFacade.createGroup(
                alicePrincipal, "platform-muted-team", "Muted Team", "OPEN", "{}");
        ImMembershipPo membership = membershipRepository
                .findBySurfaceTypeAndSurfaceIdAndUserIdAndDeletedFalse(
                        ImSurfaceType.GROUP, group.id(), alice.getId())
                .orElseThrow();
        membership.setMutedUntil(Instant.now().plusSeconds(600));
        membershipRepository.saveAndFlush(membership);

        assertThat(platformImFacade.listConversations(alicePrincipal))
                .filteredOn(view -> group.conversationId().equals(view.conversationId()))
                .singleElement()
                .satisfies(view -> {
                    assertThat(view.canRead()).isTrue();
                    assertThat(view.canPost()).isFalse();
                });
    }

    private FriendRequestView activateFriendship(Long requesterUserId, Long targetUserId) {
        FriendRequestView request = friendFacade.request(new RequestFriendCommand(
                principal(requesterUserId), targetUserId, "hello"));
        return friendFacade.accept(new DecideFriendRequestCommand(
                principal(targetUserId), request.id(), null));
    }

    private SendMessageCommand message(ImPrincipal principal, Long conversationId, String clientMsgId, String content) {
        return new SendMessageCommand(principal, conversationId, clientMsgId, "TEXT", content, "{}", "{}");
    }

    private UserPo user(String accountNo, String displayName, String avatarUrl) {
        UserPo user = new UserPo();
        user.setAccountNo(accountNo);
        user.setUsername(accountNo);
        user.setNickname(displayName);
        user.setAvatarUrl(avatarUrl);
        user.setPasswordHash("test-password-hash");
        user.setStatus(RbacStatus.ENABLED);
        return userRepository.saveAndFlush(user);
    }

    private ImPrincipal principal(Long userId) {
        return new ImPrincipal(userId, Set.of("IM_USER"), PlatformRoomFacade.PLATFORM_CONTEXT_TYPE, Map.of());
    }
}
