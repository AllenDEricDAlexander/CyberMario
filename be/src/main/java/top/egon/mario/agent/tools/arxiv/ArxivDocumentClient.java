package top.egon.mario.agent.tools.arxiv;

import org.springframework.ai.document.Document;

import java.util.List;

/**
 * Boundary around the arXiv document reader for testable tool services.
 */
@FunctionalInterface
public interface ArxivDocumentClient {

    List<Document> read(String query, int maxResults);

}
