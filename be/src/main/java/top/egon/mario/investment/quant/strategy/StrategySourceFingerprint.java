package top.egon.mario.investment.quant.strategy;

import java.util.regex.Pattern;

public record StrategySourceFingerprint(String sourceHash, String buildRevision) {

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public StrategySourceFingerprint {
        if (sourceHash == null || !SHA_256.matcher(sourceHash).matches()) {
            throw new IllegalArgumentException("sourceHash must be a lowercase SHA-256 value");
        }
        if (buildRevision == null || buildRevision.isBlank() || buildRevision.length() > 128) {
            throw new IllegalArgumentException("buildRevision is required");
        }
    }
}
