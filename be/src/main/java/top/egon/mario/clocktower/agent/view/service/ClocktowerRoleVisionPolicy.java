package top.egon.mario.clocktower.agent.view.service;

import org.springframework.stereotype.Service;

@Service
public class ClocktowerRoleVisionPolicy {

    public boolean canSeeGrimoire(String roleCode) {
        return "SPY".equals(roleCode);
    }
}
