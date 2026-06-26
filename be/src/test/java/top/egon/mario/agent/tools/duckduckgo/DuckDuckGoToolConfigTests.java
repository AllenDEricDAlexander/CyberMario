package top.egon.mario.agent.tools.duckduckgo;

import com.alibaba.cloud.ai.toolcalling.duckduckgo.DuckDuckGoQueryNewsService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Verifies the DuckDuckGo starter service is exposed to the agent as a project tool callback.
 */
class DuckDuckGoToolConfigTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(DuckDuckGoToolConfig.class);

    @Test
    void searchDuckDuckGoNewsToolCallbackUsesStableNameAndDescription() {
        DuckDuckGoToolConfig config = new DuckDuckGoToolConfig();
        DuckDuckGoQueryNewsService service = mock(DuckDuckGoQueryNewsService.class);

        ToolCallback callback = config.searchDuckDuckGoNewsToolCallback(service);

        assertThat(callback.getToolDefinition().name()).isEqualTo(DuckDuckGoToolConfig.SEARCH_DUCKDUCKGO_NEWS_TOOL);
        assertThat(callback.getToolDefinition().description()).contains("[LOCAL runtime tool]", "DuckDuckGo", "news", "recent");
        assertThat(callback.getToolDefinition().inputSchema()).contains("\"q\"");
    }

    @Test
    void duckDuckGoToolCallbackBacksOffWhenStarterServiceIsDisabled() {
        contextRunner
                .withPropertyValues("spring.ai.alibaba.toolcalling.duckduckgo.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ToolCallback.class);
                });
    }

    @Test
    void duckDuckGoToolCallbackIsCreatedWhenStarterServiceExists() {
        contextRunner
                .withPropertyValues("spring.ai.alibaba.toolcalling.duckduckgo.enabled=true")
                .withUserConfiguration(DuckDuckGoServiceConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ToolCallback.class);
                    assertThat(context.getBean(ToolCallback.class).getToolDefinition().name())
                            .isEqualTo(DuckDuckGoToolConfig.SEARCH_DUCKDUCKGO_NEWS_TOOL);
                });
    }

    @Configuration
    static class DuckDuckGoServiceConfiguration {

        @Bean
        DuckDuckGoQueryNewsService duckDuckGoQueryNewsService() {
            return mock(DuckDuckGoQueryNewsService.class);
        }
    }

}
