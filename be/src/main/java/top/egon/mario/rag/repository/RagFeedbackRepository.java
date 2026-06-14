package top.egon.mario.rag.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.rag.po.RagFeedbackPo;

/**
 * Repository for RAG answer feedback.
 */
public interface RagFeedbackRepository extends JpaRepository<RagFeedbackPo, Long> {
}
