package top.egon.mario.rag.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import top.egon.mario.rag.po.RagIngestionJobPo;

import java.util.Optional;

/**
 * Repository for RAG ingestion jobs.
 */
public interface RagIngestionJobRepository extends JpaRepository<RagIngestionJobPo, Long>, JpaSpecificationExecutor<RagIngestionJobPo> {

    Optional<RagIngestionJobPo> findByIdAndDeletedFalse(Long id);

}
