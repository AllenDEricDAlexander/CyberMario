package top.egon.mario.agent.config;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import top.egon.mario.agent.tools.arxiv.ArxivTools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Verifies arXiv search is exposed to the agent as a local runtime tool callback.
 */
class ArxivToolConfigTests {

    @Test
    void searchArxivToolCallbackUsesStableNameAndLocalDescription() {
        ArxivToolConfig config = new ArxivToolConfig();
        ArxivTools arxivTools = mock(ArxivTools.class);

        ToolCallback callback = config.searchArxivToolCallback(arxivTools);

        assertThat(callback.getToolDefinition().name()).isEqualTo(ArxivToolConfig.SEARCH_ARXIV_TOOL);
        assertThat(callback.getToolDefinition().description()).contains("[LOCAL runtime tool]", "arXiv",
                "academic");
        assertThat(callback.getToolDefinition().inputSchema()).contains("\"query\"");
    }

}
