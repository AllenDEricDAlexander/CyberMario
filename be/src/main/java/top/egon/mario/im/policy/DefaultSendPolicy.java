package top.egon.mario.im.policy;

import org.springframework.stereotype.Component;
import top.egon.mario.im.po.enums.ImConversationType;

@Component
public class DefaultSendPolicy implements SendPolicy {

    @Override
    public String contextType() {
        return DEFAULT_CONTEXT_TYPE;
    }

    @Override
    public boolean canSend(ImAccessContext context) {
        if (context == null || !context.authenticated() || !context.activeConversation() || context.activeGlobalMute()) {
            return false;
        }
        if (context.conversationType() == ImConversationType.CHANNEL_MAIN) {
            return context.activeChannelSurface() && canActiveMemberSend(context);
        }
        if (context.conversationType() == ImConversationType.GROUP) {
            return context.activeGroupSurface() && canActiveMemberSend(context);
        }
        if (context.conversationType() == ImConversationType.DM) {
            return context.activeDmPairSurface() && context.dmPairParticipant() && !context.dmPairFrozen();
        }
        return false;
    }

    private boolean canActiveMemberSend(ImAccessContext context) {
        return context.activeMembership() && !context.activeMemberMute() && !context.activeBan();
    }
}
