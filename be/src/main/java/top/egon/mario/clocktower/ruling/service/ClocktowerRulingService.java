package top.egon.mario.clocktower.ruling.service;

import top.egon.mario.clocktower.ruling.dto.ClocktowerRulingApplyResponse;
import top.egon.mario.clocktower.ruling.dto.ClocktowerRulingCreateRequest;
import top.egon.mario.clocktower.ruling.dto.ClocktowerRulingResponse;
import top.egon.mario.clocktower.ruling.dto.ClocktowerRulingUndoRequest;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;

public interface ClocktowerRulingService {

    ClocktowerRulingApplyResponse create(Long roomId, ClocktowerRulingCreateRequest request, RbacPrincipal principal);

    List<ClocktowerRulingResponse> list(Long roomId, RbacPrincipal principal);

    ClocktowerRulingApplyResponse undo(Long roomId, Long rulingId, ClocktowerRulingUndoRequest request,
                                       RbacPrincipal principal);
}
