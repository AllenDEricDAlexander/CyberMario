package top.egon.mario.investment.research.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import top.egon.mario.common.entity.BaseAuditablePo;

/**
 * Owner-scoped named collection of Investment instruments.
 */
@Getter
@Setter
@Entity
@Table(name = "investment_watchlist", uniqueConstraints = {
        @UniqueConstraint(name = "uk_investment_watchlist_workspace_name", columnNames = {"workspace_id", "name"})
})
public class InvestmentWatchlistPo extends BaseAuditablePo {

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "description", nullable = false, length = 512)
    private String description = "";

    @Column(name = "sort_no", nullable = false)
    private int sortNo;
}
