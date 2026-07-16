package top.egon.mario.investment.marketdata.quality;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.DataCapability;
import top.egon.mario.investment.common.model.PriceType;
import top.egon.mario.investment.marketdata.po.InvestmentDataQualityIssuePo;
import top.egon.mario.investment.marketdata.ingest.MarketDataJobInput;
import top.egon.mario.investment.marketdata.provider.model.ExternalCandle;
import top.egon.mario.investment.marketdata.repository.InvestmentDataQualityIssueRepository;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Detects cross-record quality problems and persists them as append-only audited facts.
 */
@Service
public class MarketDataQualityService {

    private final InvestmentDataQualityIssueRepository issueRepository;
    private final ObjectMapper objectMapper;

    public MarketDataQualityService(InvestmentDataQualityIssueRepository issueRepository, ObjectMapper objectMapper) {
        this.issueRepository = issueRepository;
        this.objectMapper = objectMapper;
    }

    public List<MarketDataQualityFinding> inspectCandles(List<ExternalCandle> candles) {
        if (candles.isEmpty()) {
            return List.of();
        }
        List<ExternalCandle> ordered = candles.stream()
                .sorted(Comparator.comparing(ExternalCandle::openTime))
                .toList();
        List<MarketDataQualityFinding> findings = new ArrayList<>();
        Set<Instant> seen = new HashSet<>();
        ExternalCandle previous = null;
        for (ExternalCandle candle : ordered) {
            if (!seen.add(candle.openTime())) {
                findings.add(finding(MarketDataQualityCode.DUPLICATE, "WARNING", candle, Map.of()));
            }
            if (!validOhlc(candle)) {
                findings.add(finding(MarketDataQualityCode.OHLC_INVALID, "ERROR", candle, Map.of()));
            }
            if (negative(candle.baseVolume()) || negative(candle.quoteVolume())) {
                findings.add(finding(MarketDataQualityCode.NEGATIVE_VOLUME, "ERROR", candle, Map.of()));
            }
            Duration interval = duration(candle.interval());
            findings.addAll(inspectCandleTiming(candle));
            if (previous != null && previous.openTime().plus(interval).isBefore(candle.openTime())) {
                findings.add(finding(MarketDataQualityCode.GAP, "WARNING", candle,
                        Map.of("expectedOpenTime", previous.openTime().plus(interval).toString())));
            }
            previous = candle;
        }
        return List.copyOf(findings);
    }

    public List<MarketDataQualityFinding> inspectCandleTiming(List<ExternalCandle> candles) {
        return candles.stream().flatMap(candle -> inspectCandleTiming(candle).stream()).toList();
    }

    public List<MarketDataQualityFinding> missingQuoteInputs(Instant pointTime,
                                                              Set<DataCapability> capabilities,
                                                              BigDecimal markPrice,
                                                              BigDecimal openInterest) {
        List<MarketDataQualityFinding> findings = new ArrayList<>();
        if (requiresQuoteMarkPrice(capabilities) && markPrice == null) {
            findings.add(missing(MarketDataQualityCode.MISSING_MARK_PRICE, pointTime));
        }
        if (requiresQuoteOpenInterest(capabilities) && openInterest == null) {
            findings.add(missing(MarketDataQualityCode.MISSING_OPEN_INTEREST, pointTime));
        }
        return List.copyOf(findings);
    }

    public boolean requiresQuoteMarkPrice(Set<DataCapability> capabilities) {
        return capabilities.contains(DataCapability.LATEST_TICKER);
    }

    public boolean requiresQuoteOpenInterest(Set<DataCapability> capabilities) {
        return capabilities.contains(DataCapability.OPEN_INTEREST);
    }

    public List<MarketDataQualityFinding> missingContractInputs(Instant pointTime, boolean markPricePresent,
                                                                 boolean fundingRatePresent,
                                                                 boolean positionTierPresent) {
        List<MarketDataQualityFinding> findings = new ArrayList<>();
        if (!markPricePresent) {
            findings.add(missing(MarketDataQualityCode.MISSING_MARK_PRICE, pointTime));
        }
        if (!fundingRatePresent) {
            findings.add(missing(MarketDataQualityCode.MISSING_FUNDING_RATE, pointTime));
        }
        if (!positionTierPresent) {
            findings.add(missing(MarketDataQualityCode.MISSING_POSITION_TIER, pointTime));
        }
        return List.copyOf(findings);
    }

    public MarketDataQualityFinding staleQuote(Instant sourceTime, Instant cutoff) {
        if (!sourceTime.isBefore(cutoff)) {
            return null;
        }
        return new MarketDataQualityFinding(MarketDataQualityCode.STALE_QUOTE, "WARNING", "QUOTE",
                PriceType.NONE, BarInterval.NONE, sourceTime, Map.of("cutoff", cutoff.toString()));
    }

