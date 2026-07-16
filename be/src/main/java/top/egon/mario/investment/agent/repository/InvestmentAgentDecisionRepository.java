package top.egon.mario.investment.agent.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import top.egon.mario.investment.agent.model.InvestmentAgentExecutionStatus;
import top.egon.mario.investment.agent.po.InvestmentAgentDecisionPo;

import java.util.List;
import java.util.Optional;

public interface InvestmentAgentDecisionRepository extends JpaRepository<InvestmentAgentDecisionPo, Long> {

    List<InvestmentAgentDecisionPo> findByRunIdOrderByIdAsc(Long runId);

    List<InvestmentAgentDecisionPo> findByRunIdInOrderByRunIdAscIdAsc(List<Long> runIds);

    Optional<InvestmentAgentDecisionPo> findFirstByRunIdOrderByIdAsc(Long runId);

    List<InvestmentAgentDecisionPo> findByExecutionStatusAndIntentIdIsNullOrderByIdAsc(
            InvestmentAgentExecutionStatus executionStatus);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select decision from InvestmentAgentDecisionPo decision where decision.id = :decisionId")
    Optional<InvestmentAgentDecisionPo> findByIdForUpdate(@Param("decisionId") Long decisionId);
}
