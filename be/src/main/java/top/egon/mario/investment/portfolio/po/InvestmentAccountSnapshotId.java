package top.egon.mario.investment.portfolio.po;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.time.Instant;

@Embeddable
public record InvestmentAccountSnapshotId(
        @Column(name = "account_id") Long accountId,
        @Column(name = "snapshot_time") Instant snapshotTime
) implements Serializable {
}
