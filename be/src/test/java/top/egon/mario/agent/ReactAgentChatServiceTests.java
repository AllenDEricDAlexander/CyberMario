package top.egon.mario.agent;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import reactor.test.StepVerifier;
import top.egon.mario.agent.service.impl.ReactAgentChatService;
import top.egon.mario.pojo.response.ChatResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class ReactAgentChatServiceTests {

    @Test
    void chatUsesThreadIdAndReturnsAgentResponse() throws Exception {
        ReactAgent agent = mock(ReactAgent.class);
        given(agent.call(eq("你好"), any(RunnableConfig.class))).willAnswer(invocation -> {
            RunnableConfig config = invocation.getArgument(1);
            assertThat(config.threadId()).contains("thread-1");
            return new AssistantMessage("你好，我是 CyberMario。");
                });
        ReactAgentChatService chatService = new ReactAgentChatService(agent);

        StepVerifier.create(chatService.chat("你好", "thread-1"))
                .expectNext(new ChatResponse("thread-1", "你好，我是 CyberMario。"))
                .verifyComplete();
    }

    @Test
    void chatCreatesThreadIdWhenRequestDoesNotProvideOne() throws Exception {
        ReactAgent agent = mock(ReactAgent.class);
        given(agent.call(eq("你好"), any(RunnableConfig.class))).willAnswer(invocation -> {
            RunnableConfig config = invocation.getArgument(1);
            assertThat(config.threadId()).isPresent();
            return new AssistantMessage("你好，我是 CyberMario。");
        });
        ReactAgentChatService chatService = new ReactAgentChatService(agent);

        StepVerifier.create(chatService.chat("你好", " "))
                .assertNext(response -> {
                    assertThat(response.threadId()).isNotBlank();
                    assertThat(response.message()).isEqualTo("你好，我是 CyberMario。");
                })
                .verifyComplete();
    }

}
