package top.egon.mario.agent.tools.bravesearch;

import com.alibaba.cloud.ai.toolcalling.bravesearch.BraveSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Verifies Brave Search is exposed to the agent as a project tool callback.
 */
class BraveSearchToolConfigTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(BraveSearchToolConfig.class);

    @Test
    void searchBraveWebToolCallbackUsesStableNameAndDescription() {
        BraveSearchToolConfig config = new BraveSearchToolConfig();
        BraveSearchService service = mock(BraveSearchService.class);

        ToolCallback callback = config.searchBraveWebToolCallback(service);

        assertThat(callback.getToolDefinition().name()).isEqualTo(BraveSearchToolConfig.SEARCH_BRAVE_WEB_TOOL);
        assertThat(callback.getToolDefinition().description()).contains("[LOCAL runtime tool]", "Brave", "web", "current");
        assertThat(callback.getToolDefinition().inputSchema()).contains("\"query\"");
    }

    @Test
    void braveSearchToolCallbackBacksOffWhenStarterServiceIsDisabled() {
        contextRunner
                .withPropertyValues("spring.ai.alibaba.toolcalling.bravesearch.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(BraveSearchToolConfig.SEARCH_BRAVE_WEB_TOOL);
                });
    }

    @Test
    void braveSearchToolCallbackIsCreatedWhenStarterServiceExists() {
        contextRunner
                .withPropertyValues("spring.ai.alibaba.toolcalling.bravesearch.enabled=true")
                .withUserConfiguration(BraveSearchServiceConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ToolCallback.class);
                    assertThat(context.getBean(ToolCallback.class).getToolDefinition().name())
                            .isEqualTo(BraveSearchToolConfig.SEARCH_BRAVE_WEB_TOOL);
                });
    }

    @Configuration
    static class BraveSearchServiceConfiguration {

        @Bean
        BraveSearchService braveSearchService() {
            return mock(BraveSearchService.class);
        }
    }

}
