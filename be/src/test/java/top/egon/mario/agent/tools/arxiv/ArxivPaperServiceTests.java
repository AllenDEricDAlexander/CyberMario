package top.egon.mario.agent.tools.arxiv;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import top.egon.mario.agent.tools.arxiv.config.ArxivToolProperties;
import top.egon.mario.agent.tools.arxiv.dto.ArxivPaper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies arXiv document metadata is converted into stable tool DTOs.
 */
class ArxivPaperServiceTests {

    @Test
    void searchSummariesMapsDocumentMetadataToPaperDto() {
        ArxivPaperService service = new ArxivPaperService(
                (query, maxResults) -> List.of(document()),
                (query, maxResults) -> List.of(),
                new ArxivToolProperties(5, 10, 12_000)
        );

        List<ArxivPaper> papers = service.searchSummaries("cat:cs.AI", 3);

        assertThat(papers).hasSize(1);
        ArxivPaper paper = papers.getFirst();
        assertThat(paper.entryId()).isEqualTo("http://arxiv.org/abs/2401.00001");
        assertThat(paper.title()).isEqualTo("Agentic Retrieval");
        assertThat(paper.authors()).containsExactly("Mario", "Luigi");
        assertThat(paper.summary()).isEqualTo("summary text");
        assertThat(paper.published()).isEqualTo(LocalDateTime.parse("2024-01-01T00:00:00"));
        assertThat(paper.updated()).isEqualTo(LocalDateTime.parse("2024-01-02T00:00:00"));
        assertThat(paper.categories()).containsExactly("cs.AI", "cs.CL");
        assertThat(paper.primaryCategory()).isEqualTo("cs.AI");
        assertThat(paper.pdfUrl()).isEqualTo("http://arxiv.org/pdf/2401.00001");
        assertThat(paper.doi()).isEqualTo("10.1234/demo");
        assertThat(paper.comment()).isEqualTo("accepted");
    }

    @Test
    void readFullTextReturnsBoundedPreview() {
        ArxivPaperService service = new ArxivPaperService(
                (query, maxResults) -> List.of(),
                (query, maxResults) -> List.of(new Document("x".repeat(20), Map.of())),
                new ArxivToolProperties(5, 10, 8)
        );

        String preview = service.readFullTextPreview("id:2401.00001", 1);

        assertThat(preview).isEqualTo("xxxxxxxx");
    }

    private Document document() {
        return new Document("summary text", Map.ofEntries(
                entry("entry_id", "http://arxiv.org/abs/2401.00001"),
                entry("title", "Agentic Retrieval"),
                entry("authors", List.of("Mario", "Luigi")),
                entry("summary", "summary text"),
                entry("published", LocalDateTime.parse("2024-01-01T00:00:00")),
                entry("updated", LocalDateTime.parse("2024-01-02T00:00:00")),
                entry("categories", List.of("cs.AI", "cs.CL")),
                entry("primary_category", "cs.AI"),
                entry("pdf_url", "http://arxiv.org/pdf/2401.00001"),
                entry("doi", "10.1234/demo"),
                entry("comment", "accepted")
        ));
    }

}
