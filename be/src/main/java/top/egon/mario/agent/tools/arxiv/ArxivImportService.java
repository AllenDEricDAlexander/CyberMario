package top.egon.mario.agent.tools.arxiv;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import top.egon.mario.agent.tools.arxiv.dto.ArxivImportJob;
import top.egon.mario.agent.tools.arxiv.dto.ArxivPaper;
import top.egon.mario.agent.tools.arxiv.po.ArxivToolLogPo;
import top.egon.mario.agent.tools.arxiv.po.enums.ArxivToolLogStatus;
import top.egon.mario.agent.tools.arxiv.repository.ArxivToolLogRepository;
import top.egon.mario.common.utils.LogUtil;
import top.egon.mario.rag.dto.response.RagIngestionJobResponse;
import top.egon.mario.rag.repository.RagKnowledgeBaseRepository;
import top.egon.mario.rag.service.RagDocumentService;
import top.egon.mario.rag.service.bootstrap.SuperAdminArxivKnowledgeBaseBootstrap;
import top.egon.mario.rbac.po.RolePo;
import top.egon.mario.rbac.po.UserRolePo;
import top.egon.mario.rbac.repository.RoleRepository;
import top.egon.mario.rbac.repository.UserRepository;
import top.egon.mario.rbac.repository.UserRoleRepository;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Queues arXiv papers for import into the protected super-admin knowledge base.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ArxivImportService {

    private static final String SUPER_ADMIN_ROLE_CODE = "SUPER_ADMIN";

    private final RagKnowledgeBaseRepository knowledgeBaseRepository;
    private final ArxivToolLogService logService;
    private final ArxivToolLogRepository logRepository;
    private final RagDocumentService documentService;
    private final ArxivPdfDownloader pdfDownloader;
    private final SuperAdminArxivKnowledgeBaseBootstrap knowledgeBaseBootstrap;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final Scheduler blockingScheduler;

    public List<ArxivImportJob> importPapers(String requestId, Long userId, String username, String query,
                                             List<ArxivPaper> papers) {
        Long knowledgeBaseId = superAdminArxivKnowledgeBaseId();
        if (knowledgeBaseId == null) {
            return papers.stream()
                    .map(paper -> new ArxivImportJob(paper.entryId(), null, null,
                            "IMPORT_FAILED", "super-admin-arxiv 知识库不存在"))
                    .toList();
        }
        return papers.stream()
                .map(paper -> queueImport(requestId, userId, username, query, knowledgeBaseId, paper))
                .toList();
    }

    private Long superAdminArxivKnowledgeBaseId() {
        return knowledgeBaseRepository
                .findByCodeAndDeletedFalse(SuperAdminArxivKnowledgeBaseBootstrap.SUPER_ADMIN_ARXIV_KNOWLEDGE_BASE_CODE)
                .map(knowledgeBase -> knowledgeBase.getId())
                .orElseGet(() -> {
                    knowledgeBaseBootstrap.bootstrap();
                    return knowledgeBaseRepository
                            .findByCodeAndDeletedFalse(SuperAdminArxivKnowledgeBaseBootstrap.SUPER_ADMIN_ARXIV_KNOWLEDGE_BASE_CODE)
                            .map(knowledgeBase -> knowledgeBase.getId())
                            .orElse(null);
                });
    }

    private ArxivImportJob queueImport(String requestId, Long userId, String username, String query,
                                       Long knowledgeBaseId, ArxivPaper paper) {
        ArxivToolLogPo existing = logRepository.findFirstByKnowledgeBaseIdAndEntryIdAndStatusOrderByIdDesc(
                knowledgeBaseId, paper.entryId(), ArxivToolLogStatus.IMPORT_SUCCESS).orElse(null);
        ArxivToolLogPo log = logService.createImportLog(requestId, userId, username, query, knowledgeBaseId, paper);
        if (existing != null) {
            logService.markImportSkipped(log, existing.getDocumentId(), existing.getRagIngestionJobId());
            return new ArxivImportJob(paper.entryId(), existing.getDocumentId(), existing.getRagIngestionJobId(),
                    "IMPORT_SKIPPED", "论文已收录");
        }
        Mono.fromRunnable(() -> runImport(log, knowledgeBaseId, paper, userId, username))
                .subscribeOn(blockingScheduler)
                .subscribe();
        LogUtil.info(this.log).log("arxiv import queued, logId={}, entryId={}", log.getId(), paper.entryId());
        return new ArxivImportJob(paper.entryId(), null, null, "IMPORT_PENDING", "已提交后台收录");
    }

    private void runImport(ArxivToolLogPo log, Long knowledgeBaseId, ArxivPaper paper, Long userId, String username) {
        Path pdf = null;
        try {
            logService.markImportRunning(log);
            pdf = pdfDownloader.download(paper);
            RbacPrincipal principal = importPrincipal(userId, username);
            RagIngestionJobResponse response = documentService.importArxivPdf(knowledgeBaseId, pdf, filename(paper), principal);
            logService.markImportSuccess(log, response.documentId(), response.id());
        } catch (Exception e) {
            logService.markImportFailed(log, e.getMessage());
        } finally {
            deleteTemporaryFile(pdf);
        }
    }

    private RbacPrincipal importPrincipal(Long userId, String username) {
        Long ownerUserId = userId == null ? fallbackSuperAdminUserId() : userId;
        String ownerUsername = username == null ? SUPER_ADMIN_ROLE_CODE : username;
        return new RbacPrincipal(ownerUserId, ownerUsername,
                java.util.Set.of(SUPER_ADMIN_ROLE_CODE), java.util.Set.of("api:rag:document:*"), "arxiv-import");
    }

    private Long fallbackSuperAdminUserId() {
        RolePo role = roleRepository.findByRoleCodeAndDeletedFalse(SUPER_ADMIN_ROLE_CODE)
                .orElseThrow(() -> new IllegalStateException("super admin role is required for anonymous arXiv import"));
        return userRoleRepository.findByRoleId(role.getId()).stream()
                .map(UserRolePo::getUserId)
                .filter(userId -> userRepository.findByIdAndDeletedFalse(userId).isPresent())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("super admin user is required for anonymous arXiv import"));
    }

    private String filename(ArxivPaper paper) {
        String title = paper.title() == null || paper.title().isBlank() ? "arxiv-paper" : paper.title().trim();
        String cleanTitle = title.replaceAll("[\\\\/:*?\"<>|]", "_");
        return cleanTitle.endsWith(".pdf") ? cleanTitle : cleanTitle + ".pdf";
    }

    private void deleteTemporaryFile(Path pdf) {
        if (pdf == null) {
            return;
        }
        try {
            Files.deleteIfExists(pdf);
        } catch (Exception ignored) {
            // Temporary file cleanup must not fail the import result.
        }
    }

}
