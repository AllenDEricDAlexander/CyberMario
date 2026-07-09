package top.egon.mario.clocktower.agent.strategy.llm;

public class ClocktowerAgentLlmPolicyException extends RuntimeException {

    public ClocktowerAgentLlmPolicyException(String message) {
        super(message);
    }

    public ClocktowerAgentLlmPolicyException(String message, Throwable cause) {
        super(message, cause);
    }
}
