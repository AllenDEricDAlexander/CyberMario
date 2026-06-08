package top.egon.mario;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.ai.chat.model.ChatModel;

@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
class BeApplicationTests {

    @MockitoBean
    private ChatModel chatModel;

    @Test
    void contextLoads() {
    }

}
