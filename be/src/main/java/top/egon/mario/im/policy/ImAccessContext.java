package top.egon.mario.im.policy;

import top.egon.mario.im.po.enums.ImConversationStatus;
import top.egon.mario.im.po.enums.ImConversationType;
import top.egon.mario.im.po.enums.ImMembershipStatus;
import top.egon.mario.im.po.enums.ImSurfaceStatus;
import top.egon.mario.im.po.enums.ImSurfaceType;

import java.time.Instant;

public record ImAccessContext(
        ImPrincipal principal,
        String contextType,
        Long contextId,
        ImConversationType conversationType,
        ImConversationStatus conversationStatus,
        ImSurfaceType surfaceType,
        Long surfaceId,
        ImSurfaceStatus surfaceStatus,
        ImMembershipStatus membershipStatus,
        Instant memberMutedUntil,
        boolean conversationMemberMuted,
        boolean activeBan,
        boolean activeGlobalMute,
        boolean dmPairParticipant,
        boolean dmPairFrozen,
        Instant now) {

    public boolean authenticated() {
        return principal != null;
    }

    public boolean activeConversation() {
        return conversationStatus == ImConversationStatus.ACTIVE;
    }

    public boolean activeSurface() {
        return surfaceStatus == ImSurfaceStatus.ACTIVE;
    }

    public boolean activeChannelSurface() {
        return surfaceType == ImSurfaceType.CHANNEL && activeSurface();
    }

    public boolean activeGroupSurface() {
        return surfaceType == ImSurfaceType.GROUP && activeSurface();
    }

    public boolean activeDmPairSurface() {
        return surfaceType == ImSurfaceType.DM_PAIR && activeSurface();
    }

    public boolean activeMembership() {
        return membershipStatus == ImMembershipStatus.ACTIVE;
    }

    public boolean activeMemberMute() {
        return conversationMemberMuted || memberMutedUntil != null && memberMutedUntil.isAfter(evaluationTime());
    }

    private Instant evaluationTime() {
        return now == null ? Instant.now() : now;
    }
}
