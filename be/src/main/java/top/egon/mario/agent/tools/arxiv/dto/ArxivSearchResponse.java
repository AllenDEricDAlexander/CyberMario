package top.egon.mario.agent.tools.arxiv.dto;

import java.util.List;

public record ArxivSearchResponse(
        boolean success,
        String message,
        String query,
        List<ArxivPaper> papers,
        String content,
        List<ArxivImportJob> importJobs
) {
    public ArxivSearchResponse {
        papers = papers == null ? List.of() : List.copyOf(papers);
        importJobs = importJobs == null ? List.of() : List.copyOf(importJobs);
    }

    public ArxivSearchResponse(String content) {
        this(true, content, null, List.of(), content, List.of());
    }
}
