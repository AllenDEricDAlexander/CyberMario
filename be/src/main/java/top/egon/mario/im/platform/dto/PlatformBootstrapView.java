package top.egon.mario.im.platform.dto;

import java.util.List;

public record PlatformBootstrapView(
        PlatformUserView currentUser,
        PlatformConversationView publicChannel,
        List<PlatformConversationView> conversations,
        long unreadTotal,
        long pendingFriendRequestCount) {
}
