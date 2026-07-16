package top.egon.mario.investment.portfolio.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import top.egon.mario.common.entity.BaseAuditablePo;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Private, paper-only USDT futures account owned through an Investment workspace.
 */
@Getter
@Setter
@Entity
@Table(name = "investment_paper_account", uniqueConstraints = {
        @UniqueConstraint(name = "uk_investment_paper_account_workspace_name",
                columnNames = {"workspace_id", "name"})
})
public class InvestmentPaperAccountPo extends BaseAuditablePo {

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "margin_asset", nullable = false, length = 32)
    private String marginAsset = "USDT";

    @Column(name = "initial_equity", nullable = false, precision = 38, scale = 18, updatable = false)
    private BigDecimal initialEquity;

    @Column(name = "wallet_balance", nullable = false, precision = 38, scale = 18)
    private BigDecimal walletBalance;

    @Column(name = "ledger_sequence", nullable = false)
    private Long ledgerSequence = 0L;

    @Column(name = "margin_mode", nullable = false, length = 32)
    private String marginMode = "ISOLATED";

    @Column(name = "position_mode", nullable = false, length = 32)
    private String positionMode = "ONE_WAY";

    @Column(name = "trading_enabled", nullable = false)
    private boolean tradingEnabled;

    @Column(name = "agent_auto_trade_enabled", nullable = false)
    private boolean agentAutoTradeEnabled;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "ACTIVE";

    @Column(name = "opened_at", nullable = false, updatable = false)
    private Instant openedAt;
}
