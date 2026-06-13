package top.egon.mario.rag.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.rag.po.RagKnowledgeBaseUserPo;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Repository for user grants on RAG knowledge bases.
 */
public interface RagKnowledgeBaseUserRepository extends JpaRepository<RagKnowledgeBaseUserPo, Long> {

    Optional<RagKnowledgeBaseUserPo> findByKnowledgeBaseIdAndUserIdAndDeletedFalse(Long knowledgeBaseId, Long userId);

    List<RagKnowledgeBaseUserPo> findByUserIdAndDeletedFalse(Long userId);

    List<RagKnowledgeBaseUserPo> findByUserIdAndKnowledgeBaseIdInAndDeletedFalse(Long userId, Collection<Long> knowledgeBaseIds);

    List<RagKnowledgeBaseUserPo> findByKnowledgeBaseIdAndDeletedFalse(Long knowledgeBaseId);

}
