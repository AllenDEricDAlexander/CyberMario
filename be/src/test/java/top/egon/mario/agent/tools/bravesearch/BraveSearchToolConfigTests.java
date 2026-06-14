package top.egon.mario.agent.tools.bravesearch;

import com.alibaba.cloud.ai.toolcalling.bravesearch.BraveSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Verifies Brave Search is exposed to the agent as a project tool callback.
 */
class BraveSearchToolConfigTests {

    @Test
    void searchBraveWebToolCallbackUsesStableNameAndDescription() {
        BraveSearchToolConfig config = new BraveSearchToolConfig();
        BraveSearchService service = mock(BraveSearchService.class);

        ToolCallback callback = config.searchBraveWebToolCallback(service);

        assertThat(callback.getToolDefinition().name()).isEqualTo(BraveSearchToolConfig.SEARCH_BRAVE_WEB_TOOL);
        assertThat(callback.getToolDefinition().description()).contains("Brave", "web", "current");
        assertThat(callback.getToolDefinition().inputSchema()).contains("\"query\"");
    }

}
