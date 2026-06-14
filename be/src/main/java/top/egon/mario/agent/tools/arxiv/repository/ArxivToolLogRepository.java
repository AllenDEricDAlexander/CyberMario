package top.egon.mario.agent.tools.arxiv.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import top.egon.mario.agent.tools.arxiv.po.ArxivToolLogPo;
import top.egon.mario.agent.tools.arxiv.po.enums.ArxivToolLogStatus;

import java.util.Optional;

/**
 * Repository for arXiv tool search and import logs.
 */
public interface ArxivToolLogRepository extends JpaRepository<ArxivToolLogPo, Long>, JpaSpecificationExecutor<ArxivToolLogPo> {

    Optional<ArxivToolLogPo> findFirstByKnowledgeBaseIdAndEntryIdAndStatusOrderByIdDesc(
            Long knowledgeBaseId, String entryId, ArxivToolLogStatus status);

}
