package top.egon.mario.investment.marketdata.ingest;

import org.springframework.stereotype.Component;
import top.egon.mario.investment.common.job.InvestmentJobNonRetryableException;
import top.egon.mario.investment.common.job.InvestmentJobRetryableException;
import top.egon.mario.investment.marketdata.po.InvestmentDataSourcePo;
import top.egon.mario.investment.marketdata.po.InvestmentInstrumentPo;
import top.egon.mario.investment.marketdata.repository.InvestmentDataSourceRepository;
import top.egon.mario.investment.marketdata.repository.InvestmentInstrumentRepository;

/**
 * Resolves platform catalog identifiers without trusting identifiers supplied by a job payload.
 */
@Component
public class MarketDataDimensionResolver {

    private final InvestmentDataSourceRepository dataSourceRepository;
    private final InvestmentInstrumentRepository instrumentRepository;

    public MarketDataDimensionResolver(InvestmentDataSourceRepository dataSourceRepository,
                                       InvestmentInstrumentRepository instrumentRepository) {
        this.dataSourceRepository = dataSourceRepository;
        this.instrumentRepository = instrumentRepository;
    }

    public MarketDataDimension resolve(MarketDataJobInput input) {
        InvestmentDataSourcePo source = dataSourceRepository.findByCodeAndDeletedFalse(input.sourceCode())
                .orElseThrow(() -> new InvestmentJobNonRetryableException("MARKET_SOURCE_NOT_FOUND",
                        "Market-data source is not registered: " + input.sourceCode()));
        InvestmentInstrumentPo instrument = instrumentRepository
                .findByVenueIdAndProductTypeAndSymbolAndDeletedFalse(
                        source.getVenueId(), input.productType(), input.symbol())
                .orElseThrow(() -> new InvestmentJobRetryableException("MARKET_INSTRUMENT_NOT_READY",
                        "Market instrument is awaiting contract metadata: "
                                + input.productType() + "/" + input.symbol()));
        return new MarketDataDimension(source.getId(), instrument.getId());
    }
}
