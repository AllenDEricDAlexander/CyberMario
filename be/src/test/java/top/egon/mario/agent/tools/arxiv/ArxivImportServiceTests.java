package top.egon.mario.agent.tools.arxiv;

import org.junit.jupiter.api.Test;
import reactor.core.scheduler.Schedulers;
import top.egon.mario.agent.tools.arxiv.dto.ArxivImportJob;
import top.egon.mario.agent.tools.arxiv.dto.ArxivPaper;
import top.egon.mario.agent.tools.arxiv.po.ArxivToolLogPo;
import top.egon.mario.agent.tools.arxiv.po.enums.ArxivToolLogStatus;
import top.egon.mario.agent.tools.arxiv.repository.ArxivToolLogRepository;
import top.egon.mario.rag.dto.response.RagIngestionJobResponse;
import top.egon.mario.rag.po.RagKnowledgeBasePo;
import top.egon.mario.rag.po.enums.RagIngestionJobStatus;
import top.egon.mario.rag.po.enums.RagIngestionStep;
import top.egon.mario.rag.repository.RagKnowledgeBaseRepository;
import top.egon.mario.rag.service.RagDocumentService;
import top.egon.mario.rag.service.bootstrap.SuperAdminArxivKnowledgeBaseBootstrap;
import top.egon.mario.rbac.po.RolePo;
import top.egon.mario.rbac.po.UserPo;
import top.egon.mario.rbac.po.UserRolePo;
import top.egon.mario.rbac.repository.RoleRepository;
import top.egon.mario.rbac.repository.UserRepository;
import top.egon.mario.rbac.repository.UserRoleRepository;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies arXiv import queueing executes PDF download and RAG ingestion.
 */
class ArxivImportServiceTests {

    @Test
    void importPapersQueuesAndRunsImportIntoSuperAdminKnowledgeBase() throws Exception {
        RagKnowledgeBaseRepository knowledgeBaseRepository = mock(RagKnowledgeBaseRepository.class);
        ArxivToolLogService logService = mock(ArxivToolLogService.class);
        ArxivToolLogRepository logRepository = mock(ArxivToolLogRepository.class);
        RagDocumentService documentService = mock(RagDocumentService.class);
        ArxivPdfDownloader pdfDownloader = mock(ArxivPdfDownloader.class);
        SuperAdminArxivKnowledgeBaseBootstrap bootstrap = mock(SuperAdminArxivKnowledgeBaseBootstrap.class);
        UserRepository userRepository = mock(UserRepository.class);
        RoleRepository roleRepository = mock(RoleRepository.class);
        UserRoleRepository userRoleRepository = mock(UserRoleRepository.class);
        ArxivImportService service = new ArxivImportService(knowledgeBaseRepository, logService, logRepository,
                documentService, pdfDownloader, bootstrap, userRepository, roleRepository,
                userRoleRepository, Schedulers.immediate());
        RagKnowledgeBasePo knowledgeBase = new RagKnowledgeBasePo();
        knowledgeBase.setId(12L);
        knowledgeBase.setCode("super-admin-arxiv");
        knowledgeBase.setName("Super Admin arXiv");
        ArxivToolLogPo log = new ArxivToolLogPo();
        log.setId(7L);
        ArxivPaper paper = paper();
        Path pdf = Files.createTempFile("arxiv-import-service-", ".pdf");
        when(knowledgeBaseRepository.findByCodeAndDeletedFalse("super-admin-arxiv"))
                .thenReturn(Optional.of(knowledgeBase));
        when(logRepository.findFirstByKnowledgeBaseIdAndEntryIdAndStatusOrderByIdDesc(
                12L, paper.entryId(), ArxivToolLogStatus.IMPORT_SUCCESS))
                .thenReturn(Optional.empty());
        when(logService.createImportLog("request-1", 8L, "luigi", "cat:cs.AI", 12L, paper)).thenReturn(log);
        when(pdfDownloader.download(paper)).thenReturn(pdf);
        when(documentService.importArxivPdf(eq(12L), eq(pdf), eq("Agentic Retrieval.pdf"), any(RbacPrincipal.class)))
                .thenReturn(new RagIngestionJobResponse(34L, 21L, 12L, RagIngestionJobStatus.SUCCESS,
                        RagIngestionStep.DONE, 100, 1, 1, 0, null, Instant.now(), Instant.now(), Instant.now()));

        List<ArxivImportJob> jobs = service.importPapers("request-1", 8L, "luigi", "cat:cs.AI", List.of(paper));

        assertThat(jobs).hasSize(1);
        assertThat(jobs.getFirst().status()).isEqualTo("IMPORT_PENDING");
        verify(logService).markImportRunning(log);
        verify(documentService).importArxivPdf(eq(12L), eq(pdf), eq("Agentic Retrieval.pdf"), any(RbacPrincipal.class));
        verify(logService).markImportSuccess(log, 21L, 34L);
    }

