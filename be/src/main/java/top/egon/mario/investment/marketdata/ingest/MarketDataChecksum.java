package top.egon.mario.investment.marketdata.ingest;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Stable SHA-256 checksums for revision and snapshot comparisons.
 */
public final class MarketDataChecksum {

    private MarketDataChecksum() {
    }

    public static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    /**
     * Produces one scale-independent decimal representation for all market-data hashes.
     */
    public static String decimal(BigDecimal value) {
        if (value == null) {
            return "null";
        }
        return value.signum() == 0 ? "0" : value.stripTrailingZeros().toPlainString();
    }
}
