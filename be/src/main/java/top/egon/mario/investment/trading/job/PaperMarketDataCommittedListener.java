package top.egon.mario.investment.trading.job;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import top.egon.mario.investment.marketdata.event.InvestmentMarketDataCommittedEvent;

/** Routes committed market facts into idempotent paper-maintenance reconciliation. */
@Component
@RequiredArgsConstructor
public class PaperMarketDataCommittedListener {

    private final PaperMaintenanceJobPlanner planner;

    @EventListener
    public void onCommitted(InvestmentMarketDataCommittedEvent event) {
        planner.onMarketDataCommitted(event);
    }
}
