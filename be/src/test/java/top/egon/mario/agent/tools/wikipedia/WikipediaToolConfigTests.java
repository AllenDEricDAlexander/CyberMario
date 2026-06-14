package top.egon.mario.agent.tools.wikipedia;

import com.alibaba.cloud.ai.toolcalling.wikipedia.WikipediaService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies the Wikipedia starter service is exposed to the agent as a project tool callback.
 */
class WikipediaToolConfigTests {

    @Test
    void searchWikipediaToolCallbackUsesStableNameAndDescription() {
        WikipediaToolConfig config = new WikipediaToolConfig();
        WikipediaService service = mock(WikipediaService.class);
        when(service.apply(any(WikipediaService.Request.class))).thenReturn(response());

        ToolCallback callback = config.searchWikipediaToolCallback(service);

        assertThat(callback.getToolDefinition().name()).isEqualTo(WikipediaToolConfig.SEARCH_WIKIPEDIA_TOOL);
        assertThat(callback.getToolDefinition().description()).contains("Wikipedia", "encyclopedic", "background");
        assertThat(callback.call("{\"query\":\"Spring AI\",\"limit\":1,\"includeContent\":false}"))
                .contains("pages")
                .contains("language");
    }

    private static WikipediaService.Response response() {
        return new WikipediaService.Response("找到 0 个相关页面", List.of(), "en");
    }

}
