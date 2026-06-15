package top.egon.mario.agent.service;

/**
 * Business exception raised by agent debug and audit services.
 */
public class AgentException extends RuntimeException {

    private final String code;

    public AgentException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }

}
