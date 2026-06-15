package top.egon.mario.agent.observability.po.enums;

public enum AgentRunEventType {
    RUN_STARTED,
    USER_MESSAGE,
    MODEL_REQUEST,
    MODEL_RESPONSE,
    TOOL_REQUEST,
    TOOL_RESPONSE,
    ASSISTANT_THINK,
    ASSISTANT_MESSAGE,
    RUN_COMPLETED,
    RUN_FAILED,
    RUN_CANCELLED
}
