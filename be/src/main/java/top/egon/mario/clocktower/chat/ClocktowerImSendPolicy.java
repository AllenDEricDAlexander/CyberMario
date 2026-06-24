package top.egon.mario.clocktower.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import top.egon.mario.im.context.ImContext;
import top.egon.mario.im.policy.ImSendPolicy;

@Component
@RequiredArgsConstructor
public class ClocktowerImSendPolicy implements ImSendPolicy {

    private final ClocktowerChatConversationResolver resolver;
    private final ClocktowerChatPolicy policy;

    @Override
    public boolean supports(String contextType) {
        return ClocktowerChatConstants.CONTEXT_TYPE.equals(contextType);
    }

    @Override
    public boolean canSend(ImContext context) {
        return resolver.resolve(context)
                .map(chatContext -> policy.canSend(resolver.accessContext(chatContext,
                        context.principal().userId(), context.activeConversationMember(), null)))
                .orElse(false);
    }
}
