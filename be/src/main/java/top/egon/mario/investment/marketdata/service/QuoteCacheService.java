package top.egon.mario.investment.marketdata.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import top.egon.mario.investment.marketdata.event.InvestmentMarketDataCommittedEvent;
import top.egon.mario.investment.marketdata.event.MarketDataAfterCommitPublisher;
import top.egon.mario.investment.marketdata.repository.jdbc.model.ContractQuoteWrite;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Refreshes the disposable latest-quote cache strictly after its database transaction commits.
 */
@Service
public class QuoteCacheService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(2);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final MarketDataAfterCommitPublisher afterCommitPublisher;

    public QuoteCacheService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
                             MarketDataAfterCommitPublisher afterCommitPublisher) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.afterCommitPublisher = afterCommitPublisher;
    }

    /**
     * Registers one callback in the active transaction. A rollback therefore performs neither cache nor event work.
     */
    public void refreshAfterCommit(ContractQuoteWrite quote) {
        String cacheValue = cacheValue(quote);
        afterCommitPublisher.publishAfterCommit(new InvestmentMarketDataCommittedEvent(
                        quote.sourceId(), quote.instrumentId(), "QUOTE", 1, quote.sourceTime()),
                () -> redisTemplate.opsForValue().set(cacheKey(quote.sourceId(), quote.instrumentId()),
                        cacheValue, CACHE_TTL));
    }

    private String cacheKey(long sourceId, long instrumentId) {
        return "investment:quote:" + sourceId + ":" + instrumentId;
    }

    private String cacheValue(ContractQuoteWrite quote) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("lastPrice", quote.lastPrice());
        value.put("markPrice", quote.markPrice());
        value.put("indexPrice", quote.indexPrice());
        value.put("bidPrice", quote.bidPrice());
        value.put("askPrice", quote.askPrice());
        value.put("fundingRate", quote.fundingRate());
        value.put("openInterest", quote.openInterest());
        value.put("sourceTime", quote.sourceTime());
        value.put("receivedAt", quote.receivedAt());
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Unable to encode latest quote cache value", ex);
        }
    }
}
