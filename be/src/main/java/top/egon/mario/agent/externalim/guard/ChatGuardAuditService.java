package top.egon.mario.agent.externalim.guard;

import top.egon.mario.agent.externalim.model.ChatInvocation;

public interface ChatGuardAuditService {

    void record(ChatInvocation invocation, ChatGuardResult result, String requestId, String traceId);
}
