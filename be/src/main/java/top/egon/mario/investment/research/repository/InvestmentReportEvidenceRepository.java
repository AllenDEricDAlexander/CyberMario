package top.egon.mario.investment.research.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.investment.research.po.InvestmentReportEvidencePo;

import java.util.List;

/**
 * Append-only evidence persistence owned by a research report.
 */
public interface InvestmentReportEvidenceRepository extends JpaRepository<InvestmentReportEvidencePo, Long> {

    List<InvestmentReportEvidencePo> findAllByReportIdOrderByIdAsc(Long reportId);
}
