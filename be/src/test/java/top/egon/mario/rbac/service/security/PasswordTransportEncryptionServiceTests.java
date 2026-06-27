package top.egon.mario.rbac.service.security;

import org.junit.jupiter.api.Test;
import top.egon.mario.rbac.dto.response.PasswordEncryptionKeyResponse;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordTransportEncryptionServiceTests {

    @Test
    void decryptsPasswordEncryptedWithPublishedPublicKey() throws Exception {
        PasswordTransportEncryptionService service = new PasswordTransportEncryptionService();
        PasswordEncryptionKeyResponse key = service.currentKey();

        String encryptedPassword = encrypt(key.publicKey(), "secret-password");

        assertThat(key.keyId()).isNotBlank();
        assertThat(key.algorithm()).isEqualTo("RSA-OAEP-256");
        assertThat(service.decryptPassword(key.keyId(), encryptedPassword)).isEqualTo("secret-password");
    }

    private String encrypt(String publicKey, String password) throws Exception {
        byte[] publicKeyBytes = Base64.getDecoder().decode(publicKey);
        PublicKey key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(publicKeyBytes));
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new OAEPParameterSpec(
                "SHA-256",
                "MGF1",
                MGF1ParameterSpec.SHA256,
                PSource.PSpecified.DEFAULT
        ));
        return Base64.getEncoder().encodeToString(cipher.doFinal(password.getBytes(StandardCharsets.UTF_8)));
    }

}
