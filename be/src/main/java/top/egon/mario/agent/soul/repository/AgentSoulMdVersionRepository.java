package top.egon.mario.agent.soul.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.agent.soul.po.AgentSoulMdVersionPo;

import java.util.List;

/**
 * Repository for user Agent SoulMD version snapshots.
 */
public interface AgentSoulMdVersionRepository extends JpaRepository<AgentSoulMdVersionPo, Long> {

    List<AgentSoulMdVersionPo> findByUserIdOrderByVersionNoDesc(Long userId);

}
