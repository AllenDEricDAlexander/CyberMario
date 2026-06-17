package top.egon.mario.clocktower.replay.service;

import top.egon.mario.clocktower.replay.dto.ClocktowerReplayResponse;
import top.egon.mario.clocktower.replay.dto.ClocktowerVoteReplayResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;

public interface ClocktowerReplayService {

    ClocktowerReplayResponse replay(Long roomId, String mode, Long fromSeq, Long toSeq, RbacPrincipal principal);

    List<ClocktowerVoteReplayResponse> votes(Long roomId, RbacPrincipal principal);
}
