package top.egon.mario.investment.portfolio.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.investment.portfolio.po.InvestmentAccountSnapshotId;
import top.egon.mario.investment.portfolio.po.InvestmentAccountSnapshotPo;

public interface InvestmentAccountSnapshotRepository extends
        JpaRepository<InvestmentAccountSnapshotPo, InvestmentAccountSnapshotId> {
}
