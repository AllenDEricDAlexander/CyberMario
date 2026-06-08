package top.egon.mario.agent.tools.arxiv.dto;

import org.springframework.ai.tool.annotation.ToolParam;

public record ArxivSearchRequest(
        @ToolParam(description = "arXiv 查询语句，例如：all:LLM agent, ti:\"tool calling\", cat:cs.AI")
        String query,
        @ToolParam(description = "返回论文数量，建议 3 到 10", required = false)
        Integer maxResults
) {
}
