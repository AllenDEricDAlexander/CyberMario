package top.egon.mario.clocktower.agent.decision.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.clocktower.agent.decision.po.ClocktowerAgentDecisionPo;

import java.util.List;

public interface ClocktowerAgentDecisionRepository extends JpaRepository<ClocktowerAgentDecisionPo, Long> {

    List<ClocktowerAgentDecisionPo> findByGameIdAndAgentInstanceIdAndDeletedFalseOrderByCreatedAtDescIdDesc(
            Long gameId, Long agentInstanceId);

    List<ClocktowerAgentDecisionPo> findByTriggerTaskIdAndDeletedFalseOrderByIdAsc(Long triggerTaskId);
}
