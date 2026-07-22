package top.egon.mario.rbac.activation.model;

/**
 * Identity of a successfully activated user.
 */
public record AccountActivationResult(Long userId, String username) {
}
