package top.egon.mario.agent.tools.arxiv;

import com.alibaba.cloud.ai.reader.arxiv.ArxivDocumentReader;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Default arXiv document readers backed by Spring AI Alibaba.
 */
@Component
public class DefaultArxivDocumentClients {

    public ArxivDocumentClient summaries() {
        return (query, maxResults) -> new ArxivDocumentReader(query, maxResults).getSummaries();
    }

    public ArxivDocumentClient fullText() {
        return (query, maxResults) -> {
            List<Document> documents = new ArxivDocumentReader(query, maxResults).get();
            return documents == null ? List.of() : documents;
        };
    }

}
