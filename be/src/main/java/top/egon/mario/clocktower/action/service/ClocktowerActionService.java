package top.egon.mario.clocktower.action.service;

import top.egon.mario.clocktower.action.dto.ClocktowerActionRequest;
import top.egon.mario.clocktower.action.dto.ClocktowerActionResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

public interface ClocktowerActionService {

    ClocktowerActionResponse submit(Long roomId, ClocktowerActionRequest request, RbacPrincipal principal);
}
