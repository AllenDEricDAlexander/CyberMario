package top.egon.mario.agent.externalim.flow;

import top.egon.mario.agent.externalim.model.ChatInvocation;
import top.egon.mario.pojo.request.ChatRequest;
import top.egon.mario.rbac.service.security.RbacPrincipal;

public interface ChatInvocationPolicy {

    ChatInvocation fromWeb(ChatRequest request, RbacPrincipal principal);

    ChatInvocation requireExternal(ChatInvocation invocation);
}
