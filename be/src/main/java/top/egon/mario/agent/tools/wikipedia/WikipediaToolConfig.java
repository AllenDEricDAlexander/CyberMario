package top.egon.mario.agent.tools.wikipedia;

import com.alibaba.cloud.ai.toolcalling.wikipedia.WikipediaService;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes Wikipedia search to the conversation agent.
 */
@Configuration
public class WikipediaToolConfig {

    public static final String SEARCH_WIKIPEDIA_TOOL = "searchWikipedia";

    /**
     * Creates the tool callback consumed by ReactAgent.
     */
    @Bean
    public ToolCallback searchWikipediaToolCallback(WikipediaService wikipediaService) {
        return FunctionToolCallback
                .builder(SEARCH_WIKIPEDIA_TOOL, wikipediaService)
                .description("""
                        Search Wikipedia for encyclopedic background, definitions and stable reference context.
                        Use this for concepts, organizations, people, technologies and historical background.
                        """)
                .inputType(WikipediaService.Request.class)
                .build();
    }

}
