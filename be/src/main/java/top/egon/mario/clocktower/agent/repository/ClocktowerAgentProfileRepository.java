package top.egon.mario.clocktower.agent.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.clocktower.agent.po.ClocktowerAgentProfilePo;

import java.util.List;
import java.util.Optional;

public interface ClocktowerAgentProfileRepository extends JpaRepository<ClocktowerAgentProfilePo, Long> {

    Optional<ClocktowerAgentProfilePo> findByIdAndDeletedFalse(Long id);

    Optional<ClocktowerAgentProfilePo> findFirstByNameAndDeletedFalse(String name);

    List<ClocktowerAgentProfilePo> findByDeletedFalseOrderByIdAsc();
}
