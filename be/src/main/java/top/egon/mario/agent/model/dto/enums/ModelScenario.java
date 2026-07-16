package top.egon.mario.agent.model.dto.enums;

/**
 * Business scenario for a model call, used only for audit attribution.
 */
public enum ModelScenario {

    UNKNOWN,
    AGENT_CHAT,
    RAG_CHAT,
    RAG_SUMMARY,
    AGENT_SOUL_EVOLUTION,
    BACKGROUND_TASK,
    INVESTMENT_AGENT

}
