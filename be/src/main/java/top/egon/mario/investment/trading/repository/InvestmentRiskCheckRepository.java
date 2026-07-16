package top.egon.mario.investment.trading.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.investment.trading.po.InvestmentRiskCheckPo;

import java.util.List;

public interface InvestmentRiskCheckRepository extends JpaRepository<InvestmentRiskCheckPo, Long> {

    List<InvestmentRiskCheckPo> findByIntentIdOrderById(Long intentId);
}
