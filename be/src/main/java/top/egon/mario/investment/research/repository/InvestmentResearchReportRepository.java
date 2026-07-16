package top.egon.mario.investment.research.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import top.egon.mario.investment.research.po.InvestmentResearchReportPo;

import java.util.Optional;

/**
 * Owner-scoped persistence for immutable report versions.
 */
public interface InvestmentResearchReportRepository extends JpaRepository<InvestmentResearchReportPo, Long> {

    @Query("""
            select report from InvestmentResearchReportPo report
            join InvestmentWorkspacePo workspace on workspace.id = report.workspaceId
            where report.workspaceId = :workspaceId
              and workspace.ownerUserId = :ownerUserId
              and workspace.status = 'ACTIVE'
              and workspace.deleted = false
              and report.deleted = false
              and (:reportType is null or report.reportType = :reportType)
            """)
    Page<InvestmentResearchReportPo> findOwnedReports(
            @Param("workspaceId") Long workspaceId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("reportType") String reportType,
            Pageable pageable);

    @Query("""
            select report from InvestmentResearchReportPo report
            join InvestmentWorkspacePo workspace on workspace.id = report.workspaceId
            where report.id = :reportId
              and workspace.ownerUserId = :ownerUserId
              and workspace.status = 'ACTIVE'
              and workspace.deleted = false
              and report.deleted = false
            """)
    Optional<InvestmentResearchReportPo> findOwnedReport(
            @Param("reportId") Long reportId, @Param("ownerUserId") Long ownerUserId);

    Optional<InvestmentResearchReportPo> findByIdAndDeletedFalse(Long reportId);

    Optional<InvestmentResearchReportPo> findFirstBySourceTypeAndSourceReferenceIdAndDeletedFalseOrderByIdAsc(
            String sourceType, Long sourceReferenceId);
}
