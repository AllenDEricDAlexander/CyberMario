package top.egon.mario.clocktower.grimoire.service;

import top.egon.mario.clocktower.grimoire.dto.response.ClocktowerGrimoireResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

public interface ClocktowerGrimoireService {

    ClocktowerGrimoireResponse getGrimoire(Long roomId, RbacPrincipal principal);
}
