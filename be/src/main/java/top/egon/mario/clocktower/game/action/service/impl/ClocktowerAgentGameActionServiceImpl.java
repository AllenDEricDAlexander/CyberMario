package top.egon.mario.clocktower.game.action.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.clocktower.agent.constant.ClocktowerActorType;
import top.egon.mario.clocktower.agent.constant.ClocktowerAgentAutoMode;
import top.egon.mario.clocktower.agent.constant.ClocktowerAgentStatus;
import top.egon.mario.clocktower.agent.po.ClocktowerAgentInstancePo;
import top.egon.mario.clocktower.agent.repository.ClocktowerAgentInstanceRepository;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.clocktower.game.action.dto.ActorContext;
import top.egon.mario.clocktower.game.action.dto.ClocktowerGameActionRequest;
import top.egon.mario.clocktower.game.action.dto.ClocktowerGameActionResponse;
import top.egon.mario.clocktower.game.action.dto.GameActionCommand;
import top.egon.mario.clocktower.game.action.service.ClocktowerAgentGameActionService;
import top.egon.mario.clocktower.game.action.service.ClocktowerGameActionExecutor;
import top.egon.mario.clocktower.game.po.ClocktowerGameSeatPo;
import top.egon.mario.clocktower.game.repository.ClocktowerGameSeatRepository;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ClocktowerAgentGameActionServiceImpl implements ClocktowerAgentGameActionService {

    private final ClocktowerAgentInstanceRepository agentInstanceRepository;
    private final ClocktowerGameSeatRepository gameSeatRepository;
    private final ClocktowerGameActionExecutor executor;

    @Override
    @Transactional
    public ClocktowerGameActionResponse submitAgentAction(Long gameId, Long agentInstanceId,
                                                          ClocktowerGameActionRequest request) {
        ClocktowerAgentInstancePo instance = agentInstanceRepository.findByIdAndDeletedFalse(agentInstanceId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_AGENT_INSTANCE_INVALID"));
        if (!ClocktowerAgentStatus.ACTIVE.equals(instance.getStatus())) {
            throw new ClocktowerException("CLOCKTOWER_AGENT_INSTANCE_INVALID");
        }
        if (ClocktowerAgentAutoMode.PAUSED.equals(instance.getAutoMode())) {
            throw new ClocktowerException("CLOCKTOWER_AGENT_AUTO_MODE_PAUSED");
        }
        if (!Objects.equals(instance.getGameId(), gameId)
                || request == null
                || !Objects.equals(instance.getGameSeatId(), request.actorGameSeatId())) {
            throw new ClocktowerException("CLOCKTOWER_AGENT_GAME_SEAT_MISMATCH");
        }
        ClocktowerGameSeatPo seat = gameSeatRepository
                .findByIdAndGameIdAndDeletedFalse(request.actorGameSeatId(), gameId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_AGENT_GAME_SEAT_MISMATCH"));
        if (!ClocktowerActorType.AGENT.equals(seat.getActorType())) {
            throw new ClocktowerException("CLOCKTOWER_AGENT_GAME_SEAT_MISMATCH");
        }
        return executor.execute(command(gameId, request), ActorContext.agent(instance.getActorId(), instance.getId()));
    }

    private GameActionCommand command(Long gameId, ClocktowerGameActionRequest request) {
        return new GameActionCommand(gameId, request.actorGameSeatId(), request.actionType(),
                request.targetGameSeatIds(), request.nominationId(), request.vote(), request.content(),
                request.payload());
    }
}
