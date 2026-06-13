package top.egon.mario.rag.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import top.egon.mario.rag.po.RagUserDocumentPo;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Repository for user-visible RAG documents.
 */
public interface RagUserDocumentRepository extends JpaRepository<RagUserDocumentPo, Long>, JpaSpecificationExecutor<RagUserDocumentPo> {

    Optional<RagUserDocumentPo> findByIdAndDeletedFalse(Long id);

    List<RagUserDocumentPo> findByFileObjectIdAndDeletedFalse(Long fileObjectId);

    List<RagUserDocumentPo> findByKnowledgeBaseIdInAndDeletedFalse(Collection<Long> knowledgeBaseIds);

}
