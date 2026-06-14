package top.egon.mario.rag.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.rag.po.RagRetrievalTracePo;

import java.util.Optional;

/**
 * Repository for RAG retrieval trace headers.
 */
public interface RagRetrievalTraceRepository extends JpaRepository<RagRetrievalTracePo, Long> {

    Optional<RagRetrievalTracePo> findByTraceIdAndDeletedFalse(String traceId);

}
