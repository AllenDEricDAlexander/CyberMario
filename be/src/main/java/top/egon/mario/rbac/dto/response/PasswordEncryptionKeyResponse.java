package top.egon.mario.rbac.dto.response;

/**
 * Public key metadata used by browser clients to encrypt password transport fields.
 */
public record PasswordEncryptionKeyResponse(
        String keyId,
        String algorithm,
        String publicKey
) {
}
