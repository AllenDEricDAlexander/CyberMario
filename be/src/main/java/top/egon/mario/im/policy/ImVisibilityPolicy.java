package top.egon.mario.im.policy;

import top.egon.mario.im.context.ImContext;

public interface ImVisibilityPolicy {

    boolean supports(String contextType);

    boolean canRead(ImContext context);
}
