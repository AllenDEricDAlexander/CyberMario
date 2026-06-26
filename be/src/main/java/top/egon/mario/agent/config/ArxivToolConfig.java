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
    public ToolCallback searchArxivToolCallback(ArxivTools arxivTools) {
        return FunctionToolCallback
                .builder(SEARCH_ARXIV_TOOL, arxivTools)
                .description("""
                        [LOCAL runtime tool] Search arXiv papers by query and return structured summaries, links and optional bounded full-text preview.
                        Use this when users ask for papers, arXiv, academic references or specialist research context.
                        Every returned paper is queued for background collection into the protected super-admin arXiv knowledge base.
                        """)
                .inputType(ArxivSearchRequest.class)
                .build();
    }
}
