package top.egon.mario.agent.tools.arxiv;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import top.egon.mario.agent.tools.arxiv.dto.ArxivPaper;
import top.egon.mario.agent.tools.arxiv.po.ArxivToolLogPo;
import top.egon.mario.agent.tools.arxiv.po.enums.ArxivToolLogStatus;
import top.egon.mario.agent.tools.arxiv.repository.ArxivToolLogRepository;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies arXiv tool log persistence mapping.
 */
class ArxivToolLogServiceTests {

    @Test
    void createSearchLogPersistsRequestMetadata() {
        ArxivToolLogRepository repository = mock(ArxivToolLogRepository.class);
        when(repository.save(any(ArxivToolLogPo.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ArxivToolLogService service = new ArxivToolLogService(repository);

        ArxivToolLogPo log = service.createSearchLog("request-1", 8L, "luigi",
                "cat:cs.AI", 5, true, 3, 12L);

        ArgumentCaptor<ArxivToolLogPo> captor = ArgumentCaptor.forClass(ArxivToolLogPo.class);
        verify(repository).save(captor.capture());
        ArxivToolLogPo saved = captor.getValue();
        assertThat(saved).isSameAs(log);
        assertThat(saved.getRequestId()).isEqualTo("request-1");
        assertThat(saved.getRequestUserId()).isEqualTo(8L);
        assertThat(saved.getRequestUsername()).isEqualTo("luigi");
        assertThat(saved.getQuery()).isEqualTo("cat:cs.AI");
        assertThat(saved.getMaxResults()).isEqualTo(5);
        assertThat(saved.isIncludeFullText()).isTrue();
        assertThat(saved.getResultCount()).isEqualTo(3);
        assertThat(saved.getKnowledgeBaseId()).isEqualTo(12L);
        assertThat(saved.getStatus()).isEqualTo(ArxivToolLogStatus.SEARCHED);
        assertThat(saved.getStartedAt()).isNotNull();
        assertThat(saved.getFinishedAt()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void createImportLogPersistsPaperMetadata() {
        ArxivToolLogRepository repository = mock(ArxivToolLogRepository.class);
        when(repository.save(any(ArxivToolLogPo.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ArxivToolLogService service = new ArxivToolLogService(repository);
        ArxivPaper paper = new ArxivPaper(
                "http://arxiv.org/abs/2401.00001",
                "Agentic Retrieval",
                List.of("Mario", "Luigi"),
                "summary",
                LocalDateTime.parse("2024-01-01T00:00:00"),
                LocalDateTime.parse("2024-01-02T00:00:00"),
                List.of("cs.AI", "cs.CL"),
                "cs.AI",
                "http://arxiv.org/pdf/2401.00001",
                "10.1234/demo",
                "comment",
                null
        );

        ArxivToolLogPo log = service.createImportLog("request-1", 8L, "luigi", "cat:cs.AI", 12L, paper);

        ArgumentCaptor<ArxivToolLogPo> captor = ArgumentCaptor.forClass(ArxivToolLogPo.class);
        verify(repository).save(captor.capture());
        ArxivToolLogPo saved = captor.getValue();
        assertThat(saved).isSameAs(log);
        assertThat(saved.getEntryId()).isEqualTo("http://arxiv.org/abs/2401.00001");
        assertThat(saved.getTitle()).isEqualTo("Agentic Retrieval");
        assertThat(saved.getPdfUrl()).isEqualTo("http://arxiv.org/pdf/2401.00001");
        assertThat(saved.getStatus()).isEqualTo(ArxivToolLogStatus.IMPORT_PENDING);
    }

    @Test
    void markImportSuccessAndFailureUpdateStatus() {
        ArxivToolLogRepository repository = mock(ArxivToolLogRepository.class);
        when(repository.save(any(ArxivToolLogPo.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ArxivToolLogService service = new ArxivToolLogService(repository);
        ArxivToolLogPo log = new ArxivToolLogPo();

        service.markImportSuccess(log, 21L, 34L);

        assertThat(log.getStatus()).isEqualTo(ArxivToolLogStatus.IMPORT_SUCCESS);
        assertThat(log.getDocumentId()).isEqualTo(21L);
        assertThat(log.getRagIngestionJobId()).isEqualTo(34L);
        assertThat(log.getFinishedAt()).isNotNull();

        service.markImportFailed(log, "x".repeat(1200));

        assertThat(log.getStatus()).isEqualTo(ArxivToolLogStatus.IMPORT_FAILED);
        assertThat(log.getErrorMessage()).hasSize(1024);
    }

}
