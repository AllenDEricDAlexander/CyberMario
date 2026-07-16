package top.egon.mario.investment.agent.service;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import top.egon.mario.investment.agent.model.InvestmentAgentAction;
import top.egon.mario.investment.agent.model.InvestmentAgentDecisionProposal;
import top.egon.mario.investment.agent.model.InvestmentAgentRunInput;
import top.egon.mario.investment.agent.model.InvestmentAgentRunType;
import top.egon.mario.investment.common.InvestmentErrorCode;
import top.egon.mario.investment.common.InvestmentException;
import top.egon.mario.investment.common.model.OrderType;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Strict parser that completes every validation before a decision can reach persistence or trading. */
@Service
public class InvestmentAgentDecisionValidator {

    private static final int MAX_OUTPUT_LENGTH = 100_000;
    private static final Set<String> ALLOWED_FIELDS = Set.of(
            "instrumentId", "action", "confidence", "horizon", "thesis", "risks", "invalidation",
            "requestedQuantity", "requestedNotional", "requestedLeverage", "orderType", "limitPrice",
            "dataAsOf", "expiresAt");

    private final ObjectMapper strictMapper;
    private final Clock clock;

    public InvestmentAgentDecisionValidator(Clock clock) {
        this.clock = clock;
        this.strictMapper = new ObjectMapper(JsonFactory.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .build());
    }

    public InvestmentAgentDecisionProposal validate(String rawOutput, InvestmentAgentRunInput input) {
        if (!StringUtils.hasText(rawOutput) || rawOutput.length() > MAX_OUTPUT_LENGTH || input == null) {
            throw invalid("Investment Agent final output is missing or too large");
        }
        JsonNode root = parse(rawOutput);
        if (!root.isObject()) {
            throw invalid("Investment Agent final output must be one JSON object");
        }
        root.fieldNames().forEachRemaining(field -> {
            if (!ALLOWED_FIELDS.contains(field)) {
                throw invalid("Investment Agent final output contains an unknown field: " + field);
            }
        });
        for (String field : ALLOWED_FIELDS) {
            if (!root.has(field)) {
                throw invalid("Investment Agent final output is missing field: " + field);
            }
        }

        InvestmentAgentAction action = enumValue(root, "action", InvestmentAgentAction.class, true);
        Long instrumentId = positiveLong(root, "instrumentId", false);
        BigDecimal confidence = decimal(root, "confidence", true, 24, 12);
        String horizon = text(root, "horizon", true, 64);
        String thesis = text(root, "thesis", true, 20_000);
        List<String> risks = stringList(root, "risks");
        List<String> invalidation = stringList(root, "invalidation");
        Instant dataAsOf = instant(root, "dataAsOf", true);
        Instant expiresAt = instant(root, "expiresAt", false);

        if (confidence.signum() < 0 || confidence.compareTo(BigDecimal.ONE) > 0) {
            throw invalid("confidence must be between zero and one");
        }
        if (!input.dataAsOf().equals(dataAsOf)) {
            throw invalid("dataAsOf does not match the server-selected cutoff");
        }
        if (dataAsOf.isAfter(clock.instant())) {
            throw invalid("dataAsOf cannot be in the future");
        }
        if (expiresAt != null && (!expiresAt.isAfter(dataAsOf)
                || Duration.between(dataAsOf, expiresAt).compareTo(Duration.ofDays(30)) > 0
                || !expiresAt.isAfter(clock.instant()))) {
            throw invalid("expiresAt is outside the supported decision window");
        }
        requireInstrumentScope(instrumentId, action, input);

        BigDecimal quantity = decimal(root, "requestedQuantity", action != InvestmentAgentAction.HOLD, 38, 18);
        BigDecimal notional = decimal(root, "requestedNotional", action != InvestmentAgentAction.HOLD, 38, 18);
        BigDecimal leverage = decimal(root, "requestedLeverage", action != InvestmentAgentAction.HOLD, 24, 12);
        OrderType orderType = enumValue(root, "orderType", OrderType.class, action != InvestmentAgentAction.HOLD);
        BigDecimal limitPrice = decimal(root, "limitPrice", false, 38, 18);
        validateTradeShape(root, action, quantity, notional, leverage, orderType, limitPrice);
        return new InvestmentAgentDecisionProposal(instrumentId, action, confidence, horizon, thesis,
                risks, invalidation, quantity, notional, leverage, orderType, limitPrice, dataAsOf, expiresAt);
    }

