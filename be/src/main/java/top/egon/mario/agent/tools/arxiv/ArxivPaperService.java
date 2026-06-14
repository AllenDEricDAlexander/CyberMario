package top.egon.mario.agent.tools.arxiv;

import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import top.egon.mario.agent.tools.arxiv.config.ArxivToolProperties;
import top.egon.mario.agent.tools.arxiv.dto.ArxivPaper;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Searches arXiv summaries and reads bounded full-text previews.
 */
@Service
public class ArxivPaperService {

    private final ArxivDocumentClient summaryClient;
    private final ArxivDocumentClient fullTextClient;
    private final ArxivToolProperties properties;

    @Autowired
    public ArxivPaperService(DefaultArxivDocumentClients documentClients, ArxivToolProperties properties) {
        this(documentClients.summaries(), documentClients.fullText(), properties);
    }

    public ArxivPaperService(ArxivDocumentClient summaryClient, ArxivDocumentClient fullTextClient,
                             ArxivToolProperties properties) {
        this.summaryClient = summaryClient;
        this.fullTextClient = fullTextClient;
        this.properties = properties;
    }

    public List<ArxivPaper> searchSummaries(String query, Integer maxResults) {
        int limit = limit(maxResults);
        return summaryClient.read(query, limit).stream()
                .map(this::toPaper)
                .toList();
    }

    public String readFullTextPreview(String query, Integer maxResults) {
        int limit = limit(maxResults);
        String text = fullTextClient.read(query, limit).stream()
                .map(Document::getText)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("");
        return truncate(text, properties.fullTextPreviewChars());
    }

    public int limit(Integer maxResults) {
        int requested = maxResults == null ? properties.defaultMaxResults() : maxResults;
        return Math.min(Math.max(requested, 1), properties.maxResults());
    }

    private ArxivPaper toPaper(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        return new ArxivPaper(
                text(metadata, "entry_id"),
                text(metadata, "title"),
                texts(metadata, "authors"),
                text(metadata, "summary", document.getText()),
                time(metadata, "published"),
                time(metadata, "updated"),
                texts(metadata, "categories"),
                text(metadata, "primary_category"),
                text(metadata, "pdf_url"),
                text(metadata, "doi"),
                text(metadata, "comment"),
                null
        );
    }

    private String text(Map<String, Object> metadata, String key) {
        return text(metadata, key, null);
    }

    private String text(Map<String, Object> metadata, String key, String fallback) {
        Object value = metadata.get(key);
        if (value == null) {
            return fallback;
        }
        return value.toString();
    }

    private List<String> texts(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                    .map(String::valueOf)
                    .toList();
        }
        if (value == null) {
            return List.of();
        }
        return List.of(value.toString());
    }

    private LocalDateTime time(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value instanceof LocalDateTime time) {
            return time;
        }
        if (value == null) {
            return null;
        }
        return LocalDateTime.parse(value.toString());
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

}
