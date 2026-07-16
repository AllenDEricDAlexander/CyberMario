package top.egon.mario.im;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.im.facade.ImFacade;
import top.egon.mario.im.facade.RoomFacade;
import top.egon.mario.im.facade.dto.command.JoinCommand;
import top.egon.mario.im.facade.dto.command.SendMessageCommand;
import top.egon.mario.im.facade.dto.query.HistoryQuery;
import top.egon.mario.im.facade.dto.view.ChannelView;
import top.egon.mario.im.facade.dto.view.JoinResultView;
import top.egon.mario.im.platform.PlatformImBootstrap;
import top.egon.mario.im.platform.PlatformImBootstrapProperties;
import top.egon.mario.im.platform.PlatformRoomFacade;
import top.egon.mario.im.policy.ImPrincipal;
import top.egon.mario.im.repository.ImChannelRepository;
import top.egon.mario.im.repository.ImConversationMemberRepository;
import top.egon.mario.im.repository.ImInboxRepository;
import top.egon.mario.im.service.ImException;
import top.egon.mario.rbac.application.RbacUserDirectoryFacade;
import top.egon.mario.rbac.dto.response.UserDirectoryItemResponse;
import top.egon.mario.rbac.service.bootstrap.RbacAdminBootstrapProperties;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.ai.dashscope.api-key=test-api-key",
        "mario.rbac.bootstrap.admin.enabled=true",
        "mario.rbac.bootstrap.admin.username=platform-admin",
        "mario.rbac.bootstrap.admin.password=Platform#Admin2026!",
        "mario.rbac.role-presets.enabled=false",
        "mario.im.platform.bootstrap.enabled=true",
        "mario.im.platform.bootstrap.owner-account-no=platform-admin",
        "mario.im.platform.bootstrap.channel-key=general",
        "mario.im.platform.bootstrap.channel-name=公共频道"
})
@Transactional
class PlatformImBootstrapTests {

    @Autowired
    private PlatformImBootstrap platformImBootstrap;

    @Autowired
    private ImChannelRepository channelRepository;

    @Autowired
    private RoomFacade roomFacade;

    @Autowired
    private ImFacade imFacade;

    @Autowired
    private ImConversationMemberRepository conversationMemberRepository;

    @Autowired
    private ImInboxRepository inboxRepository;

    @Test
    void bootstrapIsIdempotentAndPublicChannelRequiresJoiningToPostAndAccrueUnread() {
        ChannelView first = platformImBootstrap.bootstrap();
        ChannelView second = platformImBootstrap.bootstrap();

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(first.contextType()).isEqualTo(PlatformRoomFacade.PLATFORM_CONTEXT_TYPE);
        assertThat(first.contextId()).isNull();
        assertThat(first.channelKey()).isEqualTo("general");
        assertThat(first.name()).isEqualTo("公共频道");
        assertThat(first.visibility()).isEqualTo("PUBLIC");
        assertThat(first.joinPolicy()).isEqualTo("OPEN");
        assertThat(channelRepository.findActivePublicByContext(
                PlatformRoomFacade.PLATFORM_CONTEXT_TYPE, null))
                .filteredOn(channel -> "general".equals(channel.getChannelKey()))
                .hasSize(1);

        Long readerUserId = 88001L;
        Long outsiderUserId = 88002L;
        ImPrincipal owner = principal(first.ownerUserId());
        ImPrincipal reader = principal(readerUserId);
        imFacade.send(message(owner, first.mainConversationId(), "general-owner-1", "welcome"));

        assertThat(imFacade.history(new HistoryQuery(
                reader, first.mainConversationId(), 0, 20, null, null)).getContent())
                .extracting(message -> message.content())
                .containsExactly("welcome");
        assertThatThrownBy(() -> imFacade.send(message(
                reader, first.mainConversationId(), "general-reader-denied", "before join")))
                .isInstanceOf(ImException.class)
                .extracting("code")
                .isEqualTo("IM_SEND_DENIED");

        JoinResultView joined = roomFacade.applyJoin(new JoinCommand(
                reader, "CHANNEL", first.id(), "join general"));
        assertThat(joined.status()).isEqualTo("ACTIVE");
        assertThat(imFacade.send(message(
                reader, first.mainConversationId(), "general-reader-joined", "after join")).content())
                .isEqualTo("after join");
        assertThat(conversationMemberRepository.findActiveByConversationIdAndUserId(
                first.mainConversationId(), readerUserId)).isPresent();
        assertThat(conversationMemberRepository.findActiveByConversationIdAndUserId(
                first.mainConversationId(), outsiderUserId)).isEmpty();
        assertThat(inboxRepository.findByUserIdAndReadFalseAndDeletedFalseOrderByMessageSeqAsc(outsiderUserId))
                .isEmpty();
    }

    @Test
    void disabledBootstrapDoesNotQueryUsersOrWriteRooms() {
        PlatformRoomFacade roomFacade = mock(PlatformRoomFacade.class);
        RbacUserDirectoryFacade directoryFacade = mock(RbacUserDirectoryFacade.class);
        PlatformImBootstrap bootstrap = new PlatformImBootstrap(
                new PlatformImBootstrapProperties(false, "missing", "general", "公共频道"),
                roomFacade,
                directoryFacade,
                new RbacAdminBootstrapProperties(false, "admin", "", false));

        assertThat(bootstrap.bootstrap()).isNull();
        verifyNoInteractions(roomFacade, directoryFacade);
    }

    @Test
    void unspecifiedOwnerFallsBackToConfiguredRbacAdministrator() {
        PlatformRoomFacade roomFacade = mock(PlatformRoomFacade.class);
        RbacUserDirectoryFacade directoryFacade = mock(RbacUserDirectoryFacade.class);
        UserDirectoryItemResponse owner = new UserDirectoryItemResponse(
                99001L, "configured-admin", "Configured Admin", null);
        ChannelView channel = new ChannelView(
                99002L, "PLATFORM", null, "general", "公共频道", owner.userId(),
                "PUBLIC", "OPEN", "ACTIVE", "", 99003L, 1, null);
        when(directoryFacade.findEnabledByAccountNo("configured-admin")).thenReturn(Optional.of(owner));
        when(roomFacade.createGeneralChannel(any(), eq("general"), eq("公共频道")))
                .thenReturn(channel);
        PlatformImBootstrap bootstrap = new PlatformImBootstrap(
                new PlatformImBootstrapProperties(true, null, "general", "公共频道"),
                roomFacade,
                directoryFacade,
                new RbacAdminBootstrapProperties(false, "configured-admin", "", false));

        assertThat(bootstrap.bootstrap()).isSameAs(channel);
        verify(directoryFacade).findEnabledByAccountNo("configured-admin");
    }

    private SendMessageCommand message(ImPrincipal principal, Long conversationId, String clientMsgId, String content) {
        return new SendMessageCommand(principal, conversationId, clientMsgId, "TEXT", content, "{}", "{}");
    }

    private ImPrincipal principal(Long userId) {
        return new ImPrincipal(userId, Set.of(), PlatformRoomFacade.PLATFORM_CONTEXT_TYPE, Map.of());
    }
}
