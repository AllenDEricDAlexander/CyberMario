package top.egon.mario.clocktower.chat;

import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class ClocktowerChatPolicy {

    private static final int DEFAULT_PRIVATE_CHAT_DAY_LIMIT = 2;
    private static final Set<String> DAY_LIKE_PHASES = Set.of("DAY", "NOMINATION", "EXECUTION");

    public boolean canSend(ClocktowerChatAccessContext context) {
        if (context == null || context.viewerMode() == null) {
            return false;
        }
        if (context.viewerMode() == ClocktowerChatViewerMode.ADMIN_AUDIT
                || context.viewerMode() == ClocktowerChatViewerMode.UNKNOWN) {
            return false;
        }
        if (isSpectatorChannel(context)) {
            return context.viewerMode() == ClocktowerChatViewerMode.SPECTATOR;
        }
        if (context.viewerMode() == ClocktowerChatViewerMode.SPECTATOR) {
            return false;
        }
        if (isSystemChannel(context)) {
            return context.viewerMode() == ClocktowerChatViewerMode.STORYTELLER;
        }
        if (isRoomConversation(context)) {
            return context.activeConversationMember();
        }
        if (isPublicConversation(context)) {
            if (context.viewerMode() == ClocktowerChatViewerMode.STORYTELLER) {
                return true;
            }
            return context.viewerMode() == ClocktowerChatViewerMode.PLAYER
                    && context.activeConversationMember()
                    && isDayLike(context.phase());
        }
        if (isPrivateConversation(context)) {
            return context.viewerMode() == ClocktowerChatViewerMode.PLAYER
                    && context.activeConversationMember()
                    && isDayLike(context.phase())
                    && context.dayNo() >= 1
                    && context.dayNo() <= DEFAULT_PRIVATE_CHAT_DAY_LIMIT;
        }
        return false;
    }

    public boolean canRead(ClocktowerChatAccessContext context) {
        if (context == null || context.viewerMode() == null
                || context.viewerMode() == ClocktowerChatViewerMode.UNKNOWN) {
            return false;
        }
        if (context.viewerMode() == ClocktowerChatViewerMode.ADMIN_AUDIT) {
            return true;
        }
        if (isSpectatorChannel(context)) {
            return context.viewerMode() == ClocktowerChatViewerMode.SPECTATOR;
        }
        if (isPrivateConversation(context)) {
            return context.viewerMode() == ClocktowerChatViewerMode.STORYTELLER
                    || (context.viewerMode() == ClocktowerChatViewerMode.PLAYER
                    && context.activeConversationMember());
        }
        if (isPublicConversation(context) || isSystemChannel(context) || isRoomConversation(context)) {
            return context.viewerMode() == ClocktowerChatViewerMode.STORYTELLER
                    || context.viewerMode() == ClocktowerChatViewerMode.PLAYER
                    || context.viewerMode() == ClocktowerChatViewerMode.SPECTATOR;
        }
        return false;
    }

    private boolean isDayLike(String phase) {
        return phase != null && DAY_LIKE_PHASES.contains(phase);
    }

    private boolean isRoomConversation(ClocktowerChatAccessContext context) {
        return ClocktowerChatConstants.CONVERSATION_ROOM.equals(context.conversationType());
    }

    private boolean isPublicConversation(ClocktowerChatAccessContext context) {
        return ClocktowerChatConstants.GROUP_PUBLIC.equals(context.groupKey())
                || ClocktowerChatConstants.CONVERSATION_PUBLIC.equals(context.conversationType());
    }

    private boolean isPrivateConversation(ClocktowerChatAccessContext context) {
        return ClocktowerChatConstants.CONVERSATION_PRIVATE.equals(context.conversationType());
    }

    private boolean isSpectatorChannel(ClocktowerChatAccessContext context) {
        return ClocktowerChatConstants.GROUP_SPECTATOR.equals(context.groupKey())
                || ClocktowerChatConstants.CONVERSATION_SPECTATOR.equals(context.conversationType());
    }

    private boolean isSystemChannel(ClocktowerChatAccessContext context) {
        return ClocktowerChatConstants.GROUP_SYSTEM.equals(context.groupKey())
                || ClocktowerChatConstants.CONVERSATION_SYSTEM.equals(context.conversationType());
    }
}
