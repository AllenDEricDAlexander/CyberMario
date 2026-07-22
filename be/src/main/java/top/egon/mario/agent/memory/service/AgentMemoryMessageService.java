package top.egon.mario.agent.memory.service;

import top.egon.mario.agent.externalim.model.ExternalChatPlatform;
import top.egon.mario.agent.memory.po.AgentMemoryMessagePo;
import top.egon.mario.agent.memory.po.AgentMemorySessionPo;
import top.egon.mario.agent.memory.po.enums.AgentMemoryMessageRole;
import top.egon.mario.agent.memory.po.enums.AgentMemoryMessageStatus;
import top.egon.mario.agent.memory.po.enums.AgentMemoryMessageType;
import top.egon.mario.agent.memory.service.model.AgentMemoryMessageRecord;
import top.egon.mario.agent.memory.service.model.AgentMemoryTurn;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;
import java.util.Optional;

public interface AgentMemoryMessageService {

    List<AgentMemoryMessagePo> appendAll(List<AgentMemoryMessageRecord> records);

    List<AgentMemoryMessagePo> messages(String sessionId, RbacPrincipal principal);

    List<AgentMemoryTurn> recentTurns(AgentMemorySessionPo session);

    int nextTurnNo(String sessionId);

    Optional<AgentMemoryMessagePo> findExternalMessage(
            String memorySpaceId, ExternalChatPlatform platform, String connectorId,
            String externalEventId, AgentMemoryMessageRole role,
            AgentMemoryMessageType messageType, AgentMemoryMessageStatus status);

    void markResponded(Long messageId);
}
