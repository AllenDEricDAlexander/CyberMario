package top.egon.mario.rag.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.rag.po.RagRetrievalTraceItemPo;

import java.util.List;

/**
 * Repository for ranked RAG retrieval trace items.
 */
public interface RagRetrievalTraceItemRepository extends JpaRepository<RagRetrievalTraceItemPo, Long> {

    List<RagRetrievalTraceItemPo> findByTraceIdAndDeletedFalseOrderByStageAscRankNoAsc(String traceId);

}
