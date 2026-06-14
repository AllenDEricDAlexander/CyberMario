package top.egon.mario.rag.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import top.egon.mario.rag.po.RagKnowledgeBasePo;
import top.egon.mario.rag.repository.RagDocumentChunkRepository;
import top.egon.mario.rag.repository.RagFileObjectRepository;
import top.egon.mario.rag.repository.RagIngestionJobRepository;
import top.egon.mario.rag.repository.RagKnowledgeBaseRepository;
import top.egon.mario.rag.repository.RagKnowledgeBaseUserRepository;
import top.egon.mario.rag.repository.RagUserDocumentRepository;
import top.egon.mario.rag.service.bootstrap.SuperAdminArxivKnowledgeBaseBootstrap;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies system-owned RAG knowledge bases cannot be removed through business APIs.
 */
@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
class RagKnowledgeBaseProtectionTests {

    @Autowired
    private RagKnowledgeBaseService knowledgeBaseService;
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

    @BeforeEach
    void setUp() {
        chunkRepository.deleteAll();
        jobRepository.deleteAll();
        documentRepository.deleteAll();
        fileObjectRepository.deleteAll();
        knowledgeBaseUserRepository.deleteAll();
        knowledgeBaseRepository.deleteAll();
    }

    @Test
    void deleteRejectsSuperAdminArxivKnowledgeBase() {
        RagKnowledgeBasePo knowledgeBase = new RagKnowledgeBasePo();
        knowledgeBase.setCode(SuperAdminArxivKnowledgeBaseBootstrap.SUPER_ADMIN_ARXIV_KNOWLEDGE_BASE_CODE);
        knowledgeBase.setName("Super Admin arXiv");
        knowledgeBase = knowledgeBaseRepository.save(knowledgeBase);

        Long knowledgeBaseId = knowledgeBase.getId();
        assertThatThrownBy(() -> knowledgeBaseService.delete(knowledgeBaseId, superAdminPrincipal()))
                .isInstanceOf(RagException.class)
                .extracting("code")
                .isEqualTo("RAG_KB_PROTECTED");
        assertThat(knowledgeBaseRepository.findByIdAndDeletedFalse(knowledgeBaseId)).isPresent();
    }

    @Test
    void pageHidesSuperAdminArxivKnowledgeBaseFromNonSuperAdmin() {
        RagKnowledgeBasePo protectedKnowledgeBase = new RagKnowledgeBasePo();
        protectedKnowledgeBase.setCode(SuperAdminArxivKnowledgeBaseBootstrap.SUPER_ADMIN_ARXIV_KNOWLEDGE_BASE_CODE);
        protectedKnowledgeBase.setName("Super Admin arXiv");
        knowledgeBaseRepository.save(protectedKnowledgeBase);
        RagKnowledgeBasePo normalKnowledgeBase = new RagKnowledgeBasePo();
        normalKnowledgeBase.setCode("team-research");
        normalKnowledgeBase.setName("Team Research");
        knowledgeBaseRepository.save(normalKnowledgeBase);

        assertThat(knowledgeBaseService.page(PageRequest.of(0, 20), normalPrincipal()).getContent())
                .extracting("code")
                .containsExactly("team-research");
        assertThat(knowledgeBaseService.page(PageRequest.of(0, 20), superAdminPrincipal()).getContent())
                .extracting("code")
                .contains(SuperAdminArxivKnowledgeBaseBootstrap.SUPER_ADMIN_ARXIV_KNOWLEDGE_BASE_CODE);
    }

    private RbacPrincipal superAdminPrincipal() {
        return new RbacPrincipal(1L, "admin", Set.of("SUPER_ADMIN"), Set.of("api:rag:knowledge-base:*"), "permission-v1");
    }

    private RbacPrincipal normalPrincipal() {
        return new RbacPrincipal(2L, "luigi", Set.of("RAG_USER"), Set.of("api:rag:knowledge-base:collection"), "permission-v1");
    }

}
