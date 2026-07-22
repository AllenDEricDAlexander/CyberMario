package top.egon.mario.agent.externalim.memory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.agent.externalim.memory.po.ExternalChatBindingPo;
import top.egon.mario.agent.externalim.model.ExternalChatPlatform;

import java.util.Optional;

public interface ExternalChatBindingRepository extends JpaRepository<ExternalChatBindingPo, Long> {

    Optional<ExternalChatBindingPo>
    findByPlatformAndConnectorIdAndExternalConversationIdAndEnabledTrueAndDeletedFalse(
            ExternalChatPlatform platform, String connectorId, String externalConversationId);
}
