package top.egon.mario.investment.quant.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.investment.quant.po.InvestmentBacktestTradePo;

public interface InvestmentBacktestTradeRepository extends JpaRepository<InvestmentBacktestTradePo, Long> {
    Page<InvestmentBacktestTradePo> findByRunId(Long runId, Pageable pageable);
    boolean existsByRunId(Long runId);
}
