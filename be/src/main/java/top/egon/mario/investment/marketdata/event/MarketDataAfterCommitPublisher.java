package top.egon.mario.investment.marketdata.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.mario.common.utils.LogUtil;

/**
 * Registers sanitized notifications against the active transaction without making a committed job retryable.
 */
@Service
@Slf4j
public class MarketDataAfterCommitPublisher {

    private final ApplicationEventPublisher eventPublisher;

    public MarketDataAfterCommitPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void publishAfterCommit(InvestmentMarketDataCommittedEvent event) {
        publishAfterCommit(event, () -> { });
    }

    /**
     * Runs optional disposable cache work before publishing. Cache and listener failures are logged after the durable
     * database commit, so they cannot turn a successful write into a retry whose idempotent replay emits no event.
     */
    public void publishAfterCommit(InvestmentMarketDataCommittedEvent event, Runnable beforePublish) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Market-data event requires an active synchronized transaction");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    beforePublish.run();
                } catch (RuntimeException ex) {
                    LogUtil.warn(log).log("investment market cache refresh failed after commit, dataType={}, error={}",
                            event.dataType(), message(ex));
                }
                try {
                    eventPublisher.publishEvent(event);
                } catch (RuntimeException ex) {
                    LogUtil.warn(log).log("investment market event publish failed after commit, dataType={}, error={}",
                            event.dataType(), message(ex));
                }
            }
        });
    }

    private String message(RuntimeException ex) {
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }
}
