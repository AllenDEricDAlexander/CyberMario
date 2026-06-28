package top.egon.mario.clocktower.chat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import top.egon.mario.im.policy.ImAccessContext;
import top.egon.mario.im.policy.PolicyRegistry;
import top.egon.mario.im.policy.SendPolicy;
import top.egon.mario.im.policy.VisibilityPolicy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static top.egon.mario.clocktower.chat.ClocktowerChatConstants.CONVERSATION_PRIVATE;
import static top.egon.mario.clocktower.chat.ClocktowerChatConstants.GROUP_PUBLIC;
import static top.egon.mario.clocktower.chat.ClocktowerChatConstants.GROUP_SPECTATOR;
import static top.egon.mario.clocktower.chat.ClocktowerChatConstants.GROUP_SYSTEM;

@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
class ClocktowerChatPolicyTests {

    private final ClocktowerChatPolicy policy = new ClocktowerChatPolicy();

    @Autowired
    private PolicyRegistry policyRegistry;

    @Test
    void policyRegistryResolvesClocktowerStrategiesForClocktowerContext() {
        assertThat(ClocktowerChatConstants.CONTEXT_TYPE).isEqualTo("CLOCKTOWER_ROOM");
        assertThat(policyRegistry.resolve(ClocktowerChatConstants.CONTEXT_TYPE, SendPolicy.class))
                .isInstanceOf(ClocktowerSendPolicy.class);
        assertThat(policyRegistry.resolve(ClocktowerChatConstants.CONTEXT_TYPE, VisibilityPolicy.class))
                .isInstanceOf(ClocktowerVisibilityPolicy.class);
    }

    @Test
    void visibilityPolicyReturnsFalseForUnauthenticatedContextWithoutAdapterLookup() {
        ClocktowerImAdapter adapter = mock(ClocktowerImAdapter.class);
        doThrow(new AssertionError("adapter should not be called for unauthenticated visibility"))
                .when(adapter).accessContext(any(ImAccessContext.class));
        ClocktowerVisibilityPolicy visibilityPolicy = new ClocktowerVisibilityPolicy(adapter, policy);

        assertThat(visibilityPolicy.canRead(unauthenticatedAccessContext())).isFalse();
        verifyNoInteractions(adapter);
    }

    @Test
    void playerPublicChatAllowedOnlyDuringDayLikePhases() {
        assertThat(canSend(playerPublic("DAY"))).isTrue();
        assertThat(canSend(playerPublic("NOMINATION"))).isTrue();
        assertThat(canSend(playerPublic("EXECUTION"))).isTrue();

        assertThat(canSend(playerPublic("FIRST_NIGHT"))).isFalse();
        assertThat(canSend(playerPublic("NIGHT"))).isFalse();
    }

    @Test
    void storytellerAnnouncementBypassesNightRestriction() {
        ClocktowerChatAccessContext publicAnnouncement = context(ClocktowerChatViewerMode.STORYTELLER,
                GROUP_PUBLIC, GROUP_PUBLIC, "NIGHT", 2, false);
        ClocktowerChatAccessContext systemAnnouncement = context(ClocktowerChatViewerMode.STORYTELLER,
                GROUP_SYSTEM, GROUP_SYSTEM, "NIGHT", 2, false);

        assertThat(canSend(publicAnnouncement)).isTrue();
        assertThat(canSend(systemAnnouncement)).isTrue();
    }

    @Test
    void spectatorCanOnlySendSpectatorChannel() {
        assertThat(canSend(context(ClocktowerChatViewerMode.SPECTATOR,
                GROUP_PUBLIC, GROUP_PUBLIC, "DAY", 1, false))).isFalse();
        assertThat(canSend(context(ClocktowerChatViewerMode.SPECTATOR,
                ClocktowerChatConstants.GROUP_PRIVATE, CONVERSATION_PRIVATE, "DAY", 1, false))).isFalse();
        assertThat(canSend(context(ClocktowerChatViewerMode.SPECTATOR,
                GROUP_SPECTATOR, GROUP_SPECTATOR, "NIGHT", 1, false))).isTrue();
    }

    @Test
    void storytellerCannotReadSpectatorChannelInNormalRoomPage() {
        ClocktowerChatAccessContext spectatorChannel = context(ClocktowerChatViewerMode.STORYTELLER,
                GROUP_SPECTATOR, GROUP_SPECTATOR, "DAY", 1, false);

        assertThat(policy.canRead(spectatorChannel)).isFalse();
    }

    @Test
    void managementAuditCanReadAllConversations() {
        assertThat(policy.canRead(context(ClocktowerChatViewerMode.ADMIN_AUDIT,
                GROUP_SPECTATOR, GROUP_SPECTATOR, "NIGHT", 4, false))).isTrue();
        assertThat(policy.canRead(context(ClocktowerChatViewerMode.ADMIN_AUDIT,
                ClocktowerChatConstants.GROUP_PRIVATE, CONVERSATION_PRIVATE, "DAY", 4, false))).isTrue();
    }

    @Test
    void privateChatWindowDefaultsToFirstTwoDays() {
        assertThat(canSend(context(ClocktowerChatViewerMode.PLAYER,
                ClocktowerChatConstants.GROUP_PRIVATE, CONVERSATION_PRIVATE, "DAY", 1, true))).isTrue();
        assertThat(canSend(context(ClocktowerChatViewerMode.PLAYER,
                ClocktowerChatConstants.GROUP_PRIVATE, CONVERSATION_PRIVATE, "DAY", 2, true))).isTrue();
        assertThat(canSend(context(ClocktowerChatViewerMode.PLAYER,
                ClocktowerChatConstants.GROUP_PRIVATE, CONVERSATION_PRIVATE, "NIGHT", 1, true))).isFalse();
        assertThat(canSend(context(ClocktowerChatViewerMode.PLAYER,
                ClocktowerChatConstants.GROUP_PRIVATE, CONVERSATION_PRIVATE, "DAY", 3, true))).isFalse();
    }

    private boolean canSend(ClocktowerChatAccessContext context) {
        return policy.canSend(context);
    }

    private ClocktowerChatAccessContext playerPublic(String phase) {
        return context(ClocktowerChatViewerMode.PLAYER, GROUP_PUBLIC, GROUP_PUBLIC, phase, 1, true);
    }

    private ClocktowerChatAccessContext context(ClocktowerChatViewerMode mode, String groupKey,
                                                String conversationType, String phase, int dayNo,
                                                boolean activeMember) {
        return new ClocktowerChatAccessContext(mode, groupKey, conversationType, phase, dayNo, activeMember);
    }

    private ImAccessContext unauthenticatedAccessContext() {
        return new ImAccessContext(null, ClocktowerChatConstants.CONTEXT_TYPE, 1L, null,
                null, null, 1L, null, null, null, false, false, false, false, false, null);
    }
}
