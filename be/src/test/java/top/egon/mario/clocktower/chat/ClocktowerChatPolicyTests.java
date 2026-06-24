package top.egon.mario.clocktower.chat;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static top.egon.mario.clocktower.chat.ClocktowerChatConstants.CONVERSATION_PRIVATE;
import static top.egon.mario.clocktower.chat.ClocktowerChatConstants.GROUP_PUBLIC;
import static top.egon.mario.clocktower.chat.ClocktowerChatConstants.GROUP_SPECTATOR;
import static top.egon.mario.clocktower.chat.ClocktowerChatConstants.GROUP_SYSTEM;

class ClocktowerChatPolicyTests {

    private final ClocktowerChatPolicy policy = new ClocktowerChatPolicy();

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
}
