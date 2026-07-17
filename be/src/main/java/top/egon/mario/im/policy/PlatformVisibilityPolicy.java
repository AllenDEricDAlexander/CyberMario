package top.egon.mario.im.policy;

import org.springframework.stereotype.Component;
import top.egon.mario.im.po.enums.ImConversationType;

@Component
public class PlatformVisibilityPolicy implements VisibilityPolicy {

    public static final String PLATFORM_CONTEXT_TYPE = "PLATFORM";

    @Override
    public String contextType() {
        return PLATFORM_CONTEXT_TYPE;
    }

    @Override
    public boolean canRead(ImAccessContext context) {
        if (context == null || !context.authenticated() || !context.activeConversation()) {
            return false;
        }
        if (ImConversationType.CHANNEL_MAIN.equals(context.conversationType())) {
            return context.activeChannelSurface() && context.activeMembership();
        }
        if (ImConversationType.GROUP.equals(context.conversationType())) {
            return context.activeGroupSurface() && context.activeMembership();
        }
        if (ImConversationType.DM.equals(context.conversationType())) {
            return context.activeDmPairSurface() && context.dmPairParticipant();
        }
        return false;
    }
}
