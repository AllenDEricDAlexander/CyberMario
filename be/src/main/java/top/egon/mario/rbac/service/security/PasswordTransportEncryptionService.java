package top.egon.mario.rbac.service.security;

import org.springframework.stereotype.Service;
import top.egon.mario.rbac.dto.response.PasswordEncryptionKeyResponse;
import top.egon.mario.rbac.service.RbacException;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.spec.MGF1ParameterSpec;
import java.util.Base64;

/**
 * Provides the public key and private-key decryptor for password transport encryption.
 */
@Service
public class PasswordTransportEncryptionService {

    private static final String ALGORITHM = "RSA-OAEP-256";
    private static final String CIPHER_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";

    private final KeyPair keyPair;
    private final PasswordEncryptionKeyResponse currentKey;

    public PasswordTransportEncryptionService() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            keyPair = generator.generateKeyPair();
            byte[] publicKey = keyPair.getPublic().getEncoded();
            currentKey = new PasswordEncryptionKeyResponse(keyId(publicKey), ALGORITHM,
                    Base64.getEncoder().encodeToString(publicKey));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("failed to initialize password transport encryption", e);
        }
    }

    public PasswordEncryptionKeyResponse currentKey() {
        return currentKey;
    }

    public String decryptPassword(String keyId, String encryptedPassword) {
        if (!currentKey.keyId().equals(keyId)) {
            throw new RbacException("AUTH_PASSWORD_KEY_INVALID", "password encryption key is invalid");
        }
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keyPair.getPrivate(), oaepSpec());
            byte[] encryptedBytes = Base64.getDecoder().decode(encryptedPassword);
            return new String(cipher.doFinal(encryptedBytes), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            throw new RbacException("AUTH_PASSWORD_DECRYPT_FAILED", "password encryption payload is invalid");
        }
    }

    private String keyId(byte[] publicKey) throws GeneralSecurityException {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(publicKey);
        byte[] prefix = new byte[16];
        System.arraycopy(digest, 0, prefix, 0, prefix.length);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(prefix);
    }

    private OAEPParameterSpec oaepSpec() {
        return new OAEPParameterSpec(
                "SHA-256",
                "MGF1",
                MGF1ParameterSpec.SHA256,
                PSource.PSpecified.DEFAULT
        );
    }

}
