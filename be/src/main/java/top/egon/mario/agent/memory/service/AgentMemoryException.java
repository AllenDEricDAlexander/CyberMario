package top.egon.mario.agent.memory.service;

/**
 * Business exception for Agent memory lifecycle and ownership rules.
 */
public class AgentMemoryException extends RuntimeException {

    private final String code;

    public AgentMemoryException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
