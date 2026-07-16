package top.egon.mario.investment.quant.dataset;

import org.springframework.stereotype.Component;
import top.egon.mario.investment.common.InvestmentErrorCode;
import top.egon.mario.investment.common.InvestmentException;
import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.DataCapability;
import top.egon.mario.investment.marketdata.repository.jdbc.model.MarketBarDailyRow;
import top.egon.mario.investment.marketdata.repository.jdbc.model.MarketBarIntradayRow;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

/**
 * Rejects incomplete or future-leaking inputs before a dataset is persisted.
 */
@Component
public class DatasetCapabilityValidator {

    public void validate(MarketDataAsOfReader.ReadRequest request,
                         MarketDataAsOfReader.AsOfDataset dataset,
                         Set<DataCapability> capabilities) {
        for (MarketDataAsOfReader.AsOfInstrument instrument : dataset.instruments()) {
            if (capabilities.contains(DataCapability.CONTRACT_METADATA) && instrument.contractSpec() == null) {
                unavailable("Contract specification is missing", instrument.instrumentId());
            }
            if (instrument.contractSpec() != null
                    && (instrument.contractSpec().getIngestedAt() == null
                        || instrument.contractSpec().getIngestedAt().isAfter(request.dataAsOf()))) {
                unavailable("Contract specification is newer than dataAsOf", instrument.instrumentId());
            }
            if (capabilities.contains(DataCapability.POSITION_TIER) && instrument.positionTiers().isEmpty()) {
                unavailable("Position tiers are missing", instrument.instrumentId());
            }
            if (instrument.positionTiers().stream().anyMatch(tier -> tier.getObservedAt() == null
                    || tier.getIngestedAt() == null
                    || tier.getObservedAt().isAfter(request.dataAsOf())
                    || tier.getIngestedAt().isAfter(request.dataAsOf()))) {
                unavailable("Position tiers are newer than dataAsOf", instrument.instrumentId());
            }
            if (capabilities.contains(DataCapability.FUNDING_RATE) && instrument.fundingRates().isEmpty()) {
                unavailable("Funding rates are missing", instrument.instrumentId());
            }
            if (capabilities.contains(DataCapability.FUNDING_RATE)) {
                validateFunding(request, instrument);
            }
            for (MarketDataAsOfReader.AsOfBarSeries series : instrument.barSeries()) {
                validateSeries(request, instrument.instrumentId(), series);
            }
        }
    }

    private static void validateFunding(MarketDataAsOfReader.ReadRequest request,
                                        MarketDataAsOfReader.AsOfInstrument instrument) {
        if (instrument.contractSpec() == null || instrument.contractSpec().getFundingIntervalHours() <= 0) {
            unavailable("Funding interval metadata is missing", instrument.instrumentId());
        }
        Duration interval = Duration.ofHours(instrument.contractSpec().getFundingIntervalHours());
        List<top.egon.mario.investment.marketdata.repository.jdbc.model.FundingRateRow> rows =
                instrument.fundingRates();
        if (rows.getFirst().fundingTime().isAfter(request.startTime().plus(interval))
                || rows.getLast().fundingTime().plus(interval).isBefore(request.endTime())) {
            unavailable("Funding rates are stale for the requested range", instrument.instrumentId());
        }
        for (int index = 1; index < rows.size(); index++) {
            if (rows.get(index).fundingTime().isAfter(rows.get(index - 1).fundingTime().plus(interval))) {
                unavailable("Funding rates contain a gap", instrument.instrumentId());
            }
        }
    }

    private static void validateSeries(MarketDataAsOfReader.ReadRequest request, long instrumentId,
                                       MarketDataAsOfReader.AsOfBarSeries series) {
        if (series.interval() == BarInterval.D1) {
            validateDaily(request, instrumentId, series.dailyRows());
            return;
        }
        List<MarketBarIntradayRow> rows = series.intradayRows();
        if (rows.isEmpty() || rows.stream().anyMatch(row -> !row.closed())) {
            unavailable("Closed intraday bars are missing", instrumentId);
        }
        Duration step = duration(series.interval());
        if (!rows.getFirst().openTime().equals(request.startTime())
                || !rows.getLast().closeTime().equals(request.endTime())) {
            unavailable("Intraday bars are stale for the requested range", instrumentId);
        }
        for (int index = 1; index < rows.size(); index++) {
            if (!rows.get(index).openTime().equals(rows.get(index - 1).openTime().plus(step))) {
                unavailable("Intraday bars contain a gap", instrumentId);
            }
        }
    }

    private static void validateDaily(MarketDataAsOfReader.ReadRequest request, long instrumentId,
                                      List<MarketBarDailyRow> rows) {
        LocalDate start = request.startTime().atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate end = request.endTime().atZone(ZoneOffset.UTC).toLocalDate();
        if (!request.startTime().equals(start.atStartOfDay().toInstant(ZoneOffset.UTC))
                || !request.endTime().equals(end.atStartOfDay().toInstant(ZoneOffset.UTC))
                || rows.isEmpty() || rows.stream().anyMatch(row -> !row.closed())
                || !rows.getFirst().barDate().equals(start)
                || !rows.getLast().barDate().plusDays(1).equals(end)) {
            unavailable("Closed daily bars are incomplete", instrumentId);
        }
        for (int index = 1; index < rows.size(); index++) {
            if (!rows.get(index).barDate().equals(rows.get(index - 1).barDate().plusDays(1))) {
                unavailable("Daily bars contain a gap", instrumentId);
            }
        }
    }

    static Duration duration(BarInterval interval) {
        return switch (interval) {
            case M1 -> Duration.ofMinutes(1);
            case M5 -> Duration.ofMinutes(5);
            case M15 -> Duration.ofMinutes(15);
            case M30 -> Duration.ofMinutes(30);
            case H1 -> Duration.ofHours(1);
            case H4 -> Duration.ofHours(4);
            case D1 -> Duration.ofDays(1);
            case NONE -> throw new IllegalArgumentException("Concrete interval is required");
        };
    }

    private static void unavailable(String message, long instrumentId) {
        throw new InvestmentException(InvestmentErrorCode.DATA_UNAVAILABLE,
                message + " for instrument " + instrumentId);
    }
}
