package top.egon.mario.investment.research.report;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import top.egon.mario.investment.common.InvestmentErrorCode;
import top.egon.mario.investment.common.InvestmentException;
import top.egon.mario.investment.common.model.ProductType;
import top.egon.mario.investment.marketdata.subscription.InvestmentMarketSubscriptionRegistry;
import top.egon.mario.investment.marketdata.subscription.MarketSubscription;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves evidence identities only from active database mappings admitted by the code registry.
 */
@Service
public class ResearchEvidenceSourceService {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final Set<SubscriptionKey> subscriptionKeys;

    public ResearchEvidenceSourceService(NamedParameterJdbcTemplate jdbcTemplate,
                                         InvestmentMarketSubscriptionRegistry subscriptionRegistry) {
        this.jdbcTemplate = jdbcTemplate;
        this.subscriptionKeys = subscriptionRegistry.subscriptions().stream()
                .map(SubscriptionKey::of).collect(Collectors.toUnmodifiableSet());
    }

    public ResearchEvidenceSource requireInstrumentSource(long instrumentId) {
        return activeSources().stream().filter(source -> source.instrumentId() == instrumentId).findFirst()
                .orElseThrow(() -> new InvestmentException(InvestmentErrorCode.CAPABILITY_UNAVAILABLE,
                        "No active code-subscribed evidence source for instrument " + instrumentId));
    }

    public List<ResearchEvidenceSource> requireMarketSources() {
        List<ResearchEvidenceSource> sources = activeSources();
        if (sources.isEmpty()) {
            throw new InvestmentException(InvestmentErrorCode.CAPABILITY_UNAVAILABLE,
                    "No active code-subscribed market evidence source is available");
        }
        return sources;
    }

    private List<ResearchEvidenceSource> activeSources() {
        return jdbcTemplate.query("""
                select ins.source_id, ins.instrument_id, source.code as source_code,
                       instrument.product_type, ins.external_symbol
                from investment_instrument_source ins
                join investment_data_source source on source.id = ins.source_id
                  and source.deleted = false and source.status = 'ACTIVE'
                join investment_instrument instrument on instrument.id = ins.instrument_id
                  and instrument.deleted = false and instrument.status = 'ACTIVE'
                where ins.deleted = false and ins.source_status = 'ACTIVE'
                order by ins.instrument_id asc, ins.source_id asc, ins.id asc
                """, Map.of(), (resultSet, rowNumber) -> new SourceProjection(
                resultSet.getLong("source_id"), resultSet.getLong("instrument_id"),
                resultSet.getString("source_code"), ProductType.valueOf(resultSet.getString("product_type")),
                resultSet.getString("external_symbol"))).stream()
                .filter(source -> subscriptionKeys.contains(source.key()))
                .map(source -> new ResearchEvidenceSource(source.sourceId(), source.instrumentId(),
                        source.sourceCode(), source.externalSymbol()))
                .toList();
    }

    private record SourceProjection(long sourceId, long instrumentId, String sourceCode,
                                    ProductType productType, String externalSymbol) {
        private SubscriptionKey key() {
            return new SubscriptionKey(sourceCode, productType, externalSymbol);
        }
    }

    private record SubscriptionKey(String sourceCode, ProductType productType, String externalSymbol) {
        private static SubscriptionKey of(MarketSubscription subscription) {
            return new SubscriptionKey(subscription.sourceCode(), subscription.productType(), subscription.symbol());
        }
    }
}
