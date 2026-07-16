package top.egon.mario.investment.research.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import top.egon.mario.common.entity.BaseAuditablePo;

/**
 * Instrument membership in an owner-scoped Investment watchlist.
 */
@Getter
@Setter
@Entity
@Table(name = "investment_watchlist_item", uniqueConstraints = {
        @UniqueConstraint(name = "uk_investment_watchlist_item_instrument",
                columnNames = {"watchlist_id", "instrument_id"})
})
public class InvestmentWatchlistItemPo extends BaseAuditablePo {

    @Column(name = "watchlist_id", nullable = false)
    private Long watchlistId;

    @Column(name = "instrument_id", nullable = false)
    private Long instrumentId;

    @Column(name = "sort_no", nullable = false)
    private int sortNo;

    @Column(name = "note", nullable = false, length = 512)
    private String note = "";
}
