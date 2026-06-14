package top.egon.mario.agent.tools.duckduckgo;

import com.alibaba.cloud.ai.toolcalling.duckduckgo.DuckDuckGoQueryNewsService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Verifies the DuckDuckGo starter service is exposed to the agent as a project tool callback.
 */
class DuckDuckGoToolConfigTests {

    @Test
    void searchDuckDuckGoNewsToolCallbackUsesStableNameAndDescription() {
        DuckDuckGoToolConfig config = new DuckDuckGoToolConfig();
        DuckDuckGoQueryNewsService service = mock(DuckDuckGoQueryNewsService.class);

        ToolCallback callback = config.searchDuckDuckGoNewsToolCallback(service);

        assertThat(callback.getToolDefinition().name()).isEqualTo(DuckDuckGoToolConfig.SEARCH_DUCKDUCKGO_NEWS_TOOL);
        assertThat(callback.getToolDefinition().description()).contains("DuckDuckGo", "news", "recent");
        assertThat(callback.getToolDefinition().inputSchema()).contains("\"q\"");
    }

}
