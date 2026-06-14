package top.egon.mario.agent.model.audit;

/**
 * Persists or forwards model call audit events.
 */
public interface ModelAuditService {

    /**
     * Records one finished model call event.
     */
    void record(ModelAuditEvent event);

}
