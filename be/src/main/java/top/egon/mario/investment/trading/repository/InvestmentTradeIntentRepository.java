package top.egon.mario.investment.trading.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.investment.trading.po.InvestmentTradeIntentPo;

import java.util.Optional;

public interface InvestmentTradeIntentRepository extends JpaRepository<InvestmentTradeIntentPo, Long> {

    Optional<InvestmentTradeIntentPo> findByIdempotencyKey(String idempotencyKey);
}
