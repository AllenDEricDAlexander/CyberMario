package top.egon.mario.investment.quant.strategy;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Fingerprints the deployed strategy bytecode without relying on source-tree access.
 */
@Component
public class InvestmentStrategySourceHasher {

    public StrategySourceFingerprint fingerprint(Class<? extends InvestmentStrategy> implementationClass) {
        Objects.requireNonNull(implementationClass, "implementationClass");
        String resourceName = "/" + implementationClass.getName().replace('.', '/') + ".class";
        byte[] bytecode;
        try (InputStream input = implementationClass.getResourceAsStream(resourceName)) {
            if (input == null) {
                throw new IllegalStateException("Strategy bytecode resource is unavailable: " + resourceName);
            }
            bytecode = input.readAllBytes();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read strategy bytecode: " + resourceName, exception);
        }
        String sourceHash = sha256(bytecode);
        String packageVersion = implementationClass.getPackage().getImplementationVersion();
        String buildRevision = packageVersion == null || packageVersion.isBlank() ? sourceHash : packageVersion;
        return new StrategySourceFingerprint(sourceHash, buildRevision);
    }

    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
