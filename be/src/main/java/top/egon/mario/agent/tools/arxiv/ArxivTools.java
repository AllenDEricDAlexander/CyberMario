package top.egon.mario.agent.tools.arxiv;

import com.alibaba.cloud.ai.reader.arxiv.ArxivDocumentReader;
import org.springframework.ai.document.Document;
import top.egon.mario.agent.tools.arxiv.dto.ArxivSearchRequest;
import top.egon.mario.agent.tools.arxiv.dto.ArxivSearchResponse;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ArxivTools implements Function<ArxivSearchRequest, ArxivSearchResponse> {
    @Override
    public ArxivSearchResponse apply(ArxivSearchRequest request) {
        int limit = request.maxResults() == null ? 5 : Math.min(Math.max(request.maxResults(), 1), 10);

        ArxivDocumentReader reader = new ArxivDocumentReader(request.query(), limit);

        // getSummaries 只取摘要，比较轻；get 会下载并解析 PDF，比较重
        List<Document> documents = reader.getSummaries();

        if (documents.isEmpty()) {
            return new ArxivSearchResponse("未检索到相关 arXiv 论文。query = " + request.query());
        }

        String content = documents.stream().map(this::format).collect(Collectors.joining("\n\n---\n\n"));

        return new ArxivSearchResponse(content);
    }

    private String format(Document document) {
        Map<String, Object> metadata = document.getMetadata();

        return """
                Title: %s
                Authors: %s
                Published: %s
                Category: %s
                PDF: %s
                
                Summary:
                %s
                """.formatted(get(metadata, "title"), get(metadata, "authors"), get(metadata, "published"), get(metadata, "primary_category"), get(metadata, "pdf_url"), document.getText());
    }

    private String get(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        return value == null ? "" : value.toString();
    }
}
