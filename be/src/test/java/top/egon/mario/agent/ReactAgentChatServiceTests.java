package top.egon.mario.agent;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import top.egon.mario.agent.service.impl.ReactAgentChatService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class ReactAgentChatServiceTests {

    @Test
    void chatUsesThreadIdAndReturnsAgentResponse() throws Exception {
        ReactAgent agent = mock(ReactAgent.class);
        given(agent.stream(eq("你好"), any(RunnableConfig.class)))
                .willAnswer(invocation -> {
                    RunnableConfig config = invocation.getArgument(1);
                    assertThat(config.threadId()).contains("thread-1");
                    return Flux.empty();
                });
        ReactAgentChatService chatService = new ReactAgentChatService(agent, Schedulers.immediate());

        StepVerifier.create(chatService.chat("你好", "thread-1"))
                .verifyComplete();
    }

    @Test
    void chatCreatesThreadIdWhenRequestDoesNotProvideOne() throws Exception {
        ReactAgent agent = mock(ReactAgent.class);
        given(agent.stream(eq("你好"), any(RunnableConfig.class)))
                .willAnswer(invocation -> {
                    RunnableConfig config = invocation.getArgument(1);
                    assertThat(config.threadId()).isPresent();
                    return Flux.empty();
                });
        ReactAgentChatService chatService = new ReactAgentChatService(agent, Schedulers.immediate());

        StepVerifier.create(chatService.chat("你好", " "))
                .verifyComplete();
    }

}
