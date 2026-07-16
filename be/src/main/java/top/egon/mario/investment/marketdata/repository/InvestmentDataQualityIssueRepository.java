package top.egon.mario.investment.marketdata.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.investment.marketdata.po.InvestmentDataQualityIssuePo;

/**
 * Persistence for audited market-data quality facts.
 */
public interface InvestmentDataQualityIssueRepository extends JpaRepository<InvestmentDataQualityIssuePo, Long> {
}
