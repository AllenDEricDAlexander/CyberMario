package top.egon.mario.im.policy;

import top.egon.mario.im.context.ImContext;

public interface ImSendPolicy {

    boolean supports(String contextType);

    boolean canSend(ImContext context);
}
