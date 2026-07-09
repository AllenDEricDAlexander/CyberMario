package top.egon.mario.clocktower.game.mic.service;

import top.egon.mario.clocktower.game.mic.dto.ClocktowerMicSessionView;
import top.egon.mario.rbac.service.security.RbacPrincipal;

public interface ClocktowerPublicMicService {

    ClocktowerMicSessionView startDayMicSession(Long gameId, RbacPrincipal principal);

    ClocktowerMicSessionView getMicSession(Long gameId, RbacPrincipal principal);

    ClocktowerMicSessionView finishCurrentTurn(Long gameId, Long turnId, RbacPrincipal principal);

    ClocktowerMicSessionView finishCurrentTurnAsActor(Long gameId, Long actorGameSeatId);

    ClocktowerMicSessionView skipTurn(Long gameId, Long turnId, RbacPrincipal principal);

    ClocktowerMicSessionView grabMic(Long gameId, RbacPrincipal principal);

    ClocktowerMicSessionView grabMicAsActor(Long gameId, Long actorGameSeatId);

    ClocktowerMicSessionView releaseMic(Long gameId, RbacPrincipal principal);

    ClocktowerMicSessionView extendGrabMic(Long gameId, long seconds, RbacPrincipal principal);

    ClocktowerMicSessionView closeSession(Long gameId, RbacPrincipal principal);

    boolean canSpeak(Long gameId, Long actorGameSeatId);

    void requireCanSpeak(Long gameId, Long actorGameSeatId);
}
