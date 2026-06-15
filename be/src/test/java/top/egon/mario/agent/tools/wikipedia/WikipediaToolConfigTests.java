package top.egon.mario.agent.tools.wikipedia;

import com.alibaba.cloud.ai.toolcalling.wikipedia.WikipediaService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies the Wikipedia starter service is exposed to the agent as a project tool callback.
 */
class WikipediaToolConfigTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(WikipediaToolConfig.class);

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

    @Test
    void wikipediaToolCallbackBacksOffWhenStarterServiceIsDisabled() {
        contextRunner
                .withPropertyValues("spring.ai.alibaba.toolcalling.wikipedia.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ToolCallback.class);
                });
    }

    @Test
    void wikipediaToolCallbackIsCreatedWhenStarterServiceExists() {
        contextRunner
                .withPropertyValues("spring.ai.alibaba.toolcalling.wikipedia.enabled=true")
                .withUserConfiguration(WikipediaServiceConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ToolCallback.class);
                    assertThat(context.getBean(ToolCallback.class).getToolDefinition().name())
                            .isEqualTo(WikipediaToolConfig.SEARCH_WIKIPEDIA_TOOL);
                });
    }

    private static WikipediaService.Response response() {
        return new WikipediaService.Response("找到 0 个相关页面", List.of(), "en");
    }

    @Configuration
    static class WikipediaServiceConfiguration {

        @Bean
        WikipediaService wikipediaService() {
            return mock(WikipediaService.class);
        }
    }

}
