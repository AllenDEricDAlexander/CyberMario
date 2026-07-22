package top.egon.mario.agent.externalim.memory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.agent.externalim.memory.po.AgentMemorySpacePo;

import java.util.Optional;

public interface AgentMemorySpaceRepository extends JpaRepository<AgentMemorySpacePo, Long> {

    Optional<AgentMemorySpacePo> findBySpaceIdAndDeletedFalse(String spaceId);

    Optional<AgentMemorySpacePo> findBySpaceIdAndOwnerUserIdAndDeletedFalse(String spaceId, Long ownerUserId);
}
