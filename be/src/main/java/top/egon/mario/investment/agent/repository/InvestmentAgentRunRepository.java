package top.egon.mario.investment.agent.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import top.egon.mario.investment.agent.po.InvestmentAgentRunPo;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface InvestmentAgentRunRepository extends JpaRepository<InvestmentAgentRunPo, Long> {

    Optional<InvestmentAgentRunPo> findByIdempotencyKeyAndDeletedFalse(String idempotencyKey);

    Optional<InvestmentAgentRunPo> findByIdAndDeletedFalse(Long id);

    @Query("""
            select run from InvestmentAgentRunPo run
            join InvestmentWorkspacePo workspace on workspace.id = run.workspaceId
            where run.id = :runId
              and workspace.ownerUserId = :actorId
              and workspace.status = 'ACTIVE'
              and workspace.deleted = false
              and run.deleted = false
            """)
    Optional<InvestmentAgentRunPo> findOwnedRun(
            @Param("runId") Long runId, @Param("actorId") Long actorId);

    @Query("""
            select run from InvestmentAgentRunPo run
            join InvestmentWorkspacePo workspace on workspace.id = run.workspaceId
            where run.workspaceId = :workspaceId
              and workspace.ownerUserId = :actorId
              and workspace.status = 'ACTIVE'
              and workspace.deleted = false
              and run.deleted = false
            """)
    Page<InvestmentAgentRunPo> findOwnedRuns(
            @Param("workspaceId") Long workspaceId,
            @Param("actorId") Long actorId,
            Pageable pageable);

    List<InvestmentAgentRunPo>
    findTop5ByWorkspaceIdAndStatusAndFinishedAtLessThanEqualAndDeletedFalseOrderByFinishedAtDescIdDesc(
            Long workspaceId, top.egon.mario.investment.common.model.InvestmentRunStatus status, Instant cutoff);
}
