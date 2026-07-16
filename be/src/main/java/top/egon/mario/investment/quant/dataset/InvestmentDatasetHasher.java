package top.egon.mario.investment.quant.dataset;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import top.egon.mario.investment.marketdata.repository.jdbc.model.FundingRateRow;
import top.egon.mario.investment.marketdata.repository.jdbc.model.MarketBarDailyRow;
import top.egon.mario.investment.marketdata.repository.jdbc.model.MarketBarIntradayRow;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Produces stable SHA-256 hashes from recursively key-sorted JSON.
 */
@Component
public class InvestmentDatasetHasher {

    private final ObjectMapper objectMapper;

    public InvestmentDatasetHasher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String hash(Object value) {
        return sha256(canonicalJson(value));
    }

    public String canonicalJson(Object value) {
        return writeCanonical(objectMapper.valueToTree(value));
    }

    public String canonicalizeJson(String value) {
        try {
            return writeCanonical(objectMapper.readTree(value == null || value.isBlank() ? "{}" : value));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Dataset JSON must be valid", exception);
        }
    }

    public String hashIntradayBars(List<MarketBarIntradayRow> rows) {
        return hash(rows.stream().map(this::intradayValue).toList());
    }

    public String hashDailyBars(List<MarketBarDailyRow> rows) {
        return hash(rows.stream().map(this::dailyValue).toList());
    }

    public String hashFundingRates(List<FundingRateRow> rows) {
        return hash(rows.stream().map(this::fundingValue).toList());
    }

    public List<Map<String, Object>> fundingValues(List<FundingRateRow> rows) {
        return rows.stream().map(this::fundingValue).toList();
    }

    private String writeCanonical(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(sort(value));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not canonicalize dataset JSON", exception);
        }
    }

    private JsonNode sort(JsonNode value) {
        if (value == null || value.isNull() || value.isValueNode()) {
            return value;
        }
        if (value.isArray()) {
            ArrayNode result = objectMapper.createArrayNode();
            value.forEach(item -> result.add(sort(item)));
            return result;
        }
        ObjectNode result = objectMapper.createObjectNode();
        List<String> names = new ArrayList<>();
        value.fieldNames().forEachRemaining(names::add);
        names.stream().sorted(Comparator.naturalOrder())
                .forEach(name -> result.set(name, sort(value.get(name))));
        return result;
    }

    private Map<String, Object> intradayValue(MarketBarIntradayRow value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("openTime", value.openTime().toString());
        result.put("closeTime", value.closeTime().toString());
        barPrices(result, value.openPrice(), value.highPrice(), value.lowPrice(), value.closePrice(),
                value.baseVolume(), value.quoteVolume());
        result.put("closed", value.closed());
        result.put("revision", value.revision());
        result.put("checksum", value.checksum());
        return result;
    }

    private Map<String, Object> dailyValue(MarketBarDailyRow value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("barDate", value.barDate().toString());
        barPrices(result, value.openPrice(), value.highPrice(), value.lowPrice(), value.closePrice(),
                value.baseVolume(), value.quoteVolume());
        result.put("closed", value.closed());
        result.put("revision", value.revision());
        result.put("checksum", value.checksum());
        return result;
    }

    private Map<String, Object> fundingValue(FundingRateRow value) {
        return Map.of("instrumentId", value.instrumentId(), "fundingTime", value.fundingTime().toString(),
                "fundingRate", decimal(value.fundingRate()), "revision", value.revision(),
                "checksum", value.checksum());
    }

    private static void barPrices(Map<String, Object> result, BigDecimal open, BigDecimal high,
                                  BigDecimal low, BigDecimal close, BigDecimal base, BigDecimal quote) {
        result.put("open", decimal(open));
        result.put("high", decimal(high));
        result.put("low", decimal(low));
        result.put("close", decimal(close));
        result.put("baseVolume", decimal(base));
        result.put("quoteVolume", decimal(quote));
    }

    private static String decimal(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
