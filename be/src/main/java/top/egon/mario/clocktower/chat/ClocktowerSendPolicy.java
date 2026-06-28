package top.egon.mario.clocktower.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import top.egon.mario.im.policy.ImAccessContext;
import top.egon.mario.im.policy.SendPolicy;

@Component
@RequiredArgsConstructor
public class ClocktowerSendPolicy implements SendPolicy {

    private final ClocktowerImAdapter adapter;
    private final ClocktowerChatPolicy policy;

    @Override
    public String contextType() {
        return ClocktowerChatConstants.CONTEXT_TYPE;
    }

    @Override
    public boolean canSend(ImAccessContext context) {
        if (context == null || !context.authenticated() || !context.activeConversation()
                || !context.activeSurface() || context.activeGlobalMute()
                || context.activeBan() || context.activeMemberMute()) {
            return false;
        }
        return adapter.accessContext(context)
                .map(policy::canSend)
                .orElse(false);
    }
}
