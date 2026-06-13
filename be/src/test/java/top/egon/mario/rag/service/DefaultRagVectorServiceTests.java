package top.egon.mario.rag.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import top.egon.mario.rag.po.RagDocumentChunkPo;
import top.egon.mario.rag.po.RagKnowledgeBasePo;
import top.egon.mario.rag.po.RagUserDocumentPo;
import top.egon.mario.rag.repository.RagDocumentChunkRepository;
import top.egon.mario.rag.service.impl.DefaultRagVectorService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Verifies that RAG indexing uses the Spring AI vector store when it is available.
 */
class DefaultRagVectorServiceTests {

    @Test
    void indexChunksDelegatesToVectorStoreWhenAvailable() {
        VectorStore vectorStore = mock(VectorStore.class);
        RagDocumentChunkRepository chunkRepository = mock(RagDocumentChunkRepository.class);
        DefaultRagVectorService service = new DefaultRagVectorService(Optional.of(vectorStore), chunkRepository);
        RagKnowledgeBasePo knowledgeBase = new RagKnowledgeBasePo();
        knowledgeBase.setId(10L);
        knowledgeBase.setName("Docs");
        RagUserDocumentPo document = new RagUserDocumentPo();
        document.setId(20L);
        document.setDisplayName("guide.md");
        RagDocumentChunkPo chunk = new RagDocumentChunkPo();
        chunk.setId(30L);
        chunk.setKnowledgeBaseId(10L);
        chunk.setDocumentId(20L);
        chunk.setChunkIndex(0);
        chunk.setContent("hello pgvector");

        service.indexChunks(knowledgeBase, document, List.of(chunk));

        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        String vectorId = captor.getValue().getFirst().getId();
        assertThat(UUID.fromString(vectorId).toString()).isEqualTo(vectorId);
        assertThat(captor.getValue().getFirst().getText()).isEqualTo("hello pgvector");
        assertThat(captor.getValue().getFirst().getMetadata()).containsEntry("chunk_id", "30");
    }

    @Test
    void deleteChunksDelegatesToVectorStoreWhenAvailable() {
        VectorStore vectorStore = mock(VectorStore.class);
        RagDocumentChunkRepository chunkRepository = mock(RagDocumentChunkRepository.class);
        DefaultRagVectorService service = new DefaultRagVectorService(Optional.of(vectorStore), chunkRepository);
        RagDocumentChunkPo chunk = new RagDocumentChunkPo();
        chunk.setId(30L);

        service.deleteChunks(List.of(chunk));

        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).delete(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(UUID.fromString(captor.getValue().getFirst()).toString()).isEqualTo(captor.getValue().getFirst());
    }

}
