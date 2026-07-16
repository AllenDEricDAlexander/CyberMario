package top.egon.mario.investment.trading.matching;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.PriceType;
import top.egon.mario.investment.marketdata.repository.jdbc.MarketBarJdbcRepository;
import top.egon.mario.investment.marketdata.repository.jdbc.model.MarketBarIntradayRow;
import top.egon.mario.investment.trading.matching.model.FuturesBar;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Replays current closed M1 bars from N+1 so deferred GTC orders cannot skip a crossing bar.
 */
@Component
@RequiredArgsConstructor
public class PaperMatchingMarketReader {

    private static final int PAGE_SIZE = 1000;

    private final MarketBarJdbcRepository barRepository;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<FuturesBar> closedBars(long sourceId, long instrumentId, Instant eligibleAfter, Instant now) {
        List<FuturesBar> bars = new ArrayList<>();
        int offset = 0;
        Instant exclusiveEnd = now.plusSeconds(1);
        while (true) {
            List<MarketBarIntradayRow> page = barRepository.findCurrentIntraday(
                    sourceId, instrumentId, PriceType.MARKET, BarInterval.M1,
                    eligibleAfter, exclusiveEnd, offset, PAGE_SIZE);
            page.stream()
                    .filter(row -> row.closed() && !row.closeTime().isAfter(now))
                    .map(PaperMatchingMarketReader::toBar)
                    .forEach(bars::add);
            if (page.size() < PAGE_SIZE) {
                return List.copyOf(bars);
            }
            offset += page.size();
        }
    }

    private static FuturesBar toBar(MarketBarIntradayRow row) {
        return new FuturesBar(row.openTime(), row.closeTime(), row.openPrice(), row.highPrice(),
                row.lowPrice(), row.closePrice(), row.closed());
    }
}
