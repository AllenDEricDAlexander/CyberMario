package top.egon.mario.agent.tools.arxiv.dto;

import org.springframework.ai.tool.annotation.ToolParam;

public record ArxivSearchRequest(
        @ToolParam(description = "arXiv 查询语句，例如：all:LLM agent, ti:\"tool calling\", cat:cs.AI")
        String query,
        @ToolParam(description = "返回论文数量，建议 3 到 10", required = false)
        Integer maxResults,
        @ToolParam(description = "是否读取 PDF 全文预览。默认 false，只有用户明确要求阅读全文时才开启。", required = false)
        Boolean includeFullText,
        @ToolParam(description = "全文预览最大字符数。默认由系统配置控制。", required = false)
        Integer fullTextMaxChars
) {
}
