package top.egon.mario.agent.tools.duckduckgo;

import com.alibaba.cloud.ai.toolcalling.duckduckgo.DuckDuckGoConstants;
import com.alibaba.cloud.ai.toolcalling.duckduckgo.DuckDuckGoQueryNewsService;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes DuckDuckGo news search to the conversation agent.
 */
@Configuration
public class DuckDuckGoToolConfig {

    public static final String SEARCH_DUCKDUCKGO_NEWS_TOOL = "searchDuckDuckGoNews";

    /**
     * Creates the tool callback consumed by ReactAgent.
     */
    @Bean
    @ConditionalOnProperty(prefix = DuckDuckGoConstants.CONFIG_PREFIX, name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public ToolCallback searchDuckDuckGoNewsToolCallback(DuckDuckGoQueryNewsService duckDuckGoQueryNewsService) {
        return FunctionToolCallback
                .builder(SEARCH_DUCKDUCKGO_NEWS_TOOL, duckDuckGoQueryNewsService)
                .description("""
                        Search DuckDuckGo news for recent public information and timely web updates.
                        Use this when users ask for latest news, recent events or time-sensitive context.
                        """)
                .inputType(DuckDuckGoQueryNewsService.DuckDuckGoQueryNewsRequest.class)
                .build();
    }

}
