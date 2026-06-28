package top.egon.mario.clocktower.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import top.egon.mario.im.policy.ImAccessContext;
import top.egon.mario.im.policy.VisibilityPolicy;

@Component
@RequiredArgsConstructor
public class ClocktowerVisibilityPolicy implements VisibilityPolicy {

    private final ClocktowerImAdapter adapter;
    private final ClocktowerChatPolicy policy;

    @Override
    public String contextType() {
        return ClocktowerChatConstants.CONTEXT_TYPE;
    }

    @Override
    public boolean canRead(ImAccessContext context) {
        if (context == null || !context.authenticated() || !context.activeConversation()
                || !context.activeSurface()) {
            return false;
        }
        return adapter.accessContext(context)
                .map(policy::canRead)
                .orElse(false);
    }
}