    private JsonNode parse(String rawOutput) {
        try {
            return strictMapper.readTree(rawOutput);
        } catch (JsonProcessingException exception) {
            throw invalid("Investment Agent final output is not strict JSON", exception);
        }
    }

    private void requireInstrumentScope(Long instrumentId, InvestmentAgentAction action,
                                        InvestmentAgentRunInput input) {
        boolean instrumentRequired = action != InvestmentAgentAction.HOLD
                || input.runType() == InvestmentAgentRunType.INSTRUMENT_ANALYSIS
                || input.runType() == InvestmentAgentRunType.AUTO_TRADE;
        if (instrumentRequired && instrumentId == null) {
            throw invalid("This decision requires an instrumentId");
        }
        if (instrumentId != null && !input.instrumentIds().contains(instrumentId)) {
            throw invalid("Decision instrument is outside the server-bound run scope");
        }
    }

    private void validateTradeShape(JsonNode root, InvestmentAgentAction action, BigDecimal quantity,
                                    BigDecimal notional, BigDecimal leverage, OrderType orderType,
                                    BigDecimal limitPrice) {
        if (action == InvestmentAgentAction.HOLD) {
            for (String field : List.of("requestedQuantity", "requestedNotional", "requestedLeverage",
                    "orderType", "limitPrice")) {
                if (root.has(field) && !root.get(field).isNull()) {
                    throw invalid("HOLD must not contain trade terms");
                }
            }
            return;
        }
        if (quantity.signum() <= 0 || notional.signum() <= 0 || leverage.signum() <= 0) {
            throw invalid("Non-HOLD trade terms must be positive");
        }
        if (orderType == OrderType.MARKET && limitPrice != null) {
            throw invalid("MARKET decisions cannot contain limitPrice");
        }
        if (orderType == OrderType.LIMIT && (limitPrice == null || limitPrice.signum() <= 0)) {
            throw invalid("LIMIT decisions require a positive limitPrice");
        }
    }

    private BigDecimal decimal(JsonNode root, String field, boolean required, int precision, int scale) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            if (required) {
                throw invalid(field + " is required");
            }
            return null;
        }
        if (!node.isNumber() && !node.isTextual()) {
            throw invalid(field + " must be a decimal");
        }
        try {
            BigDecimal value = new BigDecimal(node.asText());
            if (value.precision() > precision || Math.max(value.scale(), 0) > scale) {
                throw invalid(field + " exceeds supported precision");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw invalid(field + " must be a decimal", exception);
        }
    }

    private Long positiveLong(JsonNode root, String field, boolean required) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            if (required) {
                throw invalid(field + " is required");
            }
            return null;
        }
        if (!node.isIntegralNumber() || !node.canConvertToLong() || node.longValue() <= 0) {
            throw invalid(field + " must be a positive integer");
        }
        return node.longValue();
    }

    private String text(JsonNode root, String field, boolean required, int maxLength) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            if (required) {
                throw invalid(field + " is required");
            }
            return null;
        }
        if (!node.isTextual() || !StringUtils.hasText(node.textValue())) {
            throw invalid(field + " must be non-blank text");
        }
        String value = node.textValue().trim();
        if (value.length() > maxLength) {
            throw invalid(field + " exceeds the supported length");
        }
        return value;
    }

    private List<String> stringList(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.isArray() || node.isEmpty() || node.size() > 20) {
            throw invalid(field + " must contain between one and twenty items");
        }
        Set<String> seen = new HashSet<>();
        List<String> values = new java.util.ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isTextual() || !StringUtils.hasText(item.textValue())) {
                throw invalid(field + " entries must be non-blank text");
            }
            String value = item.textValue().trim();
            if (value.length() > 1_000) {
                throw invalid(field + " entry exceeds the supported length");
            }
            if (seen.add(value)) {
                values.add(value);
            }
        }
        return List.copyOf(values);
    }

    private Instant instant(JsonNode root, String field, boolean required) {
        String value = text(root, field, required, 64);
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            throw invalid(field + " must be an ISO-8601 instant", exception);
        }
    }

    private <E extends Enum<E>> E enumValue(JsonNode root, String field, Class<E> type, boolean required) {
        String value = text(root, field, required, 64);
        if (value == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw invalid(field + " contains an unsupported value", exception);
        }
    }

    private static InvestmentException invalid(String message) {
        return new InvestmentException(InvestmentErrorCode.INVALID_REQUEST, message);
    }

    private static InvestmentException invalid(String message, Throwable cause) {
        return new InvestmentException(InvestmentErrorCode.INVALID_REQUEST, message, cause);
    }
}
