package top.egon.mario.clocktower.agent.decision;

public final class ClocktowerAgentDecisionStatus {

    public static final String ACCEPTED = "ACCEPTED";
    public static final String ACTION_REJECTED = "ACTION_REJECTED";
    public static final String ILLEGAL_INTENT_FALLBACK = "ILLEGAL_INTENT_FALLBACK";
    public static final String LLM_ERROR_FALLBACK = "LLM_ERROR_FALLBACK";

    private ClocktowerAgentDecisionStatus() {
    }
}
