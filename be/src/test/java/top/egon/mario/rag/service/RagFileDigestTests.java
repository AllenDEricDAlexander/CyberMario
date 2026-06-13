package top.egon.mario.rag.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies stable content fingerprints used for RAG file de-duplication.
 */
class RagFileDigestTests {

    @Test
    void sha256ReturnsSameFingerprintForSameFileContent() throws Exception {
        Path first = Files.createTempFile("rag-digest-first", ".txt");
        Path second = Files.createTempFile("rag-digest-second", ".txt");
        Files.writeString(first, "same document", StandardCharsets.UTF_8);
        Files.writeString(second, "same document", StandardCharsets.UTF_8);

        assertThat(RagFileDigest.sha256(first)).isEqualTo(RagFileDigest.sha256(second));
    }

    @Test
    void sha256ReturnsDifferentFingerprintForDifferentFileContent() throws Exception {
        Path first = Files.createTempFile("rag-digest-first", ".txt");
        Path second = Files.createTempFile("rag-digest-second", ".txt");
        Files.writeString(first, "first document", StandardCharsets.UTF_8);
        Files.writeString(second, "second document", StandardCharsets.UTF_8);

        assertThat(RagFileDigest.sha256(first)).isNotEqualTo(RagFileDigest.sha256(second));
    }

}
