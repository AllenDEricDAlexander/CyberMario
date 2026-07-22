package top.egon.mario.agent.externalim.guard;

import top.egon.mario.agent.externalim.model.ChatInvocation;

import java.util.concurrent.CompletableFuture;

public interface ChatGuardService {

    CompletableFuture<ChatGuardResult> decide(ChatInvocation invocation, String currentGroupWindow,
                                              String requestId, String traceId);
}
