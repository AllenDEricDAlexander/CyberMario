package top.egon.mario.im.platform.dto;

import java.util.List;

public record PlatformBootstrapView(
        PlatformUserView currentUser,
        List<PlatformConversationView> conversations,
        long unreadTotal,
        long pendingFriendRequestCount) {
}
