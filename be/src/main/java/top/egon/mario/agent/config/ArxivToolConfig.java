package top.egon.mario.agent.config;

import lombok.extern.slf4j.Slf4j;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import top.egon.mario.agent.tools.arxiv.ArxivTools;
import top.egon.mario.agent.tools.arxiv.dto.ArxivSearchRequest;

@Configuration
@Slf4j
public class ArxivToolConfig {
    public static final String SEARCH_ARXIV_TOOL = "searchArxiv";

    @Bean
    public ToolCallback searchArxivToolCallback() {
        return FunctionToolCallback
                .builder(SEARCH_ARXIV_TOOL, new ArxivTools())
                .description("search arXiv paper.when user ask paper、arXiv or you need get some specialist info，use this tool.")
                .inputType(ArxivSearchRequest.class)
                .build();
    }
}
