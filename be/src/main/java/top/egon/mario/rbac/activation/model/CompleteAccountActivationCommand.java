package top.egon.mario.rbac.activation.model;

/**
 * Atomic account activation input.
 */
public record CompleteAccountActivationCommand(
        String token,
        String passwordKeyId,
        String encryptedPassword,
        String ip,
        String userAgent
) {
}
