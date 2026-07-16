package top.egon.mario.investment.quant.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import top.egon.mario.investment.quant.po.InvestmentBacktestRunPo;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface InvestmentBacktestRunRepository extends JpaRepository<InvestmentBacktestRunPo, Long> {
    Optional<InvestmentBacktestRunPo> findByJobIdAndDeletedFalse(Long jobId);
    Optional<InvestmentBacktestRunPo> findByIdAndWorkspaceIdAndDeletedFalse(Long id, Long workspaceId);
    @Query("""
            select run from InvestmentBacktestRunPo run
            join InvestmentWorkspacePo workspace on workspace.id = run.workspaceId
            where run.id = :runId
              and workspace.ownerUserId = :ownerUserId
              and workspace.status = 'ACTIVE'
              and workspace.deleted = false
              and run.deleted = false
            """)
    Optional<InvestmentBacktestRunPo> findOwnedRun(
            @Param("runId") Long runId, @Param("ownerUserId") Long ownerUserId);
    Page<InvestmentBacktestRunPo> findByWorkspaceIdAndDeletedFalse(Long workspaceId, Pageable pageable);
    List<InvestmentBacktestRunPo> findTop5ByWorkspaceIdAndStatusAndFinishedAtLessThanEqualAndDeletedFalseOrderByFinishedAtDescIdDesc(
            Long workspaceId, String status, Instant cutoff);
    Optional<InvestmentBacktestRunPo> findFirstByWorkspaceIdAndStatusAndFinishedAtLessThanEqualAndDeletedFalseOrderByFinishedAtDescIdDesc(
            Long workspaceId, String status, Instant cutoff);
}
