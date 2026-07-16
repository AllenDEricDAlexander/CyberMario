package top.egon.mario.investment.trading.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.investment.trading.po.InvestmentPaperFillPo;

import java.util.Optional;

public interface InvestmentPaperFillRepository extends JpaRepository<InvestmentPaperFillPo, Long> {

    Optional<InvestmentPaperFillPo> findByOrderIdAndFillNo(Long orderId, Long fillNo);
}
