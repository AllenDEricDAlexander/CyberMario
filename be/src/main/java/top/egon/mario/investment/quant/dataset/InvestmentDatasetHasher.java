package top.egon.mario.investment.quant.dataset;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
