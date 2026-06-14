package top.egon.mario.rag.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import top.egon.mario.rag.dto.request.RetrievalSearchRequest;
import top.egon.mario.rag.dto.response.RagSearchMode;
import top.egon.mario.rag.dto.response.RetrievalSearchResponse;
import top.egon.mario.rag.dto.response.SourceReferenceResponse;
import top.egon.mario.rag.po.RagDocumentChunkPo;
import top.egon.mario.rag.po.RagFileObjectPo;
import top.egon.mario.rag.po.RagKnowledgeBasePo;
import top.egon.mario.rag.po.RagKnowledgeBaseUserPo;
import top.egon.mario.rag.po.RagUserDocumentPo;
import top.egon.mario.rag.po.enums.RagAccessLevel;
import top.egon.mario.rag.po.enums.RagDocumentSourceType;
import top.egon.mario.rag.po.enums.RagDocumentStatus;
import top.egon.mario.rag.po.enums.RagFileType;
import top.egon.mario.rag.po.enums.RagStorageType;
import top.egon.mario.rag.repository.RagDocumentChunkRepository;
import top.egon.mario.rag.repository.RagFileObjectRepository;
import top.egon.mario.rag.repository.RagIngestionJobRepository;
import top.egon.mario.rag.repository.RagKnowledgeBaseRepository;
import top.egon.mario.rag.repository.RagKnowledgeBaseUserRepository;
import top.egon.mario.rag.repository.RagUserDocumentRepository;
import top.egon.mario.rbac.po.UserPo;
import top.egon.mario.rbac.po.enums.RbacStatus;
import top.egon.mario.rbac.repository.UserRepository;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies hybrid retrieval combines vector, keyword and optional rerank stages.
 */
@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
class RagHybridRetrievalServiceTests {

    @Autowired
    private RagRetrievalService retrievalService;
    @Autowired
    private RagKnowledgeBaseRepository knowledgeBaseRepository;
    @Autowired
    private RagKnowledgeBaseUserRepository knowledgeBaseUserRepository;
    @Autowired
    private RagFileObjectRepository fileObjectRepository;
    @Autowired
    private RagUserDocumentRepository documentRepository;
    @Autowired
    private RagDocumentChunkRepository chunkRepository;
    @Autowired
    private RagIngestionJobRepository jobRepository;
    @Autowired
    private UserRepository userRepository;
    @MockitoBean
    private RagVectorService vectorService;
    @MockitoBean
    private RagRerankService rerankService;

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
    void hybridSearchReturnsStageBreakdownAndFusedResultsWithoutRerankByDefault() {
        TestKnowledgeBase data = createKnowledgeBaseWithChunks();
        SourceReferenceResponse vectorOnly = source(data.knowledgeBase(), data.document(), data.vectorChunk(), 0.91D, "VECTOR");
        when(vectorService.search("retry import", List.of(data.knowledgeBase().getId()), 50, BigDecimal.valueOf(0.55)))
                .thenReturn(List.of(vectorOnly));

        RetrievalSearchResponse response = retrievalService.search(new RetrievalSearchRequest(
                "retry import",
                List.of(data.knowledgeBase().getId()),
                6,
                50,
                BigDecimal.valueOf(0.55),
                RagSearchMode.HYBRID,
                false,
                true
        ), principal(data.user().getId()));

        assertThat(response.searchMode()).isEqualTo(RagSearchMode.HYBRID);
        assertThat(response.traceId()).isNotBlank();
        assertThat(response.results()).extracting(SourceReferenceResponse::chunkId)
                .contains(data.vectorChunk().getId(), data.keywordChunk().getId());
        assertThat(response.stages().vector()).extracting(SourceReferenceResponse::chunkId)
                .containsExactly(data.vectorChunk().getId());
        assertThat(response.stages().keyword()).extracting(SourceReferenceResponse::chunkId)
                .containsExactly(data.keywordChunk().getId());
        assertThat(response.stages().reranked()).isEmpty();
    }

