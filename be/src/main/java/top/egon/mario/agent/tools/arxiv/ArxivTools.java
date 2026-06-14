package top.egon.mario.agent.tools.arxiv;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import top.egon.mario.agent.tools.arxiv.dto.ArxivImportJob;
import top.egon.mario.agent.tools.arxiv.dto.ArxivPaper;
import top.egon.mario.agent.tools.arxiv.dto.ArxivSearchRequest;
import top.egon.mario.agent.tools.arxiv.dto.ArxivSearchResponse;
import top.egon.mario.common.api.TraceContext;
import top.egon.mario.common.utils.LogUtil;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Spring AI function tool for arXiv search, full-text preview and background collection.
 */
@Slf4j
@Component
public class ArxivTools implements Function<ArxivSearchRequest, ArxivSearchResponse> {

    private final ArxivPaperService paperService;
    private final ArxivToolLogService logService;
    private final ArxivImportService importService;
    private final ArxivToolUserContext userContext;

    public ArxivTools(ArxivPaperService paperService, ArxivToolLogService logService,
                      ArxivImportService importService, ArxivToolUserContext userContext) {
        this.paperService = paperService;
        this.logService = logService;
        this.importService = importService;
        this.userContext = userContext;
    }

    @Override
    public ArxivSearchResponse apply(ArxivSearchRequest request) {
        if (request == null || !StringUtils.hasText(request.query())) {
            return new ArxivSearchResponse(false, "arXiv 查询语句不能为空。", null, List.of(), "", List.of());
        }
        String query = request.query().trim();
        int limit = paperService.limit(request.maxResults());
        boolean includeFullText = Boolean.TRUE.equals(request.includeFullText());
        RbacPrincipal principal = userContext.get();
        Long userId = principal == null ? null : principal.userId();
        String username = principal == null ? null : principal.username();
        String requestId = TraceContext.newTraceId();

        LogUtil.info(log).log("arxiv tool search started, queryLength={}, limit={}, includeFullText={}",
                query.length(), limit, includeFullText);

        List<ArxivPaper> papers = paperService.searchSummaries(query, limit);
        if (includeFullText && !papers.isEmpty()) {
            String preview = paperService.readFullTextPreview(query, 1);
            papers = withFullTextPreview(papers, preview);
        }
        logService.createSearchLog(requestId, userId, username, query, limit, includeFullText, papers.size(), null);
        List<ArxivImportJob> importJobs = importService.importPapers(requestId, userId, username, query, papers);

        if (papers.isEmpty()) {
            LogUtil.info(log).log("arxiv tool search completed, resultCount=0");
            return new ArxivSearchResponse(true, "未检索到相关 arXiv 论文。", query, List.of(),
                    "未检索到相关 arXiv 论文。query = " + query, importJobs);
        }

        String content = papers.stream()
                .map(this::format)
                .collect(Collectors.joining("\n\n---\n\n"));
        LogUtil.info(log).log("arxiv tool search completed, resultCount={}, importJobCount={}",
                papers.size(), importJobs.size());
        return new ArxivSearchResponse(true, "已检索到 arXiv 论文，并已提交后台收录任务。", query, papers, content, importJobs);
    }

    private List<ArxivPaper> withFullTextPreview(List<ArxivPaper> papers, String preview) {
        if (!StringUtils.hasText(preview)) {
            return papers;
        }
        ArxivPaper first = papers.getFirst();
        ArxivPaper updated = new ArxivPaper(first.entryId(), first.title(), first.authors(), first.summary(),
                first.published(), first.updated(), first.categories(), first.primaryCategory(), first.pdfUrl(),
                first.doi(), first.comment(), preview);
        return java.util.stream.Stream.concat(java.util.stream.Stream.of(updated), papers.stream().skip(1)).toList();
    }

    private String format(ArxivPaper paper) {
        return """
                Title: %s
                Authors: %s
                Published: %s
                Updated: %s
                Category: %s
                PDF: %s
                Entry: %s
                
                Summary:
                %s%s
                """.formatted(
                value(paper.title()),
                String.join(", ", paper.authors()),
                value(paper.published()),
                value(paper.updated()),
                value(paper.primaryCategory()),
                value(paper.pdfUrl()),
                value(paper.entryId()),
                value(paper.summary()),
                fullTextBlock(paper)
        );
    }

    private String fullTextBlock(ArxivPaper paper) {
        if (!StringUtils.hasText(paper.fullTextPreview())) {
            return "";
        }
        return "\n\nFull Text Preview:\n" + paper.fullTextPreview();
    }

    private String value(Object value) {
        return value == null ? "" : value.toString();
    }
}
