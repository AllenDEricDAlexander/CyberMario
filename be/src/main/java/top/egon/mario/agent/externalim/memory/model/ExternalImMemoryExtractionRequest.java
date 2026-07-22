package top.egon.mario.agent.externalim.memory.model;

import top.egon.mario.agent.memory.po.AgentMemoryMessagePo;
import top.egon.mario.agent.memory.po.AgentMemorySessionPo;

public record ExternalImMemoryExtractionRequest(
        AgentMemorySessionPo session,
        AgentMemoryMessagePo userMessage,
        AgentMemoryMessagePo assistantMessage,
        String requestId,
        String traceId
) {
}
