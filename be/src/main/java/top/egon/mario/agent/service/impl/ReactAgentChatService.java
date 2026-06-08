package top.egon.mario.agent.service.impl;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import top.egon.mario.agent.service.ChatAgentService;
import top.egon.mario.pojo.response.ChatResponse;

import java.util.ArrayList;
import java.util.UUID;
import java.util.List;

/**
 * Reactive adapter around Spring AI Alibaba's synchronous ReactAgent API.
 */
public class ReactAgentChatService implements ChatAgentService {

    private final ReactAgent agent;
    private static final int RESPONSE_CHUNK_SIZE = 48;

    public ReactAgentChatService(ReactAgent agent) {
        this.agent = agent;
    }

    /**
     * Runs the blocking agent call on a bounded elastic scheduler and preserves conversation memory by thread id.
     */
    @Override
    public Flux<ChatResponse> chat(String message, String threadId) {
        String conversationThreadId = resolveThreadId(threadId);
        return Mono.fromCallable(() -> {
                    RunnableConfig config = RunnableConfig.builder()
                            .threadId(conversationThreadId)
                            .build();
                    AssistantMessage response = agent.call(message, config);
                    return response.getText();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(responseText -> splitToChunks(responseText).map(chunk -> new ChatResponse(conversationThreadId, chunk)));
    }

    private Flux<String> splitToChunks(String responseText) {
        return Flux.fromIterable(splitResponseText(responseText));
    }

    private List<String> splitResponseText(String responseText) {
        if (!StringUtils.hasText(responseText)) {
            return List.of("");
        }

        List<String> chunks = new ArrayList<>();
        for (int start = 0; start < responseText.length(); start += RESPONSE_CHUNK_SIZE) {
            int end = Math.min(start + RESPONSE_CHUNK_SIZE, responseText.length());
            chunks.add(responseText.substring(start, end));
        }
        return chunks;
    }

    private String resolveThreadId(String threadId) {
        if (StringUtils.hasText(threadId)) {
            return threadId.trim();
        }
        return UUID.randomUUID().toString();
    }

}
