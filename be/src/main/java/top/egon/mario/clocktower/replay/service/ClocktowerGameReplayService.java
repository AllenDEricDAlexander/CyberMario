package top.egon.mario.clocktower.replay.service;

import top.egon.mario.clocktower.replay.dto.ClocktowerGameHistoryResponse;
import top.egon.mario.clocktower.replay.dto.ClocktowerGameReplayResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;

public interface ClocktowerGameReplayService {

    ClocktowerGameReplayResponse replay(Long gameId, RbacPrincipal principal);

    List<ClocktowerGameHistoryResponse> history(RbacPrincipal principal);
}
