package top.egon.mario.rag.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Calculates stable content fingerprints for globally de-duplicated RAG files.
 */
public final class RagFileDigest {

    private static final int BUFFER_SIZE = 8192;
    private static final String SHA_256 = "SHA-256";

    private RagFileDigest() {
    }

    /**
     * Returns the SHA-256 fingerprint of a stored file.
     */
    public static String sha256(Path file) throws IOException {
        MessageDigest digest = newDigest();
        try (InputStream inputStream = Files.newInputStream(file);
             DigestInputStream digestInputStream = new DigestInputStream(inputStream, digest)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            while (digestInputStream.read(buffer) != -1) {
                // DigestInputStream updates the digest as bytes are consumed.
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance(SHA_256);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest algorithm is not available", e);
        }
    }

}
