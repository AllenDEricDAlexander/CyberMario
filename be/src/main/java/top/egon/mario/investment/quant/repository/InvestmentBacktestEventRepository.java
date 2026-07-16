package top.egon.mario.investment.quant.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.investment.quant.po.InvestmentBacktestEventPo;

public interface InvestmentBacktestEventRepository extends JpaRepository<InvestmentBacktestEventPo, Long> {
    Page<InvestmentBacktestEventPo> findByRunId(Long runId, Pageable pageable);
    boolean existsByRunId(Long runId);
}