    @Test
    void importPapersRebuildsSuperAdminKnowledgeBaseWhenMissing() {
        RagKnowledgeBaseRepository knowledgeBaseRepository = mock(RagKnowledgeBaseRepository.class);
        ArxivToolLogService logService = mock(ArxivToolLogService.class);
        ArxivToolLogRepository logRepository = mock(ArxivToolLogRepository.class);
        RagDocumentService documentService = mock(RagDocumentService.class);
        ArxivPdfDownloader pdfDownloader = mock(ArxivPdfDownloader.class);
        SuperAdminArxivKnowledgeBaseBootstrap bootstrap = mock(SuperAdminArxivKnowledgeBaseBootstrap.class);
        UserRepository userRepository = mock(UserRepository.class);
        RoleRepository roleRepository = mock(RoleRepository.class);
        UserRoleRepository userRoleRepository = mock(UserRoleRepository.class);
        ArxivImportService service = new ArxivImportService(knowledgeBaseRepository, logService, logRepository,
                documentService, pdfDownloader, bootstrap, userRepository, roleRepository,
                userRoleRepository, Schedulers.immediate());
        RagKnowledgeBasePo knowledgeBase = new RagKnowledgeBasePo();
        knowledgeBase.setId(12L);
        knowledgeBase.setCode("super-admin-arxiv");
        knowledgeBase.setName("Super Admin arXiv");
        ArxivPaper paper = paper();
        when(knowledgeBaseRepository.findByCodeAndDeletedFalse("super-admin-arxiv"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(knowledgeBase));
        when(logRepository.findFirstByKnowledgeBaseIdAndEntryIdAndStatusOrderByIdDesc(
                12L, paper.entryId(), ArxivToolLogStatus.IMPORT_SUCCESS))
                .thenReturn(Optional.of(successLog()));

        List<ArxivImportJob> jobs = service.importPapers("request-1", 8L, "luigi", "cat:cs.AI", List.of(paper));

        verify(bootstrap).bootstrap();
        assertThat(jobs).hasSize(1);
        assertThat(jobs.getFirst().status()).isEqualTo("IMPORT_SKIPPED");
    }

    @Test
    void importPapersUsesSuperAdminUserAsFallbackOwnerWhenRequestUserIsMissing() throws Exception {
        RagKnowledgeBaseRepository knowledgeBaseRepository = mock(RagKnowledgeBaseRepository.class);
        ArxivToolLogService logService = mock(ArxivToolLogService.class);
        ArxivToolLogRepository logRepository = mock(ArxivToolLogRepository.class);
        RagDocumentService documentService = mock(RagDocumentService.class);
        ArxivPdfDownloader pdfDownloader = mock(ArxivPdfDownloader.class);
        SuperAdminArxivKnowledgeBaseBootstrap bootstrap = mock(SuperAdminArxivKnowledgeBaseBootstrap.class);
        UserRepository userRepository = mock(UserRepository.class);
        RoleRepository roleRepository = mock(RoleRepository.class);
        UserRoleRepository userRoleRepository = mock(UserRoleRepository.class);
        ArxivImportService service = new ArxivImportService(knowledgeBaseRepository, logService, logRepository,
                documentService, pdfDownloader, bootstrap, userRepository, roleRepository,
                userRoleRepository, Schedulers.immediate());
        RagKnowledgeBasePo knowledgeBase = new RagKnowledgeBasePo();
        knowledgeBase.setId(12L);
        knowledgeBase.setCode("super-admin-arxiv");
        knowledgeBase.setName("Super Admin arXiv");
        ArxivToolLogPo log = new ArxivToolLogPo();
        log.setId(7L);
        ArxivPaper paper = paper();
        Path pdf = Files.createTempFile("arxiv-import-service-", ".pdf");
        UserPo admin = new UserPo();
        admin.setId(99L);
        admin.setUsername("admin");
        RolePo role = new RolePo();
        role.setId(3L);
        role.setRoleCode("SUPER_ADMIN");
        UserRolePo userRole = new UserRolePo();
        userRole.setUserId(99L);
        userRole.setRoleId(3L);
        when(knowledgeBaseRepository.findByCodeAndDeletedFalse("super-admin-arxiv"))
                .thenReturn(Optional.of(knowledgeBase));
        when(logRepository.findFirstByKnowledgeBaseIdAndEntryIdAndStatusOrderByIdDesc(
                12L, paper.entryId(), ArxivToolLogStatus.IMPORT_SUCCESS))
                .thenReturn(Optional.empty());
        when(logService.createImportLog("request-1", null, null, "cat:cs.AI", 12L, paper)).thenReturn(log);
        when(pdfDownloader.download(paper)).thenReturn(pdf);
        when(roleRepository.findByRoleCodeAndDeletedFalse("SUPER_ADMIN")).thenReturn(Optional.of(role));
        when(userRoleRepository.findByRoleId(3L)).thenReturn(List.of(userRole));
        when(userRepository.findByIdAndDeletedFalse(99L)).thenReturn(Optional.of(admin));
        when(documentService.importArxivPdf(eq(12L), eq(pdf), eq("Agentic Retrieval.pdf"), any(RbacPrincipal.class)))
                .thenReturn(new RagIngestionJobResponse(34L, 21L, 12L, RagIngestionJobStatus.SUCCESS,
                        RagIngestionStep.DONE, 100, 1, 1, 0, null, Instant.now(), Instant.now(), Instant.now()));

        service.importPapers("request-1", null, null, "cat:cs.AI", List.of(paper));

        verify(documentService).importArxivPdf(eq(12L), eq(pdf), eq("Agentic Retrieval.pdf"),
                argThat(principal -> principal.userId().equals(99L) && principal.roleCodes().contains("SUPER_ADMIN")));
    }

    private ArxivToolLogPo successLog() {
        ArxivToolLogPo log = new ArxivToolLogPo();
        log.setDocumentId(21L);
        log.setRagIngestionJobId(34L);
        return log;
    }

    private ArxivPaper paper() {
        return new ArxivPaper(
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
    }

}
