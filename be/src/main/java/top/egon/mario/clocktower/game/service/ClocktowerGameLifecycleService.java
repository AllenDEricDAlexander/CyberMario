package top.egon.mario.clocktower.game.service;

import top.egon.mario.clocktower.game.dto.ClocktowerGameResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.time.Duration;
import java.time.Instant;

public interface ClocktowerGameLifecycleService {

    ClocktowerGameResponse startGame(Long roomId, RbacPrincipal principal);

    ClocktowerGameResponse endGame(Long gameId, RbacPrincipal principal);

    ClocktowerGameResponse abortGame(Long gameId, RbacPrincipal principal);

    boolean abortTimedOutRoom(Long roomId, Duration timeout, Instant now, RbacPrincipal principal);
}
