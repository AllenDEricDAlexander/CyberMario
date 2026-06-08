package top.egon.mario.agent.config;


import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import top.egon.mario.agent.tools.arxiv.ArxivTools;
import top.egon.mario.agent.tools.arxiv.dto.ArxivSearchRequest;

@Configuration
public class ArxivToolConfig {
    public static final String SEARCH_ARXIV_TOOL = "searchArxiv";

    @Bean
    public ToolCallback searchArxivToolCallback() {
        return FunctionToolCallback
                .builder(SEARCH_ARXIV_TOOL, new ArxivTools())
                .description("搜索 arXiv 学术论文。当用户询问论文、preprint、arXiv、LLM paper、AI paper、机器学习研究时，使用这个工具。")
                .inputType(ArxivSearchRequest.class)
                .build();
    }
}
