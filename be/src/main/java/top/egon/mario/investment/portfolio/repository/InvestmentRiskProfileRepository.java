package top.egon.mario.investment.portfolio.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.investment.portfolio.po.InvestmentRiskProfilePo;

import java.util.Optional;

/**
 * One-to-one risk profile persistence keyed by paper account.
 */
public interface InvestmentRiskProfileRepository extends JpaRepository<InvestmentRiskProfilePo, Long> {

    Optional<InvestmentRiskProfilePo> findByAccountId(Long accountId);
}
