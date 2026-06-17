package top.egon.mario.clocktower.view.service;

import top.egon.mario.clocktower.view.dto.ClocktowerPlayerViewResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

public interface ClocktowerViewService {

    ClocktowerPlayerViewResponse playerView(Long roomId, Long seatId, RbacPrincipal principal);
}
