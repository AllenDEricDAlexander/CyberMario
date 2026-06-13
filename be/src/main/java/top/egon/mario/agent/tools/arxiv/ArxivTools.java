package top.egon.mario.agent.tools.arxiv;

import com.alibaba.cloud.ai.reader.arxiv.ArxivDocumentReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import top.egon.mario.agent.tools.arxiv.dto.ArxivSearchRequest;
import top.egon.mario.agent.tools.arxiv.dto.ArxivSearchResponse;
import top.egon.mario.common.utils.LogUtil;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class ArxivTools implements Function<ArxivSearchRequest, ArxivSearchResponse> {
    @Override
    public ArxivSearchResponse apply(ArxivSearchRequest request) {
        int limit = request.maxResults() == null ? 5 : Math.min(Math.max(request.maxResults(), 1), 10);
        LogUtil.info(log).log("arxiv tool search started, queryLength={}, limit={}",
                request.query() == null ? 0 : request.query().length(), limit);

        ArxivDocumentReader reader = new ArxivDocumentReader(request.query(), limit);

        // getSummaries 只取摘要，比较轻；get 会下载并解析 PDF，比较重
        List<Document> documents = reader.getSummaries();

        if (documents.isEmpty()) {
            LogUtil.info(log).log("arxiv tool search completed, resultCount=0");
            return new ArxivSearchResponse("未检索到相关 arXiv 论文。query = " + request.query());
        }

        String content = documents.stream().map(this::format).collect(Collectors.joining("\n\n---\n\n"));

        LogUtil.info(log).log("arxiv tool search completed, resultCount={}", documents.size());
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
