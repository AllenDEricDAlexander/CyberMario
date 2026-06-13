package top.egon.mario.rag.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import top.egon.mario.rag.po.RagKnowledgeBasePo;
import top.egon.mario.rag.po.enums.RagKnowledgeBaseStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Repository for RAG knowledge base metadata.
 */
public interface RagKnowledgeBaseRepository extends JpaRepository<RagKnowledgeBasePo, Long>, JpaSpecificationExecutor<RagKnowledgeBasePo> {

    Optional<RagKnowledgeBasePo> findByIdAndDeletedFalse(Long id);

    Optional<RagKnowledgeBasePo> findByCodeAndDeletedFalse(String code);

    List<RagKnowledgeBasePo> findByIdInAndDeletedFalseAndStatus(Collection<Long> ids, RagKnowledgeBaseStatus status);

    List<RagKnowledgeBasePo> findByDeletedFalseAndStatus(RagKnowledgeBaseStatus status);

    boolean existsByCodeAndDeletedFalse(String code);

}
