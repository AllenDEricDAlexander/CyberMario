package top.egon.mario.agent.tools.arxiv;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import top.egon.mario.agent.tools.arxiv.config.ArxivToolProperties;
import top.egon.mario.agent.tools.arxiv.dto.ArxivPaper;
import top.egon.mario.agent.tools.arxiv.dto.ArxivSearchRequest;
import top.egon.mario.agent.tools.arxiv.dto.ArxivSearchResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the arXiv function tool returns papers and queues global imports.
 */
class ArxivToolsTests {

    private final ArxivToolUserContext userContext = new ArxivToolUserContext();

    @AfterEach
    void tearDown() {
        userContext.clear();
    }

    @Test
    void applySearchesSummariesLogsAndQueuesImportsForNormalUser() {
        StubPaperService paperService = new StubPaperService();
        StubLogService logService = new StubLogService();
        StubImportService importService = new StubImportService();
        ArxivTools tools = new ArxivTools(
                paperService,
                logService,
                importService,
                userContext
        );
        userContext.set(new RbacPrincipal(8L, "luigi", Set.of("CHAT_BASIC"), Set.of("api:chat:stream"), "v1"));

        ArxivSearchResponse response = tools.apply(new ArxivSearchRequest("cat:cs.AI", 3, false, null));

        assertThat(response.success()).isTrue();
        assertThat(response.papers()).containsExactly(paperService.paper);
        assertThat(response.importJobs()).hasSize(1);
        assertThat(response.content()).contains("Agentic Retrieval");
        assertThat(logService.searchUserId).isEqualTo(8L);
        assertThat(logService.searchUsername).isEqualTo("luigi");
        assertThat(importService.importedPapers).containsExactly(paperService.paper);
        assertThat(response.message()).contains("后台收录");
    }

    @Test
    void applyRejectsBlankQueryWithoutImporting() {
        StubPaperService paperService = new StubPaperService();
        StubLogService logService = new StubLogService();
        StubImportService importService = new StubImportService();
        ArxivTools tools = new ArxivTools(
                paperService,
                logService,
                importService,
                userContext
        );

        ArxivSearchResponse response = tools.apply(new ArxivSearchRequest(" ", 3, false, null));

        assertThat(response.success()).isFalse();
        assertThat(response.papers()).isEmpty();
        assertThat(importService.importedPapers).isEmpty();
        assertThat(logService.searchCreated).isFalse();
    }

    private static final class StubPaperService extends ArxivPaperService {

        private final ArxivPaper paper = new ArxivPaper(
                "http://arxiv.org/abs/2401.00001",
                "Agentic Retrieval",
                List.of("Mario"),
                "summary",
                LocalDateTime.parse("2024-01-01T00:00:00"),
                LocalDateTime.parse("2024-01-02T00:00:00"),
                List.of("cs.AI"),
                "cs.AI",
                "http://arxiv.org/pdf/2401.00001",
                null,
                null,
                null
        );

        private StubPaperService() {
            super((query, maxResults) -> List.of(), (query, maxResults) -> List.of(),
                    new ArxivToolProperties(5, 10, 12_000));
        }

        @Override
        public List<ArxivPaper> searchSummaries(String query, Integer maxResults) {
            return List.of(paper);
        }
    }

    private static final class StubLogService extends ArxivToolLogService {

        private boolean searchCreated;
        private Long searchUserId;
        private String searchUsername;

        private StubLogService() {
            super(null);
        }

        @Override
        public top.egon.mario.agent.tools.arxiv.po.ArxivToolLogPo createSearchLog(String requestId, Long userId,
                                                                                  String username, String query,
                                                                                  int maxResults,
                                                                                  boolean includeFullText,
                                                                                  int resultCount,
                                                                                  Long knowledgeBaseId) {
            searchCreated = true;
            searchUserId = userId;
            searchUsername = username;
            return new top.egon.mario.agent.tools.arxiv.po.ArxivToolLogPo();
        }
    }

    private static final class StubImportService extends ArxivImportService {

        private final List<ArxivPaper> importedPapers = new ArrayList<>();

        private StubImportService() {
            super(null, null, null, null, null, null, null, null, null, null);
        }

        @Override
        public List<top.egon.mario.agent.tools.arxiv.dto.ArxivImportJob> importPapers(String requestId,
                                                                                      Long userId,
                                                                                      String username,
                                                                                      String query,
                                                                                      List<ArxivPaper> papers) {
            importedPapers.addAll(papers);
            return papers.stream()
                    .map(paper -> new top.egon.mario.agent.tools.arxiv.dto.ArxivImportJob(
                            paper.entryId(), null, null, "IMPORT_PENDING", "已提交后台收录"))
                    .toList();
        }
    }

}
