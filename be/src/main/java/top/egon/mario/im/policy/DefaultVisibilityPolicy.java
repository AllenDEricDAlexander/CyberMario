package top.egon.mario.im.policy;

import org.springframework.stereotype.Component;
import top.egon.mario.im.po.enums.ImConversationType;

@Component
public class DefaultVisibilityPolicy implements VisibilityPolicy {

    @Override
    public String contextType() {
        return DEFAULT_CONTEXT_TYPE;
    }

    @Override
    public boolean canRead(ImAccessContext context) {
        if (context == null || !context.activeConversation()) {
            return false;
        }
        if (context.conversationType() == ImConversationType.CHANNEL_MAIN) {
            return context.activeChannelSurface();
        }
        if (context.conversationType() == ImConversationType.GROUP) {
            return context.activeGroupSurface() && context.activeMembership();
        }
        if (context.conversationType() == ImConversationType.DM) {
            return context.activeDmPairSurface() && context.dmPairParticipant();
        }
        return false;
    }
}
