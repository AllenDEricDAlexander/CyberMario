package top.egon.mario.agent.externalim.guard;

import top.egon.mario.agent.externalim.model.ChatInvocation;

public record ChatGuardModelInput(
        ChatInvocation invocation,
        String currentGroupWindow,
        String requestId,
        String traceId
) {
}
