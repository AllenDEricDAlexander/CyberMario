package top.egon.mario.rag.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import top.egon.mario.rag.dto.response.RagIngestionJobResponse;
import top.egon.mario.rag.po.RagFileObjectPo;
import top.egon.mario.rag.po.RagKnowledgeBasePo;
import top.egon.mario.rag.po.enums.RagDocumentSourceType;
import top.egon.mario.rag.po.enums.RagFileType;
import top.egon.mario.rag.repository.RagDocumentChunkRepository;
import top.egon.mario.rag.repository.RagFileObjectRepository;
import top.egon.mario.rag.repository.RagIngestionJobRepository;
import top.egon.mario.rag.repository.RagKnowledgeBaseRepository;
import top.egon.mario.rag.repository.RagKnowledgeBaseUserRepository;
import top.egon.mario.rag.repository.RagUserDocumentRepository;
import top.egon.mario.rag.service.model.RagChunkCandidate;
import top.egon.mario.rbac.po.UserPo;
import top.egon.mario.rbac.po.enums.RbacStatus;
import top.egon.mario.rbac.repository.OneTimeTokenRepository;
import top.egon.mario.rbac.repository.UserRepository;
import top.egon.mario.rbac.repository.UserRoleRepository;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Verifies arXiv PDF imports reuse the RAG document ingestion pipeline.
 */
@SpringBootTest(properties = {
        "spring.ai.dashscope.api-key=test-api-key",
        "mario.rag.storage.local-root=/private/tmp/cybermario-rag-test"
})
class RagDocumentArxivImportTests {

    @Autowired
    private RagDocumentService documentService;
    @Autowired
    private RagKnowledgeBaseRepository knowledgeBaseRepository;
    @Autowired
    private RagKnowledgeBaseUserRepository knowledgeBaseUserRepository;
    @Autowired
    private RagFileObjectRepository fileObjectRepository;
    @Autowired
    private RagUserDocumentRepository documentRepository;
    @Autowired
    private RagIngestionJobRepository jobRepository;
    @Autowired
    private RagDocumentChunkRepository chunkRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private OneTimeTokenRepository oneTimeTokenRepository;
    @Autowired
    private UserRoleRepository userRoleRepository;
    @MockitoBean
    private RagDocumentParser documentParser;
    @MockitoBean
    private RagTextChunker textChunker;
    @MockitoBean
    private RagVectorService vectorService;

    @BeforeEach
    void setUp() {
        chunkRepository.deleteAll();
        jobRepository.deleteAll();
        documentRepository.deleteAll();
        fileObjectRepository.deleteAll();
        knowledgeBaseUserRepository.deleteAll();
        knowledgeBaseRepository.deleteAll();
        userRoleRepository.deleteAll();
        oneTimeTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void importArxivPdfStoresDocumentWithArxivSourceTypeAndRunsIngestion() throws Exception {
        RagKnowledgeBasePo knowledgeBase = new RagKnowledgeBasePo();
        knowledgeBase.setCode("super-admin-arxiv");
        knowledgeBase.setName("Super Admin arXiv");
        knowledgeBase = knowledgeBaseRepository.save(knowledgeBase);
        Path pdf = Files.createTempFile("arxiv-import-", ".pdf");
        Files.writeString(pdf, "paper text");
        when(documentParser.parse(any(RagFileObjectPo.class))).thenReturn("paper text");
        when(textChunker.split("paper text")).thenReturn(List.of(new RagChunkCandidate(0, "paper text", 2)));
        UserPo admin = new UserPo();
        admin.setUsername("admin");
        admin.setNickname("Administrator");
        admin.setPasswordHash("password");
        admin.setStatus(RbacStatus.ENABLED);
        admin = userRepository.save(admin);

        RagIngestionJobResponse response = documentService.importArxivPdf(
                knowledgeBase.getId(), pdf, "Agentic Retrieval.pdf", superAdminPrincipal(admin.getId()));

        assertThat(response.documentId()).isNotNull();
        assertThat(response.id()).isNotNull();
        assertThat(documentRepository.findByIdAndDeletedFalse(response.documentId())).hasValueSatisfying(document -> {
            assertThat(document.getSourceType()).isEqualTo(RagDocumentSourceType.ARXIV);
            assertThat(document.getFileType()).isEqualTo(RagFileType.PDF);
            assertThat(document.getDisplayName()).isEqualTo("Agentic Retrieval.pdf");
        });
    }

    private RbacPrincipal superAdminPrincipal(Long userId) {
        return new RbacPrincipal(userId, "admin", Set.of("SUPER_ADMIN"), Set.of("api:rag:document:*"), "permission-v1");
    }

}
