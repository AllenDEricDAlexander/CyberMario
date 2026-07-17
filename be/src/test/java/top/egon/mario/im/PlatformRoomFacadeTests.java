package top.egon.mario.im;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.im.facade.dto.view.ChannelView;
import top.egon.mario.im.facade.dto.view.GroupView;
import top.egon.mario.im.platform.PlatformRoomFacade;
import top.egon.mario.im.policy.ImPrincipal;
import top.egon.mario.im.service.ImException;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.ai.dashscope.api-key=test-api-key",
        "spring.datasource.url=jdbc:h2:mem:platform_room_facade_tests;MODE=PostgreSQL;"
                + "DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"
})
@Transactional
class PlatformRoomFacadeTests {

    @Autowired
    private PlatformRoomFacade platformRoomFacade;

    @Test
    void freshPlatformHasNoDefaultChannelAndListsOnlyCallerMemberships() {
        ImPrincipal alice = principal(81001L);
        ImPrincipal bob = principal(81002L);

        assertThat(platformRoomFacade.listChannels(alice)).isEmpty();

        ChannelView aliceChannel = platformRoomFacade.createChannel(alice, "Alice 频道", "{}");
        ChannelView bobChannel = platformRoomFacade.createChannel(bob, "Bob 频道", "{}");
        GroupView aliceGroup = platformRoomFacade.createStandaloneGroup(alice, "Alice 群组", "{}");
        platformRoomFacade.createStandaloneGroup(bob, "Bob 群组", "{}");

        assertThat(platformRoomFacade.listChannels(alice))
                .extracting(ChannelView::id)
                .containsExactly(aliceChannel.id())
                .doesNotContain(bobChannel.id());
        assertThat(platformRoomFacade.listGroups(alice))
                .extracting(GroupView::id)
                .containsExactly(aliceGroup.id());
    }

    @Test
    void channelOwnerCanCreateChildGroupButOutsiderCannotDiscoverIt() {
        ImPrincipal owner = principal(82001L);
        ImPrincipal outsider = principal(82002L);
        ChannelView channel = platformRoomFacade.createChannel(owner, "产品频道", "{}");

        GroupView group = platformRoomFacade.createChannelGroup(
                owner, channel.id(), "研发群", "OPEN", "{}");

        assertThat(platformRoomFacade.listChannelGroups(owner, channel.id()))
                .extracting(GroupView::id)
                .containsExactly(group.id());
        assertThatThrownBy(() -> platformRoomFacade.listChannelGroups(outsider, channel.id()))
                .isInstanceOf(ImException.class)
                .extracting("code")
                .isEqualTo("IM_PARENT_CHANNEL_MEMBERSHIP_REQUIRED");
        assertThatThrownBy(() -> platformRoomFacade.createChannelGroup(
                outsider, channel.id(), "越权群", "OPEN", "{}"))
                .isInstanceOf(ImException.class)
                .extracting("code")
                .isEqualTo("IM_PARENT_CHANNEL_MEMBERSHIP_REQUIRED");
    }

    private ImPrincipal principal(Long userId) {
        return new ImPrincipal(userId, Set.of("IM_USER"), PlatformRoomFacade.PLATFORM_CONTEXT_TYPE, Map.of());
    }
}
