package top.egon.mario.investment.quant.strategy;

import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.DataCapability;
import top.egon.mario.investment.common.model.PriceType;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Immutable code-owned strategy configuration exposed read-only to clients.
 */
public record StrategyDescriptor(
        String strategyCode,
        String strategyVersion,
        String displayName,
        String description,
        StrategyEngineType engineType,
        Set<DataCapability> requiredCapabilities,
        Set<BarInterval> supportedIntervals,
        BarInterval evaluationInterval,
        PriceType priceType,
        String evaluationSchedule,
        String positionSizingPolicy,
        BigDecimal defaultLeverage,
        BigDecimal maximumLeverage,
        String feeModelCode,
        String slippageModelCode,
        String matchingModelCode
) {

    private static final Pattern CODE = Pattern.compile("[A-Z0-9][A-Z0-9_:-]*");
    private static final Pattern VERSION = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.+-]*");

    public StrategyDescriptor {
        strategyCode = identifier(strategyCode, CODE, "strategyCode", 128);
        strategyVersion = identifier(strategyVersion, VERSION, "strategyVersion", 64);
        displayName = text(displayName, "displayName", 256);
        description = text(description, "description", 2000);
        Objects.requireNonNull(engineType, "engineType");
        requiredCapabilities = immutableCapabilities(requiredCapabilities);
        supportedIntervals = immutableIntervals(supportedIntervals);
        Objects.requireNonNull(evaluationInterval, "evaluationInterval");
        if (evaluationInterval == BarInterval.NONE || !supportedIntervals.contains(evaluationInterval)) {
            throw new IllegalArgumentException("evaluationInterval must be a supported concrete interval");
        }
        Objects.requireNonNull(priceType, "priceType");
        if (priceType == PriceType.NONE || !requiredCapabilities.contains(candleCapability(priceType))) {
            throw new IllegalArgumentException("priceType must be backed by a required candle capability");
        }
        evaluationSchedule = identifier(evaluationSchedule, CODE, "evaluationSchedule", 128);
        positionSizingPolicy = identifier(positionSizingPolicy, CODE, "positionSizingPolicy", 128);
        defaultLeverage = positive(defaultLeverage, "defaultLeverage");
        maximumLeverage = positive(maximumLeverage, "maximumLeverage");
        if (defaultLeverage.compareTo(maximumLeverage) > 0) {
            throw new IllegalArgumentException("defaultLeverage must not exceed maximumLeverage");
        }
        feeModelCode = identifier(feeModelCode, CODE, "feeModelCode", 128);
        slippageModelCode = identifier(slippageModelCode, CODE, "slippageModelCode", 128);
        matchingModelCode = identifier(matchingModelCode, CODE, "matchingModelCode", 128);
    }

    private static Set<DataCapability> immutableCapabilities(Set<DataCapability> values) {
        if (values == null || values.isEmpty() || values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("requiredCapabilities must not be empty");
        }
        return Collections.unmodifiableSet(EnumSet.copyOf(values));
    }

    private static Set<BarInterval> immutableIntervals(Set<BarInterval> values) {
        if (values == null || values.isEmpty() || values.stream().anyMatch(Objects::isNull)
                || values.contains(BarInterval.NONE)) {
            throw new IllegalArgumentException("supportedIntervals must contain concrete intervals");
        }
        return Collections.unmodifiableSet(EnumSet.copyOf(values));
    }

    private static DataCapability candleCapability(PriceType value) {
        return switch (value) {
            case MARKET -> DataCapability.MARKET_CANDLE;
            case MARK -> DataCapability.MARK_CANDLE;
            case INDEX -> DataCapability.INDEX_CANDLE;
            case NONE -> throw new IllegalArgumentException("Concrete priceType is required");
        };
    }

    private static String identifier(String value, Pattern pattern, String name, int maxLength) {
        if (value == null || value.length() > maxLength || !pattern.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be a normalized code");
        }
        return value;
    }

    private static String text(String value, String name, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > maxLength) {
            throw new IllegalArgumentException(name + " is required");
        }
        return normalized;
    }

    private static BigDecimal positive(BigDecimal value, String name) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
