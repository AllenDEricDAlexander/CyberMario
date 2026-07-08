package top.egon.mario.clocktower.game.action.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.clocktower.agent.constant.ClocktowerActorType;
import top.egon.mario.clocktower.common.ClocktowerAccess;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.clocktower.game.action.dto.ActorContext;
import top.egon.mario.clocktower.game.action.dto.ClocktowerGameActionRequest;
import top.egon.mario.clocktower.game.action.dto.ClocktowerGameActionResponse;
import top.egon.mario.clocktower.game.action.dto.GameActionCommand;
import top.egon.mario.clocktower.game.action.service.ClocktowerGameActionExecutor;
import top.egon.mario.clocktower.game.action.service.ClocktowerHumanGameActionService;
import top.egon.mario.clocktower.game.po.ClocktowerGameSeatPo;
import top.egon.mario.clocktower.game.repository.ClocktowerGameSeatRepository;
import top.egon.mario.rbac.service.security.RbacPrincipal;

@Service
@RequiredArgsConstructor
public class ClocktowerHumanGameActionServiceImpl implements ClocktowerHumanGameActionService {

    private final ClocktowerGameSeatRepository gameSeatRepository;
    private final ClocktowerGameActionExecutor executor;

    @Override
    @Transactional
    public ClocktowerGameActionResponse submit(Long gameId, ClocktowerGameActionRequest request,
                                               RbacPrincipal principal) {
        ClocktowerAccess.requireAuthenticated(principal);
        ClocktowerGameSeatPo seat = gameSeatRepository.findByGameIdAndUserIdAndDeletedFalse(gameId, principal.userId())
                .filter(candidate -> "ACTIVE".equals(candidate.getStatus()))
                .filter(candidate -> ClocktowerActorType.HUMAN.equals(candidate.getActorType()))
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_GAME_ACTION_PLAYER_REQUIRED"));
        if (request == null || request.actorGameSeatId() == null || !request.actorGameSeatId().equals(seat.getId())) {
            throw new ClocktowerException("CLOCKTOWER_GAME_ACTION_SEAT_FORBIDDEN");
        }
        return executor.execute(command(gameId, request), ActorContext.human(seat.getActorId(), seat.getUserId()));
    }

    private GameActionCommand command(Long gameId, ClocktowerGameActionRequest request) {
        return new GameActionCommand(gameId, request.actorGameSeatId(), request.actionType(),
                request.targetGameSeatIds(), request.nominationId(), request.vote(), request.content(),
                request.payload());
    }
}
