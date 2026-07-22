package top.egon.mario.rbac.activation.model;

/**
 * Selects the audit action for activation token issue and reissue.
 */
public enum ActivationTokenIssueReason {
    ISSUED("AUTH_ACTIVATION_TOKEN_ISSUED"),
    REISSUED("AUTH_ACTIVATION_TOKEN_REISSUED");

    private final String auditAction;

    ActivationTokenIssueReason(String auditAction) {
        this.auditAction = auditAction;
    }

    public String auditAction() {
        return auditAction;
    }
}
