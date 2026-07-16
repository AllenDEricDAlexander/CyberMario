package top.egon.mario.investment.marketdata.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.investment.marketdata.po.InvestmentContractSpecPo;

/**
 * Persistence for current normalized contract specifications.
 */
public interface InvestmentContractSpecRepository extends JpaRepository<InvestmentContractSpecPo, Long> {
}
