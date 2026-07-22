package top.egon.mario.agent.memory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.agent.memory.po.AgentMemoryMessagePo;
import top.egon.mario.agent.externalim.model.ExternalChatPlatform;
import top.egon.mario.agent.memory.po.enums.AgentMemoryMessageRole;
import top.egon.mario.agent.memory.po.enums.AgentMemoryMessageStatus;
import top.egon.mario.agent.memory.po.enums.AgentMemoryMessageType;

import java.util.List;
import java.util.Optional;

/**
 * Repository for normalized memory messages.
 */
public interface AgentMemoryMessageRepository extends JpaRepository<AgentMemoryMessagePo, Long> {

    List<AgentMemoryMessagePo> findBySessionIdAndDeletedFalseOrderBySeqNoAsc(String sessionId);

    List<AgentMemoryMessagePo> findTop40BySessionIdAndDeletedFalseOrderBySeqNoDesc(String sessionId);

    List<AgentMemoryMessagePo> findTop80ByMemorySpaceIdAndIdLessThanAndDeletedFalseOrderByIdDesc(
            String memorySpaceId, Long beforeId);

    List<AgentMemoryMessagePo>
    findTop12ByMemorySpaceIdAndSourcePlatformAndSourceConnectorIdAndSourceConversationIdAndIdLessThanAndDeletedFalseOrderByIdDesc(
            String memorySpaceId, ExternalChatPlatform sourcePlatform, String sourceConnectorId,
            String sourceConversationId, Long beforeId);

    Optional<AgentMemoryMessagePo>
    findFirstByMemorySpaceIdAndSourcePlatformAndSourceConnectorIdAndExternalEventIdAndRoleAndMessageTypeAndMessageStatusAndDeletedFalseOrderByIdDesc(
            String memorySpaceId, ExternalChatPlatform platform, String connectorId, String externalEventId,
            AgentMemoryMessageRole role, AgentMemoryMessageType messageType,
            AgentMemoryMessageStatus messageStatus);

}