    @Test
    void hybridRerankSearchUsesRerankedOrderWhenRequestEnablesRerank() {
        TestKnowledgeBase data = createKnowledgeBaseWithChunks();
        SourceReferenceResponse vectorOnly = source(data.knowledgeBase(), data.document(), data.vectorChunk(), 0.91D, "VECTOR");
        when(vectorService.search("retry import", List.of(data.knowledgeBase().getId()), 50, BigDecimal.valueOf(0.55)))
                .thenReturn(List.of(vectorOnly));
        when(rerankService.rerank(any(), any(), anyInt()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    List<SourceReferenceResponse> candidates = invocation.getArgument(1);
                    return candidates.stream()
                            .sorted((left, right) -> Long.compare(right.chunkId(), left.chunkId()))
                            .toList();
                });

        RetrievalSearchResponse response = retrievalService.search(new RetrievalSearchRequest(
                "retry import",
                List.of(data.knowledgeBase().getId()),
                6,
                50,
                BigDecimal.valueOf(0.55),
                RagSearchMode.HYBRID_RERANK,
                true,
                true
        ), principal(data.user().getId()));

        assertThat(response.searchMode()).isEqualTo(RagSearchMode.HYBRID_RERANK);
        assertThat(response.stages().reranked()).isNotEmpty();
        assertThat(response.results()).extracting(SourceReferenceResponse::chunkId)
                .startsWith(data.keywordChunk().getId());
    }

    @Test
    void retrievalUsesSingleKnowledgeBaseDefaultsWhenRequestOmitsOptions() {
        TestKnowledgeBase data = createKnowledgeBaseWithChunks();
        data.knowledgeBase().setDefaultSearchMode(RagSearchMode.HYBRID);
        data.knowledgeBase().setRerankEnabled(true);
        data.knowledgeBase().setDefaultTopK(1);
        data.knowledgeBase().setCandidateTopK(7);
        data.knowledgeBase().setDefaultSimilarityThreshold(BigDecimal.valueOf(0.23));
        knowledgeBaseRepository.save(data.knowledgeBase());

        SourceReferenceResponse vectorOnly = source(data.knowledgeBase(), data.document(), data.vectorChunk(), 0.91D, "VECTOR");
        when(vectorService.search(any(), any(), anyInt(), any())).thenReturn(List.of(vectorOnly));
        when(rerankService.rerank(any(), any(), anyInt()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    List<SourceReferenceResponse> candidates = invocation.getArgument(1);
                    return candidates;
                });

        RetrievalSearchResponse response = retrievalService.search(new RetrievalSearchRequest(
                "retry import",
                List.of(data.knowledgeBase().getId()),
                null,
                null,
                null,
                null,
                null,
                true
        ), principal(data.user().getId()));

        assertThat(response.searchMode()).isEqualTo(RagSearchMode.HYBRID_RERANK);
        assertThat(response.results()).hasSize(1);
        verify(vectorService).search(eq("retry import"), eq(List.of(data.knowledgeBase().getId())), eq(7), any());
        verify(rerankService).rerank(eq("retry import"), any(), eq(1));
    }

    @Test
    void retrievalUsesSingleKnowledgeBaseFusionWeightsWhenRequestOmitsOptions() {
        TestKnowledgeBase data = createKnowledgeBaseWithChunks();
        data.knowledgeBase().setDefaultSearchMode(RagSearchMode.HYBRID);
        data.knowledgeBase().setRerankEnabled(false);
        data.knowledgeBase().setVectorWeight(BigDecimal.ZERO);
        data.knowledgeBase().setKeywordWeight(BigDecimal.ONE);
        knowledgeBaseRepository.save(data.knowledgeBase());

        SourceReferenceResponse vectorOnly = source(data.knowledgeBase(), data.document(), data.vectorChunk(), 0.91D, "VECTOR");
        when(vectorService.search("retry import", List.of(data.knowledgeBase().getId()), 50, BigDecimal.valueOf(0.55)))
                .thenReturn(List.of(vectorOnly));

        RetrievalSearchResponse response = retrievalService.search(new RetrievalSearchRequest(
                "retry import",
                List.of(data.knowledgeBase().getId()),
                null,
                null,
                null,
                null,
                null,
                true
        ), principal(data.user().getId()));

        assertThat(response.searchMode()).isEqualTo(RagSearchMode.HYBRID);
        assertThat(response.results()).extracting(SourceReferenceResponse::chunkId)
                .startsWith(data.keywordChunk().getId());
    }

