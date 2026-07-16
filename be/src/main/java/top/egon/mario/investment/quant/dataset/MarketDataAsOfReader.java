package top.egon.mario.investment.quant.dataset;

import org.springframework.stereotype.Component;
import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.PriceType;
import top.egon.mario.investment.marketdata.po.InvestmentContractSpecPo;
import top.egon.mario.investment.marketdata.po.InvestmentPositionTierPo;
import top.egon.mario.investment.marketdata.repository.InvestmentContractSpecRepository;
import top.egon.mario.investment.marketdata.repository.InvestmentPositionTierRepository;
import top.egon.mario.investment.marketdata.repository.jdbc.FundingRateJdbcRepository;
import top.egon.mario.investment.marketdata.repository.jdbc.MarketBarJdbcRepository;
import top.egon.mario.investment.marketdata.repository.jdbc.model.FundingRateRow;
import top.egon.mario.investment.marketdata.repository.jdbc.model.MarketBarDailyRow;
import top.egon.mario.investment.marketdata.repository.jdbc.model.MarketBarIntradayRow;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reads an SCD2 market-data view in bounded pages without one encompassing transaction.
 */
@Component
public class MarketDataAsOfReader {

    static final int PAGE_SIZE = 1_000;

    private final MarketBarJdbcRepository barRepository;
    private final FundingRateJdbcRepository fundingRepository;
    private final InvestmentContractSpecRepository contractSpecRepository;
    private final InvestmentPositionTierRepository positionTierRepository;

    public MarketDataAsOfReader(MarketBarJdbcRepository barRepository,
                                FundingRateJdbcRepository fundingRepository,
                                InvestmentContractSpecRepository contractSpecRepository,
                                InvestmentPositionTierRepository positionTierRepository) {
        this.barRepository = barRepository;
        this.fundingRepository = fundingRepository;
        this.contractSpecRepository = contractSpecRepository;
        this.positionTierRepository = positionTierRepository;
    }

    public AsOfDataset read(ReadRequest request) {
        Objects.requireNonNull(request, "request");
        List<AsOfInstrument> instruments = request.instrumentIds().stream().sorted()
                .map(instrumentId -> readInstrument(request, instrumentId))
                .toList();
        return new AsOfDataset(instruments);
    }

    private AsOfInstrument readInstrument(ReadRequest request, Long instrumentId) {
        InvestmentContractSpecPo specification = contractSpecRepository.findById(instrumentId)
                .filter(value -> Objects.equals(value.getSourceId(), request.sourceId()))
                .orElse(null);
        List<InvestmentPositionTierPo> tiers = positionTierRepository
                .findFirstBySourceIdAndInstrumentIdAndObservedAtLessThanEqualOrderByObservedAtDesc(
                        request.sourceId(), instrumentId, request.dataAsOf())
                .map(latest -> positionTierRepository
                        .findBySourceIdAndInstrumentIdAndObservedAtOrderByTierLevel(
                                request.sourceId(), instrumentId, latest.getObservedAt()))
                .orElseGet(List::of);
        List<AsOfBarSeries> bars = new ArrayList<>();
        for (PriceType priceType : request.priceTypes().stream().sorted().toList()) {
            for (BarInterval interval : request.intervals().stream().sorted().toList()) {
                bars.add(readBars(request, instrumentId, priceType, interval));
            }
        }
        List<FundingRateRow> fundingRates = request.includeFunding()
                ? readFunding(request, instrumentId) : List.of();
        return new AsOfInstrument(instrumentId, specification, List.copyOf(tiers), List.copyOf(bars), fundingRates);
    }

    private AsOfBarSeries readBars(ReadRequest request, long instrumentId,
                                   PriceType priceType, BarInterval interval) {
        if (interval == BarInterval.D1) {
            List<MarketBarDailyRow> rows = page(offset -> barRepository.findDailyAsOf(
                    request.sourceId(), instrumentId, priceType,
                    request.startTime().atZone(ZoneOffset.UTC).toLocalDate(),
                    request.endTime().atZone(ZoneOffset.UTC).toLocalDate(),
                    request.dataAsOf(), offset, PAGE_SIZE));
            return new AsOfBarSeries(priceType, interval, List.of(), rows);
        }
        List<MarketBarIntradayRow> rows = page(offset -> barRepository.findIntradayAsOf(
                request.sourceId(), instrumentId, priceType, interval,
                request.startTime(), request.endTime(), request.dataAsOf(), offset, PAGE_SIZE));
        return new AsOfBarSeries(priceType, interval, rows, List.of());
    }

    private List<FundingRateRow> readFunding(ReadRequest request, long instrumentId) {
        return page(offset -> fundingRepository.findAsOf(request.sourceId(), instrumentId,
                request.startTime(), request.endTime(), request.dataAsOf(), offset, PAGE_SIZE));
    }

    private static <T> List<T> page(PageReader<T> reader) {
        List<T> result = new ArrayList<>();
        int offset = 0;
        while (true) {
            List<T> page = List.copyOf(reader.read(offset));
            result.addAll(page);
            if (page.size() < PAGE_SIZE) {
                return List.copyOf(result);
            }
            offset += page.size();
        }
    }

    @FunctionalInterface
    private interface PageReader<T> {
        List<T> read(int offset);
    }

    public record ReadRequest(long sourceId, Set<Long> instrumentIds,
                              Set<PriceType> priceTypes, Set<BarInterval> intervals,
                              Instant startTime, Instant endTime, Instant dataAsOf,
                              boolean includeFunding) {
        public ReadRequest {
            if (sourceId <= 0 || instrumentIds == null || instrumentIds.isEmpty()
                    || instrumentIds.stream().anyMatch(id -> id == null || id <= 0)) {
                throw new IllegalArgumentException("Source and instrument ids must be positive");
            }
            priceTypes = immutableConcrete(priceTypes, PriceType.NONE, "priceTypes");
            intervals = immutableConcrete(intervals, BarInterval.NONE, "intervals");
            Objects.requireNonNull(startTime, "startTime");
            Objects.requireNonNull(endTime, "endTime");
            Objects.requireNonNull(dataAsOf, "dataAsOf");
            if (!endTime.isAfter(startTime) || dataAsOf.isBefore(endTime)) {
                throw new IllegalArgumentException("Dataset time range is invalid");
            }
            instrumentIds = Set.copyOf(instrumentIds);
        }

        private static <T> Set<T> immutableConcrete(Collection<T> values, T none, String name) {
            if (values == null || values.isEmpty()
                    || values.stream().anyMatch(value -> value == null || value.equals(none))) {
                throw new IllegalArgumentException(name + " must contain concrete values");
            }
            return Set.copyOf(values);
        }
    }

    public record AsOfDataset(List<AsOfInstrument> instruments) {
    }

    public record AsOfInstrument(long instrumentId, InvestmentContractSpecPo contractSpec,
                                 List<InvestmentPositionTierPo> positionTiers,
                                 List<AsOfBarSeries> barSeries,
                                 List<FundingRateRow> fundingRates) {
    }

    public record AsOfBarSeries(PriceType priceType, BarInterval interval,
                                List<MarketBarIntradayRow> intradayRows,
                                List<MarketBarDailyRow> dailyRows) {
    }
}
