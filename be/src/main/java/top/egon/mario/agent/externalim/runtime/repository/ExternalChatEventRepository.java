package top.egon.mario.agent.externalim.runtime.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.agent.externalim.model.ExternalChatPlatform;
import top.egon.mario.agent.externalim.runtime.po.ExternalChatEventPo;

import java.util.Optional;

public interface ExternalChatEventRepository extends JpaRepository<ExternalChatEventPo, Long> {

    Optional<ExternalChatEventPo> findByPlatformAndConnectorIdAndExternalEventId(
            ExternalChatPlatform platform, String connectorId, String externalEventId);
}
