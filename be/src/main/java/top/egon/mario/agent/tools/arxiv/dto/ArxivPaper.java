package top.egon.mario.agent.tools.arxiv.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Structured arXiv paper metadata returned by the agent tool.
 */
public record ArxivPaper(
        String entryId,
        String title,
        List<String> authors,
        String summary,
        LocalDateTime published,
        LocalDateTime updated,
        List<String> categories,
        String primaryCategory,
        String pdfUrl,
        String doi,
        String comment,
        String fullTextPreview
) {
    public ArxivPaper {
        authors = authors == null ? List.of() : List.copyOf(authors);
        categories = categories == null ? List.of() : List.copyOf(categories);
    }
}
