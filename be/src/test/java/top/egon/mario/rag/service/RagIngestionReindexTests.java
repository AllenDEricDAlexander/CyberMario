package top.egon.mario.rag.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import top.egon.mario.rag.dto.response.RagIngestionJobResponse;
import top.egon.mario.rag.po.RagDocumentChunkPo;
import top.egon.mario.rag.po.RagFileObjectPo;
import top.egon.mario.rag.po.RagIngestionJobPo;
import top.egon.mario.rag.po.RagKnowledgeBasePo;
import top.egon.mario.rag.po.RagUserDocumentPo;
import top.egon.mario.rag.po.enums.RagDocumentSourceType;
import top.egon.mario.rag.po.enums.RagDocumentStatus;
import top.egon.mario.rag.po.enums.RagFileType;
import top.egon.mario.rag.po.enums.RagIngestionJobStatus;
import top.egon.mario.rag.po.enums.RagIngestionStep;
import top.egon.mario.rag.po.enums.RagStorageType;
import top.egon.mario.rag.repository.RagDocumentChunkRepository;
import top.egon.mario.rag.repository.RagFileObjectRepository;
import top.egon.mario.rag.repository.RagIngestionJobRepository;
import top.egon.mario.rag.repository.RagKnowledgeBaseRepository;
import top.egon.mario.rag.repository.RagUserDocumentRepository;
import top.egon.mario.rag.service.model.RagChunkCandidate;
import top.egon.mario.rbac.po.UserPo;
import top.egon.mario.rbac.po.enums.RbacStatus;
import top.egon.mario.rbac.repository.UserRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies re-indexing an existing document replaces old chunks safely.
 */
@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
class RagIngestionReindexTests {

    @Autowired
    private RagIngestionService ingestionService;
    @Autowired
    private RagKnowledgeBaseRepository knowledgeBaseRepository;
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
        knowledgeBaseRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void reindexReplacesExistingActiveChunksWithSameDocumentAndIndex() {
        UserPo user = new UserPo();
        user.setUsername("rag-user");
        user.setNickname("RAG User");
        user.setPasswordHash("password");
        user.setStatus(RbacStatus.ENABLED);
        user = userRepository.save(user);
        RagKnowledgeBasePo knowledgeBase = new RagKnowledgeBasePo();
        knowledgeBase.setCode("kb-reindex");
        knowledgeBase.setName("Reindex KB");
        knowledgeBase = knowledgeBaseRepository.save(knowledgeBase);
        RagFileObjectPo fileObject = new RagFileObjectPo();
        fileObject.setSha256("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        fileObject.setStorageType(RagStorageType.LOCAL);
        fileObject.setStorageKey("local/test.md");
        fileObject.setOriginalFilename("test.md");
        fileObject.setContentType("text/markdown");
        fileObject.setFileType(RagFileType.MD);
        fileObject.setFileSize(10);
        fileObject = fileObjectRepository.save(fileObject);
        RagUserDocumentPo document = new RagUserDocumentPo();
        document.setUserId(user.getId());
        document.setKnowledgeBaseId(knowledgeBase.getId());
        document.setFileObjectId(fileObject.getId());
        document.setDisplayName("test.md");
        document.setSourceType(RagDocumentSourceType.UPLOAD);
        document.setFileType(RagFileType.MD);
        document.setContentHash(fileObject.getSha256());
        document.setStatus(RagDocumentStatus.INDEXED);
        document = documentRepository.save(document);
        RagDocumentChunkPo oldChunk = new RagDocumentChunkPo();
        oldChunk.setDocumentId(document.getId());
        oldChunk.setKnowledgeBaseId(knowledgeBase.getId());
        oldChunk.setFileObjectId(fileObject.getId());
        oldChunk.setChunkIndex(0);
        oldChunk.setContent("old chunk");
        oldChunk.setContentPreview("old chunk");
        oldChunk.setTokenCount(2);
        oldChunk.setEnabled(true);
        chunkRepository.saveAndFlush(oldChunk);
        RagIngestionJobPo job = new RagIngestionJobPo();
        job.setDocumentId(document.getId());
        job.setKnowledgeBaseId(knowledgeBase.getId());
        job.setFileObjectId(fileObject.getId());
        job.setStatus(RagIngestionJobStatus.PENDING);
        job.setCurrentStep(RagIngestionStep.UPLOAD);
        job = jobRepository.save(job);
        when(documentParser.parse(any(RagFileObjectPo.class))).thenReturn("new chunk");
        when(textChunker.split("new chunk")).thenReturn(List.of(new RagChunkCandidate(0, "new chunk", 2)));

        RagIngestionJobResponse response = ingestionService.ingest(job.getId());

        assertThat(response.status()).isEqualTo(RagIngestionJobStatus.SUCCESS);
        assertThat(jobRepository.findById(job.getId()).orElseThrow().getStatus()).isEqualTo(RagIngestionJobStatus.SUCCESS);
        assertThat(documentRepository.findById(document.getId()).orElseThrow().getStatus()).isEqualTo(RagDocumentStatus.INDEXED);
        ArgumentCaptor<List<RagDocumentChunkPo>> deletedChunksCaptor = ArgumentCaptor.forClass(List.class);
        verify(vectorService).deleteChunks(deletedChunksCaptor.capture());
        assertThat(deletedChunksCaptor.getValue()).extracting(RagDocumentChunkPo::getId).containsExactly(oldChunk.getId());
        List<RagDocumentChunkPo> chunks = chunkRepository.findByDocumentIdAndDeletedFalseOrderByChunkIndexAsc(document.getId());
        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst().getContent()).isEqualTo("new chunk");
    }

}