    public MarketDataQualityFinding outOfSubscription(Instant pointTime, MarketDataJobInput input) {
        return new MarketDataQualityFinding(MarketDataQualityCode.OUT_OF_SUBSCRIPTION, "ERROR", "SUBSCRIPTION",
                PriceType.NONE, BarInterval.NONE, pointTime, Map.of(
                "capability", input.capability().name(),
                "priceType", input.priceType().name(),
                "interval", input.interval().name(),
                "startInclusive", string(input.startInclusive()),
                "endExclusive", string(input.endExclusive())));
    }

    public MarketDataQualityFinding invalidProviderData(MarketDataJobInput input, Instant pointTime,
                                                        String providerMessage) {
        if (!isCandle(input.capability())) {
            return null;
        }
        String normalized = providerMessage == null ? "" : providerMessage.toLowerCase();
        MarketDataQualityCode code = normalized.contains("volume")
                ? MarketDataQualityCode.NEGATIVE_VOLUME : MarketDataQualityCode.OHLC_INVALID;
        return new MarketDataQualityFinding(code, "ERROR", "BAR", input.priceType(), input.interval(),
                input.startInclusive() == null ? pointTime : input.startInclusive(),
                Map.of("reason", normalized.contains("volume") ? "INVALID_VOLUME" : "INVALID_OHLC"));
    }

    public void persist(long jobId, MarketDataDimensionRef dimension,
                        List<MarketDataQualityFinding> findings) {
        for (MarketDataQualityFinding finding : findings) {
            InvestmentDataQualityIssuePo issue = new InvestmentDataQualityIssuePo();
            issue.setJobId(jobId);
            issue.setSourceId(dimension.sourceId());
            issue.setInstrumentId(dimension.instrumentId());
            issue.setDataType(finding.dataType());
            issue.setPriceType(finding.priceType());
            issue.setInterval(finding.interval());
            issue.setPointTime(finding.pointTime());
            issue.setIssueCode(finding.code().name());
            issue.setSeverity(finding.severity());
            issue.setDetailsJson(json(finding.details()));
            issue.setResolutionStatus("OPEN");
            issueRepository.save(issue);
        }
    }

    private MarketDataQualityFinding missing(MarketDataQualityCode code, Instant pointTime) {
        return new MarketDataQualityFinding(code, "ERROR", "CONTRACT_INPUT", PriceType.NONE,
                BarInterval.NONE, pointTime, Map.of("zeroFallbackAllowed", false));
    }

    private MarketDataQualityFinding finding(MarketDataQualityCode code, String severity,
                                             ExternalCandle candle, Map<String, Object> details) {
        return new MarketDataQualityFinding(code, severity, "BAR", candle.priceType(), candle.interval(),
                candle.openTime(), details);
    }

    private List<MarketDataQualityFinding> inspectCandleTiming(ExternalCandle candle) {
        Duration interval = duration(candle.interval());
        List<MarketDataQualityFinding> findings = new ArrayList<>();
        if (!aligned(candle.openTime(), interval)) {
            findings.add(finding(MarketDataQualityCode.GAP, "ERROR", candle,
                    Map.of("reason", "MISALIGNED_INTERVAL")));
        }
        if (!candle.closeTime().equals(candle.openTime().plus(interval))) {
            findings.add(finding(MarketDataQualityCode.GAP, "ERROR", candle,
                    Map.of("reason", "CLOSE_TIME_MISMATCH", "expectedCloseTime",
                            candle.openTime().plus(interval).toString())));
        }
        return findings;
    }

    private boolean isCandle(DataCapability capability) {
        return capability == DataCapability.MARKET_CANDLE || capability == DataCapability.MARK_CANDLE
                || capability == DataCapability.INDEX_CANDLE;
    }

    private boolean validOhlc(ExternalCandle candle) {
        return candle.low().compareTo(candle.open()) <= 0 && candle.low().compareTo(candle.close()) <= 0
                && candle.high().compareTo(candle.open()) >= 0 && candle.high().compareTo(candle.close()) >= 0
                && candle.low().compareTo(candle.high()) <= 0;
    }

    private boolean negative(BigDecimal value) {
        return value.signum() < 0;
    }

    private boolean aligned(Instant time, Duration interval) {
        return Math.floorMod(time.getEpochSecond(), interval.toSeconds()) == 0 && time.getNano() == 0;
    }

    private Duration duration(BarInterval interval) {
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

    private String json(Map<String, Object> details) {
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Unable to encode quality details", ex);
        }
    }

    private String string(Instant value) {
        return value == null ? "NONE" : value.toString();
    }

    public record MarketDataDimensionRef(long sourceId, long instrumentId) {
        public MarketDataDimensionRef {
            if (sourceId <= 0 || instrumentId <= 0) {
                throw new IllegalArgumentException("sourceId and instrumentId must be positive");
            }
        }
    }
}
