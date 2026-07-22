package top.egon.mario.agent.externalim.guard.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.agent.externalim.guard.po.AgentChatGuardAuditPo;

public interface AgentChatGuardAuditRepository extends JpaRepository<AgentChatGuardAuditPo, Long> {
}
