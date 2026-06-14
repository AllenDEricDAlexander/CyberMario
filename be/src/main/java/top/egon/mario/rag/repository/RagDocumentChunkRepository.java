package top.egon.mario.rag.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import top.egon.mario.rag.po.RagDocumentChunkPo;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Repository for searchable RAG document chunks.
 */
public interface RagDocumentChunkRepository extends JpaRepository<RagDocumentChunkPo, Long> {

    Optional<RagDocumentChunkPo> findByIdAndDeletedFalse(Long id);

    Page<RagDocumentChunkPo> findByDocumentIdAndDeletedFalse(Long documentId, Pageable pageable);

    List<RagDocumentChunkPo> findByDocumentIdOrderByChunkIndexAsc(Long documentId);

    List<RagDocumentChunkPo> findByDocumentIdAndDeletedFalseOrderByChunkIndexAsc(Long documentId);

    List<RagDocumentChunkPo> findByKnowledgeBaseIdInAndEnabledTrueAndDeletedFalse(Collection<Long> knowledgeBaseIds);

    List<RagDocumentChunkPo> findByKnowledgeBaseIdInAndEnabledTrueAndDeletedFalseAndContentContainingIgnoreCase(Collection<Long> knowledgeBaseIds,
                                                                                                                String content);

    @Modifying(flushAutomatically = true)
    @Query("delete from RagDocumentChunkPo chunk where chunk.documentId = :documentId")
    int deleteByDocumentId(@Param("documentId") Long documentId);

}
