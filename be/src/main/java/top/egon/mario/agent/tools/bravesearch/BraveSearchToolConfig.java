package top.egon.mario.agent.tools.bravesearch;

import com.alibaba.cloud.ai.toolcalling.bravesearch.BraveSearchConstants;
import com.alibaba.cloud.ai.toolcalling.bravesearch.BraveSearchService;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes Brave Search to the conversation agent.
 */
@Configuration
public class BraveSearchToolConfig {

    public static final String SEARCH_BRAVE_WEB_TOOL = "searchBraveWeb";

    /**
     * Creates the tool callback consumed by ReactAgent.
     */
    @Bean
    @ConditionalOnProperty(prefix = BraveSearchConstants.CONFIG_PREFIX, name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public ToolCallback searchBraveWebToolCallback(BraveSearchService braveSearchService) {
        return FunctionToolCallback
                .builder(SEARCH_BRAVE_WEB_TOOL, braveSearchService)
                .description("""
                        Search Brave web results for current public web information and source discovery.
                        Use this when users ask for current facts, recent web pages or broad web research.
                        """)
                .inputType(BraveSearchService.Request.class)
                .build();
    }

}
