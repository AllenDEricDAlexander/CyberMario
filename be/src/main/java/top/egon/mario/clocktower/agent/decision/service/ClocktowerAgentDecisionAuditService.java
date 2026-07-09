package top.egon.mario.clocktower.agent.decision.service;

import top.egon.mario.clocktower.agent.decision.po.ClocktowerAgentDecisionPo;

public interface ClocktowerAgentDecisionAuditService {

    ClocktowerAgentDecisionPo write(ClocktowerAgentDecisionAuditCommand command);
}
