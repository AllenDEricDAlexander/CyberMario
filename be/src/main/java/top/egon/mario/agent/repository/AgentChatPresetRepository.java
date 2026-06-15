package top.egon.mario.agent.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import top.egon.mario.agent.po.AgentChatPresetPo;

import java.util.Optional;

/**
 * Repository for saved agent debug presets.
 */
public interface AgentChatPresetRepository extends JpaRepository<AgentChatPresetPo, Long>, JpaSpecificationExecutor<AgentChatPresetPo> {

    Optional<AgentChatPresetPo> findByIdAndDeletedFalse(Long id);

}
