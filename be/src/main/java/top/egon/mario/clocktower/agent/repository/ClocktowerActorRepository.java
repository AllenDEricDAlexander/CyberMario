package top.egon.mario.clocktower.agent.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.clocktower.agent.po.ClocktowerActorPo;

import java.util.Optional;

public interface ClocktowerActorRepository extends JpaRepository<ClocktowerActorPo, Long> {

    Optional<ClocktowerActorPo> findByIdAndDeletedFalse(Long id);
}
