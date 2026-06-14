package top.egon.mario.agent.model.api;

/**
 * Business scenario for a model call, used only for audit attribution.
 */
public enum ModelScenario {

    UNKNOWN,
    AGENT_CHAT,
    RAG_CHAT,
    RAG_SUMMARY,
    BACKGROUND_TASK

}
