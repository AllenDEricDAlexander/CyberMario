package top.egon.mario.investment.marketdata.service;

import org.springframework.stereotype.Service;
import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.PriceType;
import top.egon.mario.investment.marketdata.subscription.InvestmentMarketSubscriptionRegistry;
import top.egon.mario.investment.marketdata.subscription.MarketSubscription;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Computes code-owned retention ranges eligible for snapshot-protected bounded deletion.
 */
@Service
public class MarketDataRetentionCandidateService {

    private final InvestmentMarketSubscriptionRegistry subscriptionRegistry;
    private final Clock clock;

    public MarketDataRetentionCandidateService(InvestmentMarketSubscriptionRegistry subscriptionRegistry,
                                               Clock clock) {
        this.subscriptionRegistry = subscriptionRegistry;
        this.clock = clock;
    }

    public List<RetentionCandidate> candidates() {
        Instant now = clock.instant();
        List<RetentionCandidate> candidates = new ArrayList<>();
        for (MarketSubscription subscription : subscriptionRegistry.subscriptions()) {
            subscription.retentionPolicy().retainedFor().forEach((interval, retainedFor) -> {
                if (interval != BarInterval.D1 && interval != BarInterval.NONE) {
                    for (PriceType priceType : subscription.priceTypes()) {
                        candidates.add(new RetentionCandidate(subscription.sourceCode(),
                                subscription.productType().name(), subscription.symbol(), priceType, interval,
                                Instant.EPOCH, now.minus(retainedFor), true));
                    }
                }
            });
        }
        return candidates.stream().sorted(Comparator.comparing(RetentionCandidate::sourceCode)
                .thenComparing(RetentionCandidate::symbol)
                .thenComparing(candidate -> candidate.priceType().name())
                .thenComparing(candidate -> candidate.interval().name())).toList();
    }

    public record RetentionCandidate(
            String sourceCode,
            String productType,
            String symbol,
            PriceType priceType,
            BarInterval interval,
            Instant fromInclusive,
            Instant toExclusive,
            boolean physicalDeletionEnabled
    ) {
    }
}