    private TestKnowledgeBase createKnowledgeBaseWithChunks() {
        UserPo user = new UserPo();
        user.setUsername("rag-hybrid-user-" + UUID.randomUUID());
        user.setNickname("RAG Hybrid User");
        user.setPasswordHash("password");
        user.setStatus(RbacStatus.ENABLED);
        user = userRepository.save(user);

        RagKnowledgeBasePo knowledgeBase = new RagKnowledgeBasePo();
        knowledgeBase.setCode("hybrid-kb");
        knowledgeBase.setName("Hybrid KB");
        knowledgeBase = knowledgeBaseRepository.save(knowledgeBase);

        RagKnowledgeBaseUserPo grant = new RagKnowledgeBaseUserPo();
        grant.setKnowledgeBaseId(knowledgeBase.getId());
        grant.setUserId(user.getId());
        grant.setAccessLevel(RagAccessLevel.READ);
        knowledgeBaseUserRepository.save(grant);

        RagFileObjectPo fileObject = new RagFileObjectPo();
        fileObject.setSha256("abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789");
        fileObject.setStorageType(RagStorageType.LOCAL);
        fileObject.setStorageKey("local/hybrid.md");
        fileObject.setOriginalFilename("hybrid.md");
        fileObject.setContentType("text/markdown");
        fileObject.setFileType(RagFileType.MD);
        fileObject.setFileSize(10);
        fileObject = fileObjectRepository.save(fileObject);

        RagUserDocumentPo document = new RagUserDocumentPo();
        document.setUserId(user.getId());
        document.setKnowledgeBaseId(knowledgeBase.getId());
        document.setFileObjectId(fileObject.getId());
        document.setDisplayName("hybrid.md");
        document.setSourceType(RagDocumentSourceType.UPLOAD);
        document.setFileType(RagFileType.MD);
        document.setContentHash(fileObject.getSha256());
        document.setStatus(RagDocumentStatus.INDEXED);
        document = documentRepository.save(document);

        RagDocumentChunkPo vectorChunk = chunk(document, fileObject, 0, "semantic content about arxiv ingestion");
        RagDocumentChunkPo keywordChunk = chunk(document, fileObject, 1, "retry import job when arxiv import failed");
        return new TestKnowledgeBase(user, knowledgeBase, document, vectorChunk, keywordChunk);
    }

    private RagDocumentChunkPo chunk(RagUserDocumentPo document, RagFileObjectPo fileObject, int index, String content) {
        RagDocumentChunkPo chunk = new RagDocumentChunkPo();
        chunk.setDocumentId(document.getId());
        chunk.setKnowledgeBaseId(document.getKnowledgeBaseId());
        chunk.setFileObjectId(fileObject.getId());
        chunk.setChunkIndex(index);
        chunk.setContent(content);
        chunk.setContentPreview(content);
        chunk.setTokenCount(content.length() / 2);
        chunk.setEnabled(true);
        return chunkRepository.save(chunk);
    }

    private SourceReferenceResponse source(RagKnowledgeBasePo knowledgeBase, RagUserDocumentPo document,
                                           RagDocumentChunkPo chunk, double score, String matchedBy) {
        return new SourceReferenceResponse(
                String.valueOf(chunk.getId()),
                knowledgeBase.getId(),
                knowledgeBase.getName(),
                document.getId(),
                document.getDisplayName(),
                chunk.getId(),
                chunk.getChunkIndex(),
                score,
                chunk.getContent(),
                Map.of("matched_by", matchedBy)
        );
    }

    private RbacPrincipal principal(Long userId) {
        return new RbacPrincipal(userId, "rag-hybrid-user", Set.of("RAG_USER"),
                Set.of("api:rag:retrieval:search"), "permission-v1");
    }

    private record TestKnowledgeBase(
            UserPo user,
            RagKnowledgeBasePo knowledgeBase,
            RagUserDocumentPo document,
            RagDocumentChunkPo vectorChunk,
            RagDocumentChunkPo keywordChunk
    ) {
    }

}
